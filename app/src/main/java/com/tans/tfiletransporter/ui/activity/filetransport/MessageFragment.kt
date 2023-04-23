package com.tans.tfiletransporter.ui.activity.filetransport

import android.view.View
import android.view.inputmethod.InputMethodManager
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.MessageFragmentBinding
import com.tans.tfiletransporter.databinding.MessageItemLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestMsgSuspend
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.activity.FileTransportActivity
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEventListener
import org.kodein.di.instance


class MessageFragment : BaseFragment<MessageFragmentBinding, Unit>(
    R.layout.message_fragment,
    Unit
) {

    private val inputMethodManager: InputMethodManager by instance()

    private val fileExplore: FileExplore by instance()

    init {

    }

    override fun initViews(binding: MessageFragmentBinding) {

        binding.messageRv.adapter = SimpleAdapterSpec<FileTransportActivity.Companion.Message, MessageItemLayoutBinding>(
            layoutId = R.layout.message_item_layout,
            bindData = { _, data, lBinding ->
                val isRemote = data.fromRemote
                if (isRemote) {
                    lBinding.remoteMessageTv.visibility = View.VISIBLE
                    lBinding.myMessageTv.visibility = View.GONE
                    lBinding.remoteMessageTv.text = data.msg
                } else {
                    lBinding.remoteMessageTv.visibility = View.GONE
                    lBinding.myMessageTv.visibility = View.VISIBLE
                    lBinding.myMessageTv.text = data.msg
                }
            },
            dataUpdater = (requireActivity() as FileTransportActivity).observeMessages().map { it.asReversed() }.distinctUntilChanged()
        ).toAdapter {
            if (it.isNotEmpty()) {
                binding.messageRv.scrollToPosition(it.size - 1)
            }
        }

        binding.sendLayout.clicks()
            .map { binding.editText.text.toString()}
            .filter { it.isNotEmpty() }
            .switchMapSingle { sendingMessage ->
                rxSingle(Dispatchers.IO) {
                    runCatching {
                        fileExplore.requestMsgSuspend(sendingMessage)
                    }.onSuccess {
                        (requireActivity() as FileTransportActivity).updateNewMessage(
                            FileTransportActivity.Companion.Message(
                                time = System.currentTimeMillis(),
                                msg = sendingMessage,
                                fromRemote = false
                            )
                        )
                        AndroidLog.d(TAG, "Send msg success.")
                        withContext(Dispatchers.Main) {
                            binding.editText.text?.clear()
                            Unit
                        }
                    }.onFailure {
                        AndroidLog.e(TAG, "Send msg fail: $it", it)
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
                    (activity as FileTransportActivity).observeMessages().firstOrError()
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
        private const val TAG = "MessageFragment"
    }

}