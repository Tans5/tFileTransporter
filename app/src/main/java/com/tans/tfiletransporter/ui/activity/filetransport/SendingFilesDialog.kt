package com.tans.tfiletransporter.ui.activity.filetransport

import android.app.Activity
import android.app.Dialog
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ReadingWritingFilesDialogLayoutBinding
import com.tans.tfiletransporter.file.FileConstants
import com.tans.tfiletransporter.net.NET_BUFFER_SIZE
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import com.tans.tfiletransporter.utils.getSizeString
import com.tans.tfiletransporter.utils.writeTo
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption


fun Activity.startSendingFiles(files: List<File>, outputStream: OutputStream): Single<Unit> {
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
                        val writer = Channels.newChannel(outputStream)
                        val buffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
                        for ((i, f) in files.withIndex()) {
                            val fileSizeString = getSizeString(f.size)
                            withContext(Dispatchers.Main) {
                                binding.titleTv.text = getString(R.string.sending_files_dialog_title, i + 1, files.size)
                                binding.fileNameTv.text = f.name
                                binding.filePb.progress = 0
                                binding.fileDealSizeTv.text = getString(R.string.file_deal_progress, getSizeString(0L), fileSizeString)
                            }
                            val reader = FileChannel.open(Paths.get(FileConstants.homePathString + f.path), StandardOpenOption.READ)
                            reader.use {
                                reader.writeTo(writer, f.size, buffer) { sending, _ ->
                                    withContext(Dispatchers.Main) {
                                        binding.filePb.progress = ((sending.toDouble() / f.size.toDouble()) * 100.0).toInt()
                                        binding.fileDealSizeTv.text = getString(R.string.file_deal_progress, getSizeString(sending), fileSizeString)
                                    }
                                }
                            }
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