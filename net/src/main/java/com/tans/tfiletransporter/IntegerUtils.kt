package com.tans.tfiletransporter

fun Int.toBytes(isRevert: Boolean = false): ByteArray {
    val result = ByteArray(4) { index ->
        (this and (0x000000FF shl (index * 8)) ushr (index * 8)).toByte()
    }
    return if (isRevert) {
        result
    } else {
        result.reverse()
        result
    }
}

fun ByteArray.toInt(): Int {
    if (size != 4)
        throw Exception("The length of the byte array must be at least 4 bytes long.")

    return 0xff and get(0).toInt() shl 56 or (0xff and get(1).toInt() shl 48) or (0xff and get(2).toInt() shl 40) or (0xff and get(3).toInt() shl 32)
}