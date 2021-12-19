package com.tans.tfiletransporter.net.netty.fileexplore

import com.tans.tfiletransporter.moshi
import com.tans.tfiletransporter.net.FILE_TRANSPORT_LISTEN_PORT
import com.tans.tfiletransporter.net.model.*
import com.tans.tfiletransporter.net.netty.common.NettyPkg
import com.tans.tfiletransporter.net.netty.common.handler.writePkg
import com.tans.tfiletransporter.net.netty.common.handler.writePkgBlockReply
import com.tans.tfiletransporter.net.netty.common.setDefaultHandler
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

private val ioExecutor = Dispatchers.IO.asExecutor()

private const val FILE_EXPLORE_VERSION = 2

private inline fun <reified T> T.toBaseJsonString(): String {
    val content = moshi.adapter(T::class.java).toJson(this)
    val type = when (T::class.java) {
        FileExploreHandshakeModel::class.java -> FILE_MODEL_TYPE_HANDSHAKE
        else -> 0
    }
    val model = FileExploreBaseModel(
        type = type,
        content = content
    )
    return moshi.adapter(FileExploreBaseModel::class.java).toJson(model)
}

private fun String.toFileContentModel(): FileExploreContentModel? {
    val baseModel = moshi.adapter(FileExploreBaseModel::class.java).fromJson(this)
    return if (baseModel != null) {
        when (baseModel.type) {
            FILE_MODEL_TYPE_HANDSHAKE -> {
                moshi.adapter(FileExploreHandshakeModel::class.java).fromJson(baseModel.content)
            }
            else -> null
        }
    } else {
        null
    }
}

fun startFileExploreServer(localAddress: InetAddress): FileExploreConnection {
    var serverChannel: Channel? = null
    var clientChannel: Channel? = null
    val connection = FileExploreConnection(
        closeConnection = { notifyRemote ->
            if (notifyRemote && clientChannel?.isActive == true) {
                clientChannel?.writePkg(NettyPkg.ClientFinishPkg("Close"))
            }
            clientChannel?.close()
            serverChannel?.close()
        }
    )
    ioExecutor.execute {
        val clientConnected = AtomicBoolean(false)
        val connectionEventGroup = NioEventLoopGroup(1, ioExecutor)
        val rwEventGroup = NioEventLoopGroup(1, ioExecutor)
        try {
            val server = ServerBootstrap()
                .group(connectionEventGroup, rwEventGroup)
                .channel(NioServerSocketChannel::class.java)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel?) {
                        if (ch != null && clientConnected.compareAndSet(false, true)) {
                            ch.pipeline()
                                .setDefaultHandler()
                                .addLast(object : ChannelDuplexHandler() {
                                    override fun channelActive(ctx: ChannelHandlerContext?) {
                                        super.channelActive(ctx)
                                        val handshakeModel = FileExploreHandshakeModel(
                                            version = FILE_EXPLORE_VERSION,
                                            pathSeparator = File.separator
                                        )
                                        ctx?.channel()?.writePkg(NettyPkg.JsonPkg(json = handshakeModel.toBaseJsonString()))
                                    }

                                    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                                        super.channelRead(ctx, msg)
                                        if (msg != null && msg is NettyPkg && ctx != null) {
                                            when (msg) {
                                                is NettyPkg.JsonPkg -> {
                                                    when (val model = msg.json.toFileContentModel()) {
                                                        is FileExploreHandshakeModel -> {
                                                            connection.connectionActive(model)
                                                            clientChannel = ctx.channel()
                                                        }
                                                    }
                                                }
                                                is NettyPkg.ServerFinishPkg, is NettyPkg.TimeoutPkg -> {
                                                    connection.close(false)
                                                }
                                                else -> {
                                                }
                                            }
                                        }
                                    }

                                    override fun channelInactive(ctx: ChannelHandlerContext?) {
                                        super.channelInactive(ctx)
                                        connection.close(false)
                                    }

                                    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                                        connection.close(false)
                                    }
                                })
                        } else {
                            ch?.close()
                        }
                    }
                })
            val channel = server.bind(InetSocketAddress(localAddress, FILE_TRANSPORT_LISTEN_PORT)).sync().channel()
            serverChannel = channel
            channel.closeFuture().sync()
        } finally {
            connectionEventGroup.shutdownGracefully()
            rwEventGroup.shutdownGracefully()
        }
    }
    return connection
}

fun connectionFileExploreServer(remoteAddress: InetAddress): FileExploreConnection {
    var channel: Channel? = null
    val connection = FileExploreConnection(
        closeConnection = { notifyRemote ->
            if (notifyRemote && channel?.isActive == true) {
                channel?.writePkg(NettyPkg.ServerFinishPkg("Close"))
            }
            channel?.close()
        }
    )
    ioExecutor.execute {
        val eventGroup = NioEventLoopGroup(1, ioExecutor)
        try {
            val client = Bootstrap()
                .group(eventGroup)
                .channel(NioSocketChannel::class.java)
                .handler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel?) {
                        ch?.pipeline()
                            ?.setDefaultHandler()
                            ?.addLast(object : ChannelDuplexHandler() {

                                override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                                    if (msg != null && msg is NettyPkg && ctx != null) {
                                        when (msg) {
                                            is NettyPkg.JsonPkg -> {
                                                when (val model = msg.json.toFileContentModel()) {
                                                    is FileExploreHandshakeModel -> {
                                                        val handshakeModel = FileExploreHandshakeModel(
                                                            version = FILE_EXPLORE_VERSION,
                                                            pathSeparator = File.pathSeparator
                                                        )
                                                        ctx.channel().writePkgBlockReply(NettyPkg.JsonPkg(handshakeModel.toBaseJsonString()))
                                                        connection.connectionActive(model)
                                                    }
                                                }
                                            }
                                            is NettyPkg.ClientFinishPkg, is NettyPkg.TimeoutPkg -> {
                                               connection.close(false)
                                            }
                                            else -> {}
                                        }
                                    }
                                }

                                override fun channelInactive(ctx: ChannelHandlerContext?) {
                                    super.channelInactive(ctx)
                                    connection.close(false)
                                }
                                override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                                    connection.close(false)
                                }
                            })
                    }
                })

            val channelLocal = client.connect(InetSocketAddress(remoteAddress, FILE_TRANSPORT_LISTEN_PORT)).sync().channel()
            channel = channelLocal
            channelLocal.closeFuture().sync()
        } finally {
            eventGroup.shutdownGracefully()
        }
    }
    return connection
}