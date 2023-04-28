package com.tans.tfiletransporter.ui.activity.connection.broadcastconnetion

import android.app.Activity
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.emptyView
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastReceiverDialogLayoutBinding
import com.tans.tfiletransporter.databinding.RemoteServerEmptyItemLayoutBinding
import com.tans.tfiletransporter.databinding.RemoteServerItemLayoutBinding
import com.tans.tfiletransporter.file.LOCAL_DEVICE
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.netty.getAndroidBroadcastAddress
import com.tans.tfiletransporter.resumeExceptionIfActive
import com.tans.tfiletransporter.resumeIfActive
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastReceiver
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastReceiverObserver
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastReceiverState
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import com.tans.tfiletransporter.transferproto.broadcastconn.requestFileTransferSuspend
import com.tans.tfiletransporter.transferproto.broadcastconn.startReceiverSuspend
import com.tans.tfiletransporter.transferproto.broadcastconn.waitCloseSuspend
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import com.tans.tfiletransporter.utils.showToastShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull


class BroadcastReceiverDialog(
    private val activity: Activity,
    private val localAddress: InetAddress,
    private val simpleCallback: SimpleCallback<RemoteDevice>
) : BaseCustomDialog<BroadcastReceiverDialogLayoutBinding, BroadcastReceiverDialog.Companion.BroadcastReceiverDialogState>(
    context = activity,
    layoutId = R.layout.broadcast_receiver_dialog_layout,
    defaultState = BroadcastReceiverDialogState(),
    outSizeCancelable = false
) {
    private val hasInvokeCallback: AtomicBoolean = AtomicBoolean(false)
    private val receiver: AtomicReference<BroadcastReceiver?> = AtomicReference(null)
    override fun bindingStart(binding: BroadcastReceiverDialogLayoutBinding) {

        launch {
            val receiver = BroadcastReceiver(deviceName = LOCAL_DEVICE, log = AndroidLog)
            this@BroadcastReceiverDialog.receiver.get()?.closeConnectionIfActive()
            this@BroadcastReceiverDialog.receiver.set(receiver)
            updateState { state -> state.copy(receiver = Optional.of(receiver)) }.await()
            runCatching {
                withContext(Dispatchers.IO) {
                    receiver.startReceiverSuspend(localAddress, getAndroidBroadcastAddress())
                }
            }.onSuccess {
                receiver.addObserver(object : BroadcastReceiverObserver {
                    override fun onNewBroadcast(
                        remoteDevice: RemoteDevice
                    ) {}
                    override fun onNewState(state: BroadcastReceiverState) {}
                    override fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>) {
                        launch { updateState { s -> s.copy(remoteDevices = remoteDevices) }.await() }
                    }
                })
                binding.cancelButton.clicks()
                    .doOnNext {
                        receiver.closeConnectionIfActive()
                        if (hasInvokeCallback.compareAndSet(false, true)) {
                            simpleCallback.onError("Close by user.")
                        }
                        cancel()
                    }
                    .bindLife()
                receiver.waitCloseSuspend()
                if (hasInvokeCallback.compareAndSet(false, true)) {
                    simpleCallback.onError("Connection closed")
                }
                activity.showToastShort("Connection closed")
            }.onFailure {
                withContext(Dispatchers.Main) {
                    activity.showToastShort(R.string.error_toast)
                }
                if (hasInvokeCallback.compareAndSet(false, true)) {
                    simpleCallback.onError("Connection error: ${it.message}")
                }
            }
        }
        launch {
            binding.serversRv.adapter = SimpleAdapterSpec<RemoteDevice, RemoteServerItemLayoutBinding>(
                layoutId = R.layout.remote_server_item_layout,
                bindData = { _, data, binding ->
                    binding.ipAddress = data.remoteAddress.address.toString().removePrefix("/")
                    binding.device = data.deviceName
                },
                dataUpdater = bindState().map { it.remoteDevices }.distinctUntilChanged(),
                differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1 == d2 }),
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, data ->
                        rxSingle(Dispatchers.IO) {
                            val receiver = bindState().map { it.receiver }.firstOrError().await().getOrNull()
                            if (receiver == null) {
                                if (hasInvokeCallback.compareAndSet(false, true)) {
                                    simpleCallback.onError("Receiver is null")
                                }
                                cancel()
                            } else {
                                runCatching {
                                    receiver.requestFileTransferSuspend(data.remoteAddress.address)
                                }.onSuccess {
                                    if (hasInvokeCallback.compareAndSet(false, true)) {
                                        simpleCallback.onSuccess(data)
                                    }
                                    cancel()
                                }.onFailure {
                                    AndroidLog.e(TAG, "Request transfer error: ${it.message}", it)
                                    withContext(Dispatchers.Main) {
                                        activity.showToastShort(R.string.error_toast)
                                    }
                                }
                            }
                            Unit
                        }
                    }
                }
            ).emptyView<RemoteDevice, RemoteServerItemLayoutBinding, RemoteServerEmptyItemLayoutBinding>(
                emptyLayout = R.layout.remote_server_empty_item_layout,
                initShowEmpty = true)
                .toAdapter()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (hasInvokeCallback.compareAndSet(false, true)) {
            simpleCallback.onError("Close by system.")
        }
        receiver.get()?.closeConnectionIfActive()
    }

    companion object {

        private const val TAG = "BroadcastReceiverDialog"

        data class BroadcastReceiverDialogState(
            val receiver: Optional<BroadcastReceiver> = Optional.empty(),
            val remoteDevices: List<RemoteDevice> = emptyList()
        )
    }
}

suspend fun Activity.showReceiverDialog(localAddress: InetAddress) = suspendCancellableCoroutine { cont ->
    BroadcastReceiverDialog(
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