package com.tans.tfiletransporter.netty.extensions

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

class DefaultServerManager(
    val connectionTask: INettyConnectionTask,
    private val converterFactory: IConverterFactory = DefaultConverterFactory(),
    private val log: ILog
) : IServerManager, NettyConnectionObserver {

    init {
        connectionTask.addObserver(this)
    }

    private val servers: LinkedBlockingDeque<IServer<*, *>> by lazy {
        LinkedBlockingDeque()
    }

    private val handledMessageId: ConcurrentHashMap<Long, Unit> by lazy {
        ConcurrentHashMap()
    }

    override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {}

    // 收到 client 发送的请求数据
    override fun onNewMessage(
        localAddress: InetSocketAddress?,
        remoteAddress: InetSocketAddress?,
        msg: PackageData,
        task: INettyConnectionTask
    ) {
        // 找一个 server 来处理这种类型的数据
        val server = servers.find { it.couldHandle(msg.type) }
        if (server != null) {
            // 是否已经处理过这个 message
            val isNew = !handledMessageId.containsKey(msg.messageId)
            handledMessageId[msg.messageId] = Unit
            // 交给 server 处理
            server.dispatchRequest(
                localAddress = localAddress,
                remoteAddress = remoteAddress,
                msg = msg,
                converterFactory = converterFactory,
                connectionTask = task,
                isNewRequest = isNew
            )
        }
    }

    override fun <Request, Response> registerServer(s: IServer<Request, Response>) {
        servers.add(s)
    }

    override fun <Request, Response> unregisterServer(s: IServer<Request, Response>) {
        servers.remove(s)
    }

    override fun clearAllServers() {
        servers.clear()
    }

    companion object {
        private const val TAG = "DefaultServerManager"
    }
}