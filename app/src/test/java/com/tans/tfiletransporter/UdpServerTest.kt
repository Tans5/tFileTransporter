package com.tans.tfiletransporter

import com.tans.tfiletransporter.logs.ILog
import com.tans.tfiletransporter.netty.*
import com.tans.tfiletransporter.netty.extensions.ConnectionServerImpl
import com.tans.tfiletransporter.netty.extensions.IServer
import com.tans.tfiletransporter.netty.extensions.withServer
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask.Companion.ConnectionType

object UdpServerTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val task = NettyUdpConnectionTask(ConnectionType.Bind(localAddress, 9999), true)
            .withServer<ConnectionServerImpl>(log = TestLog)

        task.registerServer(object : IServer<String, String> {

            override val requestClass: Class<String> = String::class.java
            override val responseClass: Class<String> = String::class.java
            override val replyType: Int = 1
            override val log: ILog = TestLog

            override fun couldHandle(requestType: Int): Boolean {
                return requestType == 0
            }

            override fun onRequest(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                r: String
            ): String {
                return "Hello, Client"
            }

            override fun onNewRequest(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                r: String
            ) {
                println("Receive request $r from $remoteAddress")
            }

        })
        task.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                super.onNewState(nettyState, task)
                println("UpdServerState: $nettyState")
            }
        })
        task.startTask()

        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}