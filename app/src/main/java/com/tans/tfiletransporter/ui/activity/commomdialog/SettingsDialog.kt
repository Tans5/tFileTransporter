package com.tans.tfiletransporter.ui.activity.commomdialog

import android.app.Activity
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.jakewharton.rxbinding3.widget.userChanges
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.SettingsDialogBinding
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await

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
            binding.bufferSizeSb.min = Settings.minBufferSize / 1024
            binding.bufferSizeSb.max = Settings.maxBufferSize.toInt() / 1024
            val bufferSize = Settings.transferFileBufferSize().await()
            val bufferSizeInKb = bufferSize.toInt() / 1024
            binding.bufferSizeSb.progress = bufferSizeInKb
            binding.bufferSizeTv.text = context.getString(R.string.setting_buffer_size_kb, bufferSizeInKb)
        }

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

        binding.bufferSizeSb.userChanges()
            .distinctUntilChanged()
            .skip(1)
            .switchMapSingle {
                binding.bufferSizeTv.text = context.getString(R.string.setting_buffer_size_kb, it)
                Settings.updateTransferFileBufferSize(it * 1024L)
            }
            .bindLife()


    }
}