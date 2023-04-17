package com.tans.tfiletransporter

import com.tans.tfiletransporter.logs.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.extensions.*
import com.tans.tfiletransporter.netty.tcp.NettyTcpServerConnectionTask
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

object TcpServerTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val serverTask = NettyTcpServerConnectionTask(
            bindAddress = localAddress,
            bindPort = 1996,
            newClientTaskCallback = { newClientTask ->
                println("NewClientTask: $newClientTask")
                val serverConnection = newClientTask
                    .withServer<ConnectionServerImpl>(log = TestLog)
                    .witchClient<ConnectionServerClientImpl>(log = TestLog)
                serverConnection
                    .registerServer(object : IServer<String, String> {
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
                            return "Hello, Client."
                        }

                        override fun onNewRequest(
                            localAddress: InetSocketAddress?,
                            remoteAddress: InetSocketAddress?,
                            r: String
                        ) {
                            println("Receive client request: $r from $remoteAddress")
                        }

                    })
                serverConnection.addObserver(object : NettyConnectionObserver {
                    override fun onNewState(
                        nettyState: NettyTaskState,
                        task: INettyConnectionTask
                    ) {
                        println("ClientTaskState: $nettyState")
                    }
                })
            }
        )

        serverTask.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                println("ServerTaskState: $nettyState")
            }
        })
        serverTask.startTask()
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }

    fun findLocalAddressV4(): List<InetAddress> {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        val result = ArrayList<InetAddress>()
        while (interfaces.hasMoreElements()) {
            val inetAddresses = interfaces.nextElement().inetAddresses
            while (inetAddresses.hasMoreElements()) {
                val address = inetAddresses.nextElement()
                if (address.address.size == 4 && !address.isLinkLocalAddress && !address.isLoopbackAddress) {
                    result.add(address)
                }
            }
        }
        return result
    }
}