package com.tans.tfiletransporter.ui.activity.connection.wifip2pconnection

import android.annotation.SuppressLint
import android.net.wifi.p2p.*
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class WifiActionResult(val code: Int) {
    Success(-1),
    Error(WifiP2pManager.ERROR),
    Busy(WifiP2pManager.BUSY),
    Unsupported(WifiP2pManager.P2P_UNSUPPORTED)
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.discoverPeersSuspend(channel: WifiP2pManager.Channel) = suspendCoroutine<WifiActionResult> { cont ->
    discoverPeers(channel, object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            cont.resume(WifiActionResult.Success)
        }

        override fun onFailure(reason: Int) {
            cont.resume(WifiActionResult.values().first { it.code == reason } ?: WifiActionResult.Error)
        }

    })
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.requestPeersSuspend(channel: WifiP2pManager.Channel) = suspendCoroutine<Optional<WifiP2pDeviceList>> { cont ->
    requestPeers(channel) { cont.resume(Optional.ofNullable(it)) }
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.connectSuspend(channel: WifiP2pManager.Channel, config: WifiP2pConfig) = suspendCoroutine<WifiActionResult> { cont ->
    connect(channel, config, object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            cont.resume(WifiActionResult.Success)
        }

        override fun onFailure(reason: Int) {
            cont.resume(WifiActionResult.values().first { it.code == reason } ?: WifiActionResult.Error)
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

suspend fun WifiP2pManager.cancelConnectionSuspend(channel: WifiP2pManager.Channel) = suspendCoroutine<WifiActionResult> { cont ->
    cancelConnect(channel, object : WifiP2pManager.ActionListener {

        override fun onSuccess() {
            cont.resume(WifiActionResult.Success)
        }

        override fun onFailure(reason: Int) {
            cont.resume(WifiActionResult.values().first { it.code == reason } ?: WifiActionResult.Error)
        }

    })
}

suspend fun WifiP2pManager.removeGroupSuspend(channel: WifiP2pManager.Channel) = suspendCoroutine<WifiActionResult> { cont ->
    removeGroup(channel, object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            cont.resume(WifiActionResult.Success)
        }

        override fun onFailure(reason: Int) {
            cont.resume(WifiActionResult.values().first { it.code == reason } ?: WifiActionResult.Error)
        }

    })
}