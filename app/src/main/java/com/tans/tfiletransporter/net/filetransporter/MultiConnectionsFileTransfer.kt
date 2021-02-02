package com.tans.tfiletransporter.net.filetransporter

import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong
import kotlin.jvm.Throws

// 1 MB
private const val MULTI_CONNECTIONS_BUFFER_SIZE: Int = 1024 * 1024
private const val MULTI_CONNECTIONS_MAX: Int = 100
// 10 MB
private const val MULTI_CONNECTIONS_MIN_FRAME_SIZE = 1024 * 1024 * 10
private const val MULTI_CONNECTIONS_FILES_TRANSFER_PORT = 6669


class MultiConnectionsFileTransferServer(
    private val fileMd5: FileMd5,
    private val localAddress: InetAddress,
    val progress: suspend (hasSend: Long, size: Long) -> Unit = { _, _ -> }
) {

    @Throws(IOException::class)
    suspend fun startServer() = coroutineScope {
        val md5 = fileMd5.md5
        val file = fileMd5.file
        val finishCheckChannel = Channel<Unit>(1)
        val path = Paths.get(FileConstants.homePathString, file.path)
        if (file.size <= 0 || !Files.exists(path) || Files.isDirectory(path)) {
            return@coroutineScope
        }
        val ssc = openAsynchronousServerSocketChannelSuspend()
        ssc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
        ssc.bindSuspend(InetSocketAddress(localAddress, MULTI_CONNECTIONS_FILES_TRANSFER_PORT), MULTI_CONNECTIONS_MAX)
        val fileData = RandomAccessFile(path.toFile(), "r")
        val progress = AtomicLong(0L)

        suspend fun newClient(client: AsynchronousSocketChannel) = client.use {
            val buffer = ByteBuffer.allocate(MULTI_CONNECTIONS_BUFFER_SIZE)
            client.readSuspendSize(buffer, 16)
            val remoteMd5 = buffer.copyAvailableBytes()
            if (!md5.contentEquals(remoteMd5)) {
                client.close()
            }
            client.readSuspendSize(buffer, 8)
            val start = buffer.asLongBuffer().get()
            client.readSuspendSize(buffer, 8)
            val end = buffer.asLongBuffer().get()
            if (start >= end || end > file.size) {
                client.close()
            }
            val limitReadSize = end - start
            var offset: Long = start
            var hasRead: Long = 0
            val bufferSize = buffer.capacity()
            while (true) {
                val thisTimeRead = if (bufferSize + hasRead >= limitReadSize) {
                    (limitReadSize - hasRead).toInt()
                } else {
                    bufferSize
                }
                synchronized(fileData) {
                    fileData.seek(offset)
                    runBlocking { fileData.channel.readSuspendSize(byteBuffer = buffer, size = thisTimeRead) }
                }
                client.writeSuspendSize(buffer, buffer.copyAvailableBytes())
                hasRead += thisTimeRead
                offset += thisTimeRead
                val allSend = progress.addAndGet(thisTimeRead.toLong())
                progress(allSend, file.size)
                if (allSend >= file.size) { finishCheckChannel.send(Unit) }
                if (hasRead >= limitReadSize) { break }
            }
        }

        val job = launch(Dispatchers.IO) {
            while (true) {
                val client = ssc.acceptSuspend()
                launch(Dispatchers.IO) { newClient(client) }
            }
        }
        finishCheckChannel.receive()
        job.cancel("File: ${file.name}, Download Finish")
    }

}