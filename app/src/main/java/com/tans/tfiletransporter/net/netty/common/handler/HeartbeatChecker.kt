package com.tans.tfiletransporter.net.netty.common.handler

import com.tans.tfiletransporter.logs.Log
import com.tans.tfiletransporter.net.netty.common.NettyPkg
import com.tans.tfiletransporter.net.netty.common.TAG_NETTY
import com.tans.tfiletransporter.utils.ioExecutor
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.timeout.IdleState

class HeartbeatChecker(private val durationSeconds: Int) : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        ioExecutor.execute {
            do {
                ctx.write(NettyPkg.HeartBeatPkg)
                Thread.sleep(durationSeconds * 1000L)
            } while (ctx.channel().isActive)
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        super.userEventTriggered(ctx, evt)
        if (evt == IdleState.ALL_IDLE) {
            Log.e(TAG_NETTY, "", Throwable("Read or Write timeout"))
        }
    }
}