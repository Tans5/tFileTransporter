package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.transferproto.broadcastconn.model.BroadcastTransferFileReq
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import java.net.InetSocketAddress

interface BroadcastSenderObserver {

    fun onNewState(state: BroadcastSenderState) {

    }

    fun requestTransferFile(remoteDevice: RemoteDevice) {

    }
}