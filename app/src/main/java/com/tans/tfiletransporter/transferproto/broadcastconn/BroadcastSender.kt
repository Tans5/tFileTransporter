package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.logs.ILog
import com.tans.tfiletransporter.net.LOCAL_DEVICE
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.extensions.ConnectionClientImpl
import com.tans.tfiletransporter.netty.extensions.ConnectionServerImpl
import com.tans.tfiletransporter.netty.extensions.IServer
import com.tans.tfiletransporter.netty.extensions.simplifyServer
import com.tans.tfiletransporter.netty.extensions.witchClient
import com.tans.tfiletransporter.netty.extensions.withServer
import com.tans.tfiletransporter.netty.getBroadcastAddress
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask.Companion.ConnectionType
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.SimpleObservable
import com.tans.tfiletransporter.transferproto.SimpleStateable
import com.tans.tfiletransporter.transferproto.TransferProtoConstant
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastDataType
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastTransferFileReq
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastTransferFileResp
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


class BroadcastSender(
    val deviceName: String = LOCAL_DEVICE,
    val log: ILog = AndroidLog,
    val broadcastSendIntervalMillis: Long = 3000
) : SimpleObservable<BroadcastSenderObserver>, SimpleStateable<BroadcastSenderState> {

    override val state: AtomicReference<BroadcastSenderState> = AtomicReference(BroadcastSenderState.NoConnection)

    override val observers: LinkedBlockingDeque<BroadcastSenderObserver> = LinkedBlockingDeque()

    private val transferServer: IServer<BroadcastTransferFileReq, BroadcastTransferFileResp> by lazy {
        simplifyServer(
            requestType = BroadcastDataType.TransferFileReq.type,
            responseType = BroadcastDataType.TransferFileResp.type,
            log = log,
            onRequest = { lr, rr, r ->
                if (rr == null && r.version == TransferProtoConstant.VERSION) {
                    null
                } else {
                    BroadcastTransferFileResp(deviceName = deviceName)
                }
            },
            onNewRequest = { _, rr, r ->
                if (rr != null) {
                    dispatchTransferReq(rr, r)
                }
            }
        )
    }

    private val closeObserver: NettyConnectionObserver by lazy {
        object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                if (nettyState is NettyTaskState.ConnectionClosed || nettyState is NettyTaskState.Error) {
                    closeConnectionIfActive()
                }
            }
        }
    }

    override fun addObserver(o: BroadcastSenderObserver) {
        super.addObserver(o)
        o.onNewState(getCurrentState())
    }

    fun startBroadcastSender(localAddress: InetAddress, simpleCallback: SimpleCallback<Unit>) {
        val currentState = getCurrentState()
        if (currentState != BroadcastSenderState.NoConnection) {
            simpleCallback.onError("Wrong state: $currentState")
        }
        newState(BroadcastSenderState.Requesting)
        val hasInvokeCallback = AtomicBoolean(false)
        val (broadcastAddress, _) = localAddress.getBroadcastAddress()
        val senderTask = NettyUdpConnectionTask(
            connectionType = ConnectionType.Connect(
                address = broadcastAddress,
                port = TransferProtoConstant.BROADCAST_SCANNER_PORT
            )
        ).witchClient<ConnectionClientImpl>(log = log)

        val requestReceiverTask = NettyUdpConnectionTask(
            connectionType = ConnectionType.Bind(
                address = localAddress,
                port = TransferProtoConstant.BROADCAST_TRANSFER_PORT
            )
        ).withServer<ConnectionServerImpl>(log = log)
        requestReceiverTask.registerServer(transferServer)

        senderTask.addObserver(object : NettyConnectionObserver {
            override fun onNewState(senderState: NettyTaskState, task: INettyConnectionTask) {
                if (senderState is NettyTaskState.ConnectionClosed
                    || senderState is NettyTaskState.Error
                    || getCurrentState() !is BroadcastSenderState.Requesting
                ) {
                    log.e(TAG, "Sender task error: $senderState")
                    if (hasInvokeCallback.compareAndSet(false, true)) {
                        simpleCallback.onError(senderState.toString())
                    }
                    newState(BroadcastSenderState.NoConnection)
                    senderTask.removeObserver(this)
                    senderTask.stopTask()
                } else {
                    if (senderState is NettyTaskState.ConnectionActive) {
                        log.d(TAG, "Sender task connect success")
                        requestReceiverTask.addObserver(object : NettyConnectionObserver {
                            override fun onNewState(
                                receiverState: NettyTaskState,
                                task: INettyConnectionTask
                            ) {
                                if (receiverState is NettyTaskState.ConnectionClosed
                                    || receiverState is NettyTaskState.Error
                                    || senderTask.getCurrentState() !is NettyTaskState.ConnectionActive
                                    || getCurrentState() !is BroadcastSenderState.Requesting
                                ) {
                                    log.d(TAG, "Request task bind fail: $receiverState")
                                    if (hasInvokeCallback.compareAndSet(false, true)) {
                                        simpleCallback.onError(receiverState.toString())
                                    }
                                    newState(BroadcastSenderState.NoConnection)
                                    requestReceiverTask.removeObserver(this)
                                    requestReceiverTask.stopTask()
                                    senderTask.stopTask()
                                } else {
                                    if (receiverState is NettyTaskState.ConnectionActive) {
                                        log.d(TAG, "Request task bind success")
                                        if (hasInvokeCallback.compareAndSet(false, true)) {
                                            simpleCallback.onSuccess(Unit)
                                        }
                                        newState(
                                            BroadcastSenderState.Active(
                                                broadcastAddress = broadcastAddress,
                                                broadcastSenderTask = senderTask,
                                                requestReceiverTask = requestReceiverTask
                                            )
                                        )
                                        senderTask.addObserver(closeObserver)
                                        requestReceiverTask.addObserver(closeObserver)
                                    }
                                }
                            }
                        })
                        requestReceiverTask.startTask()
                        senderTask.removeObserver(this)
                    }
                }
            }
        })
        senderTask.startTask()
    }


    fun closeConnectionIfActive() {
        val currentState = getCurrentState()
        if (currentState is BroadcastSenderState.Active) {
            currentState.requestReceiverTask.stopTask()
            currentState.broadcastSenderTask.stopTask()
        }
        newState(BroadcastSenderState.NoConnection)
    }

    override fun onNewState(s: BroadcastSenderState) {
        super.onNewState(s)
        for (o in observers) {
            o.onNewState(s)
        }
    }

    private fun dispatchTransferReq(remoteAddress: InetSocketAddress, req: BroadcastTransferFileReq) {
        for (o in observers) {
            o.requestTransferFile(remoteAddress, req)
        }
    }

    companion object {
        private const val TAG = "BroadcastSender"
    }
}