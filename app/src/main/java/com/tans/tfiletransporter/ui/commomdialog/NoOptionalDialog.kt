package com.tans.tfiletransporter.ui.commomdialog

import android.view.View
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.NoOptionalDialogLayoutBinding
import com.tans.tuiutils.dialog.BaseSimpleCoroutineResultCancelableDialogFragment
import com.tans.tuiutils.view.clicks

class NoOptionalDialog : BaseSimpleCoroutineResultCancelableDialogFragment<Unit, Unit> {

    private val title: String?
    private val message: String?
    private val positiveButtonText: String?

    override val layoutId: Int = R.layout.no_optional_dialog_layout

    constructor() : super(Unit) {
        title = null
        message = null
        positiveButtonText = null
    }

    constructor(
        title: String,
        message: String,
        positiveButtonText: String,
    ) : super(Unit) {
        this.title = title
        this.message = message
        this.positiveButtonText = positiveButtonText
    }

    override fun firstLaunchInitData() {

    }

    override fun bindContentView(view: View) {
        val viewBinding = NoOptionalDialogLayoutBinding.bind(view)
        viewBinding.titleTv.text = title ?: ""
        viewBinding.messageTv.text = message ?: ""
        viewBinding.positiveButton.text = positiveButtonText ?: ""

        viewBinding.positiveButton.clicks(this) {
            onResult(Unit)
        }
    }
}