package com.tans.tfiletransporter.ui.filetransport

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
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
import com.tans.tuiutils.dialog.BaseCoroutineStateForceResultDialogFragment
import com.tans.tuiutils.dialog.DialogForceResultCallback
import com.tans.tuiutils.mediastore.insertMediaFilesToMediaStore
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.lang.ref.WeakReference
import java.net.InetAddress
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.jvm.optionals.getOrNull

class FileDownloaderDialog : BaseCoroutineStateForceResultDialogFragment<FileTransferDialogState, FileTransferResult> {

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

    private val hasInvokeCallback: AtomicBoolean by lazy {
        AtomicBoolean(false)
    }

    constructor() : super(FileTransferDialogState(), null) {
        this.senderAddress = null
        this.files = null
        this.downloadDir = null
        this.maxConnectionSize = null
    }

    constructor(
        senderAddress: InetAddress,
        files: List<FileExploreFile>,
        downloadDir: File,
        maxConnectionSize: Int,
        callback: DialogForceResultCallback<FileTransferResult>) : super(FileTransferDialogState(), callback) {
        this.senderAddress = senderAddress
        this.files = files
        this.downloadDir = downloadDir
        this.maxConnectionSize = maxConnectionSize
    }

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context)
            .inflate(R.layout.reading_writing_files_dialog_layout, parent, false)
    }

    override fun firstLaunchInitData() {
        val senderAddress = this.senderAddress ?: return
        val files = this.files ?: return
        val downloadDir = this.downloadDir ?: return
        val maxConnectionSize = this.maxConnectionSize ?: return
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
            downloader.addObserver(object : FileTransferObserver {
                override fun onNewState(s: FileTransferState) {
                    when (s) {
                        FileTransferState.NotExecute -> {}
                        FileTransferState.Started -> {
                            speedCalculator.start()
                        }
                        FileTransferState.Canceled -> {
                            speedCalculator.stop()
                            onResult(FileTransferResult.Cancel)
                        }
                        FileTransferState.Finished -> {
                            speedCalculator.stop()
                            onResult(FileTransferResult.Finished)
                        }
                        is FileTransferState.Error -> {
                            speedCalculator.stop()
                            onResult(FileTransferResult.Error(s.msg))
                        }
                        is FileTransferState.RemoteError -> {
                            speedCalculator.stop()
                            onResult(FileTransferResult.Error("Remote error: ${s.msg}"))
                        }
                    }
                }

                override fun onStartFile(file: FileExploreFile) {
                    speedCalculator.reset()
                    updateState {
                        FileTransferDialogState(
                            transferFile = Optional.of(file),
                            process = 0L
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
                    val mimeAndMediaType = getMediaMimeTypeWithFileName(file.name)
                    if (mimeAndMediaType != null) {
                        runCatching {
                            insertMediaFilesToMediaStore(
                                filesToMimeType = mapOf(File(downloadDir, file.name) to mimeAndMediaType.first)
                            )
                        }
                    }
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

suspend fun FragmentManager.showFileDownloaderDialog(
    senderAddress: InetAddress,
    files: List<FileExploreFile>,
    downloadDir: File,
    maxConnectionSize: Int
): FileTransferResult = suspendCancellableCoroutine { cont ->
    val d = FileDownloaderDialog(
        senderAddress = senderAddress,
        files = files,
        downloadDir = downloadDir,
        maxConnectionSize = maxConnectionSize,
        callback = object : DialogForceResultCallback<FileTransferResult> {
            override fun onError(e: String) {
                if (cont.isActive) {
                    cont.resume(FileTransferResult.Error(e))
                }
            }

            override fun onResult(t: FileTransferResult) {
                if (cont.isActive) {
                    cont.resume(t)
                }
            }
        }
    )
    d.show(this, "FileDownloaderDialog#${System.currentTimeMillis()}")
    val wd = WeakReference(d)
    cont.invokeOnCancellation {
        wd.get()?.dismissSafe()
    }
}