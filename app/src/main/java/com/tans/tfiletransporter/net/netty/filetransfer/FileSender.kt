package com.tans.tfiletransporter.net.netty.filetransfer

import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.net.MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.net.netty.common.NettyPkg
import com.tans.tfiletransporter.net.netty.common.handler.writePkgBlockReply
import com.tans.tfiletransporter.net.netty.common.setDefaultHandler
import com.tans.tfiletransporter.utils.copyAvailableBytes
import com.tans.tfiletransporter.utils.readSuspendSize
import com.tans.tfiletransporter.utils.toLong
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.reactivex.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong


private val ioExecutor = Dispatchers.IO.asExecutor()

// 512KB
private const val WRITE_BUFFER_SIZE = 1024 * 256

typealias PathConverter = (file: File) -> Path
val defaultPathConverter: PathConverter = { file ->
    Paths.get(FileConstants.homePathString, file.path).let {
        if (file.size <= 0 || !Files.exists(it) || Files.isDirectory(it)) {
            error("Wrong File: $file")
        } else {
            it
        }
    }
}

// Server
fun sendFileObservable(
    fileMd5: FileMd5,
    localAddress: InetAddress,
    pathConverter: PathConverter = defaultPathConverter
): Observable<Long> {

    return Observable.create { emitter ->
        val fileData = fileMd5.file
        val md5 = fileMd5.md5
        val path: Path = pathConverter(fileData)
        val realFile = path.toFile()
        val sendProgress = AtomicLong(0L)
        val fileSize = fileData.size

        var channel: Channel? = null

        val connectionCancelObserver = LinkedBlockingDeque<ConnectionCancelObserver>()
        val sendState = AtomicBoolean(true)

        fun tryCancelConnection(notifyToClient: Boolean) {
            if (sendState.compareAndSet(true, false)) {
                if (sendProgress.get() >= fileSize) {
                    channel?.close()?.sync()
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                } else {
                    var hasSendToClient = false
                    for (o in connectionCancelObserver) {
                        if (notifyToClient) {
                            if (hasSendToClient) {
                                o(false)
                            } else {
                                hasSendToClient = o(true)
                            }
                        } else {
                            o(false)
                        }
                    }
                    channel?.close()?.sync()
                    if (!emitter.isDisposed) {
                        emitter.onError(Throwable("User cancel or error."))
                    }
                }
            }
        }

        fun emitterNextOrComplete() {
            if (sendState.get()) {
                val p = sendProgress.get()
                if (!emitter.isDisposed) {
                    emitter.onNext(p)
                }
                if (p >= fileSize) {
                    sendState.set(false)
                    channel?.close()?.sync()
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                }
            }
        }

        emitter.setCancellable {
            tryCancelConnection(true)
        }

        ioExecutor.execute {
            val connectionEventGroup = NioEventLoopGroup(1, ioExecutor)
            val rwEventGroup = NioEventLoopGroup(MAX_CLIENT_CONNECTIONS, ioExecutor)
            try {
                val serverBootstrap = ServerBootstrap()
                    .group(connectionEventGroup, rwEventGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childHandler(object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel?) {
                            ch?.pipeline()
                                ?.setDefaultHandler()
                                ?.addLast(object : ChannelDuplexHandler() {

                                    override fun channelRead(
                                        ctx: ChannelHandlerContext?,
                                        msg: Any?
                                    ) {
                                        if (ctx != null && msg is NettyPkg) {
                                            when (msg) {
                                                is NettyPkg.BytesPkg -> {
                                                    ioExecutor.execute {
                                                        val bytes = msg.bytes
                                                        if (bytes.size == 32) {
                                                            val co: ConnectionCancelObserver = { notifyToClient ->
                                                                if (ch.isActive) {
                                                                    if (notifyToClient) {
                                                                        ch.writeAndFlush(NettyPkg.ClientFinishPkg("Server cancel")).sync()
                                                                        ch.close()
                                                                        true
                                                                    } else {
                                                                        ch.close()
                                                                        false
                                                                    }
                                                                } else {
                                                                    false
                                                                }
                                                            }
                                                            connectionCancelObserver.add(co)
                                                            try {
                                                                val readMd5 = ByteArray(16)
                                                                System.arraycopy(bytes, 0, readMd5, 0, 16)
                                                                val startBytes = ByteArray(8)
                                                                System.arraycopy(bytes, 16, startBytes, 0, 8)
                                                                val endBytes = ByteArray(8)
                                                                System.arraycopy(bytes, 24, endBytes, 0, 8)
                                                                val start = startBytes.toLong()
                                                                val end = endBytes.toLong()
                                                                val localFrameSize = end - start
                                                                if (!readMd5.contentEquals(md5) || start < 0 || end < 0 || start >= end|| end > fileSize) {
                                                                    tryCancelConnection(true)
                                                                } else {
                                                                    val fileChannel = RandomAccessFile(realFile, "r").let {
                                                                        it.seek(start)
                                                                        it.channel
                                                                    }

                                                                    fileChannel.use {
                                                                        val byteBuffer = ByteBuffer.allocate(WRITE_BUFFER_SIZE)
                                                                        val bufferSize = WRITE_BUFFER_SIZE
                                                                        var hasSendSize = 0L
                                                                        while (true) {
                                                                            val thisTimeRead = if (bufferSize + hasSendSize >= localFrameSize) {
                                                                                (localFrameSize - hasSendSize).toInt()
                                                                            } else {
                                                                                bufferSize
                                                                            }
                                                                            runBlocking {
                                                                                fileChannel.readSuspendSize(byteBuffer, thisTimeRead)
                                                                            }
                                                                            if (ch.isActive) {
                                                                                val byteArray = byteBuffer.copyAvailableBytes()
                                                                                ctx.writePkgBlockReply(NettyPkg.BytesPkg(byteArray))
                                                                                hasSendSize += thisTimeRead
                                                                                sendProgress.addAndGet(thisTimeRead.toLong())
                                                                                emitterNextOrComplete()
                                                                                if (hasSendSize >= localFrameSize) {
                                                                                    break
                                                                                }
                                                                            } else {
                                                                                tryCancelConnection(true)
                                                                                break
                                                                            }
                                                                        }
                                                                        if (ch.isActive) {
                                                                            ctx.close()
                                                                        }
                                                                    }

                                                                }

                                                            } finally {
                                                                connectionCancelObserver.remove(co)
                                                            }
                                                        } else {
                                                            tryCancelConnection(true)
                                                        }
                                                    }
                                                }
                                                NettyPkg.TimeoutPkg, is NettyPkg.ServerFinishPkg -> {
                                                    tryCancelConnection(false)
                                                }
                                            }
                                        }
                                    }

                                    override fun exceptionCaught(
                                        ctx: ChannelHandlerContext?,
                                        cause: Throwable?
                                    ) {
                                        tryCancelConnection(false)
                                    }

                                })
                        }
                    })
                channel = serverBootstrap.bind(InetSocketAddress(localAddress, MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT)).sync().channel()
                channel?.closeFuture()?.sync()
            } finally {
                connectionEventGroup.shutdownGracefully()
                rwEventGroup.shutdownGracefully()
            }
        }
    }
}