package com.tans.tfiletransporter.ui.activity.filetransport

import android.widget.Toast
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.RemoteDirFragmentBinding
import com.tans.tfiletransporter.ui.activity.BaseFragment
import org.kodein.di.instance

class RemoteDirFragment : BaseFragment<RemoteDirFragmentBinding, Unit>(R.layout.remote_dir_fragment, Unit) {

    private val fileTransportScopeData: FileTransportScopeData by instance()

    override fun onInit() {
        fileTransportScopeData.floatBtnEvent
                .filter { !isHidden }
                .doOnNext {
                    Toast.makeText(requireContext(), "Message From Remote Dir", Toast.LENGTH_SHORT).show()
                }
                .bindLife()
    }

    companion object {
        const val FRAGMENT_TAG = "remote_dir_fragment_tag"
    }
}