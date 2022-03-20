package com.tans.tfiletransporter.ui.activity.filetransport.activity

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.FileTransportActivityBinding
import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.net.connection.RemoteDevice
import com.tans.tfiletransporter.net.model.*
import com.tans.tfiletransporter.net.netty.fileexplore.FileExploreConnection
import com.tans.tfiletransporter.net.netty.fileexplore.connectToFileExploreServer
import com.tans.tfiletransporter.net.netty.fileexplore.startFileExploreServer
import com.tans.tfiletransporter.net.netty.filetransfer.defaultPathConverter
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.showLoadingDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showNoOptionalDialog
import com.tans.tfiletransporter.ui.activity.filetransport.*
import com.tans.tfiletransporter.viewpager2.FragmentStateAdapter
import io.reactivex.rxkotlin.cast
import io.reactivex.rxkotlin.ofType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import org.threeten.bp.Instant
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneId
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.runCatching
import kotlin.streams.toList

data class FileTransportActivityState(
    val selectedTabType: DirTabType = DirTabType.MyApps,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Connecting,
    val shareMyDir: Boolean = false
)

enum class DirTabType {
    MyApps,
    MyImages,
    MyDir,
    RemoteDir,
    Message
}


sealed class ConnectionStatus {
    object Connecting : ConnectionStatus()
    data class Connected(
        val localAddress: InetAddress,
        val remoteAddress: InetAddress,
        val handshakeModel: FileExploreHandshakeModel,
        val fileExploreConnection: FileExploreConnection
    ) : ConnectionStatus()
}

class FileTransportActivity : BaseActivity<FileTransportActivityBinding, FileTransportActivityState>(R.layout.file_transport_activity, FileTransportActivityState()) {

    private val fileTransportScopeData by instance<FileTransportScopeData>()

    private val fragments: Map<DirTabType, BaseFragment<*, *>> = mapOf(
        DirTabType.MyApps to MyAppsFragment(),
        DirTabType.MyImages to MyImagesFragment(),
        DirTabType.MyDir to MyDirFragment(),
        DirTabType.RemoteDir to RemoteDirFragment(),
        DirTabType.Message to MessageFragment())

    override fun DI.MainBuilder.addDIInstance() {
        bind<FileTransportScopeData>() with singleton {
            val connected = bindState().map { it.connectionStatus }
                .ofType<ConnectionStatus.Connected>()
                .firstOrError().blockingGet()
            FileTransportScopeData(
                handshakeModel = connected.handshakeModel,
                fileExploreConnection = connected.fileExploreConnection,
                localAddress = connected.localAddress,
                remoteAddress = connected.remoteAddress
            )
        }
    }

