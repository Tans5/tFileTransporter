package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.findLocalAddressV4
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanClient
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanObserver
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object QRCodeScanClientTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val localAddress = findLocalAddressV4()[0]
        val server = QRCodeScanClient(log = TestLog)
        server.addObserver(object : QRCodeScanObserver {

            override fun onNewState(state: QRCodeScanState) {
                println("Client state: $state")
            }

        })
        server.startQRCodeScanClient(
            serverAddress = localAddress,
            callback = object : SimpleCallback<Unit> {
                override fun onSuccess(data: Unit) {
                    println("Client connect success")
                    server.requestFileTransfer(
                        targetAddress = localAddress,
                        deviceName = "TestDeviceName",
                        simpleCallback = object : SimpleCallback<Unit> {

                            override fun onSuccess(data: Unit) {
                                println("Client request transfer success.")
                            }

                            override fun onError(errorMsg: String) {
                                println("Client request transfer fail: $errorMsg")
                            }
                        })
                }

                override fun onError(errorMsg: String) {
                    println("Client $errorMsg")
                }
            }
        )
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}