package com.tans.tfiletransporter.transferproto.filetransfer

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.extensions.ConnectionClientImpl
import com.tans.tfiletransporter.netty.extensions.ConnectionServerClientImpl
import com.tans.tfiletransporter.netty.extensions.IClientManager
import com.tans.tfiletransporter.netty.extensions.IServer
import com.tans.tfiletransporter.netty.extensions.requestSimplify
import com.tans.tfiletransporter.netty.extensions.simplifyServer
import com.tans.tfiletransporter.netty.extensions.withClient
import com.tans.tfiletransporter.netty.extensions.withServer
import com.tans.tfiletransporter.netty.tcp.NettyTcpServerConnectionTask
import com.tans.tfiletransporter.resumeExceptionIfActive
import com.tans.tfiletransporter.resumeIfActive
import com.tans.tfiletransporter.transferproto.SimpleObservable
import com.tans.tfiletransporter.transferproto.SimpleStateable
import com.tans.tfiletransporter.transferproto.TransferProtoConstant
import com.tans.tfiletransporter.transferproto.filetransfer.model.DownloadReq
import com.tans.tfiletransporter.transferproto.filetransfer.model.ErrorReq
import com.tans.tfiletransporter.transferproto.filetransfer.model.FileTransferDataType
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.FileHandle
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.buffer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class FileSender(
    private val files: List<SenderFile>,
    private val bindAddress: InetAddress,
    private val bufferSize: Long,
    private val log: ILog
) : SimpleObservable<FileTransferObserver>, SimpleStateable<FileTransferState> {

    override val observers: LinkedBlockingDeque<FileTransferObserver> = LinkedBlockingDeque()

    override val state: AtomicReference<FileTransferState> = AtomicReference(FileTransferState.NotExecute)

    private val serverTask: AtomicReference<NettyTcpServerConnectionTask?> by lazy {
        AtomicReference(null)
    }

    private val waitingSenders: LinkedBlockingDeque<SingleFileSender> = LinkedBlockingDeque()
    private val workingSender: AtomicReference<SingleFileSender?> by lazy {
        AtomicReference(null)
    }
    private val finishedSenders: LinkedBlockingDeque<SingleFileSender> = LinkedBlockingDeque()
    override fun addObserver(o: FileTransferObserver) {
        super.addObserver(o)
        o.onNewState(state.get())
    }

    @Synchronized
    fun start() {
        if (getCurrentState() is FileTransferState.NotExecute) {
            newState(FileTransferState.Started)
            waitingSenders.clear()
            for (f in files) {
                waitingSenders.add(SingleFileSender(f))
            }
            doNextSender(null)
            val serverTask = NettyTcpServerConnectionTask(
                bindAddress = bindAddress,
                bindPort = TransferProtoConstant.FILE_TRANSFER_PORT,
                newClientTaskCallback = { clientTask ->
                    assertActive(
                        notActive = { clientTask.stopTask() }
                    ) {
                        val workingSender = workingSender.get()
                        if (workingSender != null) {
                            workingSender.newChildTask(clientTask)
                        } else {
                            clientTask.stopTask()
                            val msg = "No working sender to handle clientTask."
                            log.e(TAG, msg)
                            errorStateIfActive(msg)
                        }
                    }
                }
            )
            this.serverTask.set(serverTask)
            serverTask.addObserver(object : NettyConnectionObserver {
                override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {

                    if (nettyState is NettyTaskState.Error
                        || nettyState is NettyTaskState.ConnectionClosed) {
                        if (getCurrentState() is FileTransferState.Started) {
                            val errorMsg = "Bind address fail: $nettyState, ${getCurrentState()}"
                            log.e(TAG, errorMsg)
                            errorStateIfActive(errorMsg)
                        }
                    } else {
                        if (nettyState is NettyTaskState.ConnectionActive) {
                            log.d(TAG, "Bind address success: $nettyState")
                        }
                    }
                }

                override fun onNewMessage(
                    localAddress: InetSocketAddress?,
                    remoteAddress: InetSocketAddress?,
                    msg: PackageData,
                    task: INettyConnectionTask
                ) {}
            })
            serverTask.startTask()

        }
    }

    @Synchronized
    fun cancel() {
        if (getCurrentState() !is FileTransferState.NotExecute) {
            assertActive {
                newState(FileTransferState.Canceled)
            }
            closeNoneFinishedSenders("User canceled", true)
            closeConnectionIfActive()
        }
    }

    private fun dispatchProgressUpdate(hasSentSize: Long, file: SenderFile) {
        assertActive {
            for (o in observers) {
                o.onProgressUpdate(file.exploreFile, hasSentSize)
            }
        }
    }
    private fun closeNoneFinishedSenders(reason: String, reportRemote: Boolean) {
        workingSender.get()?.onCanceled(reason, reportRemote)
        workingSender.set(null)
        waitingSenders.clear()
    }

    private fun doNextSender(finishedSender: SingleFileSender?) {
        assertActive {
            if (finishedSender != null) {
                finishedSenders.add(finishedSender)
                for (o in observers) {
                    o.onEndFile(finishedSender.file.exploreFile)
                }
            }
            val targetSender = waitingSenders.pollFirst()
            if (targetSender != null) {
                targetSender.onActive()
                workingSender.set(targetSender)
                for (o in observers) {
                    o.onStartFile(targetSender.file.exploreFile)
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
        closeNoneFinishedSenders(errorMsg, true)
        closeConnectionIfActive()
    }

    private fun remoteErrorStateIfActive(errorMsg: String) {
        assertActive {
            newState(FileTransferState.RemoteError(errorMsg))
        }
        closeNoneFinishedSenders(errorMsg, false)
        closeConnectionIfActive()
    }

    private fun assertActive(notActive: (() -> Unit)? = null, active: () -> Unit) {
        if (getCurrentState() == FileTransferState.Started) {
            active()
        } else {
            notActive?.invoke()
        }
    }

    private fun closeConnectionIfActive() {
        serverTask.get()?.stopTask()
        serverTask.set(null)
    }

    override fun onNewState(s: FileTransferState) {
        super.onNewState(s)
        for (o in observers) {
            o.onNewState(s)
        }
    }

    private inner class SingleFileSender(val file: SenderFile) {

        private val fileHandle: AtomicReference<FileHandle?> by lazy { AtomicReference(null) }

        private val isSingleFileSenderExecuted: AtomicBoolean by lazy {
            AtomicBoolean(false)
        }

        private val isSingleFileSenderCanceled: AtomicBoolean by lazy {
            AtomicBoolean(false)
        }

        private val isSingleFileSenderFinished: AtomicBoolean by lazy {
            AtomicBoolean(false)
        }

        private val singleFileHasSentSize: AtomicLong by lazy {
            AtomicLong(0L)
        }

        private val fragmentSenders: LinkedBlockingDeque<SingleFileFragmentSender> by lazy {
            LinkedBlockingDeque()
        }

        fun onActive() {
            if (!isSingleFileSenderCanceled.get() && !isSingleFileSenderFinished.get() && isSingleFileSenderExecuted.compareAndSet(false, true)) {
                try {
                    fileHandle.set(FileSystem.SYSTEM.openReadOnly(file.realFile.toOkioPath()))
                } catch (e: Throwable) {
                    val msg = "Read file: $file error: ${e.message}"
                    log.d(TAG, msg)
                    errorStateIfActive(msg)
                }
            }
        }

        fun newChildTask(task: NettyTcpServerConnectionTask.ChildConnectionTask) {
            assertSingleFileSenderActive(notActive = {
                task.stopTask()
                val msg = "Single task is not active, can't deal child task"
                log.e(TAG, msg)
                errorStateIfActive(msg)
            }
            ) {
                val fileHandle = fileHandle.get()
                if (fileHandle != null) {
                    fragmentSenders.add(SingleFileFragmentSender(fileHandle = fileHandle, task = task))
                } else {
                    task.stopTask()
                    val msg = "File handle is null"
                    log.e(TAG, msg)
                    errorStateIfActive(msg)
                }
            }
        }

        fun onCanceled(reason: String, reportRemote: Boolean) {
            if (isSingleFileSenderExecuted.get() && !isSingleFileSenderFinished.get() && isSingleFileSenderCanceled.compareAndSet(false, true)) {
                for (fs in fragmentSenders) {
                    if (reportRemote) {
                        fs.sendRemoteError(reason)
                    }
                    fs.closeConnectionIfActive()
                }
                fragmentSenders.clear()
            }
        }

        private fun updateProgress(sentSize: Long) {
            val hasSentSize = singleFileHasSentSize.addAndGet(sentSize)
            dispatchProgressUpdate(hasSentSize, file)
            if (hasSentSize >= file.exploreFile.size) {
                onFinished()
            }
        }

        private fun onFinished() {
            assertSingleFileSenderActive {
                if (isSingleFileSenderFinished.compareAndSet(false, true)) {
                    log.d(TAG, "File: ${file.exploreFile.name} send success!!!")
                    for (fs in fragmentSenders){
                        fs.closeConnectionIfActive()
                    }
                    fragmentSenders.clear()
                    doNextSender(this)
                }
            }
        }

        private fun assertSingleFileSenderActive(notActive: (() -> Unit)? = null, active: () -> Unit) {
            if (!isSingleFileSenderFinished.get() && !isSingleFileSenderCanceled.get() && isSingleFileSenderExecuted.get()) {
                active()
            } else {
                notActive?.invoke()
            }
        }

        private inner class SingleFileFragmentSender(
            private val fileHandle: FileHandle,
            task: NettyTcpServerConnectionTask.ChildConnectionTask): CoroutineScope by CoroutineScope(Dispatchers.IO) {

            private val serverClientTask: ConnectionServerClientImpl = task.withClient<ConnectionClientImpl>(log = log).withServer(log = log)

            private val isFragmentFinished: AtomicBoolean by lazy {
                AtomicBoolean(false)
            }

            private val downloadReq: AtomicReference<DownloadReq?> by lazy {
                AtomicReference(null)
            }

            private val downloadReqServer: IServer<DownloadReq, Unit> by lazy {
                simplifyServer(
                    requestType = FileTransferDataType.DownloadReq.type,
                    responseType = FileTransferDataType.DownloadResp.type,
                    log = log,
                    onRequest = { _, _, r, isNew ->
                        if (isNew) {
                            if (downloadReq.get() == null) {
                                if (r.file != file.exploreFile) {
                                    val msg = "Wrong file, require $file, receive ${r.file}"
                                    log.e(TAG, msg)
                                    errorStateIfActive(msg)
                                    return@simplifyServer
                                }
                                val startIndex = r.start
                                val endIndex = r.end
                                if (startIndex < 0 || endIndex < 0 || startIndex > endIndex || endIndex > r.file.size) {
                                    val msg = "Wrong file info: $r"
                                    log.e(TAG, msg)
                                    errorStateIfActive(msg)
                                    return@simplifyServer
                                }
                                downloadReq.set(r)
                                startSendData(r)
                            } else {
                                serverClientTask.stopTask()
                                val msg = "Receive multi times download request: $r"
                                log.e(TAG, msg)
                                errorStateIfActive(msg)
                            }
                        }
                        Unit
                    }
                )
            }

            private val errorReqServer: IServer<ErrorReq, Unit> by lazy {
                simplifyServer(
                    requestType = FileTransferDataType.ErrorReq.type,
                    responseType = FileTransferDataType.ErrorResp.type,
                    log = log,
                    onRequest = { _, _, r, isNew ->
                        log.e(TAG, "Receive remote error request: $r")
                        if (isNew) {
                            remoteErrorStateIfActive(r.errorMsg)
                        }
                    }
                )
            }

            private val finishReqServer: IServer<Unit, Unit> by lazy {
                simplifyServer(
                    requestType = FileTransferDataType.FinishedReq.type,
                    responseType = FileTransferDataType.FinishedResp.type,
                    log = log,
                    onRequest = { _, _, _, isNew ->
                        if (isNew) {
                            log.d(TAG, "Receive download finish request.")
                        }
                    }
                )
            }

            init {
                serverClientTask.addObserver(object : NettyConnectionObserver {
                    override fun onNewState(
                        nettyState: NettyTaskState,
                        task: INettyConnectionTask
                    ) {
                        if ((nettyState is NettyTaskState.Error || nettyState is NettyTaskState.ConnectionClosed) && !isFragmentFinished.get()) {
                            val msg = "Connection closed: $nettyState"
                            log.e(TAG, msg)
                            errorStateIfActive(msg)
                        }
                    }

                    override fun onNewMessage(
                        localAddress: InetSocketAddress?,
                        remoteAddress: InetSocketAddress?,
                        msg: PackageData,
                        task: INettyConnectionTask
                    ) {}
                })
                serverClientTask.registerServer(downloadReqServer)
                serverClientTask.registerServer(errorReqServer)
                serverClientTask.registerServer(finishReqServer)
            }

            fun isActive(): Boolean = serverClientTask.getCurrentState() is NettyTaskState.ConnectionActive

            fun sendRemoteError(errorMsg: String) {
                if (isActive()) {
                    serverClientTask.requestSimplify(
                        type = FileTransferDataType.ErrorReq.type,
                        request = ErrorReq(errorMsg),
                        retryTimes = 0,
                        object : IClientManager.RequestCallback<Unit> {
                            override fun onSuccess(
                                type: Int,
                                messageId: Long,
                                localAddress: InetSocketAddress?,
                                remoteAddress: InetSocketAddress?,
                                d: Unit
                            ) {
                                log.d(TAG, "Send error msg success.")
                            }

                            override fun onFail(errorMsg: String) {
                                log.e(TAG, "Send error msg error: $errorMsg")
                            }
                        }
                    )
                }
            }

            fun closeConnectionIfActive() {
                Dispatchers.IO.asExecutor().execute {
                    Thread.sleep(100)
                    serverClientTask.stopTask()
                }
                this@SingleFileFragmentSender.cancel()
            }

            private suspend fun sendDataSuspend(bytes: ByteArray) = suspendCancellableCoroutine { cont ->
                serverClientTask.requestSimplify(
                    type = FileTransferDataType.SendReq.type,
                    request = bytes,
                    callback = object : IClientManager.RequestCallback<Unit> {
                        override fun onSuccess(
                            type: Int,
                            messageId: Long,
                            localAddress: InetSocketAddress?,
                            remoteAddress: InetSocketAddress?,
                            d: Unit
                        ) {
                            cont.resumeIfActive(d)
                        }

                        override fun onFail(errorMsg: String) {
                            cont.resumeExceptionIfActive(Throwable(errorMsg))
                        }
                    }
                )
            }

            private fun startSendData(downloadReq: DownloadReq) {
                launch {
                    val frameSize = downloadReq.end - downloadReq.start
                    var hasRead = 0L
                    try {
                        fileHandle.source(downloadReq.start).buffer().use { source ->
                            while (hasRead < frameSize) {
                                val thisTimeRead = if ((frameSize - hasRead) < bufferSize) {
                                    frameSize - hasRead
                                } else {
                                    bufferSize
                                }
                                val bytes = source.readByteArray(thisTimeRead)
                                sendDataSuspend(bytes)
                                updateProgress(thisTimeRead)
                                hasRead += thisTimeRead
                            }
                        }
                        isFragmentFinished.set(true)
                        log.d(TAG, "Frame: ${downloadReq.start} finished($frameSize bytes)")
                    } catch (e: Throwable) {
                        log.e(TAG, "Send file error: ${e.message}", e)
                        errorStateIfActive("Send file error: ${e.message}")
                    }
                }
            }
        }

    }

    companion object {
        private const val TAG = "FileSender"
    }
}