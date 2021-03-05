package com.tans.tfiletransporter.ui.activity.connection.wifip2pconnection

import android.annotation.SuppressLint
import android.net.wifi.p2p.*
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

const val WIFI_P2P_SUCCESS_CODE = -1

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.discoverPeersSuspend(channel: WifiP2pManager.Channel) = suspendCoroutine<Int> { cont ->
    discoverPeers(channel, object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            cont.resume(WIFI_P2P_SUCCESS_CODE)
        }

        override fun onFailure(reason: Int) {
            cont.resume(reason)
        }

    })
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.requestPeersSuspend(channel: WifiP2pManager.Channel) = suspendCoroutine<Optional<WifiP2pDeviceList>> { cont ->
    requestPeers(channel) { cont.resume(Optional.ofNullable(it)) }
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.connectSuspend(channel: WifiP2pManager.Channel, config: WifiP2pConfig) = suspendCoroutine<Int> { cont ->
    connect(channel, config, object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            cont.resume(WIFI_P2P_SUCCESS_CODE)
        }

        override fun onFailure(reason: Int) {
            cont.resume(reason)
        }

    })
}

suspend fun WifiP2pManager.requestConnectionInfoSuspend(channel: WifiP2pManager.Channel) = suspendCoroutine<Optional<WifiP2pInfo>> { cont ->
    requestConnectionInfo(channel) { info ->
        cont.resume(if (info?.groupOwnerAddress == null) Optional.empty() else Optional.of(info))
    }
}

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.Q)
suspend fun WifiP2pManager.requestDeviceInfoSuspend(channel: WifiP2pManager.Channel) = suspendCoroutine<Optional<WifiP2pDevice>> { cont ->
    requestDeviceInfo(channel) { device -> cont.resume(Optional.ofNullable(device)) }
}

suspend fun WifiP2pManager.cancelConnectionSuspend(channel: WifiP2pManager.Channel) = suspendCoroutine<Int> { cont ->
    cancelConnect(channel, object : WifiP2pManager.ActionListener {

        override fun onSuccess() {
            cont.resume(WIFI_P2P_SUCCESS_CODE)
        }

        override fun onFailure(reason: Int) {
            cont.resume(reason)
        }

    })
}

suspend fun WifiP2pManager.removeGroupSuspend(channel: WifiP2pManager.Channel) = suspendCoroutine<Int> { cont ->
    removeGroup(channel, object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            cont.resume(WIFI_P2P_SUCCESS_CODE)
        }

        override fun onFailure(reason: Int) {
            cont.resume(reason)
        }

    })
}