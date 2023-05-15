package com.tans.tfiletransporter.netty.handlers

import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.PackageDataWithAddress
import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.handler.timeout.IdleStateEvent
import java.net.InetSocketAddress

class CheckerHandler(
    private val task: INettyConnectionTask,
    private val channel: Channel,
    private val isUdp: Boolean = false
) : ChannelDuplexHandler() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        val currentState = task.getCurrentState()
        if (currentState == NettyTaskState.ConnectionClosed || currentState is NettyTaskState.Error) {
            ctx.close()
        } else {
            task.dispatchState(NettyTaskState.ConnectionActive(channel))
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        ctx.close()
    }

    override fun channelRead(
        ctx: ChannelHandlerContext,
        msg: Any
    ) {
        val localAddress = channel.localAddress() as? InetSocketAddress
        if (msg is PackageData) {
            val remoteAddress = channel.remoteAddress() as? InetSocketAddress
            task.dispatchDownloadData(
                localAddress,
                remoteAddress,
                msg,
            )
        }
        if (msg is PackageDataWithAddress) {
            val remoteAddress = msg.receiverAddress
            task.dispatchDownloadData(
                localAddress,
                remoteAddress,
                msg.data
            )
        }
        super.channelRead(ctx, msg)
    }

    override fun write(
        ctx: ChannelHandlerContext?,
        msg: Any?,
        promise: ChannelPromise?
    ) {
        if (task.getCurrentState() is NettyTaskState.ConnectionActive) {
            if (!isUdp) {
                if (msg is PackageData) {
                    super.write(ctx, msg, promise)
                }
            } else {
                if (msg is PackageDataWithAddress) {
                    super.write(ctx, msg, promise)
                }
            }
        }
    }

    override fun userEventTriggered(
        ctx: ChannelHandlerContext?,
        evt: Any?
    ) {
        super.userEventTriggered(ctx, evt)
        // 读写超时
        if (evt is IdleStateEvent) {
            ctx?.close()
            error("Connection read/write timeout: $evt")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun exceptionCaught(
        ctx: ChannelHandlerContext,
        cause: Throwable
    ) {
        cause.printStackTrace()
        ctx.close()
    }
}