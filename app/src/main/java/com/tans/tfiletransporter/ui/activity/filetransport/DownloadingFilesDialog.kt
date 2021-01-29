package com.tans.tfiletransporter.ui.activity.filetransport

import android.app.Activity
import android.app.Dialog
import android.os.Environment
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ReadingWritingFilesDialogLayoutBinding
import com.tans.tfiletransporter.net.NET_BUFFER_SIZE
import com.tans.tfiletransporter.net.model.File
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import com.tans.tfiletransporter.utils.getSizeString
import com.tans.tfiletransporter.utils.newChildFile
import com.tans.tfiletransporter.utils.readFrom
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

fun Activity.startDownloadingFiles(files: List<File>, inputStream: InputStream): Single<Unit> {
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
                        val downloadDir = Paths.get(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path, getString(R.string.app_name))
                        if (!Files.exists(downloadDir)) {
                            Files.createDirectory(downloadDir)
                        }
                        val buffer: ByteBuffer = ByteBuffer.allocate(NET_BUFFER_SIZE)
                        val reader = Channels.newChannel(inputStream)
                        for ((i, f) in files.withIndex()) {
                            val fileSizeString = getSizeString(f.size)
                            withContext(Dispatchers.Main) {
                                binding.titleTv.text = getString(R.string.downloading_files_dialog_title, i + 1, files.size)
                                binding.fileNameTv.text = f.name
                                binding.filePb.progress = 0
                                binding.fileDealSizeTv.text = getString(R.string.file_deal_progress, getSizeString(0L), fileSizeString)
                            }
                            val fPath = downloadDir.newChildFile(f.name)
                            if (f.size <=0 ) continue
                            try {
                                val fileWriter = FileChannel.open(fPath, StandardOpenOption.WRITE)
                                fileWriter.use {
                                    fileWriter.readFrom(
                                            readable = reader,
                                            buffer = buffer,
                                            limit = f.size) { downloadSize, _ ->
                                        withContext(Dispatchers.Main) {
                                            binding.filePb.progress = ((downloadSize.toDouble() / f.size.toDouble()) * 100.0).toInt()
                                            binding.fileDealSizeTv.text = getString(R.string.file_deal_progress, getSizeString(downloadSize), fileSizeString)
                                        }
                                    }
                                }
                            } catch (e: Throwable) {
                                Files.delete(fPath)
                                throw e
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