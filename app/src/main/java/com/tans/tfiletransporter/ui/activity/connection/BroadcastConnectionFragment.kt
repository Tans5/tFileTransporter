package com.tans.tfiletransporter.ui.activity.connection

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastConnectionFragmentBinding
import com.tans.tfiletransporter.net.LOCAL_DEVICE
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.activity.FileTransportActivity
import com.tans.tfiletransporter.utils.findLocalAddressV4
import com.tans.tfiletransporter.utils.toBytes
import io.reactivex.Single
import io.reactivex.rxkotlin.withLatestFrom
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import org.kodein.di.instance
import java.net.InetAddress
import java.util.*

data class BroadcastState(
        val address: Optional<InetAddress> = Optional.empty(),
        val useSystemBroadcast: Boolean = false
)

class BroadcastConnectionFragment : BaseFragment<BroadcastConnectionFragmentBinding, BroadcastState>(
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
                    delay(5000)
                    updateState {
                        val canUseAddress = findLocalAddressV4().getOrNull(0)
                        it.copy(address = if (canUseAddress != null) {
                            Optional.of(canUseAddress)
                        } else {
                            Optional.empty()
                        })
                    }.await()
                }
            }
        }
    }

    override fun initViews(binding: BroadcastConnectionFragmentBinding) {

        updateState {
            // to deal as hotspot host situation, ugly code.
            val canUseAddress = findLocalAddressV4().getOrNull(0)
            it.copy(address = if (canUseAddress != null) {
                Optional.of(canUseAddress)
            } else {
                Optional.empty()
            })
        }.bindLife()


        connectivityManager.registerNetworkCallback(networkRequest, netWorkerCallback)


        binding.deviceTv.text = getString(R.string.broadcast_connection_local_device, LOCAL_DEVICE)

        render ({ it.address }) {
            binding.ipAddressTv.text = getString(R.string.broadcast_connection_local_ip_address, if (it.isPresent) it.get().hostAddress else "Not available")
        }.bindLife()

        binding.searchServerLayout.clicks()
                .withLatestFrom(bindState())
                .filter { it.second.address.isPresent }
                .map { it.second.address.get() to it.second.useSystemBroadcast }
                .switchMapSingle { (localAddress, useSystemBroadcast) ->
                    requireActivity().showBroadcastReceiverDialog(localAddress, !useSystemBroadcast)
                            .doOnSuccess {
                                if (it.isPresent) {
                                    startActivity(FileTransportActivity.getIntent(
                                            context = requireContext(),
                                            localAddress = localAddress,
                                            remoteDevice = it.get(),
                                            asServer = false))
                                }
                            }
                            .map {  }
                            .onErrorResumeNext {
                                Single.just(Unit)
                            }
                }
                .bindLife()

        binding.asServerLayout.clicks()
                .withLatestFrom(bindState())
                .filter { it.second.address.isPresent }
                .map { it.second.address.get() to it.second.useSystemBroadcast }
                .switchMapSingle { (localAddress, useSystemBroadcast) ->
                    requireActivity().showBroadcastSenderDialog(localAddress, !useSystemBroadcast)
                            .doOnSuccess {
                                if (it.isPresent) {
                                    startActivity(FileTransportActivity.getIntent(
                                            context = requireContext(),
                                            localAddress = localAddress,
                                            remoteDevice = it.get(),
                                            asServer = true))
                                }
                            }
                            .map {  }
                            .onErrorResumeNext {
                                Single.just(Unit)
                            }
                }
                .bindLife()

        binding.systemBroadcastSwitch.checkedChanges()
                .flatMapSingle { check ->
                    updateState { it.copy(useSystemBroadcast = check) }
                }
                .bindLife()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        connectivityManager.unregisterNetworkCallback(netWorkerCallback)
    }

}