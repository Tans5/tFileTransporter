package com.tans.tfiletransporter.transferproto.qrscanconn

interface QRCodeScanObserver {

    fun onNewState(state: QRCodeScanState)
}