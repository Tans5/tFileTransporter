package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice

interface BroadcastSenderObserver {

    fun onNewState(state: BroadcastSenderState)

    fun requestTransferFile(remoteDevice: RemoteDevice)
}