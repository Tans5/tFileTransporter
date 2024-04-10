package com.tans.tfiletransporter.ui

import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.FileItemLayoutBinding
import com.tans.tfiletransporter.databinding.FileTreeLayoutBinding
import com.tans.tfiletransporter.databinding.FolderItemLayoutBinding
import com.tans.tfiletransporter.file.FileLeaf
import com.tans.tfiletransporter.file.FileTree
import com.tans.tfiletransporter.file.fileDateText
import com.tans.tfiletransporter.file.isRootFileTree
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.ui.commomdialog.loadingDialogSuspend
import com.tans.tfiletransporter.utils.dp2px
import com.tans.tfiletransporter.utils.firstVisibleItemPosition
import com.tans.tuiutils.adapter.decoration.MarginDividerItemDecoration
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.builders.plus
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.DataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.state.CoroutineState
import com.tans.tuiutils.view.clicks
import com.tans.tuiutils.view.refreshes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.LinkedBlockingDeque


class FileTreeUI(
    private val viewBinding: FileTreeLayoutBinding,
    private val rootTreeUpdater: suspend () -> FileTree,
    private val subTreeUpdater: suspend (parentTree: FileTree, dir: FileLeaf.DirectoryFileLeaf) -> FileTree,
    private val coroutineScope: CoroutineScope,
    override val stateFlow: MutableStateFlow<FileTreeState>,
    private val recyclerViewScrollChannel: Channel<Int>,
    private val folderPositionDeque: LinkedBlockingDeque<Int>
) : CoroutineScope by coroutineScope, CoroutineState<FileTreeUI.Companion.FileTreeState> {

    private val dirDataSource: DataSourceImpl<FileLeaf.DirectoryFileLeaf> by lazy {
        DataSourceImpl(
            areDataItemsTheSameParam = { d1, d2 -> d1.path == d2.path },
            areDataItemsContentTheSameParam = { d1, d2 -> d1.path == d2.path }
        )
    }

    private val fileDataSource: DataSourceImpl<Pair<FileLeaf.CommonFileLeaf, Boolean>> by lazy {
        DataSourceImpl(
            areDataItemsTheSameParam = { d1, d2 -> d1.first.path == d2.first.path },
            areDataItemsContentTheSameParam = {d1, d2 -> d1.first.path == d2.first.path},
            getDataItemsChangePayloadParam = { d1, d2 -> if (d1.first == d2.first && d1.second != d2.second) Unit else null }
        )
    }

    init {
        val context = viewBinding.root.context as FragmentActivity
        val currentState = currentState()

        // Empty State, need to load root tree.
        if (currentState == FileTreeState()) {
            launch(Dispatchers.IO) {
                val rootTree = rootTreeUpdater()
                updateState { s ->
                    s.copy(fileTree = rootTree)
                }
            }
        }

        renderStateNewCoroutine({ it.fileTree.path }) {
            viewBinding.pathTv.text = it
        }

        val directoryAdapterBuilder = SimpleAdapterBuilderImpl<FileLeaf.DirectoryFileLeaf> (
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.folder_item_layout),
            dataSource = dirDataSource,
            dataBinder = DataBinderImpl { data, view, _ ->
                val itemViewBinding = FolderItemLayoutBinding.bind(view)
                itemViewBinding.titleTv.text = data.name
                itemViewBinding.filesCountTv.text = context.getString(R.string.file_count, data.childrenCount)
                itemViewBinding.modifiedDateTv.text = fileDateText(data.lastModified)
                itemViewBinding.root.clicks(coroutineScope = coroutineScope, clickWorkOn = Dispatchers.IO) {
                    context.supportFragmentManager.loadingDialogSuspend {
                        val i = withContext(Dispatchers.Main) {
                            viewBinding.fileFolderRv.firstVisibleItemPosition()
                        }
                        folderPositionDeque.push(i)
                        val parentTree = currentState().fileTree
                        val newTree = subTreeUpdater(parentTree, data)
                        updateState { oldState ->
                            oldState.copy(fileTree = newTree, selectedFiles = emptyList())
                        }
                    }
                }
            }
        )

        val fileAdapterBuilder = SimpleAdapterBuilderImpl<Pair<FileLeaf.CommonFileLeaf, Boolean>>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.file_item_layout),
            dataSource = fileDataSource,
            dataBinder = DataBinderImpl<Pair<FileLeaf.CommonFileLeaf, Boolean>> { data, view, _ ->
                val itemViewBinding = FileItemLayoutBinding.bind(view)
                itemViewBinding.titleTv.text = data.first.name
                itemViewBinding.modifiedDateTv.text = fileDateText(data.first.lastModified)
                itemViewBinding.filesSizeTv.text = data.first.size.toSizeString()

                itemViewBinding.root.clicks(coroutineScope) {
                    val currentFile = data.first
                    val selectedFiles = currentState().selectedFiles
                    val newSelectedFiles = if (selectedFiles.contains(currentFile)) {
                        selectedFiles - currentFile
                    } else {
                        selectedFiles + currentFile
                    }
                    updateState { it.copy(selectedFiles = newSelectedFiles) }
                }
            }.addPayloadDataBinder(Unit, ) { data, view, _ ->
                val itemViewBinding = FileItemLayoutBinding.bind(view)
                itemViewBinding.fileCb.isChecked = data.second
            }
        )


        viewBinding.fileFolderRv.adapter = (directoryAdapterBuilder + fileAdapterBuilder).build()

        renderStateNewCoroutine({ state ->
            val fileTree = state.fileTree
            val sortType = state.sortType
            val selectedFiles = state.selectedFiles
            fileTree.dirLeafs.sortDir(sortType) to fileTree.fileLeafs.sortFile(sortType).map { it to selectedFiles.contains(it) }
        }) { (dirs, files) ->
            var position = recyclerViewScrollChannel.tryReceive().getOrNull()
            val allSize = dirs.size + files.size
            fun positionFix() {
                val p = position
                position = null
                if (p != null && p < allSize) {
                    (viewBinding.fileFolderRv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(p, 0)
                }
            }
            dirDataSource.submitDataList(dirs) { positionFix() }
            fileDataSource.submitDataList(files) { positionFix() }
        }

        viewBinding.fileFolderRv.addItemDecoration(
            MarginDividerItemDecoration.Companion.Builder()
                .divider(MarginDividerItemDecoration.Companion.ColorDivider(context.getColor(R.color.line_color), context.dp2px(1)))
                .marginStart(context.dp2px(65))
                .build()
        )

        viewBinding.refreshLayout.setColorSchemeResources(R.color.teal_200)
        viewBinding.refreshLayout.refreshes(coroutineScope = coroutineScope, refreshWorkOn = Dispatchers.IO) {
            val oldState = currentState()
            val oldTree = oldState.fileTree
            val newTree = if (oldTree.isRootFileTree()) {
                rootTreeUpdater()
            } else {
                val parentTree = oldTree.parentTree
                val dirLeaf = parentTree?.dirLeafs?.find { it.path == oldTree.path }
                if (parentTree != null && dirLeaf != null) {
                    subTreeUpdater(parentTree, dirLeaf)
                } else {
                    null
                }
            }
            if (newTree != null) {
                updateState { it.copy(fileTree = newTree, selectedFiles = emptyList()) }
            } else {
                updateState { it.copy( selectedFiles = emptyList()) }
            }
        }

        val popupMenu = PopupMenu(context, viewBinding.folderMenuLayout)
        popupMenu.inflate(R.menu.folder_menu)

        popupMenu.setOnMenuItemClickListener {
            updateState { oldState ->
                val tree = oldState.fileTree
                when (it.itemId) {
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
            }
            true
        }

        viewBinding.folderMenuLayout.clicks(coroutineScope) {
            popupMenu.show()
        }
    }

    fun getSelectedFiles(): List<FileLeaf.CommonFileLeaf> = currentState().selectedFiles

    fun clearSelectedFiles() {
        updateState { it.copy(selectedFiles = emptyList()) }
    }

    fun backPress(): Boolean {
        val lastState = currentState()
        val parentTree = lastState.fileTree.parentTree
        return if (parentTree == null) {
            false
        } else {
            val p = folderPositionDeque.poll()
            if (p != null) {
                recyclerViewScrollChannel.trySend(p).isSuccess
            }
            updateState {
                lastState.copy(fileTree = parentTree)
            }
            true
        }
    }

    companion object {

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