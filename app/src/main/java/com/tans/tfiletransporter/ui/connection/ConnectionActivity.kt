package com.tans.tfiletransporter.ui.connection

import android.Manifest
import android.content.Intent
import android.net.*
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.activity.addCallback
import androidx.lifecycle.Lifecycle
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ConnectionActivityBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.ui.commomdialog.showOptionalDialogSuspend
import com.tans.tfiletransporter.ui.commomdialog.showSettingsDialog
import com.tans.tfiletransporter.ui.connection.localconnetion.LocalNetworkConnectionFragment
import com.tans.tfiletransporter.ui.connection.wifip2pconnection.WifiP2pConnectionFragment
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.permission.permissionsRequestSuspend
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

@SystemBarStyle(statusBarThemeStyle = 1, navigationBarThemeStyle = 1)
class ConnectionActivity : BaseCoroutineStateActivity<Unit>(
    defaultState = Unit
) {
    override val layoutId: Int = R.layout.connection_activity

    private val wifiP2pFragment by lazyViewModelField("wifiP2pFragment") {
        WifiP2pConnectionFragment()
    }

    private val localNetworkFragment by lazyViewModelField("localNetworkFragment") {
        LocalNetworkConnectionFragment()
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        onBackPressedDispatcher.addCallback {
            finish()
        }
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = ConnectionActivityBinding.bind(contentView)
        launch {
            val permissionsNeed = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsNeed.add(Manifest.permission.READ_MEDIA_IMAGES)
                    permissionsNeed.add(Manifest.permission.READ_MEDIA_AUDIO)
                    permissionsNeed.add(Manifest.permission.READ_MEDIA_VIDEO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        permissionsNeed.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    }
                } else {
                    permissionsNeed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            } else {
                permissionsNeed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissionsNeed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsNeed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                permissionsNeed.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissionsNeed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            runCatching {
                permissionsRequestSuspend(*permissionsNeed.toTypedArray())
            }.onSuccess { (_, denied) ->
                if (denied.isNotEmpty()) {
                    AndroidLog.e(TAG, "Contains denied permissions: $denied")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {

                    val grant = this@ConnectionActivity.supportFragmentManager.showOptionalDialogSuspend(
                        title = getString(R.string.permission_request_title),
                        message = getString(R.string.permission_storage_request_content)
                    )

                    if (grant == true) {
                        val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        i.data = Uri.fromParts("package", packageName, null)
                        startActivity(i)
                    }
                }
            }.onFailure {
                AndroidLog.e(TAG, "Request permission error: ${it.message}", it)
            }
        }

        viewBinding.toolBar.menu.findItem(R.id.settings).setOnMenuItemClickListener {
            this@ConnectionActivity.supportFragmentManager.showSettingsDialog()
            true
        }

        val tcWifiP2p = supportFragmentManager.beginTransaction()
        if (supportFragmentManager.findFragmentByTag(WIFI_P2P_CONNECTION_FRAGMENT_TAG) == null) {
            tcWifiP2p.add(R.id.wifi_p2p_fragment_container, wifiP2pFragment, WIFI_P2P_CONNECTION_FRAGMENT_TAG)
        }
        tcWifiP2p.setMaxLifecycle(wifiP2pFragment, Lifecycle.State.RESUMED)
        tcWifiP2p.commitAllowingStateLoss()

        val tcLocalNetwork = supportFragmentManager.beginTransaction()
        if (supportFragmentManager.findFragmentByTag(LOCAL_NETWORK_FRAGMENT_TAG) == null) {
            tcWifiP2p.add(R.id.local_network_fragment_container, localNetworkFragment, LOCAL_NETWORK_FRAGMENT_TAG)
        }
        tcLocalNetwork.setMaxLifecycle(localNetworkFragment, Lifecycle.State.RESUMED)
        tcLocalNetwork.commitAllowingStateLoss()
    }

    companion object {
        private const val TAG = "ConnectionActivity"

        private const val WIFI_P2P_CONNECTION_FRAGMENT_TAG = "WIFI_P2P_CONNECTION_FRAGMENT_TAG"
        private const val LOCAL_NETWORK_FRAGMENT_TAG = "LOCAL_NETWORK_FRAGMENT_TAG"
    }
}