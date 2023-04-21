package com.tans.tfiletransporter.ui.activity.connection.broadcastconnetion

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Bundle
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastConnectionFragmentBinding
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.activity.FileTransportActivity
import com.tans.tfiletransporter.utils.showToastShort
import com.tans.tfiletransporter.utils.toBytes
import io.reactivex.Single
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

        binding.searchServerLayout.clicks()
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
                            // TODO:
                            requireActivity().showToastShort(it.toString())
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            requireActivity().showToastShort(it.message ?: "")
                        }
                    }
                }
            }
            .bindLife()

        binding.asServerLayout.clicks()
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
                            // TODO:
                            requireActivity().showToastShort(it.toString())
                        }
                    }.onFailure {
                        withContext(Dispatchers.Main) {
                            requireActivity().showToastShort(it.message ?: "")
                        }
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
        data class BroadcastState(
            val address: Optional<InetAddress> = Optional.empty()
        )
    }
}