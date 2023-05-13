package com.tans.tfiletransporter.ui.activity.filetransport

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.recyclerviewutils.MarginDividerItemDecoration
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.plus
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.FileItemLayoutBinding
import com.tans.tfiletransporter.databinding.FolderItemLayoutBinding
import com.tans.tfiletransporter.databinding.MyDirFragmentBinding
import com.tans.tfiletransporter.file.*
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.utils.dp2px
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import org.kodein.di.instance
import java.util.*
import androidx.activity.addCallback
import com.jakewharton.rxbinding4.appcompat.itemClicks
import com.jakewharton.rxbinding4.swiperefreshlayout.refreshes
import com.jakewharton.rxbinding4.view.clicks
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.ui.DataBindingAdapter
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import com.tans.tfiletransporter.utils.firstVisibleItemPosition
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.withLatestFrom
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import java.io.File
import kotlin.math.min

class MyDirFragment : BaseFragment<MyDirFragmentBinding, MyDirFragment.Companion.MyDirFragmentState>(R.layout.my_dir_fragment, MyDirFragmentState()) {

    private val rootDir: File by lazy {
        (requireActivity() as FileTransportActivity).rootDirFile
    }

    private val recyclerViewScrollChannel = Channel<Int>(1)

    private val folderPositionDeque: Deque<Int> = ArrayDeque()

