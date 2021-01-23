package com.tans.tfiletransporter.net

import com.tans.tfiletransporter.utils.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import kotlin.jvm.Throws

const val FILE_TRANSPORT_LISTEN_PORT = 6668

const val VERSION_INT: Byte = 0x01

/**
 * All text type data is utf-8 encoding.
 */
enum class FileNetAction(
    // The first byte of action, the modifier of action.
    val actionCode: Byte) {

    /**
     * - 0-4 bytes (Int): The length of the directory path.
     *      example: 20
     *
     * - 4-(length + 4) bytes (String): The folder of the request.
     *      example: /home/user/downloads
     */
    RequestFolderChildrenShare(0x00),

    /**
     * - 0-4 bytes (Int): The length of the folder's children information.
     * - 4-(length + 4) (Json): The folder's children information.
     * example:
     * @see com.tans.tfiletransporter.net.model.ResponseFolderModel
     */
    FolderChildrenShare(0x01),

    /**
     * - 0-4 bytes (Int): The length of the files' information.
     * - 4-(length + 4) (Json): The files' information.
     * example:
     * the list of
     * @see com.tans.tfiletransporter.net.model.File
     */
    RequestFilesShare(0x02),

    /**
     * - 0-4 bytes (Int): The length of the files' information.
     * - 4-(length + 4) (Json): The files' information.
     * example:
     * the list of
     * @see com.tans.tfiletransporter.net.model.File
     *
     * - (length + 4)-(length + 4 + files' size) (file's data): The files' data.
     */
    FilesShare(0x03),

    /**
     * - 0-4 bytes (Int): The length of the message.
     * - 4-(length + 4) (String): The message.
     *
     */
    SendMessage(0x04)
}

class FileTransport(private val localAddress: InetAddress, private val remoteAddress: InetAddress, private val localFileSystemSeparator: String) {

    @Throws(IOException::class)
    internal suspend fun startAsClient() {
        val sc = openAsynchronousSocketChannel()
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
                handleAction(sc, String(buffer.copyAvailableBytes(), Charsets.UTF_8))
            } else {
                throw VersionCheckError
            }
        }
    }

    @Throws(IOException::class)
    internal suspend fun startAsServer() {
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
                handleAction(sc, remoteSeparator)
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