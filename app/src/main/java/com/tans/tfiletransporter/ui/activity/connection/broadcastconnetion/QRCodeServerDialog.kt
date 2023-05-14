package com.tans.tfiletransporter.ui.activity.connection.broadcastconnetion

import android.app.Activity
import android.os.Bundle
import com.jakewharton.rxbinding4.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.QrCodeServerDialogBinding
import com.tans.tfiletransporter.file.LOCAL_DEVICE
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.netty.toInt
import com.tans.tfiletransporter.transferproto.TransferProtoConstant
import com.tans.tfiletransporter.transferproto.qrscanconn.model.QRCodeShare
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import com.tans.tfiletransporter.utils.toJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.glxn.qrgen.android.QRCode
import java.net.InetAddress

class QRCodeServerDialog(
    private val context: Activity,
    private val localAddress: InetAddress
) : BaseCustomDialog<QrCodeServerDialogBinding, Unit>(
    context = context,
    layoutId = R.layout.qr_code_server_dialog,
    defaultState = Unit,
    outSizeCancelable = false
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
    }

    override fun bindingStart(binding: QrCodeServerDialogBinding) {
        binding.cancelButton.clicks()
            .doOnNext {
                cancel()
            }
            .bindLife()

        launch {
            val qrcodeBitmap = withContext(Dispatchers.IO) {
                try {
                    val qrcodeContent = QRCodeShare(
                        version = TransferProtoConstant.VERSION,
                        deviceName = LOCAL_DEVICE,
                        address = localAddress.toInt()
                    ).toJson()
                    if (qrcodeContent != null) {
                        QRCode.from(qrcodeContent).withSize(320, 320).bitmap()
                    } else {
                        null
                    }
                } catch (e: Throwable) {
                    AndroidLog.e(TAG, "Create qrcode fail: ${e.message}", e)
                    null
                }
            }
            if (qrcodeBitmap != null) {
                binding.qrCodeIv.setImageBitmap(qrcodeBitmap)
            }
        }
    }

    companion object {
        private const val TAG = "QRCodeServerDialog"
    }

}