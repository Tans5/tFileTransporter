package com.tans.tfiletransporter

import java.io.RandomAccessFile

fun RandomAccessFile.readContent(fileOffset: Long, byteArray: ByteArray, contentLen: Int, contentOffset: Int = 0) {
    synchronized(this) {
        seek(fileOffset)
        val readLen = read(byteArray, contentOffset, contentLen)
        if (readLen < contentLen) {
            this.readContent(
                fileOffset = fileOffset + readLen,
                byteArray = byteArray,
                contentLen = contentLen - readLen,
                contentOffset = readLen
            )
        }
    }
}

fun RandomAccessFile.writeContent(fileOffset: Long, byteArray: ByteArray, contentLen: Int, contentOffset: Int = 0) {
    synchronized(this) {
        seek(fileOffset)
        write(byteArray, contentOffset, contentLen)
    }
}