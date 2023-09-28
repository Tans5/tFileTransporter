package com.tans.tfiletransporter.ui.activity.filetransport

import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding4.swiperefreshlayout.refreshes
import com.tans.rxutils.QueryMediaItem
import com.tans.rxutils.QueryMediaType
import com.tans.rxutils.getMedia
import com.tans.rxutils.switchThread
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.recyclerviewutils.MarginDividerItemDecoration
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.BaseMediaFragmentLayoutBinding
import com.tans.tfiletransporter.databinding.ImageItemLayoutBinding
import com.tans.tfiletransporter.databinding.VideoAudioItemLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.file.toFileExploreFile
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import com.tans.tfiletransporter.utils.dp2px
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import org.kodein.di.instance
import java.io.File


abstract class BaseMediaFragment(
    private val mediaType: MediaType
) : BaseFragment<BaseMediaFragmentLayoutBinding, BaseMediaFragment.Companion.BaseMediaState>(
    layoutId = R.layout.base_media_fragment_layout,
    default = BaseMediaState()
) {

    private val fileExplore: FileExplore by instance()

    private val androidRootDir: File by lazy {
        Environment.getExternalStorageDirectory()
    }

    private val recyclerViewScrollChannel = Channel<Int>(1)

    override fun initViews(binding: BaseMediaFragmentLayoutBinding) {

        refreshMediaItems().switchThread().bindLife()

        val rvAdapter = when (mediaType) {
            MediaType.Image -> {
                binding.myMediaItemsRv.layoutManager = GridLayoutManager(requireContext(), 2)
                SimpleAdapterSpec<Pair<QueryMediaItem.Image, Boolean>, ImageItemLayoutBinding>(
                    layoutId = R.layout.image_item_layout,
                    bindData = { _, (image, select), lBinding ->
                        lBinding.image = image; lBinding.select = select
                    },
                    itemClicks = listOf { lBinding, _ ->
                        lBinding.root to { _, (image, lastSelectStatus) ->
                            selectMediaItem(!lastSelectStatus, image).map { Unit }
                        }
                    },
                    dataUpdater = bindState().map { state ->
                        state.mediaItems.filterIsInstance<QueryMediaItem.Image>().map {
                            it to state.selectedMediaItems.contains(it)
                        }
                    },
                    differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1.first.uri == d2.first.uri },
                        contentTheSame = { d1, d2 -> d1.first.uri == d2.first.uri && d1.second == d2.second },
                        changePayLoad = { d1, d2 ->
                            if (d1.first.uri == d2.first.uri && d1.second != d2.second) {
                                MediaItemSelectChange
                            } else {
                                null
                            }
                        }),
                    bindDataPayload = { _, data, lBinding, payloads ->
                        if (payloads.contains(MediaItemSelectChange)) {
                            lBinding.select = data.second
                            true
                        } else {
                            false
                        }
                    }
                ).toAdapter {
                    val position = recyclerViewScrollChannel.tryReceive().getOrNull()
                    if (position != null && it.isNotEmpty()) {
                        (binding.myMediaItemsRv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
                    }
                }
            }
            else -> {
                binding.myMediaItemsRv.layoutManager = LinearLayoutManager(requireContext())
                binding.myMediaItemsRv.addItemDecoration(
                    MarginDividerItemDecoration.Companion.Builder()
                        .divider(
                            MarginDividerItemDecoration.Companion.ColorDivider(requireActivity().getColor(R.color.line_color),
                                requireActivity().dp2px(1)))
                        .marginStart(requireActivity().dp2px(65))
                        .build()
                )
                SimpleAdapterSpec<Pair<QueryMediaItem, Boolean>, VideoAudioItemLayoutBinding>(
                    layoutId = R.layout.video_audio_item_layout,
                    bindData = { _, (item, isSelect), lBinding ->
                        lBinding.mediaCb.isChecked = isSelect
                        if (item is QueryMediaItem.Video) {
                            lBinding.mediaItemIv.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_movie))
                            lBinding.titleTv.text = item.displayName
                            lBinding.artistTv.text = requireContext().getString(R.string.media_artist_name, item.artist)
                            lBinding.albumTv.text = requireContext().getString(R.string.media_album_name, item.album)
                            lBinding.mediaSizeTv.text = item.size.toSizeString()
                        }
                        if (item is QueryMediaItem.Audio) {
                            lBinding.mediaItemIv.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_audio))
                            lBinding.titleTv.text = item.displayName
                            lBinding.artistTv.text = requireContext().getString(R.string.media_artist_name, item.artist)
                            lBinding.albumTv.text = requireContext().getString(R.string.media_album_name, item.album)
                            lBinding.mediaSizeTv.text = item.size.toSizeString()
                        }
                    },
                    itemClicks = listOf { lBinding, _ ->
                        lBinding.root to { _, (item, lastSelectStatus) ->
                            selectMediaItem(!lastSelectStatus, item).map { Unit }
                        }
                    },
                    dataUpdater = bindState().map { state ->
                        state.mediaItems.filter { it is QueryMediaItem.Video || it is QueryMediaItem.Audio }.map {
                            it to state.selectedMediaItems.contains(it)
                        }
                    },
                    differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1.first.uri == d2.first.uri },
                        contentTheSame = { d1, d2 -> d1.first.uri == d2.first.uri && d1.second == d2.second },
                        changePayLoad = { d1, d2 ->
                            if (d1.first.uri == d2.first.uri && d1.second != d2.second) {
                                MediaItemSelectChange
                            } else {
                                null
                            }
                        }),
                    bindDataPayload = { _, data, lBinding, payloads ->
                        if (payloads.contains(MediaItemSelectChange)) {
                            lBinding.mediaCb.isChecked = data.second
                            true
                        } else {
                            false
                        }
                    }
                ).toAdapter {
                    val position = recyclerViewScrollChannel.tryReceive().getOrNull()
                    if (position != null && it.isNotEmpty()) {
                        (binding.myMediaItemsRv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
                    }
                }
            }
        }

        binding.myMediaItemsRv.adapter = rvAdapter

        binding.mediaItemsRefreshLayout.setColorSchemeResources(R.color.teal_200)
        binding.mediaItemsRefreshLayout.refreshes()
            .switchMapSingle {
                refreshMediaItems()
                    .switchThread()
                    .doFinally {
                        recyclerViewScrollChannel.trySend(0).isSuccess
                        if (binding.mediaItemsRefreshLayout.isRefreshing)
                            binding.mediaItemsRefreshLayout.isRefreshing = false
                    }
            }
            .bindLife()

        (requireActivity() as FileTransportActivity).observeFloatBtnClick()
            .flatMapSingle {
                (activity as FileTransportActivity).bindState().map { it.selectedTabType }
                    .firstOrError()
            }
            .filter {
                when (mediaType) {
                    MediaType.Image -> it == FileTransportActivity.Companion.DirTabType.MyImages
                    MediaType.Video -> it == FileTransportActivity.Companion.DirTabType.MyVideos
                    MediaType.Audio -> it == FileTransportActivity.Companion.DirTabType.MyAudios
                }
            }
            .switchMapSingle {
                rxSingle(Dispatchers.IO) {
                    val selectedItems = bindState().firstOrError().map { it.selectedMediaItems }.await()
                    if (selectedItems.isEmpty()) return@rxSingle
                    val files = selectedItems.mapNotNull {
                        val parentFile = File(androidRootDir, it.path)
                        val file = File(parentFile, it.displayName)
                        if (file.isFile) {
                            file
                        } else {
                            null
                        }
                    }
                    val senderFiles = files.map { SenderFile(it, it.toFileExploreFile()) }
                    if (senderFiles.isNotEmpty()) {
                        runCatching {
                            fileExplore.requestSendFilesSuspend(
                                sendFiles = senderFiles.map { it.exploreFile },
                                maxConnection = Settings.transferFileMaxConnection().await()
                            )
                        }.onSuccess {
                            AndroidLog.d(TAG, "Request send image success")
                            (requireActivity() as FileTransportActivity).sendSenderFiles(senderFiles)
                        }.onFailure {
                            AndroidLog.e(TAG, "Request send image fail.")
                        }
                    } else {
                        AndroidLog.e(TAG, "Selected files is empty.")
                    }
                    updateState { it.copy(selectedMediaItems = emptyList()) }.await()
                }.onErrorResumeNext {
                    Single.just(Unit)
                }
            }
            .bindLife()
    }

    private fun refreshMediaItems() = getMedia(
        context = requireContext(),
        queryMediaType = when (mediaType) {
            MediaType.Image -> QueryMediaType.Image
            MediaType.Video -> QueryMediaType.Video
            MediaType.Audio -> QueryMediaType.Audio
        }
    )
        .flatMap { media ->
            updateState {
                val mediaItems = when (mediaType) {
                    MediaType.Image -> media.filterIsInstance<QueryMediaItem.Image>()
                        .sortedByDescending { it.dateModify }

                    MediaType.Video -> media.filterIsInstance<QueryMediaItem.Video>()
                        .sortedByDescending { it.dateModify }

                    MediaType.Audio -> media.filterIsInstance<QueryMediaItem.Audio>()
                        .sortedByDescending { it.dateModify }
                }.filter { it.size > 1024 }
                BaseMediaState(mediaItems = mediaItems)
            }
        }

    private fun selectMediaItem(select: Boolean, item: QueryMediaItem) = updateState { state ->
        val oldSelect = state.selectedMediaItems
        state.copy(selectedMediaItems = if (select) oldSelect + item else oldSelect - item)
    }

    companion object {
        private const val TAG = "BaseMediaFragment"
        data class BaseMediaState(
            val mediaItems: List<QueryMediaItem> = emptyList(),
            val selectedMediaItems: List<QueryMediaItem> = emptyList()
        )

        object MediaItemSelectChange

        enum class MediaType { Image, Video, Audio }
    }
}