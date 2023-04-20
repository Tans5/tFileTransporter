package com.tans.tfiletransporter.transferproto.p2pconn

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.extensions.*
import com.tans.tfiletransporter.netty.tcp.NettyTcpClientConnectionTask
import com.tans.tfiletransporter.netty.tcp.NettyTcpServerConnectionTask
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.SimpleObservable
import com.tans.tfiletransporter.transferproto.SimpleStateable
import com.tans.tfiletransporter.transferproto.TransferProtoConstant
import com.tans.tfiletransporter.transferproto.p2pconn.model.P2pDataType
import com.tans.tfiletransporter.transferproto.p2pconn.model.P2pHandshakeReq
import com.tans.tfiletransporter.transferproto.p2pconn.model.P2pHandshakeResp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class P2pConnection(
    private val currentDeviceName: String,
    private val log: ILog
) : SimpleObservable<P2pConnectionObserver>, SimpleStateable<P2pConnectionState> {

    override val state: AtomicReference<P2pConnectionState> = AtomicReference(P2pConnectionState.NoConnection)

    override val observers: LinkedBlockingDeque<P2pConnectionObserver> by lazy {
        LinkedBlockingDeque()
    }

    private val handShakeServer: IServer<P2pHandshakeReq, P2pHandshakeResp> by lazy {
        simplifyServer(
            requestType = P2pDataType.HandshakeReq.type,
            responseType = P2pDataType.HandshakeResp.type,
            log = log,
            onRequest = { _, rd, r ->
                if (getCurrentState() is P2pConnectionState.Active
                    && r.version == TransferProtoConstant.VERSION) {
                    P2pHandshakeResp(
                        deviceName = currentDeviceName
                    )
                } else {
                    null
                }
            },
            onNewRequest = { ld, rd, r ->
                log.d(TAG, "Receive handshake: ld -> $ld, rd -> $rd, r -> $r")
                if (ld is InetSocketAddress
                    && rd is InetSocketAddress
                    && getCurrentState() is P2pConnectionState.Active) {
                    newState(P2pConnectionState.Handshake(
                        localAddress = ld,
                        remoteAddress = rd,
                        remoteDeviceName = r.deviceName
                    ))
                } else {
                    closeConnectionIfActive()
                }
            }
        )
    }

    private val transferFileServer: IServer<Unit, Unit> by lazy {
        simplifyServer(
            requestType = P2pDataType.TransferFileReq.type,
            responseType = P2pDataType.TransferFileResp.type,
            log = log,
            onRequest = { _, _, _ -> },
            onNewRequest = { _, _, _ ->
                log.d(TAG, "Receive transfer file request.")
                dispatchTransferFile(true)
            }
        )
    }

    private val closeServer: IServer<Unit, Unit> by lazy {
        simplifyServer(
            requestType = P2pDataType.CloseConnReq.type,
            responseType = P2pDataType.CloseConnResp.type,
            log = log,
            onRequest = { _, _, _ -> },
            onNewRequest = { _, _, _ ->
                log.d(TAG, "Receive close request.")
                Dispatchers.IO.asExecutor().runCatching {
                    Thread.sleep(100)
                    closeConnectionIfActive()
                }
            }
        )
    }

    private val activeServerNettyTask: AtomicReference<NettyTcpServerConnectionTask?> by lazy {
        AtomicReference(null)
    }

    private val activeCommunicationNettyTask: AtomicReference<ConnectionServerClientImpl?> by lazy {
        AtomicReference(null)
    }

    private val activeCommunicationTaskObserver: NettyConnectionObserver by lazy {
        object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                if (nettyState is NettyTaskState.ConnectionClosed || nettyState is NettyTaskState.Error) {
                    log.d(TAG, "Connection closed: $nettyState")
                    closeConnectionIfActive()
                }
            }
        }
    }

    override fun addObserver(o: P2pConnectionObserver) {
        super.addObserver(o)
        o.onNewState(getCurrentState())
    }

    fun bind(address: InetAddress, simpleCallback: SimpleCallback<Unit>) {
        val currentState = getCurrentState()
        if (currentState != P2pConnectionState.NoConnection) {
            simpleCallback.onError("Wrong current state: $currentState")
            return
        }
        newState(P2pConnectionState.Requesting)
        val hasInvokeCallback = AtomicBoolean(false)
        var serverTask: NettyTcpServerConnectionTask? = null
        var communicationTask: ConnectionServerClientImpl? = null
        val stateCallback = object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                if (nettyState is NettyTaskState.Error
                    || nettyState is NettyTaskState.ConnectionClosed
                    || getCurrentState() !is P2pConnectionState.Requesting
                ) {
                    if (hasInvokeCallback.compareAndSet(false, true)) {
                        simpleCallback.onError(nettyState.toString())
                    }
                    serverTask?.stopTask()
                    communicationTask?.stopTask()
                    log.e(TAG, "Bind $address fail: $nettyState, ${getCurrentState()}")
                    task.removeObserver(this)
                    newState(P2pConnectionState.NoConnection)
                } else {
                    if (task !is NettyTcpServerConnectionTask && nettyState is NettyTaskState.ConnectionActive) {
                        task.addObserver(activeCommunicationTaskObserver)
                        activeCommunicationNettyTask.set(communicationTask)
                        if (hasInvokeCallback.compareAndSet(false, true)) {
                            simpleCallback.onSuccess(Unit)
                        }
                        newState(
                            P2pConnectionState.Active(
                                localAddress = nettyState.channel.localAddress() as? InetSocketAddress,
                                remoteAddress = nettyState.channel.remoteAddress() as? InetSocketAddress
                            )
                        )
                        log.d(TAG, "Bind $address success: $nettyState")
                        task.removeObserver(this)
                    }
                    if (task is NettyTcpServerConnectionTask && nettyState is NettyTaskState.ConnectionActive) {
                        activeServerNettyTask.set(serverTask)
                        log.d(TAG, "Server is active.")
                        task.removeObserver(this)
                    }
                }
            }
        }
        val hasClientConnection = AtomicBoolean(false)
        serverTask = NettyTcpServerConnectionTask(
            bindAddress = address,
            bindPort = TransferProtoConstant.P2P_GROUP_OWNER_PORT,
            newClientTaskCallback = { client ->
                if (hasClientConnection.compareAndSet(false, true)) {
                    val fixedClientConnection = client.withServer<ConnectionServerImpl>(log = log)
                        .witchClient<ConnectionServerClientImpl>(log = log)
                    fixedClientConnection.registerServer(handShakeServer)
                    fixedClientConnection.registerServer(transferFileServer)
                    fixedClientConnection.registerServer(closeServer)
                    fixedClientConnection.addObserver(stateCallback)
                    communicationTask = fixedClientConnection
                } else {
                    client.stopTask()
                }
            }
        )
        serverTask.addObserver(stateCallback)
        serverTask.startTask()
    }

    fun connect(serverAddress: InetAddress, simpleCallback: SimpleCallback<Unit>) {
        val currentState = getCurrentState()
        if (currentState != P2pConnectionState.NoConnection) {
            simpleCallback.onError("Wrong current state: $currentState")
            return
        }
        newState(P2pConnectionState.Requesting)
        val clientTask = NettyTcpClientConnectionTask(
            serverAddress = serverAddress,
            serverPort = TransferProtoConstant.P2P_GROUP_OWNER_PORT
        ).withServer<ConnectionServerImpl>(log = log).witchClient<ConnectionServerClientImpl>(log = log)
        clientTask.registerServer(transferFileServer)
        clientTask.registerServer(closeServer)
        val hasInvokeCallback = AtomicBoolean(false)
        clientTask.addObserver(
            object : NettyConnectionObserver {
                override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {

                    if (nettyState is NettyTaskState.Error
                        || nettyState is NettyTaskState.ConnectionClosed
                        || getCurrentState() !is P2pConnectionState.Requesting) {
                        if (hasInvokeCallback.compareAndSet(false, true)) {
                            simpleCallback.onError(nettyState.toString())
                        }
                        task.removeObserver(this)
                        clientTask.stopTask()
                        log.e(TAG, "Connect $serverAddress fail: $nettyState, ${getCurrentState()}")
                        newState(P2pConnectionState.NoConnection)
                    } else {
                        if (nettyState is NettyTaskState.ConnectionActive) {
                            task.addObserver(activeCommunicationTaskObserver)
                            activeCommunicationNettyTask.set(clientTask)
                            if (hasInvokeCallback.compareAndSet(false, true)) {
                                simpleCallback.onSuccess(Unit)
                            }
                            newState(
                                P2pConnectionState.Active(
                                    localAddress = nettyState.channel.localAddress() as? InetSocketAddress,
                                    remoteAddress = nettyState.channel.remoteAddress() as? InetSocketAddress
                                )
                            )
                            task.removeObserver(this)
                            log.d(TAG, "Connection $serverAddress success.")
                            requestHandshake(clientTask)
                        }
                    }
                }
            }
        )
        clientTask.startTask()
    }

    fun requestTransferFile(simpleCallback: SimpleCallback<P2pConnectionState.Handshake>) {
        assertConnectionAndState(
            onSuccess = { activeConnection, handshake ->
                activeConnection.requestSimplify<Unit, Unit>(
                    type = P2pDataType.TransferFileReq.type,
                    request = Unit,
                    callback = object : IClientManager.RequestCallback<Unit> {

                        override fun onSuccess(
                            type: Int,
                            messageId: Long,
                            localAddress: InetSocketAddress?,
                            remoteAddress: InetSocketAddress?,
                            d: Unit
                        ) {
                            simpleCallback.onSuccess(handshake)
                            dispatchTransferFile(false)
                        }

                        override fun onFail(errorMsg: String) {
                            simpleCallback.onError(errorMsg)
                        }

                    }
                )
            },
            onError = {
                simpleCallback.onError(it)
            }
        )
    }

    fun requestClose(simpleCallback: SimpleCallback<Unit>) {
        assertConnectionAndState(
            onSuccess = { activeConnection, _ ->
                activeConnection.requestSimplify(
                    type = P2pDataType.CloseConnReq.type,
                    request = Unit,
                    callback = object : IClientManager.RequestCallback<Unit> {

                        override fun onSuccess(
                            type: Int,
                            messageId: Long,
                            localAddress: InetSocketAddress?,
                            remoteAddress: InetSocketAddress?,
                            d: Unit
                        ) {
                            simpleCallback.onSuccess(Unit)
                        }

                        override fun onFail(errorMsg: String) {
                            simpleCallback.onError(errorMsg)
                        }

                    }
                )
            },
            onError = { errorMsg ->
                simpleCallback.onError(errorMsg)
            }
        )
    }

    fun closeConnectionIfActive() {
        activeCommunicationNettyTask.get()?.let {
            it.stopTask()
            activeCommunicationNettyTask.set(null)
        }
        activeServerNettyTask.get()?.let {
            it.stopTask()
            activeServerNettyTask.set(null)
        }
        newState(P2pConnectionState.NoConnection)
        clearObserves()
    }

    private fun assertConnectionAndState(
        onSuccess: (activeConnection: ConnectionServerClientImpl, handshake: P2pConnectionState.Handshake) -> Unit,
        onError: (msg: String) -> Unit
    ) {
        val activeConnection = getActiveCommunicationTask()
        if (activeConnection == null) {
            onError("Active connection is null")
            return
        }
        val state = getCurrentState()
        if (state !is P2pConnectionState.Handshake) {
            onError("Current is not handshake: $state")
            return
        }
        onSuccess(activeConnection, state)
    }

    private fun requestHandshake(client: ConnectionServerClientImpl) {
        client.requestSimplify(
            type = P2pDataType.HandshakeReq.type,
            request = P2pHandshakeReq(
                version = TransferProtoConstant.VERSION,
                deviceName = currentDeviceName
            ),
            callback = object : IClientManager.RequestCallback<P2pHandshakeResp> {
                override fun onSuccess(
                    type: Int,
                    messageId: Long,
                    localAddress: InetSocketAddress?,
                    remoteAddress: InetSocketAddress?,
                    d: P2pHandshakeResp
                ) {
                    log.d(TAG, "Handshake request success: $d!!")
                    if (localAddress is InetSocketAddress
                        && remoteAddress is InetSocketAddress
                        && getCurrentState() is P2pConnectionState.Active) {
                        newState(P2pConnectionState.Handshake(
                            localAddress = localAddress,
                            remoteAddress = remoteAddress,
                            remoteDeviceName = d.deviceName
                        ))
                    } else {
                        closeConnectionIfActive()
                    }
                }

                override fun onFail(errorMsg: String) {
                    log.e(TAG, "Handshake request error: $errorMsg")
                    closeConnectionIfActive()
                }
            }
        )
    }

    override fun onNewState(s: P2pConnectionState) {
        for (o in observers) {
            o.onNewState(s)
        }
    }

    private fun dispatchTransferFile(isReceiver: Boolean) {
        val currentState = getCurrentState()
        if (currentState is P2pConnectionState.Handshake) {
            for (o in observers) {
                o.requestTransferFile(currentState, isReceiver)
            }
        }
    }

    private fun getActiveCommunicationTask(): ConnectionServerClientImpl? =
        activeCommunicationNettyTask.get()

    companion object {
        private const val TAG = "P2pConnection"
    }
}