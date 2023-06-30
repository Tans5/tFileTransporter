package com.tans.tfiletransporter.ui.activity.folderselect

import com.jakewharton.rxbinding4.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.FolderSelectActivityBinding
import com.tans.tfiletransporter.ui.activity.BaseActivity
import io.reactivex.rxjava3.core.Single

class FolderSelectActivity : BaseActivity<FolderSelectActivityBinding, FolderSelectActivity.Companion.FolderSelectState>(
    layoutId = R.layout.folder_select_activity,
    defaultState = FolderSelectState()
) {

    override fun firstLaunchInitData() {

    }

    override fun initViews(binding: FolderSelectActivityBinding) {

        binding.toolBar.menu.findItem(R.id.create_new_folder).clicks()
            .flatMapSingle {
                // TODO: create new folder.
                Single.just(Unit)
            }
            .bindLife()
    }

    companion object {
        data class FolderSelectState(
            val u: Unit = Unit
        )
    }
}