package com.tans.tfiletransporter.netty


data class PackageData(
    val type: Int,
    val messageId: Long,
    val body: NetByteArray
)