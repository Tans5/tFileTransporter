package com.tans.tfiletransporter.net.filetransporter

import android.os.Environment
import com.tans.tfiletransporter.net.MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.utils.newChildFile
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.reactivex.Observable
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max


fun fileDownloadsObservable(
    fileMd5: FileMd5,
    serverAddress: InetAddress
): Observable<Long> {

    return Observable.create { emitter ->
        val file = fileMd5.file
        val fileSize = file.size
        val downloadDir: Path by lazy {
            val result = Paths.get(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path, "tFileTransfer")
            if (!Files.exists(result)) {
                Files.createDirectory(result)
            }
            result
        }
        val path: Path by lazy { downloadDir.newChildFile(file.name) }
        val progressLong = AtomicLong(0L)


        // Init file and frame size.
        val randomAccessFile = RandomAccessFile(path.toFile(), "rw")
        randomAccessFile.use { randomAccessFile.setLength(fileSize) }
        val (frameSize: Long, frameCount: Int) = if (fileSize <= MULTI_CONNECTIONS_MIN_FRAME_SIZE * MULTI_CONNECTIONS_MAX) {
            MULTI_CONNECTIONS_MIN_FRAME_SIZE to (fileSize / MULTI_CONNECTIONS_MIN_FRAME_SIZE).toInt() + if (fileSize % MULTI_CONNECTIONS_MIN_FRAME_SIZE != 0L) 1 else 0
        } else {
            val frameSize = fileSize / max((MULTI_CONNECTIONS_MAX - 1), 1)
            frameSize to max((if (fileSize % frameSize > 0L) MULTI_CONNECTIONS_MAX else MULTI_CONNECTIONS_MAX - 1), 1)
        }

        val eventLoopGroup = NioEventLoopGroup(15)

        fun downloadFrame(
            start: Long,
            end: Long,
            error: (t: Throwable) -> Unit,
            progress: (downloaded: Long) -> Unit
        ): ChannelFuture {
            val allFrameSize = end - start
            var downloadedSize = 0L

            return Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel::class.java)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_RCVBUF, MULTI_CONNECTIONS_BUFFER_SIZE)
                .handler(object : ChannelInitializer<NioSocketChannel>() {
                    override fun initChannel(ch: NioSocketChannel?) {
                        if (ch == null) {
                            error(Throwable("Init channel fail"))
                        } else {

                            ch.pipeline().addLast(object : ChannelDuplexHandler() {
                                override fun channelActive(ctx: ChannelHandlerContext?) {
                                    if (ctx != null) {
                                        val buffer = PooledByteBufAllocator.DEFAULT.buffer()
                                        buffer.writeBytes(fileMd5.md5)
                                        buffer.writeLong(start)
                                        buffer.writeLong(end)
                                        ctx.writeAndFlush(buffer)
                                    }
                                }

                                override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                                    if (msg != null && msg is ByteBuf && downloadedSize < allFrameSize) {
                                        val fileChannel: FileChannel = RandomAccessFile(path.toFile(), "rw").let {
                                            it.seek(start + downloadedSize)
                                            it.channel
                                        }
                                        fileChannel.use {
                                            val size = msg.writerIndex() - msg.readerIndex()
                                            val nioBuffer = msg.nioBuffer(msg.readerIndex(), size)
                                            while (true) {
                                                val writeSize = it.write(nioBuffer)
                                                if (writeSize <= 0) {
                                                    break
                                                } else {
                                                    downloadedSize += writeSize
                                                }
                                            }
                                            msg.clear()
                                            progress(size.toLong())
                                            if (downloadedSize >= allFrameSize) {
                                                ctx?.close()
                                            }
                                        }
                                    }
                                }

                            })
                        }
                    }

                    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
                        ctx?.close()
                        error(cause ?: Throwable("Unknown error!!"))
                    }

                })
                .connect(InetSocketAddress(serverAddress, MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT))
                .addListener {
                   if (!it.isSuccess) { error.invoke(it.cause()) }
                }
        }

        val tasks = (0 until frameCount)
            .map { i ->
                val start = i * frameSize
                val end = if (start + frameSize > fileSize) {
                    fileSize
                } else {
                    start + frameSize
                }
                downloadFrame(
                    start = start,
                    end = end,
                    error = {
                        Files.delete(path)
                        eventLoopGroup.shutdownGracefully()
                        emitter.onError(it)
                    }) {
                    val size = progressLong.addAndGet(it)
                    emitter.onNext(size)
                    if (size >= fileSize) {
                        emitter.onComplete()
                    }
                }
            }

        emitter.setCancellable {
            for (task in tasks) {
                if (task.channel().isActive) {
                    task.channel().close()
                }
            }
            eventLoopGroup.shutdownGracefully()
            if (progressLong.get() < fileSize) {
                Files.delete(path)
            }
        }

    }
}