package com.tans.tfiletransporter.ui.commomdialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.NoOptionalDialogLayoutBinding
import com.tans.tuiutils.dialog.BaseCoroutineStateCancelableResultDialogFragment
import com.tans.tuiutils.dialog.DialogCancelableResultCallback
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class NoOptionalDialog : BaseCoroutineStateCancelableResultDialogFragment<Unit, Unit> {

    private val title: String?
    private val message: String?
    private val positiveButtonText: String?
    constructor() : super(Unit, null) {
        title = null
        message = null
        positiveButtonText = null
    }

    constructor(
        title: String,
        message: String,
        positiveButtonText: String,
        callback: DialogCancelableResultCallback<Unit>
    ) : super(Unit, callback) {
        this.title = title
        this.message = message
        this.positiveButtonText = positiveButtonText
    }

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.no_optional_dialog_layout, parent, false)
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

suspend fun FragmentManager.showNoOptionalDialogSuspend(
    title: String,
    message: String,
    positiveButtonText: String = "OK"
): Unit? {
    return suspendCancellableCoroutine { cont ->
        val d = NoOptionalDialog(
            title = title,
            message = message,
            positiveButtonText = positiveButtonText,
            callback = CoroutineDialogCancelableResultCallback(cont)
        )
        val isShow = coroutineShowSafe(d, "NoOptionalDialog#${System.currentTimeMillis()}", cont)
        if (!isShow) {
            cont.resume(null)
        }
    }
}