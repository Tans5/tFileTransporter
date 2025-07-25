package com.tans.tfiletransporter.netty

import com.tans.tlrucache.memory.LruByteArrayPool
import com.tans.tlrucache.memory.LruByteArrayPool.Companion.ByteArrayValue

class ByteArrayPool(val maxPoolSize: Long = DEFAULT_MAX_POOL_SIZE) {

    private val pool: LruByteArrayPool by lazy {
        LruByteArrayPool(maxPoolSize)
    }

    fun get(requestSize: Int): ByteArrayValue {
        return if (requestSize <= 0) {
            ByteArrayValue(
                ByteArray(0)
            )
        } else if (requestSize % 1024 == 0) {
            pool.get(requestSize)
        } else {
            val fixedRequestSize = requestSize - (requestSize % 1024) + 1024
            pool.get(fixedRequestSize)
        }
    }

    fun put(value: ByteArrayValue) {
        if (value.value.isNotEmpty()) {
            pool.put(value)
        }
    }

    fun clearMemory() {
        pool.clearMemory()
    }

    companion object {
        // 5 MB
        const val DEFAULT_MAX_POOL_SIZE = 1024L * 1024L * 5L
    }
}