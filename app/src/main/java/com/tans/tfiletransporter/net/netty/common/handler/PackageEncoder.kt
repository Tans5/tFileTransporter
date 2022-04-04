package com.tans.tfiletransporter.net.netty.common.handler

import com.tans.tfiletransporter.net.netty.common.*
import com.tans.tfiletransporter.utils.toBytes
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.MessageToByteEncoder
import java.util.concurrent.atomic.AtomicLong

class PackageEncoder : MessageToByteEncoder<NettyPkg>() {

    override fun encode(ctx: ChannelHandlerContext, msg: NettyPkg, out: ByteBuf) {
        when (msg) {
            NettyPkg.HeartBeatPkg -> {
                out.writeByte(HEART_BEHAT_PKG.toInt())
            }
            is NettyPkg.ClientFinishPkg -> {
                out.writeByte(CLIENT_FINISH_PKG.toInt())
                out.writeBytes(msg.msg.toByteArray(Charsets.UTF_8))
            }
            is NettyPkg.ServerFinishPkg -> {
                out.writeByte(SERVER_FINISH_PKG.toInt())
                out.writeBytes(msg.msg.toByteArray(Charsets.UTF_8))
            }
            is NettyPkg.BytesPkg -> {
                out.writeByte(BYTES_PKG.toInt())
                out.writeBytes(msg.pkgIndex!!.toBytes())
                out.writeBytes(msg.bytes)
            }
            is NettyPkg.JsonPkg -> {
                out.writeByte(JSON_PKG.toInt())
                out.writeBytes(msg.pkgIndex!!.toBytes())
                out.writeBytes(msg.json.toByteArray(Charsets.UTF_8))
            }
            is NettyPkg.TextPkg -> {
                out.writeByte(TEXT_PKG.toInt())
                out.writeBytes(msg.pkgIndex!!.toBytes())
                out.writeBytes(msg.text.toByteArray(Charsets.UTF_8))
            }
            is NettyPkg.ResponsePkg -> {
                out.writeByte(RESPONSE_PKG.toInt())
                out.writeBytes(msg.pkgIndex.toBytes())
            }
            else -> {

            }
        }
    }

    private fun UInt.toBytes(): ByteArray = toLong().toBytes().takeLast(4).toByteArray()

}