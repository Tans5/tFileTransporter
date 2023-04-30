package com.tans.tfiletransporter.ui.activity.filetransport

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.recyclerviewutils.MarginDividerItemDecoration
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.AppItemLayoutBinding
import com.tans.tfiletransporter.databinding.MyAppsFragmentLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import com.tans.tfiletransporter.ui.DataBindingAdapter
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.utils.dp2px
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import org.kodein.di.instance
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths


class MyAppsFragment : BaseFragment<MyAppsFragmentLayoutBinding, MyAppsFragment.Companion.MyAppsState>(
        layoutId = R.layout.my_apps_fragment_layout,
        default = MyAppsState()
) {

    private val fileExplore: FileExplore by instance()

    override fun initViews(binding: MyAppsFragmentLayoutBinding) {

        refreshApps().subscribeOn(Schedulers.io()).bindLife()

        binding.appsRefreshLayout.refreshes()
                .switchMapSingle {
                    refreshApps()
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .doFinally {
                                if (binding.appsRefreshLayout.isRefreshing) { binding.appsRefreshLayout.isRefreshing = false }
                            }
                }
                .bindLife()

        binding.myAppsRv.adapter = SimpleAdapterSpec<Pair<AppInfo, Boolean>, AppItemLayoutBinding>(
                layoutId = R.layout.app_item_layout,
                dataUpdater = bindState().map { state -> state.apps.map { it to state.selected.contains(it) } },
                bindData = { _, (app, select), lBinding ->
                    lBinding.appNameTv.text = app.name
                    lBinding.appIdTv.text = app.packageName
                    DataBindingAdapter.fileSizeText(lBinding.appSizeTv, app.appSize)
                    lBinding.appCb.isChecked = select
                    lBinding.appIconIv.background = app.icon
                },
                differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1.first.packageName == d2.first.packageName },
                        contentTheSame = { d1, d2 -> d1.first.packageName == d2.first.packageName && d1.second == d2.second },
                        changePayLoad = { d1, d2 ->
                            if (d1.first.packageName == d2.first.packageName && d1.second != d2.second) {
                                AppSelectChange
                            } else {
                                null
                            }
                        }),
                bindDataPayload = { _, (_, isSelect), lBinding, payloads ->
                    if (payloads.contains(AppSelectChange)) {
                        lBinding.appCb.isChecked = isSelect
                        true
                    } else {
                        false
                    }
                },
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, (app, isSelect) ->
                        updateState { oldState ->
                            val oldSelect = oldState.selected
                            val newSelect = if (isSelect) oldSelect - app else oldSelect + app
                            oldState.copy(selected = newSelect)
                        }.map { }
                    }
                }
        ).toAdapter()

        binding.myAppsRv.addItemDecoration(MarginDividerItemDecoration.Companion.Builder()
                .divider(MarginDividerItemDecoration.Companion.ColorDivider(requireContext().getColor(R.color.line_color),
                        requireContext().dp2px(1)))
                .marginStart(requireContext().dp2px(70))
                .build()
        )

        (requireActivity() as FileTransportActivity).observeFloatBtnClick()
            .flatMapSingle {
                (activity as FileTransportActivity).bindState().map { it.selectedTabType }
                    .firstOrError()
            }
            .filter { it == FileTransportActivity.Companion.DirTabType.MyApps }
            .observeOn(Schedulers.io())
            .switchMapSingle {
                rxSingle {
                    val selectedApps = bindState().firstOrError().map { it.selected }.await()
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
                                maxConnection = Settings.transferFileMaxConnection().await()
                            )
                        }.onFailure {
                            AndroidLog.e(TAG, "Request send apps fail: $it", it)
                        }.onSuccess {
                            AndroidLog.d(TAG, "Request send apps success: $it")
                            (requireActivity() as FileTransportActivity)
                                .sendSenderFiles(
                                    files = senderFiles,
                                    bufferSize = it.bufferSize.toLong()
                                )
                        }
                    }
                    updateState { it.copy(selected = emptySet()) }.await()
                    Unit
                }.onErrorResumeNext {
                    Single.just(Unit)
                }
            }
            .bindLife()
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
                selected = emptySet()
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
            val selected: Set<AppInfo> = emptySet()
        )

        object AppSelectChange

        private const val TAG = "MyAppsFragment"
    }
}