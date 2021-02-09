package com.tans.tfiletransporter.ui.activity.connection

import android.app.Activity
import android.app.Dialog
import android.util.Log
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.emptyView
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastReceiverDialogLayoutBinding
import com.tans.tfiletransporter.databinding.RemoteServerEmptyItemLayoutBinding
import com.tans.tfiletransporter.databinding.RemoteServerItemLayoutBinding
import com.tans.tfiletransporter.net.RemoteDevice
import com.tans.tfiletransporter.net.launchBroadcastReceiver
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showLoadingDialog
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*


fun Activity.showBroadcastReceiverDialog(localAddress: InetAddress, noneBroadcast: Boolean = false): Single<Optional<RemoteDevice>> {
    var dialog: Dialog? = null
    return Single.create<Optional<RemoteDevice>> { emitter ->

        val dialogInternal = object : BaseCustomDialog<BroadcastReceiverDialogLayoutBinding, Unit>(
                context = this,
                layoutId = R.layout.broadcast_receiver_dialog_layout,
                defaultState = Unit,
                outSizeCancelable = false
        ) {

            override fun bindingStart(binding: BroadcastReceiverDialogLayoutBinding) {
                binding.cancelButton.clicks()
                        .doOnNext {
                            if (isShowing) {
                                cancel()
                            }
                            if (!emitter.isDisposed) {
                                emitter.onSuccess(Optional.empty())
                            }
                        }
                        .bindLife()
                launch {
                    val result = runCatching {
                        launchBroadcastReceiver(localAddress = localAddress, noneBroadcast = noneBroadcast) { receiverJob: Job ->
                            withContext(Dispatchers.Main) {
                                binding.serversRv.adapter = SimpleAdapterSpec<RemoteDevice, RemoteServerItemLayoutBinding>(
                                        layoutId = R.layout.remote_server_item_layout,
                                        bindData = { _, data, binding ->
                                            binding.ipAddress = (data.first as InetSocketAddress).address.hostAddress
                                            binding.device = data.second
                                        },
                                        dataUpdater = bindRemoveDevice(),
                                        differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1.first == d2.first }),
                                        itemClicks = listOf { binding, _ ->
                                            binding.root to { _, data ->
                                                rxSingle {
                                                    val loadingDialog = withContext(Dispatchers.Main) { showLoadingDialog(cancelable = false) }
                                                    val result = withContext(Dispatchers.IO) {
                                                        runCatching { connectTo((data.first as InetSocketAddress).address) }
                                                    }
                                                    withContext(Dispatchers.Main) { loadingDialog.dismiss() }
                                                    if (result.getOrNull() == true) {
                                                        emitter.onSuccess(Optional.of(data))
                                                        receiverJob.cancel()
                                                    }
                                                }
                                            }
                                        }
                                ).emptyView<RemoteDevice, RemoteServerItemLayoutBinding, RemoteServerEmptyItemLayoutBinding>(
                                        emptyLayout = R.layout.remote_server_empty_item_layout,
                                        initShowEmpty = true)
                                        .toAdapter()
                            }
                        }
                    }
                    if (result.isFailure) {
                        Log.e("BroadcastReceiverDialog", result.exceptionOrNull().toString())
                    }
                    if (!emitter.isDisposed) {
                        emitter.onSuccess(Optional.empty())
                    }
                }
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