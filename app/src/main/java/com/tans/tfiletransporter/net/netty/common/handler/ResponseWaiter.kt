package com.tans.tfiletransporter.net.netty.common.handler

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import java.util.concurrent.atomic.AtomicLong

class ResponseWaiter : ChannelInboundHandlerAdapter() {

    private val writePackageIndex: AtomicLong = AtomicLong(0)

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {

        super.channelRead(ctx, msg)
    }
}