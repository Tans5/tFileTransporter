package com.tans.tfiletransporter.ui.activity.filetransport

import android.os.Environment
import android.widget.Toast
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
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.kodein.di.instance
import java.io.File

class MyDirFragment : BaseFragment<MyDirFragmentBinding, FileTree>(R.layout.my_dir_fragment, newRootFileTree()) {

    private val fileTransportScopeData: FileTransportScopeData by instance()

    private val pathPrefix = Environment.getExternalStorageDirectory().let { it.path }
    override fun onInit() {
        bindState()
            .distinctUntilChanged()
            .observeOn(Schedulers.io())
            .flatMapSingle { oldTree ->
                if (!oldTree.notNeedRefresh) {
                    val file = File(pathPrefix + oldTree.path)
                    val children = file.listFiles()?.map {
                        if (it.isDirectory) {
                            DirectoryYoungLeaf(
                                name = it.name,
                                childrenCount = it.listFiles()?.size?.toLong() ?: 0L,
                                lastModified = it.lastModified()
                            )
                        } else {
                            FileYoungLeaf(
                                name = it.name,
                                size = it.length(),
                                lastModified = it.lastModified()
                            )
                        }
                    } ?: emptyList()
                    updateState { children.refreshFileTree(oldTree) }
                } else {
                    Single.just(oldTree)
                }
            }
            .bindLife()

        render {
            binding.pathTv.text = it.path
        }.bindLife()

        binding.fileFolderRv.adapter = (SimpleAdapterSpec<DirectoryFileLeaf, FolderItemLayoutBinding>(
            layoutId = R.layout.folder_item_layout,
            bindData = { _, data, binding -> binding.data = data },
            dataUpdater = bindState().map { it.dirLeafs },
            differHandler = DifferHandler(
                itemsTheSame = { a, b -> a.path == b.path },
                contentTheSame = { a, b -> a == b }
            ),
            itemClicks = listOf { binding, _ ->
                binding.root to { _, data ->
                    updateState {
                        data.newSubTree()
                    }.map { }
                }
            }
        ) + SimpleAdapterSpec<CommonFileLeaf, FileItemLayoutBinding>(
            layoutId = R.layout.file_item_layout,
            bindData = { _, data, binding -> binding.data = data },
            dataUpdater = bindState().map { it.fileLeafs },
            differHandler = DifferHandler(
                itemsTheSame = { a, b -> a.path == b.path },
                contentTheSame = { a, b -> a == b }
            )
        )).toAdapter()

        binding.fileFolderRv.addItemDecoration(MarginDividerItemDecoration.Companion.Builder()
                .divider(MarginDividerItemDecoration.Companion.ColorDivider(requireContext().getColor(R.color.line_color),
                        requireContext().dp2px(1)))
                .marginStart(requireContext().dp2px(65))
                .build()
        )

        fileTransportScopeData.floatBtnEvent
                .filter { !isHidden }
                .doOnNext {
                    Toast.makeText(requireContext(), "Message From My Dir", Toast.LENGTH_SHORT).show()
                }
                .bindLife()
    }


    override fun onBackPressed(): Boolean {
        return if (bindState().firstOrError().blockingGet().isRootFileTree()) {
            false
        } else {
            updateState { state ->
                state.parentTree ?: state
            }.bindLife()
            true
        }
    }

    companion object {
        const val FRAGMENT_TAG = "my_dir_fragment_tag"
    }
}