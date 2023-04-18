package com.tans.tfiletransporter.transferproto.p2pconn

import java.net.InetSocketAddress

sealed class P2pConnectionState {

    object NoConnection : P2pConnectionState()

    class Active(
        val localAddress: InetSocketAddress?,
        val remoteAddress: InetSocketAddress?
    ) : P2pConnectionState()

    data class Handshake(
        val localAddress: InetSocketAddress,
        val remoteAddress: InetSocketAddress,
        val remoteDeviceName: String
    ) : P2pConnectionState()
}