package com.tans.tfiletransporter.ui.activity.filetransport

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.MyDirFragmentBinding
import com.tans.tfiletransporter.file.*
import com.tans.tfiletransporter.ui.activity.BaseFragment
import kotlinx.coroutines.Dispatchers
import org.kodein.di.instance
import androidx.activity.addCallback
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.ui.activity.FileTreeUI
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.withLatestFrom
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle

class MyDirFragment : BaseFragment<MyDirFragmentBinding, Unit>(R.layout.my_dir_fragment, Unit) {

    private val onBackPressedDispatcher: OnBackPressedDispatcher by instance()

    private val fileExplore: FileExplore by instance()

    private var fileTreeUI : FileTreeUI? = null

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        onBackPressedDispatcher.addCallback {
            launch {
                fileTreeUI?.backPress()?.await()
            }
        }
    }

    override fun initViews(binding: MyDirFragmentBinding) {
        val fileTreeUI = FileTreeUI(
            binding = binding.fileTreeLayout,
            rootTreeUpdater = {
                Single.fromCallable { createLocalRootTree(requireContext()) }
            },
            subTreeUpdater = { parentTree, dir ->
                Single.fromCallable { parentTree.newLocalSubTree(dir) }
            }
        )
        this.fileTreeUI = fileTreeUI
        fileTreeUI.start()

        fileTreeUI.bindState()
            .map { it.fileTree }
            .distinctUntilChanged()
            .withLatestFrom((requireActivity() as FileTransportActivity).bindState().map { it.selectedTabType })
            .doOnNext { (tree, tab) ->
                onBackPressedCallback.isEnabled = !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.MyDir
            }
            .bindLife()

        (requireActivity() as FileTransportActivity).bindState()
            .map { it.selectedTabType }
            .withLatestFrom(fileTreeUI.bindState().map { it.fileTree })
            .distinctUntilChanged()
            .doOnNext { (tab, tree) ->
                onBackPressedCallback.isEnabled = !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.MyDir
            }
            .bindLife()

        (requireActivity() as FileTransportActivity).observeFloatBtnClick()
            .flatMapSingle {
                (activity as FileTransportActivity).bindState().map { it.selectedTabType }
                    .firstOrError()
                    .flatMap { tabType ->
                        fileTreeUI.getSelectedFiles()
                            .map { tabType to it }
                    }
            }
            .filter { it.first == FileTransportActivity.Companion.DirTabType.MyDir && it.second.isNotEmpty() }
            .flatMapSingle { (_, selectedFiles) ->
                rxSingle(Dispatchers.IO) {
                    val exploreFiles = selectedFiles.toList().toExploreFiles()
                    runCatching {
                        fileExplore.requestSendFilesSuspend(sendFiles = exploreFiles, maxConnection = Settings.transferFileMaxConnection())
                    }.onSuccess {
                        AndroidLog.d(TAG, "Request send files success: $it")
                        (requireActivity() as FileTransportActivity)
                            .sendFiles(exploreFiles)
                    }.onFailure {
                        AndroidLog.e(TAG, "Request send files fail: $it", it)
                    }
                }.flatMap {
                    fileTreeUI.clearSelectedFiles().toSingleDefault(Unit)
                }
            }
            .bindLife()
    }

    override fun onDestroy() {
        super.onDestroy()
        fileTreeUI?.stop()
    }

    companion object {

        private const val TAG = "MyDirFragment"
    }
}