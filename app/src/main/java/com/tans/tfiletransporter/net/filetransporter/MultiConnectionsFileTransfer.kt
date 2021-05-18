package com.tans.tfiletransporter.net.filetransporter

import android.os.Environment
import android.util.Log
import com.tans.tfiletransporter.core.Stateable
import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.net.MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT
import com.tans.tfiletransporter.net.NetBufferPool
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.rx2.await
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

// 512KB
const val MULTI_CONNECTIONS_BUFFER_SIZE: Int = 1024 * 512
const val MULTI_CONNECTIONS_MAX: Int = 30
// 10 MB
const val MULTI_CONNECTIONS_MIN_FRAME_SIZE: Long = 1024 * 1024 * 10
const val MULTI_CONNECTIONS_MAX_SERVER_ERROR_TIMES = 5

private val fileTransporterPool = NetBufferPool(
        poolSize = MULTI_CONNECTIONS_MAX * 2,
        bufferSize = MULTI_CONNECTIONS_BUFFER_SIZE
)

@Throws(IOException::class)
suspend fun startMultiConnectionsFileServer(
        fileMd5: FileMd5,
        localAddress: InetAddress,
        pathConverter: PathConverter = defaultPathConverter,
        serverInstance: suspend (server: MultiConnectionsFileServer) -> Unit = {},
        progress: suspend (hasSend: Long, size: Long) -> Unit = { _, _ -> }
) {
    val server = MultiConnectionsFileServer(fileMd5 = fileMd5, localAddress = localAddress, progress = progress, pathConverter = pathConverter)
    serverInstance(server)
    server.start()
}

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

class MultiConnectionsFileServer(
        fileMd5: FileMd5,
        private val localAddress: InetAddress,
        pathConverter: PathConverter = defaultPathConverter,
        private val progress: suspend (hasSend: Long, size: Long) -> Unit = { _, _ -> }
) {

    private val file = fileMd5.file
    private val md5 = fileMd5.md5
    private val path: Path = pathConverter(file)

    private val ssc: AsynchronousServerSocketChannel by lazy { AsynchronousServerSocketChannel.open() }

    private val progressLong = AtomicLong(0L)

    private val finishCheckChannel = Channel<Unit>(1)

    var sendingCoroutineScope: CoroutineScope? = null

    internal suspend fun start() = coroutineScope {
        sendingCoroutineScope = this
        ssc.use {
            ssc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
            ssc.bindSuspend(InetSocketAddress(localAddress, MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT), MULTI_CONNECTIONS_MAX)
            val job = launch(Dispatchers.IO) {
                val errorTimes = AtomicInteger(0)
                while (true) {
                    val connectResult = kotlin.runCatching {
                        ssc.acceptSuspend()
                    }
                    if (connectResult.isSuccess) {
                        launch(Dispatchers.IO) {
                            val result = kotlin.runCatching {
                                val client = connectResult.getOrThrow()
                                client.use {
                                    newClient(client)
                                }
                            }
                            if (result.isFailure) {
                                Log.e("startMultiConnectionsFileServer", "startMultiConnectionsFileServer", result.exceptionOrNull())
                                if (errorTimes.addAndGet(1) >= MULTI_CONNECTIONS_MAX_SERVER_ERROR_TIMES) {
                                    throw result.exceptionOrNull()!!
                                }
                            }
                        }
                    } else {
                        connectResult.exceptionOrNull()?.printStackTrace()
                        if (progressLong.get() >= file.size || !isActive || !ssc.isOpen) {
                            break
                        }
                    }
                }
            }
            finishCheckChannel.receive()
            job.cancel("File: ${file.name}, Download Finish")
        }
    }

    suspend fun cancel() {
        sendingCoroutineScope?.cancel("Canceled by user.")
        if (ssc.isOpen) ssc.close()
    }

    /**
     * Read Sequence:
     * 1. File's MD5 16 bytes.
     * 2. File's frame start 8 bytes.
     * 3. File's frame end 8 bytes.
     * Write:
     * File's frame.
     */
    private suspend fun newClient(client: AsynchronousSocketChannel) {
        val buffer = fileTransporterPool.requestBuffer()
        var hasRead: Long = 0
        val result = kotlin.runCatching {
            client.readSuspendSize(buffer, 16)
            val remoteMd5 = buffer.copyAvailableBytes()
            if (!md5.contentEquals(remoteMd5)) {
                client.close()
                return
            }
            client.readSuspendSize(buffer, 8)
            val start = buffer.asLongBuffer().get()
            client.readSuspendSize(buffer, 8)
            val end = buffer.asLongBuffer().get()
            if (start >= end || end > file.size) {
                client.close()
                return
            }
            val limitReadSize = end - start
            var offset: Long = start
            val bufferSize = buffer.capacity()
            val file = RandomAccessFile(path.toFile(), "r")
            file.seek(start)
            val fileChannel = file.channel
            file.use {
                fileChannel.use {
                    // val fileLock = fileChannel.lock(start, limitReadSize, true)
                    while (true) {
                        val thisTimeRead = if (bufferSize + hasRead >= limitReadSize) {
                            (limitReadSize - hasRead).toInt()
                        } else {
                            bufferSize
                        }
                        fileChannel.readSuspendSize(buffer, thisTimeRead)
                        client.writeSuspendSize(buffer)
                        hasRead += thisTimeRead
                        offset += thisTimeRead
                        val allSend = progressLong.addAndGet(thisTimeRead.toLong())
                        progress(allSend, this.file.size)
                        if (allSend >= this.file.size) {
                            finishCheckChannel.send(Unit)
                        }
                        if (hasRead >= limitReadSize) {
                            break
                        }
                    }
                    // fileLock.release()
                }
            }
        }
        fileTransporterPool.recycleBuffer(buffer)
        if (result.isFailure) {
            progressLong.set(progressLong.get() - hasRead)
            throw result.exceptionOrNull()!!
        }
    }
}



