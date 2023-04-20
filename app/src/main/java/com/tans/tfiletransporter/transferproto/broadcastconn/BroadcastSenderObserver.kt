package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastTransferFileReq
import java.net.InetSocketAddress

interface BroadcastSenderObserver {

    fun onNewState(state: BroadcastSenderState) {

    }

    fun requestTransferFile(remoteAddress: InetSocketAddress, req: BroadcastTransferFileReq) {

    }
}