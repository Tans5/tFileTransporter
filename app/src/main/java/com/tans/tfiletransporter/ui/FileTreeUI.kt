package com.tans.tfiletransporter.ui

import android.app.Activity
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding4.appcompat.itemClicks
import com.jakewharton.rxbinding4.swiperefreshlayout.refreshes
import com.jakewharton.rxbinding4.view.clicks
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.recyclerviewutils.MarginDividerItemDecoration
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.plus
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.core.BindLife
import com.tans.tfiletransporter.core.Stateable
import com.tans.tfiletransporter.databinding.FileItemLayoutBinding
import com.tans.tfiletransporter.databinding.FileTreeLayoutBinding
import com.tans.tfiletransporter.databinding.FolderItemLayoutBinding
import com.tans.tfiletransporter.file.FileLeaf
import com.tans.tfiletransporter.file.FileTree
import com.tans.tfiletransporter.file.isRootFileTree
import com.tans.tfiletransporter.ui.commomdialog.loadingDialogSuspend
import com.tans.tfiletransporter.utils.dp2px
import com.tans.tfiletransporter.utils.firstVisibleItemPosition
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.withLatestFrom
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Deque
import java.util.concurrent.LinkedBlockingDeque

class FileTreeUI(
    private val binding: FileTreeLayoutBinding,
    private val rootTreeUpdater: () -> Single<FileTree>,
    private val subTreeUpdater: (parentTree: FileTree, dir: FileLeaf.DirectoryFileLeaf) -> Single<FileTree>
) : Stateable<FileTreeUI.Companion.FileTreeState>, BindLife, CoroutineScope by CoroutineScope(Dispatchers.Main) {

    private val recyclerViewScrollChannel = Channel<Int>(1)
    private val folderPositionDeque: Deque<Int> = LinkedBlockingDeque()

    override val lifeCompositeDisposable: CompositeDisposable = CompositeDisposable()
    override val stateStore: Subject<FileTreeState> = BehaviorSubject.createDefault(FileTreeState())

    fun start() {
        val context = binding.root.context as Activity
        launch(Dispatchers.IO) {
            val rootTree = rootTreeUpdater().await()
            updateState { it.copy(fileTree = rootTree) }.await()
        }

        render({ it.fileTree }) {
            binding.pathTv.text = it.path
        }.bindLife()

        binding.fileFolderRv.adapter =
            (SimpleAdapterSpec<FileLeaf.DirectoryFileLeaf, FolderItemLayoutBinding>(
                layoutId = R.layout.folder_item_layout,
                bindData = { _, data, binding ->
                    binding.titleTv.text = data.name
                    DataBindingAdapter.dateText(binding.modifiedDateTv, data.lastModified)
                    binding.filesCountTv.text = context.getString(R.string.file_count, data.childrenCount)
                },
                dataUpdater = bindState().map { it.fileTree.dirLeafs.sortDir(it.sortType) },
                differHandler = DifferHandler(
                    itemsTheSame = { a, b -> a.path == b.path },
                    contentTheSame = { a, b -> a == b }
                ),
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, data ->
                        rxSingle(Dispatchers.IO) {
                            (binding.root.context as? FragmentActivity)?.let {
                                it.supportFragmentManager.loadingDialogSuspend {
                                    val i = withContext(Dispatchers.Main) {
                                        this@FileTreeUI.binding.fileFolderRv.firstVisibleItemPosition()
                                    }
                                    folderPositionDeque.push(i)
                                    val parentTree = bindState().firstOrError().await().fileTree
                                    val newTree = subTreeUpdater(parentTree, data).await()
                                    updateState { oldState ->
                                        oldState.copy(fileTree = newTree, selectedFiles = emptyList())
                                    }.await()
                                }
                            }
                            Unit
                        }
                    }
                }
            ) + SimpleAdapterSpec<Pair<FileLeaf.CommonFileLeaf, Boolean>, FileItemLayoutBinding>(
                layoutId = R.layout.file_item_layout,
                bindData = { _, data, binding ->
                    binding.titleTv.text = data.first.name
                    DataBindingAdapter.dateText(binding.modifiedDateTv, data.first.lastModified)
                    DataBindingAdapter.fileSizeText(binding.filesSizeTv, data.first.size)
                    binding.fileCb.isChecked = data.second
                },
                dataUpdater = bindState().map { state ->
                    state.fileTree.fileLeafs.sortFile(state.sortType)
                        .map { it to state.selectedFiles.contains(it) }
                },
                differHandler = DifferHandler(
                    itemsTheSame = { a, b -> a.first.path == b.first.path },
                    contentTheSame = { a, b -> a == b },
                    changePayLoad = { d1, d2 ->
                        if (d1.first == d2.first && d1.second != d2.second) {
                            FileSelectChange
                        } else {
                            null
                        }
                    }
                ),
                bindDataPayload = { _, data, binding, payloads ->
                    if (payloads.contains(FileSelectChange)) {
                        binding.fileCb.isChecked = data.second
                        true
                    } else {
                        false
                    }
                },
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, (file, isSelect) ->
                        updateState { oldState ->
                            val selectedFiles = oldState.selectedFiles
                            oldState.copy(selectedFiles = if (isSelect) selectedFiles - file else selectedFiles + file)
                        }.map { }
                    }
                }
            )).toAdapter { list ->
                val position = recyclerViewScrollChannel.tryReceive().getOrNull()
                if (position != null && position < list.size) {
                    (binding.fileFolderRv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(
                        position,
                        0
                    )
                }
            }

        binding.fileFolderRv.addItemDecoration(
            MarginDividerItemDecoration.Companion.Builder()
            .divider(
                MarginDividerItemDecoration.Companion.ColorDivider(context.getColor(R.color.line_color),
                context.dp2px(1)))
            .marginStart(context.dp2px(65))
            .build()
        )

        binding.refreshLayout.setColorSchemeResources(R.color.teal_200)
        binding.refreshLayout.refreshes()
            .observeOn(Schedulers.io())
            .flatMapSingle {
                rxSingle {
                    val oldState = bindState().firstOrError().await()
                    val oldTree = oldState.fileTree
                    val newState = if (oldTree.isRootFileTree()) {
                        oldState.copy(fileTree = rootTreeUpdater().await())
                    } else {
                        val parentTree = oldTree.parentTree
                        val dirLeaf = parentTree?.dirLeafs?.find { it.path == oldTree.path }
                        if (parentTree != null && dirLeaf != null) {
                            oldState.copy(
                                fileTree = subTreeUpdater(parentTree, dirLeaf).await(),
                                selectedFiles = emptyList()
                            )
                        } else {
                            oldState.copy(selectedFiles = emptyList())
                        }
                    }
                    updateState { newState }.await()
                }.observeOn(AndroidSchedulers.mainThread())
                    .doFinally {
                        binding.refreshLayout.isRefreshing = false
                    }
            }
            .bindLife()

        val popupMenu = PopupMenu(context, binding.folderMenuLayout)
        popupMenu.inflate(R.menu.folder_menu)

        popupMenu.itemClicks()
            .withLatestFrom(bindState())
            .flatMapSingle { (menuItem, state) ->
                rxSingle {
                    withContext(Dispatchers.IO) {
                        updateState { oldState ->
                            val tree = oldState.fileTree
                            when (menuItem.itemId) {
                                R.id.select_all_files -> {
                                    oldState.copy(fileTree = tree, selectedFiles = tree.fileLeafs)
                                }

                                R.id.unselect_all_files -> {
                                    oldState.copy(fileTree = tree, selectedFiles = emptyList())
                                }

                                R.id.sort_by_date -> {
                                    if (oldState.sortType != FileSortType.SortByDate) {
                                        recyclerViewScrollChannel.trySend(0).isSuccess
                                        oldState.copy(
                                            sortType = FileSortType.SortByDate
                                        )
                                    } else {
                                        oldState
                                    }
                                }

                                R.id.sort_by_name -> {
                                    if (oldState.sortType != FileSortType.SortByName) {
                                        recyclerViewScrollChannel.trySend(0).isSuccess
                                        oldState.copy(sortType = FileSortType.SortByName)
                                    } else {
                                        oldState
                                    }
                                }

                                else -> {
                                    oldState
                                }
                            }

                        }.await()
                    }
                }
            }
            .bindLife()

        binding.folderMenuLayout.clicks()
            .doOnNext {
                popupMenu.show()
            }
            .bindLife()
    }

    fun stop() {
        cancel()
        lifeCompositeDisposable.clear()
    }

    fun getSelectedFiles(): Single<List<FileLeaf.CommonFileLeaf>> = bindState().firstOrError().map { it.selectedFiles }

    fun clearSelectedFiles(): Completable = updateStateCompletable { it.copy(selectedFiles = emptyList()) }

    fun backPress(): Single<Boolean> = rxSingle {
        val lastState = bindState().firstOrError().await()
        val parentTree = lastState.fileTree.parentTree
        if (parentTree == null) {
            false
        } else {
            val p = folderPositionDeque.poll()
            if (p != null) {
                recyclerViewScrollChannel.trySend(p).isSuccess
            }
            updateState {
                lastState.copy(fileTree = parentTree)
            }.await()
            true
        }
    }

    companion object {

        object FileSelectChange

        enum class FileSortType {
            SortByDate,
            SortByName
        }

        data class FileTreeState(
            val fileTree: FileTree = FileTree(
                dirLeafs = emptyList(),
                fileLeafs = emptyList(),
                path = File.separator,
                parentTree = null
            ),
            val selectedFiles: List<FileLeaf.CommonFileLeaf> = emptyList(),
            val sortType: FileSortType = FileSortType.SortByName
        )

        private fun List<FileLeaf.CommonFileLeaf>.sortFile(sortType: FileSortType): List<FileLeaf.CommonFileLeaf> = when (sortType) {
            FileSortType.SortByDate -> {
                sortedByDescending { it.lastModified }
            }
            FileSortType.SortByName -> {
                sortedBy { it.name }
            }
        }

        private fun List<FileLeaf.DirectoryFileLeaf>.sortDir(sortType: FileSortType): List<FileLeaf.DirectoryFileLeaf> = when (sortType) {
            FileSortType.SortByDate -> {
                sortedByDescending { it.lastModified }
            }
            FileSortType.SortByName -> {
                sortedBy { it.name }
            }
        }
    }
}