    private val onBackPressedDispatcher: OnBackPressedDispatcher by instance()

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        onBackPressedDispatcher.addCallback {
            launch {
                updateState { state ->
                    val i = folderPositionDeque.poll()
                    if (i != null) {
                        recyclerViewScrollChannel.trySend(i).isSuccess
                    }
                    if (state.fileTree.parentTree == null) state else MyDirFragmentState(state.fileTree.parentTree, emptySet())
                }.await()
            }
        }
    }

    private val fileExplore: FileExplore by instance()

    @Suppress("NAME_SHADOWING")
    override fun initViews(binding: MyDirFragmentBinding) {
        launch(Dispatchers.IO) {
            updateState {
                it.copy(fileTree = createLocalRootTree(rootDir))
            }.await()
        }

        bindState()
            .map { it.fileTree }
            .distinctUntilChanged()
            .withLatestFrom((requireActivity() as FileTransportActivity).bindState().map { it.selectedTabType })
            .doOnNext { (tree, tab) ->
                onBackPressedCallback.isEnabled = !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.MyDir
            }
            .bindLife()

        (requireActivity() as FileTransportActivity).bindState()
            .map { it.selectedTabType }
            .withLatestFrom(bindState().map { it.fileTree })
            .distinctUntilChanged()
            .doOnNext { (tab, tree) ->
                onBackPressedCallback.isEnabled = !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.MyDir
            }
            .bindLife()

        render({ it.fileTree }) {
            binding.pathTv.text = it.path
        }.bindLife()

        binding.fileFolderRv.adapter =
            (SimpleAdapterSpec<FileLeaf.DirectoryFileLeaf, FolderItemLayoutBinding>(
                layoutId = R.layout.folder_item_layout,
                bindData = { _, data, binding ->
                    binding.titleTv.text = data.name
                    DataBindingAdapter.dateText(binding.modifiedDateTv, data.lastModified)
                    binding.filesCountTv.text = getString(R.string.file_count, data.childrenCount)
                },
                dataUpdater = bindState().map { it.fileTree.dirLeafs.sortDir(it.sortType) },
                differHandler = DifferHandler(
                    itemsTheSame = { a, b -> a.path == b.path },
                    contentTheSame = { a, b -> a == b }
                ),
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, data ->
                        rxSingle(Dispatchers.IO) {
                            val i = withContext(Dispatchers.Main) {
                                this@MyDirFragment.binding.fileFolderRv.firstVisibleItemPosition()
                            }
                            folderPositionDeque.push(i)
                            updateState { oldState ->
                                oldState.copy(
                                    fileTree = oldState.fileTree.newLocalSubTree(data, rootDir),
                                    selectedFiles = emptySet()
                                )
                            }.await()
                            Unit
                        }
                            .observeOn(AndroidSchedulers.mainThread())
                            .loadingDialog(requireActivity())
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

        binding.fileFolderRv.addItemDecoration(MarginDividerItemDecoration.Companion.Builder()
                .divider(MarginDividerItemDecoration.Companion.ColorDivider(requireContext().getColor(R.color.line_color),
                        requireContext().dp2px(1)))
                .marginStart(requireContext().dp2px(65))
                .build()
        )

        (requireActivity() as FileTransportActivity).observeFloatBtnClick()
            .flatMapSingle {
                (activity as FileTransportActivity).bindState().map { it.selectedTabType }
                    .firstOrError()
            }
            .withLatestFrom(bindState().map { it.selectedFiles })
            .filter { it.first == FileTransportActivity.Companion.DirTabType.MyDir && it.second.isNotEmpty() }
            .flatMapSingle { (_, selectedFiles) ->
                rxSingle(Dispatchers.IO) {
                    val exploreFiles = selectedFiles.toList().toExploreFiles()
                    runCatching {
                        fileExplore.requestSendFilesSuspend(sendFiles = exploreFiles, maxConnection = Settings.transferFileMaxConnection().await())
                    }.onSuccess {
                        AndroidLog.d(TAG, "Request send files success: $it")
                        val mineBufferSize = Settings.transferFileBufferSize().await()
                        (requireActivity() as FileTransportActivity)
                            .sendFiles(exploreFiles, Settings.fixTransferFileBufferSize(min(it.bufferSize.toLong(), mineBufferSize)))
                    }.onFailure {
                        AndroidLog.e(TAG, "Request send files fail: $it", it)
                    }
                }.flatMap {
                    updateState { state -> state.copy(selectedFiles = emptySet()) }
                }
            }
            .bindLife()

        binding.refreshLayout.refreshes()
            .observeOn(Schedulers.io())
            .flatMapSingle {
                updateState { oldState ->
                    val oldTree = oldState.fileTree
                    if (oldTree.isRootFileTree()) {
                        oldState.copy(
                            fileTree = createLocalRootTree(rootDir),
                            selectedFiles = emptySet()
                        )
                    } else {
                        val parentTree = oldTree.parentTree
                        val dirLeaf = parentTree?.dirLeafs?.find { it.path == oldTree.path }
                        if (parentTree != null && dirLeaf != null) {
                            oldState.copy(
                                fileTree = parentTree.newLocalSubTree(dirLeaf, rootDir),
                                selectedFiles = emptySet()
                            )
                        } else {
                            oldState.copy(selectedFiles = emptySet())
                        }
                    }
                }.observeOn(AndroidSchedulers.mainThread())
                    .doFinally {
                        binding.refreshLayout.isRefreshing = false
                    }
            }
            .bindLife()

        val popupMenu = PopupMenu(requireContext(), binding.folderMenuLayout)
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
                                    oldState.copy(fileTree = tree, selectedFiles = tree.fileLeafs.toHashSet())
                                }

                                R.id.unselect_all_files -> {
                                    oldState.copy(fileTree = tree, selectedFiles = emptySet())
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

    companion object {

        private const val TAG = "MyDirFragment"

        object FileSelectChange

        enum class FileSortType {
            SortByDate,
            SortByName
        }

        data class MyDirFragmentState(
            val fileTree: FileTree = FileTree(
                dirLeafs = emptyList(),
                fileLeafs = emptyList(),
                path = File.separator,
                parentTree = null
            ),
            val selectedFiles: Set<FileLeaf.CommonFileLeaf> = emptySet(),
            val sortType: FileSortType = FileSortType.SortByName
        )

        fun List<FileLeaf.CommonFileLeaf>.sortFile(sortType: FileSortType): List<FileLeaf.CommonFileLeaf> = when (sortType) {
            FileSortType.SortByDate -> {
                sortedByDescending { it.lastModified }
            }
            FileSortType.SortByName -> {
                sortedBy { it.name }
            }
        }

        fun List<FileLeaf.DirectoryFileLeaf>.sortDir(sortType: FileSortType): List<FileLeaf.DirectoryFileLeaf> = when (sortType) {
            FileSortType.SortByDate -> {
                sortedByDescending { it.lastModified }
            }
            FileSortType.SortByName -> {
                sortedBy { it.name }
            }
        }
    }
}