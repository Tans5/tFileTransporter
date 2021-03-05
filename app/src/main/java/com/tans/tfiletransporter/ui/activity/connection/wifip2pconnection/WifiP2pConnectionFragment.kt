package com.tans.tfiletransporter.ui.activity.connection.wifip2pconnection

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Bundle
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.emptyView
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.RemoteServerEmptyItemLayoutBinding
import com.tans.tfiletransporter.databinding.RemoteServerItemLayoutBinding
import com.tans.tfiletransporter.databinding.WifiP2pConnectionFragmentBinding
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import org.kodein.di.instance
import java.net.InetAddress
import java.util.*

data class RemoteDevicesInfo(
        val address: InetAddress,
        val macAddress: String,
        val name: String
)

data class WifiP2pConnectionState(
        val localDevice: Optional<WifiP2pDevice> = Optional.empty(),
        val localAddress: Optional<InetAddress> = Optional.empty(),
        val peers: Optional<WifiP2pDeviceList> = Optional.empty(),
        val currentConnection: Optional<WifiP2pInfo> = Optional.empty(),
        val connectedRemoteDevice: Optional<RemoteDevicesInfo> = Optional.empty()
)

class WifiP2pConnectionFragment : BaseFragment<WifiP2pConnectionFragmentBinding, WifiP2pConnectionState>(
        layoutId = R.layout.wifi_p2p_connection_fragment,
        default = WifiP2pConnectionState()
) {

    private val wifiP2pManager: WifiP2pManager by instance()
    private val wifiChannel: WifiP2pManager.Channel by lazy { wifiP2pManager.initialize(requireActivity(), requireActivity().mainLooper, null) }

    private val wifiReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {

                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                        updateState { WifiP2pConnectionState() }.bindLife()
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val wifiDevicesList = intent.getParcelableExtra<WifiP2pDeviceList>(WifiP2pManager.EXTRA_P2P_DEVICE_LIST)
                    updateState { oldState ->
                        oldState.copy(peers = Optional.ofNullable(wifiDevicesList))
                    }.bindLife()
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val wifiP2pInfo = intent.getParcelableExtra<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                    updateState { oldState ->
                        oldState.copy(currentConnection = if (wifiP2pInfo?.groupOwnerAddress == null) Optional.empty() else Optional.of(wifiP2pInfo))
                    }.bindLife()
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val localDevice = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    updateState { oldState ->
                        oldState.copy(localDevice = Optional.ofNullable(localDevice))
                    }.bindLife()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        requireActivity().registerReceiver(wifiReceiver, intentFilter)
    }

    override fun initViews(binding: WifiP2pConnectionFragmentBinding) {
        launch {

            render({ it.localDevice }) {
                if (it.isPresent) {
                    binding.localDeviceTv.text = getString(R.string.wifi_p2p_connection_local_device, it.get().deviceName, it.get().deviceAddress)
                } else {
                    binding.localDeviceTv.text = getString(R.string.wifi_p2p_connection_local_device, "Not Available", "Not Available")
                }
            }.bindLife()

            render({ it.localAddress }) {
                if (it.isPresent) {
                    binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, it.get().hostAddress)
                } else {
                    binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, "Not Available")
                }
            }.bindLife()

            render({ it.connectedRemoteDevice }) {
                if (it.isPresent) {
                    binding.remoteConnectedDeviceTv.text = getString(R.string.wifi_p2p_connection_remote_device,
                            it.get().name, it.get().address.hostAddress, it.get().macAddress)
                } else {
                    binding.remoteConnectedDeviceTv.text = getString(R.string.wifi_p2p_connection_remote_device,
                            "Not Available", "Not Available", "Not Available")
                }
            }.bindLife()

            val grant = RxPermissions(requireActivity())
                    .request(Manifest.permission.ACCESS_FINE_LOCATION)
                    .firstOrError()
                    .await()
            if (grant) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    launch {
                        val localDevice = wifiP2pManager.requestDeviceInfoSuspend(wifiChannel)
                        updateState { oldState ->
                            oldState.copy(localDevice = localDevice)
                        }.await()
                    }
                }

                launch(Dispatchers.IO) {
                    while (true) {
                        val state = wifiP2pManager.discoverPeersSuspend(wifiChannel)
                        if ( state == WIFI_P2P_SUCCESS_CODE) {
                            val peers = wifiP2pManager.requestPeersSuspend(channel = wifiChannel)
                            updateState { oldState -> oldState.copy(peers = peers) }.await()
                        }
                        delay(1000 * 8)
                    }
                }

                binding.closeCurrentConnectionLayout.clicks()
                        .flatMapSingle {
                            rxSingle {
                                wifiP2pManager.cancelConnectionSuspend(wifiChannel)
                                wifiP2pManager.removeGroupSuspend(wifiChannel)
                            }.loadingDialog(requireActivity())
                        }
                        .bindLife()

                binding.remoteDevicesRv.adapter = SimpleAdapterSpec<WifiP2pDevice, RemoteServerItemLayoutBinding>(
                        layoutId = R.layout.remote_server_item_layout,
                        bindData = { _, device, lBinding ->
                            lBinding.device = device.deviceName
                            lBinding.ipAddress = "Mac address: ${device.deviceAddress}"
                        },
                        dataUpdater = bindState().map { if (it.peers.isPresent) it.peers.get().deviceList.toList() else emptyList() },
                        itemClicks = listOf { binding, _ ->
                            binding.root to { _, data ->
                                rxSingle {
                                    // TODO: handle click.
                                }
                            }
                        }
                ).emptyView<WifiP2pDevice, RemoteServerItemLayoutBinding, RemoteServerEmptyItemLayoutBinding>(
                        emptyLayout = R.layout.remote_server_empty_item_layout,
                        initShowEmpty = true)
                        .toAdapter()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wifiP2pManager.cancelConnect(wifiChannel, null)
        wifiP2pManager.removeGroup(wifiChannel, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        requireActivity().unregisterReceiver(wifiReceiver)
    }


}