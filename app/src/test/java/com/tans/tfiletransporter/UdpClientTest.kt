package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.*
import com.tans.tfiletransporter.netty.extensions.ConnectionClientImpl
import com.tans.tfiletransporter.netty.extensions.IClientManager
import com.tans.tfiletransporter.netty.extensions.witchClient
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask
import io.netty.channel.DefaultAddressedEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.Executor

object UdpClientTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val ioExecutor: Executor = Dispatchers.IO.asExecutor()
        val localAddress = findLocalAddressV4()[0]
        val broadcastAddress = localAddress.getBroadcastAddress().first
        val task = NettyUdpConnectionTask(
            bindAddress = broadcastAddress,
            bindPort = 9998,
            receiveBroadCast = true
        ).witchClient<ConnectionClientImpl>(log = TestLog)

        task.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, taskLocal: INettyConnectionTask) {
                super.onNewState(nettyState, taskLocal)
                println("UdpClientState: $nettyState")
                ioExecutor.execute {
                    repeat(1000) {
                        Thread.sleep(2000)
                        task.request<String, String>(
                            type = 0,
                            request = "Hello, Server",
                            requestClass = String::class.java,
                            responseClass = String::class.java,
                            targetAddress = InetSocketAddress(broadcastAddress, 9999),
                            senderAddress = InetSocketAddress(broadcastAddress, 9998),
                            callback = object : IClientManager.RequestCallback<String> {
                                override fun onSuccess(
                                    type: Int,
                                    messageId: Long,
                                    localAddress: InetSocketAddress?,
                                    remoteAddress: InetSocketAddress?,
                                    d: String
                                ) {
                                    println("Request result: $d from $remoteAddress reply")
                                }

                                override fun onFail(errorMsg: String) {
                                    println("Request fail: $errorMsg")
                                }

                            }
                        )
                    }
                }
            }
        })
        task.startTask()

        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}