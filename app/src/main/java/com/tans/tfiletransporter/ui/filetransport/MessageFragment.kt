package com.tans.tfiletransporter.ui.filetransport

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.MessageFragmentBinding
import com.tans.tfiletransporter.databinding.MessageItemLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.transferproto.fileexplore.FileExplore
import com.tans.tfiletransporter.transferproto.fileexplore.requestMsgSuspend
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.fragment.BaseCoroutineStateFragment
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEventListener

class MessageFragment : BaseCoroutineStateFragment<Unit>(
    Unit
) {
    override val layoutId: Int = R.layout.message_fragment

    private val inputMethodManager: InputMethodManager by lazy {
        requireActivity().getSystemService<InputMethodManager>()!!
    }

    private val fileExplore: FileExplore by lazy {
        (requireActivity() as FileTransportActivity).fileExplore
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {  }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = MessageFragmentBinding.bind(contentView)
        val context = requireActivity() as FileTransportActivity

        viewBinding.messageRv.adapter = SimpleAdapterBuilderImpl<FileTransportActivity.Companion.Message>(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.message_item_layout),
            dataSource = FlowDataSourceImpl(context.observeMessages()),
            dataBinder = DataBinderImpl { data, view, _ ->
                val itemViewBinding = MessageItemLayoutBinding.bind(view)
                val isRemote = data.fromRemote
                if (isRemote) {
                    itemViewBinding.remoteMessageTv.visibility = View.VISIBLE
                    itemViewBinding.myMessageTv.visibility = View.GONE
                    itemViewBinding.remoteMessageTv.text = data.msg
                } else {
                    itemViewBinding.remoteMessageTv.visibility = View.GONE
                    itemViewBinding.myMessageTv.visibility = View.VISIBLE
                    itemViewBinding.myMessageTv.text = data.msg
                }
            }
        ).build()

        viewBinding.sendLayout.clicks(this) {
            val text = viewBinding.editText.text.toString()
            if (text.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        fileExplore.requestMsgSuspend(text)
                    }.onSuccess {
                        context.updateNewMessage(
                            FileTransportActivity.Companion.Message(
                                time = System.currentTimeMillis(),
                                msg = text,
                                fromRemote = false
                            )
                        )
                        AndroidLog.d(TAG, "Send msg success.")
                        withContext(Dispatchers.Main) {
                            viewBinding.editText.text?.clear()
                        }
                    }.onFailure {
                        AndroidLog.e(TAG, "Send msg fail: $it", it)
                    }
                }
            }
        }

        launch {
            context.stateFlow()
                .map { it.selectedTabType }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Main)
                .collect {
                    inputMethodManager.hideSoftInputFromWindow(viewBinding.editText.windowToken, 0)
                }
        }

        KeyboardVisibilityEvent.registerEventListener(requireActivity(), object : KeyboardVisibilityEventListener {
            override fun onVisibilityChanged(isOpen: Boolean) {
                if (isOpen) {
                    launch {
                        val messages = context.observeMessages().first()
                        if (messages.isNotEmpty()) {
                            viewBinding.messageRv.scrollToPosition(0)
                        }
                    }
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.editLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom + v.paddingBottom)

            insets
        }
    }

    companion object {
        private const val TAG = "MessageFragment"
    }

}