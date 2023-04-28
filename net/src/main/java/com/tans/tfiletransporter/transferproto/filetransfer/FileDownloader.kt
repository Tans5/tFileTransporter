package com.tans.tfiletransporter.transferproto.filetransfer

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.tcp.NettyTcpClientConnectionTask
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.SimpleObservable
import com.tans.tfiletransporter.transferproto.SimpleStateable
import com.tans.tfiletransporter.transferproto.TransferProtoConstant
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import okio.FileHandle
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class FileDownloader(
    val downloadDir: File,
    val files: List<FileExploreFile>,
    val connectAddress: InetAddress,
    val maxConnectionSize: Long,
    val minFrameSize: Long = 1024 * 1024 * 10, // 10MB
    val log: ILog
) : SimpleObservable<FileTransferObserver>, SimpleStateable<FileTransferState> {

    override val observers: LinkedBlockingDeque<FileTransferObserver> = LinkedBlockingDeque()

    override val state: AtomicReference<FileTransferState> = AtomicReference(FileTransferState.NotExecute)

    private val waitingDownloader: LinkedBlockingDeque<SingleFileDownloader> = LinkedBlockingDeque()
    private val workingDownloader: AtomicReference<SingleFileDownloader?> = AtomicReference(null)
    private val finishedDownloader: LinkedBlockingDeque<SingleFileDownloader> = LinkedBlockingDeque()


    override fun addObserver(o: FileTransferObserver) {
        super.addObserver(o)
        o.onNewState(state.get())
    }
    @Synchronized
    fun start() {
        if (getCurrentState() is FileTransferState.NotExecute) {
            newState(FileTransferState.Started)
            waitingDownloader.clear()
            for (f in files) {
                waitingDownloader.add(SingleFileDownloader(f))
            }
            doNextDownloader(null)
        }
    }

    @Synchronized
    fun cancel() {
        if (getCurrentState() !is FileTransferState.NotExecute) {
            assertActive {
                newState(FileTransferState.Canceled)
            }
            closeConnectionIfActive()
            closeConnectionAndReportError("User canceled")
        }
    }

    private fun doNextDownloader(finishedDownloader: SingleFileDownloader?) {
        assertActive {
            if (finishedDownloader != null) {
                this.finishedDownloader.add(finishedDownloader)
                for (o in observers) {
                    o.onEndFile(finishedDownloader.file)
                }
            }
            val targetDownloader = waitingDownloader.pollFirst()
            if (targetDownloader != null) {
                targetDownloader.onActive()
                workingDownloader.set(targetDownloader)
                for (o in observers) {
                    o.onStartFile(targetDownloader.file)
                }
            } else {
                newState(FileTransferState.Finished)
                closeConnectionIfActive()
            }
        }
    }

    private fun errorStateIfActive(errorMsg: String) {
        assertActive {
            newState(FileTransferState.Error(errorMsg))
        }
        closeConnectionAndReportError(errorMsg)
    }

    private fun remoteErrorStateIfActive(errorMsg: String) {
        assertActive {
            newState(FileTransferState.RemoteError(errorMsg))
        }
        closeConnectionIfActive()
    }

    private fun dispatchProgressUpdate(hasSentSize: Long, file: FileExploreFile) {
        assertActive {
            for (o in observers) {
                o.onProgressUpdate(file, hasSentSize)
            }
        }
    }

    private fun assertActive(notActive: (() -> Unit)? = null, active: () -> Unit) {
        if (getCurrentState() == FileTransferState.Started) {
            active()
        } else {
            notActive?.invoke()
        }
    }

    private fun closeConnectionIfActive() {
        workingDownloader.get()?.onCanceled("none", false)
        workingDownloader.set(null)
        waitingDownloader.clear()
    }

    private fun closeConnectionAndReportError(errorMsg: String) {
        workingDownloader.get()?.onCanceled(errorMsg, true)
        workingDownloader.set(null)
        waitingDownloader.clear()
    }

    override fun onNewState(s: FileTransferState) {
        super.onNewState(s)
        for (o in observers) {
            o.onNewState(s)
        }
    }

    private inner class SingleFileDownloader(val file: FileExploreFile) {

        private val singleFileHasDownloadSize: AtomicLong by lazy {
            AtomicLong(0L)
        }

        private val isSingleFileDownloaderExecuted: AtomicBoolean by lazy {
            AtomicBoolean(false)
        }

        private val isSingleFileDownloaderCanceled: AtomicBoolean by lazy {
            AtomicBoolean(false)
        }

        private val isSingleFileFinished: AtomicBoolean by lazy {
            AtomicBoolean(false)
        }

        private val downloadingFile: AtomicReference<File?> by lazy {
            AtomicReference(null)
        }

        fun onActive() {
            if (!isSingleFileDownloaderCanceled.get() && !isSingleFileFinished.get() && isSingleFileDownloaderExecuted.compareAndSet(false, true)) {
                try {
                    val downloadingFile = createDownloadingFile()
                    this.downloadingFile.set(downloadingFile)
                    val downloadingFileHandle = FileSystem.SYSTEM.openReadWrite(downloadingFile.toOkioPath())
                    downloadingFileHandle.resize(file.size)
                    val fragmentsRange = createFragmentsRange()
                    log.d(TAG, "Real download fragment size: ${fragmentsRange.size}")

                } catch (e: Throwable) {
                    val msg = "Create download task fail: ${e.message}"
                    log.e(TAG, msg, e)
                    errorStateIfActive(msg)
                }
            }
        }

        fun onCanceled(reason: String, reportRemote: Boolean) {
            if (isSingleFileDownloaderExecuted.get() && !isSingleFileFinished.get() && isSingleFileDownloaderCanceled.compareAndSet(false, true)) {
                // TODO: cancel.

                downloadingFile.get()?.let {
                    it.delete()
                    downloadingFile.set(null)
                }
            }
        }

        private fun updateProgress(downloadedSize: Long) {
            val hasDownloadedSize = singleFileHasDownloadSize.addAndGet(downloadedSize)
            dispatchProgressUpdate(hasDownloadedSize, file)
            if (hasDownloadedSize >= file.size) {
                onFinished()
            }
        }

        private fun onFinished() {
            if (isSingleFileDownloaderExecuted.get() && !isSingleFileDownloaderCanceled.get() && isSingleFileFinished.compareAndSet(false, true)) {
                downloadingFile.get()?.let {
                    it.renameTo(getDownloadedFile(file.name))
                    downloadingFile.set(null)
                    log.d(TAG, "File: ${file.name} download success!!!")
                }
                doNextDownloader(this)
            }
        }

        private fun assertSingleFileDownloaderActive(notActive: (() -> Unit)? = null, active: () -> Unit) {
            if (!isSingleFileFinished.get() && !isSingleFileDownloaderCanceled.get() && isSingleFileDownloaderExecuted.get()) {
                active()
            } else {
                notActive?.invoke()
            }
        }

        private fun createFragmentsRange(): List<Pair<Long, Long>> {
            val fileSize = file.size
            val frameCount = maxConnectionSize
            val minFrameSize = minFrameSize
            if (fileSize < 0) error("Wrong file size: $fileSize")
            if (minFrameSize <= 0) error("Wrong min frame size: $minFrameSize")
            if (frameCount <= 0) error("Wrong min frame count: $frameCount")
            return if (frameCount * minFrameSize > fileSize) {
                val lastFrameSize = fileSize % minFrameSize
                val realFrameCount = fileSize / minFrameSize + if (lastFrameSize > 0L) 1 else 0
                val result = mutableListOf<Pair<Long, Long>>()
                for (i in 0 until realFrameCount) {
                    val start = i * minFrameSize
                    val end = if (i != realFrameCount - 1) (i + 1) * minFrameSize else fileSize
                    result.add(start to end)
                }
                result
            } else {
                val lastFrameSize = fileSize % frameCount
                val frameSize = if (lastFrameSize == 0L) {
                    fileSize / frameCount
                }  else {
                    (fileSize - lastFrameSize) / (frameCount - 1)
                }
                val result = mutableListOf<Pair<Long, Long>>()
                for (i in 0 until frameCount) {
                    val start = i * frameSize
                    val end = if (i != frameCount - 1) (i + 1) * frameSize else fileSize
                    result.add(start to end)
                }
                result
            }
        }

        private fun createDownloadingFile(): File {
            if (!downloadDir.isDirectory) {
                downloadDir.mkdirs()
            }
            val downloadingFileName = "${file.name}.downloading"
            val downloadingFile = File(downloadDir, downloadingFileName)
            if (downloadingFile.isFile) {
                downloadingFile.delete()
            }
            downloadingFile.createNewFile()
            return downloadingFile
        }

        /**
         * return file is not exists
         */
        private fun getDownloadedFile(fileName: String): File {
            if (!downloadDir.isDirectory) {
                downloadDir.mkdirs()
            }
            val targetFile = File(downloadDir, fileName)
            if (!targetFile.exists()) {
                return targetFile
            }
            val nameIndexSuffixRegex = "((.|\\s)+)-(\\d+)(\\.(.|\\s)+)$".toRegex()
            val nameSuffix = "((.|\\s)+)(\\.(.|\\s)+)\$".toRegex()
            val nameIndex = "((.|\\s)+)-(\\d+)$".toRegex()
            return when {
                nameIndexSuffixRegex.matches(fileName) -> {
                    val values = nameIndexSuffixRegex.find(fileName)?.groupValues ?: emptyList()
                    val name = values.getOrNull(1) ?: ""
                    val index = values.getOrNull(3) ?: "0"
                    val suffix = values.getOrNull(4) ?: ""
                    getDownloadedFile("$name-${index.toLong() + 1}$suffix")
                }
                nameSuffix.matches(fileName) -> {
                    val values = nameSuffix.find(fileName)?.groupValues ?: emptyList()
                    val name = values.getOrNull(1) ?: ""
                    val suffix = values.getOrNull(3) ?: ""
                    getDownloadedFile("$name-1$suffix")
                }
                nameIndex.matches(fileName) -> {
                    val values = nameIndexSuffixRegex.find(fileName)?.groupValues ?: emptyList()
                    val name = values.getOrNull(1) ?: ""
                    val index = values.getOrNull(2) ?: "0"
                    getDownloadedFile("$name-${index.toLong() + 1}")
                }
                else -> {
                    getDownloadedFile("$fileName-1")
                }
            }
        }

        private inner class SingleFileFragmentDownloader(
            private val fileHandle: FileHandle,
            private val start: Long,
            private val end: Long
        ) {

            fun connectToServer(simpleCallback: SimpleCallback<Unit>) {
                val task = NettyTcpClientConnectionTask(
                    serverAddress = connectAddress,
                    serverPort = TransferProtoConstant.FILE_TRANSFER_PORT
                )

            }

            fun sendRemoteError(errorMsg: String) {

            }

            fun closeConnectionIfActive() {

            }
        }

    }

    companion object {
        private const val TAG = "FileDownloader"
    }

}