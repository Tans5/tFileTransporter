package com.tans.tfiletransporter.ui.activity.connection

import android.Manifest
import android.content.Intent
import android.net.*
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ConnectionActivityBinding
import com.tans.tfiletransporter.net.LOCAL_DEVICE
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tans.tfiletransporter.utils.toBytes
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import org.kodein.di.instance
import java.net.InetAddress
import java.util.*

class ConnectionActivity : BaseActivity<ConnectionActivityBinding, Unit>(
    layoutId = R.layout.connection_activity,
    defaultState = Unit
) {
    override fun firstLaunchInitData() {
        launch {
            val grantStorage = RxPermissions(this@ConnectionActivity).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.request(Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
                    } else {
                        it.request(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                } else {
                    it.request(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.firstOrError().await()
            if (!grantStorage) {
                finish()
            }
            RxPermissions(this@ConnectionActivity)
                .let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.request(Manifest.permission.NEARBY_WIFI_DEVICES)
                    } else {
                        it.request(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }
                .firstOrError()
                .await()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                i.data = Uri.fromParts("package", packageName, null)
                startActivity(i)
            }
        }

    }

}