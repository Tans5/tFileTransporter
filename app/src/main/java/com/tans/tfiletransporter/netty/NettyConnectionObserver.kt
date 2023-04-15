package com.tans.tfiletransporter.netty

import java.net.InetSocketAddress

interface NettyConnectionObserver {

    fun onNewState(nettyState: NettyTaskState, task: INettyConnectionTask) {

    }

    fun onNewMessage(localAddress: InetSocketAddress?, remoteAddress: InetSocketAddress?, msg: PackageData, task: INettyConnectionTask) {

    }
}