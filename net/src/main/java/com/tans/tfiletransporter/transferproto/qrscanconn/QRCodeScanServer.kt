package com.tans.tfiletransporter.transferproto.qrscanconn

import com.tans.tfiletransporter.transferproto.SimpleObservable
import com.tans.tfiletransporter.transferproto.SimpleStateable
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicReference

class QRCodeScanServer : SimpleObservable<QRCodeScanServerObserver>, SimpleStateable<QRCodeScanState> {

    override val observers: LinkedBlockingDeque<QRCodeScanServerObserver> = LinkedBlockingDeque()

    override val state: AtomicReference<QRCodeScanState> = AtomicReference(QRCodeScanState.NoConnection)

    override fun addObserver(o: QRCodeScanServerObserver) {
        super.addObserver(o)
        o.onNewState(state.get())
    }

    fun startQrCodeScanServer(localAddress: InetAddress) {

    }

}