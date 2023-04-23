package com.tans.tfiletransporter.ui.activity.filetransport

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
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestSendFilesSuspend
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.activity.*
import com.tans.tfiletransporter.utils.toFileExploreFile
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import okio.source
import org.kodein.di.instance
import java.nio.file.Files
import java.nio.file.Paths
import java.io.File


class MyImagesFragment : BaseFragment<MyImagesFragmentLayoutBinding, MyImagesFragment.Companion.MyImagesState>(
    layoutId = R.layout.my_images_fragment_layout,
    default = MyImagesState()
) {

    private val fileExplore: FileExplore by instance()

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

        (requireActivity() as FileTransportActivity).observeFloatBtnClick()
            .flatMapSingle {
                (activity as FileTransportActivity).bindState().map { it.selectedTabType }
                    .firstOrError()
            }
            .filter { it == FileTransportActivity.Companion.DirTabType.MyImages }
            .switchMapSingle {
                rxSingle(Dispatchers.IO) {
                    val selectImages = bindState().firstOrError().map { it.selectedImages }.await()
                    if (selectImages.isEmpty()) return@rxSingle
                    clearImageCaches()
                    val files = selectImages.createCatches()
                    val exploreFiles = files.map { it.toFileExploreFile("") }
                    if (exploreFiles.isNotEmpty()) {
                        runCatching {
                            fileExplore.requestSendFilesSuspend(
                                sendFiles = exploreFiles
                            )
                        }.onSuccess {
                            AndroidLog.d(TAG, "Request send image success")
                            // TODO: Send images.
                        }.onFailure {
                            AndroidLog.e(TAG, "Request send image fail.")
                        }
                    } else {
                        AndroidLog.e(TAG, "Selected files is empty.")
                    }
                    updateState { it.copy(selectedImages = emptySet()) }.await()
                }.onErrorResumeNext {
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

    private fun Set<QueryMediaItem.Image>.createCatches(): List<File> {
        val cacheDir = File(requireActivity().cacheDir, IMAGE_CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val result = mutableListOf<File>()
        for (image in this) {
            try {
                val imageFile = File(cacheDir, image.displayName)
                if (!imageFile.exists()) {
                    imageFile.createNewFile()
                }
                FileSystem.SYSTEM.sink(imageFile.toOkioPath()).buffer().use { sink ->
                    requireActivity().contentResolver.openInputStream(image.uri)!!.use { inputStream ->
                        inputStream.source().buffer().use { source ->
                            sink.writeAll(source)
                        }
                    }
                }
                result.add(imageFile)
            } catch (e: Throwable) {
                AndroidLog.e(TAG, "Create cache image fail: $e", e)
            }
        }
        return result
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
        private const val TAG = "MyImagesFragment"
        data class MyImagesState(
            val images: List<QueryMediaItem.Image> = emptyList(),
            val selectedImages: Set<QueryMediaItem.Image> = emptySet()
        )

        object ImageSelectChange

        const val IMAGE_CACHE_DIR = "image_cache"
    }
}