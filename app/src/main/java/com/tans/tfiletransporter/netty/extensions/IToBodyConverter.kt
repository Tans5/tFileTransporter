package com.tans.tfiletransporter.netty.extensions

import com.tans.tfiletransporter.netty.PackageData

interface IToBodyConverter {

    fun couldHandle(type: Int, dataClass: Class<*>): Boolean

    fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData): T?
}