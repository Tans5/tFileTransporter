package com.tans.tfiletransporter.netty.tcp

import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.handlers.BytesToPackageDataDecoder
import com.tans.tfiletransporter.netty.handlers.PackageDataToBytesEncoder
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.timeout.IdleStateEvent
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

class NettyTcpServerConnectionTask(
    private val bindAddress: InetAddress,
    private val bindPort: Int,
    private val idleLimitDuration: Long = Long.MAX_VALUE,
    private val newClientTaskCallback: (clientTask: ChildConnectionTask) -> Unit
) : INettyConnectionTask, NettyConnectionObserver {

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

    private val activeChildrenChannel: LinkedBlockingDeque<INettyConnectionTask> by lazy {
        LinkedBlockingDeque<INettyConnectionTask>()
    }

    override fun runTask() {
        val connectionLoopGroup = NioEventLoopGroup(DEFAULT_CONNECTION_THREAD_COUNT, ioExecutor)
        val writeReadLoopGroup = NioEventLoopGroup(DEFAULT_WRITE_READ_LOOP_THREAD_COUNT, ioExecutor)
        try {
            val bootstrap = ServerBootstrap()
                .group(connectionLoopGroup, writeReadLoopGroup)
                .channel(NioServerSocketChannel::class.java)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childHandler(object : ChannelInitializer<NioSocketChannel>() {
                    override fun initChannel(ch: NioSocketChannel) {
                        val childTask = ChildConnectionTask(ch)
                        activeChildrenChannel.add(childTask)
                        childTask.addObserver(this@NettyTcpServerConnectionTask)
                        childTask.startTask()
                        newClientTaskCallback(childTask)
                    }
                })
            val channel = bootstrap.bind(InetSocketAddress(bindAddress, bindPort)).sync().channel()
            if (NettyTaskState.ConnectionClosed != getCurrentState()) {
                dispatchState(NettyTaskState.Init)
                dispatchState(NettyTaskState.ConnectionActive(channel))
            } else {
                channel.close()
            }
            channel.closeFuture().sync()
            if (getCurrentState() !is NettyTaskState.Error) {
                dispatchState(NettyTaskState.ConnectionClosed)
            }
        } catch (e: Throwable) {
            if (getCurrentState() != NettyTaskState.ConnectionClosed) {
                dispatchState(NettyTaskState.Error(e))
            }
        } finally {
            connectionLoopGroup.shutdownGracefully()
            writeReadLoopGroup.shutdownGracefully()
        }
    }

    /**
     * Children State.
     */
    override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
        if (nettyState is NettyTaskState.ConnectionClosed || nettyState is NettyTaskState.Error) {
            activeChildrenChannel.remove(task)
        }
    }

    override fun sendData(
        data: PackageData,
        sendDataCallback: INettyConnectionTask.SendDataCallback?
    ) {
        sendDataCallback?.onFail("Server task not support send data")
    }

    override fun stopTask() {
        super.stopTask()
        for (t in activeChildrenChannel) {
            t.stopTask()
        }
        activeChildrenChannel.clear()
    }

    inner class ChildConnectionTask(private val socketChannel: NioSocketChannel) : INettyConnectionTask {

        override val isExecuted: AtomicBoolean = AtomicBoolean(false)
        override val state: AtomicReference<NettyTaskState> = AtomicReference(NettyTaskState.NotExecute)
        override val ioExecutor: Executor = this@NettyTcpServerConnectionTask.ioExecutor
        override val observers: LinkedBlockingDeque<NettyConnectionObserver> = LinkedBlockingDeque()

        override fun runTask() {
            try {
                val remoteAddress = socketChannel.remoteAddress()
                val localAddress = socketChannel.localAddress()
                if (NettyTaskState.ConnectionClosed != getCurrentState()) {
                    dispatchState(NettyTaskState.Init)
                    dispatchState(NettyTaskState.ConnectionActive(socketChannel))
                }
                socketChannel.pipeline()
                    .addLast(IdleStateHandler(idleLimitDuration, 0, 0, TimeUnit.MILLISECONDS))// 超时时间
                    .addLast(
                        LengthFieldBasedFrameDecoder(Int.MAX_VALUE,
                            /** length 长度偏移量 **/ /** length 长度偏移量 **/0,
                            /** 长度 **/ /** 长度 **/4, 0, 4)
                    )
                    .addLast(LengthFieldPrepender(4))
                    .addLast(BytesToPackageDataDecoder())
                    .addLast(PackageDataToBytesEncoder())
                    .addLast(object : ChannelDuplexHandler() {

                        override fun channelInactive(ctx: ChannelHandlerContext?) {
                            super.channelInactive(ctx)
                            ctx?.close()
                        }

                        override fun channelRead(
                            ctx: ChannelHandlerContext,
                            msg: Any?
                        ) {
                            if (msg != null && msg is PackageData) {
                                for (o in observers) {
                                    o.onNewMessage(
                                        localAddress,
                                        remoteAddress,
                                        msg,
                                        this@ChildConnectionTask
                                    )
                                }
                            }
                            super.channelRead(ctx, msg)
                        }

                        override fun write(
                            ctx: ChannelHandlerContext?,
                            msg: Any?,
                            promise: ChannelPromise?
                        ) {
                            if (getCurrentState() is NettyTaskState.ConnectionActive) {
                                if (msg is PackageData) {
                                    super.write(ctx, msg, promise)
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

                        override fun exceptionCaught(
                            ctx: ChannelHandlerContext,
                            cause: Throwable
                        ) {
                            ctx.close()
                            throw cause
                        }
                    })
                socketChannel.closeFuture().sync()
                if (getCurrentState() !is NettyTaskState.Error) {
                    dispatchState(NettyTaskState.ConnectionClosed)
                }
            } catch (e: Throwable) {
                if (getCurrentState() != NettyTaskState.ConnectionClosed) {
                    dispatchState(NettyTaskState.Error(e))
                }
            }
        }

    }

    companion object {
        private const val DEFAULT_WRITE_READ_LOOP_THREAD_COUNT = 10
        private const val DEFAULT_CONNECTION_THREAD_COUNT = 1
    }
}