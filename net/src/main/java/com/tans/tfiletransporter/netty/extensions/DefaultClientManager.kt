package com.tans.tfiletransporter.netty.extensions

import com.tans.tfiletransporter.ILog
import com.tans.tfiletransporter.netty.INettyConnectionTask
import com.tans.tfiletransporter.netty.NettyConnectionObserver
import com.tans.tfiletransporter.netty.NettyTaskState
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tfiletransporter.netty.PackageDataWithAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.net.InetSocketAddress
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class DefaultClientManager(
    val connectionTask: INettyConnectionTask,
    val converterFactory: IConverterFactory = DefaultConverterFactory(),
    val log: ILog
) : IClientManager, NettyConnectionObserver {

    private val waitingRspTasks: LinkedBlockingDeque<Task<*, *>> by lazy {
        LinkedBlockingDeque()
    }

    private val ioExecutor: Executor by lazy {
        Dispatchers.IO.asExecutor()
    }

    private val messageId: AtomicLong by lazy {
        AtomicLong(0L)
    }

    init {
        connectionTask.addObserver(this)
    }

    override fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {}

    override fun onNewMessage(
        localAddress: InetSocketAddress?,
        remoteAddress: InetSocketAddress?,
        msg: PackageData,
        task: INettyConnectionTask
    ) {
        for (t in waitingRspTasks) {
            t.onNewDownloadData(
                localAddress = localAddress,
                remoteAddress = remoteAddress,
                downloadData = msg
            )
        }
    }

    override fun <Request, Response> request(
        type: Int,
        request: Request,
        requestClass: Class<Request>,
        responseClass: Class<Response>,
        retryTimes: Int,
        callback: IClientManager.RequestCallback<Response>
    ) {
        enqueueTask(
            Task(
                type = type,
                messageId = messageId.addAndGet(1),
                udpTargetAddress = null,
                udpSenderAddress = null,
                request = request,
                requestClass = requestClass,
                responseClass = responseClass,
                retryTimes = retryTimes,
                delay = 0L,
                callback = callback
            )
        )
    }

    override fun <Request, Response> request(
        type: Int,
        request: Request,
        requestClass: Class<Request>,
        responseClass: Class<Response>,
        targetAddress: InetSocketAddress,
        senderAddress: InetSocketAddress?,
        retryTimes: Int,
        callback: IClientManager.RequestCallback<Response>
    ) {
        enqueueTask(
            Task(
                type = type,
                messageId = messageId.addAndGet(1),
                udpTargetAddress = targetAddress,
                udpSenderAddress = senderAddress,
                request = request,
                requestClass = requestClass,
                responseClass = responseClass,
                retryTimes = retryTimes,
                delay = 0L,
                callback = callback
            )
        )
    }

    private fun enqueueTask(task: Task<*, *>) {
        if (task.delay > 0) {
            taskScheduleExecutor.schedule({
                ioExecutor.execute(task)
            }, task.delay, TimeUnit.MILLISECONDS)
        } else {
            ioExecutor.execute(task)
        }
        // log.d(TAG, "Current waiting task size: ${waitingRspTasks.size}")
    }

    private fun <Req, Resp> addWaitingTask(t: Task<Req, Resp>) {
        waitingRspTasks.add(t)
    }

    private fun <Req, Resp> removeWaitingTask(t: Task<Req, Resp>) {
        waitingRspTasks.remove(t)
    }

    inner class Task<Request, Response>(
        private val type: Int,
        private val messageId: Long,
        private val udpTargetAddress: InetSocketAddress?,
        private val udpSenderAddress: InetSocketAddress?,
        private val request: Request,
        private val requestClass: Class<Request>,
        private val responseClass: Class<Response>,
        private val retryTimes: Int,
        val delay: Long = 0L,
        private val callback: IClientManager.RequestCallback<Response>
    ) : Runnable {

        private val taskIsDone: AtomicBoolean = AtomicBoolean(false)

        private val timeoutTask: AtomicReference<ScheduledFuture<*>?> = AtomicReference(null)

        fun onNewDownloadData(
            localAddress: InetSocketAddress?,
            remoteAddress: InetSocketAddress?,
            downloadData: PackageData
        ) {
            if (downloadData.messageId == this.messageId) {
                timeoutTask.get()?.cancel(true)
                timeoutTask.set(null)
                // log.d(TAG, "Received response: msgId -> ${downloadData.messageId}, type -> $type")
                val converter = converterFactory.findBodyConverter(downloadData.type, responseClass)
                if (converter != null) {
                    val response = converter.convert(
                        downloadData.type,
                        responseClass,
                        downloadData
                    )
                    if (response != null) {
                        removeWaitingTask(this)
                        if (taskIsDone.compareAndSet(false, true)) {
                            callback.onSuccess(
                                type = type,
                                messageId = messageId,
                                localAddress = localAddress,
                                remoteAddress = remoteAddress,
                                d = response
                            )
                        }
                    } else {
                        val errorMsg = "${converter::class.java} convert $responseClass fail."
                        log.e(TAG, errorMsg)
                        handleError(errorMsg)
                    }
                } else {
                    val errorMsg = "Didn't find converter for: $type, $responseClass"
                    log.e(TAG, errorMsg)
                    handleError(errorMsg)
                }
            }
        }

        override fun run() {
            addWaitingTask(this)
            // log.d(TAG, "Sending request: msgId -> $messageId, cmdType -> $type")
            val converter = converterFactory.findPackageDataConverter(
                type = type,
                dataClass = requestClass
            )
            if (converter == null) {
                val errorMsg = "Didn't find converter for: $type, $requestClass"
                log.e(TAG, errorMsg)
                handleError(errorMsg)
            } else {
                val pckData = converter.convert(
                    type = type,
                    messageId = messageId,
                    data = request,
                    dataClass = requestClass
                )
                if (pckData != null) {
                    val timeoutTask = taskScheduleExecutor.schedule(
                        {
                            handleError("Waiting Response timeout: $WAIT_RSP_TIMEOUT ms.")
                        },
                        WAIT_RSP_TIMEOUT,
                        TimeUnit.MILLISECONDS
                    )
                    this.timeoutTask.set(timeoutTask)
                    if (udpTargetAddress != null) {
                        connectionTask.sendData(
                            data = PackageDataWithAddress(
                                receiverAddress = udpTargetAddress,
                                senderAddress = udpSenderAddress,
                                data = pckData
                            ),
                            sendDataCallback = object :  INettyConnectionTask.SendDataCallback {
                                override fun onSuccess() {}
                                override fun onFail(message: String) {
                                    log.e(TAG, message)
                                    handleError(message)
                                }
                            }
                        )
                    } else {
                        connectionTask.sendData(
                            data = pckData,
                            sendDataCallback = object : INettyConnectionTask.SendDataCallback {
                                override fun onSuccess() {}
                                override fun onFail(message: String) {
                                    log.e(TAG, message)
                                    handleError(message)
                                }
                            }
                        )
                    }
                } else {
                    val errorMsg = "${converter::class.java} convert $requestClass fail."
                    log.e(TAG, errorMsg)
                    handleError(errorMsg)
                }
            }
        }

        private fun handleError(e: String) {
            removeWaitingTask(this)
            log.e(TAG, "Send request error: msgId -> $messageId, cmdType -> $type, error -> $e")
            if (retryTimes > 0) {
                if (taskIsDone.compareAndSet(false, true)) {
                    log.e(TAG, "Retry send request")
                    enqueueTask(
                        Task(
                            type = type,
                            messageId = messageId,
                            request = request,
                            requestClass = requestClass,
                            responseClass = responseClass,
                            callback = callback,
                            retryTimes = retryTimes - 1,
                            delay = RETRY_DELAY,
                            udpTargetAddress = udpTargetAddress,
                            udpSenderAddress = udpSenderAddress
                        )
                    )
                }
            } else {
                if (taskIsDone.compareAndSet(false, true)) {
                    callback.onFail(e)
                }
            }
        }

    }

    companion object {

        private val taskScheduleExecutor: ScheduledExecutorService by lazy {
            Executors.newScheduledThreadPool(2) {
                Thread(it, "ClientTaskThread")
            }
        }

        private const val RETRY_DELAY = 100L
        private const val WAIT_RSP_TIMEOUT = 1000L
        private const val TAG = "DefaultClientManager"
    }
}