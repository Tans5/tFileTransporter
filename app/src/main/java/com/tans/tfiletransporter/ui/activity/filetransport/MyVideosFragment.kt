package com.tans.tfiletransporter.ui.activity.filetransport

import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding4.swiperefreshlayout.refreshes
import com.tans.rxutils.QueryMediaItem
import com.tans.rxutils.QueryMediaType
import com.tans.rxutils.getMedia
import com.tans.rxutils.switchThread
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.MyVideosFragmentLayoutBinding
import com.tans.tfiletransporter.databinding.VideoItemLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.file.toFileExploreFile
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.source
import org.kodein.di.instance
import java.nio.file.Files
import java.nio.file.Paths
import java.io.File


class MyVideosFragment : BaseFragment<MyVideosFragmentLayoutBinding, MyVideosFragment.Companion.MyVideosState>(
    layoutId = R.layout.my_videos_fragment_layout,
    default = MyVideosState()
) {

    private val fileExplore: FileExplore by instance()

    private val recyclerViewScrollChannel = Channel<Int>(1)
    @Suppress("NAME_SHADOWING")
    override fun initViews(binding: MyVideosFragmentLayoutBinding) {
        refreshVideos().switchThread().bindLife()
        binding.myVideosRv.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.myVideosRv.adapter =
            SimpleAdapterSpec<Pair<QueryMediaItem.Video, Boolean>, VideoItemLayoutBinding>(
                layoutId = R.layout.video_item_layout,
                bindData = { _, (video, select), lBinding ->
                    lBinding.video = video; lBinding.select = select
                },
                itemClicks = listOf { lBinding, _ ->
                    lBinding.root to { _, (video, select) ->
                        rxSingle {
                            updateState { state ->
                                val oldSelect = state.selectedVideos
                                state.copy(selectedVideos = if (select) oldSelect - video else oldSelect + video)
                            }.await()
                            Unit
                        }
                    }
                },
                dataUpdater = bindState().map { state ->
                    state.videos.map {
                        it to state.selectedVideos.contains(
                            it
                        )
                    }
                },
                differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1.first.uri == d2.first.uri },
                    contentTheSame = { d1, d2 -> d1.first.uri == d2.first.uri && d1.second == d2.second },
                    changePayLoad = { d1, d2 ->
                        if (d1.first.uri == d2.first.uri && d1.second != d2.second) {
                            VideoSelectChange
                        } else {
                            null
                        }
                    }),
                bindDataPayload = { _, data, binding, payloads ->
                    if (payloads.contains(VideoSelectChange)) {
                        binding.select = data.second
                        true
                    } else {
                        false
                    }
                }
            ).toAdapter {
                val position = recyclerViewScrollChannel.tryReceive().getOrNull()
                if (position != null && it.isNotEmpty()) {
                    (binding.myVideosRv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
                }
            }

        binding.videosRefreshLayout.setColorSchemeResources(R.color.teal_200)
        binding.videosRefreshLayout.refreshes()
            .switchMapSingle {
                refreshVideos()
                    .switchThread()
                    .doFinally {
                        recyclerViewScrollChannel.trySend(0).isSuccess
                        if (binding.videosRefreshLayout.isRefreshing)
                            binding.videosRefreshLayout.isRefreshing = false
                    }
            }
            .bindLife()

        (requireActivity() as FileTransportActivity).observeFloatBtnClick()
            .flatMapSingle {
                (activity as FileTransportActivity).bindState().map { it.selectedTabType }
                    .firstOrError()
            }
            .filter { it == FileTransportActivity.Companion.DirTabType.MyVideos }
            .switchMapSingle {
                rxSingle(Dispatchers.IO) {
                    val selectVideos = bindState().firstOrError().map { it.selectedVideos }.await()
                    if (selectVideos.isEmpty()) return@rxSingle
                    clearVideoCaches()
                    val files = selectVideos.createCatches()
                    val senderFiles = files.map { SenderFile(it, it.toFileExploreFile()) }
                    if (senderFiles.isNotEmpty()) {
                        runCatching {
                            fileExplore.requestSendFilesSuspend(
                                sendFiles = senderFiles.map { it.exploreFile },
                                maxConnection = Settings.transferFileMaxConnection().await()
                            )
                        }.onSuccess {
                            AndroidLog.d(TAG, "Request send video success")
                            (requireActivity() as FileTransportActivity).sendSenderFiles(senderFiles)
                        }.onFailure {
                            AndroidLog.e(TAG, "Request send video fail.")
                        }
                    } else {
                        AndroidLog.e(TAG, "Selected files is empty.")
                    }
                    updateState { it.copy(selectedVideos = emptySet()) }.await()
                }.onErrorResumeNext {
                    Single.just(Unit)
                }
            }
            .bindLife()
    }

    private fun refreshVideos() = getMedia(
        context = requireContext(),
        queryMediaType = QueryMediaType.Video)
        .flatMap { media ->
            updateState {
                val videos = media.filterIsInstance<QueryMediaItem.Video>().filter { it.size > 1024 }.sortedByDescending { it.dateModify }
                MyVideosState(videos = videos)
            }
        }

    private fun Set<QueryMediaItem.Video>.createCatches(): List<File> {
        val cacheDir = File(requireActivity().cacheDir, IMAGE_CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val result = mutableListOf<File>()
        for (video in this) {
            try {
                val videoFile = File(cacheDir, video.displayName)
                if (!videoFile.exists()) {
                    videoFile.createNewFile()
                }
                FileSystem.SYSTEM.sink(videoFile.toOkioPath()).buffer().use { sink ->
                    requireActivity().contentResolver.openInputStream(video.uri)!!.use { inputStream ->
                        inputStream.source().buffer().use { source ->
                            sink.writeAll(source)
                        }
                    }
                }
                result.add(videoFile)
            } catch (e: Throwable) {
                AndroidLog.e(TAG, "Create cache video fail: $e", e)
            }
        }
        return result
    }

    private fun clearVideoCaches() {
        val cachePath = Paths.get(requireActivity().cacheDir.toString(), IMAGE_CACHE_DIR)
        if (Files.exists(cachePath) && Files.isDirectory(cachePath)) {
            Files.list(cachePath)
                .forEach { child ->
                    if (!Files.isDirectory(child)) { Files.delete(child) }
                }
        }
    }

    companion object {
        private const val TAG = "MyVideosFragment"
        data class MyVideosState(
            val videos: List<QueryMediaItem.Video> = emptyList(),
            val selectedVideos: Set<QueryMediaItem.Video> = emptySet()
        )

        object VideoSelectChange

        const val IMAGE_CACHE_DIR = "video_cache"
    }
}