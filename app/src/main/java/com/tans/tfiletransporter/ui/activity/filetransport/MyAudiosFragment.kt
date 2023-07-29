package com.tans.tfiletransporter.ui.activity.filetransport

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
import com.tans.tfiletransporter.databinding.AudioItemLayoutBinding
import com.tans.tfiletransporter.databinding.MyAudiosFragmentLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.file.toFileExploreFile
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import com.tans.tfiletransporter.utils.dp2px
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
import kotlin.math.min


class MyAudiosFragment : BaseFragment<MyAudiosFragmentLayoutBinding, MyAudiosFragment.Companion.MyAudiosState>(
    layoutId = R.layout.my_audios_fragment_layout,
    default = MyAudiosState()
) {

    private val fileExplore: FileExplore by instance()

    private val recyclerViewScrollChannel = Channel<Int>(1)
    @Suppress("NAME_SHADOWING")
    override fun initViews(binding: MyAudiosFragmentLayoutBinding) {
        refreshAudios().switchThread().bindLife()
        binding.myAudiosRv.layoutManager = LinearLayoutManager(requireContext())
        binding.myAudiosRv.adapter =
            SimpleAdapterSpec<Pair<QueryMediaItem.Audio, Boolean>, AudioItemLayoutBinding>(
                layoutId = R.layout.audio_item_layout,
                bindData = { _, (audio, select), lBinding ->
                    lBinding.audio = audio; lBinding.select = select
                },
                itemClicks = listOf { lBinding, _ ->
                    lBinding.root to { _, (audio, select) ->
                        rxSingle {
                            updateState { state ->
                                val oldSelect = state.selectedAudios
                                state.copy(selectedAudios = if (select) oldSelect - audio else oldSelect + audio)
                            }.await()
                            Unit
                        }
                    }
                },
                dataUpdater = bindState().map { state ->
                    state.audios.map {
                        it to state.selectedAudios.contains(
                            it
                        )
                    }
                },
                differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1.first.uri == d2.first.uri },
                    contentTheSame = { d1, d2 -> d1.first.uri == d2.first.uri && d1.second == d2.second },
                    changePayLoad = { d1, d2 ->
                        if (d1.first.uri == d2.first.uri && d1.second != d2.second) {
                            AudioSelectChange
                        } else {
                            null
                        }
                    }),
                bindDataPayload = { _, data, binding, payloads ->
                    if (payloads.contains(AudioSelectChange)) {
                        binding.select = data.second
                        true
                    } else {
                        false
                    }
                }
            ).toAdapter {
                val position = recyclerViewScrollChannel.tryReceive().getOrNull()
                if (position != null && it.isNotEmpty()) {
                    (binding.myAudiosRv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
                }
            }
        binding.myAudiosRv.addItemDecoration(MarginDividerItemDecoration.Companion.Builder()
            .divider(MarginDividerItemDecoration.Companion.ColorDivider(requireContext().getColor(R.color.line_color),
                requireContext().dp2px(1)))
            .marginStart(requireContext().dp2px(70))
            .build()
        )

        binding.audiosRefreshLayout.setColorSchemeResources(R.color.teal_200)
        binding.audiosRefreshLayout.refreshes()
            .switchMapSingle {
                refreshAudios()
                    .switchThread()
                    .doFinally {
                        recyclerViewScrollChannel.trySend(0).isSuccess
                        if (binding.audiosRefreshLayout.isRefreshing)
                            binding.audiosRefreshLayout.isRefreshing = false
                    }
            }
            .bindLife()

        (requireActivity() as FileTransportActivity).observeFloatBtnClick()
            .flatMapSingle {
                (activity as FileTransportActivity).bindState().map { it.selectedTabType }
                    .firstOrError()
            }
            .filter { it == FileTransportActivity.Companion.DirTabType.MyAudios }
            .switchMapSingle {
                rxSingle(Dispatchers.IO) {
                    val selectAudios = bindState().firstOrError().map { it.selectedAudios }.await()
                    if (selectAudios.isEmpty()) return@rxSingle
                    clearAudioCaches()
                    val files = selectAudios.createCatches()
                    val senderFiles = files.map { SenderFile(it, it.toFileExploreFile()) }
                    if (senderFiles.isNotEmpty()) {
                        runCatching {
                            fileExplore.requestSendFilesSuspend(
                                sendFiles = senderFiles.map { it.exploreFile },
                                maxConnection = Settings.transferFileMaxConnection().await()
                            )
                        }.onSuccess {
                            AndroidLog.d(TAG, "Request send audio success")
                            (requireActivity() as FileTransportActivity).sendSenderFiles(senderFiles)
                        }.onFailure {
                            AndroidLog.e(TAG, "Request send audio fail.")
                        }
                    } else {
                        AndroidLog.e(TAG, "Selected files is empty.")
                    }
                    updateState { it.copy(selectedAudios = emptySet()) }.await()
                }.onErrorResumeNext {
                    Single.just(Unit)
                }
            }
            .bindLife()
    }

    private fun refreshAudios() = getMedia(
        context = requireContext(),
        queryMediaType = QueryMediaType.Audio)
        .flatMap { media ->
            updateState {
                val audios = media.filterIsInstance<QueryMediaItem.Audio>().filter { it.size > 1024 }.sortedByDescending { it.displayName }
                MyAudiosState(audios = audios)
            }
        }

    private fun Set<QueryMediaItem.Audio>.createCatches(): List<File> {
        val cacheDir = File(requireActivity().cacheDir, IMAGE_CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val result = mutableListOf<File>()
        for (audio in this) {
            try {
                val audioFile = File(cacheDir, audio.displayName)
                if (!audioFile.exists()) {
                    audioFile.createNewFile()
                }
                FileSystem.SYSTEM.sink(audioFile.toOkioPath()).buffer().use { sink ->
                    requireActivity().contentResolver.openInputStream(audio.uri)!!.use { inputStream ->
                        inputStream.source().buffer().use { source ->
                            sink.writeAll(source)
                        }
                    }
                }
                result.add(audioFile)
            } catch (e: Throwable) {
                AndroidLog.e(TAG, "Create cache audio fail: $e", e)
            }
        }
        return result
    }

    private fun clearAudioCaches() {
        val cachePath = Paths.get(requireActivity().cacheDir.toString(), IMAGE_CACHE_DIR)
        if (Files.exists(cachePath) && Files.isDirectory(cachePath)) {
            Files.list(cachePath)
                .forEach { child ->
                    if (!Files.isDirectory(child)) { Files.delete(child) }
                }
        }
    }

    companion object {
        private const val TAG = "MyAudiosFragment"
        data class MyAudiosState(
            val audios: List<QueryMediaItem.Audio> = emptyList(),
            val selectedAudios: Set<QueryMediaItem.Audio> = emptySet()
        )

        object AudioSelectChange

        const val IMAGE_CACHE_DIR = "audio_cache"
    }
}