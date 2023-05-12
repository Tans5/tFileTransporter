package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.extensions.ConnectionClientImpl
import com.tans.tfiletransporter.netty.extensions.ConnectionServerImpl
import com.tans.tfiletransporter.netty.extensions.IClientManager
import com.tans.tfiletransporter.netty.extensions.IServer
import com.tans.tfiletransporter.netty.extensions.requestSimplify
import com.tans.tfiletransporter.netty.extensions.simplifyServer
import com.tans.tfiletransporter.netty.extensions.withClient
import com.tans.tfiletransporter.netty.extensions.withServer
import com.tans.tfiletransporter.netty.tcp.NettyTcpClientConnectionTask
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask
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
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BroadcastReceiver(
    private val deviceName: String,
    private val log: ILog,
    private val activeDeviceDuration: Long = 5000
) : SimpleStateable<BroadcastReceiverState>, SimpleObservable<BroadcastReceiverObserver> {

    override val state: AtomicReference<BroadcastReceiverState> = AtomicReference(BroadcastReceiverState.NoConnection)

    override val observers: LinkedBlockingDeque<BroadcastReceiverObserver> = LinkedBlockingDeque()

    private val receiverTask: AtomicReference<ConnectionServerImpl?> by lazy {
        AtomicReference(null)
    }

    private val activeDevices: LinkedBlockingDeque<RemoveFutureAndRemoteDevice> by lazy {
        LinkedBlockingDeque()
    }

    private val closeObserver: NettyConnectionObserver by lazy {
        object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                if (nettyState is NettyTaskState.ConnectionClosed || nettyState is NettyTaskState.Error) {
                    closeConnectionIfActive()
                }
            }

            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {
            }
        }
    }

    private val receiveBroadcastServer: IServer<BroadcastMsg, Unit> by lazy {
        simplifyServer(
            requestType = BroadcastDataType.BroadcastMsg.type,
            responseType = BroadcastDataType.BroadcastMsg.type,
            log = log,
            onRequest = { _, rr, r, isNewRequest ->
                if (rr != null && isNewRequest) {
                    dispatchBroadcast(rr, r)
                }
                null
            }
        )
    }

    override fun addObserver(o: BroadcastReceiverObserver) {
        super.addObserver(o)
        o.onNewState(state.get())
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    fun startBroadcastReceiver(
        broadcastAddress: InetAddress,
        simpleCallback: SimpleCallback<Unit>
    ) {
        val currentState = getCurrentState()
        if (currentState != BroadcastReceiverState.NoConnection) {
            simpleCallback.onError("Wrong state: $currentState")
        }
        newState(BroadcastReceiverState.Requesting)
        val receiverTask = NettyUdpConnectionTask(
            connectionType = NettyUdpConnectionTask.Companion.ConnectionType.Bind(
                address = broadcastAddress,
                port = TransferProtoConstant.BROADCAST_SCANNER_PORT
            ),
            enableBroadcast = true
        ).withServer<ConnectionServerImpl>(log = log)
        this.receiverTask.get()?.stopTask()
        this.receiverTask.set(receiverTask)

        val hasInvokeCallback = AtomicBoolean(false)

        receiverTask.addObserver(object : NettyConnectionObserver {
            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {}
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
                        log.d(TAG, "Bind transfer req task success")
                        if (hasInvokeCallback.compareAndSet(false, true)) {
                            simpleCallback.onSuccess(Unit)
                        }
                        receiverTask.registerServer(receiveBroadcastServer)
                        receiverTask.addObserver(closeObserver)
                        newState(BroadcastReceiverState.Active)
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
        val clientTask = NettyTcpClientConnectionTask(
            serverAddress = targetAddress,
            serverPort = TransferProtoConstant.BROADCAST_TRANSFER_SERVER_PORT
        ).withClient<ConnectionClientImpl>(log = log)
        val hasInvokeCallback = AtomicBoolean(false)

        fun onErrorCallback(errorMsg: String) {
            if (hasInvokeCallback.compareAndSet(false, true)) {
                simpleCallback.onError(errorMsg)
            }
            clientTask.stopTask()
        }

        fun onSuccessCallback(r: BroadcastTransferFileResp) {
            if (hasInvokeCallback.compareAndSet(false, true)) {
                simpleCallback.onSuccess(r)
            }
            clientTask.stopTask()
        }

        clientTask.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                if (nettyState is NettyTaskState.Error || nettyState is NettyTaskState.ConnectionClosed) {
                    log.e(TAG, nettyState.toString())
                    onErrorCallback(nettyState.toString())
                }
                if (nettyState is NettyTaskState.ConnectionActive) {
                    clientTask.requestSimplify(
                        type = BroadcastDataType.TransferFileReq.type,
                        request = BroadcastTransferFileReq(
                            version = TransferProtoConstant.VERSION,
                            deviceName = deviceName
                        ),
                        callback = object : IClientManager.RequestCallback<BroadcastTransferFileResp> {

                            override fun onSuccess(
                                type: Int,
                                messageId: Long,
                                localAddress: InetSocketAddress?,
                                remoteAddress: InetSocketAddress?,
                                d: BroadcastTransferFileResp
                            ) {
                                onSuccessCallback(d)
                            }

                            override fun onFail(errorMsg: String) {
                                log.e(TAG, errorMsg)
                                onErrorCallback(errorMsg)
                            }
                        }
                    )
                }
            }

            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {}
        })
        clientTask.startTask()
    }

    fun closeConnectionIfActive() {
        this.receiverTask.get()?.stopTask()
        this.receiverTask.set(null)
        newState(BroadcastReceiverState.NoConnection)
        clearObserves()
        for (a in activeDevices) {
            a.removeFuture.cancel(true)
        }
        activeDevices.clear()
    }

    override fun onNewState(s: BroadcastReceiverState) {
        for (o in observers) {
            o.onNewState(s)
        }
    }

    private data class RemoveFutureAndRemoteDevice(
        val removeFuture: ScheduledFuture<*>,
        val remoteDevice: RemoteDevice,
        val firstVisibleTime: Long
    )

    private fun dispatchBroadcast(remoteAddress: InetSocketAddress, broadcastMsg: BroadcastMsg) {
        val rd = RemoteDevice(remoteAddress, broadcastMsg.deviceName)
        for (o in observers) {
            o.onNewBroadcast(rd)
        }
        val future = taskScheduleExecutor.schedule({
            activeDevices.removeIf { it.remoteDevice == rd }
            val newDevices = activeDevices.sortedBy { it.firstVisibleTime }.map { it.remoteDevice }
            for (o in observers) {
                o.onActiveRemoteDevicesUpdate(newDevices)
            }
        }, activeDeviceDuration, TimeUnit.MILLISECONDS)
        val cache = activeDevices.find { it.remoteDevice == rd }
        if (cache != null) {
            activeDevices.remove(cache)
            cache.removeFuture.cancel(true)
            val new = cache.copy(removeFuture = future)
            activeDevices.add(new)
        } else {
            activeDevices.add(
                RemoveFutureAndRemoteDevice(
                    removeFuture = future,
                    remoteDevice = rd,
                    firstVisibleTime = System.currentTimeMillis()
                )
            )
        }
        val newDevices = activeDevices.sortedBy { it.firstVisibleTime }.map { it.remoteDevice }
        for (o in observers) {
            o.onActiveRemoteDevicesUpdate(newDevices)
        }
    }

    companion object {
        private const val TAG = "BroadcastReceiver"
        private val taskScheduleExecutor: ScheduledExecutorService by lazy {
            Executors.newScheduledThreadPool(1) {
                Thread(it, "BroadcastReceiverTaskThread")
            }
        }
    }
}