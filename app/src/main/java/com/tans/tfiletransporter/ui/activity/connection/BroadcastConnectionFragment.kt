package com.tans.tfiletransporter.ui.activity.connection

import android.net.wifi.WifiManager
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastConnectionFragmentBinding
import com.tans.tfiletransporter.ui.activity.BaseFragment
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
        val localIp = wifiManager.dhcpInfo?.ipAddress?.toBytes(isRevert = true)?.let {
            InetAddress.getByAddress(it)
        } ?: findLocalAddressV4()[0]

        binding.ipAddressTv.text = getString(R.string.broadcast_connection_local_ip_address, localIp.hostAddress)

        binding.searchServerLayout.clicks()
            .bindLife()

        binding.asServerLayout.clicks()
            .bindLife()
    }

}