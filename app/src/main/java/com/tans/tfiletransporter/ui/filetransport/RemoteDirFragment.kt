package com.tans.tfiletransporter.ui.filetransport

import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.RemoteDirFragmentBinding
import com.tans.tfiletransporter.ui.BaseFragment

class RemoteDirFragment : BaseFragment<RemoteDirFragmentBinding, Unit>(R.layout.remote_dir_fragment, Unit) {

    companion object {
        const val FRAGMENT_TAG = "remote_dir_fragment_tag"
    }
}