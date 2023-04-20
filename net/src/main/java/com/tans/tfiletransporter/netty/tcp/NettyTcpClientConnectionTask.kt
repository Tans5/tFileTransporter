package com.tans.tfiletransporter.netty.tcp

import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.handlers.BytesToPackageDataDecoder
import com.tans.tfiletransporter.netty.handlers.CheckerHandler
import com.tans.tfiletransporter.netty.handlers.PackageDataToBytesEncoder
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.timeout.IdleStateHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class NettyTcpClientConnectionTask(
    private val serverAddress: InetAddress,
    private val serverPort: Int,
    private val idleLimitDuration: Long = Long.MAX_VALUE
) : INettyConnectionTask {

    override val isExecuted: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    override val state: AtomicReference<NettyTaskState> by lazy {
        AtomicReference(NettyTaskState.NotExecute)
    }

    override val ioExecutor: Executor by lazy {
        Dispatchers.IO.asExecutor()
    }

    override val observers: LinkedBlockingDeque<NettyConnectionObserver> by lazy {
        LinkedBlockingDeque()
    }

    override fun runTask() {
        val loopEventGroup = NioEventLoopGroup(DEFAULT_LOOP_THREAD_COUNT, ioExecutor)
        try {
            val bootstrap = Bootstrap()
                .group(loopEventGroup)
                .channel(NioSocketChannel::class.java)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(object : ChannelInitializer<NioSocketChannel>() {
                    override fun initChannel(ch: NioSocketChannel) {
                        ch.pipeline()
                            .addLast(IdleStateHandler(idleLimitDuration, 0, 0, TimeUnit.MILLISECONDS))// 超时时间
                            .addLast(LengthFieldBasedFrameDecoder(Int.MAX_VALUE, /** length 长度偏移量 **/0, /** 长度 **/4, 0, 4))
                            .addLast(LengthFieldPrepender(4))
                            .addLast(BytesToPackageDataDecoder())
                            .addLast(PackageDataToBytesEncoder())
                            .addLast(CheckerHandler(this@NettyTcpClientConnectionTask, ch))
                    }
                })
            val channel = bootstrap.connect(InetSocketAddress(serverAddress, serverPort)).sync().channel()
            channel.closeFuture().sync()
            if (getCurrentState() !is NettyTaskState.Error) {
                dispatchState(NettyTaskState.ConnectionClosed)
            }
        } catch (e: Throwable) {
            if (getCurrentState() != NettyTaskState.ConnectionClosed) {
                dispatchState(NettyTaskState.Error(e))
            }
        } finally {
            loopEventGroup.shutdownGracefully()
        }
    }

    companion object {
        private const val DEFAULT_LOOP_THREAD_COUNT = 10
    }

}