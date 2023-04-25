package com.tans.tfiletransporter.transferproto.filetransfer

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.tcp.NettyTcpServerConnectionTask
import com.tans.tfiletransporter.transferproto.SimpleObservable
import com.tans.tfiletransporter.transferproto.SimpleStateable
import com.tans.tfiletransporter.transferproto.TransferProtoConstant
import com.tans.tfiletransporter.transferproto.filetransfer.model.SenderFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicReference

class FileSender(
    private val bufferSize: Int,
    private val files: List<SenderFile>,
    private val bindAddress: InetAddress,
    private val log: ILog
) : SimpleObservable<FileTransferObserver>, SimpleStateable<FileTransferState> {

    override val observers: LinkedBlockingDeque<FileTransferObserver> = LinkedBlockingDeque()

    override val state: AtomicReference<FileTransferState> = AtomicReference()

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
                            errorStateIfActive("No working sender to handle clientTask.")
                        }
                    }
                }
            )
            this.serverTask.set(serverTask)
            serverTask.addObserver(object : NettyConnectionObserver {
                override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {

                    if (nettyState is NettyTaskState.Error
                        || nettyState is NettyTaskState.ConnectionClosed
                        || getCurrentState() !is FileTransferState.Started) {
                        val errorMsg = "Bind address fail: $nettyState, ${getCurrentState()}"
                        log.e(TAG, errorMsg)
                        errorStateIfActive(errorMsg)
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
            workingSender.get()?.onCanceled()
            workingSender.set(null)
            waitingSenders.clear()
            closeConnectionIfActive()
        }
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
            }
        }
    }

    private fun errorStateIfActive(errorMsg: String) {
        assertActive {
            newState(FileTransferState.Error(errorMsg))
        }
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

        fun onActive() {
            // TODO:
        }

        fun newChildTask(task: NettyTcpServerConnectionTask.ChildConnectionTask) {
            // TODO:
        }

        fun onCanceled() {

        }

    }

    companion object {
        private const val TAG = "FileSender"
    }
}