package com.tans.tfiletransporter.ui.activity.commomdialog

import android.app.Activity
import com.jakewharton.rxbinding4.view.clicks
import com.jakewharton.rxbinding4.widget.checkedChanges
import com.jakewharton.rxbinding4.widget.userChanges
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.SettingsDialogBinding
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await

class SettingsDialog(context: Activity) : BaseCustomDialog<SettingsDialogBinding, Unit>(
    context = context,
    layoutId = R.layout.settings_dialog,
    defaultState = Unit,
) {
    override fun bindingStart(binding: SettingsDialogBinding) {
        launch {
            binding.downloadDirTv.text = Settings.getDownloadDir().await()
            binding.shareMyDirSt.isChecked = Settings.isShareMyDir().await()
            binding.maxConnectionSb.min = Settings.minConnectionSize
            binding.maxConnectionSb.max = Settings.maxConnectionSize
            val connection = Settings.transferFileMaxConnection().await()
            binding.maxConnectionSb.progress = connection
            binding.maxConnectionTv.text = connection.toString()
        }

        binding.downloadDirEditIv.clicks()
            .doOnNext {
                // TODO:
            }
            .bindLife()

        binding.downloadDirResetIv.clicks()
            .doOnNext {
                // TODO:
            }
            .bindLife()

        binding.shareMyDirSt.checkedChanges()
            .skipInitialValue()
            .switchMapSingle {
                Settings.updateShareDir(it)
            }
            .bindLife()

        binding.maxConnectionSb.userChanges()
            .distinctUntilChanged()
            .skip(1)
            .switchMapSingle {
                binding.maxConnectionTv.text = it.toString()
                Settings.updateTransferFileMaxConnection(it)
            }
            .bindLife()

    }
}