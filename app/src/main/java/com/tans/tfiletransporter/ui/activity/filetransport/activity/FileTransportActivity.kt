package com.tans.tfiletransporter.ui.activity.filetransport.activity

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.jakewharton.rxbinding3.view.clicks
import com.squareup.moshi.Types
import com.tans.rxutils.switchThread
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.FileTransportActivityBinding
import com.tans.tfiletransporter.moshi
import com.tans.tfiletransporter.net.connection.RemoteDevice
import com.tans.tfiletransporter.net.filetransporter.FileTransporter
import com.tans.tfiletransporter.net.filetransporter.launchFileTransport
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.net.model.ResponseFolderModelJsonAdapter
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.showLoadingDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showNoOptionalDialog
import com.tans.tfiletransporter.ui.activity.filetransport.*
import com.tans.tfiletransporter.utils.*
import com.tans.tfiletransporter.viewpager2.FragmentStateAdapter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.cast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import java.net.InetAddress
import java.util.*
import kotlin.runCatching

data class FileTransportActivityState(
    val selectedTabType: DirTabType = DirTabType.MyApps,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Connecting
)

enum class DirTabType(val tabName: String) {
    MyApps("MY APPS"),
    MyImages("MY IMAGES"),
    MyDir("MY FOLDER"),
    RemoteDir("REMOTE FOLDER"),
    Message("MESSAGE")
}


sealed class ConnectionStatus {
    object Connecting : ConnectionStatus()
    data class Connected(
        val remoteAddress: InetAddress,
        val remoteDeviceInfo: String,
        val remoteFileSeparator: String,
        val fileTransporter: FileTransporter
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
            val connected = bindState().firstOrError().map { it.connectionStatus as ConnectionStatus.Connected }.blockingGet()
            FileTransportScopeData(
                remoteDirSeparator = connected.remoteFileSeparator,
                fileTransporter = connected.fileTransporter
            )
        }
    }

    override fun firstLaunchInitData() {
        val (remoteAddress, isServer, localAddress) = with(intent) { Triple(getRemoteAddress(), getIsServer(), getLocalAddress()) }

        val fileTransporter = FileTransporter(
                localAddress = localAddress,
                remoteAddress = remoteAddress
        )
        launch(Dispatchers.IO) {
            val result = runCatching {

                fileTransporter.launchFileTransport(isServer) {

                    requestFolderChildrenShareChain { _, inputStream, limit, _ ->
                        val string = inputStream.readString(limit)
                        fileTransporter.writerHandleChannel.send(newFolderChildrenShareWriterHandle(string))
                    }

                    folderChildrenShareChain { _, inputStream, limit, _ ->
                        val string = inputStream.readString(limit)
                        val folderModel = ResponseFolderModelJsonAdapter(moshi).fromJson(string)
                        if (folderModel != null) {
                            fileTransportScopeData.remoteFolderModelEvent.onNext(folderModel)
                        }
                    }

                    requestFilesShareChain { _, inputStream, limit, _ ->
                        val string = inputStream.readString(limit)
                        val moshiType = Types.newParameterizedType(List::class.java, File::class.java)
                        val files = moshi.adapter<List<File>>(moshiType).fromJson(string)
                        if (files != null) {
                            fileTransporter.writerHandleChannel.send(newFilesShareWriterHandle(files))
                        }
                    }


                    filesShareDownloader { files, remoteAddress ->
                        withContext(Dispatchers.Main) {
                            val result = runCatching {
                                startDownloadingFiles(files, remoteAddress).await()
                            }
                            if (result.isFailure) {
                                Log.e("Download Files Fail", "Download Files Fail", result.exceptionOrNull())
                            }
                        }
                        true
                    }

                    sendMessageChain { _, inputStream, limit, _ ->
                        val message = inputStream.readString(limit)
                        val newMessage = FileTransportScopeData.Companion.Message(
                            isRemote = true,
                            message = message,
                            timeMilli = System.currentTimeMillis()
                        )
                        val messages: List<FileTransportScopeData.Companion.Message> = fileTransportScopeData.messagesEvent.firstOrError().await()
                        fileTransportScopeData.messagesEvent.onNext(messages + newMessage)
                    }
                }
            }
            Log.e(this@FileTransportActivity::javaClass.name,"FileConnectionBreak", result.exceptionOrNull())
            withContext(Dispatchers.Main) {
                showNoOptionalDialog(
                        title = getString(R.string.connection_error_title),
                        message = getString(R.string.connection_error_message),
                        cancelable = true
                ).await()
                finish()
            }
        }

        launch(Dispatchers.IO) {
            val remoteSeparator = fileTransporter.whenConnectReady()
            val remoteDeviceInfo = intent.getRemoteInfo()
            updateState { oldState -> oldState.copy(connectionStatus = ConnectionStatus.Connected(
                remoteAddress = remoteAddress,
                remoteDeviceInfo = remoteDeviceInfo,
                remoteFileSeparator = remoteSeparator,
                fileTransporter = fileTransporter
            )) }.await()
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

            binding.viewPager.adapter = object : FragmentStateAdapter(this@FileTransportActivity) {
                override fun getItemCount(): Int = fragments.size
                override fun createFragment(position: Int): Fragment = fragments[DirTabType.values()[position]]!!
            }

            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position -> tab.text = DirTabType.values()[position].tabName }.attach()

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
                        binding.toolBar.title = it.remoteDeviceInfo
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