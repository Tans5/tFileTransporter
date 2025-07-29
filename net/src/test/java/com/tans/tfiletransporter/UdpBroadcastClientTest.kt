package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.*
import com.tans.tfiletransporter.netty.udp.NettyUdpConnectionTask
import com.tans.tlrucache.memory.LruByteArrayPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.Executor

object UdpBroadcastClientTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val ioExecutor: Executor = Dispatchers.IO.asExecutor()
        val localAddress = findLocalAddressV4()[0]
        val broadcastAddress = localAddress.getBroadcastAddress().first
        val task = NettyUdpConnectionTask(
            connectionType = NettyUdpConnectionTask.Companion.ConnectionType.Connect(
                address = broadcastAddress,
                port = 9997
            ),
            enableBroadcast = true
        )

        task.addObserver(object : NettyConnectionObserver {
            override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {
                println("UpdBroadcastClientState: $nettyState")
                if (nettyState is NettyTaskState.ConnectionActive) {
                    ioExecutor.execute {
                        repeat(1000) {
                            Thread.sleep(2000)
                            if (task.getCurrentState() !is NettyTaskState.ConnectionActive) return@execute
                            println("Send broadcast.")
                            val bytes = "This is a broadcast msg.".toByteArray(Charsets.UTF_8)
                            task.sendData(
                                data = PackageDataWithAddress(
                                    receiverAddress = InetSocketAddress(broadcastAddress, 9997),
                                    data = PackageData(
                                        type = 0,
                                        messageId = System.currentTimeMillis(),
                                        body = NetByteArray(
                                            LruByteArrayPool.Companion.ByteArrayValue(value = bytes),
                                            readSize = bytes.size
                                        )
                                    )
                                ),
                                sendDataCallback = null
                            )
                        }
                    }
                }
            }

            override fun onNewMessage(
                localAddress: InetSocketAddress?,
                remoteAddress: InetSocketAddress?,
                msg: PackageData,
                task: INettyConnectionTask
            ) {}
        })
        task.startTask()
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}