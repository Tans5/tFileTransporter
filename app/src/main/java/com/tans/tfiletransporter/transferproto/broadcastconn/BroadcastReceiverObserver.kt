package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastMsg
import java.net.InetSocketAddress

interface BroadcastReceiverObserver {

    fun onNewState(state: BroadcastReceiverState) {

    }

    fun onNewBroadcast(remoteAddress: InetSocketAddress, broadcastMsg: BroadcastMsg) {

    }
}