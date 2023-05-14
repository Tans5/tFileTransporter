package com.tans.tfiletransporter.ui.activity.connection.broadcastconnetion

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Bundle
import com.afollestad.inlineactivityresult.coroutines.startActivityAwaitResult
import com.jakewharton.rxbinding4.view.clicks
import com.tans.rxutils.ignoreSeveralClicks
import com.tans.rxutils.switchThread
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastConnectionFragmentBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.netty.toInetAddress
import com.tans.tfiletransporter.toBytes
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
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.withLatestFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.coroutines.withContext
import org.kodein.di.instance
import java.net.InetAddress
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class BroadcastConnectionFragment : BaseFragment<BroadcastConnectionFragmentBinding, BroadcastConnectionFragment.Companion.BroadcastState>(
    layoutId = R.layout.broadcast_connection_fragment,
    default = BroadcastState()
) {

    private val wifiManager: WifiManager by instance()
    private val connectivityManager: ConnectivityManager by instance()

    private val networkRequest: NetworkRequest by lazy {
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
    }

    private val netWorkerCallback: ConnectivityManager.NetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val address = InetAddress.getByAddress(wifiManager.dhcpInfo.ipAddress.toBytes(isRevert = true))
                updateState { it.copy(address = Optional.of(address)) }.bindLife()
            }

            override fun onLost(network: Network) {
                // to deal as hotspot host situation, ugly code.
                launch {
                    updateState {
                        it.copy(address = Optional.empty())
                    }.await()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager.registerNetworkCallback(networkRequest, netWorkerCallback)
    }

    override fun initViews(binding: BroadcastConnectionFragmentBinding) {

        render({ it.address }) {
            val address = it.getOrNull()
            if (address != null) {
                binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, address.toString().removePrefix("/"))
            } else {
                binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, "Not Available")
            }
        }.bindLife()

        binding.scanQrCodeLayout.clicks()
            .ignoreSeveralClicks()
            .withLatestFrom(bindState().map { it.address })
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
                                scanClient.requestFileTransferSuspend(targetAddress = serverAddress, deviceName = qrcodeShare.deviceName)
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
            .withLatestFrom(bindState().map { it.address })
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
            .withLatestFrom(bindState().map { it.address })
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
            .withLatestFrom(bindState().map { it.address })
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

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(netWorkerCallback)
    }

    companion object {
        private const val TAG = "BroadcastConnectionFragment"
        data class BroadcastState(
            val address: Optional<InetAddress> = Optional.empty()
        )
    }
}