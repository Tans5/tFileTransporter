package com.tans.tfiletransporter.ui.connection.localconnetion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.view.View
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.LocalAddressItemLayoutBinding
import com.tans.tfiletransporter.databinding.LocalNetworkConnectionFragmentBinding
import com.tans.tfiletransporter.file.LOCAL_DEVICE
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.netty.findLocalAddressV4
import com.tans.tfiletransporter.netty.toInetAddress
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanClient
import com.tans.tfiletransporter.transferproto.qrscanconn.model.QRCodeShare
import com.tans.tfiletransporter.transferproto.qrscanconn.requestFileTransferSuspend
import com.tans.tfiletransporter.transferproto.qrscanconn.startQRCodeScanClientSuspend
import com.tans.tfiletransporter.ui.filetransport.FileTransportActivity
import com.tans.tfiletransporter.ui.qrcodescan.ScanQrCodeActivity
import com.tans.tfiletransporter.utils.fromJson
import com.tans.tfiletransporter.utils.showToastShort
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.Optional
import kotlin.jvm.optionals.getOrNull
import androidx.core.content.getSystemService
import com.tans.tuiutils.actresult.startActivityResultSuspend
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map

class LocalNetworkConnectionFragment : BaseCoroutineStateFragment<LocalNetworkConnectionFragment.Companion.LocalNetworkState>(
    defaultState = LocalNetworkState()
) {

    private val connectivityManager: ConnectivityManager? by lazy {
        requireActivity().getSystemService()
    }

    private val networkRequest: NetworkRequest by lazy {
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
    }

    private val netWorkerCallback: ConnectivityManager.NetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                AndroidLog.d(TAG, "Network available: $network")
                updateAddress()
            }
            override fun onLost(network: Network) {
                AndroidLog.d(TAG, "Network lost: $network")
                updateAddress()
            }
        }
    }

    private val wifiApChangeBroadcastReceiver: BroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                AndroidLog.d(TAG, "Wifi AP changed.")
                updateAddress()
            }
        }
    }

    private val wifiP2pConnectionBroadcastReceiver: BroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                AndroidLog.d(TAG, "Wifi p2p changed.")
                updateAddress()
            }
        }
    }
    override val layoutId: Int = R.layout.local_network_connection_fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager?.registerNetworkCallback(networkRequest, netWorkerCallback)
        val wifiApIf = IntentFilter()
        // wifiApIf.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
        wifiApIf.addAction("android.net.conn.TETHER_STATE_CHANGED")
        requireContext().registerReceiver(wifiApChangeBroadcastReceiver, wifiApIf)
        val wifiP2pConnFilter = IntentFilter()
        wifiP2pConnFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        requireContext().registerReceiver(wifiP2pConnectionBroadcastReceiver, wifiP2pConnFilter)
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {  }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = LocalNetworkConnectionFragmentBinding.bind(contentView)

        val addressAdapterBuilder = SimpleAdapterBuilderImpl<Pair<InetAddress, Boolean>>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.local_address_item_layout),
            dataSource = FlowDataSourceImpl(
                dataFlow = stateFlow
                    .map {
                        val selected = it.selectedAddress.getOrNull()
                        val available = it.availableAddresses
                        available.map { address ->
                            address to (address == selected)
                        }
                    },
                areDataItemsTheSameParam = { d1, d2 -> d1.first == d2.first },
                getDataItemsChangePayloadParam = { d1, d2 -> if (d1.first == d2.first && d1.second != d2.second) Unit else null }
            ),
            dataBinder = DataBinderImpl<Pair<InetAddress, Boolean>> { data, view, _ ->
                val itemViewBinding = LocalAddressItemLayoutBinding.bind(view)
                itemViewBinding.addressTv.text = data.first.toString().removePrefix("/")
                itemViewBinding.root.clicks(this@bindContentViewCoroutine) {
                    updateState { s ->
                        s.copy(selectedAddress = Optional.of(data.first))
                    }
                }
            }.addPayloadDataBinder(Unit) { data, view, _ ->
                val itemViewBinding = LocalAddressItemLayoutBinding.bind(view)
                itemViewBinding.addressRb.isChecked = data.second
            }
        )
        viewBinding.localAddressesRv.adapter = addressAdapterBuilder.build()

        // Scan QrCode
        viewBinding.scanQrCodeLayout.clicks(this) {
            val selectedAddress = currentState().selectedAddress.getOrNull()
            if (selectedAddress != null) {
                // Scan QRCode.
                val (_, resultData) = startActivityResultSuspend(Intent(requireActivity(), ScanQrCodeActivity::class.java))
                if (resultData != null) {
                    val scanResultStrings = ScanQrCodeActivity.getResult(resultData)
                    val qrcodeShare = scanResultStrings.map { it.fromJson<QRCodeShare>() }.firstOrNull()
                    if (qrcodeShare != null) {
                        val scanClient = QRCodeScanClient(AndroidLog)
                        runCatching {
                            val serverAddress = qrcodeShare.address.toInetAddress()
                            // Create request transfer file to QRCodeServer connection.
                            scanClient.startQRCodeScanClientSuspend(serverAddress)
                            AndroidLog.d(TAG, "Client connect address: $serverAddress success.")
                            withContext(Dispatchers.IO) {
                                // Request transfer file.
                                scanClient.requestFileTransferSuspend(targetAddress = serverAddress, deviceName = LOCAL_DEVICE)
                            }
                            serverAddress
                        }.onSuccess { serverAddress ->
                            withContext(Dispatchers.Main) {
                                requireActivity().startActivity(
                                    FileTransportActivity.getIntent(
                                        context = requireContext(),
                                        localAddress = selectedAddress,
                                        remoteAddress = serverAddress,
                                        remoteDeviceInfo = qrcodeShare.deviceName,
                                        isServer = false
                                    ))
                            }
                        }.onFailure {
                            withContext(Dispatchers.Main) {
                                requireActivity().showToastShort(getString(R.string.error_toast, it.message))
                            }
                        }
                    }
                }
            }
        }

        // Show QrCode
        viewBinding.showQrCodeLayout.clicks(this) {
            val selectedAddress = currentState().selectedAddress.getOrNull()
            if (selectedAddress != null) {
                val remoteAddress = childFragmentManager.showQRCodeServerDialogSuspend(selectedAddress)
                if (remoteAddress != null) {
                    withContext(Dispatchers.Main.immediate) {
                        requireActivity().startActivity(
                            FileTransportActivity.getIntent(
                                context = requireContext(),
                                localAddress = selectedAddress,
                                remoteAddress = remoteAddress.remoteAddress.address,
                                remoteDeviceInfo = remoteAddress.deviceName,
                                isServer = true
                            ))
                    }
                }
            }
        }

        // Search Servers
        viewBinding.searchServerLayout.clicks(this) {
            val selectedAddress = currentState().selectedAddress.getOrNull()
            if (selectedAddress != null) {
                val remoteDevice =
                    childFragmentManager.showBroadcastReceiverDialogSuspend(selectedAddress)
                if (remoteDevice != null) {
                    withContext(Dispatchers.Main.immediate) {
                        startActivity(
                            FileTransportActivity.getIntent(
                                context = requireContext(),
                                localAddress = selectedAddress,
                                remoteAddress = remoteDevice.remoteAddress.address,
                                remoteDeviceInfo = remoteDevice.deviceName,
                                isServer = false
                            )
                        )
                    }
                }
            }
        }

        // As Server
        viewBinding.asServerLayout.clicks(this) {
            val selectedAddress = currentState().selectedAddress.getOrNull()
            if (selectedAddress != null) {
                val remoteDevice = withContext(Dispatchers.Main) {
                    childFragmentManager.showBroadcastSenderDialogSuspend(selectedAddress)
                }
                if (remoteDevice != null) {
                    withContext(Dispatchers.Main.immediate) {
                        startActivity(
                            FileTransportActivity.getIntent(
                                context = requireContext(),
                                localAddress = selectedAddress,
                                remoteAddress = remoteDevice.remoteAddress.address,
                                remoteDeviceInfo = remoteDevice.deviceName,
                                isServer = true
                            ))
                    }
                }
            }
        }
    }

    private fun updateAddress() {
        val availableAddresses = findLocalAddressV4()
        AndroidLog.d(TAG, "AvailableAddress: $availableAddresses")
        updateState { oldState ->
            if (availableAddresses.isEmpty()) {
                oldState.copy(selectedAddress = Optional.empty(), availableAddresses = emptyList())
            } else {
                val oldSelectedAddress = oldState.selectedAddress
                val newSelectedAddress = if (oldSelectedAddress.isPresent) {
                    if (availableAddresses.contains(oldSelectedAddress.get())) {
                        oldSelectedAddress
                    } else {
                        Optional.of(availableAddresses[0])
                    }
                } else {
                    Optional.of(availableAddresses[0])
                }
                oldState.copy(
                    selectedAddress = newSelectedAddress,
                    availableAddresses = availableAddresses
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager?.unregisterNetworkCallback(netWorkerCallback)
        requireContext().unregisterReceiver(wifiApChangeBroadcastReceiver)
        requireContext().unregisterReceiver(wifiP2pConnectionBroadcastReceiver)
    }

    companion object {
        private const val TAG = "LocalNetworkConnectionFragment"
        data class LocalNetworkState(
            val selectedAddress: Optional<InetAddress> = Optional.empty(),
            val availableAddresses: List<InetAddress> = emptyList()
        )
    }
}