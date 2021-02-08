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
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.activity.FileTransportScopeData
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import org.kodein.di.instance

data class MyImagesState(
    val images: List<QueryMediaItem.Image> = emptyList(),
    val selectedImages: Set<QueryMediaItem.Image> = emptySet()
)

object ImageSelectChange

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
                val position = recyclerViewScrollChannel.poll()
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
                        recyclerViewScrollChannel.offer(0)
                        if (binding.imagesRefreshLayout.isRefreshing)
                            binding.imagesRefreshLayout.isRefreshing = false
                    }
            }
            .bindLife()

        scopeData.floatBtnEvent
            .switchMapSingle {
                rxSingle {
                    updateState { it.copy(selectedImages = emptySet()) }.await()
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

    companion object {
        const val FRAGMENT_TAG = "my_images_fragment_tag"
    }
}