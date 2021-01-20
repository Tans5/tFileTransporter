package com.tans.tfiletransporter.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetSocketAddress
import java.net.SocketOption
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.nio.channels.DatagramChannel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun DatagramChannel.sendSuspend(src: ByteBuffer, endPoint: InetSocketAddress) {
    blockToSuspend(cancel = { this.close() }) { send(src, endPoint) }
}

suspend fun openDatagramChannel(): DatagramChannel = blockToSuspend { DatagramChannel.open() }

suspend fun <V> DatagramChannel.setOptionSuspend(option: SocketOption<V>, value: V): DatagramChannel = blockToSuspend { setOption(option, value) }

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