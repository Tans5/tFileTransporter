package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.findLocalAddressV4
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastSender
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastSenderObserver
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastSenderState
import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastTransferFileReq
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress

object BroadcastSenderTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val sender = BroadcastSender(
            deviceName = "TestSender",
            log = TestLog
        )
        sender.addObserver(object : BroadcastSenderObserver {

            override fun onNewState(state: BroadcastSenderState) {
                println("Sender state: $state")
            }

            override fun requestTransferFile(
                remoteDevice: RemoteDevice
            ) {
                println("Sender receive transfer request: $remoteDevice")
            }
        })
        sender.startBroadcastSender(
            localAddress = localAddress,
            simpleCallback = object : SimpleCallback<Unit> {
                override fun onSuccess(data: Unit) {
                    super.onSuccess(data)
                    println("Sender start Success")
                }

                override fun onError(errorMsg: String) {
                    super.onError(errorMsg)
                    println("Sender start fail: $errorMsg")
                }
            }
        )
        runBlocking {
            delay(60 * 1000 * 50)
        }
    }
}