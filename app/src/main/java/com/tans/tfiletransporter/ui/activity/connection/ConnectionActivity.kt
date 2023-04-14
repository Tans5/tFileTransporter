package com.tans.tfiletransporter.ui.activity.connection

import android.Manifest
import android.content.Intent
import android.net.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ConnectionActivityBinding
import com.tans.tfiletransporter.net.LOCAL_DEVICE
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tans.tfiletransporter.utils.toBytes
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import org.kodein.di.instance
import java.net.InetAddress
import java.util.*

data class ConnectionActivityState(
    val address: Optional<InetAddress> = Optional.empty()
)

class ConnectionActivity : BaseActivity<ConnectionActivityBinding, ConnectionActivityState>(
    layoutId = R.layout.connection_activity,
    defaultState = ConnectionActivityState()
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

    override fun firstLaunchInitData() {
        launch {
            RxPermissions(this@ConnectionActivity).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    it.request(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    it.request(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }.firstOrError().await()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                i.data = Uri.fromParts("package", packageName, null)
                startActivity(i)
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager.registerNetworkCallback(networkRequest, netWorkerCallback)
    }

    override fun initViews(binding: ConnectionActivityBinding) {
        binding.deviceTv.text = getString(R.string.broadcast_connection_local_device, LOCAL_DEVICE)
        render ({ it.address }) {
            binding.ipAddressTv.text = getString(R.string.broadcast_connection_local_ip_address, if (it.isPresent) it.get().hostAddress else "Not available")
        }.bindLife()
    }

    override fun onDestroy() {
        connectivityManager.unregisterNetworkCallback(netWorkerCallback)
        super.onDestroy()
    }

}