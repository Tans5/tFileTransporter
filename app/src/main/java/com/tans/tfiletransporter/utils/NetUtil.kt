package com.tans.tfiletransporter.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.channels.DatagramChannel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


suspend fun openDatagramChannel(): DatagramChannel = blockToSuspend { DatagramChannel.open() }

suspend fun <V> DatagramChannel.setOptionSuspend(option: SocketOption<V>, value: V): DatagramChannel = blockToSuspend { setOption(option, value) }

suspend fun DatagramChannel.bindSuspend(address: SocketAddress): DatagramChannel = blockToSuspend { bind(address) }

suspend fun DatagramChannel.sendSuspend(src: ByteBuffer, endPoint: InetSocketAddress): Int = blockToSuspend(cancel = { if(isOpen) this.close() }) { send(src, endPoint) }

suspend fun DatagramChannel.receiveSuspend(src: ByteBuffer): SocketAddress = blockToSuspend(cancel = { if (isOpen) this.close() }) { receive(src) }

suspend fun AsynchronousServerSocketChannel.acceptSuspend(): AsynchronousSocketChannel = suspendCancellableCoroutine { cont ->
    accept(this, object : CompletionHandler<AsynchronousSocketChannel, AsynchronousServerSocketChannel> {

        override fun completed(result: AsynchronousSocketChannel, attachment: AsynchronousServerSocketChannel) {
            if (cont.isActive) cont.resume(result)
        }

        override fun failed(exc: Throwable, attachment: AsynchronousServerSocketChannel) {
            if (attachment.isOpen) attachment.close()
            if (cont.isActive) cont.resumeWithException(exc)
        }

    })
    cont.invokeOnCancellation { if (cont.isActive && this.isOpen) this.close() }
}

suspend fun openAsynchronousServerSocketChannelSuspend(): AsynchronousServerSocketChannel = blockToSuspend { AsynchronousServerSocketChannel.open() }

suspend fun AsynchronousServerSocketChannel.bindSuspend(address: InetSocketAddress, backlog: Int): AsynchronousServerSocketChannel = blockToSuspend(cancel = { if (isOpen) close() }) {
    bind(address, backlog)
}

suspend fun <V> AsynchronousServerSocketChannel.setOptionSuspend(option: SocketOption<V>, value: V): AsynchronousServerSocketChannel = blockToSuspend(cancel = { if (isOpen) close() }) {
    setOption(option, value)
}

suspend fun openAsynchronousSocketChannel(): AsynchronousSocketChannel = blockToSuspend { AsynchronousSocketChannel.open() }

suspend fun AsynchronousSocketChannel.connectSuspend(endPoint: SocketAddress): Unit = suspendCancellableCoroutine { cont ->
    connect(endPoint, this, object : CompletionHandler<Void, AsynchronousSocketChannel> {
        override fun completed(result: Void?, attachment: AsynchronousSocketChannel?) {
            if (cont.isActive) cont.resume(Unit)
        }
        override fun failed(exc: Throwable, attachment: AsynchronousSocketChannel) {
            if (attachment.isOpen) attachment.close()
            if (cont.isActive) cont.resumeWithException(exc)
        }
    })
    cont.invokeOnCancellation { if (isOpen) close() }
}

suspend fun AsynchronousSocketChannel.readSuspend(dst: ByteBuffer) = suspendCancellableCoroutine<Int> { cont ->
    read(dst, this, object : CompletionHandler<Int, AsynchronousSocketChannel> {
        override fun completed(result: Int, attachment: AsynchronousSocketChannel) {
            if (cont.isActive) cont.resume(result)
        }

        override fun failed(exc: Throwable, attachment: AsynchronousSocketChannel) {
            if (attachment.isOpen) attachment.close()
            if (cont.isActive) cont.resumeWithException(exc)
        }

    })
    cont.invokeOnCancellation {
        if (this.isOpen) this.close()
    }
}

suspend fun AsynchronousSocketChannel.writeSuspend(src: ByteBuffer) = suspendCancellableCoroutine<Int> { cont ->
    write(src, this, object : CompletionHandler<Int, AsynchronousSocketChannel> {
        override fun completed(result: Int, attachment: AsynchronousSocketChannel?) {
            if (cont.isActive) cont.resume(result)
        }

        override fun failed(exc: Throwable, attachment: AsynchronousSocketChannel) {
            if (attachment.isOpen) attachment.close()
            if (cont.isActive) cont.resumeWithException(exc)
        }
    })
    cont.invokeOnCancellation { if (isOpen) close() }
}

suspend fun <V> AsynchronousSocketChannel.setOptionSuspend(option: SocketOption<V>, value: V): AsynchronousSocketChannel = blockToSuspend { setOption(option, value) }


fun Int.toBytes(isRevert: Boolean = false): ByteArray {
    val result = ByteArray(4) { index ->
        (this and (0x000000FF shl (index * 8)) ushr (index * 8)).toByte()
    }
    return if (isRevert) {
        result
    } else {
        result.reverse()
        result
    }
}

fun findLocalAddressV4(): List<InetAddress> {
    val interfaces = NetworkInterface.getNetworkInterfaces()
    val result = ArrayList<InetAddress>()
    while (interfaces.hasMoreElements()) {
        val inetAddresses = interfaces.nextElement().inetAddresses
        while (inetAddresses.hasMoreElements()) {
            val address = inetAddresses.nextElement()
            if (address.address.size == 4 && !address.isLinkLocalAddress && !address.isLoopbackAddress) {
                result.add(address)
            }
        }
    }
    return result
}

fun InetAddress.getBroadcastAddress()
        : InetAddress = NetworkInterface.getByInetAddress(this).interfaceAddresses
        .mapNotNull { it.broadcast }
        .lastOrNull() ?: InetAddress.getByAddress((-1).toBytes())

fun ByteBuffer.copyAvailableBytes(): ByteArray {
    val position = position()
    val limit = limit()
    val array = array()
    return ByteArray(limit - position) { i -> array[position + i] }
}