package com.tans.tfiletransporter.ui.filetransport

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.MyDirFragmentBinding
import com.tans.tfiletransporter.file.*
import kotlinx.coroutines.Dispatchers
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.ui.FileTreeUI
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingDeque

class MyDirFragment : BaseCoroutineStateFragment<Unit>(Unit) {

    override val layoutId: Int = R.layout.my_dir_fragment

    private val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = requireActivity().onBackPressedDispatcher

    private val fileExplore: FileExplore by lazy {
        (requireActivity() as FileTransportActivity).fileExplore
    }

    private var fileTreeUI : FileTreeUI? = null

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                uiCoroutineScope?.launch {
                    fileTreeUI?.backPress()
                }
            }
        }
    }

    private val fileTreeStateFlow by lazy {
        MutableStateFlow(FileTreeUI.Companion.FileTreeState())
    }

    private val fileTreeRecyclerViewScrollChannel: Channel<Int> by lazy {
        Channel(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    }

    private val fileTreeFolderPositionDeque: LinkedBlockingDeque<Int> by lazy {
        LinkedBlockingDeque()
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {  }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        onBackPressedDispatcher.addCallback(this@MyDirFragment, onBackPressedCallback)
        val viewBinding = MyDirFragmentBinding.bind(contentView)
        val fileTreeUI = FileTreeUI(
            context = requireActivity(),
            viewBinding = viewBinding.fileTreeLayout,
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
        this@MyDirFragment.fileTreeUI = fileTreeUI

        val context = requireActivity() as FileTransportActivity

        launch {
            fileTreeUI.stateFlow()
                .map { it.fileTree }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Main)
                .collect { tree ->
                    val tab = context.currentState().selectedTabType
                    onBackPressedCallback.isEnabled = !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.MyDir
                }
        }

        launch {
            context.stateFlow()
                .map { it.selectedTabType }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Main)
                .collect { tab ->
                    val tree = fileTreeUI.currentState().fileTree
                    onBackPressedCallback.isEnabled = !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.MyDir
                }
        }

        launch {
            context.observeFloatBtnClick()
                .collect {
                    val tab = context.currentState().selectedTabType
                    val exploreFiles = fileTreeUI.getSelectedFiles().toExploreFiles()
                    if (tab == FileTransportActivity.Companion.DirTabType.MyDir && exploreFiles.isNotEmpty()) {
                        launch {
                            runCatching {
                                fileExplore.requestSendFilesSuspend(sendFiles = exploreFiles, maxConnection = Settings.transferFileMaxConnection())
                            }.onSuccess {
                                AndroidLog.d(TAG, "Request send files success: $it")
                                runCatching {
                                    (requireActivity() as FileTransportActivity).sendFiles(exploreFiles)
                                }
                            }.onFailure {
                                AndroidLog.e(TAG, "Request send files fail: $it", it)
                            }
                            fileTreeUI.clearSelectedFiles()
                        }
                    }
                }
        }
    }

    companion object {

        private const val TAG = "MyDirFragment"
    }
}