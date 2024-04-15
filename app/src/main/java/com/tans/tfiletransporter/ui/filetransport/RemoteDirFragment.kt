package com.tans.tfiletransporter.ui.filetransport

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.RemoteDirFragmentBinding
import com.tans.tfiletransporter.file.*
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestDownloadFilesSuspend
import com.tans.tfiletransporter.transferproto.fileexplore.requestScanDirSuspend
import com.tans.tfiletransporter.ui.FileTreeUI
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.min


class RemoteDirFragment : BaseCoroutineStateFragment<Unit>(Unit) {

    override val layoutId: Int = R.layout.remote_dir_fragment

    private var fileTreeUI: FileTreeUI? = null

    private val onBackPressedDispatcher: OnBackPressedDispatcher
        get() = requireActivity().onBackPressedDispatcher

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                uiCoroutineScope?.launch {
                    fileTreeUI?.backPress()
                }
            }
        }
    }

    private val fileExplore: FileExplore by lazy {
        (requireActivity() as FileTransportActivity).fileExplore
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

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {  }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        onBackPressedDispatcher.addCallback(this@RemoteDirFragment, onBackPressedCallback)
        val viewBinding = RemoteDirFragmentBinding.bind(contentView)
        val context = requireActivity() as FileTransportActivity
        val fileTreeUI = FileTreeUI(
            context = requireActivity(),
            viewBinding = viewBinding.fileTreeLayout,
            rootTreeUpdater = {
                val handshake = (context.currentState().connectionStatus as? FileTransportActivity.Companion.ConnectionStatus.Connected)?.handshake
                if (handshake != null) {
                   val result = runCatching {
                        fileExplore.requestScanDirSuspend(handshake.remoteFileSeparator)
                    }.onSuccess {
                        AndroidLog.d(TAG, "Request scan root dir success")
                    }.onFailure {
                        AndroidLog.e(TAG, "Request scan root dir fail: $it", it)
                    }
                    if (result.isSuccess) {
                        createRemoteRootTree(result.getOrNull()!!)
                    } else {
                        FileTree(
                            dirLeafs = emptyList(),
                            fileLeafs = emptyList(),
                            path = handshake.remoteFileSeparator,
                            parentTree = null
                        )
                    }
                } else {
                    FileTree(
                        dirLeafs = emptyList(),
                        fileLeafs = emptyList(),
                        path = File.separator,
                        parentTree = null
                    )
                }
            },
            subTreeUpdater = { parentTree, dir ->
                val result = runCatching {
                    fileExplore.requestScanDirSuspend(dir.path)
                }.onSuccess {
                    AndroidLog.d(TAG, "Request dir success")
                }.onFailure {
                    AndroidLog.e(TAG, "Request dir fail: $it", it)
                }
                result.getOrNull()?.let {
                    parentTree.newRemoteSubTree(it)
                } ?: parentTree
            },
            coroutineScope = this,
            stateFlow = fileTreeStateFlow,
            recyclerViewScrollChannel = fileTreeRecyclerViewScrollChannel,
            folderPositionDeque = fileTreeFolderPositionDeque
        )

        this@RemoteDirFragment.fileTreeUI = fileTreeUI

        launch {
            fileTreeUI.stateFlow()
                .map { it.fileTree }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Main)
                .collect { tree ->
                    val tab = context.currentState().selectedTabType
                    onBackPressedCallback.isEnabled = !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.RemoteDir
                }
        }

        launch {
            context.stateFlow()
                .map { it.selectedTabType }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Main)
                .collect { tab ->
                    val tree = fileTreeUI.currentState().fileTree
                    onBackPressedCallback.isEnabled = !tree.isRootFileTree() && tab == FileTransportActivity.Companion.DirTabType.RemoteDir
                }
        }


        launch {
            context.observeFloatBtnClick()
                .collect {
                    val tab = context.currentState().selectedTabType
                    val exploreFiles = fileTreeUI.getSelectedFiles().toExploreFiles()
                    if (tab == FileTransportActivity.Companion.DirTabType.RemoteDir && exploreFiles.isNotEmpty()) {
                        launch {
                            runCatching {
                                fileExplore.requestDownloadFilesSuspend(exploreFiles)
                            }.onSuccess {
                                AndroidLog.d(TAG, "Request download fails success.")
                                val mineMax = Settings.transferFileMaxConnection()
                                runCatching {
                                    (requireActivity() as FileTransportActivity)
                                        .downloadFiles(files = exploreFiles, Settings.fixTransferFileConnectionSize(min(it.maxConnection, mineMax)))
                                }
                            }.onFailure {
                                AndroidLog.e(TAG, "Request download files fail: $it", it)
                            }
                            fileTreeUI.clearSelectedFiles()
                        }
                    }
                }
        }
    }

    companion object {
        private const val TAG = "RemoteDirFragment"
    }
}