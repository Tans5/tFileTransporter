package com.tans.tfiletransporter.ui.activity.filetransport

import android.util.Log
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding3.swiperefreshlayout.refreshes
import com.tans.rxutils.QueryMediaItem
import com.tans.rxutils.QueryMediaType
import com.tans.rxutils.getMedia
import com.tans.rxutils.switchThread
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ImageItemLayoutBinding
import com.tans.tfiletransporter.databinding.MyImagesFragmentLayoutBinding
import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.net.commonNetBufferPool
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.net.model.ShareFilesModel
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.activity.*
import com.tans.tfiletransporter.utils.getFilePathMd5
import com.tans.tfiletransporter.utils.readFrom
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import org.kodein.di.instance
import org.threeten.bp.OffsetDateTime
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Paths

data class MyImagesState(
    val images: List<QueryMediaItem.Image> = emptyList(),
    val selectedImages: Set<QueryMediaItem.Image> = emptySet()
)

object ImageSelectChange

const val IMAGE_CACHE_DIR = "image_cache"

class MyImagesFragment : BaseFragment<MyImagesFragmentLayoutBinding, MyImagesState>(
    layoutId = R.layout.my_images_fragment_layout,
    default = MyImagesState()
) {

    private val scopeData: FileTransportScopeData by instance()

    private val recyclerViewScrollChannel = Channel<Int>(1)
    override fun initViews(binding: MyImagesFragmentLayoutBinding) {
        refreshImages().switchThread().bindLife()
        binding.myImagesRv.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.myImagesRv.adapter =
            SimpleAdapterSpec<Pair<QueryMediaItem.Image, Boolean>, ImageItemLayoutBinding>(
                layoutId = R.layout.image_item_layout,
                bindData = { _, (image, select), lBinding ->
                    lBinding.image = image; lBinding.select = select
                },
                itemClicks = listOf { lBinding, _ ->
                    lBinding.root to { _, (image, select) ->
                        rxSingle {
                            updateState { state ->
                                val oldSelect = state.selectedImages
                                state.copy(selectedImages = if (select) oldSelect - image else oldSelect + image)
                            }.await()
                            Unit
                        }
                    }
                },
                dataUpdater = bindState().map { state ->
                    state.images.map {
                        it to state.selectedImages.contains(
                            it
                        )
                    }
                },
                differHandler = DifferHandler(itemsTheSame = { d1, d2 -> d1.first.uri == d2.first.uri },
                    contentTheSame = { d1, d2 -> d1.first.uri == d2.first.uri && d1.second == d2.second },
                    changePayLoad = { d1, d2 ->
                        if (d1.first.uri == d2.first.uri && d1.second != d2.second) {
                            ImageSelectChange
                        } else {
                            null
                        }
                    }),
                bindDataPayload = { _, data, binding, payloads ->
                    if (payloads.contains(ImageSelectChange)) {
                        binding.select = data.second
                        true
                    } else {
                        false
                    }
                }
            ).toAdapter {
                val position = recyclerViewScrollChannel.tryReceive().getOrNull()
                if (position != null && it.isNotEmpty()) {
                    (binding.myImagesRv.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(position, 0)
                }
            }

//        val horizontalDivider = MarginDividerItemDecoration.Companion.Builder()
//            .divider(MarginDividerItemDecoration.Companion.ColorDivider(color = Color.TRANSPARENT, size = requireContext().dp2px(6)))
//            .dividerDirection(MarginDividerItemDecoration.Companion.DividerDirection.Horizontal)
//            .build()
//
//        val verticalDivider = MarginDividerItemDecoration.Companion.Builder()
//            .divider(MarginDividerItemDecoration.Companion.ColorDivider(color = Color.TRANSPARENT, size = requireContext().dp2px(6)))
//            .dividerDirection(MarginDividerItemDecoration.Companion.DividerDirection.Vertical)
//            .dividerController(IgnoreGridLastRowVerticalDividerController(rowSize = 2))
//            .build()
//        binding.myImagesRv.addItemDecoration(horizontalDivider)
//        binding.myImagesRv.addItemDecoration(verticalDivider)

        binding.imagesRefreshLayout.refreshes()
            .switchMapSingle {
                refreshImages()
                    .switchThread()
                    .doFinally {
                        recyclerViewScrollChannel.trySend(0).isSuccess
                        if (binding.imagesRefreshLayout.isRefreshing)
                            binding.imagesRefreshLayout.isRefreshing = false
                    }
            }
            .bindLife()

        scopeData.floatBtnEvent
            .flatMapSingle {
                (activity as FileTransportActivity).bindState().map { it.selectedTabType }
                    .firstOrError()
            }
            .filter { it == DirTabType.MyImages }
            .observeOn(Schedulers.io())
            .switchMapSingle {
                rxSingle {
                    val selectImages = bindState().firstOrError().map { it.selectedImages }.await()
                    if (selectImages.isNotEmpty()) {
                        clearImageCaches()

                        updateState { it.copy(selectedImages = emptySet()) }.await()
                        val fileConnection = scopeData.fileExploreConnection
                        val md5Files = selectImages.createCatches().filter { it.size > 0 }.map { FileMd5(md5 = Paths.get(
                            FileConstants.homePathString, it.path).getFilePathMd5(), file = it) }
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
                    }
                }.onErrorResumeNext {
                    Log.e("Send Images", "Send Images Error", it)
                    Single.just(Unit)
                }
            }
            .bindLife()
    }

    private fun refreshImages() = getMedia(
        context = requireContext(),
        queryMediaType = QueryMediaType.Image)
        .flatMap { media ->
            updateState {
                val images = media.filterIsInstance<QueryMediaItem.Image>().filter { it.size > 1024 }.sortedByDescending { it.dateModify }
                MyImagesState(images = images)
            }
        }

    private suspend fun Set<QueryMediaItem.Image>.createCatches(): List<File> {
        val catchDirPath = Paths.get(requireActivity().cacheDir.toString(), IMAGE_CACHE_DIR).toAbsolutePath()
        if (!Files.exists(catchDirPath)) {
            Files.createDirectory(catchDirPath)
        }
        val buffer = commonNetBufferPool.requestBuffer()
        return try {
            filter { it.size > 1024 }
                .map { image ->
                    val filePath = Paths.get(catchDirPath.toString(), image.displayName)
                    if (Files.exists(filePath)) {
                        Files.delete(filePath)
                    }
                    Files.createFile(filePath)
                    val writerChannel = Channels.newChannel(Files.newOutputStream(filePath))
                    val readerChannel = Channels.newChannel(requireActivity().contentResolver.openInputStream(image.uri))
                    writerChannel.use {
                        readerChannel.use {
                            writerChannel.readFrom(
                                readable = readerChannel,
                                limit = image.size,
                                buffer = buffer
                            )
                        }
                    }
                    File(name = image.displayName,
                            path = filePath.toAbsolutePath().toString(),
                            size = image.size,
                            lastModify = OffsetDateTime.now())
                }.toList()
        } finally {
            commonNetBufferPool.recycleBuffer(buffer)
        }
    }

    private fun clearImageCaches() {
        val cachePath = Paths.get(requireActivity().cacheDir.toString(), IMAGE_CACHE_DIR)
        if (Files.exists(cachePath) && Files.isDirectory(cachePath)) {
            Files.list(cachePath)
                .forEach { child ->
                    if (!Files.isDirectory(child)) { Files.delete(child) }
                }
        }
    }

    companion object {
        const val FRAGMENT_TAG = "my_images_fragment_tag"
    }
}