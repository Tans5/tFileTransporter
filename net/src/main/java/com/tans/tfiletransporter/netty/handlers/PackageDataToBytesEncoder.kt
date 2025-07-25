package com.tans.tfiletransporter.netty.handlers

import com.tans.tfiletransporter.netty.ByteArrayPool
import com.tans.tfiletransporter.netty.PackageData
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

class PackageDataToBytesEncoder(
    private val byteArrayPool: ByteArrayPool
) : MessageToByteEncoder<PackageData>() {

    override fun encode(ctx: ChannelHandlerContext, msg: PackageData, out: ByteBuf) {
        out.writeInt(msg.type)
        out.writeLong(msg.messageId)
        val bytes = msg.body.value.value
        val len = msg.body.readSize
        out.writeBytes(bytes, 0, len)
        byteArrayPool.put(msg.body.value)
    }
}