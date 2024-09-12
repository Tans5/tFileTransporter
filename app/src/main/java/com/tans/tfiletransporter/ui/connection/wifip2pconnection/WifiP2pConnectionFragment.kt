package com.tans.tfiletransporter.ui.connection.wifip2pconnection

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.getSystemService
import androidx.core.view.isEmpty
import com.airbnb.lottie.Lottie
import com.airbnb.lottie.LottieAnimationView
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.RemoteServerItemLayoutBinding
import com.tans.tfiletransporter.databinding.WifiP2pConnectionFragmentBinding
import com.tans.tfiletransporter.file.LOCAL_DEVICE
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnection
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnectionObserver
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnectionState
import com.tans.tfiletransporter.transferproto.p2pconn.bindSuspend
import com.tans.tfiletransporter.transferproto.p2pconn.closeSuspend
import com.tans.tfiletransporter.transferproto.p2pconn.connectSuspend
import com.tans.tfiletransporter.transferproto.p2pconn.transferFileSuspend
import com.tans.tfiletransporter.transferproto.p2pconn.waitClose
import com.tans.tfiletransporter.transferproto.p2pconn.waitHandshaking
import com.tans.tfiletransporter.ui.connection.ConnectionActivity
import com.tans.tfiletransporter.ui.connection.home.EventListener
import com.tans.tfiletransporter.ui.filetransport.FileTransportActivity
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.util.*
import kotlin.jvm.optionals.getOrNull

class WifiP2pConnectionFragment : BaseCoroutineStateFragment<WifiP2pConnectionFragment.Companion.WifiP2pConnectionState>(defaultState = WifiP2pConnectionState()) {

    private val wifiP2pManager: WifiP2pManager by lazy {
        requireActivity().getSystemService()!!
    }

    private val wifiChannel: WifiP2pManager.Channel by lazy { wifiP2pManager.initialize(requireActivity(), requireActivity().mainLooper, null) }

