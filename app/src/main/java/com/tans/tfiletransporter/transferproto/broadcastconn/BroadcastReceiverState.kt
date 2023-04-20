package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.netty.extensions.ConnectionClientImpl
import com.tans.tfiletransporter.netty.extensions.ConnectionServerImpl

sealed class BroadcastReceiverState {
    object NoConnection : BroadcastReceiverState()

    object Requesting : BroadcastReceiverState()

    data class Active(
        val transferRequestTask: ConnectionClientImpl,
        val receiverTask: ConnectionServerImpl
    ) : BroadcastReceiverState()
}