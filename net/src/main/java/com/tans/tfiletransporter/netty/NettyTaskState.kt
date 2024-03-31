package com.tans.tfiletransporter.netty

import io.netty.channel.Channel

sealed class NettyTaskState {

    /**
     * 未执行
     */
    data object NotExecute : NettyTaskState()

    /**
     * 连接可用
     */
    data class ConnectionActive(
        val channel: Channel
    ) : NettyTaskState()

    /**
     * 连接关闭
     */
    data object ConnectionClosed : NettyTaskState()

    /**
     * 错误
     */
    data class Error(val throwable: Throwable) : NettyTaskState()
}