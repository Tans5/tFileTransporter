package com.tans.tfiletransporter.net.filetransporter

import com.squareup.moshi.Types
import com.tans.tfiletransporter.moshi
import com.tans.tfiletransporter.net.NET_BUFFER_SIZE
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.utils.readDataLimit
import com.tans.tfiletransporter.utils.readString
import com.tans.tfiletransporter.utils.readSuspend
import com.tans.tfiletransporter.utils.readSuspendSize
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.Channels


interface ReaderHandleChains<T> {
    var chains: List<ReaderHandleChain<T>>

    suspend fun process(data: T, inputStream: InputStream, limit: Long) {
        val chains = chains
        val lastIndex = chains.lastIndex
        val chain = chains[lastIndex]
        if (lastIndex <= 0) {
            chain(data, inputStream, limit, null)
        } else {
            val newChains = chains.subList(0, lastIndex - 1)
            chain(data, inputStream, limit, object : ReaderHandleChains<T> { override var chains: List<ReaderHandleChain<T>> = newChains })
        }
    }

    suspend fun newChain(chain: ReaderHandleChain<T>) {
        synchronized(this) {
            chains = chains + chain
        }
    }
}

typealias ReaderHandleChain<T> = suspend (data: T, inputStream: InputStream, limit: Long, chains: ReaderHandleChains<T>?) -> Unit

fun <T> ReaderHandleChains(defaultChain: ReaderHandleChain<T> = { _, inputStream, limit, _ ->
    val c = Channels.newChannel(inputStream)
    val buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
    val bufferSize = NET_BUFFER_SIZE
    var read = 0L
    while (true) {
        val thisTimeRead = if (read + bufferSize >= limit) {
            (limit - read).toInt()
        } else {
            bufferSize
        }
        c.readSuspendSize(buffer, thisTimeRead)
        read += thisTimeRead
        if (read >= limit) {
            break
        }
    }
}) = object : ReaderHandleChains<T> {
    override var chains: List<ReaderHandleChain<T>> = listOf(defaultChain)
}


sealed class FileTransporterReaderHandle {
    abstract suspend fun handle(readChannel: AsynchronousSocketChannel)

    suspend fun AsynchronousSocketChannel.simpleSizeDeal(chains: ReaderHandleChains<Unit>) {
        val buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
        readSuspendSize(buffer, 4)
        val limit = buffer.asIntBuffer().get()
        readDataLimit(
                limit = limit.toLong(),
                buffer = buffer
        ) { inputStream -> chains.process(Unit, inputStream, limit.toLong()) }
    }
}

/**
 * @see com.tans.tfiletransporter.net.filetransporter.FileNetAction.RequestFolderChildrenShare
 */
class RequestFolderChildrenShareReaderHandle : FileTransporterReaderHandle(), ReaderHandleChains<Unit> by ReaderHandleChains() {

    override suspend fun handle(readChannel: AsynchronousSocketChannel) { readChannel.simpleSizeDeal(this) }
}

/**
 * @see com.tans.tfiletransporter.net.filetransporter.FileNetAction.FolderChildrenShare
 */
class FolderChildrenShareReaderHandle : FileTransporterReaderHandle(), ReaderHandleChains<Unit> by ReaderHandleChains() {

    override suspend fun handle(readChannel: AsynchronousSocketChannel) { readChannel.simpleSizeDeal(this) }
}

/**
 * @see com.tans.tfiletransporter.net.filetransporter.FileNetAction.RequestFilesShare
 */
class RequestFilesShareReaderHandle : FileTransporterReaderHandle(), ReaderHandleChains<Unit> by ReaderHandleChains() {

    override suspend fun handle(readChannel: AsynchronousSocketChannel) { readChannel.simpleSizeDeal(this) }
}

/**
 * @see com.tans.tfiletransporter.net.filetransporter.FileNetAction.FilesShare
 */
class FilesShareReaderHandle : FileTransporterReaderHandle(), ReaderHandleChains<List<File>> by ReaderHandleChains() {

    override suspend fun handle(readChannel: AsynchronousSocketChannel) {
        val buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
        readChannel.readSuspendSize(buffer, 4)
        val limit = buffer.asIntBuffer().get()
        val files = readChannel.readDataLimit(
                limit = limit.toLong(),
                buffer = buffer
        ) { inputStream ->
            val filesJson = inputStream.readString(limit.toLong())
            val moshiType = Types.newParameterizedType(List::class.java, File::class.java)
            val files: List<File>? = moshi.adapter<List<File>>(moshiType).fromJson(filesJson)
            if (files.isNullOrEmpty()) {
                throw error("FilesShareReaderHandle, wrong files string: $filesJson")
            } else {
                files
            }
        }

        val filesLimit = files.sumOf { it.size }
        readChannel.readDataLimit(
                limit = filesLimit,
                buffer = buffer
        ) { fileInputStream ->
            process(files, fileInputStream, filesLimit)
        }
    }

}

/**
 * @see com.tans.tfiletransporter.net.filetransporter.FileNetAction.SendMessage
 */
class SendMessageReaderHandle : FileTransporterReaderHandle(), ReaderHandleChains<Unit> by ReaderHandleChains() {

    override suspend fun handle(readChannel: AsynchronousSocketChannel) { readChannel.simpleSizeDeal(this) }

}
