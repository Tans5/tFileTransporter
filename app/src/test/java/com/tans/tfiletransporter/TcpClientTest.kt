package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.tcp.NettyTcpClientConnectionTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.Executor

object TcpClientTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val ioExecutor: Executor = Dispatchers.IO.asExecutor()
        val localAddress = TcpServerTest.findLocalAddressV4()[0]
        val t = NettyTcpClientConnectionTask(
            serverAddress = localAddress,
            serverPort = 1996
        )
        t.addObserver(object : NettyConnectionObserver {
            override fun onNewState(
                nettyState: NettyTaskState,
                task: INettyConnectionTask
            ) {
                println("ClientTaskState: $nettyState")
                if (nettyState is NettyTaskState.ConnectionActive) {
                    ioExecutor.execute {
                        repeat(1000) {
                            Thread.sleep(2000)
                            task.sendData(PackageData(0, it.toLong(), "Hello, Server".toByteArray(Charsets.UTF_8)), null)
                        }
                    }
                }
            }

            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {
                println("Receive message from server: ${msg.body.toString(Charsets.UTF_8)}")
            }
        })
        t.startTask()
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}