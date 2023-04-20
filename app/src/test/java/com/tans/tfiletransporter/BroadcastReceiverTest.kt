package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.findLocalAddressV4
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastReceiver
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastReceiverObserver
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastReceiverState
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastMsg
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

object BroadcastReceiverTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val receiver = BroadcastReceiver(
            deviceName = "TestReceiver",
            log = TestLog
        )

        receiver.addObserver(object : BroadcastReceiverObserver {
            override fun onNewState(state: BroadcastReceiverState) {
                super.onNewState(state)
                println("Receiver state: $state")
            }

            override fun onNewBroadcast(
                remoteAddress: InetSocketAddress,
                broadcastMsg: BroadcastMsg
            ) {
               println("Receiver receive broadcast: $broadcastMsg, $remoteAddress")
            }
        })

        receiver.startBroadcastReceiver(
            localAddress = localAddress,
            simpleCallback = object : SimpleCallback<Unit> {
                override fun onError(errorMsg: String) {
                    println("Receiver start error: $errorMsg")
                }

                override fun onSuccess(data: Unit) {
                    super.onSuccess(data)
                    println("Receiver start success")
                }
            }
        )

        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}