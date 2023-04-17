package com.tans.tfiletransporter.ui.activity.connection.wifip2pconnection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Build
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
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.sync.Mutex
import org.kodein.di.instance
import java.net.InetAddress
import java.util.*
import kotlin.jvm.optionals.getOrNull

class WifiP2pConnectionFragment : BaseFragment<WifiP2pConnectionFragmentBinding, WifiP2pConnectionFragment.Companion.WifiP2pConnectionState>(
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
                    if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                        AndroidLog.e(TAG, "Wifi p2p disabled.")
                        updateState { WifiP2pConnectionState() }.bindLife()
                    } else {
                        AndroidLog.d(TAG, "Wifi p2p enabled.")
                        updateState { it.copy(isP2pEnabled = true) }.bindLife()
                    }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    val wifiDevicesList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST, WifiP2pDeviceList::class.java)
                    } else {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST)
                    }
                    AndroidLog.d(TAG, "WIFI p2p devices: ${wifiDevicesList?.deviceList?.joinToString { "${it.deviceName} -> ${it.deviceAddress}" }}")
                    updateState { oldState ->
                        oldState.copy(peers = wifiDevicesList?.deviceList?.map { P2pPeer(it.deviceName, it.deviceAddress) } ?: emptyList())
                    }.bindLife()
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val wifiP2pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, WifiP2pInfo::class.java)
                    } else {
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                    }
                    AndroidLog.d(TAG, "WIFI P2P new connection: OwnerAddress ->${wifiP2pInfo?.groupOwnerAddress.toString()}, IsOwner -> ${wifiP2pInfo?.isGroupOwner}")
                    updateState { oldState ->
                        val isGroupOwner = wifiP2pInfo?.isGroupOwner
                        val ownerAddress = wifiP2pInfo?.groupOwnerAddress
                        if (isGroupOwner != null && ownerAddress != null) {
                            oldState.copy(p2pConnection = Optional.of(P2pConnection(isGroupOwner = isGroupOwner, groupOwnerAddress = ownerAddress)), p2pHandshake = Optional.empty())
                        } else {
                            oldState.copy(p2pConnection = Optional.empty(), p2pHandshake = Optional.empty())
                        }
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
            launch {
                closeCurrentConnection()
            }
            render({ it.p2pHandshake }) {
                if (it.isPresent) {
                    binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, it.get().localAddress)
                } else {
                    binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, "Not Available")
                }
            }.bindLife()

            render({ it.p2pHandshake }) {
                if (it.isPresent) {
                    binding.remoteConnectedDeviceTv.text = getString(R.string.wifi_p2p_connection_remote_device,
                            it.get().remoteDeviceName, it.get().remoteAddress)
                    binding.connectedActionsLayout.visibility = View.VISIBLE
                    binding.remoteDevicesRv.visibility = View.GONE
                } else {
                    binding.remoteConnectedDeviceTv.text = getString(R.string.wifi_p2p_connection_remote_device,
                            "Not Available", "Not Available")
                    binding.connectedActionsLayout.visibility = View.GONE
                    binding.remoteDevicesRv.visibility = View.VISIBLE
                }
            }.bindLife()

            launch(Dispatchers.IO) {
                while (true) {
                    val (connection, isP2pEnabled) = bindState().map { it.p2pConnection to it.isP2pEnabled }.firstOrError().await()
                    if (!isP2pEnabled) {
                        updateState { oldState -> oldState.copy(peers = emptyList()) }.await()
                    } else if (!connection.isPresent) {
                        val state = wifiP2pManager.discoverPeersSuspend(wifiChannel)
                        if (state == WifiActionResult.Success) {
                            AndroidLog.d(TAG, "Request discover peer success")
                            val peers = wifiP2pManager.requestPeersSuspend(channel = wifiChannel)
                            AndroidLog.d(TAG, "WIFI p2p devices: ${peers.orElseGet { null }?.deviceList?.joinToString { "${it.deviceName} -> ${it.deviceAddress}" }}")
                            updateState { oldState -> oldState.copy(peers = peers.getOrNull()?.deviceList?.map { P2pPeer(it.deviceName, it.deviceAddress) } ?: emptyList()) }.await()
                        } else {
                            updateState { oldState -> oldState.copy(peers = emptyList()) }.await()
                            AndroidLog.e(TAG, "Request discover peer fail: $state")
                        }
                    }
                    delay(5000)
                }
            }

