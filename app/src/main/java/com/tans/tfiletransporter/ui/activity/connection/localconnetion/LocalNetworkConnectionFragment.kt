package com.tans.tfiletransporter.ui.activity.connection.localconnetion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.*
import android.os.Bundle
import com.afollestad.inlineactivityresult.coroutines.startActivityAwaitResult
import com.jakewharton.rxbinding4.view.clicks
import com.tans.rxutils.ignoreSeveralClicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.LocalNetworkConnectionFragmentBinding
import com.tans.tfiletransporter.file.LOCAL_DEVICE
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.netty.findLocalAddressV4
import com.tans.tfiletransporter.netty.toInetAddress
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanClient
import com.tans.tfiletransporter.transferproto.qrscanconn.model.QRCodeShare
import com.tans.tfiletransporter.transferproto.qrscanconn.requestFileTransferSuspend
import com.tans.tfiletransporter.transferproto.qrscanconn.startQRCodeScanClientSuspend
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import com.tans.tfiletransporter.ui.activity.filetransport.FileTransportActivity
import com.tans.tfiletransporter.ui.activity.qrcodescan.ScanQrCodeActivity
import com.tans.tfiletransporter.utils.fromJson
import com.tans.tfiletransporter.utils.showToastShort
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.withLatestFrom
import kotlinx.coroutines.Dispatchers
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager.registerNetworkCallback(networkRequest, netWorkerCallback)
        val wifiApIf = IntentFilter()
        // wifiApIf.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
        wifiApIf.addAction("android.net.conn.TETHER_STATE_CHANGED")
        requireContext().registerReceiver(wifiApChangeBroadcastReceiver, wifiApIf)
    }

    override fun initViews(binding: LocalNetworkConnectionFragmentBinding) {

        render({ it.selectedAddress }) {
            val address = it.getOrNull()
            if (address != null) {
                binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, address.toString().removePrefix("/"))
            } else {
                binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, "Not Available")
            }
        }.bindLife()

        render({ it.availableAddresses }) {
            AndroidLog.d(TAG, "Available addresses: $it")
        }.bindLife()

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
                                    requireActivity().startActivity(FileTransportActivity.getIntent(
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
                    }.loadingDialog(requireActivity())
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
                    runCatching {
                        requireActivity().showQRCodeServerDialogSuspend(localAddress)
                    }.onSuccess { remoteAddress ->
                        requireActivity().startActivity(FileTransportActivity.getIntent(
                            context = requireContext(),
                            localAddress = localAddress,
                            remoteAddress = remoteAddress.remoteAddress.address,
                            remoteDeviceInfo = remoteAddress.deviceName,
                            isServer = true
                        ))
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
    }

    companion object {
        private const val TAG = "LocalNetworkConnectionFragment"
        data class LocalNetworkState(
            val selectedAddress: Optional<InetAddress> = Optional.empty(),
            val availableAddresses: List<InetAddress> = emptyList()
        )
    }
}