package com.tans.tfiletransporter.ui.activity.connection

import android.Manifest
import android.content.Intent
import android.net.*
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ConnectionActivityBinding
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

class ConnectionActivity : BaseActivity<ConnectionActivityBinding, Unit>(
    layoutId = R.layout.connection_activity,
    defaultState = Unit
) {
    override fun firstLaunchInitData() {
        launch {
            val permissionNeed = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionNeed.add(Manifest.permission.READ_MEDIA_IMAGES)
                    permissionNeed.add(Manifest.permission.READ_MEDIA_AUDIO)
                    permissionNeed.add(Manifest.permission.READ_MEDIA_VIDEO)
                } else {
                    permissionNeed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            } else {
                permissionNeed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissionNeed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionNeed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                permissionNeed.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissionNeed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            RxPermissions(this@ConnectionActivity)
                .request(*permissionNeed.toTypedArray())
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