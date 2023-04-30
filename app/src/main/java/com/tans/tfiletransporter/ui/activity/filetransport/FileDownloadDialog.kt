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
import com.tans.tfiletransporter.transferproto.filetransfer.FileDownloader
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferObserver
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferState
import com.tans.tfiletransporter.ui.activity.BaseCustomDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import kotlinx.coroutines.rx2.rxSingle
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.net.InetAddress
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull

class FileDownloaderDialog(
    private val context: Activity,
    private val senderAddress: InetAddress,
    private val files: List<FileExploreFile>,
    private val downloadDir: File,
    private val maxConnectionSize: Int,
    private val callback: (result: FileTransferResult) -> Unit
) : BaseCustomDialog<ReadingWritingFilesDialogLayoutBinding, FileTransferDialogState>(
    context = context,
    layoutId = R.layout.reading_writing_files_dialog_layout,
    defaultState = FileTransferDialogState(),
    outSizeCancelable = false
) {
    private val downloader: AtomicReference<FileDownloader?> by lazy {
        AtomicReference(null)
    }

    private val hasInvokeCallback: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    @SuppressLint("SetTextI18n")
    override fun bindingStart(binding: ReadingWritingFilesDialogLayoutBinding) {
        setCancelable(false)
        launch(Dispatchers.IO) {
            val downloader = FileDownloader(
                files = files,
                downloadDir = downloadDir,
                connectAddress = senderAddress,
                maxConnectionSize = maxConnectionSize.toLong(),
                log = AndroidLog
            )
            this@FileDownloaderDialog.downloader.get()?.cancel()
            this@FileDownloaderDialog.downloader.set(downloader)
            downloader.addObserver(object : FileTransferObserver {
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
                            }
                            rxSingle(Dispatchers.Main) {
                                cancel()
                            }.bindLife()
                        }
                        is FileTransferState.Error -> {
                            if (hasInvokeCallback.compareAndSet(false, true)) {
                                callback(FileTransferResult.Error(s.msg))
                            }
                            rxSingle(Dispatchers.Main) {
                                cancel()
                            }.bindLife()
                        }
                        is FileTransferState.RemoteError -> {
                            if (hasInvokeCallback.compareAndSet(false, true)) {
                                callback(FileTransferResult.Error(s.msg))
                            }
                            rxSingle(Dispatchers.Main) {
                                cancel()
                            }.bindLife()
                        }
                    }
                }

                override fun onStartFile(file: FileExploreFile) {
                    rxSingle {
                        updateState {
                            FileTransferDialogState(
                                transferFile = Optional.of(file),
                                process = 0L
                            )
                        }.await()
                    }.bindLife()
                }

                override fun onProgressUpdate(file: FileExploreFile, progress: Long) {
                    rxSingle {
                        updateState { oldState ->
                            oldState.copy(process = progress)
                        }.await()
                    }.bindLife()
                }

                override fun onEndFile(file: FileExploreFile) {}

            })
            downloader.start()
        }

        render({ it.transferFile }) {
            val file = it.getOrNull()
            if (file != null) {
                binding.titleTv.text = context.getString(
                    R.string.downloading_files_dialog_title,
                    files.indexOf(file) + 1, files.size
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
                val processInPercent = process * 100L / file.size
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
            .doOnNext { downloader.get()?.cancel() }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                cancel()
            }
            .bindLife()
    }

    override fun onStop() {
        super.onStop()
        downloader.get()?.cancel()
    }
}

suspend fun Activity.showFileDownloaderDialog(
    senderAddress: InetAddress,
    files: List<FileExploreFile>,
    downloadDir: File,
    maxConnectionSize: Int
): FileTransferResult = suspendCancellableCoroutine { cont ->
    val d = FileDownloaderDialog(
        context = this,
        senderAddress = senderAddress,
        files = files,
        maxConnectionSize = maxConnectionSize,
        downloadDir = downloadDir,
        callback = { result ->
            cont.resumeIfActive(result)
        }
    )
    d.show()
}