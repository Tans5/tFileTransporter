package com.tans.tfiletransporter.ui.filetransport

import android.annotation.SuppressLint
import android.media.MediaScannerConnection
import android.view.View
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ReadingWritingFilesDialogLayoutBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.filetransfer.FileDownloader
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferObserver
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferState
import com.tans.tfiletransporter.transferproto.filetransfer.SpeedCalculator
import com.tans.tfiletransporter.utils.getMediaMimeTypeWithFileName
import com.tans.tuiutils.dialog.BaseSimpleCoroutineResultForceDialogFragment
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull

class FileDownloaderDialog : BaseSimpleCoroutineResultForceDialogFragment<FileTransferDialogState, FileTransferResult> {

    private val senderAddress: InetAddress?
    private val files: List<FileExploreFile>?
    private val downloadDir: File?
    private val maxConnectionSize: Int?

    private val downloader: AtomicReference<FileDownloader?> by lazy {
        AtomicReference(null)
    }

    private val speedCalculator: AtomicReference<SpeedCalculator?> by lazy {
        AtomicReference(null)
    }

    override val layoutId: Int = R.layout.reading_writing_files_dialog_layout

    constructor() : super(FileTransferDialogState()) {
        this.senderAddress = null
        this.files = null
        this.downloadDir = null
        this.maxConnectionSize = null
    }

    constructor(
        senderAddress: InetAddress,
        files: List<FileExploreFile>,
        downloadDir: File,
        maxConnectionSize: Int) : super(FileTransferDialogState()) {
        this.senderAddress = senderAddress
        this.files = files
        this.downloadDir = downloadDir
        this.maxConnectionSize = maxConnectionSize
    }

    override fun firstLaunchInitData() {
        val senderAddress = this.senderAddress ?: return
        val files = this.files ?: return
        val downloadDir = this.downloadDir ?: return
        val maxConnectionSize = this.maxConnectionSize ?: return
        val ctx = activity?.application ?: return
        launch {
            val downloader = FileDownloader(
                files = files,
                downloadDir = downloadDir,
                connectAddress = senderAddress,
                maxConnectionSize = maxConnectionSize.toLong(),
                log = AndroidLog
            )
            this@FileDownloaderDialog.downloader.get()?.cancel()
            this@FileDownloaderDialog.downloader.set(downloader)
            val speedCalculator = SpeedCalculator()
            speedCalculator.addObserver(object : SpeedCalculator.Companion.SpeedObserver {
                override fun onSpeedUpdated(speedInBytes: Long, speedInString: String) {
                    updateState {
                        it.copy(speedString = speedInString)
                    }
                }
            })
            this@FileDownloaderDialog.speedCalculator.set(speedCalculator)
            fun checkFinishedFileAndInsertToMediaStore() {
                val finishedFiles = this@FileDownloaderDialog.currentState().finishedFiles
                if (finishedFiles.isNotEmpty()) {
                    Dispatchers.IO.asExecutor().execute {
                        val pathAndMimeType = finishedFiles.mapNotNull {
                            val mimeType = getMediaMimeTypeWithFileName(it.name)?.first
                            if (mimeType != null) {
                                File(downloadDir, it.name).canonicalPath to mimeType
                            } else {
                                null
                            }
                        }
                        if (pathAndMimeType.isNotEmpty()) {
                            MediaScannerConnection.scanFile(
                                ctx,
                                pathAndMimeType.map { it.first }.toTypedArray(),
                                pathAndMimeType.map { it.second }.toTypedArray(), null
                            )
                        }
                    }
                }
            }
            downloader.addObserver(object : FileTransferObserver {
                override fun onNewState(s: FileTransferState) {
                    when (s) {
                        FileTransferState.NotExecute -> {}
                        FileTransferState.Started -> {
                            speedCalculator.start()
                        }
                        FileTransferState.Canceled -> {
                            checkFinishedFileAndInsertToMediaStore()
                            speedCalculator.stop()
                            onResult(FileTransferResult.Cancel)
                        }
                        FileTransferState.Finished -> {
                            checkFinishedFileAndInsertToMediaStore()
                            speedCalculator.stop()
                            onResult(FileTransferResult.Finished)
                        }
                        is FileTransferState.Error -> {
                            checkFinishedFileAndInsertToMediaStore()
                            speedCalculator.stop()
                            onResult(FileTransferResult.Error(s.msg))
                        }
                        is FileTransferState.RemoteError -> {
                            checkFinishedFileAndInsertToMediaStore()
                            speedCalculator.stop()
                            onResult(FileTransferResult.Error("Remote error: ${s.msg}"))
                        }
                    }
                }

                override fun onStartFile(file: FileExploreFile) {
                    speedCalculator.reset()
                    updateState {
                        it.copy(
                            transferFile = Optional.of(file),
                            process = 0L,
                            speedString = ""
                        )
                    }
                }

                override fun onProgressUpdate(file: FileExploreFile, progress: Long) {
                    speedCalculator.updateCurrentSize(progress)
                    updateState { oldState ->
                        oldState.copy(process = progress)
                    }
                }

                override fun onEndFile(file: FileExploreFile) {
                    updateState { oldState -> oldState.copy(finishedFiles = oldState.finishedFiles + file) }
                }

            })
            downloader.start()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun bindContentView(view: View) {
        val files = this.files ?: return
        val viewBinding = ReadingWritingFilesDialogLayoutBinding.bind(view)
        renderStateNewCoroutine({ it.transferFile }) {
            val file = it.getOrNull()
            if (file != null) {
                viewBinding.titleTv.text = requireContext().getString(
                    R.string.downloading_files_dialog_title,
                    files.indexOf(file) + 1, files.size
                )
                viewBinding.fileNameTv.text = file.name
            } else {
                viewBinding.titleTv.text = ""
                viewBinding.fileNameTv.text = ""
            }
        }

        renderStateNewCoroutine({ it.transferFile to it. process }) {
            val file = it.first.getOrNull()
            val process = it.second
            if (file != null) {
                val processInPercent = process * 100L / file.size
                viewBinding.filePb.progress = processInPercent.toInt()
                viewBinding.fileDealSizeTv.text = "${process.toSizeString()}/${file.size.toSizeString()}"
            } else {
                viewBinding.filePb.progress = 0
                viewBinding.fileDealSizeTv.text = ""
            }
        }

        renderStateNewCoroutine({ it.speedString }) {
            viewBinding.speedTv.text = it
        }

        viewBinding.cancelButton.clicks(this, 1000L) {
            onResult(FileTransferResult.Cancel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloader.get()?.cancel()
        speedCalculator.get()?.stop()
    }
}