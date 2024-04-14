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
import com.tans.tfiletransporter.transferproto.filetransfer.FileSender
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferObserver
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferState
import com.tans.tfiletransporter.transferproto.filetransfer.SpeedCalculator
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import com.tans.tfiletransporter.ui.commomdialog.CoroutineDialogForceResultCallback
import com.tans.tfiletransporter.ui.commomdialog.coroutineShowSafe
import com.tans.tuiutils.dialog.BaseCoroutineStateForceResultDialogFragment
import com.tans.tuiutils.dialog.DialogForceResultCallback
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.jvm.optionals.getOrNull

class FileSenderDialog : BaseCoroutineStateForceResultDialogFragment<FileTransferDialogState, FileTransferResult> {

    private val bindAddress: InetAddress?
    private val files: List<SenderFile>?

    private val sender: AtomicReference<FileSender?> by lazy {
        AtomicReference(null)
    }

    private val speedCalculator: AtomicReference<SpeedCalculator?> by lazy {
        AtomicReference(null)
    }

    constructor() : super(FileTransferDialogState(), null) {
        this.bindAddress = null
        this.files = null
    }

    constructor(bindAddress: InetAddress, files: List<SenderFile>, callback: DialogForceResultCallback<FileTransferResult>) : super(
        FileTransferDialogState(), callback
    ) {
        this.bindAddress = bindAddress
        this.files = files
    }

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context)
            .inflate(R.layout.reading_writing_files_dialog_layout, parent, false)
    }

    override fun firstLaunchInitData() {
        val files = this.files ?: return
        val bindAddress = this.bindAddress ?: return
        launch {
            val sender = FileSender(
                files = files,
                bindAddress = bindAddress,
                log = AndroidLog
            )
            this@FileSenderDialog.sender.get()?.cancel()
            this@FileSenderDialog.sender.set(sender)
            val speedCalculator = SpeedCalculator()
            speedCalculator.addObserver(object : SpeedCalculator.Companion.SpeedObserver {
                override fun onSpeedUpdated(speedInBytes: Long, speedInString: String) {
                    updateState {
                        it.copy(speedString = speedInString)
                    }
                }
            })
            this@FileSenderDialog.speedCalculator.set(speedCalculator)
            sender.addObserver(object : FileTransferObserver {
                override fun onNewState(s: FileTransferState) {
                    when (s) {
                        FileTransferState.NotExecute -> {}
                        FileTransferState.Started -> {
                            speedCalculator.start()
                        }
                        FileTransferState.Canceled -> {
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

                override fun onEndFile(file: FileExploreFile) {}

            })
            sender.start()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun bindContentView(view: View) {
        val files = this.files ?: return

        val viewBinding = ReadingWritingFilesDialogLayoutBinding.bind(view)

        val exploreFiles = files.map { it.exploreFile }.toList()
        renderStateNewCoroutine({ it.transferFile }) {
            val file = it.getOrNull()
            if (file != null) {
                viewBinding.titleTv.text = requireContext().getString(
                    R.string.sending_files_dialog_title,
                    exploreFiles.indexOf(file) + 1, exploreFiles.size
                )
                viewBinding.fileNameTv.text = file.name
            } else {
                viewBinding.titleTv.text = ""
                viewBinding.fileNameTv.text = ""
            }
        }

        renderStateNewCoroutine({ it.transferFile to it.process }) {
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
        sender.get()?.cancel()
        speedCalculator.get()?.stop()
    }
}

sealed class FileTransferResult {
    data class Error(val msg: String) : FileTransferResult()
    data object Cancel : FileTransferResult()
    data object Finished : FileTransferResult()
}

data class FileTransferDialogState(
    val transferFile: Optional<FileExploreFile> = Optional.empty(),
    val process: Long = 0L,
    val speedString: String = ""
)

suspend fun FragmentManager.showFileSenderDialog(
    bindAddress: InetAddress,
    files: List<SenderFile>,
): FileTransferResult {
    return try {
        suspendCancellableCoroutine { cont ->
            val d = FileSenderDialog(
                bindAddress = bindAddress,
                files = files,
                callback = CoroutineDialogForceResultCallback(cont)
            )
            if (!coroutineShowSafe(d, "FileSenderDialog#${System.currentTimeMillis()}", cont)) {
                cont.resume(FileTransferResult.Error("FragmentManager was destroyed."))
            }
        }
    } catch (e: Throwable) {
        FileTransferResult.Error(e.message ?: "")
    }
}