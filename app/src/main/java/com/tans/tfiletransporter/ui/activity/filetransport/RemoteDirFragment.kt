package com.tans.tfiletransporter.ui.activity.filetransport

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.addCallback
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.RemoteDirFragmentBinding
import com.tans.tfiletransporter.file.*
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestDownloadFilesSuspend
import com.tans.tfiletransporter.transferproto.fileexplore.requestScanDirSuspend
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.FileTreeUI
import kotlinx.coroutines.Dispatchers
import io.reactivex.rxjava3.kotlin.withLatestFrom
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import org.kodein.di.instance
import kotlin.jvm.optionals.getOrNull
import kotlin.math.min


class RemoteDirFragment : BaseFragment<RemoteDirFragmentBinding, Unit>(R.layout.remote_dir_fragment, Unit) {

    private var fileTreeUI: FileTreeUI? = null

    private val onBackPressedDispatcher: OnBackPressedDispatcher by instance()

    private val onBackPressedCallback: OnBackPressedCallback by lazy {
        onBackPressedDispatcher.addCallback {
            launch {
                fileTreeUI?.backPress()?.await()
            }
        }
    }

    private val fileExplore: FileExplore by instance()

    override fun initViews(binding: RemoteDirFragmentBinding) {

        val fileTreeUI = FileTreeUI(
            binding = binding.fileTreeLayout,
            rootTreeUpdater = { rxSingle {
                val handshake = (requireActivity() as FileTransportActivity).bindState()
                    .filter { it.handshake.getOrNull() != null }
                    .map { it.handshake.get() }
                    .firstOrError()
                    .await()
                val result = runCatching {
                    fileExplore.requestScanDirSuspend(handshake.remoteFileSeparator)
                }.onSuccess {
                    AndroidLog.d(TAG, "Request scan root dir success")
                }.onFailure {
                    AndroidLog.e(TAG, "Request scan root dir fail: $it", it)

                }
                result.getOrNull()?.let {
                    createRemoteRootTree(it)
                } ?: FileTree(
                    dirLeafs = emptyList(),
                    fileLeafs = emptyList(),
                    path = handshake.remoteFileSeparator,
                    parentTree = null
                )
            } },
            subTreeUpdater = { parentTree, dir ->
                rxSingle {
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
                }
            }
        )
        fileTreeUI.start()
        this.fileTreeUI = fileTreeUI

        fileTreeUI.bindState()
            .map { it.fileTree }
            .distinctUntilChanged()
            .doOnNext {
                onBackPressedCallback.isEnabled = it.parentTree != null
            }
            .bindLife()

        (requireActivity() as FileTransportActivity).bindState()
            .map { it.selectedTabType }
            .distinctUntilChanged()
            .doOnNext {
                onBackPressedCallback.isEnabled = it == FileTransportActivity.Companion.DirTabType.RemoteDir
            }
            .bindLife()

        fileTreeUI.bindState()
            .map { it.fileTree }
            .distinctUntilChanged()
            .withLatestFrom((requireActivity() as FileTransportActivity).bindState().map { it.selectedTabType })
            .doOnNext { (tree, tab) ->
                onBackPressedCallback.isEnabled = tree.parentTree != null && tab == FileTransportActivity.Companion.DirTabType.RemoteDir
            }
            .bindLife()

        (requireActivity() as FileTransportActivity).bindState()
            .map { it.selectedTabType }
            .withLatestFrom(fileTreeUI.bindState().map { it.fileTree })
            .distinctUntilChanged()
            .doOnNext { (tab, tree) ->
                onBackPressedCallback.isEnabled = tree.parentTree != null && tab == FileTransportActivity.Companion.DirTabType.RemoteDir
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
            .filter { it.first == FileTransportActivity.Companion.DirTabType.RemoteDir && it.second.isNotEmpty() }
            .map { it.second }
            .flatMapSingle { leafs ->
                rxSingle(Dispatchers.IO) {
                    val exploreFiles = leafs.toList().toExploreFiles()
                    runCatching {
                        fileExplore.requestDownloadFilesSuspend(exploreFiles)
                    }.onSuccess {
                        AndroidLog.d(TAG, "Request download fails success.")
                        val mineMax = Settings.transferFileMaxConnection().await()
                        (requireActivity() as FileTransportActivity)
                            .downloadFiles(files = exploreFiles, Settings.fixTransferFileConnectionSize(min(it.maxConnection, mineMax)))
                    }.onFailure {
                        AndroidLog.e(TAG, "Request download files fail: $it", it)
                    }
                    fileTreeUI.clearSelectedFiles().await()
                }
            }
            .bindLife()
    }

    override fun onDestroy() {
        super.onDestroy()
        fileTreeUI?.stop()
    }

    companion object {
        private const val TAG = "RemoteDirFragment"
    }
}