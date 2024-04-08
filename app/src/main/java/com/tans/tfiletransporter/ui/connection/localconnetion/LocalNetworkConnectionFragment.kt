package com.tans.tfiletransporter.ui.connection.localconnetion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import com.afollestad.inlineactivityresult.coroutines.startActivityAwaitResult
import com.jakewharton.rxbinding4.view.clicks
import com.tans.rxutils.ignoreSeveralClicks
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
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
import com.tans.tfiletransporter.ui.BaseFragment
import com.tans.tfiletransporter.ui.commomdialog.loadingDialogSuspend
import com.tans.tfiletransporter.ui.filetransport.FileTransportActivity
import com.tans.tfiletransporter.ui.qrcodescan.ScanQrCodeActivity
import com.tans.tfiletransporter.utils.fromJson
import com.tans.tfiletransporter.utils.showToastShort
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.withLatestFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.coroutines.withContext
import org.kodein.di.instance
import java.net.InetAddress
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class LocalNetworkConnectionFragment : BaseFragment<LocalNetworkConnectionFragmentBinding, LocalNetworkConnectionFragment.Companion.LocalNetworkState>(
    layoutId = R.layout.local_network_connection_fragment,
    default = LocalNetworkState()
) {

    private val connectivityManager: ConnectivityManager by instance()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager.registerNetworkCallback(networkRequest, netWorkerCallback)
        val wifiApIf = IntentFilter()
        // wifiApIf.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
        wifiApIf.addAction("android.net.conn.TETHER_STATE_CHANGED")
        requireContext().registerReceiver(wifiApChangeBroadcastReceiver, wifiApIf)

        val wifiP2pConnFilter = IntentFilter()
        wifiP2pConnFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        requireContext().registerReceiver(wifiP2pConnectionBroadcastReceiver, wifiP2pConnFilter)
    }

    override fun initViews(binding: LocalNetworkConnectionFragmentBinding) {

        render({ it.availableAddresses }) {
            AndroidLog.d(TAG, "Available addresses: $it")
        }.bindLife()

        val selectAddressChangePayload = Any()
        binding.localAddressesRv.adapter = SimpleAdapterSpec<Pair<InetAddress, Boolean>, LocalAddressItemLayoutBinding>(
            layoutId = R.layout.local_address_item_layout,
            bindData = { _, data, lBinding ->
                lBinding.addressRb.isChecked = data.second
                lBinding.addressTv.text = data.first.toString().removePrefix("/")
            },
            itemClicks = listOf { lBinding, _ ->
                lBinding.root to { _, data ->
                    rxSingle {
                        updateState { oldState ->
                            oldState.copy(selectedAddress = Optional.of(data.first))
                        }.await()
                        Unit
                    }
                }
            },
            differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1.first == d2.first },
                contentTheSame = { d1, d2 -> d1 == d2 },
                changePayLoad = { d1, d2 ->
                    if (d1.first == d2.first && d1.second != d2.second) {
                        selectAddressChangePayload
                    } else {
                        null
                    }
                }),
            bindDataPayload = { _, data, lBinding, payloads ->
                if (payloads.contains(selectAddressChangePayload)) {
                    lBinding.addressRb.isChecked = data.second
                    true
                } else {
                    false
                }
            },
            dataUpdater = bindState()
                .map { it.selectedAddress to it.availableAddresses }
                .distinctUntilChanged()
                .map { it.second.map { address -> address to (address == it.first.getOrNull()) } }
        ).toAdapter()

        binding.scanQrCodeLayout.clicks()
            .ignoreSeveralClicks()
            .withLatestFrom(bindState().map { it.selectedAddress })
            .map { it.second }
            .filter { it.isPresent }
            .map { it.get() }
            .flatMapSingle {
                rxSingle(Dispatchers.Main) {
                    it to startActivityAwaitResult<ScanQrCodeActivity>()
                }
            }
            .flatMapSingle { (localAddress, scanResult) ->
                if (scanResult.success) {
                    rxSingle(Dispatchers.Main) {
                        this@LocalNetworkConnectionFragment.childFragmentManager.loadingDialogSuspend {
                            val scanResultStrings = ScanQrCodeActivity.getResult(scanResult.data)
                            val qrcodeShare = scanResultStrings.map { it.fromJson<QRCodeShare>() }.firstOrNull()
                            if (qrcodeShare != null) {
                                val scanClient = QRCodeScanClient(AndroidLog)
                                runCatching {
                                    val serverAddress = qrcodeShare.address.toInetAddress()
                                    scanClient.startQRCodeScanClientSuspend(serverAddress)
                                    AndroidLog.d(TAG, "Client connect address: $serverAddress success.")
                                    scanClient.requestFileTransferSuspend(targetAddress = serverAddress, deviceName = LOCAL_DEVICE)
                                    serverAddress
                                }.onSuccess { serverAddress ->
                                    withContext(Dispatchers.Main) {
                                        requireActivity().startActivity(
                                            FileTransportActivity.getIntent(
                                                context = requireContext(),
                                                localAddress = localAddress,
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
                            } else {
                                val msg = "Don't find any qrcode share: $scanResultStrings"
                                withContext(Dispatchers.Main) {
                                    requireActivity().showToastShort(getString(R.string.error_toast, msg))
                                }
                                AndroidLog.e(TAG, msg)
                            }
                        }
                    }
                } else {
                    Single.just(Unit)
                }
            }
            .bindLife()

        binding.showQrCodeLayout.clicks()
            .ignoreSeveralClicks()
            .withLatestFrom(bindState().map { it.selectedAddress })
            .map { it.second }
            .filter { it.isPresent }
            .map { it.get() }
            .flatMapSingle { localAddress ->
                rxSingle(Dispatchers.Main) {
                    val remoteAddress = childFragmentManager.showQRCodeServerDialogSuspend(localAddress)
                    if (remoteAddress != null) {
                        withContext(Dispatchers.Main.immediate) {
                            requireActivity().startActivity(
                                FileTransportActivity.getIntent(
                                    context = requireContext(),
                                    localAddress = localAddress,
                                    remoteAddress = remoteAddress.remoteAddress.address,
                                    remoteDeviceInfo = remoteAddress.deviceName,
                                    isServer = true
                                ))
                        }
                    }
                }
            }
            .bindLife()

        binding.searchServerLayout.clicks()
            .ignoreSeveralClicks()
            .withLatestFrom(bindState().map { it.selectedAddress })
            .map { it.second }
            .filter { it.isPresent }
            .map { it.get() }
            .switchMapSingle { localAddress ->
                rxSingle {
                    runCatching {
                        withContext(Dispatchers.Main) {
                            requireActivity().showReceiverDialog(localAddress)
                        }
                    }.onSuccess {
                        withContext(Dispatchers.Main) {
                            startActivity(
                                FileTransportActivity.getIntent(
                                context = requireContext(),
                                localAddress = localAddress,
                                remoteAddress = it.remoteAddress.address,
                                remoteDeviceInfo = it.deviceName,
                                isServer = false
                            ))
                        }
                    }.onFailure {
                        AndroidLog.e(TAG, "Search server error: ${it.message}")
                    }
                }
            }
            .bindLife()

        binding.asServerLayout.clicks()
            .ignoreSeveralClicks()
            .withLatestFrom(bindState().map { it.selectedAddress })
            .map { it.second }
            .filter { it.isPresent }
            .map { it.get() }
            .switchMapSingle { localAddress ->
                rxSingle {
                    runCatching {
                        withContext(Dispatchers.Main) {
                            requireActivity().showSenderDialog(localAddress)
                        }
                    }.onSuccess {
                        withContext(Dispatchers.Main) {
                            startActivity(
                                FileTransportActivity.getIntent(
                                context = requireContext(),
                                localAddress = localAddress,
                                remoteAddress = it.remoteAddress.address,
                                remoteDeviceInfo = it.deviceName,
                                isServer = true
                            ))
                        }
                    }.onFailure {
                        AndroidLog.e(TAG, "Wait client error: ${it.message}")
                    }
                    Unit
                }
            }
            .bindLife()
    }

    private fun updateAddress() {
        updateState { oldState ->
            val availableAddresses = findLocalAddressV4()
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
        }.bindLife()
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(netWorkerCallback)
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