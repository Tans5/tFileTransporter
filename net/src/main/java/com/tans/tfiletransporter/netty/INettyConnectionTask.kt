package com.tans.tfiletransporter.netty

import java.net.InetSocketAddress
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

interface INettyConnectionTask : Runnable {

    val isExecuted: AtomicBoolean

    val state: AtomicReference<NettyTaskState>

    val ioExecutor: Executor

    val observers: LinkedBlockingDeque<NettyConnectionObserver>

    val byteArrayPool: ByteArrayPool

    fun isExecuted(): Boolean = isExecuted.get()

    fun getCurrentState(): NettyTaskState = state.get()

    fun startTask() {
        ioExecutor.execute(this)
    }

    fun sendData(data: PackageData, sendDataCallback: SendDataCallback?) {
        sendDataInner(data, sendDataCallback)
    }

    fun sendData(data: PackageDataWithAddress, sendDataCallback: SendDataCallback?) {
        sendDataInner(data, sendDataCallback)
    }

    private fun sendDataInner(data: Any, sendDataCallback: SendDataCallback?) {
        if (isExecuted()) {
            val state = getCurrentState()
            if (state is NettyTaskState.ConnectionActive) {
                state.channel.writeAndFlush(data)
                sendDataCallback?.onSuccess()
            } else {
                sendDataCallback?.onFail("Wrong task state: $state")
            }
        } else {
            sendDataCallback?.onFail("Task not execute.")
        }
    }

    fun addObserver(o: NettyConnectionObserver) {
        o.onNewState(state.get(), this)
        observers.add(o)
    }

    fun removeObserver(o: NettyConnectionObserver) {
        observers.remove(o)
    }

    override fun run() {
        if (isExecuted.compareAndSet(false, true)) {
            runTask()
        } else {
            val msg = "Task already executed."
            dispatchState(NettyTaskState.Error(IllegalStateException(msg)))
        }
    }

    fun runTask()

    fun stopTask() {
        if (isExecuted()) {
            val state = getCurrentState()
            if (state is NettyTaskState.ConnectionActive) {
                state.channel.close()
            }
            dispatchState(NettyTaskState.ConnectionClosed)
            byteArrayPool.clearMemory()
        }
    }

    fun dispatchState(state: NettyTaskState) {
        val lastState = getCurrentState()
        if (lastState == state) {
            return
        }
        this.state.set(state)
        for (o in observers) {
            o.onNewState(state, this)
        }
        if (state is NettyTaskState.ConnectionClosed || state is NettyTaskState.Error) {
            observers.clear()
        }
    }

    fun dispatchDownloadData(
        localAddress: InetSocketAddress?,
        remoteAddress: InetSocketAddress?,
        msg: PackageData) {
        for (o in observers) {
            o.onNewMessage(
                localAddress,
                remoteAddress,
                msg,
                this
            )
        }
    }

    interface SendDataCallback {

        fun onSuccess()

        fun onFail(message: String)
    }
}