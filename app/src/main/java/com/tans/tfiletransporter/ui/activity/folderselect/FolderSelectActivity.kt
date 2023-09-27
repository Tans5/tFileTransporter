package com.tans.tfiletransporter.ui.activity.folderselect

import android.app.Activity
import android.content.Intent
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import com.jakewharton.rxbinding4.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.FolderSelectActivityBinding
import com.tans.tfiletransporter.file.createLocalRootTree
import com.tans.tfiletransporter.file.isRootFileTree
import com.tans.tfiletransporter.file.newLocalSubTree
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tans.tfiletransporter.ui.activity.FileTreeUI
import com.tans.tfiletransporter.ui.activity.commomdialog.TextInputDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showNoOptionalDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showTextInputDialog
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.withLatestFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.coroutines.withContext
import java.io.File

class FolderSelectActivity : BaseActivity<FolderSelectActivityBinding, Unit>(
    layoutId = R.layout.folder_select_activity,
    defaultState = Unit
) {

    private var fileTreeUI: FileTreeUI? = null

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        onBackPressedDispatcher.addCallback {
            launch {
                fileTreeUI?.backPress()?.await()
            }
        }
    }

    override fun initViews(binding: FolderSelectActivityBinding) {

        val fileTreeUI = FileTreeUI(
            binding = binding.fileTreeLayout,
            rootTreeUpdater = {
                Single.fromCallable {
                    createLocalRootTree(this@FolderSelectActivity).copy(fileLeafs = emptyList())
                }
            },
            subTreeUpdater = { parentTree, dir ->
                Single.fromCallable {
                    parentTree.newLocalSubTree(dir).copy(fileLeafs = emptyList())
                }
            }
        )
        this.fileTreeUI = fileTreeUI
        fileTreeUI.start()

        fileTreeUI.bindState()
            .distinctUntilChanged()
            .doOnNext {
                onBackPressedCallback.isEnabled = !it.fileTree.isRootFileTree()
            }
            .bindLife()

        binding.toolBar.menu.findItem(R.id.create_new_folder).clicks()
            .flatMapSingle {
                rxSingle(Dispatchers.Main) {
                    val inputResult = this@FolderSelectActivity.showTextInputDialog(getString(R.string.folder_select_create_new_folder_hint))
                    if (inputResult is TextInputDialog.Companion.Result.Success) {
                        val tree = fileTreeUI.bindState().firstOrError().await().fileTree
                        if (tree.isRootFileTree() || !Settings.isDirWriteable(tree.path)) {
                            this@FolderSelectActivity.showNoOptionalDialog(
                                title = getString(R.string.folder_select_create_folder_error),
                                message = getString(R.string.folder_select_error_body, "Can't create new folder.")
                            ).await()
                        } else {
                            val createFolderResult = withContext(Dispatchers.IO) {
                                try {
                                    val f = File(tree.path, inputResult.text)
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
                                            fileTree = parentTree.newLocalSubTree(dirLeaf)
                                        )
                                    } else {
                                        oldState
                                    }
                                }.await()
                            }
                        }
                    }
                }
            }
            .bindLife()

        binding.doneActionBt.clicks()
            .withLatestFrom(fileTreeUI.bindState().map { it.fileTree })
            .flatMapSingle { (_, tree) ->
                rxSingle(Dispatchers.Main) {
                    if (tree.isRootFileTree()) {
                        this@FolderSelectActivity.showNoOptionalDialog(
                            title = getString(R.string.folder_select_error_title),
                            message = getString(R.string.folder_select_error_body, "Root folder can't be selected.")
                        ).await()
                    } else {
                        if (withContext(Dispatchers.IO) { Settings.isDirWriteable(tree.path) }) {
                            val i = Intent()
                            i.putExtra(FOLDER_SELECT_KEY, tree.path)
                            this@FolderSelectActivity.setResult(Activity.RESULT_OK, i)
                            finish()
                        } else {
                            this@FolderSelectActivity.showNoOptionalDialog(
                                title = getString(R.string.folder_select_error_title),
                                message = getString(R.string.folder_select_error_body, "Can't write in ${tree.path}")
                            ).await()
                        }
                    }
                }
            }
            .bindLife()
    }

    override fun onDestroy() {
        super.onDestroy()
        fileTreeUI?.stop()
    }

    companion object {

        private const val FOLDER_SELECT_KEY = "folder_select_key"

        fun getResult(i: Intent): String? {
            return i.getStringExtra(FOLDER_SELECT_KEY)
        }
    }
}