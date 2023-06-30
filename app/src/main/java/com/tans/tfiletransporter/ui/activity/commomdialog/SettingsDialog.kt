package com.tans.tfiletransporter.ui.activity.commomdialog

import android.annotation.SuppressLint
import android.app.Activity
import com.jakewharton.rxbinding4.view.clicks
import com.jakewharton.rxbinding4.widget.checkedChanges
import com.jakewharton.rxbinding4.widget.userChanges
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.SettingsDialogBinding
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

class SettingsDialog(context: Activity) : BaseCustomDialog<SettingsDialogBinding, Unit>(
    context = context,
    layoutId = R.layout.settings_dialog,
    defaultState = Unit,
) {
    @SuppressLint("ClickableViewAccessibility")
    override fun bindingStart(binding: SettingsDialogBinding) {
        // To solve seekbar in dialog ui problem.
        binding.root.setOnTouchListener { _, _ -> true }
        binding.maxConnectionSb.min = Settings.minConnectionSize
        binding.maxConnectionSb.max = Settings.maxConnectionSize

        fun <T : Any> observeSingleFromUI(map: (Settings.SettingsData) -> T): Observable<T> {
            return Settings.bindState()
                .map(map)
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
        }

        observeSingleFromUI { it.downloadDir }
            .doOnNext { binding.downloadDirTv.text = it }
            .bindLife()

        observeSingleFromUI { it.shareMyDir }
            .doOnNext { binding.shareMyDirSt.isChecked = it }
            .bindLife()

        observeSingleFromUI { it.transferFileMaxConnection }
            .doOnNext {
                binding.maxConnectionSb.progress = it
                binding.maxConnectionTv.text = it.toString()
            }
            .bindLife()

        binding.downloadDirEditIv.clicks()
            .doOnNext {
                // TODO:
            }
            .bindLife()

        binding.downloadDirResetIv.clicks()
            .flatMapSingle {
                Settings.updateDownloadDir(Settings.defaultDownloadDir)
                    .onErrorResumeNext { Single.just(Unit) }
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