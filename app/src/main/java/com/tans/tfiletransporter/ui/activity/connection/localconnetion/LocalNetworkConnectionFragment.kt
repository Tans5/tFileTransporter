package com.tans.tfiletransporter.ui.activity.connection.localconnetion

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Bundle
import com.jakewharton.rxbinding3.view.clicks
import com.tans.rxutils.ignoreSeveralClicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.LocalNetworkConnectionFragmentBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.toBytes
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.FileTransportActivity
import io.reactivex.rxkotlin.withLatestFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import org.kodein.di.instance
import java.net.InetAddress
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class LocalNetworkConnectionFragment : BaseFragment<LocalNetworkConnectionFragmentBinding, LocalNetworkConnectionFragment.Companion.LocalNetworkState>(
    layoutId = R.layout.local_network_connection_fragment,
    default = LocalNetworkState()
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

    override fun initViews(binding: LocalNetworkConnectionFragmentBinding) {

        render({ it.address }) {
            val address = it.getOrNull()
            if (address != null) {
                binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, address.toString().removePrefix("/"))
            } else {
                binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, "Not Available")
            }
        }.bindLife()

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
        private const val TAG = "LocalNetworkConnectionFragment"
        data class LocalNetworkState(
            val address: Optional<InetAddress> = Optional.empty()
        )
    }
}