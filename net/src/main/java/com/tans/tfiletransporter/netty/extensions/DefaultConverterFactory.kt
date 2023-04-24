package com.tans.tfiletransporter.netty.extensions

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.tans.tfiletransporter.netty.PackageData
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter

open class DefaultConverterFactory : IConverterFactory {

    override fun findBodyConverter(type: Int, dataClass: Class<*>): IBodyConverter? {
        return defaultToBodyConverters.find { it.couldHandle(type, dataClass) }
    }

    override fun findPackageDataConverter(type: Int, dataClass: Class<*>): IPackageDataConverter? {
        return defaultToPackageDataConverters.find { it.couldHandle(type, dataClass) }
    }


    companion object {

        private val defaultToBodyConverters: List<IBodyConverter> = listOf(
            StringDataBodyConverter(),
            UnitDataBodyConverter(),
            PackageDataBodyConverter(),
            ByteArrayDataBodyConverter(),
            MoshiBodyConverter()
        )

        private val defaultToPackageDataConverters: List<IPackageDataConverter> = listOf(
            StringPackageDataConverter(),
            UnitPackageDataConverter(),
            PackageDataPackageDataConverter(),
            ByteArrayPackageDataConverter(),
            MoshiPackageDataConverter()
        )

        @Suppress("UNCHECKED_CAST")
        private class PackageDataBodyConverter : IBodyConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === PackageData::class.java
            }

            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData): T? {
                return packageData as? T
            }
        }

        @Suppress("UNCHECKED_CAST")
        private class StringDataBodyConverter : IBodyConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === String::class.java
            }
            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData): T {
                return packageData.body.toString(Charsets.UTF_8) as T
            }
        }

        @Suppress("UNCHECKED_CAST")
        private class ByteArrayDataBodyConverter : IBodyConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === ByteArray::class.java
            }

            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData): T? {
                return packageData.body as T
            }

        }

        @Suppress("UNCHECKED_CAST")
        private class UnitDataBodyConverter : IBodyConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === Unit::class.java
            }

            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData): T {
                return Unit as T
            }

        }

        private class MoshiBodyConverter : IBodyConverter {

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

        private class StringPackageDataConverter : IPackageDataConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === String::class.java
            }

            override fun <T> convert(
                type: Int,
                messageId: Long,
                data: T,
                dataClass: Class<T>
            ): PackageData {
                return PackageData(
                    type = type,
                    messageId = messageId,
                    body = (data as String).toByteArray(Charsets.UTF_8)
                )
            }
        }

        private class UnitPackageDataConverter : IPackageDataConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === Unit::class.java
            }

            override fun <T> convert(
                type: Int,
                messageId: Long,
                data: T,
                dataClass: Class<T>
            ): PackageData {
                return PackageData(
                    type = type,
                    messageId = messageId,
                    body = byteArrayOf()
                )
            }
        }

        private class ByteArrayPackageDataConverter : IPackageDataConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === ByteArray::class.java
            }

            override fun <T> convert(
                type: Int,
                messageId: Long,
                data: T,
                dataClass: Class<T>
            ): PackageData {
                return PackageData(
                    type = type,
                    messageId = messageId,
                    body = data as ByteArray
                )
            }


        }

        private class PackageDataPackageDataConverter : IPackageDataConverter {

            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === PackageData::class.java
            }

            override fun <T> convert(type: Int, messageId: Long, data: T, dataClass: Class<T>): PackageData? {
                return data as? PackageData?
            }
        }

        private class MoshiPackageDataConverter : IPackageDataConverter {

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

        private val defaultMoshi: Moshi by lazy {
            Moshi.Builder()
                .build()
        }
    }
}