package com.tans.tfiletransporter.netty.handlers

import com.tans.tfiletransporter.netty.ByteArrayPool
import com.tans.tfiletransporter.netty.NetByteArray
import com.tans.tfiletransporter.netty.PackageData
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

class BytesToPackageDataDecoder(
    private val byteArrayPool: ByteArrayPool
) : ByteToMessageDecoder() {

    override fun decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: MutableList<Any>) {
        try {
            val type = buffer.readInt()
            val messageId = buffer.readLong()
            val bodySize = buffer.writerIndex() - buffer.readerIndex()
            val byteArrayValue = byteArrayPool.get(bodySize)
            buffer.readBytes(byteArrayValue.value, 0, bodySize)
            out.add(PackageData(type = type, messageId = messageId, body = NetByteArray(byteArrayValue, bodySize)))
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            buffer.clear()
        }
    }
}