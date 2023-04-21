package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.extensions.ConnectionClientImpl
import com.tans.tfiletransporter.netty.extensions.ConnectionServerImpl
import com.tans.tfiletransporter.netty.extensions.IClientManager
import com.tans.tfiletransporter.netty.extensions.IServer
import com.tans.tfiletransporter.netty.extensions.requestSimplify
import com.tans.tfiletransporter.netty.extensions.simplifyServer
import com.tans.tfiletransporter.netty.extensions.witchClient
import com.tans.tfiletransporter.netty.extensions.withServer
import com.tans.tfiletransporter.netty.getBroadcastAddress
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.SimpleObservable
import com.tans.tfiletransporter.transferproto.SimpleStateable
import com.tans.tfiletransporter.transferproto.TransferProtoConstant
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastDataType
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastMsg
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastTransferFileReq
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastTransferFileResp
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BroadcastReceiver(
    val deviceName: String,
    val log: ILog
) : SimpleStateable<BroadcastReceiverState>, SimpleObservable<BroadcastReceiverObserver> {

    override val state: AtomicReference<BroadcastReceiverState> = AtomicReference(BroadcastReceiverState.NoConnection)

    override val observers: LinkedBlockingDeque<BroadcastReceiverObserver> = LinkedBlockingDeque()

    private val closeObserver: NettyConnectionObserver by lazy {
        object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                if (nettyState is NettyTaskState.ConnectionClosed || nettyState is NettyTaskState.Error) {
                    closeConnectionIfActive()
                }
            }
        }
    }

    private val receiveBroadcastServer: IServer<BroadcastMsg, Unit> by lazy {
        simplifyServer(
            requestType = BroadcastDataType.BroadcastMsg.type,
            responseType = BroadcastDataType.BroadcastMsg.type,
            log = log,
            onRequest = { _, _, _ -> null },
            onNewRequest = { _, rr, r ->
                if (rr != null) {
                    dispatchBroadcast(rr, r)
                }
            }
        )
    }

    override fun addObserver(o: BroadcastReceiverObserver) {
        super.addObserver(o)
        o.onNewState(state.get())
    }

    fun startBroadcastReceiver(
        localAddress: InetAddress,
        simpleCallback: SimpleCallback<Unit>
    ) {
        val currentState = getCurrentState()
        if (currentState != BroadcastReceiverState.NoConnection) {
            simpleCallback.onError("Wrong state: $currentState")
        }
        val (broadcastAddress, _) = localAddress.getBroadcastAddress()
        newState(BroadcastReceiverState.Requesting)
        val receiverTask = NettyUdpConnectionTask(
            connectionType = NettyUdpConnectionTask.Companion.ConnectionType.Bind(
                address = broadcastAddress,
                port = TransferProtoConstant.BROADCAST_SCANNER_PORT
            ),
            enableBroadcast = true
        ).withServer<ConnectionServerImpl>(log = log)
        val transferRequestTask = NettyUdpConnectionTask(
            connectionType = NettyUdpConnectionTask.Companion.ConnectionType.Bind(
                address = localAddress,
                port = TransferProtoConstant.BROADCAST_TRANSFER_CLIENT_PORT
            )
        ).witchClient<ConnectionClientImpl>(log = log)

        val hasInvokeCallback = AtomicBoolean(false)

        receiverTask.addObserver(object : NettyConnectionObserver {
            override fun onNewState(receiverTaskState: NettyTaskState, task: INettyConnectionTask) {
                if (receiverTaskState is NettyTaskState.Error
                    || receiverTaskState is NettyTaskState.ConnectionClosed
                    || getCurrentState() !is BroadcastReceiverState.Requesting) {
                    log.e(TAG, "Bind receiver task error: $receiverTaskState, ${getCurrentState()}")
                    if (hasInvokeCallback.compareAndSet(false, true)) {
                        simpleCallback.onError(receiverTaskState.toString())
                    }
                    receiverTask.stopTask()
                    receiverTask.removeObserver(this)
                    onNewState(BroadcastReceiverState.NoConnection)
                } else {
                    if (receiverTaskState is NettyTaskState.ConnectionActive) {
                        log.d(TAG, "Bind receiver task success")
                        receiverTask.removeObserver(this)
                        transferRequestTask.addObserver(
                            object : NettyConnectionObserver {
                                override fun onNewState(
                                    transferTaskState: NettyTaskState,
                                    task: INettyConnectionTask
                                ) {
                                    if (transferTaskState is NettyTaskState.Error
                                        || transferTaskState is NettyTaskState.ConnectionClosed
                                        || receiverTask.getCurrentState() !is NettyTaskState.ConnectionActive
                                        || getCurrentState() !is BroadcastReceiverState.Requesting) {
                                        log.e(TAG, "Bind transfer req task error: $transferTaskState, ${receiverTask.getCurrentState()}, ${getCurrentState()}")
                                        if (hasInvokeCallback.compareAndSet(false, true)) {
                                            simpleCallback.onError(transferTaskState.toString())
                                        }
                                        transferRequestTask.stopTask()
                                        transferRequestTask.removeObserver(this)
                                        receiverTask.stopTask()
                                        onNewState(BroadcastReceiverState.NoConnection)
                                    } else {
                                        if (transferTaskState is NettyTaskState.ConnectionActive) {
                                            log.d(TAG, "Bind transfer req task success")
                                            if (hasInvokeCallback.compareAndSet(false, true)) {
                                                simpleCallback.onSuccess(Unit)
                                            }
                                            transferRequestTask.removeObserver(this)
                                            transferRequestTask.addObserver(closeObserver)
                                            receiverTask.registerServer(receiveBroadcastServer)
                                            receiverTask.addObserver(closeObserver)
                                            newState(
                                                BroadcastReceiverState.Active(
                                                    transferRequestTask = transferRequestTask,
                                                    receiverTask = receiverTask
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        )
                        transferRequestTask.startTask()
                    }
                }
            }
        })

        receiverTask.startTask()
    }


    fun requestFileTransfer(targetAddress: InetAddress, simpleCallback: SimpleCallback<BroadcastTransferFileResp>) {
        val currentState = getCurrentState()
        if (currentState !is BroadcastReceiverState.Active) {
            simpleCallback.onError("Current state is not active: $currentState")
            return
        }
        currentState.transferRequestTask.requestSimplify<BroadcastTransferFileReq, BroadcastTransferFileResp>(
            type = BroadcastDataType.TransferFileReq.type,
            request = BroadcastTransferFileReq(
                version = TransferProtoConstant.VERSION,
                deviceName = deviceName
            ),
            targetAddress = InetSocketAddress(targetAddress, TransferProtoConstant.BROADCAST_TRANSFER_SERVER_PORT),
            callback = object : IClientManager.RequestCallback<BroadcastTransferFileResp> {

                override fun onSuccess(
                    type: Int,
                    messageId: Long,
                    localAddress: InetSocketAddress?,
                    remoteAddress: InetSocketAddress?,
                    d: BroadcastTransferFileResp
                ) {
                    simpleCallback.onSuccess(d)
                }

                override fun onFail(errorMsg: String) {
                    simpleCallback.onError(errorMsg)
                }

            }
        )
    }

    fun closeConnectionIfActive() {
        val currentState = getCurrentState()
        if (currentState is BroadcastReceiverState.Active) {
            currentState.receiverTask.stopTask()
            currentState.transferRequestTask.stopTask()
        }
        newState(BroadcastReceiverState.NoConnection)
        clearObserves()
    }

    override fun onNewState(s: BroadcastReceiverState) {
        for (o in observers) {
            o.onNewState(s)
        }
    }
    private fun dispatchBroadcast(remoteAddress: InetSocketAddress, broadcastMsg: BroadcastMsg) {
        for (o in observers) {
            o.onNewBroadcast(remoteAddress, broadcastMsg)
        }
    }

    companion object {
        private const val TAG = "BroadcastReceiver"
    }
}