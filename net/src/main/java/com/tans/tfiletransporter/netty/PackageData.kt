package com.tans.tfiletransporter.netty

import io.netty.buffer.ByteBuf

data class PackageData(
    val type: Int,
    val messageId: Long,
    val body: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PackageData

        if (type != other.type) return false
        return body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + body.contentHashCode()
        return result
    }
}

fun ByteBuf.readBytes(): ByteArray {
    val size = writerIndex() - readerIndex()
    return if (size > 0) {
        ByteArray(size).apply {
            readBytes(this)
        }
    } else {
        ByteArray(0)
    }
}