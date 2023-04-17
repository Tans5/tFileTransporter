package com.tans.tfiletransporter.utils

import okio.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.io.use


fun ByteArray.enGzip(): ByteArray {
    return ByteArrayOutputStream().use { resultOutputStream ->
        resultOutputStream.sink().use { resultSink ->
            resultSink.gzip().buffer().use { gzipSink ->
                gzipSink.write(this)
            }
        }
        resultOutputStream.toByteArray()
    }
}


fun ByteArray.deGzip(): ByteArray {
    return ByteArrayInputStream(this).source().gzip().buffer().use { gzipSource ->
        gzipSource.readByteArray()
    }
}