package com.tans.tfiletransporter.ui.activity.connection.wifip2pconnection

import android.annotation.SuppressLint
import android.net.wifi.p2p.*
import android.os.Build
import androidx.annotation.RequiresApi
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnection
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnectionObserver
import com.tans.tfiletransporter.transferproto.p2pconn.P2pConnectionState
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

enum class WifiActionResult(val code: Int) {
    Success(-1),
    Error(WifiP2pManager.ERROR),
    Busy(WifiP2pManager.BUSY),
    Unsupported(WifiP2pManager.P2P_UNSUPPORTED)
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.discoverPeersSuspend(channel: WifiP2pManager.Channel) = suspendCancellableCoroutine<WifiActionResult> { cont ->
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
suspend fun WifiP2pManager.requestPeersSuspend(channel: WifiP2pManager.Channel) = suspendCancellableCoroutine<Optional<WifiP2pDeviceList>> { cont ->
    requestPeers(channel) { cont.resume(Optional.ofNullable(it)) }
}

@SuppressLint("MissingPermission")
suspend fun WifiP2pManager.connectSuspend(channel: WifiP2pManager.Channel, config: WifiP2pConfig) = suspendCancellableCoroutine<WifiActionResult> { cont ->
    connect(channel, config, object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            cont.resume(WifiActionResult.Success)
        }

        override fun onFailure(reason: Int) {
            cont.resume(WifiActionResult.values().first { it.code == reason } ?: WifiActionResult.Error)
        }

    })
}

suspend fun WifiP2pManager.requestConnectionInfoSuspend(channel: WifiP2pManager.Channel) = suspendCancellableCoroutine<Optional<WifiP2pInfo>> { cont ->
    requestConnectionInfo(channel) { info ->
        cont.resume(if (info?.groupOwnerAddress == null) Optional.empty() else Optional.of(info))
    }
}

@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.Q)
suspend fun WifiP2pManager.requestDeviceInfoSuspend(channel: WifiP2pManager.Channel) = suspendCancellableCoroutine<Optional<WifiP2pDevice>> { cont ->
    requestDeviceInfo(channel) { device -> cont.resume(Optional.ofNullable(device)) }
}

suspend fun WifiP2pManager.cancelConnectionSuspend(channel: WifiP2pManager.Channel) = suspendCancellableCoroutine<WifiActionResult> { cont ->
    cancelConnect(channel, object : WifiP2pManager.ActionListener {

        override fun onSuccess() {
            cont.resume(WifiActionResult.Success)
        }

        override fun onFailure(reason: Int) {
            cont.resume(WifiActionResult.values().first { it.code == reason } ?: WifiActionResult.Error)
        }

    })
}

suspend fun WifiP2pManager.removeGroupSuspend(channel: WifiP2pManager.Channel) = suspendCancellableCoroutine<WifiActionResult> { cont ->
    removeGroup(channel, object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            cont.resume(WifiActionResult.Success)
        }

        override fun onFailure(reason: Int) {
            cont.resume(WifiActionResult.values().first { it.code == reason } ?: WifiActionResult.Error)
        }

    })
}

suspend fun P2pConnection.bindSuspend(address: InetAddress) = suspendCancellableCoroutine { cont ->
    bind(address, object : SimpleCallback<Unit> {
        override fun onSuccess(data: Unit) {
            cont.resume(Unit)
        }

        override fun onError(errorMsg: String) {
            cont.resumeWithException(Throwable(errorMsg))
        }
    })
}

suspend fun P2pConnection.connectSuspend(address: InetAddress) = suspendCancellableCoroutine { cont ->
    connect(address, object : SimpleCallback<Unit> {
        override fun onSuccess(data: Unit) {
            cont.resume(Unit)
        }
        override fun onError(errorMsg: String) {
            cont.resumeWithException(Throwable(errorMsg))
        }
    })
}

suspend fun P2pConnection.waitHandshaking() = suspendCancellableCoroutine<P2pConnectionState.Handshake> { cont ->
    addObserver(object : P2pConnectionObserver {
        override fun onNewState(state: P2pConnectionState) {
            if (state is P2pConnectionState.Handshake) {
                if (cont.isActive) {
                    cont.resume(state)
                }
                removeObserver(this)
            }
            if (state is P2pConnectionState.NoConnection) {
                if (cont.isActive) {
                    cont.resumeWithException(Throwable("Connection is closed"))
                }
                removeObserver(this)
            }
        }
    })
}

suspend fun P2pConnection.waitClose() = suspendCancellableCoroutine<Unit> { cont ->
    addObserver(object : P2pConnectionObserver {
        override fun onNewState(state: P2pConnectionState) {
            if (state is P2pConnectionState.NoConnection) {
                if (cont.isActive) {
                    cont.resume(Unit)
                }
                removeObserver(this)
            }
        }
    })
}