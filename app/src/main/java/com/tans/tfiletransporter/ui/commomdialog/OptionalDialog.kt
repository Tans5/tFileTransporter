package com.tans.tfiletransporter.ui.commomdialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.OptionalDialogLayoutBinding
import com.tans.tuiutils.dialog.BaseCoroutineStateCancelableResultDialogFragment
import com.tans.tuiutils.dialog.DialogCancelableResultCallback
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

class OptionalDialog : BaseCoroutineStateCancelableResultDialogFragment<Unit, Boolean> {

    private val title: String?
    private val message: String?
    private val positiveButtonText: String?
    private val negativeButtonText: String?
    constructor() : super(Unit, null) {
        title = null
        message = null
        positiveButtonText = null
        negativeButtonText = null
    }

    constructor(
        title: String,
        message: String,
        positiveButtonText: String,
        negativeButtonText: String,
        callback: DialogCancelableResultCallback<Boolean>) : super(Unit, callback) {
        this.title = title
        this.message = message
        this.positiveButtonText = positiveButtonText
        this.negativeButtonText = negativeButtonText
    }

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.optional_dialog_layout, parent, false)
    }

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

suspend fun FragmentManager.showOptionalDialogSuspend(
    title: String,
    message: String,
    positiveButtonText: String = "OK",
    negativeButtonText: String = "NO"
): Boolean? {
    return suspendCancellableCoroutine { cont ->
        val d = OptionalDialog(
            title = title,
            message = message,
            positiveButtonText = positiveButtonText,
            negativeButtonText = negativeButtonText,
            callback = object : DialogCancelableResultCallback<Boolean> {
                override fun onResult(t: Boolean) {
                    if (cont.isActive) {
                        cont.resume(t)
                    }
                }

                override fun onCancel() {
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }
            }
        )
        d.show(this, "OptionalDialog#${System.currentTimeMillis()}")
        val wd = WeakReference(d)
        cont.invokeOnCancellation {
            wd.get()?.dismissSafe()
        }
    }
}