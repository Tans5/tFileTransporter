package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.tcp.NettyTcpServerConnectionTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executor

object TcpServerTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val ioExecutor: Executor = Dispatchers.IO.asExecutor()
        val localAddress = findLocalAddressV4()[0]
        val serverTask = NettyTcpServerConnectionTask(
            bindAddress = localAddress,
            bindPort = 1996,
            newClientTaskCallback = { newClientTask ->
                println("NewClientTask: $newClientTask")
                newClientTask.addObserver(object : NettyConnectionObserver {
                    override fun onNewState(
                        nettyState: NettyTaskState,
                        task: INettyConnectionTask
                    ) {
                        println("ClientTaskState: $nettyState")
                        if (nettyState is NettyTaskState.ConnectionActive) {
                            ioExecutor.execute {
                                repeat(1000) {
                                    Thread.sleep(2000)
                                    task.sendData(PackageData(0, it.toLong(), "Hello, Client".toByteArray(Charsets.UTF_8)), null)
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
                        println("Receive message from client: ${msg.body.toString(Charsets.UTF_8)}")
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