//                binding.transferFileLayout.clicks()
//                    .switchMapSingle {
//                        rxSingle(Dispatchers.IO) {
//                            val remoteDevice = bindState().map { it.p2pRemoteDevice }.firstOrError().await()
//                            val localAddress = bindState().map { it.p2pLocalAddress }.firstOrError().await()
//                            if (remoteDevice.isPresent && localAddress.isPresent) {
//                                val sc = openAsynchronousSocketChannel()
//                                sc.connectSuspend(InetSocketAddress(remoteDevice.get().first, FILE_WIFI_P2P_FILE_TRANSFER_LISTEN_PORT))
//                                sc.close()
//                                withContext(Dispatchers.Main) {
//                                    startActivity(
//                                        FileTransportActivity.getIntent(
//                                            context = requireContext(),
//                                            localAddress = localAddress.get(),
//                                            remoteDevice = remoteDevice.get(),
//                                            asServer = false
//                                        )
//                                    )
//                                }
//                            }
//                        }.onErrorResumeNext {
//                            it.printStackTrace()
//                            Single.just(Unit)
//                        }
//                    }
//                    .bindLife()

            binding.closeCurrentConnectionLayout.clicks()
                .flatMapSingle {
                    rxSingle { closeCurrentConnection() }
                        .loadingDialog(requireActivity())
                }
                .bindLife()

            val connectionMutex = Mutex()
            binding.remoteDevicesRv.adapter =
                SimpleAdapterSpec<P2pPeer, RemoteServerItemLayoutBinding>(
                    layoutId = R.layout.remote_server_item_layout,
                    bindData = { _, device, lBinding ->
                        lBinding.device = device.deviceName
                        lBinding.ipAddress = "Mac address: ${device.macAddress}"
                    },
                    dataUpdater = bindState().map { it.peers }.distinctUntilChanged(),
                    itemClicks = listOf { binding, _ ->
                        binding.root to { _, data ->
                            rxSingle {
                                if (connectionMutex.isLocked) { return@rxSingle }
                                connectionMutex.lock()
                                val connection = bindState().map { it.p2pConnection }.firstOrError().await()
                                if (!connection.isPresent) {
                                    val config = WifiP2pConfig()
                                    config.deviceAddress = data.macAddress
                                    val state = wifiP2pManager.connectSuspend(wifiChannel, config)
                                    if (state == WifiActionResult.Success) {
                                        AndroidLog.d(TAG, "Request P2P connection success !!!")
                                    } else {
                                        AndroidLog.e(TAG, "Request P2P connection fail: $state !!!")
                                    }
                                }
                                connectionMutex.unlock()
                            }.loadingDialog(requireActivity())
                        }
                    }
                ).emptyView<P2pPeer, RemoteServerItemLayoutBinding, RemoteServerEmptyItemLayoutBinding>(
                    emptyLayout = R.layout.remote_server_empty_item_layout,
                    initShowEmpty = true
                )
                    .toAdapter()

//                bindState()
//                        .map { it.p2pConnection }
//                        .distinctUntilChanged()
//                        .switchMapSingle {
//                            rxSingle(Dispatchers.IO) {
//                                if (it.isPresent) {
//                                    val connectionInfo = it.get()
//                                    val (remoteDevice, localAddress) = remoteDeviceInfo(connectionInfo = connectionInfo, localDevice = LOCAL_DEVICE)
//                                    updateState { oldState ->
//                                        oldState.copy(p2pLocalAddress = Optional.of(localAddress), p2pRemoteDevice = Optional.of(remoteDevice))
//                                    }.await()
//                                } else {
//                                    updateState { oldState ->
//                                        oldState.copy(p2pLocalAddress = Optional.empty(), p2pRemoteDevice = Optional.empty())
//                                    }.await()
//                                }
//                                Unit
//                            }
//                                .timeout(10000L, TimeUnit.MILLISECONDS)
//                                .onErrorResumeNext {
//                                it.printStackTrace()
//                                rxSingle { closeCurrentConnection() }
//                            }
//                                .observeOn(AndroidSchedulers.mainThread())
//                                .loadingDialog(requireActivity())
//                        }
//                        .bindLife()

//                bindState()
//                    .map { it.p2pLocalAddress }
//                    .distinctUntilChanged()
//                    .switchMapSingle { localAddress ->
//                        rxSingle(Dispatchers.IO) {
//                            if (localAddress.isPresent) {
//                                val ssc = openAsynchronousServerSocketChannelSuspend()
//                                ssc.use {
//                                    ssc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
//                                    ssc.bindSuspend(InetSocketAddress(localAddress.get(), FILE_WIFI_P2P_FILE_TRANSFER_LISTEN_PORT), Int.MAX_VALUE)
//                                    while (true) {
//                                        val sc = ssc.acceptSuspend()
//                                        sc.close()
//                                        val remoteDevice = bindState().map { it.p2pRemoteDevice }.firstOrError().await()
//                                        if (remoteDevice.isPresent) {
//                                            withContext(Dispatchers.Main) {
//                                                startActivity(
//                                                    FileTransportActivity.getIntent(
//                                                        context = requireContext(),
//                                                        localAddress = localAddress.get(),
//                                                        remoteDevice = remoteDevice.get(),
//                                                        asServer = true
//                                                    )
//                                                )
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }.onErrorResumeNext {
//                            it.printStackTrace()
//                            Single.just(Unit)
//                        }
//                    }
//                    .bindLife()
        }
    }

    private suspend fun closeCurrentConnection() {
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

    companion object {
        private const val TAG = "WifiP2pConn"

        data class P2pPeer(
            val deviceName: String,
            val macAddress: String,
        )

        data class P2pConnection(
            val isGroupOwner: Boolean,
            val groupOwnerAddress: InetAddress
        )

        data class P2pHandshake(
            val localAddress: InetAddress,
            val remoteAddress: InetAddress,
            val remoteDeviceName: String
        )

        data class WifiP2pConnectionState(
            val isP2pEnabled: Boolean = false,
            val peers: List<P2pPeer> = emptyList(),
            val p2pConnection: Optional<P2pConnection> = Optional.empty(),
            val p2pHandshake: Optional<P2pHandshake> = Optional.empty()
        )
    }
}