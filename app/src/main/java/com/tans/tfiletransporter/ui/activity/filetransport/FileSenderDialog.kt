package com.tans.tfiletransporter.ui.activity.filetransport

import android.annotation.SuppressLint
import android.app.Activity
import com.jakewharton.rxbinding3.view.clicks
import com.tans.rxutils.ignoreSeveralClicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ReadingWritingFilesDialogLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.resumeIfActive
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.filetransfer.FileSender
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferObserver
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferState
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull

class FileSenderDialog(
    private val context: Activity,
    private val bindAddress: InetAddress,
    private val files: List<SenderFile>,
    private val bufferSize: Long,
    private val callback: (result: FileTransferResult) -> Unit
) : BaseCustomDialog<ReadingWritingFilesDialogLayoutBinding, FileTransferDialogState>(
    context = context,
    layoutId = R.layout.reading_writing_files_dialog_layout,
    defaultState = FileTransferDialogState(),
    outSizeCancelable = false
) {
    private val sender: AtomicReference<FileSender?> by lazy {
        AtomicReference(null)
    }

    private val hasInvokeCallback: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    @SuppressLint("SetTextI18n")
    override fun bindingStart(binding: ReadingWritingFilesDialogLayoutBinding) {
        setCancelable(false)
        launch(Dispatchers.IO) {
            val sender = FileSender(
                files = files,
                bindAddress = bindAddress,
                bufferSize = bufferSize,
                log = AndroidLog
            )
            this@FileSenderDialog.sender.get()?.cancel()
            this@FileSenderDialog.sender.set(sender)
            sender.addObserver(object : FileTransferObserver {
                override fun onNewState(s: FileTransferState) {
                    when (s) {
                        FileTransferState.NotExecute -> {}
                        FileTransferState.Started -> {}
                        FileTransferState.Canceled -> {
                            if (hasInvokeCallback.compareAndSet(false, true)) {
                                callback(FileTransferResult.Cancel)
                            }
                        }
                        FileTransferState.Finished -> {
                            if (hasInvokeCallback.compareAndSet(false, true)) {
                                callback(FileTransferResult.Finished)
                                launch(Dispatchers.Main) {
                                    cancel()
                                }
                            }
                        }
                        is FileTransferState.Error -> {
                            if (hasInvokeCallback.compareAndSet(false, true)) {
                                callback(FileTransferResult.Error(s.msg))
                                launch(Dispatchers.Main) {
                                    cancel()
                                }
                            }
                        }
                        is FileTransferState.RemoteError -> {
                            if (hasInvokeCallback.compareAndSet(false, true)) {
                                callback(FileTransferResult.Error(s.msg))
                                launch(Dispatchers.Main) {
                                    cancel()
                                }
                            }
                        }
                    }
                }

                override fun onStartFile(file: FileExploreFile) {
                    launch {
                        updateState {
                            FileTransferDialogState(
                                transferFile = Optional.of(file),
                                process = 0L
                            )
                        }.await()
                    }
                }

                override fun onProgressUpdate(file: FileExploreFile, progress: Long) {
                    launch {
                        updateState { oldState ->
                            oldState.copy(process = progress)
                        }.await()
                    }
                }

                override fun onEndFile(file: FileExploreFile) {}

            })
            sender.start()
        }

        val exploreFiles = files.map { it.exploreFile }.toList()
        render({ it.transferFile }) {
            val file = it.getOrNull()
            if (file != null) {
                binding.titleTv.text = context.getString(
                    R.string.sending_files_dialog_title,
                    exploreFiles.indexOf(file) + 1, exploreFiles.size
                )
                binding.fileNameTv.text = file.name
            } else {
                binding.titleTv.text = ""
                binding.fileNameTv.text = ""
            }
        }.bindLife()

        render({ it.transferFile to it.process }) {
            val file = it.first.getOrNull()
            val process = it.second
            if (file != null) {
                val processInPercent = process.toDouble() / file.size.toDouble() * 100.0
                binding.filePb.progress = processInPercent.toInt()
                binding.fileDealSizeTv.text = "${process.toSizeString()}/${file.size.toSizeString()}"
            } else {
                binding.filePb.progress = 0
                binding.fileDealSizeTv.text = ""
            }
        }.bindLife()

        binding.cancelButton.clicks()
            .ignoreSeveralClicks(1000L)
            .observeOn(Schedulers.io())
            .doOnNext { sender.get()?.cancel() }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                cancel()
            }
            .bindLife()
    }

    override fun onStop() {
        super.onStop()
        sender.get()?.cancel()
    }
}

sealed class FileTransferResult {
    data class Error(val msg: String) : FileTransferResult()
    object Cancel : FileTransferResult()
    object Finished : FileTransferResult()
}

data class FileTransferDialogState(
    val transferFile: Optional<FileExploreFile> = Optional.empty(),
    val process: Long = 0L
)

suspend fun Activity.showFileSenderDialog(
    bindAddress: InetAddress,
    files: List<SenderFile>,
    bufferSize: Long,
): FileTransferResult = suspendCancellableCoroutine { cont ->
    val d = FileSenderDialog(
        context = this,
        bindAddress = bindAddress,
        files = files,
        bufferSize = bufferSize,
        callback = { result ->
            cont.resumeIfActive(result)
        }
    )
    d.show()
}