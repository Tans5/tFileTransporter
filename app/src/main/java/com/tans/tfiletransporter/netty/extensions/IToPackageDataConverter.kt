package com.tans.tfiletransporter.netty.extensions

import com.tans.tfiletransporter.netty.PackageData

interface IToPackageDataConverter {

    fun couldHandle(type: Int, dataClass: Class<*>): Boolean

    fun <T> convert(type: Int, messageId: Long, data: T, dataClass: Class<T>): PackageData?
}