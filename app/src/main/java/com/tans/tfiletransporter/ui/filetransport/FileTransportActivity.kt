package com.tans.tfiletransporter.ui.filetransport

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.FileTransportActivityBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.FileExploreObserver
import com.tans.tfiletransporter.transferproto.fileexplore.FileExploreRequestHandler
import com.tans.tfiletransporter.transferproto.fileexplore.FileExploreState
import com.tans.tfiletransporter.transferproto.fileexplore.Handshake
import com.tans.tfiletransporter.transferproto.fileexplore.bindSuspend
import com.tans.tfiletransporter.transferproto.fileexplore.connectSuspend
import com.tans.tfiletransporter.transferproto.fileexplore.handshakeSuspend
import com.tans.tfiletransporter.transferproto.fileexplore.model.DownloadFilesReq
import com.tans.tfiletransporter.transferproto.fileexplore.model.DownloadFilesResp
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirReq
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirResp
import com.tans.tfiletransporter.transferproto.fileexplore.model.SendFilesReq
import com.tans.tfiletransporter.transferproto.fileexplore.model.SendFilesResp
import com.tans.tfiletransporter.transferproto.fileexplore.model.SendMsgReq
import com.tans.tfiletransporter.transferproto.fileexplore.waitClose
import com.tans.tfiletransporter.transferproto.fileexplore.waitHandshake
import com.tans.tfiletransporter.file.scanChildren
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import com.tans.tfiletransporter.ui.commomdialog.loadingDialogSuspend
import com.tans.tfiletransporter.ui.commomdialog.showNoOptionalDialogSuspend
import com.tans.tfiletransporter.ui.commomdialog.showOptionalDialogSuspend
import com.tans.tfiletransporter.ui.commomdialog.showSettingsDialog
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.io.File
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min


