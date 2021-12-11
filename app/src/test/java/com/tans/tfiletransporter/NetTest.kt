package com.tans.tfiletransporter

import com.tans.tfiletransporter.net.netty.common.NettyPkg
import com.tans.tfiletransporter.net.netty.common.handler.writePkg
import com.tans.tfiletransporter.net.netty.common.handler.writePkgBlockReply
import com.tans.tfiletransporter.net.netty.common.setDefaultHandler
import com.tans.tfiletransporter.utils.ioExecutor
import com.tans.tfiletransporter.utils.toBytes
import com.tans.tfiletransporter.utils.toLong
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import java.net.InetSocketAddress

class NetTest {

//    @Test
//    fun nettyTest() {
//        // Server
//        val st = Thread {
//            val readAndWriteWorkGroup = NioEventLoopGroup(10, ioExecutor)
//            val connectWorkGroup = NioEventLoopGroup(1, ioExecutor)
//            try {
//                var channel: Channel? = null
//                val serverBootstrap = ServerBootstrap()
//                    .group(connectWorkGroup, readAndWriteWorkGroup)
//                    .channel(NioServerSocketChannel::class.java)
//                    .childHandler(object : ChannelInitializer<SocketChannel>() {
//                        override fun initChannel(ch: SocketChannel?) {
//                            println("Client ip: ${ch?.remoteAddress()}")
//                            ch?.pipeline()
//                                ?.setDefaultHandler()
//                                ?.addLast(object : ChannelDuplexHandler() {
//                                    override fun channelActive(ctx: ChannelHandlerContext?) {
//                                        if (ctx != null) {
//                                            ioExecutor.execute {
//                                                for (i in 0 until 1000) {
//                                                    ctx.writePkgBlockReply(NettyPkg.TextPkg(text = "Hello, here is server."))
//                                                    Thread.sleep(5000)
//                                                }
//                                                ctx.writePkg(NettyPkg.ClientFinishPkg("ClientFinish"))
//                                            }
//                                        }
//                                    }
//
//                                    override fun channelRead(
//                                        ctx: ChannelHandlerContext?,
//                                        msg: Any?
//                                    ) {
//                                        if (msg != null) {
//                                            println("Server: $msg")
//                                        }
//                                        if (msg is NettyPkg.ServerFinishPkg) {
//                                            channel?.close()
//                                        }
//                                    }
//
//                                })
//                        }
//                    })
//                channel = serverBootstrap.bind(6666).sync().channel()
//                channel.closeFuture().sync()
//            } finally {
//                connectWorkGroup.shutdownGracefully()
//                readAndWriteWorkGroup.shutdownGracefully()
//            }
//        }
//        st.start()
//
//        // Client
//        val ct = Thread {
//
//            val rwGroup = NioEventLoopGroup(10, ioExecutor)
//            try {
//                val clientStrap = Bootstrap()
//                    .group(rwGroup)
//                    .channel(NioSocketChannel::class.java)
//                    .handler(object : ChannelInitializer<SocketChannel>() {
//                        override fun initChannel(ch: SocketChannel?) {
//                            ch?.pipeline()?.setDefaultHandler()
//                                ?.addLast(object : ChannelDuplexHandler() {
//                                    override fun channelActive(ctx: ChannelHandlerContext?) {
//                                        if (ctx != null) {
//                                            println("Client connect success.")
//                                            ioExecutor.execute {
//                                                ioExecutor.execute {
//                                                    for (i in 0 until 1000) {
//                                                        ctx.writePkgBlockReply(NettyPkg.TextPkg(text = "Hello, here is client."))
//                                                        Thread.sleep(5000)
//                                                    }
//                                                    // ctx.writePkg(NettyPkg.ServerFinishPkg("Finish"))
//                                                }
//                                            }
//                                        }
//                                    }
//
//    fun createObservable(): Observable<String> = Observable.create { emitter ->
//        emitter.setCancellable {
//            println("Cancel")
//        }
//        for (i in 0..10) {
//            if (!emitter.isDisposed) {
//                if (i == 5) {
//                    emitter.onError(Throwable("TestError"))
//                } else {
//                    emitter.onNext(i.toString())
//                }
//            }
//            Thread.sleep(1000)
//        }
//        if (!emitter.isDisposed) {
//            emitter.onComplete()
//        }
//    }
}