package com.tans.tfiletransporter.ui.activity.connection

import android.app.Activity
import android.app.Dialog
import android.util.Log
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastSenderDialogLayoutBinding
import com.tans.tfiletransporter.net.RemoteDevice
import com.tans.tfiletransporter.net.launchBroadcastSender
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showOptionalDialog
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*

fun Activity.showBroadcastSenderDialog(localAddress: InetAddress): Single<Optional<RemoteDevice>> {
    var dialog: Dialog? = null
    return Single.create<Optional<RemoteDevice>> { emitter ->
        val dialogInternal = object : BaseCustomDialog<BroadcastSenderDialogLayoutBinding, Unit>(
            context = this,
            layoutId = R.layout.broadcast_sender_dialog_layout,
            defaultState = Unit,
            outSizeCancelable = false
        ) {

            override fun bindingStart(binding: BroadcastSenderDialogLayoutBinding) {
                launch {
                    val result = runCatching {
                        launchBroadcastSender(localAddress = localAddress) { remoteAddress, remoteDevice ->
                            val result = withContext(Dispatchers.Main) {
                                showOptionalDialog(
                                    title = getString(R.string.broadcast_request_connect),
                                    message = getString(R.string.broadcast_remote_info, remoteDevice, (remoteAddress as InetSocketAddress).address.hostAddress),
                                    cancelable = false,
                                    positiveButtonText = getString(R.string.broadcast_request_accept),
                                    negativeButtonText = getString(R.string.broadcast_request_deny)
                                ).await()
                            }
                            result.orElse(false)
                        }
                    }
                    if (!emitter.isDisposed) {
                        if (result.isSuccess) {
                            emitter.onSuccess(Optional.ofNullable(result.getOrNull()))
                        } else {
                            Log.e("Broadcast Sender Error", result.exceptionOrNull().toString())
                            emitter.onSuccess(Optional.empty())
                        }
                    }
                    if (isShowing) {
                        cancel()
                    }
                }
                binding.cancelButton.clicks()
                    .doOnNext {
                        cancel()
                        emitter.onSuccess(Optional.empty())
                    }
                    .bindLife()
            }
        }
        dialogInternal.setCancelable(false)
        dialogInternal.setOnCancelListener { if (!emitter.isDisposed) emitter.onSuccess(Optional.empty()) }
        dialogInternal.show()
        dialog = dialogInternal
    }.doFinally {
        val dialogInternal = dialog
        if (dialogInternal?.isShowing == true) { dialogInternal.cancel() }
    }
}