    override fun firstLaunchInitData() {
        val (remoteAddress, isServer, localAddress) = with(intent) { Triple(getRemoteAddress(), getIsServer(), getLocalAddress()) }

        launch(Dispatchers.IO) {

            val fileConnection = if (isServer) {
                startFileExploreServer(localAddress)
            } else {
                connectToFileExploreServer(remoteAddress)
            }
            val handshakeModel = fileConnection.observeConnected().await()

            updateState { oldState ->
                oldState.copy(
                    connectionStatus = ConnectionStatus.Connected(
                        localAddress = localAddress,
                        remoteAddress = remoteAddress,
                        handshakeModel = handshakeModel,
                        fileExploreConnection = fileConnection
                    )
                )
            }.await()


            fileConnection.observeRemoteFileExploreContent()
                .distinctUntilChanged()
                .doOnNext {
                    when (it) {
                        is FileExploreHandshakeModel -> {
                            fileConnection.sendFileExploreContentToRemote(
                                fileExploreContent = RequestFolderModel(
                                    requestPath = it.pathSeparator
                                )
                            )
                        }
                        is MessageModel -> {
                            val lastMessages = fileTransportScopeData.messagesEvent.firstOrError().blockingGet()
                            val message = FileTransportScopeData.Companion.Message(
                                isRemote = true,
                                timeMilli = SystemClock.uptimeMillis(),
                                message = it.message
                            )
                            fileTransportScopeData.messagesEvent.onNext(lastMessages + message)
                        }
                        is RequestFilesModel -> {
                            runBlocking(context = this.coroutineContext) {
                                val dialog = withContext(Dispatchers.Main) {
                                    showLoadingDialog()
                                }
                                fileConnection.sendFileExploreContentToRemote(
                                    fileExploreContent = ShareFilesModel(shareFiles = it.requestFiles),
                                    waitReplay = true
                                )
                                withContext(Dispatchers.Main) {
                                    dialog.cancel()
                                    val result = kotlin.runCatching {
                                        startSendingFiles(
                                            files = it.requestFiles,
                                            localAddress = localAddress,
                                            pathConverter = defaultPathConverter
                                        ).await()
                                    }
                                    if (result.isFailure) {
                                        Log.e("SendingFileError", "SendingFileError", result.exceptionOrNull())
                                    }
                                }
                            }

                        }
                        is RequestFolderModel -> {
                            val shareFolder = bindState().firstOrError().blockingGet().shareMyDir
                            val parentPath = it.requestPath
                            val path = Paths.get(FileConstants.homePathString + parentPath)
                            val children = if (shareFolder && Files.isReadable(path)) {
                                Files.list(path)
                                    .filter { Files.isReadable(it) }
                                    .map { p ->
                                        val name = p.fileName.toString()
                                        val lastModify = OffsetDateTime.ofInstant(
                                            Instant.ofEpochMilli(
                                                Files.getLastModifiedTime(p).toMillis()
                                            ), ZoneId.systemDefault()
                                        )
                                        val pathString =
                                            if (parentPath.endsWith(FileConstants.FILE_SEPARATOR)) parentPath + name else parentPath + FileConstants.FILE_SEPARATOR + name
                                        if (Files.isDirectory(p)) {
                                            Folder(
                                                name = name,
                                                path = pathString,
                                                childCount = p.let {
                                                    val s = Files.list(it)
                                                    val size = s.count()
                                                    s.close()
                                                    size
                                                },
                                                lastModify = lastModify
                                            )
                                        } else {
                                            File(
                                                name = name,
                                                path = pathString,
                                                size = Files.size(p),
                                                lastModify = lastModify
                                            )
                                        }
                                    }.toList()

                            } else {
                                emptyList()
                            }
                            fileConnection.sendFileExploreContentToRemote(
                                fileExploreContent = ShareFolderModel(
                                    path = parentPath,
                                    childrenFolders = children.filterIsInstance<Folder>(),
                                    childrenFiles = children.filterIsInstance<File>()
                                )
                            )
                        }
                        is ShareFilesModel -> {
                            val result = runCatching {
                                val unit = startDownloadingFiles(it.shareFiles, remoteAddress).blockingGet()
                            }
                            if (result.isFailure) {
                                Log.e(
                                    "Download Files Fail",
                                    "Download Files Fail",
                                    result.exceptionOrNull()
                                )
                            }
                        }
                        is ShareFolderModel -> {
                            fileTransportScopeData.remoteFolderModelEvent.onNext(ResponseFolderModel(
                                path = it.path,
                                childrenFolders = it.childrenFolders,
                                childrenFiles = it.childrenFiles
                            ))
                        }
                    }
                }
                .ignoreElements()
                .await()

            fileConnection.observeDisconnected().await()
            withContext(Dispatchers.Main) {
                showNoOptionalDialog(
                        title = getString(R.string.connection_error_title),
                        message = getString(R.string.connection_error_message),
                        cancelable = true
                ).await()
                finish()
            }
        }
    }

    override fun initViews(binding: FileTransportActivityBinding) {
        launch {
            val (remoteInfo, remoteAddress) = with(intent) { getRemoteInfo() to getRemoteAddress() }

            binding.toolBar.title = remoteInfo
            binding.toolBar.subtitle = remoteAddress.hostAddress

            val loadingDialog = showLoadingDialog(cancelable = false)
            withContext(Dispatchers.IO) {
                bindState()
                .map { it.connectionStatus }
                .filter { it is ConnectionStatus.Connected }
                .cast<ConnectionStatus.Connected>()
                .firstOrError()
                .await()
            }
            loadingDialog.cancel()

            render({ it.shareMyDir }) { binding.toolBar.menu.findItem(R.id.share_my_folder).isChecked = it }.bindLife()

            binding.viewPager.adapter = object : FragmentStateAdapter(this@FileTransportActivity) {
                override fun getItemCount(): Int = fragments.size
                override fun createFragment(position: Int): Fragment = fragments[DirTabType.values()[position]]!!
            }

            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.text = when (DirTabType.values()[position]) {
                    DirTabType.MyApps -> getString(R.string.file_transport_activity_tab_my_apps)
                    DirTabType.MyImages -> getString(R.string.file_transport_activity_tab_my_images)
                    DirTabType.MyDir -> getString(R.string.file_transport_activity_tab_my_dir)
                    DirTabType.RemoteDir -> getString(R.string.file_transport_activity_tab_remote_dir)
                    DirTabType.Message -> getString(R.string.file_transport_activity_tab_message)
                }
            }.attach()

            binding.toolBar.setOnMenuItemClickListener {
                if (it.itemId == R.id.share_my_folder) {
                    updateState { oldState ->
                        oldState.copy(shareMyDir = !oldState.shareMyDir)
                    }.bindLife()
                    true
                } else {
                    false
                }
            }

            binding.tabLayout.addOnTabSelectedListener(object :
                    TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.position) {
                        DirTabType.MyApps.ordinal -> updateStateCompletable { it.copy(selectedTabType = DirTabType.MyApps) }.bindLife()
                        DirTabType.MyImages.ordinal -> updateStateCompletable { it.copy(selectedTabType = DirTabType.MyImages) }.bindLife()
                        DirTabType.MyDir.ordinal -> updateStateCompletable { it.copy(selectedTabType = DirTabType.MyDir) }.bindLife()
                        DirTabType.RemoteDir.ordinal -> updateStateCompletable { it.copy(selectedTabType = DirTabType.RemoteDir) }.bindLife()
                        DirTabType.Message.ordinal -> updateStateCompletable { it.copy(selectedTabType = DirTabType.Message) }.bindLife()
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {
                }

                override fun onTabReselected(tab: TabLayout.Tab?) {
                }
            })

