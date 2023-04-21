package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.extensions.ConnectionClientImpl
import com.tans.tfiletransporter.netty.extensions.ConnectionServerImpl
import com.tans.tfiletransporter.netty.extensions.DefaultClientManager
import com.tans.tfiletransporter.netty.extensions.IClientManager
import com.tans.tfiletransporter.netty.extensions.IServer
import com.tans.tfiletransporter.netty.extensions.requestSimplify
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
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastMsg
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastTransferFileReq
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastTransferFileResp
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


class BroadcastSender(
    val deviceName: String,
    val log: ILog,
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

    private val sendFuture: AtomicReference<ScheduledFuture<*>?> by lazy {
        AtomicReference(null)
    }

    private val broadcastSenderTask: AtomicReference<ConnectionClientImpl?> by lazy {
        AtomicReference(null)
    }

    private val requestReceiverTask: AtomicReference<ConnectionServerImpl?> by lazy {
        AtomicReference(null)
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

    private val senderBroadcastTask: Runnable by lazy {
        Runnable {
            val state = getCurrentState()
            if (state is BroadcastSenderState.Active) {
                log.d(TAG, "Send broadcast.")
                broadcastSenderTask.get()?.requestSimplify<BroadcastMsg, Unit>(
                    type = BroadcastDataType.BroadcastMsg.type,
                    request = BroadcastMsg(
                        version = TransferProtoConstant.VERSION,
                        deviceName = deviceName
                    ),
                    retryTimes = 0,
                    targetAddress = InetSocketAddress(state.broadcastAddress, TransferProtoConstant.BROADCAST_SCANNER_PORT),
                    callback = object : IClientManager.RequestCallback<Unit> {
                        override fun onSuccess(
                            type: Int,
                            messageId: Long,
                            localAddress: InetSocketAddress?,
                            remoteAddress: InetSocketAddress?,
                            d: Unit
                        ) {}
                        override fun onFail(errorMsg: String) {}
                    }
                )
            } else {
                log.e(TAG, "Send broadcast fail, wrong state: $state")
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
            ),
            enableBroadcast = true
        ).witchClient<ConnectionClientImpl>(log = log)
        this.broadcastSenderTask.get()?.stopTask()
        this.broadcastSenderTask.set(senderTask)

        val requestReceiverTask = NettyUdpConnectionTask(
            connectionType = ConnectionType.Bind(
                address = localAddress,
                port = TransferProtoConstant.BROADCAST_TRANSFER_SERVER_PORT
            )
        ).withServer<ConnectionServerImpl>(log = log)
        requestReceiverTask.registerServer(transferServer)
        this.requestReceiverTask.get()?.stopTask()
        this.requestReceiverTask.set(requestReceiverTask)

        senderTask.addObserver(object : NettyConnectionObserver {
            override fun onNewState(senderState: NettyTaskState, task: INettyConnectionTask) {
                if (senderState is NettyTaskState.ConnectionClosed
                    || senderState is NettyTaskState.Error
                    || getCurrentState() !is BroadcastSenderState.Requesting
                ) {
                    log.e(TAG, "Sender task error: $senderState, ${getCurrentState()}")
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
                                    log.d(TAG, "Request task bind fail: $receiverState, ${senderTask.getCurrentState()}, ${getCurrentState()}")
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
                                        val senderFuture = DefaultClientManager.taskScheduleExecutor.scheduleAtFixedRate(
                                            senderBroadcastTask,
                                            1000,
                                            broadcastSendIntervalMillis, TimeUnit.MILLISECONDS
                                        )
                                        this@BroadcastSender.sendFuture.get()?.cancel(true)
                                        this@BroadcastSender.sendFuture.set(senderFuture)
                                        newState(
                                            BroadcastSenderState.Active(
                                                broadcastAddress = broadcastAddress)
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
        sendFuture.get()?.cancel(true)
        sendFuture.set(null)
        broadcastSenderTask.get()?.stopTask()
        broadcastSenderTask.set(null)
        requestReceiverTask.get()?.stopTask()
        requestReceiverTask.set(null)
        newState(BroadcastSenderState.NoConnection)
        clearObserves()
    }

    override fun onNewState(s: BroadcastSenderState) {
        super.onNewState(s)
        for (o in observers) {
            o.onNewState(s)
        }
    }

    private fun dispatchTransferReq(remoteAddress: InetSocketAddress, req: BroadcastTransferFileReq) {
        val rd = RemoteDevice(
            remoteAddress = remoteAddress,
            deviceName = req.deviceName
        )
        for (o in observers) {
            o.requestTransferFile(rd)
        }
    }

    companion object {
        private const val TAG = "BroadcastSender"
    }
}