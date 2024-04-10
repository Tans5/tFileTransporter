package com.tans.tfiletransporter.ui.filetransport

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.MyDirFragmentBinding
import com.tans.tfiletransporter.file.*
import com.tans.tfiletransporter.ui.BaseFragment
import kotlinx.coroutines.Dispatchers
import org.kodein.di.instance
import androidx.activity.addCallback
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.FileTreeLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.ui.FileTreeUI
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.withLatestFrom
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingDeque

class MyDirFragment : BaseFragment<MyDirFragmentBinding, Unit>(R.layout.my_dir_fragment, Unit) {

    private val onBackPressedDispatcher: OnBackPressedDispatcher by instance()

    private val fileExplore: FileExplore by instance()

    private var fileTreeUI : FileTreeUI? = null

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        onBackPressedDispatcher.addCallback {
            launch {
                fileTreeUI?.backPress()
            }
        }
    }

    private val fileTreeStateFlow by lazy {
        MutableStateFlow(FileTreeUI.Companion.FileTreeState())
    }

    private val fileTreeRecyclerViewScrollChannel: Channel<Int> by lazy {
        Channel<Int>(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    private val fileTreeFolderPositionDeque: LinkedBlockingDeque<Int> by lazy {
        LinkedBlockingDeque()
    }

    override fun initViews(binding: MyDirFragmentBinding) {
        val fileTreeUI = FileTreeUI(
            viewBinding = binding.fileTreeLayout,
            rootTreeUpdater = {
                withContext(Dispatchers.IO) {
                    createLocalRootTree(requireContext())
                }
            },
            subTreeUpdater = { parentTree, dir ->
                withContext(Dispatchers.IO) {
                    parentTree.newLocalSubTree(dir)
                }
            },
            coroutineScope = this,
            stateFlow = fileTreeStateFlow,
            recyclerViewScrollChannel = fileTreeRecyclerViewScrollChannel,
            folderPositionDeque = fileTreeFolderPositionDeque
        )
        this.fileTreeUI = fileTreeUI

        launch {
            fileTreeUI.stateFlow()
                .map { it.fileTree }
                .distinctUntilChanged()
                .collect { tree ->
                    val tab = (requireActivity() as FileTransportActivity).bindState().map { it.selectedTabType }.firstOrError().await()
                    onBackPressedCallback.isEnabled = !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.MyDir
                }
        }

        (requireActivity() as FileTransportActivity).bindState()
            .map { it.selectedTabType }
            .distinctUntilChanged()
            .doOnNext { tab ->
                onBackPressedCallback.isEnabled =  tab == FileTransportActivity.Companion.DirTabType.MyDir
            }
            .bindLife()

        (requireActivity() as FileTransportActivity).observeFloatBtnClick()
            .flatMapSingle {
                (activity as FileTransportActivity).bindState().map { it.selectedTabType }
                    .firstOrError()
                    .map { tabType ->
                        tabType to fileTreeUI.getSelectedFiles()
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
                    fileTreeUI.clearSelectedFiles()
                }
            }
            .bindLife()
    }

    companion object {

        private const val TAG = "MyDirFragment"
    }
}