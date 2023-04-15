package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.*
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.Executor

object UdpTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val ioExecutor: Executor = Dispatchers.IO.asExecutor()
        val localAddress = TcpServerTest.findLocalAddressV4()[0]
        val task1 = NettyUdpConnectionTask(localAddress, 9999)
        task1.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                super.onNewState(nettyState, task)
                println("Task1State: $nettyState")
                if (nettyState is NettyTaskState.ConnectionActive) {
                    ioExecutor.execute {
                        repeat(1000) {
                            Thread.sleep(2000)
                            val data = PackageData(0, it.toLong(), "Hello, Task2".toByteArray(Charsets.UTF_8))
                            task.sendData(PackageDataWithAddress(InetSocketAddress(localAddress, 9998), data), null)
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
                println("Task1Receive: ${msg.body.toString(Charsets.UTF_8)}, from: $remoteAddress")
            }
        })
        task1.startTask()

        val task2 = NettyUdpConnectionTask(localAddress, 9998)
        task2.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                super.onNewState(nettyState, task)
                println("Task2State: $nettyState")
                if (nettyState is NettyTaskState.ConnectionActive) {
                    ioExecutor.execute {
                        repeat(1000) {
                            Thread.sleep(2000)
                            val data = PackageData(0, it.toLong(), "Hello, Task1".toByteArray(Charsets.UTF_8))
                            task.sendData(PackageDataWithAddress(InetSocketAddress(localAddress, 9998), data), null)
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
                println("Task2Receive: ${msg.body.toString(Charsets.UTF_8)}, from: $remoteAddress")
            }
        })
        task2.startTask()
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}