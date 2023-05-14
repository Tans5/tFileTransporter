package com.tans.tfiletransporter.transferproto.qrscanconn

import com.tans.tfiletransporter.transferproto.SimpleObservable
import com.tans.tfiletransporter.transferproto.SimpleStateable
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicReference

class QRCodeScanClient : SimpleStateable<QRCodeScanState>, SimpleObservable<QRCodeScanObserver> {

    override val observers: LinkedBlockingDeque<QRCodeScanObserver> = LinkedBlockingDeque()

    override val state: AtomicReference<QRCodeScanState> = AtomicReference(QRCodeScanState.NoConnection)

    override fun addObserver(o: QRCodeScanObserver) {
        super.addObserver(o)
        o.onNewState(state.get())
    }

    fun startQrCodeScanClient(localAddress: InetAddress) {

    }
}