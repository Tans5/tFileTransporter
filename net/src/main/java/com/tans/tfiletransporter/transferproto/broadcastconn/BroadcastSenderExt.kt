package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.resumeExceptionIfActive
import com.tans.tfiletransporter.resumeIfActive
import com.tans.tfiletransporter.transferproto.SimpleCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress

suspend fun BroadcastSender.startSenderSuspend(localAddress: InetAddress) = suspendCancellableCoroutine<Unit> { cont ->
    startBroadcastSender(localAddress, object : SimpleCallback<Unit> {

        override fun onError(errorMsg: String) {
            cont.resumeExceptionIfActive(Throwable(errorMsg))
        }

        override fun onSuccess(data: Unit) {
            cont.resumeIfActive(data)
        }
    })
}

suspend fun BroadcastSender.waitClose() = suspendCancellableCoroutine<Unit> { cont ->
    addObserver(object : BroadcastSenderObserver {
        override fun onNewState(state: BroadcastSenderState) {
            if (state is BroadcastSenderState.NoConnection) {
                cont.resumeIfActive(Unit)
            }
        }
    })
}