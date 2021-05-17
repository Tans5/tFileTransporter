package com.tans.tfiletransporter

import com.tans.tfiletransporter.utils.copyAvailableBytes
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.reactivex.Observable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.rx2.asFlow
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NettyTest {

    companion object {
        val NETTY_SERVER_PORT: Int = 1234
    }

    fun startNettyServer(): Observable<ChannelHandlerContext> = Observable.create<ChannelHandlerContext> { emitter ->
        val serverBootstrap = ServerBootstrap()
        val eventLoopGroup = NioEventLoopGroup()
        serverBootstrap
            .group(eventLoopGroup)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 30)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(object : ChannelInitializer<NioSocketChannel>() {
                override fun initChannel(ch: NioSocketChannel?) {
                    ch?.pipeline()?.addLast(object : ChannelDuplexHandler() {
                        override fun channelActive(ctx: ChannelHandlerContext?) {
                            super.channelActive(ctx)
                            if (ctx != null) {
                                emitter.onNext(ctx)
                            }
                        }

                        override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
                            if (msg != null && ctx != null) {
                                val buffer = (msg as ByteBuf).nioBuffer()
                                println(String(buffer.copyAvailableBytes()))
                                ctx.writeAndFlush(Unpooled.copiedBuffer("Msg from server，Port", Charset.defaultCharset())).sync()
                            }
                        }

                    })
                }
            })
            .bind(NETTY_SERVER_PORT)
            .addListener {
                if (it.isSuccess) {
                    println("Bind Success!!")
                } else {
                    println("Bind Fail!!")
                    emitter.onError(Throwable("Bind Fail!!"))
                }
            }
    }

    suspend fun startClient() = suspendCancellableCoroutine<ChannelHandlerContext> { cont ->
        val bootStrap = Bootstrap()
        val eventLoopGroup = NioEventLoopGroup()
        val future = bootStrap
            .group(eventLoopGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(object : ChannelInitializer<NioSocketChannel>() {
                override fun initChannel(ch: NioSocketChannel?) {
                    ch?.pipeline()?.addLast(object : SimpleChannelInboundHandler<ByteBuf>() {
                        override fun channelActive(ctx: ChannelHandlerContext?) {
                            super.channelActive(ctx)
                            if (ctx != null) {
                                cont.resume(ctx)
                            }
                        }

                        override fun channelRead0(ctx: ChannelHandlerContext?, msg: ByteBuf?) {
                            if (msg != null) {
                                println(msg.toString(Charset.defaultCharset()))
                            }
                        }
                    })
                }

            })
            .connect(InetSocketAddress(NETTY_SERVER_PORT))
            .addListener {
                if (!it.isSuccess) {
                    cont.resumeWithException(Throwable("Fail!!"))
                }
            }
        cont.invokeOnCancellation {
            future.sync().cancel(true)
        }
    }

    @Test
    fun testNet() = runBlocking {
        val job1 = launch {
            startNettyServer()
                .asFlow()
                .collect {

                }

        }

        val job2 = launch {
            delay(100)
            coroutineScope {
                launch {
                    val result = startClient()
                    val buffer = Unpooled.copiedBuffer("Hello, World1", Charset.defaultCharset())
                    result.writeAndFlush(buffer).sync()
                }
                launch {
                    val result = startClient()
                    val buffer = Unpooled.copiedBuffer("Hello, World2", Charset.defaultCharset())
                    result.writeAndFlush(buffer).sync()
                }
            }
        }
        job1.join()
        job2.join()
    }

}