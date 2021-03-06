package com.tans.tfiletransporter.ui.activity.connection.wifip2pconnection

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Bundle
import android.view.View
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.emptyView
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.RemoteServerEmptyItemLayoutBinding
import com.tans.tfiletransporter.databinding.RemoteServerItemLayoutBinding
import com.tans.tfiletransporter.databinding.WifiP2pConnectionFragmentBinding
import com.tans.tfiletransporter.net.LOCAL_DEVICE
import com.tans.tfiletransporter.net.connection.RemoteDevice
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import org.kodein.di.instance
import java.net.InetAddress
import java.util.*


data class WifiP2pConnectionState(
        val localAddress: Optional<InetAddress> = Optional.empty(),
        val peers: Optional<WifiP2pDeviceList> = Optional.empty(),
        val currentConnection: Optional<WifiP2pInfo> = Optional.empty(),
        val connectedRemoteDevice: Optional<RemoteDevice> = Optional.empty()
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
                    // Android 10 can't get mac address.
//                    val localDevice = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
//                    updateState { oldState ->
//                        oldState.copy(localDevice = Optional.ofNullable(localDevice))
//                    }.bindLife()
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
                            it.get().second, it.get().first.hostAddress)
                    binding.connectedActionsLayout.visibility = View.VISIBLE
                    binding.remoteDevicesRv.visibility = View.GONE
                } else {
                    binding.remoteConnectedDeviceTv.text = getString(R.string.wifi_p2p_connection_remote_device,
                            "Not Available", "Not Available")
                    binding.connectedActionsLayout.visibility = View.GONE
                    binding.remoteDevicesRv.visibility = View.VISIBLE
                }
            }.bindLife()

            val grant = RxPermissions(requireActivity())
                    .request(Manifest.permission.ACCESS_FINE_LOCATION)
                    .firstOrError()
                    .await()
            if (grant) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                    launch {
//                        val localDevice = wifiP2pManager.requestDeviceInfoSuspend(wifiChannel)
//                        updateState { oldState ->
//                            oldState.copy(localDevice = localDevice)
//                        }.await()
//                    }
//                }

                launch(Dispatchers.IO) {
                    while (true) {
                        val connection = bindState().map { it.connectedRemoteDevice }.firstOrError().await()
                        if (!connection.isPresent) {
                            val state = wifiP2pManager.discoverPeersSuspend(wifiChannel)
                            if (state == WIFI_P2P_SUCCESS_CODE) {
                                val peers =
                                    wifiP2pManager.requestPeersSuspend(channel = wifiChannel)
                                updateState { oldState -> oldState.copy(peers = peers) }.await()
                            }
                        }
                        delay(1000 * 8)
                    }
                }

                binding.transferFileLayout.clicks()
                    .switchMapSingle {
                        rxSingle {
                            // TODO: Handle transfer files.
                        }.onErrorResumeNext {
                            it.printStackTrace()
                            Single.just(Unit)
                        }
                    }
                    .bindLife()

                binding.closeCurrentConnectionLayout.clicks()
                        .flatMapSingle {
                            rxSingle { closeCurrentConnection() }
                                .loadingDialog(requireActivity())
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
                                    val config = WifiP2pConfig()
                                    config.deviceAddress = data.deviceAddress
                                    val state = wifiP2pManager.connectSuspend(wifiChannel, config)
                                    if (state == WIFI_P2P_SUCCESS_CODE) {
                                        bindState().map { it.connectedRemoteDevice }.skip(1).firstOrError().await()
                                    }
                                    println("Connect State: $state")
                                }
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .loadingDialog(requireActivity())
                            }
                        }
                ).emptyView<WifiP2pDevice, RemoteServerItemLayoutBinding, RemoteServerEmptyItemLayoutBinding>(
                        emptyLayout = R.layout.remote_server_empty_item_layout,
                        initShowEmpty = true)
                        .toAdapter()

                bindState()
                        .map { it.currentConnection }
                        .distinctUntilChanged()
                        .switchMapSingle {
                            rxSingle(Dispatchers.IO) {
                                if (it.isPresent) {
                                    val connectionInfo = it.get()
                                    val (remoteDevice, localAddress) = remoteDeviceInfo(connectionInfo = connectionInfo, localDevice = LOCAL_DEVICE)
                                    updateState { oldState ->
                                        oldState.copy(localAddress = Optional.of(localAddress), connectedRemoteDevice = Optional.of(remoteDevice))
                                    }.await()
                                } else {
                                    updateState { oldState ->
                                        oldState.copy(localAddress = Optional.empty(), connectedRemoteDevice = Optional.empty())
                                    }.await()
                                }
                                Unit
                            }.onErrorResumeNext {
                                it.printStackTrace()
                                rxSingle { closeCurrentConnection() }
                            }
                                .observeOn(AndroidSchedulers.mainThread())
                                .loadingDialog(requireActivity())
                        }
                        .bindLife()

            }
        }
    }

    suspend fun closeCurrentConnection() {
        wifiP2pManager.cancelConnectionSuspend(wifiChannel)
        wifiP2pManager.removeGroupSuspend(wifiChannel)
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