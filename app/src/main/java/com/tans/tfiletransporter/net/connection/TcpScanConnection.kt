package com.tans.tfiletransporter.net.connection

import android.os.SystemClock
import com.tans.tfiletransporter.core.Stateable
import com.tans.tfiletransporter.net.TCP_SCAN_CONNECT_LISTEN_PORTER
import com.tans.tfiletransporter.net.UDP_BROADCAST_SERVER_ACCEPT
import com.tans.tfiletransporter.net.UDP_BROADCAST_SERVER_DENY
import com.tans.tfiletransporter.net.commonNetBufferPool
import com.tans.tfiletransporter.utils.*
import io.reactivex.Observable
import kotlinx.coroutines.*
import kotlinx.coroutines.rx2.await
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousSocketChannel
import java.util.*
import kotlin.math.abs
import kotlin.runCatching

suspend fun launchTcpScanConnectionServer(
    localDevice: String,
    localAddress: InetAddress,
    acceptRequest: suspend (remoteAddress: InetAddress, remoteDevice: String) -> Boolean
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
    val acceptRequest: suspend (remoteAddress: InetAddress, remoteDevice: String) -> Boolean
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
                            launch {
                                kotlin.runCatching { newClient(client) }
                            }
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
            buffer.flip()
            client.writeSuspendSize(buffer)
            buffer.clear()
            buffer.put(localDeviceBytes)
            buffer.flip()
            client.writeSuspendSize(buffer)

            client.readSuspendSize(buffer, 4)
            val remoteDeviceInfoSize = buffer.asIntBuffer().get()
            client.readSuspendSize(buffer, remoteDeviceInfoSize)
            val remoteDeviceInfo = buffer.copyAvailableBytes().toString(Charsets.UTF_8)

            buffer.clear()
            if (acceptRequest((client.remoteAddress as InetSocketAddress).address, remoteDeviceInfo)) {
                buffer.put(UDP_BROADCAST_SERVER_ACCEPT)
                client.writeSuspendSize(buffer)
                updateState { Optional.of((client.remoteAddress as InetSocketAddress).address to remoteDeviceInfo) }.await()
            } else {
                buffer.put(UDP_BROADCAST_SERVER_DENY)
                client.writeSuspendSize(buffer)
            }
        }
        commonNetBufferPool.recycleBuffer(buffer)
        result.exceptionOrNull()?.printStackTrace()
    }

}

suspend fun launchTopScanConnectionClient(
        localAddress: InetAddress,
        localDevice: String,
        timeoutRemove: Long = 5000,
        checkDuration: Long = 2000,
        handle: suspend TcpScanConnectionClient.(scanJob: Job) -> Unit) = coroutineScope {
    val tcpScanConnectionClient = TcpScanConnectionClient(
            localAddress = localAddress,
            localDevice = localDevice,
            timeoutRemove = timeoutRemove,
            checkDuration = checkDuration
    )
    val job = launch { tcpScanConnectionClient.startTcpServerScan() }
    handle(tcpScanConnectionClient, job)
}

class TcpScanConnectionClient(
        val localDevice: String,
        val localAddress: InetAddress,
        val timeoutRemove: Long = 5000,
        val checkDuration: Long = 2000
) : Stateable<List<Pair<RemoteDevice, Long>>> by Stateable(emptyList()) {

    internal suspend fun startTcpServerScan() {
        coroutineScope {

            // Remove out of date devices.
            launch {
                while (true) {
                    val oldState = bindState().firstOrError().await()
                    val now = SystemClock.uptimeMillis()
                    if (oldState.isNotEmpty() && oldState.any { abs(now - it.second) > timeoutRemove }) {
                        updateState { state ->
                            state.filter {
                                abs(now - it.second) <= timeoutRemove
                            }
                        }.await()
                    }
                    delay(checkDuration)
                }
            }

            launch {
                val (_, subNetPort) = localAddress.getBroadcastAddress()
                val nets = localAddress.getSubNetAllAddress(subNet = subNetPort.toInt())

                while (true) {
                    val jobs = nets.map { net ->
                        launch {
                            val buffer = commonNetBufferPool.requestBuffer()
                            kotlin.runCatching {
                                val sc = openAsynchronousSocketChannel()
                                sc.use {
                                    sc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
                                    sc.connectSuspend(InetSocketAddress(net, TCP_SCAN_CONNECT_LISTEN_PORTER))
                                    sc.readSuspendSize(byteBuffer = buffer, 4)
                                    val deviceSize = buffer.asIntBuffer().get()
                                    sc.readSuspendSize(byteBuffer = buffer, deviceSize)
                                    val remoteDeviceInfo = buffer.copyAvailableBytes().toString(Charsets.UTF_8)
                                    newRemoteDeviceComing(net to remoteDeviceInfo)
                                }
                            }
                            commonNetBufferPool.recycleBuffer(buffer)
                        }
                    }
                    for (job in jobs) { job.join() }
                }
            }
        }
    }

    suspend fun connectTo(remoteAddress: InetAddress): Boolean {
        val sc = openAsynchronousSocketChannel()
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

    fun bindRemoteDevice(): Observable<List<RemoteDevice>> = bindState()
            .map { s -> s.map { it.first } }
            .distinctUntilChanged()

    private suspend fun newRemoteDeviceComing(device: RemoteDevice) {
        updateState { oldState ->
            val now = SystemClock.uptimeMillis()
            if (!oldState.any { it.first.second == device.second }) {
                oldState + (device to now)
            } else {
                oldState.map {
                    if (it.first.second == device.second) {
                        it.first to now
                    } else {
                        it
                    }
                }
            }
        }.await()
    }
}