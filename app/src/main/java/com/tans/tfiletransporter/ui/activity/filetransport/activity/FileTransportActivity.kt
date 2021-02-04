package com.tans.tfiletransporter.ui.activity.filetransport.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import com.jakewharton.rxbinding3.view.clicks
import com.squareup.moshi.Types
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.FileTransportActivityBinding
import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.moshi
import com.tans.tfiletransporter.net.RemoteDevice
import com.tans.tfiletransporter.net.filetransporter.FileTransporter
import com.tans.tfiletransporter.net.filetransporter.launchFileTransport
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.net.model.ResponseFolderModelJsonAdapter
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tans.tfiletransporter.ui.activity.commomdialog.showLoadingDialog
import com.tans.tfiletransporter.ui.activity.commomdialog.showNoOptionalDialog
import com.tans.tfiletransporter.ui.activity.filetransport.MessageFragment
import com.tans.tfiletransporter.ui.activity.filetransport.MyDirFragment
import com.tans.tfiletransporter.ui.activity.filetransport.RemoteDirFragment
import com.tans.tfiletransporter.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.android.di
import org.kodein.di.android.retainedSubDI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.*
import kotlin.runCatching

class FileTransportActivity : BaseActivity<FileTransportActivityBinding, FileTransportActivityState>(R.layout.file_transport_activity, FileTransportActivityState()) {

    var remoteSeparator: String? = null
    var fileTransporter: FileTransporter? = null

    override val di: DI by retainedSubDI(di()) {
        // bind<FileTransportScopeData>() with scoped(AndroidLifecycleScope).singleton { FileTransportScopeData() }
        bind<FileTransportScopeData>() with singleton {
            val remoteSeparator = this@FileTransportActivity.remoteSeparator
            val fileTransporter = this@FileTransportActivity.fileTransporter
            FileTransportScopeData(remoteSeparator ?: FileConstants.FILE_SEPARATOR, fileTransporter!!)
        }
    }
    private val fileTransportScopeData by instance<FileTransportScopeData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val (remoteAddress, remoteInfo, isServer) = with(intent) { Triple(getRemoteAddress(), getRemoteInfo(), getIsServer()) }
        val localAddress = intent.getLocalAddress()
        binding.toolBar.title = remoteInfo
        binding.toolBar.subtitle = remoteAddress.hostAddress

        val fileTransporter = FileTransporter(
            localAddress = localAddress,
            remoteAddress = remoteAddress
        )
        this@FileTransportActivity.fileTransporter = fileTransporter

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
                        fileTransportScopeData.remoteMessageEvent.onNext(message)
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

        launch {


            val loadingDialog = showLoadingDialog(cancelable = false)
            val result = withContext(Dispatchers.IO) { runCatching { fileTransporter.whenConnectReady() } }
            loadingDialog.cancel()
            this@FileTransportActivity.remoteSeparator = result.getOrNull()

            if (result.isSuccess) {
                binding.tabLayout.addOnTabSelectedListener(object :
                    TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab?) {
                        when (tab?.position) {
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


                render({ it.selectedTabType }) {

                    when (it) {
                        DirTabType.MyDir, DirTabType.RemoteDir -> {
                            val lpCollapsing = (binding.collapsingLayout.layoutParams as? AppBarLayout.LayoutParams)
                            lpCollapsing?.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS_COLLAPSED
                            binding.collapsingLayout.layoutParams = lpCollapsing
                            val lpTab = (binding.tabLayout.layoutParams as? AppBarLayout.LayoutParams)
                            lpTab?.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
                            binding.tabLayout.layoutParams = lpTab
                        }
                        DirTabType.Message -> {
                            val lpCollapsing = (binding.collapsingLayout.layoutParams as? AppBarLayout.LayoutParams)
                            lpCollapsing?.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
                            binding.collapsingLayout.layoutParams = lpCollapsing
                            val lpTab = (binding.tabLayout.layoutParams as? AppBarLayout.LayoutParams)
                            lpTab?.scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
                            binding.tabLayout.layoutParams = lpTab
                        }
                    }

                    when (it) {
                        DirTabType.MyDir -> {
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
                    changeDirFragment(it)
                }.bindLife()
            }
        }

    }

    private fun changeDirFragment(dirTabType: DirTabType) {
        val transaction = supportFragmentManager.beginTransaction()
        binding.appbarLayout.setExpanded(true, true)
        val showFragment = when (dirTabType) {
            DirTabType.MyDir -> {
                var fragment = supportFragmentManager.findFragmentByTag(MyDirFragment.FRAGMENT_TAG)
                if (fragment == null) {
                    fragment = MyDirFragment()
                    transaction.add(R.id.fragment_container_layout, fragment, MyDirFragment.FRAGMENT_TAG)
                }
                fragment
            }

            DirTabType.RemoteDir -> {
                var fragment = supportFragmentManager.findFragmentByTag(RemoteDirFragment.FRAGMENT_TAG)
                if (fragment == null) {
                    fragment = RemoteDirFragment()
                    transaction.add(R.id.fragment_container_layout, fragment, RemoteDirFragment.FRAGMENT_TAG)
                }
                fragment
            }

            DirTabType.Message -> {
                var fragment = supportFragmentManager.findFragmentByTag(MessageFragment.FRAGMENT_TAG)
                if (fragment == null) {
                    fragment = MessageFragment()
                    transaction.add(R.id.fragment_container_layout, fragment, MessageFragment.FRAGMENT_TAG)
                }
                fragment
            }
        }
        transaction.show(showFragment)
        for (f in supportFragmentManager.fragments) {
            if (f != showFragment) { transaction.hide(f) }
        }
        transaction.commitAllowingStateLoss()
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
            i.putExtra(REMOTE_ADDRESS_EXTRA_KEY, (remoteDevice.first as InetSocketAddress).address)
            i.putExtra(REMOTE_INFO_EXTRA_KEY, remoteDevice.second)
            i.putExtra(IS_SERVER_EXTRA_KEY, asServer)
            return i
        }
    }

}

data class FileTransportActivityState(
    val selectedTabType: DirTabType = DirTabType.MyDir
)

enum class DirTabType { MyDir, RemoteDir, Message }