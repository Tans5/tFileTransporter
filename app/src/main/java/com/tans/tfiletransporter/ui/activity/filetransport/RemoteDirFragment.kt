package com.tans.tfiletransporter.ui.activity.filetransport

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.addCallback
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding3.appcompat.itemClicks
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.jakewharton.rxbinding3.view.clicks
import com.tans.rxutils.switchThread
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.recyclerviewutils.MarginDividerItemDecoration
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.plus
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.FileItemLayoutBinding
import com.tans.tfiletransporter.databinding.FolderItemLayoutBinding
import com.tans.tfiletransporter.databinding.RemoteDirFragmentBinding
import com.tans.tfiletransporter.file.*
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestDownloadFilesSuspend
import com.tans.tfiletransporter.transferproto.fileexplore.requestScanDirSuspend
import com.tans.tfiletransporter.ui.DataBindingAdapter
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import com.tans.tfiletransporter.ui.activity.filetransport.activity.*
import com.tans.tfiletransporter.utils.dp2px
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.withLatestFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import java.util.*
import com.tans.tfiletransporter.ui.activity.filetransport.MyDirFragment.Companion.FileSortType
import com.tans.tfiletransporter.ui.activity.filetransport.MyDirFragment.Companion.sortDir
import com.tans.tfiletransporter.ui.activity.filetransport.MyDirFragment.Companion.sortFile
import kotlinx.coroutines.launch
import org.kodein.di.instance
import kotlin.jvm.optionals.getOrNull


class RemoteDirFragment : BaseFragment<RemoteDirFragmentBinding, RemoteDirFragment.Companion.RemoteDirState>(R.layout.remote_dir_fragment, RemoteDirState()) {

    private val recyclerViewScrollChannel = Channel<Int>(1)
    private val folderPositionDeque: Deque<Int> = ArrayDeque()

