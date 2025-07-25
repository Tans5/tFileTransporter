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

    private val waitingRspTasks: ConcurrentHashMap<Task<*, *>, Unit> by lazy {
        ConcurrentHashMap()
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

    // 收到来自 server 的回复消息
    override fun onNewMessage(
        localAddress: InetSocketAddress?,
        remoteAddress: InetSocketAddress?,
        msg: PackageData,
        task: INettyConnectionTask
    ) {
        // 通知正在等待 server 回复消息的 Task
        for (t in waitingRspTasks.keys()) {
            t.onNewDownloadData(
                localAddress = localAddress,
                remoteAddress = remoteAddress,
                downloadData = msg
            )
        }
    }

    // 请求 server
    override fun <Request, Response> request(
        type: Int,
        request: Request,
        requestClass: Class<Request>,
        responseClass: Class<Response>,
        retryTimes: Int,
        retryTimeout: Long,
        callback: IClientManager.RequestCallback<Response>
    ) {
        // 将请求消息封装到 Task，然后在后台线程执行任务.
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
                retryTimeout = retryTimeout,
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
        retryTimeout: Long,
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
                retryTimeout = retryTimeout,
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
        waitingRspTasks[t] = Unit
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
        private val retryTimeout: Long,
        val delay: Long = 0L,
        private val callback: IClientManager.RequestCallback<Response>
    ) : Runnable {

        private val taskIsDone: AtomicBoolean = AtomicBoolean(false)

        private val timeoutTask: AtomicReference<ScheduledFuture<*>?> = AtomicReference(null)

        // 收到来自 Server 的回复消息
        fun onNewDownloadData(
            localAddress: InetSocketAddress?,
            remoteAddress: InetSocketAddress?,
            downloadData: PackageData
        ) {
            if (downloadData.messageId == this.messageId) { // 是当前的任务的回复消息
                // 移除超时信息
                timeoutTask.get()?.cancel(true)
                timeoutTask.set(null)

                // log.d(TAG, "Received response: msgId -> ${downloadData.messageId}, type -> $type")
                // 找到回复的消息的转换器
                val converter = converterFactory.findBodyConverter(downloadData.type, responseClass)
                if (converter != null) {
                    val response = converter.convert(
                        downloadData.type,
                        responseClass,
                        downloadData,
                        connectionTask.byteArrayPool
                    )
                    if (response != null) {
                        // 将当前任务从正在等待的任务队列中移除
                        removeWaitingTask(this)
                        if (taskIsDone.compareAndSet(false, true)) {
                            // 回调成功.
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

        // 发送信息到 Server
        override fun run() {
            // 将当前任务添加到等待回复的队列
            addWaitingTask(this)
            // log.d(TAG, "Sending request: msgId -> $messageId, cmdType -> $type")
            // 获取 request 的 converter
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
                    dataClass = requestClass,
                    byteArrayPool = connectionTask.byteArrayPool
                )
                if (pckData != null) {
                    // 等待 Server 回复超时 Task
                    val timeoutTask = taskScheduleExecutor.schedule(
                        {
                            handleError("Waiting Response timeout: $retryTimeout ms.")
                        },
                        retryTimeout,
                        TimeUnit.MILLISECONDS
                    )
                    this.timeoutTask.set(timeoutTask)

                    // 发送数据到 Server
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

        // 发送失败，处理异常
        private fun handleError(e: String) {
            // 从等待队列中移除当前任务
            removeWaitingTask(this)
            log.e(TAG, "Send request error: msgId -> $messageId, cmdType -> $type, error -> $e")
            if (taskIsDone.compareAndSet(false, true)) {
                // 判断是否需要重试，如果需要重试，构建一个新的任务继续请求，反之直接回调异常.
                if (retryTimes > 0) {
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
                            retryTimeout = retryTimeout,
                            udpTargetAddress = udpTargetAddress,
                            udpSenderAddress = udpSenderAddress
                        )
                    )
                } else {
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
        private const val TAG = "DefaultClientManager"
    }
}