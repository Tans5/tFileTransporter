package com.tans.tfiletransporter.netty.handlers

import com.tans.tfiletransporter.netty.PackageDataWithAddress
import com.tans.tfiletransporter.utils.enGzip
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.channel.socket.DatagramPacket

class PckAddrDataToDatagramDataEncoder :  ChannelOutboundHandlerAdapter() {

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is PackageDataWithAddress) {
            val buffer = ctx.alloc().buffer()
            buffer.writeInt(msg.data.type)
            buffer.writeLong(msg.data.messageId)
            buffer.writeBytes(msg.data.body.enGzip())
            super.write(ctx, DatagramPacket(buffer, msg.receiverAddress, msg.senderAddress), promise)
        }
    }

}