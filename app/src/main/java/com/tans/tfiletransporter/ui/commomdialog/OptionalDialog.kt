package com.tans.tfiletransporter.ui.commomdialog

import android.view.View
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.OptionalDialogLayoutBinding
import com.tans.tuiutils.dialog.BaseSimpleCoroutineResultCancelableDialogFragment
import com.tans.tuiutils.view.clicks

class OptionalDialog : BaseSimpleCoroutineResultCancelableDialogFragment<Unit, Boolean> {

    private val title: String?
    private val message: String?
    private val positiveButtonText: String?
    private val negativeButtonText: String?

    constructor() : super(Unit) {
        title = null
        message = null
        positiveButtonText = null
        negativeButtonText = null
    }

    constructor(
        title: String,
        message: String,
        positiveButtonText: String,
        negativeButtonText: String) : super(Unit) {
        this.title = title
        this.message = message
        this.positiveButtonText = positiveButtonText
        this.negativeButtonText = negativeButtonText
    }

    override val layoutId: Int = R.layout.optional_dialog_layout

    override fun firstLaunchInitData() {

    }

    override fun bindContentView(view: View) {
        val viewBinding = OptionalDialogLayoutBinding.bind(view)
        viewBinding.titleTv.text = title ?: ""
        viewBinding.messageTv.text = message ?: ""
        viewBinding.positiveButton.text = positiveButtonText ?: ""
        viewBinding.negativeButton.text = negativeButtonText ?: ""

        viewBinding.positiveButton.clicks(this) {
            onResult(true)
        }
        viewBinding.negativeButton.clicks(this) {
            onResult(false)
        }
    }
}