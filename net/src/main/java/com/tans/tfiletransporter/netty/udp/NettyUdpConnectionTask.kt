package com.tans.tfiletransporter.netty.udp

import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.handlers.*
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class NettyUdpConnectionTask(
    private val connectionType: ConnectionType,
    private val enableBroadcast: Boolean = false
) : INettyConnectionTask {

    override val isExecuted: AtomicBoolean = AtomicBoolean(false)
    override val state: AtomicReference<NettyTaskState> = AtomicReference(NettyTaskState.NotExecute)
    override val ioExecutor: Executor by lazy {
        Dispatchers.IO.asExecutor()
    }

    override val observers: LinkedBlockingDeque<NettyConnectionObserver> = LinkedBlockingDeque()

    override fun sendData(
        data: PackageData,
        sendDataCallback: INettyConnectionTask.SendDataCallback?
    ) {
        sendDataCallback?.onFail("Udp not support send tcp data.")
    }

    override fun runTask() {
        val loopEventGroup = NioEventLoopGroup(DEFAULT_LOOP_THREAD_COUNT, ioExecutor)
        try {
            val bootStrap = Bootstrap()
                .group(loopEventGroup)
                .channel(NioDatagramChannel::class.java)
                .option(ChannelOption.SO_BROADCAST, enableBroadcast)
                .option(ChannelOption.SO_REUSEADDR, true)
                .handler(object : ChannelInitializer<NioDatagramChannel>() {
                    override fun initChannel(ch: NioDatagramChannel) {
                        ch.pipeline()
                            .addLast(DatagramDataToPckAddrDataDecoder())
                            .addLast(PckAddrDataToDatagramDataEncoder())
                            .addLast(CheckerHandler(this@NettyUdpConnectionTask, ch, true))
                    }
                })

            val channel = when (connectionType) {
                is ConnectionType.Bind -> bootStrap.bind(InetSocketAddress(connectionType.address, connectionType.port)).sync().channel()
                is ConnectionType.Connect -> bootStrap.connect(InetSocketAddress(connectionType.address, connectionType.port)).sync().channel()
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
            loopEventGroup.shutdownGracefully()
        }
    }

    companion object {
        private const val DEFAULT_LOOP_THREAD_COUNT = 10
        sealed class ConnectionType {
            data class Bind(
                val address: InetAddress,
                val port: Int
            ) : ConnectionType()

            data class Connect(
                val address: InetAddress,
                val port: Int
            ) : ConnectionType()
        }
    }
}