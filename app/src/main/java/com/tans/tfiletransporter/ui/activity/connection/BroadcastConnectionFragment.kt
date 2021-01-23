package com.tans.tfiletransporter.ui.activity.connection

import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastConnectionFragmentBinding
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.FileTransportActivity
import com.tans.tfiletransporter.utils.findLocalAddressV4
import com.tans.tfiletransporter.utils.toBytes
import org.kodein.di.instance
import java.net.InetAddress

class BroadcastConnectionFragment : BaseFragment<BroadcastConnectionFragmentBinding, Unit>(
    layoutId = R.layout.broadcast_connection_fragment,
    default = Unit
) {
    private val wifiManager: WifiManager by instance()

    override fun onInit() {
        val localIp = with(wifiManager) {
            if (WifiInfo.getDetailedStateOf(connectionInfo.supplicantState) == NetworkInfo.DetailedState.CONNECTED) {
                InetAddress.getByAddress(dhcpInfo.ipAddress.toBytes(isRevert = true))
            } else {
                null
            }
        } ?: findLocalAddressV4()[0]

        binding.ipAddressTv.text = getString(R.string.broadcast_connection_local_ip_address, localIp.hostAddress)

        binding.searchServerLayout.clicks()
                .switchMapSingle {
                    requireActivity().showBroadcastReceiverDialog(localIp)
                            .doOnSuccess {
                                if (it.isPresent) {
                                    startActivity(FileTransportActivity.getIntent(requireContext(), it.get(), false))
                                }
                            }
                }
                .bindLife()

        binding.asServerLayout.clicks()
            .switchMapSingle {
                requireActivity().showBroadcastSenderDialog(localIp)
                        .doOnSuccess {
                            if (it.isPresent) {
                                startActivity(FileTransportActivity.getIntent(requireContext(), it.get(), true))
                            }
                        }
            }
            .bindLife()
    }

}