@Throws(IOException::class)
suspend fun startMultiConnectionsFileClient(
        fileMd5: FileMd5,
        serverAddress: InetAddress,
        clientInstance: suspend (client: MultiConnectionsFileTransferClient) -> Unit = {},
        progress: suspend (hasSend: Long, size: Long) -> Unit = { _, _ -> }
) {
    val client = MultiConnectionsFileTransferClient(fileMd5, serverAddress, progress)
    clientInstance(client)
    client.start()
}


class MultiConnectionsFileTransferClient(
        fileMd5: FileMd5,
        private val serverAddress: InetAddress,
        val progress: suspend (hasSend: Long, size: Long) -> Unit = { _, _ -> }
): Stateable<List<Job>> by Stateable(emptyList()) {
    private val file = fileMd5.file
    private val md5 = fileMd5.md5
    private val fileSize = file.size
    private val downloadDir: Path by lazy {
        val result = Paths.get(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path, "tFileTransfer")
        if (!Files.exists(result)) {
            Files.createDirectory(result)
        }
        result
    }
    private val path: Path by lazy { downloadDir.newChildFile(file.name) }
    private val progressLong = AtomicLong(0L)

    internal suspend fun start() =
            coroutineScope {
                val file = RandomAccessFile(path.toFile(), "rw")
                file.use { file.setLength(fileSize) }
                val (frameSize: Long, frameCount: Int) = if (fileSize <= MULTI_CONNECTIONS_MIN_FRAME_SIZE * MULTI_CONNECTIONS_MAX) {
                    MULTI_CONNECTIONS_MIN_FRAME_SIZE to (fileSize / MULTI_CONNECTIONS_MIN_FRAME_SIZE).toInt() + if (fileSize % MULTI_CONNECTIONS_MIN_FRAME_SIZE != 0L) 1 else 0
                } else {
                    val frameSize = fileSize / max((MULTI_CONNECTIONS_MAX - 1), 1)
                    frameSize to max((if (fileSize % frameSize > 0L) MULTI_CONNECTIONS_MAX else MULTI_CONNECTIONS_MAX - 1), 1)
                }
                val result = kotlin.runCatching {
                    for (i in 0 until frameCount) {
                        val start = i * frameSize
                        if (start >= fileSize) {
                            break
                        }
                        val end = if (start + frameSize > fileSize) {
                            fileSize
                        } else {
                            start + frameSize
                        }
                        val job = launch(Dispatchers.IO) { downloadFrame(start, end) }
                        updateState { it + job }.await()
                    }
                }
                if (result.isFailure) {
                    Files.delete(path)
                }

            }

    /**
     * Write Sequence:
     * 1. File's MD5 16 bytes.
     * 2. File's frame start 8 bytes.
     * 3. File's frame end 8 bytes.
     * Read:
     * File's frame.
     */
    private suspend fun downloadFrame(start: Long, end: Long, retry: Boolean = true) {
        val buffer = fileTransporterPool.requestBuffer()
        var hasRead: Long = 0
        val result = kotlin.runCatching {
            val sc = openAsynchronousSocketChannel()
            sc.use {
                sc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                sc.setOptionSuspend(StandardSocketOptions.TCP_NODELAY, true)
                sc.setOptionSuspend(StandardSocketOptions.SO_KEEPALIVE, true)
                sc.connectSuspend(InetSocketAddress(serverAddress, MULTI_CONNECTIONS_FILES_TRANSFER_LISTEN_PORT))
                sc.writeSuspendSize(buffer, md5)
                sc.writeSuspendSize(buffer, start.toBytes())
                sc.writeSuspendSize(buffer, end.toBytes())
                val limitReadSize = end - start
                var offset: Long = start
                val bufferSize = buffer.capacity()
                val file = RandomAccessFile(path.toFile(), "rw")
                file.seek(start)
                val fileChannel = file.channel
                file.use {
                    fileChannel.use {
                        val fileLock = fileChannel.lock(start, limitReadSize, true)
                        while (true) {
                            val thisTimeRead = if (bufferSize + hasRead >= limitReadSize) {
                                (limitReadSize - hasRead).toInt()
                            } else {
                                bufferSize
                            }
                            sc.readSuspendSize(buffer, thisTimeRead)
                            fileChannel.writeSuspendSize(buffer)
                            offset += thisTimeRead
                            hasRead += thisTimeRead
                            progress(progressLong.addAndGet(thisTimeRead.toLong()), fileSize)
                            if (hasRead >= limitReadSize) {
                                break
                            }
                        }
                        // fileLock.release()
                    }
                }
            }
        }
        fileTransporterPool.recycleBuffer(buffer)
        if (result.isFailure) {
            progressLong.set(progressLong.get() - hasRead)
            if (retry) {
                Log.e("startMultiConnectionsFileClient", "startMultiConnectionsFileClient", result.exceptionOrNull())
                delay(500)
                downloadFrame(start, end, false)
            } else {
                throw result.exceptionOrNull()!!
            }
        }
    }

    suspend fun cancel() {
        val jobs = bindState().firstOrError().await()
        for (job in jobs) {
            if (job.isActive) {
                job.cancel("Cancel by handle.")
            }
        }
        if (Files.exists(path)) { Files.delete(path) }
    }

}