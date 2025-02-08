package com.tans.tfiletransporter.ui.commomdialog

import android.os.IBinder
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.TextInputDialogBinding
import com.tans.tuiutils.dialog.BaseSimpleCoroutineResultCancelableDialogFragment
import com.tans.tuiutils.view.clicks

class TextInputDialog : BaseSimpleCoroutineResultCancelableDialogFragment<Unit, String> {

    private val hintText: String?

    override val layoutId: Int = R.layout.text_input_dialog

    constructor() : super(Unit ) {
        hintText = null
    }
    constructor(hintText: String) : super(Unit) {
        this.hintText = hintText
    }

    override fun firstLaunchInitData() {

    }

    private var editWindowToken: IBinder? = null
    override fun bindContentView(view: View) {
        val viewBinding = TextInputDialogBinding.bind(view)
        viewBinding.textEt.hint = hintText ?: ""
        viewBinding.cancelBt.clicks(this) {
            onCancel()
            viewBinding.textEt.clearFocus()
        }
        viewBinding.okBt.clicks(this) {
            val text = viewBinding.textEt.text?.toString()
            if (!text.isNullOrBlank()) {
                onResult(text)
            } else {
                onCancel()
            }
            viewBinding.textEt.clearFocus()
        }
        editWindowToken = viewBinding.textEt.windowToken
    }

    override fun onDestroy() {
        super.onDestroy()
        val ctx = context
        val windowToken = this.editWindowToken
        if (ctx != null && windowToken != null) {
            val inputMethodManager = ctx.getSystemService<InputMethodManager>()
            inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
        }
    }
}