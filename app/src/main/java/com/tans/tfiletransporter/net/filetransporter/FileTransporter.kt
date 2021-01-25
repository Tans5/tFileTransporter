package com.tans.tfiletransporter.net.filetransporter

import android.app.Dialog
import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.net.NET_BUFFER_SIZE
import com.tans.tfiletransporter.utils.*
import kotlinx.coroutines.*
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import kotlin.jvm.Throws

const val FILE_TRANSPORT_LISTEN_PORT = 6668

const val VERSION_INT: Byte = 0x01


@Throws(IOException::class)
suspend fun launchFileTransport(
        localAddress: InetAddress,
        remoteAddress: InetAddress,
        isServer: Boolean,
        localFileSystemSeparator: String = FileConstants.FILE_SEPARATOR,
        connectLoadingDialog: Dialog? = null,
        handle: suspend FileTransporter.() -> Unit
) = coroutineScope {
    val fileTransport = FileTransporter(
            localAddress = localAddress,
            remoteAddress = remoteAddress,
            localFileSystemSeparator = localFileSystemSeparator,
            connectLoadingDialog = connectLoadingDialog)
    launch(Dispatchers.IO) {
        if (isServer) {
            fileTransport.startAsServer()
        } else {
            fileTransport.startAsClient()
        }
    }
    launch(Dispatchers.IO) { handle.invoke(fileTransport) }
}

class FileTransporter(private val localAddress: InetAddress,
                      private val remoteAddress: InetAddress,
                      private val localFileSystemSeparator: String = FileConstants.FILE_SEPARATOR,
                      private val connectLoadingDialog: Dialog? = null) {

    @Throws(IOException::class)
    internal suspend fun startAsClient() {
        if (connectLoadingDialog != null) {
            withContext(Dispatchers.Main) {
                if (!connectLoadingDialog.isShowing) { connectLoadingDialog.show() }
            }
        }
        try {
            val sc = openAsynchronousSocketChannel()
            delay(500)
            sc.use {
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
                    if (connectLoadingDialog != null) {
                        withContext(Dispatchers.Main) { if (connectLoadingDialog.isShowing) { connectLoadingDialog.dismiss() } }
                    }
                    handleAction(sc, String(buffer.copyAvailableBytes(), Charsets.UTF_8))
                } else {
                    throw VersionCheckError
                }
            }
        } finally {
            if (connectLoadingDialog != null) {
                withContext(Dispatchers.Main) { if (connectLoadingDialog.isShowing) { connectLoadingDialog.dismiss() } }
            }
        }
    }

    @Throws(IOException::class)
    internal suspend fun startAsServer() {
        if (connectLoadingDialog != null) {
            withContext(Dispatchers.Main) {
                if (!connectLoadingDialog.isShowing) { connectLoadingDialog.show() }
            }
        }

        try {
            val ssc = openAsynchronousServerSocketChannelSuspend()
            ssc.use {
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
                    if (connectLoadingDialog != null) {
                        withContext(Dispatchers.Main) {
                            if (connectLoadingDialog.isShowing) {
                                connectLoadingDialog.dismiss()
                            }
                        }
                    }
                    handleAction(sc, remoteSeparator)
                }
            }
        } finally {
            if (connectLoadingDialog != null) {
                withContext(Dispatchers.Main) { if (connectLoadingDialog.isShowing) { connectLoadingDialog.dismiss() } }
            }
        }
    }

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