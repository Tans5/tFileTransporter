package com.tans.tfiletransporter.ui.activity.filetransport

import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.MessageFragmentBinding
import com.tans.tfiletransporter.ui.activity.BaseFragment

data class Message(
        val isRemote: Boolean,
        val timeMilli: Long,
        val message: String
)

class MessageFragment : BaseFragment<MessageFragmentBinding, List<Message>>(R.layout.message_fragment, emptyList()) {

    override fun onInit() {

    }

    companion object {
        const val FRAGMENT_TAG = "message_tag"
    }

}