package com.tans.tfiletransporter.netty

import com.tans.tlrucache.memory.LruByteArrayPool.Companion.ByteArrayValue

class NetByteArray(
    val value: ByteArrayValue,
    val readSize: Int
)