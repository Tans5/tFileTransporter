package com.tans.tfiletransporter.ui.connection

import android.Manifest
import android.content.Intent
import android.net.*
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.lifecycle.Lifecycle
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ConnectionActivityBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.ui.commomdialog.showOptionalDialogSuspend
import com.tans.tfiletransporter.ui.commomdialog.showSettingsDialog
import com.tans.tfiletransporter.ui.connection.home.EventListener
import com.tans.tfiletransporter.ui.connection.home.HomeFragment
import com.tans.tfiletransporter.ui.connection.wifip2pconnection.WifiP2pConnectionFragment
import com.tans.tfiletransporter.utils.uri2FileReal
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.permission.permissionsRequestSuspend
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import java.io.File

@SystemBarStyle(statusBarThemeStyle = 1, navigationBarThemeStyle = 1)
class ConnectionActivity :
    BaseCoroutineStateActivity<ConnectionActivity.Companion.ConnectionActivityState>(
        defaultState = ConnectionActivityState()
    ), EventListener {
    override val layoutId: Int = R.layout.connection_activity

    private val wifiP2pFragment by lazyViewModelField("wifiP2pFragment") {
        WifiP2pConnectionFragment()
    }

    private val homeFragment by lazyViewModelField("homeFragment") {
        HomeFragment(this)
    }

//    private val localNetworkFragment by lazyViewModelField("localNetworkFragment") {
//        LocalNetworkConnectionFragment()
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkIntentAction(intent)
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

                    val grant =
                        this@ConnectionActivity.supportFragmentManager.showOptionalDialogSuspend(
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
            tcWifiP2p.add(
                R.id.wifi_p2p_fragment_container,
                homeFragment,
                WIFI_P2P_CONNECTION_FRAGMENT_TAG
            )
        }
        tcWifiP2p.setMaxLifecycle(homeFragment, Lifecycle.State.RESUMED)
        tcWifiP2p.commitAllowingStateLoss()

//        val tcLocalNetwork = supportFragmentManager.beginTransaction()
//        if (supportFragmentManager.findFragmentByTag(LOCAL_NETWORK_FRAGMENT_TAG) == null) {
//            tcWifiP2p.add(R.id.local_network_fragment_container, localNetworkFragment, LOCAL_NETWORK_FRAGMENT_TAG)
//        }
//        tcLocalNetwork.setMaxLifecycle(localNetworkFragment, Lifecycle.State.RESUMED)
//        tcLocalNetwork.commitAllowingStateLoss()

//        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.nestedScrollView) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(0, 0, 0, systemBars.bottom)
//            insets
//        }

        renderStateNewCoroutine({ it.requestShareFiles }) { requestShareFiles ->
            if (requestShareFiles.isNotEmpty()) {
                viewBinding.requestShareLayout.visibility = View.VISIBLE
                viewBinding.requestShareTv.text =
                    getString(R.string.request_share_files, requestShareFiles.size)
            } else {
                viewBinding.requestShareLayout.visibility = View.GONE
            }
        }
        viewBinding.dropRequestShareBt.clicks(this) {
            updateState { it.copy(requestShareFiles = emptyList()) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkIntentAction(intent)
    }

    fun consumeRequestShareFiles(): List<String> {
        val files = currentState().requestShareFiles
        if (files.isNotEmpty()) {
            updateState { it.copy(requestShareFiles = emptyList()) }
        }
        return files.map { it.canonicalPath }
    }

    @Suppress("DEPRECATION")
    private fun checkIntentAction(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            AndroidLog.d(TAG, "Receive ACTION_SEND uri: $uri")
            if (uri != null) {
                handleSharedUris(listOf(uri))
            }
        }
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    ?: emptyList<Uri>()
            } else {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.mapNotNull { it }
                    ?: emptyList<Uri>()
            }
            AndroidLog.d(
                TAG,
                "Receive ACTION_SEND_MULTIPLE uris: ${uris.joinToString { it.toString() }}"
            )
            if (uris.isNotEmpty()) {
                handleSharedUris(uris)
            }
        }
    }

    private fun handleSharedUris(uris: List<Uri>) {
        val files = uris.mapNotNull {
            val f = uri2FileReal(this@ConnectionActivity, it)
            if (f?.isFile == true && f.canRead() && f.length() > 0L) {
                f
            } else {
                null
            }
        }
        AndroidLog.d(TAG, "Handle shared files: $files")
        if (files.isNotEmpty()) {
            updateState {
                it.copy(requestShareFiles = files)
            }
        }
    }

    companion object {
        private const val TAG = "ConnectionActivity"


        private const val WIFI_P2P_CONNECTION_FRAGMENT_TAG = "WIFI_P2P_CONNECTION_FRAGMENT_TAG"
        private const val HOME_FRAGMENT_TAG = "WIFI_P2P_CONNECTION_FRAGMENT_TAG"
//        private const val LOCAL_NETWORK_FRAGMENT_TAG = "LOCAL_NETWORK_FRAGMENT_TAG"

        data class ConnectionActivityState(
            val requestShareFiles: List<File> = emptyList()
        )
    }

    override fun onFindBtnClicked() {
        val tcWifiP2p = supportFragmentManager.beginTransaction()
        tcWifiP2p.addToBackStack(null)
        tcWifiP2p.replace(
            R.id.wifi_p2p_fragment_container,
            wifiP2pFragment,
            WIFI_P2P_CONNECTION_FRAGMENT_TAG)

        tcWifiP2p.setMaxLifecycle(wifiP2pFragment, Lifecycle.State.RESUMED)
        tcWifiP2p.commitAllowingStateLoss()
    }
}