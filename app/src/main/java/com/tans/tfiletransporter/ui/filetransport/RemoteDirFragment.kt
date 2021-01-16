package com.tans.tfiletransporter.ui.filetransport

import android.util.Log
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.RemoteDirFragmentBinding
import com.tans.tfiletransporter.ui.BaseFragment
import io.reactivex.disposables.Disposable

class RemoteDirFragment : BaseFragment<RemoteDirFragmentBinding, Unit>(R.layout.remote_dir_fragment, Unit) {

    private var downloadBtDispose: Disposable? = null
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
//        downloadBtDispose = if (!hidden) {
//            requireActivity().findViewById<FloatingActionButton>(R.id.floating_action_bt).clicks()
//                .doOnNext {
//                    println("Download Next...")
//                }
//                .subscribe({
//
//                }, {
//                    Log.e(this::class.java.name, "Share Button Error: $it")
//                })
//        } else {
//            downloadBtDispose?.dispose()
//            null
//        }
    }

    companion object {
        const val FRAGMENT_TAG = "remote_dir_fragment_tag"
    }
}