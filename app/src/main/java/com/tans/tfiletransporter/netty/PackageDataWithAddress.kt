package com.tans.tfiletransporter.netty

import java.net.InetSocketAddress

data class PackageDataWithAddress(
    val address: InetSocketAddress?,
    val data: PackageData
)