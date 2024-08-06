package com.tans.tfiletransporter.ui.commomdialog

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.tans.tfiletransporter.R
import com.tans.tuiutils.dialog.BaseDialogFragment
import com.tans.tuiutils.dialog.createDefaultDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoadingDialog : BaseDialogFragment() {
    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.loading_dialog_layout, parent, false)
    }
    override fun firstLaunchInitData() {}
    override fun bindContentView(view: View) {}
    override fun createDialog(contentView: View): Dialog {
        isCancelable = false
        return requireActivity().createDefaultDialog(
            contentView = contentView,
            isCancelable = false,
            dimAmount = 0.0f
        )
    }
}

suspend fun <T> FragmentManager.loadingDialogSuspend(job: suspend () -> T): T {
    val loadingDialog = LoadingDialog()
    val isShowLoading = withContext(Dispatchers.Main.immediate) {
        if (!isDestroyed) {
            loadingDialog.showSafe(this@loadingDialogSuspend, "LoadingDialog#${System.currentTimeMillis()}")
            true
        } else {
            false
        }
    }
    val result = job()
    withContext(Dispatchers.Main.immediate) {
        if (!isDestroyed && isShowLoading) {
            loadingDialog.dismissAllowingStateLoss()
        }
    }
    return result
}