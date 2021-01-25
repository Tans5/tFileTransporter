package com.tans.tfiletransporter.net.filetransporter

import com.tans.tfiletransporter.net.NET_BUFFER_SIZE
import com.tans.tfiletransporter.utils.readSuspend
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.Channels


interface ReaderHandleChains<T> {
    var chains: List<ReaderHandleChain<T>>

    suspend fun process(data: T, inputStream: InputStream, limit: Int) {
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

typealias ReaderHandleChain<T> = suspend (data: T, inputStream: InputStream, limit: Int, chains: ReaderHandleChains<T>?) -> Unit

fun <T> ReaderHandleChains(defaultChain: ReaderHandleChain<T> = { _, inputStream, _, _ ->
    val c = Channels.newChannel(inputStream)
    val buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
    while (true) {
        buffer.clear()
        if (c.readSuspend(buffer) == -1) { break }
    }
}) = object : ReaderHandleChains<T> {
    override var chains: List<ReaderHandleChain<T>> = listOf(defaultChain)
}



sealed class FileTransporterReaderHandle {
    abstract fun handle(readChannel: AsynchronousSocketChannel)
}