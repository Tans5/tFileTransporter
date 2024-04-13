package com.tans.tfiletransporter.ui.filetransport

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.AppItemLayoutBinding
import com.tans.tfiletransporter.databinding.MyAppsFragmentLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import com.tans.tfiletransporter.utils.dp2px
import com.tans.tuiutils.adapter.decoration.MarginDividerItemDecoration
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import com.tans.tuiutils.view.clicks
import com.tans.tuiutils.view.refreshes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths


class MyAppsFragment : BaseCoroutineStateFragment<MyAppsFragment.Companion.MyAppsState>(
        defaultState = MyAppsState()
) {

    override val layoutId: Int = R.layout.my_apps_fragment_layout

    private val fileExplore: FileExplore by lazy {
        (requireActivity() as FileTransportActivity).fileExplore
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        launch {
            refreshApps()
        }
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = MyAppsFragmentLayoutBinding.bind(contentView)

        viewBinding.appsRefreshLayout.setColorSchemeResources(R.color.teal_200)
        viewBinding.appsRefreshLayout.refreshes(coroutineScope = this, refreshWorkOn = Dispatchers.IO) {
            refreshApps()
        }

        viewBinding.myAppsRv.adapter = SimpleAdapterBuilderImpl<Pair<AppInfo, Boolean>>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.app_item_layout),
            dataSource = FlowDataSourceImpl(
                dataFlow = stateFlow().map { state ->
                    state.apps.map {
                        it to state.selected.contains(it)
                    }
                },
                areDataItemsTheSameParam = { d1, d2 -> d1.first.packageName == d2.first.packageName },
                areDataItemsContentTheSameParam = { d1, d2 -> d1.first.packageName == d2.first.packageName && d1.second == d2.second },
                getDataItemsChangePayloadParam = { d1, d2 -> if (d1.first.packageName == d2.first.packageName && d1.second != d2.second) Unit else null }
            ),
            dataBinder = DataBinderImpl<Pair<AppInfo, Boolean>> { data, view, _ ->
                val itemViewBinding = AppItemLayoutBinding.bind(view)
                itemViewBinding.appNameTv.text = data.first.name
                itemViewBinding.appIdTv.text = data.first.packageName
                itemViewBinding.appSizeTv.text = data.first.appSize.toSizeString()
                itemViewBinding.appIconIv.background = data.first.icon
                itemViewBinding.root.clicks(this) {
                    updateState {  oldState ->
                        val newSelected = if (oldState.selected.contains(data.first)) {
                            oldState.selected - data.first
                        } else {
                            oldState.selected + data.first
                        }
                        oldState.copy(selected = newSelected)
                    }
                }
            }.addPayloadDataBinder(Unit) { data, view, _ ->
                val itemViewBinding = AppItemLayoutBinding.bind(view)
                itemViewBinding.appCb.isChecked = data.second
            }
        ).build()

        viewBinding.myAppsRv.addItemDecoration(
            MarginDividerItemDecoration.Companion.Builder()
            .divider(MarginDividerItemDecoration.Companion.ColorDivider(requireContext().getColor(R.color.line_color),
                requireContext().dp2px(1)))
            .marginStart(requireContext().dp2px(70))
            .build()
        )

        val context = requireActivity() as FileTransportActivity

        launch {
            context.observeFloatBtnClick()
                .filter {
                    val selectedTab = context.currentState().selectedTabType
                    selectedTab == FileTransportActivity.Companion.DirTabType.MyApps
                }
                .collect {
                    launch(Dispatchers.IO) {
                        val selectedApps = currentState().selected
                        val senderFiles = selectedApps.mapNotNull {
                            val f = File(it.sourceDir)
                            if (f.canRead()) {
                                SenderFile(
                                    realFile = f,
                                    exploreFile = FileExploreFile(
                                        name = "${it.name}-${it.packageName}.apk",
                                        path = it.sourceDir,
                                        size = it.appSize,
                                        lastModify = System.currentTimeMillis()
                                    )
                                )
                            } else {
                                null
                            }
                        }
                        if (senderFiles.isNotEmpty()) {
                            runCatching {
                                fileExplore.requestSendFilesSuspend(
                                    sendFiles = senderFiles.map { it.exploreFile },
                                    maxConnection = Settings.transferFileMaxConnection()
                                )
                            }.onSuccess {
                                AndroidLog.d(TAG, "Request send apps success: $it")
                                runCatching {
                                    (requireActivity() as FileTransportActivity).sendSenderFiles(files = senderFiles)
                                }
                            }.onFailure {
                                AndroidLog.e(TAG, "Request send apps fail: $it", it)
                            }
                        }
                        updateState { it.copy(selected = emptyList()) }
                    }
                }
        }

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.myAppsRv) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom + v.paddingBottom)

            insets
        }

    }

    @SuppressLint("QueryPermissionsNeeded")
    fun refreshApps() = updateState {
        val apps = requireActivity().packageManager.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && Files.isReadable(Paths.get(it.sourceDir)) }
                .map {
                    AppInfo(
                        name = it.loadLabel(requireActivity().packageManager).toString(),
                        sourceDir = it.sourceDir,
                        packageName = it.packageName,
                        appSize = Files.size(Paths.get(it.sourceDir)),
                        icon = it.loadIcon(requireActivity().packageManager)
                    )
                }
                .sortedBy { it.name }
        MyAppsState(
                apps = apps,
                selected = emptyList()
        )
    }

    companion object {

        data class AppInfo(
            val name: String,
            val sourceDir: String,
            val packageName: String,
            val appSize: Long,
            val icon: Drawable
        )

        data class MyAppsState(
            val apps: List<AppInfo> = emptyList(),
            val selected: List<AppInfo> = emptyList()
        )

        private const val TAG = "MyAppsFragment"
    }
}