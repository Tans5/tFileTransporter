package com.tans.tfiletransporter.ui.activity.connection.broadcastconnetion

import android.app.Activity
import android.os.Bundle
import com.jakewharton.rxbinding4.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.QrCodeServerDialogBinding
import com.tans.tfiletransporter.file.LOCAL_DEVICE
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.netty.toInt
import com.tans.tfiletransporter.resumeExceptionIfActive
import com.tans.tfiletransporter.resumeIfActive
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.TransferProtoConstant
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanServer
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanServerObserver
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanState
import com.tans.tfiletransporter.transferproto.qrscanconn.model.QRCodeShare
import com.tans.tfiletransporter.transferproto.qrscanconn.startQRCodeScanServerSuspend
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import com.tans.tfiletransporter.utils.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.glxn.qrgen.android.QRCode
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class QRCodeServerDialog(
    private val context: Activity,
    private val localAddress: InetAddress,
    private val simpleCallback: SimpleCallback<RemoteDevice>
) : BaseCustomDialog<QrCodeServerDialogBinding, Unit>(
    context = context,
    layoutId = R.layout.qr_code_server_dialog,
    defaultState = Unit,
    outSizeCancelable = false
) {

    private val hasInvokeCallback: AtomicBoolean = AtomicBoolean(false)

    private val qrcodeServer: QRCodeScanServer by lazy {
        QRCodeScanServer(log = AndroidLog)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
    }

    override fun bindingStart(binding: QrCodeServerDialogBinding) {
        binding.cancelButton.clicks()
            .doOnNext {
                errorCallback("User cancel.")
                cancel()
            }
            .bindLife()

        launch(Dispatchers.IO) {
            qrcodeServer.addObserver(object : QRCodeScanServerObserver {
                override fun requestTransferFile(remoteDevice: RemoteDevice) {
                    AndroidLog.d(TAG, "Receive request: $remoteDevice")
                    successCallback(remoteDevice)
                    context.runOnUiThread {
                        cancel()
                    }
                }

                override fun onNewState(state: QRCodeScanState) {
                    AndroidLog.d(TAG, "Qrcode server state: $state")
                }
            })
            runCatching {
                qrcodeServer.startQRCodeScanServerSuspend(localAddress = localAddress)
            }.onSuccess {
                AndroidLog.d(TAG, "Bind address success.")
                runCatching {
                    val qrcodeContent = QRCodeShare(
                        version = TransferProtoConstant.VERSION,
                        deviceName = LOCAL_DEVICE,
                        address = localAddress.toInt()
                    ).toJson()!!
                    QRCode.from(qrcodeContent).withSize(320, 320).bitmap()
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        binding.qrCodeIv.setImageBitmap(it)
                    }
                }.onFailure {
                    AndroidLog.e(TAG, "Create qrcode fail: ${it.message}", it)
                    errorCallback("Create qrcode bitmap fail.")
                    withContext(Dispatchers.Main) {
                        cancel()
                    }
                }
            }.onFailure {
                val eMsg = "Bind address: $localAddress fail"
                AndroidLog.e(TAG, eMsg)
                errorCallback(eMsg)
                withContext(Dispatchers.Main) {
                    cancel()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        errorCallback("Dialog destroyed.")
        Dispatchers.IO.asExecutor().execute {
            Thread.sleep(1000)
            qrcodeServer.closeConnectionIfActive()
        }
    }

    private fun errorCallback(msg: String) {
        if (hasInvokeCallback.compareAndSet(false, true)) {
            simpleCallback.onError(msg)
        }
    }

    private fun successCallback(remoteDevice: RemoteDevice) {
        if (hasInvokeCallback.compareAndSet(false, true)) {
            simpleCallback.onSuccess(remoteDevice)
        }
    }

    companion object {
        private const val TAG = "QRCodeServerDialog"
    }

}

suspend fun Activity.showQRCodeServerDialogSuspend(localAddress: InetAddress): RemoteDevice = suspendCancellableCoroutine { cont ->
    QRCodeServerDialog(
        context = this,
        localAddress = localAddress,
        simpleCallback = object : SimpleCallback<RemoteDevice> {
            override fun onSuccess(data: RemoteDevice) {
                cont.resumeIfActive(data)
            }
            override fun onError(errorMsg: String) {
                cont.resumeExceptionIfActive(Throwable(errorMsg))
            }
        }
    ).show()
}