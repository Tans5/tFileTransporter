package com.tans.tfiletransporter.transferproto.qrscanconn

import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice

interface QRCodeScanServerObserver : QRCodeScanObserver {

    fun requestTransferFile(remoteDevice: RemoteDevice)
}