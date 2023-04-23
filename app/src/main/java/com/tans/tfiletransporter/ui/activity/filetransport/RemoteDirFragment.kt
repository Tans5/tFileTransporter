package com.tans.tfiletransporter.ui.activity.filetransport

import android.util.Log
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding3.appcompat.itemClicks
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.jakewharton.rxbinding3.view.clicks
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
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.net.model.RequestFilesModel
import com.tans.tfiletransporter.net.model.RequestFolderModel
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showLoadingDialog
import com.tans.tfiletransporter.ui.activity.filetransport.activity.*
import com.tans.tfiletransporter.utils.dp2px
import com.tans.tfiletransporter.utils.getFilePathMd5
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import org.kodein.di.instance
import java.io.File
import java.nio.file.Paths
import java.util.*

data class RemoteDirState(
        val fileTree: Optional<FileTree> = Optional.empty(),
        val selectedFiles: Set<CommonFileLeaf> = emptySet(),
        val sortType: FileSortType = FileSortType.SortByName
)

class RemoteDirFragment : BaseFragment<RemoteDirFragmentBinding, RemoteDirState>(R.layout.remote_dir_fragment, RemoteDirState()) {

    // private val scopeData: FileTransportScopeData by instance()

    private val recyclerViewScrollChannel = Channel<Int>(1)
    private val folderPositionDeque: Deque<Int> = ArrayDeque()

    override fun initViews(binding: RemoteDirFragmentBinding) {

        updateState {
            // TODO: File Separator
            RemoteDirState(Optional.of(newRootFileTree(path = "/")), emptySet())
        }.bindLife()

        bindState()
            .map { it.fileTree }
            .filter { it.isPresent }
            .map { it.get() }
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .flatMapSingle { oldTree ->
                if (!oldTree.notNeedRefresh) {
                    rxSingle {
//                        scopeData.fileExploreConnection.sendFileExploreContentToRemote(
//                            RequestFolderModel(
//                                requestPath = oldTree.path
//                            )
//                        )
//                        scopeData.remoteFolderModelEvent
//                            .filter { it.path == oldTree.path }
//                            .firstOrError()
//                            .flatMap { remoteFolder ->
//                                updateState { oldState ->
//                                    val children: List<YoungLeaf> = remoteFolder.childrenFolders
//                                        .map {
//                                            DirectoryYoungLeaf(
//                                                name = it.name,
//                                                childrenCount = it.childCount,
//                                                lastModified = it.lastModify.toInstant()
//                                                    .toEpochMilli()
//                                            )
//                                        } + remoteFolder.childrenFiles
//                                        .map {
//                                            FileYoungLeaf(
//                                                name = it.name,
//                                                size = it.size,
//                                                lastModified = it.lastModify.toInstant()
//                                                    .toEpochMilli()
//                                            )
//                                        }
//                                    oldState.copy(
//                                        fileTree = Optional.of(
//                                            children.refreshFileTree(
//                                                parentTree = oldTree,
//                                                dirSeparator = scopeData.handshakeModel.pathSeparator
//                                            )
//                                        ), selectedFiles = emptySet()
//                                    )
//                                }.map {
//
//                                }.onErrorResumeNext {
//                                    Log.e(this::class.qualifiedName, it.toString())
//                                    Single.just(Unit)
//                                }
//                            }.await()
                    }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                } else {
                    Single.just(Unit)
                }.let {
                    if (binding.refreshLayout.isRefreshing) {
                        it.doFinally {
                            binding.refreshLayout.isRefreshing = false
                        }
                    } else {
                        it.loadingDialog(requireActivity())
                    }
                }
            }
            .bindLife()

        render({ it.fileTree }) {
            binding.remotePathTv.text = if (it.isPresent) it.get().path else ""
        }.bindLife()

        binding.remoteFileFolderRv.adapter = (SimpleAdapterSpec<DirectoryFileLeaf, FolderItemLayoutBinding>(
                layoutId = R.layout.folder_item_layout,
                bindData = { _, data, binding -> binding.data = data },
                dataUpdater = bindState().map { if (it.fileTree.isPresent) it.fileTree.get().dirLeafs.sortDir(it.sortType) else emptyList() },
                differHandler = DifferHandler(
                        itemsTheSame = { a, b -> a.path == b.path },
                        contentTheSame = { a, b -> a == b }
                ),
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, data ->
                        updateState { oldState ->
                            val i = this@RemoteDirFragment.binding.remoteFileFolderRv.firstVisibleItemPosition()
                            folderPositionDeque.push(i)
                            oldState.copy(fileTree = Optional.of(data.newSubTree(oldState.fileTree.get())), selectedFiles = emptySet())
                        }.map { }
                    }
                }
        ) + SimpleAdapterSpec<Pair<CommonFileLeaf, Boolean>, FileItemLayoutBinding>(
                layoutId = R.layout.file_item_layout,
                bindData = { _, data, binding -> binding.data = data.first; binding.isSelect = data.second },
                dataUpdater = bindState().map { state -> state.fileTree.get().fileLeafs.sortFile(state.sortType).map { it to state.selectedFiles.contains(it) } },
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
                        binding.isSelect = data.second
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
            .observeOn(AndroidSchedulers.mainThread())
            .flatMapSingle {
                rxSingle {
                    val dialog =
                        withContext(Dispatchers.Main) { requireActivity().showLoadingDialog() }
//                    withContext(Dispatchers.IO) {
//                        scopeData.fileExploreConnection.sendFileExploreContentToRemote(
//                            fileExploreContent = RequestFilesModel(
//                                requestFiles = it.map {
//                                    FileMd5(
//                                        md5 = Paths.get(
//                                            FileConstants.homePathString, it.path
//                                        ).getFilePathMd5(), file = it.toFile()
//                                    )
//                                }
//                            ),
//                            waitReplay = true
//                        )
//                    }
                    updateState {
                        it.copy(selectedFiles = emptySet())
                    }.await()
                    withContext(Dispatchers.Main) { dialog.cancel() }
                }
            }
            .bindLife()

        binding.refreshLayout.refreshes()
            .flatMapSingle {
                updateState { oldState ->
                    val fileTree = oldState.fileTree
                    if (fileTree.isPresent) {
                        oldState.copy(fileTree = Optional.of(fileTree.get().copy(notNeedRefresh = false)), selectedFiles = emptySet())
                    } else {
                        oldState
                    }
                }
            }
            .bindLife()

        val popupMenu = PopupMenu(requireContext(), binding.folderMenuLayout)
        popupMenu.inflate(R.menu.folder_menu)

        popupMenu.itemClicks()
            .withLatestFrom(bindState())
            .flatMapSingle { (menuItem, state) ->
                rxSingle {
                    val newState = withContext(Dispatchers.IO) {
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
                                        if (oldState.sortType != FileSortType.SortByDate) {
                                            recyclerViewScrollChannel.trySend(0).isSuccess
                                            oldState.copy(sortType = FileSortType.SortByDate)
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

    override fun onBackPressed(): Boolean {
        return if (bindState().firstOrError().blockingGet().fileTree.get().isRootFileTree()) {
            false
        } else {
            updateState { state ->
                val i = folderPositionDeque.poll()
                if (i != null) {
                    recyclerViewScrollChannel.trySend(i).isSuccess
                }
                val parent = state.fileTree.get().parentTree
                if (parent != null) state.copy(fileTree = Optional.of(parent), selectedFiles = emptySet()) else state
            }.bindLife()
            true
        }
    }

    companion object {
        const val FRAGMENT_TAG = "remote_dir_fragment_tag"
    }
}