package com.tans.tfiletransporter.utils

import com.tans.tfiletransporter.net.NET_BUFFER_SIZE
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.*
import java.nio.channels.CompletionHandler
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

fun Long.toBytes(): ByteArray = ByteArray(8) { index ->
    val slide = (7 - index) * 8
    (this and ((0x00_00_00_00_00_00_00_FF).toLong() shl slide) ushr slide).toByte()
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

fun ByteBuffer.moveToEndSize(size: Int) {
    val cap = capacity()
    if (size > cap) error("Size: $size greater than Cap: $cap")
    limit(cap)
    position(cap - size)
}

suspend fun AsynchronousSocketChannel.readSuspendSize(byteBuffer: ByteBuffer, size: Int) {
    byteBuffer.moveToEndSize(size)
    var readSize = 0
    while (true) {
        val thisTimeRead = readSuspend(byteBuffer)
        if (thisTimeRead <= -1) {
            break
        }
        readSize += thisTimeRead
        if (readSize >= size) {
            break
        }
    }
    byteBuffer.moveToEndSize(size)
}

suspend fun AsynchronousSocketChannel.writeSuspendSize(byteBuffer: ByteBuffer, bytes: ByteArray) {
    val size = bytes.size
    byteBuffer.limit(size)
    byteBuffer.position(0)
    byteBuffer.put(bytes)
    byteBuffer.flip()
    var writeSize = 0
    while (true) {
        val thisTimeWrite = writeSuspend(byteBuffer)
        if (thisTimeWrite <= -1) {
            break
        }
        writeSize += thisTimeWrite
        if (writeSize >= size) {
            break
        }
    }
}

suspend fun AsynchronousSocketChannel.writeSuspendSize(byteBuffer: ByteBuffer) {
    val size = byteBuffer.limit() - byteBuffer.position()
    var writeSize = 0
    while (true) {
        val thisTimeWrite = writeSuspend(byteBuffer)
        if (thisTimeWrite <= -1) {
            break
        }
        writeSize += thisTimeWrite
        if (writeSize >= size) {
            break
        }
    }
}

suspend fun <T> AsynchronousSocketChannel.readDataLimit(
        limit: Long,
        buffer: ByteBuffer = ByteBuffer.allocate(NET_BUFFER_SIZE),
        handle: suspend (inputStream: InputStream) -> T): T = coroutineScope {
    if (limit <= 0) error("Wrong limit size: $limit")
    val outputStream = PipedOutputStream()
    val inputStream = PipedInputStream(outputStream)
    val writer = Channels.newChannel(outputStream)
    val result = async(Dispatchers.IO) {
        inputStream.use {
            handle(inputStream)
        }
    }
    launch(Dispatchers.IO) {
        writer.use {
            val bufferSize = buffer.capacity()
            var readSize = 0L
            while (true) {
                val thisTimeReadSize = if (readSize + bufferSize >= limit) {
                    (limit - readSize).toInt()
                } else {
                    bufferSize
                }
                readSuspendSize(buffer, thisTimeReadSize)
                writer.writeSuspendSize(buffer)
                readSize += thisTimeReadSize
                if (readSize >= limit) {
                    break
                }
            }
        }
        outputStream.flush()
        outputStream.close()
    }
    result.await()
}

suspend fun AsynchronousSocketChannel.writeDataLimit(
        limit: Long,
        buffer: ByteBuffer = ByteBuffer.allocate(NET_BUFFER_SIZE),
        handle: suspend (outputStream: OutputStream) -> Unit
) = coroutineScope {
    if (limit <= 0) error("Wrong limit size: $limit")
    val inputStream = PipedInputStream()
    val outputStream = PipedOutputStream(inputStream)
    val reader = Channels.newChannel(inputStream)
    launch(Dispatchers.IO) {
        outputStream.use {
            handle(outputStream)
            outputStream.flush()
        }
    }
    launch(Dispatchers.IO) {
        val bufferSize = buffer.capacity()
        reader.use {
            var hasWriteSize = 0L
            while (true) {
                val thisTimeWriteSize = if (hasWriteSize + bufferSize >= limit) {
                    (limit - hasWriteSize).toInt()
                } else {
                    bufferSize
                }
                reader.readSuspendSize(buffer, thisTimeWriteSize)
                writeSuspendSize(buffer)
                hasWriteSize += thisTimeWriteSize
                if (hasWriteSize >= limit) {
                    break
                }
            }
        }
        inputStream.close()
    }
}

suspend fun ReadableByteChannel.readSuspend(dst: ByteBuffer): Int = blockToSuspend(cancel = { if (isOpen) close() }) { read(dst) }

suspend fun ReadableByteChannel.readSuspendSize(byteBuffer: ByteBuffer, size: Int) {
    byteBuffer.moveToEndSize(size)
    var readSize = 0
    while (true) {
        val thisTimeRead = readSuspend(byteBuffer)
        if (thisTimeRead <= -1) {
            break
        }
        readSize += thisTimeRead
        if (readSize >= size) {
            break
        }
    }
    byteBuffer.moveToEndSize(size)
}

suspend fun WritableByteChannel.writeSuspend(src: ByteBuffer): Int = blockToSuspend(cancel = { if (isOpen) close() }) { write(src) }

suspend fun WritableByteChannel.writeSuspendSize(byteBuffer: ByteBuffer, bytes: ByteArray) {
    val size = bytes.size
    byteBuffer.limit(bytes.size)
    byteBuffer.position(0)
    byteBuffer.put(bytes)
    byteBuffer.flip()
    var writeSize = 0
    while (true) {
        val thisTimeWrite = writeSuspend(byteBuffer)
        if (thisTimeWrite <= -1) {
            break
        }
        writeSize += thisTimeWrite
        if (writeSize >= size) {
            break
        }
    }
}

suspend fun WritableByteChannel.writeSuspendSize(byteBuffer: ByteBuffer) {
    val size = byteBuffer.limit() - byteBuffer.position()
    var writeSize = 0
    while (true) {
        val thisTimeWrite = writeSuspend(byteBuffer)
        if (thisTimeWrite <= -1) {
            break
        }
        writeSize += thisTimeWrite
        if (writeSize >= size) {
            break
        }
    }
}

suspend fun ReadableByteChannel.writeTo(
        writeable: WritableByteChannel,
        limit: Long,
        buffer: ByteBuffer = ByteBuffer.allocate(NET_BUFFER_SIZE),
        progress: suspend (writeSize: Long, limit: Long) -> Unit = { _, _ ->}) {
    var writeSize = 0L
    val bufferSize = buffer.capacity()
    while (true) {
        buffer.clear()
        val thisTimeWriteSize = if (writeSize + bufferSize >= limit) {
            (limit - writeSize).toInt()
        } else {
            bufferSize
        }
        readSuspendSize(buffer, thisTimeWriteSize)
        writeable.writeSuspendSize(buffer)
        writeSize += thisTimeWriteSize
        progress(writeSize, limit)
        if (writeSize >= limit) {
            break
        }
    }
}

suspend fun ReadableByteChannel.writeTo(
        writeable: AsynchronousSocketChannel,
        limit: Long,
        buffer: ByteBuffer = ByteBuffer.allocate(NET_BUFFER_SIZE),
        progress: suspend (writeSize: Long, limit: Long) -> Unit = { _, _ ->}) {
    var writeSize = 0L
    val bufferSize = buffer.capacity()
    while (true) {
        buffer.clear()
        val thisTimeWriteSize = if (writeSize + bufferSize >= limit) {
            (limit - writeSize).toInt()
        } else {
            bufferSize
        }
        readSuspendSize(buffer, thisTimeWriteSize)
        writeable.writeSuspendSize(buffer)
        writeSize += thisTimeWriteSize
        progress(writeSize, limit)
        if (writeSize >= limit) {
            break
        }
    }
}

suspend fun WritableByteChannel.readFrom(
        readable: ReadableByteChannel,
        limit: Long,
        buffer: ByteBuffer = ByteBuffer.allocate(NET_BUFFER_SIZE),
        progress: suspend (hasWrite: Long, limit: Long) -> Unit = { _, _ -> }) {
    var readSize = 0L
    val bufferSize = buffer.capacity()
    while (true) {
        buffer.clear()
        val thisTimeReadSize = if (readSize + bufferSize >= limit) {
            (limit - readSize).toInt()
        } else {
            bufferSize
        }
        readable.readSuspendSize(buffer,thisTimeReadSize)
        writeSuspendSize(buffer)
        readSize += thisTimeReadSize
        progress(readSize, limit)
        if (readSize >= limit) {
            break
        }
    }
}

suspend fun WritableByteChannel.readFrom(
        readable: AsynchronousSocketChannel,
        limit: Long,
        buffer: ByteBuffer = ByteBuffer.allocate(NET_BUFFER_SIZE),
        progress: suspend (hasWrite: Long, limit: Long) -> Unit = { _, _ -> }) {
    var readSize = 0L
    val bufferSize = buffer.capacity()
    while (true) {
        buffer.clear()
        val thisTimeReadSize = if (readSize + bufferSize >= limit) {
            (limit - readSize).toInt()
        } else {
            bufferSize
        }
        readable.readSuspendSize(buffer,thisTimeReadSize)
        writeSuspendSize(buffer)
        readSize += thisTimeReadSize
        progress(readSize, limit)
        if (readSize >= limit) {
            break
        }
    }
}

suspend fun InputStream.readString(limit: Long): String {
    val outputStream = ByteArrayOutputStream()
    val writer = Channels.newChannel(outputStream)
    val reader = Channels.newChannel(this)
    reader.use {
        writer.use {
            writer.readFrom(reader, limit)
        }
    }
    val bytes = outputStream.toByteArray()
    outputStream.close()
    return String(bytes, Charsets.UTF_8)
}
