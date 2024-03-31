package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.netty.extensions.ConnectionClientImpl
import com.tans.tfiletransporter.netty.extensions.ConnectionServerImpl
import java.net.InetAddress
import java.util.concurrent.ScheduledFuture

sealed class BroadcastSenderState {
    data object NoConnection : BroadcastSenderState()
    data object Requesting : BroadcastSenderState()
    data class Active(
        val broadcastAddress: InetAddress
    ) : BroadcastSenderState()
}
