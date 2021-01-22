package com.tans.tfiletransporter.ui.activity.commomdialog

import android.app.Activity
import android.app.Dialog
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.LoadingDialogLayoutBinding
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import io.reactivex.Single

fun Activity.showLoadingDialog(cancelable: Boolean = false): Dialog {
    return object : BaseCustomDialog<LoadingDialogLayoutBinding, Unit>(
        context = this,
        layoutId = R.layout.loading_dialog_layout,
        defaultState = Unit,
        clearBackground = true,
        outSizeCancelable = false
    ) {}.apply { setCancelable(cancelable); show() }
}

fun <T> Single<T>.loadingDialog(context: Activity): Single<T> {
    var dialog: Dialog? = null
    return this.doOnSubscribe {
        dialog = context.showLoadingDialog(false)
    }.doFinally {
        val dialogInternal = dialog
        if (dialogInternal?.isShowing == true) { dialogInternal.cancel() }
    }
}