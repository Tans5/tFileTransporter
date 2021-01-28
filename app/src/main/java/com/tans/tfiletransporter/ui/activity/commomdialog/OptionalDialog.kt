package com.tans.tfiletransporter.ui.activity.commomdialog

import android.app.Activity
import android.app.Dialog
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import io.reactivex.Single
import java.util.*

fun Activity.showOptionalDialog(
    title: String,
    message: String,
    positiveButtonText: String = "OK",
    negativeButtonText: String = "NO",
    cancelable: Boolean = true
): Single<Optional<Boolean>> {
    var dialog: Dialog? = null
    return Single.create<Optional<Boolean>> { emitter ->
        val dialogInternal = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText) { dialog, _ ->
                if (!emitter.isDisposed) {
                    emitter.onSuccess(Optional.of(true))
                }
                dialog.dismiss()
            }
            .setNegativeButton(negativeButtonText) { dialog, _ ->
                if (!emitter.isDisposed) {
                    emitter.onSuccess(Optional.of(false))
                }
                dialog.dismiss()
            }
            .setOnCancelListener {
                if (!emitter.isDisposed) {
                    emitter.onSuccess(Optional.empty())
                }
            }
            .setCancelable(cancelable)
            .create()
        dialogInternal.window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        dialogInternal.show()
        dialog = dialogInternal
    }.doFinally {
        val dialogInternal = dialog
        if (dialogInternal?.isShowing == true) { dialogInternal.cancel() }
    }
}