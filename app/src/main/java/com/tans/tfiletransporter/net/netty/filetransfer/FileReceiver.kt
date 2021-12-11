package com.tans.tfiletransporter.net.netty.filetransfer

import com.tans.tfiletransporter.net.MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT
import com.tans.tfiletransporter.net.filetransporter.MULTI_CONNECTIONS_MAX
import com.tans.tfiletransporter.net.filetransporter.MULTI_CONNECTIONS_MIN_FRAME_SIZE
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.net.netty.common.NettyPkg
import com.tans.tfiletransporter.net.netty.common.handler.writePkg
import com.tans.tfiletransporter.net.netty.common.setDefaultHandler
import com.tans.tfiletransporter.utils.toBytes
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.reactivex.Observable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

private val ioExecutor = Dispatchers.IO.asExecutor()


private const val MAX_CLIENT_CONNECTIONS: Int = 15
// 10 MB
private const val MIN_CLIENT_FRAME_SIZE: Long = 1024 * 1024 * 10L

private typealias ConnectionCancelObserver = (notifyToServer: Boolean) -> Boolean

// Client
fun downloadFileObservable(
    fileMd5: FileMd5,
    serverAddress: InetAddress,
    saveFile: Path
): Observable<Long> {

    return Observable.create { emitter ->
        val fileData = fileMd5.file
        val fileMd5ByteArray = fileMd5.md5
        val fileSize = fileData.size
        val downloadProgress = AtomicLong(0L)

        val realFile = saveFile.toFile().apply { if (this.exists()) { delete() }; createNewFile() }
        val randomAccessFile = RandomAccessFile(realFile, "rw")
        randomAccessFile.use { randomAccessFile.setLength(fileSize) }

        val connectionCancelObserver = LinkedBlockingDeque<ConnectionCancelObserver>()

        val (frameSize: Long, frameCount: Int) = if (fileSize <= MIN_CLIENT_FRAME_SIZE * MAX_CLIENT_CONNECTIONS) {
            MIN_CLIENT_FRAME_SIZE to (fileSize / MIN_CLIENT_FRAME_SIZE).toInt() + if (fileSize % MIN_CLIENT_FRAME_SIZE != 0L) 1 else 0
        } else {
            val frameSize = fileSize / max(MAX_CLIENT_CONNECTIONS - 1, 1)
            frameSize to max((if (fileSize % frameSize > 0L) MAX_CLIENT_CONNECTIONS else MAX_CLIENT_CONNECTIONS - 1), 1)
        }
        val downloadState = AtomicBoolean(true)

        fun tryChancelConnection(notifyToServer: Boolean) {
            if (downloadState.compareAndSet(true, false)) {
                if (downloadProgress.get() >= fileSize) {
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                } else {
                    var hasSendToServer = false
                    for (o in connectionCancelObserver) {
                        if (notifyToServer) {
                            if (hasSendToServer) {
                                o(false)
                            } else {
                                hasSendToServer = o(true)
                            }
                        } else {
                            o(false)
                        }
                    }
                    if (realFile.exists()) {
                        realFile.delete()
                    }
                    if (!emitter.isDisposed) {
                        emitter.onError(Throwable("User cancel or error."))
                    }
                }
            }
        }

        fun emitterNextOrComplete() {
            if (downloadState.get()) {
                val p = downloadProgress.get()
                if (!emitter.isDisposed) {
                    emitter.onNext(p)
                }
                if (p >= fileSize) {
                    downloadState.set(false)
                    if (!emitter.isDisposed) {
                        emitter.onComplete()
                    }
                }
            }
        }

        emitter.setCancellable {
            tryChancelConnection(true)
        }


        fun downloadFrame(
            start: Long,
            end: Long
        ) {
            ioExecutor.execute {
                val currentFrameSize = end - start
                val localDownloadSize = AtomicLong(0)
                if (downloadState.get() && currentFrameSize > 0) {
                    val childEventLoopGroup = NioEventLoopGroup(MAX_CLIENT_CONNECTIONS, ioExecutor)
                    var c: Channel? = null
                    val co: ConnectionCancelObserver = { notifyServer ->
                        if (c?.isActive == true) {
                            if (notifyServer) {
                                c?.write(NettyPkg.ServerFinishPkg("Client cancel"))
                                c?.close()
                                true
                            } else {
                                c?.close()
                                false
                            }
                        } else {
                            false
                        }
                    }
                    try {
                        connectionCancelObserver.add(co)
                        val bootstrap = Bootstrap()
                            .group(childEventLoopGroup)
                            .channel(NioSocketChannel::class.java)
                            .option(ChannelOption.TCP_NODELAY, true)
                            .handler(object : ChannelInitializer<NioSocketChannel>() {
                                override fun initChannel(ch: NioSocketChannel?) {
                                    ch?.pipeline()
                                        ?.setDefaultHandler()
                                        ?.addLast(object : ChannelDuplexHandler() {
                                            override fun channelActive(ctx: ChannelHandlerContext?) {
                                                if (ctx != null) {
                                                    ioExecutor.execute {
                                                        val bytes = fileMd5ByteArray + start.toBytes() + end.toBytes()
                                                        ctx.writePkg(NettyPkg.BytesPkg(bytes))
                                                    }
                                                }
                                            }

                                            override fun channelRead(
                                                ctx: ChannelHandlerContext?,
                                                msg: Any?
                                            ) {
                                                if (msg is NettyPkg) {
                                                    when (msg) {
                                                        is NettyPkg.BytesPkg -> {
                                                            val randomWriteFile =
                                                                RandomAccessFile(realFile, "rw").apply {
                                                                    seek(start + localDownloadSize.get())
                                                                }
                                                            randomWriteFile.use {
                                                                val s = msg.bytes.size.toLong()
                                                                it.write(msg.bytes)
                                                                localDownloadSize.addAndGet(s)
                                                                downloadProgress.addAndGet(s)
                                                                emitterNextOrComplete()
                                                            }
                                                        }
                                                        NettyPkg.TimeoutPkg, is NettyPkg.ClientFinishPkg -> {
                                                            tryChancelConnection(false)
                                                        }
                                                    }
                                                }
                                            }
                                        })
                                }

                                override fun exceptionCaught(
                                    ctx: ChannelHandlerContext?,
                                    cause: Throwable?
                                ) {
                                    tryChancelConnection(false)
                                }
                            })
                        c = bootstrap.connect(InetSocketAddress(serverAddress, MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT)).sync().channel()
                        c?.closeFuture()?.sync()
                    } catch (t: Throwable) {
                        childEventLoopGroup.shutdownGracefully()
                        connectionCancelObserver.remove(co)
                    }

                }
            }
        }

        (0 until frameCount)
            .map { i ->
                val start = i * frameSize
                val end = if (start + frameSize > fileSize) {
                    fileSize
                } else {
                    start + frameSize
                }
                downloadFrame(start, end)
            }

    }
}