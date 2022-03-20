package com.tans.tfiletransporter.ui.activity.filetransport

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.util.Log
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.recyclerviewutils.MarginDividerItemDecoration
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.AppItemLayoutBinding
import com.tans.tfiletransporter.databinding.MyAppsFragmentLayoutBinding
import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.net.model.ShareFilesModel
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.commomdialog.loadingDialog
import com.tans.tfiletransporter.ui.activity.filetransport.activity.*
import com.tans.tfiletransporter.utils.dp2px
import com.tans.tfiletransporter.utils.getFilePathMd5
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import org.kodein.di.instance
import org.threeten.bp.OffsetDateTime
import java.nio.file.Files
import java.nio.file.Paths


data class AppInfo(
        val name: String,
        val sourceDir: String,
        val packageName: String,
        val icon: Drawable,
        val appSize: Long
)

data class MyAppsState(
        val apps: List<AppInfo> = emptyList(),
        val selected: Set<AppInfo> = emptySet()
)

object AppSelectChange

class MyAppsFragment : BaseFragment<MyAppsFragmentLayoutBinding, MyAppsState>(
        layoutId = R.layout.my_apps_fragment_layout,
        default = MyAppsState()
) {

    private val scopeData: FileTransportScopeData by instance()

    override fun initViews(binding: MyAppsFragmentLayoutBinding) {

        refreshApps()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .loadingDialog(requireActivity())
                .bindLife()

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
                    lBinding.app = app
                    lBinding.isSelect = select
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
                        lBinding.isSelect = isSelect
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

        scopeData.floatBtnEvent
            .flatMapSingle {
                (activity as FileTransportActivity).bindState().map { it.selectedTabType }
                    .firstOrError()
            }
            .filter { it == DirTabType.MyApps }
            .observeOn(Schedulers.io())
            .switchMapSingle {
                rxSingle {
                    val selectedApps = bindState().firstOrError().map { it.selected }.await()
                    val files =
                        selectedApps.filter { Files.isReadable(Paths.get(it.sourceDir)) }.map {
                            File(
                                name = "${it.name}_${it.packageName}.apk",
                                path = it.sourceDir,
                                size = it.appSize,
                                lastModify = OffsetDateTime.now()
                            )
                        }
                    if (files.isNotEmpty()) {
                        val fileConnection = scopeData.fileExploreConnection
                        val md5Files = files.filter { it.size > 0 }.map { FileMd5(md5 = Paths.get(
                            FileConstants.homePathString, it.path).getFilePathMd5(), it) }
                        fileConnection.sendFileExploreContentToRemote(
                            fileExploreContent = ShareFilesModel(shareFiles = md5Files),
                            waitReplay = true
                        )
                        withContext(Dispatchers.Main) {
                            val result = kotlin.runCatching {
                                requireActivity().startSendingFiles(
                                    files = md5Files,
                                    localAddress = scopeData.localAddress,
                                    pathConverter = { file -> Paths.get(file.path) }
                                ).await()
                            }
                            if (result.isFailure) {
                                Log.e("SendingFileError", "SendingFileError", result.exceptionOrNull())
                            }
                        }
                        updateState { it.copy(selected = emptySet()) }.await()
                    }
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
                            icon = it.loadIcon(requireActivity().packageManager),
                            appSize = Files.size(Paths.get(it.sourceDir))
                    )
                }
                .sortedBy { it.name }
        MyAppsState(
                apps = apps,
                selected = emptySet()
        )
    }

    companion object {
        const val FRAGMENT_TAG = "my_apps_fragment_tag"
    }
}