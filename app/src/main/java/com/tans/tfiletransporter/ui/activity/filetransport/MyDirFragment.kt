package com.tans.tfiletransporter.ui.activity.filetransport

import android.util.Log
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
import kotlinx.coroutines.rx2.rxSingle
import org.kodein.di.instance
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList

object FileSelectChange

class MyDirFragment : BaseFragment<MyDirFragmentBinding, Pair<FileTree, Set<CommonFileLeaf>>>(R.layout.my_dir_fragment, newRootFileTree() to emptySet()) {

    private val fileTransportScopeData: FileTransportScopeData by instance()

    override fun onInit() {
        bindState()
                .map { it.first }
                .distinctUntilChanged()
                .flatMapSingle { oldTree ->
                    if (!oldTree.notNeedRefresh) {
                        updateState {
                            val path = if (oldTree.isRootFileTree()) homePath else Paths.get(homePathString + oldTree.path)
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
                            children.refreshFileTree(oldTree) to emptySet()
                        }
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .loadingDialog(requireActivity())
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

        render({ it.first }) {
            binding.pathTv.text = it.path
        }.bindLife()

        binding.fileFolderRv.adapter = (SimpleAdapterSpec<DirectoryFileLeaf, FolderItemLayoutBinding>(
                layoutId = R.layout.folder_item_layout,
                bindData = { _, data, binding -> binding.data = data },
                dataUpdater = bindState().map { it.first.dirLeafs },
                differHandler = DifferHandler(
                        itemsTheSame = { a, b -> a.path == b.path },
                        contentTheSame = { a, b -> a == b }
                ),
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, data ->
                        updateState { (parentTree, _) ->
                            data.newSubTree(parentTree) to emptySet()
                        }.map { }
                    }
                }
        ) + SimpleAdapterSpec<Pair<CommonFileLeaf, Boolean>, FileItemLayoutBinding>(
                layoutId = R.layout.file_item_layout,
                bindData = { _, data, binding -> binding.data = data.first; binding.isSelect = data.second },
                dataUpdater = bindState().map { state -> state.first.fileLeafs.map { it to state.second.contains(it) } },
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
                            val selectedFiles = oldState.second
                            oldState.copy(second = if (isSelect) selectedFiles - file else selectedFiles + file)
                        }.map {  }
                    }
                }
        )).toAdapter()

        binding.fileFolderRv.addItemDecoration(MarginDividerItemDecoration.Companion.Builder()
                .divider(MarginDividerItemDecoration.Companion.ColorDivider(requireContext().getColor(R.color.line_color),
                        requireContext().dp2px(1)))
                .marginStart(requireContext().dp2px(65))
                .build()
        )

        fileTransportScopeData.floatBtnEvent
                .withLatestFrom(bindState().map { it.second })
                .filter { !isHidden && it.second.isNotEmpty() }
                .flatMapSingle { (_, selectedFiles) ->
                    rxSingle {
                        fileTransportScopeData.fileTransporter.writerHandleChannel.send(requireActivity().newFilesShareWriterHandle(
                                selectedFiles.map { it.toFile() }
                        ))
                    }.flatMap {
                        updateState { state -> state.copy(second = emptySet()) }
                    }
                }
                .bindLife()
    }


    override fun onBackPressed(): Boolean {
        return if (bindState().firstOrError().blockingGet().first.isRootFileTree()) {
            false
        } else {
            updateState { state ->
                if (state.first.parentTree == null) state else state.first.parentTree!! to emptySet()
            }.bindLife()
            true
        }
    }

    companion object {
        const val FRAGMENT_TAG = "my_dir_fragment_tag"
    }
}