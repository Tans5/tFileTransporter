package com.tans.tfiletransporter.ui.connection.localconnetion

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastReceiverDialogLayoutBinding
import com.tans.tfiletransporter.databinding.RemoteServerItemLayoutBinding
import com.tans.tfiletransporter.file.LOCAL_DEVICE
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.netty.getBroadcastAddress
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastReceiver
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastReceiverObserver
import com.tans.tfiletransporter.transferproto.broadcastconn.BroadcastReceiverState
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import com.tans.tfiletransporter.transferproto.broadcastconn.requestFileTransferSuspend
import com.tans.tfiletransporter.transferproto.broadcastconn.startReceiverSuspend
import com.tans.tfiletransporter.transferproto.broadcastconn.waitCloseSuspend
import com.tans.tfiletransporter.utils.showToastShort
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.builders.plus
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.dialog.BaseCoroutineStateCancelableResultDialogFragment
import com.tans.tuiutils.dialog.DialogCancelableResultCallback
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.net.InetAddress
import kotlin.coroutines.resume

class BroadcastReceiverDialog : BaseCoroutineStateCancelableResultDialogFragment<BroadcastReceiverDialog.Companion.BroadcastReceiverDialogState, RemoteDevice> {

    private val receiver by lazy {
        BroadcastReceiver(deviceName = LOCAL_DEVICE, log = AndroidLog)
    }

    private val localAddress: InetAddress?
    constructor() : super(BroadcastReceiverDialogState(), null) {
        localAddress = null
    }

    constructor(localAddress: InetAddress, callback: DialogCancelableResultCallback<RemoteDevice>) : super(
        BroadcastReceiverDialogState(), callback) {
        this.localAddress = localAddress
    }

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.broadcast_receiver_dialog_layout, parent, false)
    }

    override fun firstLaunchInitData() {}

    override fun bindContentView(view: View) {
        val localAddress = this.localAddress ?: return
        val viewBinding = BroadcastReceiverDialogLayoutBinding.bind(view)

        viewBinding.cancelButton.clicks(this) {
            onCancel()
        }

        launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    receiver.startReceiverSuspend(localAddress, localAddress.getBroadcastAddress().first)
                }
            }.onSuccess {
                receiver.addObserver(object : BroadcastReceiverObserver {
                    override fun onNewBroadcast(
                        remoteDevice: RemoteDevice
                    ) {
                        // onResult(remoteDevice)
                    }
                    override fun onNewState(state: BroadcastReceiverState) {
                        AndroidLog.d(TAG, "BroadcastReceiver State: $state")
                    }
                    override fun onActiveRemoteDevicesUpdate(remoteDevices: List<RemoteDevice>) {
                        AndroidLog.d(TAG, "RemoteDevices update: $remoteDevices")
                        updateState { it.copy(remoteDevices = remoteDevices) }
                    }
                })
                receiver.waitCloseSuspend()
                onCancel()
                withContext(Dispatchers.Main.immediate) {
                    requireActivity().showToastShort("Connection closed")
                }
            }.onFailure {
                onCancel()
                withContext(Dispatchers.Main.immediate) {
                    requireActivity().showToastShort(R.string.error_toast)
                }
            }
        }
        val devicesAdapterBuilder = SimpleAdapterBuilderImpl<RemoteDevice>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.remote_server_item_layout),
            dataSource = FlowDataSourceImpl(stateFlow.map { it.remoteDevices }),
            dataBinder = DataBinderImpl { data, itemView, _ ->
                val itemViewBinding = RemoteServerItemLayoutBinding.bind(itemView)
                itemViewBinding.remoteDeviceTv.text = data.deviceName
                itemViewBinding.ipAddressTv.text = data.remoteAddress.address.toString().removePrefix("/")
                itemViewBinding.root.clicks(
                    coroutineScope = this,
                    clickWorkOn = Dispatchers.IO) {
                    runCatching {
                        receiver.requestFileTransferSuspend(data.remoteAddress.address)
                    }.onSuccess {
                        onResult(data)
                    }.onFailure {
                        AndroidLog.e(TAG, "Request transfer error: ${it.message}", it)
                        withContext(Dispatchers.Main) {
                            requireActivity().showToastShort(requireActivity().getString(R.string.error_toast, it.message))
                        }
                    }
                }
            }
        )

        val emptyAdapterBuilder = SimpleAdapterBuilderImpl<Unit>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.remote_server_empty_item_layout),
            dataSource = FlowDataSourceImpl(stateFlow.map { if (it.remoteDevices.isEmpty()) listOf(Unit) else emptyList() }),
            dataBinder = DataBinderImpl { _, _, _ -> }
        )
        viewBinding.serversRv.adapter = (devicesAdapterBuilder + emptyAdapterBuilder).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Dispatchers.IO.asExecutor().execute {
            Thread.sleep(1000)
            receiver.closeConnectionIfActive()
        }
    }

    companion object {

        private const val TAG = "BroadcastReceiverDialog"

        data class BroadcastReceiverDialogState(
            val remoteDevices: List<RemoteDevice> = emptyList()
        )
    }
}

suspend fun FragmentManager.showBroadcastReceiverDialogSuspend(localAddress: InetAddress): RemoteDevice? {
    return suspendCancellableCoroutine { cont ->
        val d = BroadcastReceiverDialog(localAddress, object : DialogCancelableResultCallback<RemoteDevice> {
            override fun onCancel() {
                if (cont.isActive) {
                    cont.resume(null)
                }
            }

            override fun onResult(t: RemoteDevice) {
                if (cont.isActive) {
                    cont.resume(t)
                }
            }
        })
        d.show(this, "BroadcastReceiverDialog#${System.currentTimeMillis()}")
        val wd = WeakReference(d)
        cont.invokeOnCancellation {
            wd.get()?.dismissSafe()
        }
    }
}