package com.tans.tfiletransporter.transferproto.qrscanconn

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.extensions.ConnectionClientImpl
import com.tans.tfiletransporter.netty.extensions.IClientManager
import com.tans.tfiletransporter.netty.extensions.requestSimplify
import com.tans.tfiletransporter.netty.extensions.withClient
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.SimpleObservable
import com.tans.tfiletransporter.transferproto.SimpleStateable
import com.tans.tfiletransporter.transferproto.TransferProtoConstant
import com.tans.tfiletransporter.transferproto.qrscanconn.model.QRCodeTransferFileReq
import com.tans.tfiletransporter.transferproto.qrscanconn.model.QrScanDataType
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Scan QRCode to get server's information[com.tans.tfiletransporter.transferproto.qrscanconn.model.QRCodeShare], and create UDP connection to server.
 * After connection create, client send [QrScanDataType.TransferFileReq][QRCodeTransferFileReq] request to create FileExplore connection.
 *
 */
class QRCodeScanClient(private val log: ILog) : SimpleStateable<QRCodeScanState>, SimpleObservable<QRCodeScanObserver> {

    override val observers: LinkedBlockingDeque<QRCodeScanObserver> = LinkedBlockingDeque()

    override val state: AtomicReference<QRCodeScanState> = AtomicReference(QRCodeScanState.NoConnection)
    private val connectionTask: AtomicReference<ConnectionClientImpl?> = AtomicReference(null)

    override fun addObserver(o: QRCodeScanObserver) {
        super.addObserver(o)
        o.onNewState(state.get())
    }

    fun startQRCodeScanClient(serverAddress: InetAddress, callback: SimpleCallback<Unit>) {
        if (getCurrentState() != QRCodeScanState.NoConnection) {
            val eMsg = "Wrong state: ${getCurrentState()}"
            log.e(TAG, eMsg)
            callback.onError(eMsg)
            return
        }
        newState(QRCodeScanState.Requesting)
        val connectionTask = NettyUdpConnectionTask(
            connectionType = NettyUdpConnectionTask.Companion.ConnectionType.Connect(
                address = serverAddress,
                port = TransferProtoConstant.QR_CODE_SCAN_SERVER_PORT
            )
        ).withClient<ConnectionClientImpl>(log = log)
        this.connectionTask.get()?.stopTask()
        this.connectionTask.set(connectionTask)
        val hasInvokeCallback = AtomicBoolean(false)
        connectionTask.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                if (nettyState is NettyTaskState.Error || nettyState is NettyTaskState.ConnectionClosed) {
                    val eMsg = "Connection error: $nettyState"
                    log.e(TAG, eMsg)
                    if (hasInvokeCallback.compareAndSet(false, true)) {
                        callback.onError(eMsg)
                    }
                    closeConnectionIfActive()
                }
                if (nettyState is NettyTaskState.ConnectionActive) {
                    val currentState = getCurrentState()
                    if (currentState == QRCodeScanState.Requesting) {
                        newState(QRCodeScanState.Active)
                        log.d(TAG, "Connection is active.")
                        if (hasInvokeCallback.compareAndSet(false, true)) {
                            callback.onSuccess(Unit)
                        }
                    } else {
                        val eMsg = "Error current state: $currentState"
                        log.e(TAG, eMsg)
                        if (hasInvokeCallback.compareAndSet(false, true)) {
                            callback.onError(eMsg)
                        }
                        closeConnectionIfActive()
                    }
                }
            }
            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {}
        })
        connectionTask.startTask()
    }

    fun requestFileTransfer(targetAddress: InetAddress, deviceName: String, simpleCallback: SimpleCallback<Unit>) {
        val connectionTask = connectionTask.get()
        val currentState = getCurrentState()
        if (connectionTask == null) {
            val eMsg = "Current connection task is null."
            log.e(TAG, eMsg)
            simpleCallback.onError(eMsg)
            return
        }
        if (currentState != QRCodeScanState.Active) {
            val eMsg = "Wrong state: $currentState"
            log.e(TAG, eMsg)
            simpleCallback.onError(eMsg)
            return
        }
        connectionTask.requestSimplify(
            type = QrScanDataType.TransferFileReq.type,
            request = QRCodeTransferFileReq(
                version = TransferProtoConstant.VERSION,
                deviceName = deviceName
            ),
            retryTimeout = 2000L,
            targetAddress = InetSocketAddress(targetAddress, TransferProtoConstant.QR_CODE_SCAN_SERVER_PORT),
            callback = object : IClientManager.RequestCallback<Unit> {
                override fun onSuccess(
                    type: Int,
                    messageId: Long,
                    localAddress: InetSocketAddress?,
                    remoteAddress: InetSocketAddress?,
                    d: Unit
                ) {
                    log.d(TAG, "Request transfer file success")
                    simpleCallback.onSuccess(Unit)
                }

                override fun onFail(errorMsg: String) {
                    log.e(TAG, errorMsg)
                    simpleCallback.onError(errorMsg)
                }

            }
        )
    }

    override fun onNewState(s: QRCodeScanState) {
        for (o in observers) {
            o.onNewState(s)
        }
    }

    fun closeConnectionIfActive() {
        newState(QRCodeScanState.NoConnection)
        connectionTask.get()?.let {
            it.stopTask()
            connectionTask.set(null)
        }
        clearObserves()
    }

    companion object {
        private const val TAG = "QRCodeScanClient"
    }
}