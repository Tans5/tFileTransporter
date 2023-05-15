package com.tans.tfiletransporter.transferproto.qrscanconn

import com.tans.tfiletransporter.resumeExceptionIfActive
import com.tans.tfiletransporter.resumeIfActive
import com.tans.tfiletransporter.transferproto.SimpleCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress

suspend fun QRCodeScanServer.startQRCodeScanServerSuspend(localAddress: InetAddress): Unit =
    suspendCancellableCoroutine { cont ->
        startQRCodeScanServer(
            localAddress = localAddress,
            callback = object : SimpleCallback<Unit> {
                override fun onSuccess(data: Unit) {
                    cont.resumeIfActive(Unit)
                }

                override fun onError(errorMsg: String) {
                    cont.resumeExceptionIfActive(Throwable(errorMsg))
                }
            }
        )
    }


suspend fun QRCodeScanClient.startQRCodeScanClientSuspend(serverAddress: InetAddress): Unit =
    suspendCancellableCoroutine { cont ->
        startQRCodeScanClient(
            serverAddress = serverAddress,
            callback = object : SimpleCallback<Unit> {
                override fun onSuccess(data: Unit) {
                    cont.resumeIfActive(Unit)
                }

                override fun onError(errorMsg: String) {
                    cont.resumeExceptionIfActive(Throwable(errorMsg))
                }
            }
        )
    }

suspend fun QRCodeScanClient.requestFileTransferSuspend(targetAddress: InetAddress, deviceName: String): Unit =
    suspendCancellableCoroutine { cont ->
        requestFileTransfer(
            targetAddress = targetAddress,
            deviceName = deviceName,
            simpleCallback = object : SimpleCallback<Unit> {
                override fun onSuccess(data: Unit) {
                    cont.resumeIfActive(Unit)
                }

                override fun onError(errorMsg: String) {
                    cont.resumeExceptionIfActive(Throwable(errorMsg))
                }
            }
        )
    }