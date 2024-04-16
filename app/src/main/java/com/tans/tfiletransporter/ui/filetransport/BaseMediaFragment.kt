package com.tans.tfiletransporter.ui.filetransport

import android.os.Environment
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.Settings
import com.tans.tfiletransporter.databinding.BaseMediaFragmentLayoutBinding
import com.tans.tfiletransporter.databinding.ImageItemLayoutBinding
import com.tans.tfiletransporter.databinding.VideoAudioItemLayoutBinding
import com.tans.tfiletransporter.file.fileDateText
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.file.toFileExploreFile
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import com.tans.tfiletransporter.utils.dp2px
import com.tans.tuiutils.adapter.decoration.MarginDividerItemDecoration
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import com.tans.tuiutils.mediastore.MediaStoreAudio
import com.tans.tuiutils.mediastore.MediaStoreImage
import com.tans.tuiutils.mediastore.MediaStoreVideo
import com.tans.tuiutils.mediastore.queryAudioFromMediaStore
import com.tans.tuiutils.mediastore.queryImageFromMediaStore
import com.tans.tuiutils.mediastore.queryVideoFromMediaStore
import com.tans.tuiutils.view.clicks
import com.tans.tuiutils.view.refreshes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


abstract class BaseMediaFragment(
    private val mediaType: MediaType
) : BaseCoroutineStateFragment<BaseMediaFragment.Companion.BaseMediaState>(
    defaultState = BaseMediaState()
) {

    override val layoutId: Int = R.layout.base_media_fragment_layout


    private val fileExplore: FileExplore by lazy {
        (requireActivity() as FileTransportActivity).fileExplore
    }

    private val androidRootDir: File by lazy {
        Environment.getExternalStorageDirectory()
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        launch {
            refreshMediaItems()
        }
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = BaseMediaFragmentLayoutBinding.bind(contentView)


        val rvAdapter = when (mediaType) {
            MediaType.Image -> {
                viewBinding.myMediaItemsRv.layoutManager = GridLayoutManager(requireContext(), 2)
                SimpleAdapterBuilderImpl<Pair<MediaStoreImage, Boolean>>(
                    itemViewCreator = SingleItemViewCreatorImpl(R.layout.image_item_layout),
                    dataSource = FlowDataSourceImpl(
                        dataFlow = stateFlow().map { s ->
                            s.images.map {
                                it to s.selectedImages.contains(it)
                            }
                        },
                        areDataItemsTheSameParam = { d1, d2 -> d1.first == d2.first},
                        areDataItemsContentTheSameParam = { d1, d2 -> d1 == d2 },
                        getDataItemsChangePayloadParam = { d1, d2 -> if (d1.first == d2.first && d1.second != d2.second) Unit else null }
                    ),
                    dataBinder = DataBinderImpl<Pair<MediaStoreImage, Boolean>> { data, view, _ ->
                        val itemViewBinding = ImageItemLayoutBinding.bind(view)
                        val act = activity
                        if (act != null) {
                            Glide.with(act)
                                .load(data.first.uri)
                                .into(itemViewBinding.photoIv)
                        }
                        itemViewBinding.root.clicks(this) {
                            selectOrUnSelectImage(data.first)
                        }
                    }.addPayloadDataBinder(Unit) { data, view, _ ->
                        val itemViewBinding = ImageItemLayoutBinding.bind(view)
                        itemViewBinding.imageCb.isChecked = data.second
                    }
                ).build()
            }
            MediaType.Audio -> {
                viewBinding.myMediaItemsRv.layoutManager = LinearLayoutManager(requireContext())
                viewBinding.myMediaItemsRv.addItemDecoration(
                    MarginDividerItemDecoration.Companion.Builder()
                        .divider(
                            MarginDividerItemDecoration.Companion.ColorDivider(requireActivity().getColor(R.color.line_color),
                                requireActivity().dp2px(1)))
                        .marginStart(requireActivity().dp2px(65))
                        .build()
                )
                SimpleAdapterBuilderImpl<Pair<MediaStoreAudio, Boolean>>(
                    itemViewCreator = SingleItemViewCreatorImpl(R.layout.video_audio_item_layout),
                    dataSource = FlowDataSourceImpl(
                        dataFlow = stateFlow().map { s ->
                            s.audios.map {
                                it to s.selectedAudios.contains(it)
                            }
                        },
                        areDataItemsTheSameParam = { d1, d2 -> d1.first == d2.first},
                        areDataItemsContentTheSameParam = { d1, d2 -> d1 == d2 },
                        getDataItemsChangePayloadParam = { d1, d2 -> if (d1.first == d2.first && d1.second != d2.second) Unit else null }
                    ),
                    dataBinder = DataBinderImpl<Pair<MediaStoreAudio, Boolean>> { data, view, _ ->
                        val itemViewBinding = VideoAudioItemLayoutBinding.bind(view)
                        itemViewBinding.mediaItemIv.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_audio))
                        itemViewBinding.titleTv.text = data.first.title
                        itemViewBinding.artistTv.text = requireContext().getString(R.string.media_artist_name, data.first.artist)
                        itemViewBinding.albumTv.text = requireContext().getString(R.string.media_album_name, data.first.album)
                        itemViewBinding.modifiedDateTv.text = (data.first.dateModified * 1000L).fileDateText()
                        itemViewBinding.mediaSizeTv.text = data.first.size.toSizeString()
                        itemViewBinding.root.clicks(this) {
                            selectOrUnSelectAudio(data.first)
                        }
                    }.addPayloadDataBinder(Unit) { data, view, _ ->
                        val itemViewBinding = VideoAudioItemLayoutBinding.bind(view)
                        itemViewBinding.mediaCb.isChecked = data.second
                    }
                ).build()
            }
            MediaType.Video -> {
                viewBinding.myMediaItemsRv.layoutManager = LinearLayoutManager(requireContext())
                viewBinding.myMediaItemsRv.addItemDecoration(
                    MarginDividerItemDecoration.Companion.Builder()
                        .divider(
                            MarginDividerItemDecoration.Companion.ColorDivider(requireActivity().getColor(R.color.line_color),
                                requireActivity().dp2px(1)))
                        .marginStart(requireActivity().dp2px(65))
                        .build()
                )
                SimpleAdapterBuilderImpl<Pair<MediaStoreVideo, Boolean>>(
                    itemViewCreator = SingleItemViewCreatorImpl(R.layout.video_audio_item_layout),
                    dataSource = FlowDataSourceImpl(
                        dataFlow = stateFlow().map { s ->
                            s.videos.map {
                                it to s.selectedVideos.contains(it)
                            }
                        },
                        areDataItemsTheSameParam = { d1, d2 -> d1.first == d2.first},
                        areDataItemsContentTheSameParam = { d1, d2 -> d1 == d2 },
                        getDataItemsChangePayloadParam = { d1, d2 -> if (d1.first == d2.first && d1.second != d2.second) Unit else null }
                    ),
                    dataBinder = DataBinderImpl<Pair<MediaStoreVideo, Boolean>> { data, view, _ ->
                        val itemViewBinding = VideoAudioItemLayoutBinding.bind(view)
                        itemViewBinding.mediaItemIv.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_movie))
                        itemViewBinding.titleTv.text = data.first.title
                        itemViewBinding.artistTv.visibility = View.INVISIBLE
                        itemViewBinding.albumTv.visibility = View.INVISIBLE
                        itemViewBinding.modifiedDateTv.text = (data.first.dateModified * 1000L).fileDateText()
                        itemViewBinding.mediaSizeTv.text = data.first.size.toSizeString()
                        itemViewBinding.root.clicks(this) {
                            selectOrUnSelectVideo(data.first)
                        }
                    }.addPayloadDataBinder(Unit) { data, view, _ ->
                        val itemViewBinding = VideoAudioItemLayoutBinding.bind(view)
                        itemViewBinding.mediaCb.isChecked = data.second
                    }
                ).build()
            }
        }

        viewBinding.myMediaItemsRv.adapter = rvAdapter

        viewBinding.mediaItemsRefreshLayout.setColorSchemeResources(R.color.teal_200)
        viewBinding.mediaItemsRefreshLayout.refreshes(coroutineScope = this, refreshWorkOn = Dispatchers.IO) {
            refreshMediaItems()
        }

        val context = requireActivity() as FileTransportActivity

        launch {
            context.observeFloatBtnClick()
                .filter {
                    val selectedTab = context.currentState().selectedTabType
                    when (mediaType) {
                        MediaType.Image -> selectedTab == FileTransportActivity.Companion.DirTabType.MyImages
                        MediaType.Video -> selectedTab == FileTransportActivity.Companion.DirTabType.MyVideos
                        MediaType.Audio -> selectedTab == FileTransportActivity.Companion.DirTabType.MyAudios
                    }
                }
                .collect {
                    launch(Dispatchers.IO) {
                        val currentState = currentState()
                        val files = when (mediaType) {
                            MediaType.Image -> currentState.selectedImages.map { File(File(androidRootDir, it.relativePath), it.displayName) }
                            MediaType.Video -> currentState.selectedVideos.map { File(File(androidRootDir, it.relativePath), it.displayName) }
                            MediaType.Audio -> currentState.selectedAudios.map { File(File(androidRootDir, it.relativePath), it.displayName) }
                        }.filter { it.isFile }
                        val senderFiles = files.map { SenderFile(it, it.toFileExploreFile()) }
                        if (senderFiles.isNotEmpty()) {
                            runCatching {
                                fileExplore.requestSendFilesSuspend(
                                    sendFiles = senderFiles.map { it.exploreFile },
                                    maxConnection = Settings.transferFileMaxConnection()
                                )
                            }.onSuccess {
                                AndroidLog.d(TAG, "Request send image success")
                                runCatching {
                                    (requireActivity() as FileTransportActivity).sendSenderFiles(senderFiles)
                                }
                            }.onFailure {
                                AndroidLog.e(TAG, "Request send image fail.")
                            }
                        } else {
                            AndroidLog.e(TAG, "Selected files is empty.")
                        }
                        updateState { it.copy(selectedVideos = emptyList(), selectedAudios = emptyList(), selectedImages = emptyList()) }
                    }
                }
        }

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.myMediaItemsRv) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom + requireContext().dp2px(85))

            insets
        }
    }


    private suspend fun refreshMediaItems() {
        withContext(Dispatchers.IO) {
            when (mediaType) {
                MediaType.Image -> {
                    val images = queryImageFromMediaStore().sortedByDescending { it.dateModified }
                    updateState { it.copy(selectedImages = emptyList(), images = images) }
                }
                MediaType.Video -> {
                    val videos = queryVideoFromMediaStore().sortedByDescending { it.dateModified }
                    updateState { it.copy(selectedVideos = emptyList(), videos = videos) }
                }
                MediaType.Audio -> {
                    val audios = queryAudioFromMediaStore().sortedByDescending { it.dateModified }
                    updateState { it.copy(selectedAudios = emptyList(), audios = audios) }
                }
            }
        }
    }

    private fun selectOrUnSelectImage(image: MediaStoreImage) {
        updateState {  s ->
            val oldSelectImages = s.selectedImages
            val newSelectImages = if (oldSelectImages.contains(image)) {
                oldSelectImages - image
            } else {
                oldSelectImages + image
            }
            s.copy(selectedImages = newSelectImages)
        }
    }

    private fun selectOrUnSelectAudio(audio: MediaStoreAudio) {
        updateState {  s ->
            val oldSelectAudios = s.selectedAudios
            val newSelectAudios = if (oldSelectAudios.contains(audio)) {
                oldSelectAudios - audio
            } else {
                oldSelectAudios + audio
            }
            s.copy(selectedAudios = newSelectAudios)
        }
    }

    private fun selectOrUnSelectVideo(video: MediaStoreVideo) {
        updateState {  s ->
            val oldSelectVideos = s.selectedVideos
            val newSelectVideos = if (oldSelectVideos.contains(video)) {
                oldSelectVideos - video
            } else {
                oldSelectVideos + video
            }
            s.copy(selectedVideos = newSelectVideos)
        }
    }

    companion object {
        private const val TAG = "BaseMediaFragment"
        data class BaseMediaState(
            val selectedVideos: List<MediaStoreVideo> = emptyList(),
            val selectedImages: List<MediaStoreImage> = emptyList(),
            val selectedAudios: List<MediaStoreAudio> = emptyList(),
            val videos: List<MediaStoreVideo> = emptyList(),
            val images: List<MediaStoreImage> = emptyList(),
            val audios: List<MediaStoreAudio> = emptyList(),
        )

        enum class MediaType { Image, Video, Audio }
    }
}