    private val onBackPressedDispatcher: OnBackPressedDispatcher by instance()

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        onBackPressedDispatcher.addCallback(this) {
            launch {
                updateState { state ->
                    val fileTree = state.fileTree.getOrNull()
                    if (fileTree?.parentTree != null) {
                        val i = folderPositionDeque.poll()
                        if (i != null) {
                            recyclerViewScrollChannel.trySend(i).isSuccess
                        }
                        RemoteDirState(
                            fileTree = Optional.of(fileTree.parentTree),
                            selectedFiles = emptySet()
                        )
                    } else {
                        state
                    }
                }.await()
            }
        }
    }

    private val fileExplore: FileExplore by instance()

    private suspend fun scanRootDir() {
        val handshake = (requireActivity() as FileTransportActivity).bindState()
            .filter { it.handshake.getOrNull() != null }
            .map { it.handshake.get() }
            .firstOrError()
            .await()
        runCatching {
            fileExplore.requestScanDirSuspend(handshake.remoteFileSeparator)
        }.onSuccess {
            AndroidLog.d(TAG, "Request scan root dir success")
            updateState { s ->
                s.copy(fileTree = Optional.of(createRemoteRootTree(it)), selectedFiles = emptySet())
            }.await()
        }.onFailure {
            AndroidLog.e(TAG, "Request scan root dir fail: $it", it)
        }
    }

    @Suppress("NAME_SHADOWING")
    override fun initViews(binding: RemoteDirFragmentBinding) {

        rxSingle(Dispatchers.IO) {
            scanRootDir()
        }.observeOn(AndroidSchedulers.mainThread())
            .loadingDialog(requireActivity())
            .bindLife()

        bindState()
            .map { it.fileTree }
            .distinctUntilChanged()
            .doOnNext {
                onBackPressedCallback.isEnabled = it.getOrNull()?.parentTree != null
            }
            .bindLife()


        render({ it.fileTree }) {
            binding.remotePathTv.text = if (it.isPresent) it.get().path else ""
        }.bindLife()

        binding.remoteFileFolderRv.adapter = (SimpleAdapterSpec<FileLeaf.DirectoryFileLeaf, FolderItemLayoutBinding>(
                layoutId = R.layout.folder_item_layout,
                bindData = { _, data, binding ->
                    binding.titleTv.text = data.name
                    DataBindingAdapter.dateText(binding.modifiedDateTv, data.lastModified)
                    binding.filesCountTv.text = getString(R.string.file_count, data.childrenCount)
                },
                dataUpdater = bindState().map { if (it.fileTree.isPresent) it.fileTree.get().dirLeafs.sortDir(it.sortType) else emptyList() },
                differHandler = DifferHandler(
                        itemsTheSame = { a, b -> a.path == b.path },
                        contentTheSame = { a, b -> a == b }
                ),
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, data ->
                        rxSingle(Dispatchers.IO) {
                            val tree = bindState().map { it.fileTree }.firstOrError().await().getOrNull()
                            if (tree != null) {
                                runCatching {
                                    fileExplore.requestScanDirSuspend(data.path)
                                }.onSuccess {
                                    AndroidLog.d(TAG, "Request dir success")
                                    withContext(Dispatchers.Main) {
                                        val i = this@RemoteDirFragment.binding.remoteFileFolderRv.firstVisibleItemPosition()
                                        folderPositionDeque.push(i)
                                    }
                                    updateState { state ->
                                        state.copy(fileTree = Optional.of(tree.newRemoteSubTree(it)), selectedFiles = emptySet())
                                    }.await()
                                }.onFailure {
                                    AndroidLog.e(TAG, "Request dir fail: $it", it)
                                }
                            }
                            Unit
                        }.observeOn(AndroidSchedulers.mainThread())
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
                    if (state.fileTree.isPresent) {
                        state.fileTree.get().fileLeafs.sortFile(state.sortType)
                            .map { it to state.selectedFiles.contains(it) }
                    } else {
                        emptyList()
                    }
                },
                differHandler = DifferHandler(
                        itemsTheSame = { a, b -> a.first.path == b.first.path },
                        contentTheSame = { a, b -> a == b },
                        changePayLoad = { d1, d2 ->
                            if (d1.first == d2.first && d1.second != d2.second) {
                                MyDirFragment.Companion.FileSelectChange
                            } else {
                                null
                            }
                        }
                ),
                bindDataPayload = { _, data, binding, payloads ->
                    if (payloads.contains(MyDirFragment.Companion.FileSelectChange)) {
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
                        }.map {  }
                    }
                }
        )).toAdapter { list ->
            val position = recyclerViewScrollChannel.tryReceive().getOrNull()
            if (position != null && position < list.size) {
                (binding.remoteFileFolderRv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
            }
        }

        binding.remoteFileFolderRv.addItemDecoration(MarginDividerItemDecoration.Companion.Builder()
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
            .filter { it.first == FileTransportActivity.Companion.DirTabType.RemoteDir && it.second.isNotEmpty() }
            .map { it.second }
            .flatMapSingle { leafs ->
                rxSingle(Dispatchers.IO) {
                    val exploreFiles = leafs.toList().toExploreFiles()
                    runCatching {
                        fileExplore.requestDownloadFilesSuspend(exploreFiles)
                    }.onSuccess {
                        AndroidLog.d(TAG, "Request download fails success.")
                        updateState { it.copy(selectedFiles = emptySet()) }.await()
                    }.onFailure {
                        AndroidLog.e(TAG, "Request download files fail: $it", it)
                    }
                }
            }
            .bindLife()

        binding.refreshLayout.refreshes()
            .flatMapSingle {
                rxSingle(Dispatchers.IO) {
                    val fileTree = bindState().firstOrError().await().fileTree.getOrNull()
                    if (fileTree?.parentTree == null) {
                        scanRootDir()
                    } else {
                        runCatching {
                            fileExplore.requestScanDirSuspend(fileTree.parentTree.path)
                        }.onSuccess {
                            AndroidLog.d(TAG, "Refresh success")
                            updateState { s ->
                                s.copy(fileTree = Optional.of(fileTree.parentTree.newRemoteSubTree(it)), selectedFiles = emptySet())
                            }.await()
                        }.onFailure {
                            AndroidLog.e(TAG, "Refresh fail: $it", it)
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
                            if (oldState.fileTree.isPresent) {
                                val tree = oldState.fileTree.get()
                                when (menuItem.itemId) {
                                    R.id.select_all_files -> {
                                        oldState.copy(
                                            fileTree = Optional.of(tree),
                                            selectedFiles = tree.fileLeafs.toHashSet()
                                        )
                                    }

                                    R.id.unselect_all_files -> {
                                        oldState.copy(
                                            fileTree = Optional.of(tree),
                                            selectedFiles = emptySet()
                                        )
                                    }

                                    R.id.sort_by_date -> {
                                        if (oldState.sortType != MyDirFragment.Companion.FileSortType.SortByDate) {
                                            recyclerViewScrollChannel.trySend(0).isSuccess
                                            oldState.copy(sortType = MyDirFragment.Companion.FileSortType.SortByDate)
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

                            } else {
                                oldState
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
        private const val TAG = "RemoteDirFragment"
        data class RemoteDirState(
            val fileTree: Optional<FileTree> = Optional.empty(),
            val selectedFiles: Set<FileLeaf.CommonFileLeaf> = emptySet(),
            val sortType: FileSortType = MyDirFragment.Companion.FileSortType.SortByName
        )
    }
}