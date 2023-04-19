package com.tans.tfiletransporter.ui.activity.connection.wifip2pconnection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.RemoteServerItemLayoutBinding
import com.tans.tfiletransporter.databinding.WifiP2pConnectionFragmentBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnection
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnectionObserver
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnectionState
import com.tans.tfiletransporter.ui.activity.BaseFragment
import io.reactivex.rxkotlin.withLatestFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
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
                            oldState.copy(wifiP2PConnection = Optional.of(WifiP2pConnection(isGroupOwner = isGroupOwner, groupOwnerAddress = ownerAddress)), p2pHandshake = Optional.empty())
                        } else {
                            oldState.copy(wifiP2PConnection = Optional.empty(), p2pHandshake = Optional.empty())
                        }
                    }.bindLife()
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Android 10 can't get mac address.
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
                    binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, it.get().localAddress.toString().removePrefix("/"))
                } else {
                    binding.localAddressTv.text = getString(R.string.wifi_p2p_connection_local_address, "Not Available")
                }
            }.bindLife()

            render({ it.p2pHandshake }) {
                if (it.isPresent) {
                    binding.remoteConnectedDeviceTv.text = getString(R.string.wifi_p2p_connection_remote_device,
                            it.get().remoteDeviceName, it.get().remoteAddress.toString().removePrefix("/"))
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
                    val (connection, isP2pEnabled) = bindState().map { it.wifiP2PConnection to it.isP2pEnabled }.firstOrError().await()
                    val connectionNow =
                        wifiP2pManager.requestConnectionInfoSuspend(wifiChannel).getOrNull()
                    AndroidLog.d(TAG, "Connection group address: ${connectionNow?.groupOwnerAddress}, is group owner: ${connectionNow?.isGroupOwner}")
                    updateState {
                        if (connectionNow != null) {
                            it.copy(
                                wifiP2PConnection = Optional.of(
                                    WifiP2pConnection(
                                        isGroupOwner = connectionNow.isGroupOwner,
                                        groupOwnerAddress = connectionNow.groupOwnerAddress
                                    )
                                )
                            )
                        } else {
                            it.copy(wifiP2PConnection = Optional.empty(), p2pHandshake = Optional.empty())
                        }
                    }.await()
                    if (!isP2pEnabled) {
                        updateState { oldState -> oldState.copy(peers = emptyList()) }.await()
                    } else if (!connection.isPresent) {
                        if (connectionNow == null) {
                            val state = wifiP2pManager.discoverPeersSuspend(wifiChannel)
                            if (state == WifiActionResult.Success) {
                                AndroidLog.d(TAG, "Request discover peer success")
                                val peers =
                                    wifiP2pManager.requestPeersSuspend(channel = wifiChannel)
                                AndroidLog.d(
                                    TAG,
                                    "WIFI p2p devices: ${peers.orElseGet { null }?.deviceList?.joinToString { "${it.deviceName} -> ${it.deviceAddress}" }}"
                                )
                                updateState { oldState ->
                                    oldState.copy(peers = peers.getOrNull()?.deviceList?.map {
                                        P2pPeer(
                                            it.deviceName,
                                            it.deviceAddress
                                        )
                                    } ?: emptyList())
                                }.await()
                            } else {
                                updateState { oldState -> oldState.copy(peers = emptyList()) }.await()
                                AndroidLog.e(TAG, "Request discover peer fail: $state")
                            }
                        }
                    }
                    delay(5000)
                }
            }

            binding.transferFileLayout.clicks()
                .withLatestFrom(bindState().map { it.p2pHandshake })
                .switchMapSingle { (_, handshake) ->
                    rxSingle(Dispatchers.IO) {
                        val connection = handshake.getOrNull()?.p2pConnection
                        if (connection == null) {
                            AndroidLog.e(TAG, "Request transfer file fail: handshake is null.")
                        } else {
                            val requestTransferResult = runCatching {
                                connection.transferFileSuspend()
                            }
                            if (requestTransferResult.isFailure) {
                                AndroidLog.e(TAG, "Request transfer file fail: ${requestTransferResult.exceptionOrNull()?.message}")
                            } else {
                                AndroidLog.d(TAG, "Request transfer file success.")
                            }
                        }
                    }
                }
                .bindLife()

            binding.closeCurrentConnectionLayout.clicks()
                .flatMapSingle {
                    rxSingle { closeCurrentConnection() }
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
                    dataUpdater = bindState().map { if (!it.wifiP2PConnection.isPresent) it.peers else emptyList() }.distinctUntilChanged(),
                    itemClicks = listOf { binding, _ ->
                        binding.root to { _, data ->
                            rxSingle(Dispatchers.IO) {
                                if (connectionMutex.isLocked) { return@rxSingle }
                                connectionMutex.lock()
                                val connection = bindState().map { it.wifiP2PConnection }.firstOrError().await()
                                if (!connection.isPresent) {
                                    val config = WifiP2pConfig()
                                    config.deviceAddress = data.macAddress
                                    val state = wifiP2pManager.connectSuspend(wifiChannel, config)
                                    if (state == WifiActionResult.Success) {
                                        AndroidLog.d(TAG, "Request P2P connection success !!!")
                                        val connectionInfo = wifiP2pManager.requestConnectionInfoSuspend(wifiChannel).getOrNull()
                                        AndroidLog.d(TAG, "Connection group address: ${connectionInfo?.groupOwnerAddress}, is group owner: ${connectionInfo?.isGroupOwner}")
                                    } else {
                                        AndroidLog.e(TAG, "Request P2P connection fail: $state !!!")
                                    }
                                }
                                connectionMutex.unlock()
                            }
                        }
                    }
                ).toAdapter()

            bindState()
                .map { it.wifiP2PConnection }
                .distinctUntilChanged()
                .switchMapSingle { wifiConnection ->
                    rxSingle(Dispatchers.IO) {
                        val lastHandshake = bindState().firstOrError().await().p2pHandshake
                        if (lastHandshake.isPresent) {
                            lastHandshake.get().p2pConnection.closeConnectionIfActive()
                            updateState { it.copy(p2pHandshake = Optional.empty()) }.await()
                        }

                        if (wifiConnection.isPresent) {
                            val (isGroupOwner, groupOwnerAddress) = wifiConnection.get()
                            val connection = P2pConnection()
                            val connectionResult = if (isGroupOwner) {
                                val connectionResult = runCatching {
                                    withTimeout(5000) {
                                        connection.bindSuspend(address = groupOwnerAddress)
                                    }
                                }
                                if (connectionResult.isFailure) {
                                    AndroidLog.e(TAG, "Bind $groupOwnerAddress fail: ${connectionResult.exceptionOrNull()?.message}")
                                }
                                connectionResult
                            } else {
                                var tryTimes = 3
                                var connectionResult: Result<Unit>
                                do {
                                    delay(100)
                                    tryTimes --
                                    connectionResult = runCatching {
                                        connection.connectSuspend(address = groupOwnerAddress)
                                    }
                                    if (connectionResult.isSuccess) {
                                        break
                                    }
                                } while (tryTimes > 0)
                                if (connectionResult.isFailure) {
                                    AndroidLog.e(TAG, "Connect $groupOwnerAddress fail: ${connectionResult.exceptionOrNull()?.message}")
                                }
                                connectionResult
                            }
                            if (connectionResult.isFailure) {
                                connection.closeConnectionIfActive()
                                closeCurrentConnection()
                            } else {
                                val handshakeResult = withTimeout(1000) {
                                    runCatching {
                                        connection.waitHandshaking()
                                    }
                                }
                                val handshake = handshakeResult.getOrNull()
                                if (handshake == null) {
                                    AndroidLog.e(TAG, "Handshake error: ${handshakeResult.exceptionOrNull()?.message}")
                                    connection.closeConnectionIfActive()
                                } else {
                                    AndroidLog.d(TAG, "Handshake success: $handshake")
                                    updateState { oldState ->
                                        oldState.copy(
                                            p2pHandshake = Optional.of(P2pHandshake(
                                                localAddress = handshake.localAddress.address,
                                                remoteAddress = handshake.remoteAddress.address,
                                                remoteDeviceName = handshake.remoteDeviceName,
                                                p2pConnection = connection
                                            ))
                                        )
                                    }.await()
                                    connection.addObserver(object : P2pConnectionObserver {
                                        override fun requestTransferFile(
                                            handshake: P2pConnectionState.Handshake,
                                            isReceiver: Boolean
                                        ) {
                                            activity?.runOnUiThread {
                                                // TODO: Transfer file
                                                Toast.makeText(context, "TransferFile: $isReceiver", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    })
                                    connection.waitClose()
                                }
                                closeCurrentConnection()
                            }
                        }
                    }
                }
                .bindLife()
        }
    }

    private suspend fun closeCurrentConnection() {
        updateState {  oldState ->
            oldState.p2pHandshake.getOrNull()?.p2pConnection?.closeConnectionIfActive()
            oldState.copy(
                wifiP2PConnection = Optional.empty(),
                p2pHandshake = Optional.empty()
            )
        }.await()
        wifiP2pManager.cancelConnectionSuspend(wifiChannel)
        wifiP2pManager.removeGroupSuspend(wifiChannel)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Dispatchers.IO.asExecutor().execute {
            try {
                bindState().firstOrError().blockingGet()?.p2pHandshake?.get()?.p2pConnection?.closeConnectionIfActive()
            } catch (_: Throwable) {

            }
        }
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

        data class WifiP2pConnection(
            val isGroupOwner: Boolean,
            val groupOwnerAddress: InetAddress
        )

        data class P2pHandshake(
            val p2pConnection: P2pConnection,
            val localAddress: InetAddress,
            val remoteAddress: InetAddress,
            val remoteDeviceName: String
        )

        data class WifiP2pConnectionState(
            val isP2pEnabled: Boolean = false,
            val peers: List<P2pPeer> = emptyList(),
            val wifiP2PConnection: Optional<WifiP2pConnection> = Optional.empty(),
            val p2pHandshake: Optional<P2pHandshake> = Optional.empty()
        )
    }
}