package com.tans.tfiletransporter.ui.filetransport

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.MyDirFragmentBinding
import com.tans.tfiletransporter.file.*
import com.tans.tfiletransporter.ui.BaseFragment
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.io.File

class MyDirFragment : BaseFragment<MyDirFragmentBinding, FileTree>(R.layout.my_dir_fragment, newRootFileTree()) {

    val pathPrefix = Environment.getExternalStorageDirectory().let { it.path }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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
                                size = it.totalSpace,
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
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    companion object {
        const val FRAGMENT_TAG = "my_dir_fragment_tag"
    }
}