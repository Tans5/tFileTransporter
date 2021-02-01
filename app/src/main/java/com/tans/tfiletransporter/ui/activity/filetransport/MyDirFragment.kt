package com.tans.tfiletransporter.ui.activity.filetransport

import android.util.Log
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.tans.tfiletransporter.databinding.MyDirFragmentBinding
import com.tans.tfiletransporter.file.*
import com.tans.tfiletransporter.file.FileConstants.homePath
import com.tans.tfiletransporter.file.FileConstants.homePathString
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import com.tans.tfiletransporter.ui.activity.filetransport.activity.FileTransportScopeData
import com.tans.tfiletransporter.ui.activity.filetransport.activity.newFilesShareWriterHandle
import com.tans.tfiletransporter.ui.activity.filetransport.activity.toFile
import com.tans.tfiletransporter.utils.dp2px
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
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.streams.toList

object FileSelectChange

enum class FileSortType {
    SortByDate,
    SortByName
}

data class MyDirFragmentState(
    val fileTree: FileTree = newRootFileTree(),
    val selectedFiles: Set<CommonFileLeaf> = emptySet(),
    val sortType: FileSortType = FileSortType.SortByName
)

fun List<CommonFileLeaf>.sortFile(sortType: FileSortType): List<CommonFileLeaf> = when (sortType) {
    FileSortType.SortByDate -> {
        sortedByDescending { it.lastModified }
    }
    FileSortType.SortByName -> {
        sortedBy { it.name }
    }
}

fun List<DirectoryFileLeaf>.sortDir(sortType: FileSortType): List<DirectoryFileLeaf> = when (sortType) {
    FileSortType.SortByDate -> {
        sortedByDescending { it.lastModified }
    }
    FileSortType.SortByName -> {
        sortedBy { it.name }
    }
}

class MyDirFragment : BaseFragment<MyDirFragmentBinding, MyDirFragmentState>(R.layout.my_dir_fragment, MyDirFragmentState()) {

    private val fileTransportScopeData: FileTransportScopeData by instance()

    private val recyclerViewScrollChannel = Channel<Int>(1)
    private val folderPositionDeque: Deque<Int> = ArrayDeque()

    override fun onInit() {
        bindState()
            .map { it.fileTree }
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .flatMapSingle { oldTree ->
                if (!oldTree.notNeedRefresh) {
                    updateState { oldState ->
                        val path =
                            if (oldTree.isRootFileTree()) homePath else Paths.get(homePathString + oldTree.path)
                        val children = Files.list(path).map { p ->
                            if (Files.isDirectory(p)) {
                                DirectoryYoungLeaf(
                                    name = p.fileName.toString(),
                                    childrenCount = Files.list(p).let { s ->
                                        val size = s.count()
                                        s.close()
                                        size
                                    },
                                    lastModified = Files.getLastModifiedTime(p).toMillis()
                                )
                            } else {
                                FileYoungLeaf(
                                    name = p.fileName.toString(),
                                    size = Files.size(p),
                                    lastModified = Files.getLastModifiedTime(p).toMillis()
                                )
                            }
                        }.toList()
                        oldState.copy(
                            fileTree = children.refreshFileTree(oldTree),
                            selectedFiles = emptySet()
                        )
                    }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread()).let {

                            if (binding.refreshLayout.isRefreshing) {
                                it.doFinally {
                                    binding.refreshLayout.isRefreshing = false
                                }
                            } else {
                                it.loadingDialog(requireActivity())
                            }
                        }
                        .map { }
                        .onErrorResumeNext {
                            Log.e(this::class.qualifiedName, it.toString())
                            Single.just(Unit)
                        }
                } else {
                    Single.just(Unit)
                }
            }
            .bindLife()

        render({ it.fileTree }) {
            binding.pathTv.text = it.path
        }.bindLife()

        binding.fileFolderRv.adapter = (SimpleAdapterSpec<DirectoryFileLeaf, FolderItemLayoutBinding>(
                layoutId = R.layout.folder_item_layout,
                bindData = { _, data, binding -> binding.data = data },
                dataUpdater = bindState().map { it.fileTree.dirLeafs.sortDir(it.sortType) },
                differHandler = DifferHandler(
                        itemsTheSame = { a, b -> a.path == b.path },
                        contentTheSame = { a, b -> a == b }
                ),
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, data ->
                        updateState { oldState ->
                            val i = this@MyDirFragment.binding.fileFolderRv.lastVisibleItemPosition()
                            folderPositionDeque.push(i)
                            oldState.copy(fileTree = data.newSubTree(oldState.fileTree), selectedFiles = emptySet())
                        }.map { }
                    }
                }
        ) + SimpleAdapterSpec<Pair<CommonFileLeaf, Boolean>, FileItemLayoutBinding>(
                layoutId = R.layout.file_item_layout,
                bindData = { _, data, binding -> binding.data = data.first; binding.isSelect = data.second },
                dataUpdater = bindState().map { state -> state.fileTree.fileLeafs.sortFile(state.sortType).map { it to state.selectedFiles.contains(it) } },
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
            val position = recyclerViewScrollChannel.poll()
            if (position != null && position < list.size) {
                binding.fileFolderRv.scrollToPosition(position)
            }
        }

        binding.fileFolderRv.addItemDecoration(MarginDividerItemDecoration.Companion.Builder()
                .divider(MarginDividerItemDecoration.Companion.ColorDivider(requireContext().getColor(R.color.line_color),
                        requireContext().dp2px(1)))
                .marginStart(requireContext().dp2px(65))
                .build()
        )

        fileTransportScopeData.floatBtnEvent
                .withLatestFrom(bindState().map { it.selectedFiles })
                .filter { !isHidden && it.second.isNotEmpty() }
                .flatMapSingle { (_, selectedFiles) ->
                    rxSingle {
                        fileTransportScopeData.fileTransporter.writerHandleChannel.send(requireActivity().newFilesShareWriterHandle(
                                selectedFiles.map { it.toFile() }
                        ))
                    }.flatMap {
                        updateState { state -> state.copy(selectedFiles = emptySet()) }
                    }
                }
                .bindLife()

        binding.refreshLayout.refreshes()
                .flatMapSingle {
                    updateState { oldState ->
                        val newTree = oldState.fileTree.copy(notNeedRefresh = false)
                        oldState.copy(fileTree = newTree, selectedFiles = emptySet())
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
                                        recyclerViewScrollChannel.offer(0)
                                        oldState.copy(
                                            sortType = FileSortType.SortByDate
                                        )
                                    } else {
                                        oldState
                                    }
                                }

                                R.id.sort_by_name -> {
                                    if (oldState.sortType != FileSortType.SortByName) {
                                        recyclerViewScrollChannel.offer(0)
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
                    // below code useless.
//                    if (state.sortType != newState.sortType) {
//                        withContext(Dispatchers.Main) { binding.fileFolderRv.scrollToPosition(0) }
//                    }
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
        return if (bindState().firstOrError().blockingGet().fileTree.isRootFileTree()) {
            false
        } else {
            updateState { state ->
                val i = folderPositionDeque.poll()
                if (i != null) {
                    recyclerViewScrollChannel.offer(i)
                }
                if (state.fileTree.parentTree == null) state else MyDirFragmentState(state.fileTree.parentTree, emptySet())
            }.bindLife()
            true
        }
    }

    companion object {
        const val FRAGMENT_TAG = "my_dir_fragment_tag"
    }
}

fun RecyclerView.lastVisibleItemPosition(): Int {
    return (layoutManager as? LinearLayoutManager)?.findLastCompletelyVisibleItemPosition() ?: 0
}