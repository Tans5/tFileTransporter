package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.netty.extensions.ConnectionClientImpl
import com.tans.tfiletransporter.netty.extensions.ConnectionServerImpl

sealed class BroadcastReceiverState {
    data object NoConnection : BroadcastReceiverState()

    data object Requesting : BroadcastReceiverState()

    data object Active : BroadcastReceiverState()
}