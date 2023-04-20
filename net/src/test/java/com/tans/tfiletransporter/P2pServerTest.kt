package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.findLocalAddressV4
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnection
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnectionObserver
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object P2pServerTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val p2pConnection = P2pConnection(
            currentDeviceName = "Server_Device",
            log = TestLog
        )
        p2pConnection.addObserver(object : P2pConnectionObserver {
            override fun onNewState(state: P2pConnectionState) {
                super.onNewState(state)
                println("Server State: $state")
            }
        })
        p2pConnection.bind(
            address = localAddress,
            simpleCallback = object : SimpleCallback<Unit> {
                override fun onSuccess(data: Unit) {
                    println("Server connection success.")
                }

                override fun onError(errorMsg: String) {
                    println("Server connection fail: $errorMsg")
                }
            }
        )
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}