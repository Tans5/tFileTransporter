package com.tans.tfiletransporter.ui.activity.filetransport.activity

import android.app.Activity
import android.app.Dialog
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ReadingWritingFilesDialogLayoutBinding
import com.tans.tfiletransporter.net.filetransporter.MultiConnectionsFileTransferClient
import com.tans.tfiletransporter.net.filetransporter.downloadFileObservable
import com.tans.tfiletransporter.net.filetransporter.startMultiConnectionsFileClient
import com.tans.tfiletransporter.net.model.FileMd5
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import com.tans.tfiletransporter.utils.getSizeString
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.util.*

fun Activity.startDownloadingFiles(files: List<FileMd5>, serverAddress: InetAddress): Single<Unit> {
    var dialog: Dialog? = null
    return Single.create<Unit> { emitter ->
        val dialogInternal = object : BaseCustomDialog<ReadingWritingFilesDialogLayoutBinding, Optional<MultiConnectionsFileTransferClient>>(
                context = this,
                layoutId = R.layout.reading_writing_files_dialog_layout,
                defaultState = Optional.empty(),
                outSizeCancelable = false
        ) {

            override fun bindingStart(binding: ReadingWritingFilesDialogLayoutBinding) {

                launch(Dispatchers.IO) {
                    val result = runCatching {
                        for ((i, f) in files.withIndex()) {
                            val fileSizeString = getSizeString(f.file.size)
                            withContext(Dispatchers.Main) {
                                binding.titleTv.text = getString(R.string.downloading_files_dialog_title, i + 1, files.size)
                                binding.fileNameTv.text = f.file.name
                                binding.filePb.progress = 0
                                binding.fileDealSizeTv.text = getString(R.string.file_deal_progress, getSizeString(0L), fileSizeString)
                            }
                            delay(200)
//                            startMultiConnectionsFileClient(
//                                    fileMd5 = f,
//                                    serverAddress = serverAddress,
//                                    clientInstance = { client ->
//                                        updateState { Optional.of(client) }.await()
//                                    }) { hasDownload, limit ->
//                                withContext(Dispatchers.Main) {
//                                    binding.filePb.progress = ((hasDownload.toDouble() / limit.toDouble()) * 100.0).toInt()
//                                    binding.fileDealSizeTv.text = getString(R.string.file_deal_progress, getSizeString(hasDownload), fileSizeString)
//                                }
//                            }
                            downloadFileObservable(
                                fileMd5 = f,
                                serverAddress = serverAddress
                            ).observeOn(AndroidSchedulers.mainThread())
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
                                val activeClient = bindState().firstOrError().await()
                                if (activeClient.isPresent) { activeClient.get().cancel() }
                                withContext(Dispatchers.Main) {
                                    emitter.onError(Throwable("Download Canceled By User"))
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
    }.doFinally {
        val dialogInternal = dialog
        if (dialogInternal?.isShowing == true) { dialogInternal.cancel() }
    }
}