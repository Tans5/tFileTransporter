package com.tans.tfiletransporter.ui.commomdialog

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.fragment.app.FragmentManager
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.SettingsDialogBinding
import com.tans.tfiletransporter.ui.folderselect.FolderSelectActivity
import com.tans.tuiutils.actresult.startActivityResultSuspend
import com.tans.tuiutils.dialog.BaseCoroutineStateDialogFragment
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsDialog : BaseCoroutineStateDialogFragment<Settings.SettingsData>(
    defaultState = Settings.currentState(),
) {

    override fun firstLaunchInitData() {
        launch {
            Settings.stateFlow()
                .collect { newState -> updateState { newState } }
        }
    }

    override fun bindContentView(view: View) {
        val viewBinding = SettingsDialogBinding.bind(view)
        viewBinding.maxConnectionSb.min = Settings.MIN_CONNECTION_SIZE
        viewBinding.maxConnectionSb.max = Settings.MAX_CONNECTION_SIZE

        renderStateNewCoroutine({ it.downloadDir }) { downloadDir ->
            viewBinding.downloadDirTv.text = downloadDir
        }

        renderStateNewCoroutine({ it.shareMyDir }) { shareMyDir ->
            viewBinding.shareMyDirSt.isChecked = shareMyDir
        }

        renderStateNewCoroutine({ it.transferFileMaxConnection }) { maxConnection ->
            viewBinding.maxConnectionSb.progress = maxConnection
            viewBinding.maxConnectionTv.text = maxConnection.toString()
        }

        viewBinding.downloadDirEditIv.clicks(this) {
            val (_, resultData) = this@SettingsDialog.startActivityResultSuspend(Intent(this.context, FolderSelectActivity::class.java))
            if (resultData != null) {
                val selectedFolder = FolderSelectActivity.getResult(resultData)
                if (!selectedFolder.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        Settings.updateDownloadDir(selectedFolder)
                    }
                }
            }
        }

        viewBinding.downloadDirResetIv.clicks(coroutineScope = this, clickWorkOn = Dispatchers.IO) {
            Settings.updateDownloadDir(Settings.defaultDownloadDir)
        }

        viewBinding.shareMyDirSt.setOnCheckedChangeListener { _, isChecked ->
            Settings.updateShareDir(isChecked)
        }
        viewBinding.maxConnectionSb.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    Settings.updateTransferFileMaxConnection(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
    }

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.settings_dialog, parent, false)
    }
}

fun FragmentManager.showSettingsDialog() {
    val d = SettingsDialog()
    d.show(this, "SettingsDialog#${System.currentTimeMillis()}")
}