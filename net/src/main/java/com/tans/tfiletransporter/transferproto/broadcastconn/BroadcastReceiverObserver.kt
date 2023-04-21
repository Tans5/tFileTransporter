package com.tans.tfiletransporter.transferproto.broadcastconn

import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice

interface BroadcastReceiverObserver {

    fun onNewState(state: BroadcastReceiverState)

    fun onNewBroadcast(remoteDevice: RemoteDevice)

    fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>)
}