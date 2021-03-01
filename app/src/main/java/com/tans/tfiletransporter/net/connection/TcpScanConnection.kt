package com.tans.tfiletransporter.net.connection

import com.tans.tfiletransporter.core.Stateable
import com.tans.tfiletransporter.net.TCP_SCAN_CONNECT_LISTEN_PORTER
import com.tans.tfiletransporter.net.UDP_BROADCAST_SERVER_ACCEPT
import com.tans.tfiletransporter.net.UDP_BROADCAST_SERVER_DENY
import com.tans.tfiletransporter.net.commonNetBufferPool
import com.tans.tfiletransporter.utils.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousSocketChannel
import java.util.*
import kotlin.runCatching

suspend fun startTcpScanConnectionServer(
    localDevice: String,
    localAddress: InetAddress,
    acceptRequest: suspend (remoteAddress: SocketAddress, remoteDevice: String) -> Boolean
): RemoteDevice {
    val server = TcpScanConnectionServer(
        localDevice = localDevice,
        localAddress = localAddress,
        acceptRequest = acceptRequest
    )
    return server.startTcpScanConnectionServer()
}

class TcpScanConnectionServer(
    val localDevice: String,
    val localAddress: InetAddress,
    val acceptRequest: suspend (remoteAddress: SocketAddress, remoteDevice: String) -> Boolean
) : Stateable<Optional<RemoteDevice>> by Stateable(Optional.empty()) {

    internal suspend fun startTcpScanConnectionServer(): RemoteDevice {
        return coroutineScope {
            val listenJob = launch {
                val ssc = openAsynchronousServerSocketChannelSuspend()
                val result = kotlin.runCatching {
                    ssc.use {
                        ssc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                        ssc.bindSuspend(InetSocketAddress(localAddress, TCP_SCAN_CONNECT_LISTEN_PORTER), Int.MAX_VALUE)
                        while (true) {
                            val client = ssc.acceptSuspend()
                            launch { newClient(client) }
                        }
                    }
                }
                result.exceptionOrNull()?.printStackTrace()
            }
            val remoteDeviceInfo = bindState().filter { it.isPresent }.firstOrError().map { it.get() }.await()
            listenJob.cancel("Connection Request Accepted")
            remoteDeviceInfo
        }
    }

    /**
     * 1. Server Send Device Info.
     * 2. Client Send Device Info.
     * 3. Server Reply Request.
     */
    private suspend fun newClient(client: AsynchronousSocketChannel) {
        val buffer = commonNetBufferPool.requestBuffer()
        val result = runCatching {
            val localDeviceBytes = localDevice.toByteArray(Charsets.UTF_8)
            val localDeviceSizeBytes = localDeviceBytes.size.toBytes()
            buffer.put(localDeviceSizeBytes)
            client.writeSuspendSize(buffer)
            buffer.clear()
            buffer.put(localDeviceBytes)
            client.writeSuspendSize(buffer)

            client.readSuspendSize(buffer, 4)
            val remoteDeviceInfoSize = buffer.asIntBuffer().get()
            client.readSuspendSize(buffer, remoteDeviceInfoSize)
            val remoteDeviceInfo = buffer.copyAvailableBytes().toString(Charsets.UTF_8)

            buffer.clear()
            if (acceptRequest(client.remoteAddress, remoteDeviceInfo)) {
                buffer.put(UDP_BROADCAST_SERVER_ACCEPT)
                client.writeSuspendSize(buffer)
                updateState { Optional.of(client.remoteAddress to remoteDeviceInfo) }.await()
            } else {
                buffer.put(UDP_BROADCAST_SERVER_DENY)
                client.writeSuspendSize(buffer)
            }
        }
        commonNetBufferPool.recycleBuffer(buffer)
        result.exceptionOrNull()?.printStackTrace()
    }

}