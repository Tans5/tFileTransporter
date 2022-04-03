package com.tans.tfiletransporter.net.netty.fileexplore

import android.os.Build
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

private inline fun <reified T> T.toFileExploreBaseJsonString(): String {
    val content = moshi.adapter(T::class.java).toJson(this)
    val type = when (T::class.java) {
        FileExploreHandshakeModel::class.java -> FILE_MODEL_TYPE_HANDSHAKE
        RequestFolderModel::class.java -> FILE_MODEL_TYPE_REQUEST_FOLDER
        ShareFolderModel::class.java -> FILE_MODEL_TYPE_SHARE_FOLDER
        RequestFilesModel::class.java -> FILE_MODEL_TYPE_REQUEST_FILES
        ShareFilesModel::class.java -> FILE_MODEL_TYPE_SHARE_FILES
        MessageModel::class.java -> FILE_MODEL_TYPE_MESSAGE
        else -> 0
    }
    val model = FileExploreBaseModel(
        type = type,
        content = content
    )
    return moshi.adapter(FileExploreBaseModel::class.java).toJson(model)
}

inline fun <reified T> String.fromJson(): T? {
    return moshi.adapter(T::class.java).fromJson(this)
}

inline fun <reified T> T.toJson(): String? {
    return moshi.adapter(T::class.java).toJson(this)
}

private fun String.toFileContentModel(): FileExploreContentModel? {
    val baseModel = moshi.adapter(FileExploreBaseModel::class.java).fromJson(this)
    return if (baseModel != null) {
        when (baseModel.type) {
            FILE_MODEL_TYPE_HANDSHAKE -> {
                baseModel.content.fromJson<FileExploreHandshakeModel>()
            }
            FILE_MODEL_TYPE_REQUEST_FOLDER -> {
                baseModel.content.fromJson<RequestFolderModel>()
            }
            FILE_MODEL_TYPE_SHARE_FOLDER-> {
                baseModel.content.fromJson<ShareFolderModel>()
            }
            FILE_MODEL_TYPE_REQUEST_FILES -> {
                baseModel.content.fromJson<RequestFilesModel>()
            }
            FILE_MODEL_TYPE_SHARE_FILES -> {
                baseModel.content.fromJson<ShareFilesModel>()
            }
            FILE_MODEL_TYPE_MESSAGE -> {
                baseModel.content.fromJson<MessageModel>()
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
        },
        sendFileExploreContent = { content, wait ->
            val channel = clientChannel
            if (channel?.isActive == true) {
                val msg = NettyPkg.JsonPkg(json = content.toFileExploreBaseJsonString())
                if (wait) {
                    channel.writePkgBlockReply(msg)
                } else {
                    channel.writePkg(msg)
                }
            }
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
                                            pathSeparator = File.separator,
                                            deviceName = "${Build.BRAND} ${Build.MODEL}"
                                        )
                                        ctx?.channel()?.writePkg(NettyPkg.JsonPkg(json = handshakeModel.toFileExploreBaseJsonString()))
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
                                                        else -> {
                                                            if (model != null) {
                                                                connection.newRemoteFileExploreContent(model)
                                                            }
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

fun connectToFileExploreServer(remoteAddress: InetAddress): FileExploreConnection {
    var channel: Channel? = null
    val connection = FileExploreConnection(
        closeConnection = { notifyRemote ->
            if (notifyRemote && channel?.isActive == true) {
                channel?.writePkg(NettyPkg.ServerFinishPkg("Close"))
            }
            channel?.close()
        },
        sendFileExploreContent = { content, wait ->
            val channelLocal = channel
            if (channelLocal?.isActive == true) {
                val msg = NettyPkg.JsonPkg(json = content.toFileExploreBaseJsonString())
                if (wait) {
                    channelLocal.writePkgBlockReply(msg)
                } else {
                    channelLocal.writePkg(msg)
                }
            }
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
                                                            pathSeparator = File.separator,
                                                            deviceName = "${Build.BRAND} ${Build.MODEL}"
                                                        )
                                                        ctx.channel().writePkgBlockReply(NettyPkg.JsonPkg(handshakeModel.toFileExploreBaseJsonString()))
                                                        connection.connectionActive(model)
                                                    }
                                                    else -> {
                                                        if (model != null) {
                                                            connection.newRemoteFileExploreContent(model)
                                                        }
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