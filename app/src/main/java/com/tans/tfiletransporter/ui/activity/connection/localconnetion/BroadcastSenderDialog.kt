package com.tans.tfiletransporter.ui.activity.connection.localconnetion

import android.app.Activity
import com.jakewharton.rxbinding4.view.clicks
import com.tans.rxutils.ignoreSeveralClicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastSenderDialogLayoutBinding
import com.tans.tfiletransporter.file.LOCAL_DEVICE
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.netty.getBroadcastAddress
import com.tans.tfiletransporter.resumeExceptionIfActive
import com.tans.tfiletransporter.resumeIfActive
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastSender
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastSenderObserver
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastSenderState
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import com.tans.tfiletransporter.transferproto.broadcastconn.startSenderSuspend
import com.tans.tfiletransporter.transferproto.broadcastconn.waitClose
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import com.tans.tfiletransporter.utils.showToastShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BroadcastSenderDialog(
    private val activity: Activity,
    private val localAddress: InetAddress,
    private val simpleCallback: SimpleCallback<RemoteDevice>
) : BaseCustomDialog<BroadcastSenderDialogLayoutBinding, Unit>(
    context = activity,
    layoutId = R.layout.broadcast_sender_dialog_layout,
    defaultState = Unit,
    outSizeCancelable = false
) {

    private val hasInvokeCallback: AtomicBoolean = AtomicBoolean(false)

    private val sender: AtomicReference<BroadcastSender?> = AtomicReference(null)
    override fun bindingStart(binding: BroadcastSenderDialogLayoutBinding) {
        launch {
            val sender = BroadcastSender(
                deviceName = LOCAL_DEVICE,
                log = AndroidLog
            )
            this@BroadcastSenderDialog.sender.set(sender)
            runCatching {
                withContext(Dispatchers.IO) {
                    sender.startSenderSuspend(localAddress, localAddress.getBroadcastAddress().first)
                }
            }.onSuccess {
                AndroidLog.d(TAG, "Start sender success.")
                sender.addObserver(object : BroadcastSenderObserver {
                    override fun requestTransferFile(remoteDevice: RemoteDevice) {
                        callbackSuccessSafe(remoteDevice)
                        cancel()
                    }

                    override fun onNewState(state: BroadcastSenderState) {
                    }
                })

                binding.cancelButton.clicks()
                    .ignoreSeveralClicks()
                    .doOnNext {
                        callbackErrorSafe("User canceled.")
                        cancel()
                    }
                    .bindLife()

                sender.waitClose()
                activity.showToastShort("Connection closed")
                callbackErrorSafe("Connection closed.")
            }.onFailure {
                callbackErrorSafe(it.message ?: "")
                AndroidLog.e(TAG, "Start sender error: ${it.message}")
                activity.showToastShort(R.string.error_toast)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        callbackErrorSafe("System close dialog.")
        Dispatchers.IO.asExecutor().execute {
            Thread.sleep(1000)
            sender.get()?.closeConnectionIfActive()
        }
    }

    private fun callbackSuccessSafe(r: RemoteDevice) {
        if (hasInvokeCallback.compareAndSet(false, true)) {
            simpleCallback.onSuccess(r)
        }
    }

    private fun callbackErrorSafe(msg: String) {
        if (hasInvokeCallback.compareAndSet(false, true)) {
            simpleCallback.onError(msg)
        }
    }

    companion object {
        private const val TAG = "BroadcastSenderDialog"
    }
}

suspend fun Activity.showSenderDialog(localAddress: InetAddress): RemoteDevice = suspendCancellableCoroutine { cont ->
    BroadcastSenderDialog(
        activity = this,
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