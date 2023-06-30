package com.tans.tfiletransporter.ui.activity.folderselect

import android.app.Activity
import android.content.Intent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding4.swiperefreshlayout.refreshes
import com.jakewharton.rxbinding4.view.clicks
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.recyclerviewutils.MarginDividerItemDecoration
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.FolderItemLayoutBinding
import com.tans.tfiletransporter.databinding.FolderSelectActivityBinding
import com.tans.tfiletransporter.file.FileLeaf
import com.tans.tfiletransporter.file.FileTree
import com.tans.tfiletransporter.file.createLocalRootTree
import com.tans.tfiletransporter.file.isRootFileTree
import com.tans.tfiletransporter.file.newLocalSubTree
import com.tans.tfiletransporter.ui.DataBindingAdapter
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tans.tfiletransporter.ui.activity.commomdialog.TextInputDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showNoOptionalDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showTextInputDialog
import com.tans.tfiletransporter.utils.dp2px
import com.tans.tfiletransporter.utils.firstVisibleItemPosition
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.withLatestFrom
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque
import java.util.Deque

class FolderSelectActivity : BaseActivity<FolderSelectActivityBinding, FolderSelectActivity.Companion.FolderSelectState>(
    layoutId = R.layout.folder_select_activity,
    defaultState = FolderSelectState()
) {

    private val recyclerViewScrollChannel = Channel<Int>(1)

    private val folderPositionDeque: Deque<Int> = ArrayDeque()

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        onBackPressedDispatcher.addCallback {
            launch {
                updateState { state ->
                    val i = folderPositionDeque.poll()
                    if (i != null) {
                        recyclerViewScrollChannel.trySend(i).isSuccess
                    }
                    if (state.fileTree.parentTree == null) state else FolderSelectState(
                        state.fileTree.parentTree
                    )
                }.await()
            }
        }
    }

    override fun firstLaunchInitData() {

        launch(Dispatchers.IO) {
            updateState {
                it.copy(fileTree = createLocalRootTree(this@FolderSelectActivity))
            }.await()
        }
    }

    override fun initViews(binding: FolderSelectActivityBinding) {

        render({ it.fileTree.path }) {
            binding.pathTv.text = it
        }.bindLife()

        bindState()
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
                        val tree = bindState().firstOrError().await().fileTree
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
                                updateState { oldState ->
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

        binding.folderRv.adapter = SimpleAdapterSpec<FileLeaf.DirectoryFileLeaf, FolderItemLayoutBinding>(
            layoutId = R.layout.folder_item_layout,
            bindData = { _, data, lBinding ->
                lBinding.titleTv.text = data.name
                DataBindingAdapter.dateText(lBinding.modifiedDateTv, data.lastModified)
                lBinding.filesCountTv.visibility = View.INVISIBLE
            },
            dataUpdater = bindState().map { it.fileTree.dirLeafs },
            differHandler = DifferHandler(
                itemsTheSame = { a, b -> a.path == b.path },
                contentTheSame = { a, b -> a == b }
            ),
            itemClicks = listOf { lBinding, _ ->
                lBinding.root to { _, data ->
                    rxSingle(Dispatchers.IO) {
                        val i = withContext(Dispatchers.Main) {
                            binding.folderRv.firstVisibleItemPosition()
                        }
                        folderPositionDeque.push(i)
                        updateState { oldState ->
                            oldState.copy(fileTree = oldState.fileTree.newLocalSubTree(data))
                        }.await()
                        Unit
                    }
                        .observeOn(AndroidSchedulers.mainThread())
                        .loadingDialog(this)
                }
            }).toAdapter { list ->
            val position = recyclerViewScrollChannel.tryReceive().getOrNull()
            if (position != null && position < list.size) {
                (binding.folderRv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                    position,
                    0
                )
            }
        }

        binding.folderRv.addItemDecoration(
            MarginDividerItemDecoration.Companion.Builder()
            .divider(MarginDividerItemDecoration.Companion.ColorDivider(getColor(R.color.line_color), dp2px(1)))
            .marginStart(dp2px(65))
            .build()
        )

        binding.refreshLayout.setColorSchemeResources(R.color.teal_200)
        binding.refreshLayout.refreshes()
            .observeOn(Schedulers.io())
            .flatMapSingle {
                updateState { oldState ->
                    val oldTree = oldState.fileTree
                    if (oldTree.isRootFileTree()) {
                        oldState.copy(fileTree = createLocalRootTree(this))
                    } else {
                        val parentTree = oldTree.parentTree
                        val dirLeaf = parentTree?.dirLeafs?.find { it.path == oldTree.path }
                        if (parentTree != null && dirLeaf != null) {
                            oldState.copy(
                                fileTree = parentTree.newLocalSubTree(dirLeaf)
                            )
                        } else {
                            oldState
                        }
                    }
                }.observeOn(AndroidSchedulers.mainThread())
                    .doFinally {
                        binding.refreshLayout.isRefreshing = false
                    }
            }
            .bindLife()

        binding.doneActionBt.clicks()
            .withLatestFrom(bindState().map { it.fileTree })
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

    companion object {

        private const val FOLDER_SELECT_KEY = "folder_select_key"

        data class FolderSelectState(
            val fileTree: FileTree = FileTree(
                dirLeafs = emptyList(),
                fileLeafs = emptyList(),
                path = File.separator,
                parentTree = null
            )
        )

        fun getResult(i: Intent): String? {
            return i.getStringExtra(FOLDER_SELECT_KEY)
        }
    }
}