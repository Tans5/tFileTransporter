package com.tans.tfiletransporter.ui.folderselect

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.FolderSelectActivityBinding
import com.tans.tfiletransporter.file.createLocalRootTree
import com.tans.tfiletransporter.file.isRootFileTree
import com.tans.tfiletransporter.file.newLocalSubTree
import com.tans.tfiletransporter.ui.FileTreeUI
import com.tans.tfiletransporter.ui.commomdialog.showNoOptionalDialogSuspend
import com.tans.tfiletransporter.ui.commomdialog.showTextInputDialogSuspend
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.LinkedBlockingDeque

@SystemBarStyle(statusBarThemeStyle = 1, navigationBarThemeStyle = 1)
class FolderSelectActivity : BaseCoroutineStateActivity<Unit>(
    defaultState = Unit
) {

    private var fileTreeUI: FileTreeUI? = null

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        onBackPressedDispatcher.addCallback {
            fileTreeUI?.backPress()
        }
    }

    private val fileTreeStateFlow by lazyViewModelField("fileTreeStateFlow") {
        MutableStateFlow(FileTreeUI.Companion.FileTreeState())
    }

    private val fileTreeRecyclerViewScrollChannel: Channel<Int> by lazyViewModelField("fileTreeRecyclerViewScrollChannel") {
        Channel<Int>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    private val fileTreeFolderPositionDeque: LinkedBlockingDeque<Int> by lazyViewModelField("fileTreeFolderPositionDeque") {
        LinkedBlockingDeque()
    }

    override val layoutId: Int = R.layout.folder_select_activity

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {  }
    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = FolderSelectActivityBinding.bind(contentView)
        val fileTreeUI = FileTreeUI(
            viewBinding = viewBinding.fileTreeLayout,
            rootTreeUpdater = {
                withContext(Dispatchers.IO) {
                    createLocalRootTree(this@FolderSelectActivity).copy(fileLeafs = emptyList())
                }
            },
            subTreeUpdater = { parentTree, dir ->
                withContext(Dispatchers.IO) {
                    parentTree.newLocalSubTree(dir).copy(fileLeafs = emptyList())
                }
            },
            coroutineScope = this,
            stateFlow = fileTreeStateFlow,
            recyclerViewScrollChannel = fileTreeRecyclerViewScrollChannel,
            folderPositionDeque = fileTreeFolderPositionDeque
        )
        this@FolderSelectActivity.fileTreeUI = fileTreeUI

        launch {
            fileTreeUI.stateFlow()
                .map { it.fileTree }
                .collect {
                    onBackPressedCallback.isEnabled = !it.isRootFileTree()
                }
        }

        viewBinding.toolBar.menu.findItem(R.id.create_new_folder).setOnMenuItemClickListener {
            launch {
                val inputResult = this@FolderSelectActivity.supportFragmentManager.showTextInputDialogSuspend(getString(R.string.folder_select_create_new_folder_hint))
                if (inputResult != null) {
                    val tree = fileTreeUI.currentState().fileTree
                    if (tree.isRootFileTree() || !Settings.isDirWriteable(tree.path)) {
                        this@FolderSelectActivity.supportFragmentManager.showNoOptionalDialogSuspend(
                            title = getString(R.string.folder_select_create_folder_error),
                            message = getString(R.string.folder_select_error_body, "Can't create new folder.")
                        )
                    } else {
                        val createFolderResult = withContext(Dispatchers.IO) {
                            try {
                                val f = File(tree.path, inputResult)
                                f.mkdirs()
                            } catch (e: Throwable) {
                                e.printStackTrace()
                                false
                            }
                        }
                        if (createFolderResult) {
                            fileTreeUI.updateState { oldState ->
                                val oldTree = oldState.fileTree
                                val parentTree = oldTree.parentTree
                                val dirLeaf = parentTree?.dirLeafs?.find { it.path == oldTree.path }
                                if (parentTree != null && dirLeaf != null) {
                                    oldState.copy(
                                        fileTree = parentTree.newLocalSubTree(dirLeaf).copy(fileLeafs = emptyList())
                                    )
                                } else {
                                    oldState
                                }
                            }
                        }
                    }
                }
            }
            true
        }

        viewBinding.doneActionBt.clicks(this) {
            val tree = fileTreeUI.currentState().fileTree
            if (tree.isRootFileTree()) {
                this@FolderSelectActivity.supportFragmentManager.showNoOptionalDialogSuspend(
                    title = getString(R.string.folder_select_error_title),
                    message = getString(R.string.folder_select_error_body, "Root folder can't be selected.")
                )
                Unit
            } else {
                if (withContext(Dispatchers.IO) { Settings.isDirWriteable(tree.path) }) {
                    val i = Intent()
                    i.putExtra(FOLDER_SELECT_KEY, tree.path)
                    this@FolderSelectActivity.setResult(Activity.RESULT_OK, i)
                    finish()
                } else {
                    this@FolderSelectActivity.supportFragmentManager.showNoOptionalDialogSuspend(
                        title = getString(R.string.folder_select_error_title),
                        message = getString(R.string.folder_select_error_body, "Can't write in ${tree.path}")
                    )
                    Unit
                }
            }
        }
    }

    companion object {

        private const val FOLDER_SELECT_KEY = "folder_select_key"

        fun getResult(i: Intent): String? {
            return i.getStringExtra(FOLDER_SELECT_KEY)
        }
    }
}