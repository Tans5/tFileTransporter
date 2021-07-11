package com.tans.tfiletransporter.net.filetransporter

import android.os.Environment
import com.tans.tfiletransporter.logs.Log
import com.tans.tfiletransporter.net.MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.utils.newChildFile
import com.tans.tfiletransporter.utils.readSuspendSize
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.FixedLengthFrameDecoder
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.codec.LineBasedFrameDecoder
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.runBlocking
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min


// Client
fun downloadFileObservable(
    fileMd5: FileMd5,
    serverAddress: InetAddress
): Observable<Long> {

    return Observable.create { emitter ->
        val file = fileMd5.file
        val fileSize = file.size
        val fileInfoMsg = "File -> ${file.name}, Size -> $fileSize"
        val logTag = "FileDownload"
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

        Log.d(tag = logTag, msg = "$fileInfoMsg; FrameSize: $frameSize, FrameCount: $frameCount")

        val childEventLoopGroup = NioEventLoopGroup()

        fun downloadFrame(
            start: Long,
            end: Long,
            error: (t: Throwable) -> Unit,
            progress: (downloaded: Long) -> Unit
        ): ChannelFuture {
            val allFrameSize = end - start
            val frameDownloadedSize = AtomicLong(0)

            return Bootstrap()
                .group(childEventLoopGroup)
                .channel(NioSocketChannel::class.java)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_RCVBUF, MULTI_CONNECTIONS_BUFFER_SIZE)
                .handler(object : ChannelInitializer<NioSocketChannel>() {
                    override fun initChannel(ch: NioSocketChannel?) {
                        if (ch == null) {
                            error(Throwable("Init channel fail"))
                        } else {

                            ch.pipeline()
                                .addLast(object : ChannelInboundHandlerAdapter() {
                                override fun channelActive(ctx: ChannelHandlerContext?) {
                                    Schedulers.io().createWorker().schedule {
                                        Log.d(logTag, "$fileInfoMsg; Start Download Frame: Start -> $start, End -> $end")

                                        val buffer = PooledByteBufAllocator.DEFAULT.buffer()
                                        try {
                                            buffer.retain()
                                            buffer.writeBytes(fileMd5.md5)
                                            buffer.writeLong(start)
                                            buffer.writeLong(end)
                                            ctx?.writeAndFlush(buffer)?.sync()
                                        } catch (t: Throwable) {
                                            error(t)
                                        }
                                    }
                                }

                                override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                                    if (ctx != null && msg != null && msg is ByteBuf && frameDownloadedSize.get() < allFrameSize) {
                                        Schedulers.io().createWorker().schedule {
                                            try {
                                                val fileChannel: FileChannel = RandomAccessFile(path.toFile(), "rw").let {
                                                    it.seek(start + frameDownloadedSize.get())
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
                                                            frameDownloadedSize.getAndAdd(writeSize.toLong())
                                                        }
                                                    }
                                                    progress(size.toLong())
                                                    Log.d(logTag, "$fileInfoMsg; Download Frame: Start -> $start, End -> $end, Process -> ${frameDownloadedSize.get()}/$allFrameSize}", )
                                                }
                                            } catch (t: Throwable) {
                                                error(t)
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
                        if (!emitter.isDisposed) {
                            emitter.onError(it)
                        }
                        Log.e(logTag, "$fileInfoMsg; Download error", it)
                    }) {
                    val size = progressLong.addAndGet(it)
                    if (!emitter.isDisposed) {
                        emitter.onNext(size)
                    }
                    Log.d(logTag, "$fileInfoMsg; $size/$fileSize")
                    if (size >= fileSize) {
                        Log.d(logTag, "$fileInfoMsg; Download Finish..")
                        emitter.onComplete()
                    }
                }
            }

        emitter.setCancellable {
            for (task in tasks) {
                if (task.channel().isActive) {
                    task.await().channel().close()
                    task.cancel(false)
                }
            }
            childEventLoopGroup.shutdownGracefully()
            if (progressLong.get() < fileSize) {
                Files.delete(path)
            }

        }

    }
}


// Server
fun sendFileObservable(
    fileMd5: FileMd5,
    localAddress: InetAddress,
    pathConverter: PathConverter = defaultPathConverter
): Observable<Long> {

    val fileInfoMsg = "File -> ${fileMd5.file.name}, Size -> ${fileMd5.file.size}"
    val logTag = "FileSend"

    return Observable.create { emitter ->
        val file = fileMd5.file
        val md5 = fileMd5.md5
        val path: Path = pathConverter(file)
        val progressLong = AtomicLong(0L)
        val fileSize = file.size
        val childEventLoopGroup = NioEventLoopGroup()
        val parentEventLoopGroup = NioEventLoopGroup()

        val channel = ServerBootstrap()
            .group(parentEventLoopGroup, childEventLoopGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_REUSEADDR, true)
            .option(ChannelOption.SO_BACKLOG, MULTI_CONNECTIONS_MAX)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.SO_RCVBUF, MULTI_CONNECTIONS_BUFFER_SIZE)
            .childHandler(object : ChannelInitializer<NioSocketChannel>() {

                override fun initChannel(ch: NioSocketChannel?) {
                    ch?.pipeline()
                        ?.addLast(FixedLengthFrameDecoder(32))
                        ?.addLast(FrameInHandler())
                        ?.addLast(
                        object : ChannelInboundHandlerAdapter() {
                            // 1. 16 bytes md5. 2. 8 bytes start index. 3. 8 bytes end index.
                            override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                                    if (msg != null && msg is FileFrameData && ctx != null) {
                                        Schedulers.io().createWorker().schedule {
                                            try {
                                                Log.d(logTag, "$fileInfoMsg; SendFile: Start -> ${msg.start}, End -> ${msg.end}")
                                                if (!msg.fileMd5.contentEquals(md5) || msg.start < 0 || msg.end < 0 || msg.start >= msg.end || msg.end > fileSize) {
                                                    emitter.onError(Throwable("Wrong frame data: $msg"))
                                                    return@schedule
                                                }
                                                val start = msg.start
                                                val end = msg.end
                                                val frameSize = end - start

                                                val fileChannel = RandomAccessFile(path.toFile(), "r").let {
                                                    it.seek(start)
                                                    it.channel
                                                }
                                                val bioBuffer = fileTransporterPool.requestBufferBlock()
                                                val bufferSize = bioBuffer.capacity()
                                                val buffer = PooledByteBufAllocator.DEFAULT.buffer(bufferSize)
                                                try {
                                                    var hasSendSize = 0L
                                                    fileChannel.use {
                                                        while (true) {
                                                            val thisTimeRead = if (bufferSize + hasSendSize >= frameSize) {
                                                                (frameSize - hasSendSize).toInt()
                                                            } else {
                                                                bufferSize
                                                            }
                                                            hasSendSize += thisTimeRead
                                                            runBlocking {
                                                                fileChannel.readSuspendSize(bioBuffer, thisTimeRead)
                                                            }
                                                            buffer.retain()
                                                            buffer.writeBytes(bioBuffer)
                                                            ctx.writeAndFlush(buffer).await()
                                                            val progress = progressLong.addAndGet(thisTimeRead.toLong())
                                                            Log.d(logTag, "$fileInfoMsg; SendFile: $progress/$fileSize")
                                                            emitter.onNext(progress)
                                                            if (progress >= fileSize) {
                                                                emitter.onComplete()
                                                            }
                                                            bioBuffer.clear()
                                                            buffer.clear()
                                                        }
                                                    }

                                                } finally {
                                                    fileTransporterPool.recycleBufferBlock(bioBuffer)
                                                }
                                            } catch (e: Throwable) {
                                                e.printStackTrace()
                                                Log.e(logTag, "SendFileError", e)
                                                if (!emitter.isDisposed) {
                                                    emitter.onError(e)
                                                }
                                            }

                                        }
                                    }
                            }

                            override fun exceptionCaught(
                                ctx: ChannelHandlerContext?,
                                cause: Throwable?
                            ) {
                                if (cause != null && !emitter.isDisposed) {
                                    emitter.onError(cause)
                                }
                            }
                        })
                }
            })
            .bind(InetSocketAddress(localAddress, MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT))
            .addListener {
                if (!it.isSuccess && !emitter.isDisposed) {
                    emitter.onError(it.cause())
                }
            }

        emitter.setCancellable {
            channel.sync().cancel(true)
            parentEventLoopGroup.shutdownGracefully()
            childEventLoopGroup.shutdownGracefully()
        }

    }
}

private data class FileFrameData(
    val fileMd5: ByteArray,
    val start: Long,
    val end: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileFrameData

        if (!fileMd5.contentEquals(other.fileMd5)) return false
        if (start != other.start) return false
        if (end != other.end) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileMd5.contentHashCode()
        result = 31 * result + start.hashCode()
        result = 31 * result + end.hashCode()
        return result
    }
}

class FrameInHandler : ChannelInboundHandlerAdapter() {


    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        if (ctx != null && msg != null && msg is ByteBuf) {
            Log.d("FileSend", "Header Size: ${msg.readSize()}")
            val md5 = ByteArray(16)
            msg.readBytes(md5)
            val start = msg.readLong()
            val end = msg.readLong()
            ctx.fireChannelRead(FileFrameData(
                fileMd5 = md5,
                start = start,
                end = end
            ))
        }
    }
}

private inline fun ByteBuf.readSize(): Int = writerIndex() - readerIndex()

