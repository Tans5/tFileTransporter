package com.tans.tfiletransporter.ui.filetransport

import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.MyDirFragmentBinding
import com.tans.tfiletransporter.ui.BaseFragment

class MyDirFragment : BaseFragment<MyDirFragmentBinding, Unit>(R.layout.my_dir_fragment, Unit) {

    companion object {
        const val FRAGMENT_TAG = "my_dir_fragment_tag"
    }
}