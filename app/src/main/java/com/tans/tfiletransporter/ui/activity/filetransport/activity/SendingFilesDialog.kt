package com.tans.tfiletransporter.ui.activity.filetransport.activity

import android.app.Activity
import android.app.Dialog
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ReadingWritingFilesDialogLayoutBinding
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.net.netty.filetransfer.PathConverter
import com.tans.tfiletransporter.net.netty.filetransfer.defaultPathConverter
import com.tans.tfiletransporter.net.netty.filetransfer.sendFileObservable
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import com.tans.tfiletransporter.utils.getSizeString
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import java.net.InetAddress


fun Activity.startSendingFiles(files: List<FileMd5>, localAddress: InetAddress, pathConverter: PathConverter = defaultPathConverter): Single<Unit> {
    var dialog: Dialog? = null
    return Single.create<Unit> { emitter ->
        val dialogInternal = object : BaseCustomDialog<ReadingWritingFilesDialogLayoutBinding, Unit>(
                context = this,
                layoutId = R.layout.reading_writing_files_dialog_layout,
                defaultState = Unit,
                outSizeCancelable = false
        ) {

            override fun bindingStart(binding: ReadingWritingFilesDialogLayoutBinding) {

                launch(Dispatchers.IO) {

                    val result = runCatching {
                        for ((i, f) in files.withIndex()) {
                            val fileSizeString = getSizeString(f.file.size)
                            withContext(Dispatchers.Main) {
                                binding.titleTv.text = getString(R.string.sending_files_dialog_title, i + 1, files.size)
                                binding.fileNameTv.text = f.file.name
                                binding.filePb.progress = 0
                                binding.fileDealSizeTv.text = getString(R.string.file_deal_progress, getSizeString(0L), fileSizeString)
                            }
                            sendFileObservable(
                                fileMd5 = f,
                                localAddress = localAddress,
                                pathConverter = pathConverter)
                                .observeOn(AndroidSchedulers.mainThread())
                                .doOnNext {
                                    binding.filePb.progress = ((it.toDouble() / f.file.size.toDouble()) * 100.0).toInt()
                                    binding.fileDealSizeTv.text = getString(R.string.file_deal_progress, getSizeString(it), fileSizeString)
                                }
                                .ignoreElements()
                                .toSingleDefault(Unit)
                                .await()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            emitter.onSuccess(Unit)
                        } else {
                            emitter.onError(result.exceptionOrNull()!!)
                        }
                    }
                }

                binding.cancelButton.clicks()
                        .concatMapSingle {
                            rxSingle {
                                withContext(Dispatchers.Main) {
                                    emitter.onError(Throwable("Sending Files Canceled By User."))
                                    cancel()
                                }
                            }
                        }
                        .bindLife()

            }
        }
        dialogInternal.setCancelable(false)
        dialogInternal.setOnCancelListener { if (!emitter.isDisposed) emitter.onSuccess(Unit) }
        dialogInternal.show()
        dialog = dialogInternal
    }.subscribeOn(AndroidSchedulers.mainThread())
        .doFinally {
        val dialogInternal = dialog
        if (dialogInternal?.isShowing == true) { dialogInternal.cancel() }
    }
}