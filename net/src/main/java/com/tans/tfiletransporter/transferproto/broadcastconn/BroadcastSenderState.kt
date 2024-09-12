package com.tans.tfiletransporter.transferproto.broadcastconn

import java.net.InetAddress

sealed class BroadcastSenderState {
    data object NoConnection : BroadcastSenderState()
    data object Requesting : BroadcastSenderState()
    data class Active(
        val broadcastAddress: InetAddress
    ) : BroadcastSenderState()
}
