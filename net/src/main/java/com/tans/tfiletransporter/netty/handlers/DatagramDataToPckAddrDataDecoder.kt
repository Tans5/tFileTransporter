package com.tans.tfiletransporter.netty.handlers

import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.PackageDataWithAddress
import com.tans.tfiletransporter.netty.readBytes
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.DatagramPacket

class DatagramDataToPckAddrDataDecoder : ChannelInboundHandlerAdapter() {

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        if (msg is DatagramPacket) {
            val buffer = msg.content()
            try {
                val type = buffer.readInt()
                val messageId = buffer.readLong()
                val body = buffer.readBytes()
                super.channelRead(
                    ctx, PackageDataWithAddress(
                        receiverAddress = msg.sender(),
                        data = PackageData(type, messageId, body)
                    )
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                buffer.clear()
            }
        }
    }
}