            binding.floatingActionBt.clicks()
                    .doOnNext { fileTransportScopeData.floatBtnEvent.onNext(Unit) }
                    .bindLife()
            render({ it.connectionStatus }) {
                when (it) {
                    ConnectionStatus.Connecting -> {
                        binding.toolBar.title = ""
                        binding.toolBar.subtitle = ""
                    }
                    is ConnectionStatus.Connected -> {
                        binding.toolBar.title = it.handshakeModel.deviceName
                        binding.toolBar.subtitle = it.remoteAddress.hostAddress
                    }
                }
            }.bindLife()

            render({ it.selectedTabType }) {

                when (it) {
                    DirTabType.MyApps, DirTabType.MyImages, DirTabType.MyDir, DirTabType.RemoteDir -> {
                        val lpCollapsing = (binding.collapsingLayout.layoutParams as? AppBarLayout.LayoutParams)
                        lpCollapsing?.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED
                        binding.collapsingLayout.layoutParams = lpCollapsing
                    }
                    DirTabType.Message -> {
                        val lpCollapsing = (binding.collapsingLayout.layoutParams as? AppBarLayout.LayoutParams)
                        lpCollapsing?.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
                        binding.collapsingLayout.layoutParams = lpCollapsing
                    }
                }

                when (it) {
                    DirTabType.MyApps, DirTabType.MyImages, DirTabType.MyDir -> {
                        binding.floatingActionBt.setImageResource(R.drawable.share_variant_outline)
                        binding.floatingActionBt.visibility = View.VISIBLE
                    }
                    DirTabType.RemoteDir -> {
                        binding.floatingActionBt.setImageResource(R.drawable.download_outline)
                        binding.floatingActionBt.visibility = View.VISIBLE
                    }
                    DirTabType.Message -> {
                        binding.floatingActionBt.visibility = View.GONE
                    }
                }
                binding.appbarLayout.setExpanded(true, true)
            }.bindLife()
        }
    }

    override fun onBackPressed() {
        launch {
            val tabType = withContext(Dispatchers.IO) { bindState().firstOrError().map { it.selectedTabType }.await() }
            if (fragments[tabType]?.onBackPressed() != true) {
                finish()
            }
        }
    }

    companion object {

        private const val LOCAL_ADDRESS_EXTRA_KEY = "local_address_extra_key"
        private const val REMOTE_ADDRESS_EXTRA_KEY = "remote_address_extra_key"
        private const val REMOTE_INFO_EXTRA_KEY = "remote_info_extra_key"
        private const val IS_SERVER_EXTRA_KEY = "is_server_extra_key"

        private fun Intent.getLocalAddress(): InetAddress = getSerializableExtra(LOCAL_ADDRESS_EXTRA_KEY) as? InetAddress ?: error("FileTransportActivity get local address fail.")

        private fun Intent.getRemoteAddress(): InetAddress = getSerializableExtra(REMOTE_ADDRESS_EXTRA_KEY) as? InetAddress ?: error("FileTransportActivity get remote address fail.")

        private fun Intent.getRemoteInfo(): String = getStringExtra(REMOTE_INFO_EXTRA_KEY) ?: ""

        private fun Intent.getIsServer(): Boolean = getBooleanExtra(IS_SERVER_EXTRA_KEY, false)

        fun getIntent(context: Context,
                      localAddress: InetAddress,
                      remoteDevice: RemoteDevice,
                      asServer: Boolean): Intent {
            val i = Intent(context, FileTransportActivity::class.java)
            i.putExtra(LOCAL_ADDRESS_EXTRA_KEY, localAddress)
            i.putExtra(REMOTE_ADDRESS_EXTRA_KEY, remoteDevice.first)
            i.putExtra(REMOTE_INFO_EXTRA_KEY, remoteDevice.second)
            i.putExtra(IS_SERVER_EXTRA_KEY, asServer)
            return i
        }
    }

}