@SystemBarStyle(statusBarThemeStyle = 1, navigationBarThemeStyle = 1)
class FileTransportActivity : BaseCoroutineStateActivity<FileTransportActivity.Companion.FileTransportActivityState>(
    defaultState = FileTransportActivityState()
) {

    override val layoutId: Int = R.layout.file_transport_activity


    private val activityContainer: AtomicReference<WeakReference<FileTransportActivity?>?> by lazyViewModelField("activityContainer") {
        AtomicReference(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        activityContainer.set(WeakReference(this))
        super.onCreate(savedInstanceState)
    }

    private val floatActionBtnClickEvent: MutableSharedFlow<Unit> by lazyViewModelField("floatActionBtnClickEvent") {
        MutableSharedFlow(onBufferOverflow = BufferOverflow.DROP_OLDEST, extraBufferCapacity = 1)
    }

    /**
     * Remote device request scan current device's dir.
     */
    private val scanDirRequest: FileExploreRequestHandler<ScanDirReq, ScanDirResp> by lazyViewModelField("scanDirRequest") {
        object : FileExploreRequestHandler<ScanDirReq, ScanDirResp> {
            override fun onRequest(isNew: Boolean, request: ScanDirReq): ScanDirResp {
                return if (Settings.isShareMyDir()) {
                    request.scanChildren(this@FileTransportActivity)
                } else {
                    ScanDirResp(
                        path = request.requestPath,
                        childrenDirs = emptyList(),
                        childrenFiles = emptyList()
                    )
                }
            }
        }
    }

    /**
     * Remote device notify to download files.
     */
    private val sendFilesRequest: FileExploreRequestHandler<SendFilesReq, SendFilesResp> by lazyViewModelField("sendFilesRequest") {
        object : FileExploreRequestHandler<SendFilesReq, SendFilesResp> {
            override fun onRequest(isNew: Boolean, request: SendFilesReq): SendFilesResp {
                if (isNew) {
                    dataCoroutineScope.launch {
                        val mineMax = Settings.transferFileMaxConnection()
                        val act = activityContainer.get()?.get()
                        act?.downloadFiles(
                            request.sendFiles,
                            Settings.fixTransferFileConnectionSize(min(request.maxConnection, mineMax))
                        )
                    }
                }
                return SendFilesResp(bufferSize = 512 * 1024)
            }
        }
    }


    /**
     * Remote device notify to send files.
     */
    private val downloadFilesRequest: FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp> by lazyViewModelField("downloadFilesRequest") {
        object : FileExploreRequestHandler<DownloadFilesReq, DownloadFilesResp> {
            override fun onRequest(isNew: Boolean, request: DownloadFilesReq): DownloadFilesResp {
                if (isNew) {
                    dataCoroutineScope.launch {
                        val act = activityContainer.get()?.get()
                        act?.sendFiles(request.downloadFiles)
                    }
                }
                return DownloadFilesResp(maxConnection = Settings.transferFileMaxConnection())
            }
        }
    }

    val fileExplore: FileExplore by lazyViewModelField("fileExplore") {
        FileExplore(
            log = AndroidLog,
            scanDirRequest = scanDirRequest,
            sendFilesRequest = sendFilesRequest,
            downloadFileRequest = downloadFilesRequest
        )
    }


    private val fragments: Map<DirTabType, Fragment> by lazyViewModelField("fragments") {
        mapOf(
            DirTabType.MyApps to MyAppsFragment(),
            DirTabType.MyImages to MyImagesFragment(),
            DirTabType.MyVideos to MyVideosFragment(),
            DirTabType.MyAudios to MyAudiosFragment(),
            DirTabType.MyDir to MyDirFragment(),
            DirTabType.RemoteDir to RemoteDirFragment(),
            DirTabType.Message to MessageFragment()
        )
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        val (remoteAddress, isServer, localAddress) = with(intent) { Triple(getRemoteAddress(), getIsServer(), getLocalAddress()) }


        launch(Dispatchers.IO) {
            if (isServer) {
                // Server
                AndroidLog.d(TAG, "Start bind address: $localAddress")
                runCatching {
                    withTimeout(5000L) {
                        fileExplore.bindSuspend(address = localAddress)
                    }
                }
            } else {
                // Client, client retry 3 times.
                AndroidLog.d(TAG, "Start connect address: $remoteAddress")
                var connectTimes = 3
                var connectResult: Result<Unit>
                do {
                    delay(200)
                    connectResult = runCatching {
                        fileExplore.connectSuspend(remoteAddress)
                    }
                    if (connectResult.isSuccess) { break }
                } while (--connectTimes > 0)
                connectResult
            }
                .onSuccess {
                    // Create connection success.
                    AndroidLog.d(TAG, "Create connection success!!")
                    AndroidLog.d(TAG, "Start handshake.")

                    // Handshake, client request handshake, server wait handshake.
                    if (isServer) {
                        runCatching {
                            withTimeout(3000L) {
                                fileExplore.waitHandshake()
                            }
                        }
                    } else {
                        runCatching {
                            fileExplore.handshakeSuspend()
                        }
                    }
                        .onSuccess { handshake ->
                            AndroidLog.d(TAG, "Handshake success!!")
                            updateState { s -> s.copy(connectionStatus = ConnectionStatus.Connected(handshake = handshake)) }

                            fileExplore.addObserver(object : FileExploreObserver {
                                override fun onNewState(state: FileExploreState) {}
                                // New message coming.
                                override fun onNewMsg(msg: SendMsgReq) {
                                    updateNewMessage(
                                        Message(
                                            time = msg.sendTime,
                                            msg = msg.msg,
                                            fromRemote = true
                                        )
                                    )
                                }
                            })
                            // Waiting connection close.
                            fileExplore.waitClose()
                            updateState { s -> s.copy(connectionStatus = ConnectionStatus.Closed) }
                        }
                        .onFailure {
                            AndroidLog.e(TAG, "Handshake fail: ${it.message}", it)
                            updateState { s -> s.copy(connectionStatus = ConnectionStatus.Closed) }
                        }
                }
                .onFailure {
                    // Create connection fail.
                    AndroidLog.e(TAG, "Create connection fail: ${it.message}", it)
                    updateState { s -> s.copy(connectionStatus = ConnectionStatus.Closed) }
                }
        }
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = FileTransportActivityBinding.bind(contentView)

        // Loading dialog.
        launch {
            this@FileTransportActivity.supportFragmentManager.loadingDialogSuspend {
                stateFlow().map { it.connectionStatus }.first { it != ConnectionStatus.Connecting }
            }
        }

        // Connection close dialog.
        launch {
            stateFlow().map { it.connectionStatus }.first { it == ConnectionStatus.Closed }
            this@FileTransportActivity.supportFragmentManager.showNoOptionalDialogSuspend(
                title = getString(R.string.connection_error_title),
                message = getString(R.string.connection_error_message)
            )
            finish()
        }

        launch {
            stateFlow().map { it.connectionStatus }.first { it is ConnectionStatus.Connected }

            // Fragments' ViewPager
            val (remoteInfo, remoteAddress) = with(intent) { getRemoteInfo() to getRemoteAddress() }
            viewBinding.toolBar.title = remoteInfo
            viewBinding.toolBar.subtitle = remoteAddress.hostAddress
            viewBinding.viewPager.adapter = object : FragmentStateAdapter(this@FileTransportActivity) {
                override fun getItemCount(): Int = fragments.size
                override fun createFragment(position: Int): Fragment = fragments[DirTabType.entries[position]]!!
            }
            viewBinding.viewPager.offscreenPageLimit = fragments.size
            TabLayoutMediator(viewBinding.tabLayout, viewBinding.viewPager) { tab, position ->
                tab.text = when (DirTabType.entries[position]) {
                    DirTabType.MyApps -> getString(R.string.file_transport_activity_tab_my_apps)
                    DirTabType.MyImages -> getString(R.string.file_transport_activity_tab_my_images)
                    DirTabType.MyVideos -> getString(R.string.file_transport_activity_tab_my_videos)
                    DirTabType.MyAudios -> getString(R.string.file_transport_activity_tab_my_audios)
                    DirTabType.MyDir -> getString(R.string.file_transport_activity_tab_my_dir)
                    DirTabType.RemoteDir -> getString(R.string.file_transport_activity_tab_remote_dir)
                    DirTabType.Message -> getString(R.string.file_transport_activity_tab_message)
                }
            }.attach()
            viewBinding.tabLayout.addOnTabSelectedListener(object :
                TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.position) {
                        DirTabType.MyApps.ordinal -> updateState { it.copy(selectedTabType = DirTabType.MyApps) }
                        DirTabType.MyImages.ordinal -> updateState { it.copy(selectedTabType = DirTabType.MyImages) }
                        DirTabType.MyVideos.ordinal -> updateState { it.copy(selectedTabType = DirTabType.MyVideos) }
                        DirTabType.MyAudios.ordinal -> updateState { it.copy(selectedTabType = DirTabType.MyAudios) }
                        DirTabType.MyDir.ordinal -> updateState { it.copy(selectedTabType = DirTabType.MyDir) }
                        DirTabType.RemoteDir.ordinal -> updateState { it.copy(selectedTabType = DirTabType.RemoteDir) }
                        DirTabType.Message.ordinal -> updateState { it.copy(selectedTabType = DirTabType.Message) }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {
                }

                override fun onTabReselected(tab: TabLayout.Tab?) {
                }
            })

            // Setting's dialog.
            viewBinding.toolBar.menu.findItem(R.id.settings).setOnMenuItemClickListener {
                this@FileTransportActivity.supportFragmentManager.showSettingsDialog()
                true
            }

            // Update Appbar UI
            renderStateNewCoroutine({ it.selectedTabType }) {
                when (it) {
                    DirTabType.MyApps, DirTabType.MyImages, DirTabType.MyVideos, DirTabType.MyAudios, DirTabType.MyDir, DirTabType.RemoteDir -> {
                        val lpCollapsing = (viewBinding.collapsingLayout.layoutParams as? AppBarLayout.LayoutParams)
                        lpCollapsing?.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                        viewBinding.collapsingLayout.layoutParams = lpCollapsing
                    }
                    DirTabType.Message -> {
                        val lpCollapsing = (viewBinding.collapsingLayout.layoutParams as? AppBarLayout.LayoutParams)
                        lpCollapsing?.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
                        viewBinding.collapsingLayout.layoutParams = lpCollapsing
                    }
                }

                when (it) {
                    DirTabType.MyApps, DirTabType.MyImages, DirTabType.MyVideos, DirTabType.MyAudios, DirTabType.MyDir -> {
                        viewBinding.floatingActionBt.setImageResource(R.drawable.share_variant_outline)
                        viewBinding.floatingActionBt.visibility = View.VISIBLE
                    }
                    DirTabType.RemoteDir -> {
                        viewBinding.floatingActionBt.setImageResource(R.drawable.download_outline)
                        viewBinding.floatingActionBt.visibility = View.VISIBLE
                    }
                    DirTabType.Message -> {
                        viewBinding.floatingActionBt.visibility = View.GONE
                    }
                }
                viewBinding.appbarLayout.setExpanded(true, true)
            }


            // FAB clicks.
            viewBinding.floatingActionBt.clicks(this) {
                floatActionBtnClickEvent.emit(Unit)
            }
        }

        launch {
            stateFlow().map { it.connectionStatus }.first { it is ConnectionStatus.Connected }
            val requestShareFiles = intent.getRequestShareFiles()
            if (requestShareFiles.isNotEmpty()) {
                val share = supportFragmentManager.showOptionalDialogSuspend(
                    title = getString(R.string.request_share_title),
                    message = getString(R.string.request_share_body, requestShareFiles.size),
                    positiveButtonText = getString(R.string.request_share_positive),
                    negativeButtonText = getString(R.string.request_share_negative)
                )
                if (share == true) {
                    val exploreFiles = requestShareFiles.map {
                        val f = File(it)
                        FileExploreFile(
                            name = f.name,
                            path = f.path,
                            size = f.length(),
                            lastModify = f.lastModified()
                        )
                    }
                    runCatching {
                        withContext(Dispatchers.IO) {
                            fileExplore.requestSendFilesSuspend(exploreFiles, Settings.transferFileMaxConnection())
                        }
                    }.onSuccess {
                        AndroidLog.d(TAG, "Request send files success: $it")
                        runCatching {
                            sendFiles(exploreFiles)
                        }
                    }.onFailure {
                        AndroidLog.e(TAG, "Request send files fail: $it", it)
                    }
                }
            }
        }
    }

    fun observeFloatBtnClick(): Flow<Unit> = floatActionBtnClickEvent

    fun observeMessages(): Flow<List<Message>> = stateFlow.map { it.messages }.distinctUntilChanged()

    fun updateNewMessage(msg: Message) {
        updateState { it.copy(messages = it.messages + msg) }
    }

    private val fileTransferMutex: Mutex by lazy {
        Mutex(false)
    }

    suspend fun sendFiles(files: List<FileExploreFile>) {
        val fixedFiles = files.filter { it.size > 0 }
        val senderFiles = fixedFiles.map { SenderFile( File(it.path), it) }
        if (senderFiles.isEmpty()) return
        sendSenderFiles(senderFiles)
    }

    suspend fun sendSenderFiles(files: List<SenderFile>) {
        if (files.isEmpty()) return
        if (fileTransferMutex.isLocked) return
        fileTransferMutex.lock()
        val result = withContext(Dispatchers.Main) {
            supportFragmentManager.showFileSenderDialog(
                bindAddress = intent.getLocalAddress(),
                files = files
            )
        }
        if (result is FileTransferResult.Error) {
            withContext(Dispatchers.Main) {
                this@FileTransportActivity.supportFragmentManager.showNoOptionalDialogSuspend(
                    title = getString(R.string.file_transfer_error_title),
                    message = result.msg
                )
            }
        }
        fileTransferMutex.unlock()
    }

    suspend fun downloadFiles(files: List<FileExploreFile>, maxConnection: Int) {
        val fixedFiles = files.filter { it.size > 0 }
        if (fixedFiles.isEmpty()) return
        if (fileTransferMutex.isLocked) return
        fileTransferMutex.lock()
        delay(150L)
        val result = withContext(Dispatchers.Main) {
            this@FileTransportActivity.supportFragmentManager.showFileDownloaderDialog(
                senderAddress = intent.getRemoteAddress(),
                files = fixedFiles,
                downloadDir = File(Settings.getDownloadDir()),
                maxConnectionSize = maxConnection
            )
        }
        if (result is FileTransferResult.Error) {
            withContext(Dispatchers.Main) {
                this@FileTransportActivity.supportFragmentManager.showNoOptionalDialogSuspend(
                    title = getString(R.string.file_transfer_error_title),
                    message = result.msg
                )
            }
        }
        fileTransferMutex.unlock()
    }

    override fun onViewModelCleared() {
        super.onViewModelCleared()
        fileExplore.closeConnectionIfActive()
    }

    companion object {

        private const val TAG = "FileTransporterActivity"

        private const val LOCAL_ADDRESS_EXTRA_KEY = "local_address_extra_key"
        private const val REMOTE_ADDRESS_EXTRA_KEY = "remote_address_extra_key"
        private const val REMOTE_INFO_EXTRA_KEY = "remote_info_extra_key"
        private const val IS_SERVER_EXTRA_KEY = "is_server_extra_key"
        private const val REQUEST_SHARE_FILES = "request_share_files_key"

        @Suppress("DEPRECATION")
        private fun Intent.getLocalAddress(): InetAddress = getSerializableExtra(
            LOCAL_ADDRESS_EXTRA_KEY
        ) as? InetAddress ?: error("FileTransportActivity get local address fail.")

        @Suppress("DEPRECATION")
        private fun Intent.getRemoteAddress(): InetAddress = getSerializableExtra(
            REMOTE_ADDRESS_EXTRA_KEY,
        ) as? InetAddress ?: error("FileTransportActivity get remote address fail.")

        private fun Intent.getRemoteInfo(): String = getStringExtra(REMOTE_INFO_EXTRA_KEY) ?: ""

        private fun Intent.getIsServer(): Boolean = getBooleanExtra(IS_SERVER_EXTRA_KEY, false)

        private fun Intent.getRequestShareFiles(): List<String> = getStringArrayListExtra(REQUEST_SHARE_FILES) ?: emptyList()

        data class Message(
            val time: Long,
            val msg: String,
            val fromRemote: Boolean
        )

        enum class DirTabType {
            MyApps,
            MyImages,
            MyVideos,
            MyAudios,
            MyDir,
            RemoteDir,
            Message
        }

        sealed class ConnectionStatus {
            data object Connecting : ConnectionStatus()

            data class Connected(val handshake: Handshake) : ConnectionStatus()

            data object Closed : ConnectionStatus()
        }

        data class FileTransportActivityState(
            val selectedTabType: DirTabType = DirTabType.MyApps,
            val connectionStatus: ConnectionStatus = ConnectionStatus.Connecting,
            val messages: List<Message> = emptyList()
        )

        fun getIntent(context: Context,
                      localAddress: InetAddress,
                      remoteAddress: InetAddress,
                      remoteDeviceInfo: String,
                      isServer: Boolean,
                      requestShareFiles: List<String>): Intent {
            val i = Intent(context, FileTransportActivity::class.java)
            i.putExtra(LOCAL_ADDRESS_EXTRA_KEY, localAddress)
            i.putExtra(REMOTE_ADDRESS_EXTRA_KEY, remoteAddress)
            i.putExtra(REMOTE_INFO_EXTRA_KEY, remoteDeviceInfo)
            i.putExtra(IS_SERVER_EXTRA_KEY, isServer)
            i.putStringArrayListExtra(REQUEST_SHARE_FILES, requestShareFiles as? ArrayList<String>)
            return i
        }
    }

}