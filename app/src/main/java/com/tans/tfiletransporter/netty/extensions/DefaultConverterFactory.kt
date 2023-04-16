package com.tans.tfiletransporter.netty.extensions

import com.tans.tfiletransporter.defaultMoshi
import com.tans.tfiletransporter.netty.PackageData

open class DefaultConverterFactory : IConverterFactory {

    override fun findBodyConverter(type: Int, dataClass: Class<*>): IToBodyConverter? {
        return defaultToBodyConverters.find { it.couldHandle(type, dataClass) }
    }

    override fun findPackageDataConverter(type: Int, dataClass: Class<*>): IToPackageDataConverter? {
        return defaultToPackageDataConverters.find { it.couldHandle(type, dataClass) }
    }


    companion object {

        private val defaultToBodyConverters: List<IToBodyConverter> = listOf(
            PackageDataToBodyConverter(),
            MoshiToBodyConverter()
        )

        private val defaultToPackageDataConverters: List<IToPackageDataConverter> = listOf(
            PackageDataToPackageDataConverter(),
            MoshiToPackageDataConverter()
        )

        @Suppress("UNCHECKED_CAST")
        private class PackageDataToBodyConverter : IToBodyConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === PackageData::class.java
            }

            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData): T? {
                return packageData as? T
            }
        }

        private class MoshiToBodyConverter : IToBodyConverter {

            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean = true

            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData): T? {
                return try {
                    defaultMoshi.adapter(dataClass)?.fromJson(packageData.body.toString(Charsets.UTF_8))
                } catch (e: Throwable) {
                    e.printStackTrace()
                    null
                }
            }
        }

        private class PackageDataToPackageDataConverter : IToPackageDataConverter {

            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === PackageData::class.java
            }

            override fun <T> convert(type: Int, messageId: Long, data: T, dataClass: Class<T>): PackageData? {
                return data as? PackageData?
            }
        }

        private class MoshiToPackageDataConverter : IToPackageDataConverter {

            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean = true

            override fun <T> convert(type: Int, messageId: Long, data: T, dataClass: Class<T>): PackageData? {
                return try {
                    val json = defaultMoshi.adapter(dataClass)?.toJson(data)
                    if (json != null) {
                        PackageData(
                            type = type,
                            messageId = messageId,
                            body = json.toByteArray(Charsets.UTF_8)
                        )
                    } else {
                        null
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                    null
                }
            }

        }
    }
}