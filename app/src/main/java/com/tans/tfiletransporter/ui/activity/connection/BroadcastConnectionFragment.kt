package com.tans.tfiletransporter.ui.activity.connection

import android.net.*
import android.net.wifi.WifiManager
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastConnectionFragmentBinding
import com.tans.tfiletransporter.net.LOCAL_DEVICE
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.FileTransportActivity
import com.tans.tfiletransporter.utils.findLocalAddressV4
import io.reactivex.rxkotlin.withLatestFrom
import com.tans.tfiletransporter.utils.toBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import org.kodein.di.instance
import java.net.InetAddress
import java.util.*

class BroadcastConnectionFragment : BaseFragment<BroadcastConnectionFragmentBinding, Optional<InetAddress>>(
    layoutId = R.layout.broadcast_connection_fragment,
    default = Optional.empty()
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
                updateState { Optional.of(address) }.bindLife()
            }

            override fun onLost(network: Network) {
                // to deal as hotspot host situation, ugly code.
                launch {
                    updateState {
                        Optional.empty()

                    }.await()
                    delay(5000)
                    updateState {
                        val canUseAddress = findLocalAddressV4().getOrNull(0)
                        if (canUseAddress != null) {
                            Optional.of(canUseAddress)
                        } else {
                            Optional.empty()
                        }
                    }.await()
                }
            }
        }
    }

    override fun onInit() {

        updateState {
            // to deal as hotspot host situation, ugly code.
            val canUseAddress = findLocalAddressV4().getOrNull(0)
            if (canUseAddress != null) {
                Optional.of(canUseAddress)
            } else {
                Optional.empty()
            }
        }.bindLife()

        connectivityManager.registerNetworkCallback(networkRequest, netWorkerCallback)


        binding.deviceTv.text = getString(R.string.broadcast_connection_local_device, LOCAL_DEVICE)

        render {
            binding.ipAddressTv.text = getString(R.string.broadcast_connection_local_ip_address, if (it.isPresent) it.get().hostAddress else "Not available")
        }.bindLife()

        binding.searchServerLayout.clicks()
                .withLatestFrom(bindState())
                .filter { it.second.isPresent }
                .map { it.second.get() }
                .switchMapSingle { localAddress ->
                    requireActivity().showBroadcastReceiverDialog(localAddress)
                            .doOnSuccess {
                                if (it.isPresent) {
                                    startActivity(FileTransportActivity.getIntent(
                                            context = requireContext(),
                                            localAddress = localAddress,
                                            remoteDevice = it.get(),
                                            asServer = false))
                                }
                            }
                }
                .bindLife()

        binding.asServerLayout.clicks()
                .withLatestFrom(bindState())
                .filter { it.second.isPresent }
                .map { it.second.get() }
                .switchMapSingle { localAddress ->
                    requireActivity().showBroadcastSenderDialog(localAddress)
                            .doOnSuccess {
                                if (it.isPresent) {
                                    startActivity(FileTransportActivity.getIntent(
                                            context = requireContext(),
                                            localAddress = localAddress,
                                            remoteDevice = it.get(),
                                            asServer = true))
                                }
                            }
                }
                .bindLife()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        connectivityManager.unregisterNetworkCallback(netWorkerCallback)
    }

}