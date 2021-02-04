package com.tans.tfiletransporter.ui.activity.filetransport

import android.view.inputmethod.InputMethodManager
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.MessageFragmentBinding
import com.tans.tfiletransporter.databinding.MessageItemLayoutBinding
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.activity.FileTransportActivity
import com.tans.tfiletransporter.ui.activity.filetransport.activity.FileTransportScopeData
import com.tans.tfiletransporter.ui.activity.filetransport.activity.newSendMessageShareWriterHandle
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEventListener
import org.kodein.di.instance

data class Message(
        val isRemote: Boolean,
        val timeMilli: Long,
        val message: String
)

class MessageFragment : BaseFragment<MessageFragmentBinding, List<Message>>(
    R.layout.message_fragment,
    emptyList()
) {

    private val fileTransportScopeData: FileTransportScopeData by instance()

    private val inputMethodManager: InputMethodManager by instance()

    override fun onInit() {

        binding.messageRv.adapter = SimpleAdapterSpec<Message, MessageItemLayoutBinding>(
            layoutId = R.layout.message_item_layout,
            bindData = { _, data, lBinding -> lBinding.message = data },
            dataUpdater = bindState()
        ).toAdapter { if (it.isNotEmpty()) { binding.messageRv.scrollToPosition(it.size - 1) } }

        fileTransportScopeData.remoteMessageEvent
            .flatMapSingle { remoteMessage ->
                updateState { oldState ->
                    val message = Message(
                        isRemote = true,
                        timeMilli = System.currentTimeMillis(),
                        message = remoteMessage
                    )
                    val newState = oldState + message
                    newState
                }
            }
            .bindLife()

        binding.sendLayout.clicks()
            .map { binding.editText.text.toString()}
            .filter { it.isNotEmpty() }
            .switchMapSingle { sendingMessage ->
                rxSingle {
                    // val dialog = withContext(Dispatchers.Main) { requireActivity().showLoadingDialog() }
                    withContext(Dispatchers.IO) {
                        fileTransportScopeData.fileTransporter.startWriterHandleWhenFinish(
                            newSendMessageShareWriterHandle(sendingMessage)
                        )
                    }
                    updateState { oldState ->
                        oldState + Message(
                            isRemote = false,
                            timeMilli = System.currentTimeMillis(),
                            message = sendingMessage
                        )
                    }.await()
                    withContext(Dispatchers.Main) {
                        binding.editText.text?.clear()
                        // dialog.cancel()
                        Unit
                    }
                }
            }
            .bindLife()

        (requireActivity() as? FileTransportActivity)?.bindState()
                ?.distinctUntilChanged()
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.doOnNext {
                    inputMethodManager.hideSoftInputFromWindow(binding.editText.windowToken, 0)
                }
                ?.bindLife()

        KeyboardVisibilityEvent.registerEventListener(requireActivity(), object : KeyboardVisibilityEventListener {
            override fun onVisibilityChanged(isOpen: Boolean) {
                if (isOpen) {
                    bindState().firstOrError()
                        .doOnSuccess {
                            if (it.isNotEmpty()) {
                                binding.messageRv.scrollToPosition(it.size - 1)
                            }
                        }
                        .bindLife()
                }
            }
        })
    }

    companion object {
        const val FRAGMENT_TAG = "message_tag"
    }

}