    private val wifiReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        @Suppress("DEPRECATION")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {

                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                        AndroidLog.e(TAG, "Wifi p2p disabled.")
                        updateState { WifiP2pConnectionState() }
                    } else {
                        AndroidLog.d(TAG, "Wifi p2p enabled.")
                        updateState { it.copy(isP2pEnabled = true) }
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
                    }
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    AndroidLog.d(TAG, "Connection state change.")
                    dataCoroutineScope?.launch {
                        checkWifiConnection()
                    }
                }

                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // Android 10 can't get mac address.
                }
            }
        }
    }
    override val layoutId: Int = R.layout.wifi_p2p_connection_fragment

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

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        launch {
            closeCurrentWifiConnection()
        }

        // Search P2P devices task.
        launch {
            while (true) {
                val (isP2pEnabled, connection) = currentState().let { it.isP2pEnabled to it.wifiP2PConnection.getOrNull() }
                if (isResumed && isVisible) {
                    if (!isP2pEnabled) {
                        updateState { oldState -> oldState.copy(peers = emptyList()) }
                    } else {
                        if (connection == null) {
                            val state = wifiP2pManager.discoverPeersSuspend(wifiChannel)
                            if (state == WifiActionResult.Success) {
                                AndroidLog.d(TAG, "Request discover peer success")
                                val peers = wifiP2pManager.requestPeersSuspend(channel = wifiChannel)
                                AndroidLog.d(TAG, "WIFI p2p devices: ${peers.orElseGet { null }?.deviceList?.joinToString { "${it.deviceName} -> ${it.deviceAddress}" }}")
                                updateState { oldState ->
                                    oldState.copy(peers = peers.getOrNull()?.deviceList?.map {
                                        P2pPeer(
                                            it.deviceName,
                                            it.deviceAddress
                                        )
                                    } ?: emptyList())
                                }
                            } else {
                                updateState { oldState -> oldState.copy(peers = emptyList()) }
                                AndroidLog.e(TAG, "Request discover peer fail: $state")
                            }
                        } else {
                            updateState { oldState -> oldState.copy(peers = emptyList()) }
                        }
                    }
                }
                delay(4000)
            }
        }

        // Create File transfer connection task
        launch {
            stateFlow
                .map { it.wifiP2PConnection }
                .distinctUntilChanged()
                .collectLatest {
                    val wifiConnection = it.getOrNull()
                    val currentState = currentState()
                    val lastHandshake = currentState.p2pHandshake.getOrNull()
                    // close last transfer connection.
                    if (lastHandshake != null) {
                        runCatching {
                            lastHandshake.p2pConnection.closeSuspend()
                            lastHandshake.p2pConnection.closeConnectionIfActive()
                        }
                        updateState { s -> s.copy(p2pHandshake = Optional.empty()) }
                    }
                    // create new transfer connection.
                    if (wifiConnection != null) {
                        val (isGroupOwner, groupOwnerAddress) = wifiConnection
                        val connection = P2pConnection(currentDeviceName = LOCAL_DEVICE, log = AndroidLog)

                        // Create tcp connection, Client will retry 3 times, GroupOwner as server.
                        if (isGroupOwner) {
                            // Server
                            val connectionResult = runCatching {
                                withTimeout(5000) {
                                    connection.bindSuspend(address = groupOwnerAddress)
                                }
                            }
                            if (connectionResult.isFailure) {
                                AndroidLog.e(
                                    TAG,
                                    "Bind $groupOwnerAddress fail: ${connectionResult.exceptionOrNull()?.message}"
                                )
                            }
                            connectionResult
                        } else {
                            // Client
                            var tryTimes = 3
                            var connectionResult: Result<Unit>
                            do {
                                delay(100)
                                tryTimes--
                                connectionResult = runCatching {
                                    connection.connectSuspend(address = groupOwnerAddress)
                                }
                                if (connectionResult.isSuccess) {
                                    break
                                }
                            } while (tryTimes > 0)
                            if (connectionResult.isFailure) {
                                AndroidLog.e(
                                    TAG,
                                    "Connect $groupOwnerAddress fail: ${connectionResult.exceptionOrNull()?.message}"
                                )
                            }
                            connectionResult
                        }
                            .onSuccess {
                                // Do file transfer handshake.
                                updateState { s -> s.copy(connectionStatus = ConnectionStatus.Handshaking) }
                                runCatching {
                                    withTimeout(1000) {
                                        connection.waitHandshaking()
                                    }
                                }
                                    .onSuccess { handshake ->
                                        // handshake success.
                                        AndroidLog.d(TAG, "Handshake success: $handshake")
                                        updateState { oldState ->
                                            oldState.copy(
                                                p2pHandshake = Optional.of(
                                                    P2pHandshake(
                                                        localAddress = handshake.localAddress.address,
                                                        remoteAddress = handshake.remoteAddress.address,
                                                        remoteDeviceName = handshake.remoteDeviceName,
                                                        p2pConnection = connection,
                                                    )
                                                ),
                                                connectionStatus = ConnectionStatus.Connected
                                            )
                                        }
                                        connection.addObserver(object : P2pConnectionObserver {
                                            override fun onNewState(state: P2pConnectionState) {
                                            }

                                            override fun requestTransferFile(
                                                handshake: P2pConnectionState.Handshake,
                                                isReceiver: Boolean
                                            ) {
                                                // Request transfer file.
                                                activity?.runOnUiThread {
                                                    startActivity(
                                                        FileTransportActivity.getIntent(
                                                            context = requireContext(),
                                                            localAddress = handshake.localAddress.address,
                                                            remoteAddress = handshake.remoteAddress.address,
                                                            remoteDeviceInfo = handshake.remoteDeviceName,
                                                            isServer = isReceiver,
                                                            requestShareFiles = (requireActivity() as ConnectionActivity).consumeRequestShareFiles()
                                                        )
                                                    )
                                                }
                                            }
                                        })
                                        // Wait connection close.
                                        connection.waitClose()
                                    }
                                    .onFailure { e ->
                                        // handshake fail
                                        AndroidLog.e(TAG, "Handshake error: ${e.message}", e)
                                        connection.closeConnectionIfActive()
                                    }
                                updateState { s -> s.copy(p2pHandshake = Optional.empty()) }
                            }
                            .onFailure {
                                // tcp connection create fail.
                                connection.closeConnectionIfActive()
                            }
                    }
                    closeCurrentWifiConnection()
                }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = WifiP2pConnectionFragmentBinding.bind(contentView)

        renderStateNewCoroutine({ it.p2pHandshake.getOrNull() to it.connectionStatus }) { (handshake, status) ->
            if (handshake != null) {
                viewBinding.remoteConnectedDeviceTv.text = getString(R.string.wifi_p2p_connection_remote_device,
                    handshake.remoteDeviceName, handshake.remoteAddress.toString().removePrefix("/"), status)
            } else {
                viewBinding.remoteConnectedDeviceTv.text = getString(R.string.wifi_p2p_connection_remote_device,
                    "Not Available", "Not Available", status.toString())
            }
        }

        renderStateNewCoroutine({ it.wifiP2PConnection.getOrNull() to it.p2pHandshake.getOrNull() }) { (wifiP2pConnection, handshake) ->
            if (wifiP2pConnection != null) {
                Toast.makeText(context, "wifip2p is not null", Toast.LENGTH_SHORT).show()
                viewBinding.connectedActionsLayout.visibility = View.VISIBLE
                viewBinding.remoteDevicesRv.visibility = View.GONE
                viewBinding.containerSearch.visibility = View.GONE
                if (handshake != null) {
                    viewBinding.transferFileLayout.visibility = View.VISIBLE
                    Toast.makeText(context, "handshake is not null", Toast.LENGTH_SHORT).show()
                } else {
                    viewBinding.transferFileLayout.visibility = View.INVISIBLE
                    Toast.makeText(context, "handshake is null", Toast.LENGTH_SHORT).show()
                }
            } else {
                viewBinding.connectedActionsLayout.visibility = View.GONE
                viewBinding.remoteDevicesRv.visibility = View.VISIBLE
                viewBinding.containerSearch.visibility = View.VISIBLE
            }
        }

        viewBinding.transferFileLayout.clicks(coroutineScope = this, minInterval = 1000L, clickWorkOn = Dispatchers.IO) {
            val handshake = currentState().p2pHandshake.getOrNull()
            val transferFileConnection = handshake?.p2pConnection
            if (transferFileConnection == null) {
                AndroidLog.e(TAG, "Request transfer file fail: handshake is null.")
            } else {
                runCatching {
                    transferFileConnection.transferFileSuspend()
                }.onSuccess {
                    AndroidLog.d(TAG, "Request transfer file success.")
                }.onFailure {
                    AndroidLog.e(TAG, "Request transfer file fail: ${it.message}", it)
                }
            }
        }

        viewBinding.closeCurrentConnectionLayout.clicks(coroutineScope = this, clickWorkOn = Dispatchers.IO) {
            runCatching {
                currentState().p2pHandshake.getOrNull()?.p2pConnection?.closeSuspend()
            }
            closeCurrentWifiConnection()
        }

        val connectionMutex = Mutex()

        val remoteDevicesAdapterBuilder = SimpleAdapterBuilderImpl<P2pPeer>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.remote_server_item_layout),
            dataSource = FlowDataSourceImpl(stateFlow.map { it.peers }),
            dataBinder = DataBinderImpl { data, view, _ ->
                viewBinding.containerSearch.visibility = View.GONE
                val itemViewBinding = RemoteServerItemLayoutBinding.bind(view)
                itemViewBinding.remoteDeviceTv.text = data.deviceName
                itemViewBinding.ipAddressTv.text = "Mac address: ${data.macAddress}"
                itemViewBinding.root.clicks(coroutineScope = this, clickWorkOn = Dispatchers.IO) {
                    if (connectionMutex.isLocked) { return@clicks }
                    connectionMutex.lock()
                    val connection = currentState().wifiP2PConnection.getOrNull()
                    if (connection == null) {
                        val config = WifiP2pConfig()
                        config.deviceAddress = data.macAddress
                        updateState { it.copy(connectionStatus = ConnectionStatus.Connecting) }
                        val state = wifiP2pManager.connectSuspend(wifiChannel, config)
                        if (state == WifiActionResult.Success) {
                            AndroidLog.d(TAG, "Request P2P connection success !!!")
                            val connectionInfo = wifiP2pManager.requestConnectionInfoSuspend(wifiChannel).getOrNull()
                            AndroidLog.d(TAG, "Connection group address: ${connectionInfo?.groupOwnerAddress}, is group owner: ${connectionInfo?.isGroupOwner}")
                        } else {
                            updateState { it.copy(connectionStatus = ConnectionStatus.NoConnection) }
                            AndroidLog.e(TAG, "Request P2P connection fail: $state !!!")
                        }
                    }
                    connectionMutex.unlock()
                }
            }
        )
        viewBinding.remoteDevicesRv.adapter = remoteDevicesAdapterBuilder.build()


    }

    private suspend fun closeCurrentWifiConnection() {
        updateState {  oldState ->
            oldState.copy(wifiP2PConnection = Optional.empty(), connectionStatus = ConnectionStatus.NoConnection)
        }
        wifiP2pManager.cancelConnectionSuspend(wifiChannel)
        wifiP2pManager.removeGroupSuspend(wifiChannel)
    }

    private suspend fun checkWifiConnection(): WifiP2pConnection? {
        val connectionOld = currentState().wifiP2PConnection.getOrNull()
        val connectionNew = wifiP2pManager.requestConnectionInfoSuspend(wifiChannel).getOrNull()?.let {
            WifiP2pConnection(
                isGroupOwner = it.isGroupOwner,
                groupOwnerAddress = it.groupOwnerAddress
            )
        }
        AndroidLog.d(TAG, "Connection group address: ${connectionNew?.groupOwnerAddress}, is group owner: ${connectionNew?.isGroupOwner}")
        if (connectionNew != connectionOld) {
            updateState {
                it.copy(
                    wifiP2PConnection = Optional.ofNullable(connectionNew),
                    connectionStatus = if (connectionNew == null) {
                        ConnectionStatus.NoConnection
                    } else {
                        it.connectionStatus
                    }
                )
            }
        }
        return connectionNew
    }

    override fun onDestroy() {
        super.onDestroy()
        Dispatchers.IO.asExecutor().execute {
            try {
                currentState().p2pHandshake.getOrNull()?.p2pConnection?.closeConnectionIfActive()
            } catch (_: Throwable) {
            }
        }
        wifiP2pManager.cancelConnect(wifiChannel, null)
        wifiP2pManager.removeGroup(wifiChannel, null)
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

        enum class ConnectionStatus {
            NoConnection,
            Connecting,
            Handshaking,
            Connected
        }

        data class WifiP2pConnectionState(
            val isP2pEnabled: Boolean = false,
            val peers: List<P2pPeer> = emptyList(),
            val wifiP2PConnection: Optional<WifiP2pConnection> = Optional.empty(),
            val p2pHandshake: Optional<P2pHandshake> = Optional.empty(),
            val connectionStatus: ConnectionStatus = ConnectionStatus.NoConnection
        )
    }


}