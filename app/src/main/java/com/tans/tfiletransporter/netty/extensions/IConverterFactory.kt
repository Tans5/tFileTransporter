package com.tans.tfiletransporter.netty.extensions

interface IConverterFactory {

    fun findBodyConverter(type: Int, dataClass: Class<*>) : IToBodyConverter?

    fun findPackageDataConverter(type: Int, dataClass: Class<*>): IToPackageDataConverter?
}