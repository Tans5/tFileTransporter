package com.tans.tfiletransporter.netty

import io.netty.channel.Channel

sealed class NettyTaskState {

    /**
     * 未执行
     */
    object NotExecute : NettyTaskState()

    /**
     * 初始化
     */
    object Init : NettyTaskState()

    /**
     * 连接可用
     */
    data class ConnectionActive(
        val channel: Channel
    ) : NettyTaskState()

    /**
     * 连接关闭
     */
    object ConnectionClosed : NettyTaskState()

    /**
     * 错误
     */
    data class Error(val throwable: Throwable) : NettyTaskState()
}