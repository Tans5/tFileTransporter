package com.tans.tfiletransporter.ui.activity.filetransport

import android.util.Log
import android.widget.Toast
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
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import com.tans.tfiletransporter.utils.dp2px
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import org.kodein.di.instance
import java.util.*

class RemoteDirFragment : BaseFragment<RemoteDirFragmentBinding, Optional<FileTree>>(R.layout.remote_dir_fragment, Optional.empty()) {

    private val fileTransportScopeData: FileTransportScopeData by instance()

    override fun onInit() {

        updateState {
            Optional.of(newRootFileTree(path = fileTransportScopeData.remoteDirSeparator))
        }.bindLife()

        bindState()
                .filter { it.isPresent }
                .map { it.get() }
                .distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread())
                .flatMapSingle { oldTree ->
                    if (!oldTree.notNeedRefresh) {
                        rxSingle {
                            fileTransportScopeData.fileTransporter.writerHandleChannel.send(newRequestFolderChildrenShareWriterHandle(oldTree.path))
                            fileTransportScopeData.remoteFolderModelEvent.firstOrError()
                                    .flatMap { remoteFolder ->
                                        if (remoteFolder.path == oldTree.path) {
                                            updateState {
                                                val children: List<YoungLeaf> = remoteFolder.childrenFolders
                                                        .map {
                                                            DirectoryYoungLeaf(
                                                                    name = it.name,
                                                                    childrenCount = it.childCount,
                                                                    lastModified = it.lastModify.toInstant().toEpochMilli()
                                                            )
                                                        } + remoteFolder.childrenFiles
                                                        .map {
                                                            FileYoungLeaf(
                                                                    name = it.name,
                                                                    size = it.size,
                                                                    lastModified = it.lastModify.toInstant().toEpochMilli()
                                                            )
                                                        }
                                                Optional.of(children.refreshFileTree(oldTree))
                                            }.map {

                                            }.onErrorResumeNext {
                                                Log.e(this::class.qualifiedName, it.toString())
                                                Single.just(Unit)
                                            }
                                        } else {
                                            Single.just(Unit)
                                        }
                                    }.await()
                        }
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .loadingDialog(requireActivity())
                    } else {
                        Single.just(Unit)
                    }
                }
                .bindLife()

        render {
            binding.remotePathTv.text = if (it.isPresent) it.get().path else ""
        }.bindLife()

        binding.remoteFileFolderRv.adapter = (SimpleAdapterSpec<DirectoryFileLeaf, FolderItemLayoutBinding>(
                layoutId = R.layout.folder_item_layout,
                bindData = { _, data, binding -> binding.data = data },
                dataUpdater = bindState().map { if (it.isPresent) it.get().dirLeafs else emptyList() },
                differHandler = DifferHandler(
                        itemsTheSame = { a, b -> a.path == b.path },
                        contentTheSame = { a, b -> a == b }
                ),
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, data ->
                        updateState { parentTree ->
                            Optional.of(data.newSubTree(parentTree.get()))
                        }.map { }
                    }
                }
        ) + SimpleAdapterSpec<CommonFileLeaf, FileItemLayoutBinding>(
                layoutId = R.layout.file_item_layout,
                bindData = { _, data, binding -> binding.data = data },
                dataUpdater = bindState().map { if (it.isPresent) it.get().fileLeafs else emptyList()},
                differHandler = DifferHandler(
                        itemsTheSame = { a, b -> a.path == b.path },
                        contentTheSame = { a, b -> a == b }
                )
        )).toAdapter()

        binding.remoteFileFolderRv.addItemDecoration(MarginDividerItemDecoration.Companion.Builder()
                .divider(MarginDividerItemDecoration.Companion.ColorDivider(requireContext().getColor(R.color.line_color),
                        requireContext().dp2px(1)))
                .marginStart(requireContext().dp2px(65))
                .build()
        )

        fileTransportScopeData.floatBtnEvent
                .filter { !isHidden }
                .doOnNext {
                    Toast.makeText(requireContext(), "Message From Remote Dir", Toast.LENGTH_SHORT).show()
                }
                .bindLife()
    }

    override fun onBackPressed(): Boolean {
        return if (bindState().firstOrError().blockingGet().get().isRootFileTree()) {
            false
        } else {
            updateState { state ->
                val parent = state.get().parentTree
                if (parent != null) Optional.of(parent) else state
            }.bindLife()
            true
        }
    }

    companion object {
        const val FRAGMENT_TAG = "remote_dir_fragment_tag"
    }
}