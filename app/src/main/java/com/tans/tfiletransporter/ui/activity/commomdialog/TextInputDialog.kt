package com.tans.tfiletransporter.ui.activity.commomdialog

import android.app.Activity
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import com.jakewharton.rxbinding4.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.TextInputDialogBinding
import com.tans.tfiletransporter.resumeIfActive
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean

class TextInputDialog(
    private val context: Activity,
    private val hintText: String,
    private val callback: (r: Result) -> Unit) : BaseCustomDialog<TextInputDialogBinding, Unit>(
    context = context,
    layoutId = R.layout.text_input_dialog,
    defaultState = Unit
) {

    private val hasInvokeCallback: AtomicBoolean = AtomicBoolean(false)

    override fun bindingStart(binding: TextInputDialogBinding) {
        super.bindingStart(binding)
        setOnCancelListener {
            if (hasInvokeCallback.compareAndSet(false, true)) {
                callback(Result.Canceled)
            }
            val inputMethodManager = context.getSystemService<InputMethodManager>()
            inputMethodManager?.hideSoftInputFromWindow(binding.textEt.windowToken, 0)
        }
        binding.textEt.hint = hintText

        binding.cancelBt.clicks()
            .doOnNext {
                cancel()
            }
            .bindLife()

        binding.okBt.clicks()
            .doOnNext {
                val text = binding.textEt.text?.toString()
                if (!text.isNullOrBlank()) {
                    if (hasInvokeCallback.compareAndSet(false, true)) {
                        callback(Result.Success(text))
                    }
                }
                binding.textEt.clearFocus()
                cancel()
            }
            .bindLife()
    }

    companion object {
        sealed class Result {

            data class Success(val text: String) : Result()

            object Canceled : Result()
        }
    }
}

suspend fun Activity.showTextInputDialog(hintText: String): TextInputDialog.Companion.Result = suspendCancellableCoroutine { cont ->
    TextInputDialog(
        context = this,
        hintText = hintText,
        callback = { r ->
            cont.resumeIfActive(r)
        }
    ).show()
}