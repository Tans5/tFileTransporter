package com.tans.tfiletransporter.net.connection

import com.tans.tfiletransporter.core.Stateable
import com.tans.tfiletransporter.net.TCP_SCAN_CONNECT_LISTEN_PORTER
import com.tans.tfiletransporter.net.UDP_BROADCAST_SERVER_ACCEPT
import com.tans.tfiletransporter.net.UDP_BROADCAST_SERVER_DENY
import com.tans.tfiletransporter.net.commonNetBufferPool
import com.tans.tfiletransporter.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.Channels
import java.util.*


enum class ServerStatus {
    Start,
    Stop
}

data class TcpScanConnectionServerState(
    val serverStatus: ServerStatus = ServerStatus.Stop,
    val acceptClient: Optional<RemoteDevice> = Optional.empty()
)

class TcpScanConnectionServer(
    val localDevice: String,
    val localAddress: InetAddress
) : Stateable<TcpScanConnectionServerState> by Stateable(TcpScanConnectionServerState()) {

    suspend fun runTcpScanConnectionServer(acceptRequest: suspend (remoteAddress: InetAddress, remoteDevice: String) -> Boolean) {
        val serverStatus = bindState().map { it.serverStatus }.firstOrError().await()
        if (serverStatus == ServerStatus.Stop) {
            updateState { it.copy(serverStatus = ServerStatus.Start) }.await()
            coroutineScope {
                val listenJob = launch {
                    val ssc = openAsynchronousServerSocketChannelSuspend()
                    val result = kotlin.runCatching {
                        ssc.use {
                            ssc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                            ssc.bindSuspend(InetSocketAddress(localAddress, TCP_SCAN_CONNECT_LISTEN_PORTER), Int.MAX_VALUE)
                            while (true) {
                                val client = ssc.acceptSuspend()
                                launch {
                                    kotlin.runCatching { client.use { newClient(client, acceptRequest) } }
                                }
                            }
                        }
                    }
                    result.exceptionOrNull()?.printStackTrace()
                    stop()
                }
                bindState().map { it.serverStatus }.filter { it == ServerStatus.Stop }.firstOrError().await()
                listenJob.cancel("Connection Request Accepted")
            }
        }
    }

    suspend fun whenAcceptRequest(): RemoteDevice = bindState()
        .map { it.acceptClient }
        .skip(1)
        .filter { it.isPresent }
        .map { it.get() }
        .firstOrError()
        .await()

    suspend fun stop(): Unit = updateState { oldState ->
        oldState.copy(serverStatus = ServerStatus.Stop)
    }.map {  }.await()

    /**
     * 1. Server Send Device Info.
     * 2. Client Send Device Info.
     * 3. Server Reply Request.
     */
    private suspend fun newClient(
        client: AsynchronousSocketChannel,
        acceptRequest: suspend (remoteAddress: InetAddress, remoteDevice: String) -> Boolean
    ) {
        val buffer = commonNetBufferPool.requestBuffer()
        val localDeviceBytes = localDevice.toByteArray(Charsets.UTF_8)
        val localDeviceSizeBytes = localDeviceBytes.size.toBytes()
        buffer.put(localDeviceSizeBytes)
        buffer.flip()
        client.writeSuspendSize(buffer)
        buffer.clear()
        buffer.put(localDeviceBytes)
        buffer.flip()
        client.writeSuspendSize(buffer)

        client.readSuspendSize(buffer, 4)
        val remoteDeviceInfoSize = buffer.asIntBuffer().get()
        if (remoteDeviceInfoSize > 0) {
            client.readSuspendSize(buffer, remoteDeviceInfoSize)
            val remoteDeviceInfo = buffer.copyAvailableBytes().toString(Charsets.UTF_8)

            buffer.clear()
            if (acceptRequest(
                    (client.remoteAddress as InetSocketAddress).address,
                    remoteDeviceInfo
                )
            ) {
                buffer.put(UDP_BROADCAST_SERVER_ACCEPT)
                buffer.flip()
                client.writeSuspendSize(buffer)
                updateState { oldState ->
                    oldState.copy(
                        acceptClient = Optional.of((client.remoteAddress as InetSocketAddress).address to remoteDeviceInfo),
                        serverStatus = ServerStatus.Stop
                    )
                }.await()
            } else {
                buffer.put(UDP_BROADCAST_SERVER_DENY)
                buffer.flip()
                client.writeSuspendSize(buffer)
            }
        }
        commonNetBufferPool.recycleBuffer(buffer)
    }

}

class TcpScanConnectionClient(
        val localDevice: String,
        val localAddress: InetAddress
) {

    suspend fun scanServers(scanProgress: suspend (loadIndex: Int, size: Int) -> Unit = { _, _ -> }): List<RemoteDevice> {
        return coroutineScope {
            val (_, subNetPort) = localAddress.getBroadcastAddress()
            val nets = localAddress.getSubNetAllAddress(subNet = subNetPort.toInt())
            nets.map { net ->
                async {
                    val buffer = commonNetBufferPool.requestBuffer()
                    val result = kotlin.runCatching {
                        val socket = Socket()
                        socket.use {
                            socket.connect(InetSocketAddress(net, TCP_SCAN_CONNECT_LISTEN_PORTER), 300)
                            val readChannel = Channels.newChannel(socket.getInputStream())
                            readChannel.readSuspendSize(byteBuffer = buffer, 4)
                            val deviceSize = buffer.asIntBuffer().get()
                            if (deviceSize <= 0 || deviceSize > buffer.capacity()) error("Wrong device size: $deviceSize")
                            readChannel.readSuspendSize(byteBuffer = buffer, deviceSize)
                            val remoteDeviceInfo =
                                buffer.copyAvailableBytes().toString(Charsets.UTF_8)
                            net to remoteDeviceInfo
                        }
                    }
                    commonNetBufferPool.recycleBuffer(buffer)
                    Optional.ofNullable(result.getOrNull())
                }
            }
                .withIndex()
                .map { (i, job) ->
                    scanProgress(i, nets.size)
                    job.await()
                }
                .filter { it.isPresent }
                .map { it.get() }
        }
    }

    suspend fun connectTo(remoteAddress: InetAddress): Boolean {
        val sc = openAsynchronousSocketChannel()
        sc.use {
            sc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
            sc.connectSuspend(InetSocketAddress(remoteAddress, TCP_SCAN_CONNECT_LISTEN_PORTER))
            val buffer = commonNetBufferPool.requestBuffer()
            try {
                sc.readSuspendSize(byteBuffer = buffer, 4)
                val deviceSize = buffer.asIntBuffer().get()
                sc.readSuspendSize(byteBuffer = buffer, deviceSize)
                val deviceBytes = localDevice.toByteArray(Charsets.UTF_8)
                buffer.clear()
                buffer.put(deviceBytes.size.toBytes())
                buffer.flip()
                sc.writeSuspendSize(buffer)
                buffer.clear()
                buffer.put(deviceBytes)
                buffer.flip()
                sc.writeSuspendSize(buffer)
                sc.readSuspendSize(buffer, 1)
                val result: Byte = buffer.get()
                return result == UDP_BROADCAST_SERVER_ACCEPT
            } finally {
                commonNetBufferPool.recycleBuffer(buffer)
            }
        }

    }
}