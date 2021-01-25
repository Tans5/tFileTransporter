package com.tans.tfiletransporter.net.filetransporter

import com.tans.tfiletransporter.core.Stateable
import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.net.NET_BUFFER_SIZE
import com.tans.tfiletransporter.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.rx2.await
import java.io.IOException
import java.lang.Exception
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel

const val FILE_TRANSPORT_LISTEN_PORT = 6668

const val VERSION_INT: Byte = 0x01


@Throws(IOException::class)
suspend fun FileTransporter.launchFileTransport(
        isServer: Boolean,
        handle: suspend FileTransporter.() -> Unit
) = coroutineScope {
    launch(Dispatchers.IO) {
        if (isServer) {
            startAsServer()
        } else {
            startAsClient()
        }
    }
    launch(Dispatchers.IO) { handle.invoke(this@launchFileTransport) }
}


class FileTransporter(private val localAddress: InetAddress,
                      private val remoteAddress: InetAddress,
                      private val localFileSystemSeparator: String = FileConstants.FILE_SEPARATOR): Stateable<String> by Stateable("") {

    @Throws(IOException::class)
    internal suspend fun startAsClient() {
        val sc = openAsynchronousSocketChannel()
        delay(500)
        sc.use {
            try {
                sc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                sc.setOptionSuspend(StandardSocketOptions.SO_KEEPALIVE, true)
                sc.connectSuspend(InetSocketAddress(remoteAddress, FILE_TRANSPORT_LISTEN_PORT))
                val buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
                sc.readSuspendSize(buffer, 1)
                if (buffer.get() == VERSION_INT) {
                    val separatorBytes = localFileSystemSeparator.toByteArray(Charsets.UTF_8)
                    sc.writeSuspendSize(buffer, separatorBytes.size.toBytes() + separatorBytes)
                    sc.readSuspendSize(buffer, 4)
                    val separatorSize = buffer.asIntBuffer().get()
                    sc.readSuspendSize(buffer, separatorSize)
                    val remoteSeparator = String(buffer.copyAvailableBytes(), Charsets.UTF_8)
                    updateState { remoteSeparator }.await()
                    handleAction(sc, remoteSeparator)
                } else {
                    throw VersionCheckError
                }
            } catch (e: Exception) {
                stateStore.onError(e)
                throw e
            }
        }
    }

    @Throws(IOException::class)
    internal suspend fun startAsServer() {
        val ssc = openAsynchronousServerSocketChannelSuspend()
        ssc.use {
            try {
                ssc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                ssc.bindSuspend(InetSocketAddress(localAddress, FILE_TRANSPORT_LISTEN_PORT), 1)
                val sc = ssc.acceptSuspend()
                sc.use {
                    val buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
                    sc.writeSuspendSize(buffer, arrayOf(VERSION_INT).toByteArray())
                    sc.readSuspendSize(buffer, 4)
                    val size = buffer.asIntBuffer().get()
                    sc.readSuspendSize(buffer, size)
                    val remoteSeparator = String(buffer.copyAvailableBytes(), Charsets.UTF_8)
                    val localSeparatorData = localFileSystemSeparator.toByteArray(Charsets.UTF_8)
                    sc.writeSuspendSize(buffer, localSeparatorData.size.toBytes() + localSeparatorData)
                    updateState { remoteSeparator }.await()
                    handleAction(sc, remoteSeparator)
                }
            } catch (e: Exception) {
                stateStore.onError(e)
                throw e
            }
        }
    }

    /**
     * @return remote device file separator.
     */
    suspend fun whenConnectReady(): String = bindState().filter { it.isNotEmpty() }
        .firstOrError()
        .await()

    private suspend fun handleAction(sc: AsynchronousSocketChannel, remoteFileSeparator: String) = coroutineScope {
        launch {

        }

        launch {

        }
    }

    companion object {
        object VersionCheckError : IOException("Version Check Error")
    }

}