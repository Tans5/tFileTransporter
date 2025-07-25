package com.tans.tfiletransporter.netty.extensions

import com.squareup.moshi.Moshi
import com.tans.tfiletransporter.netty.ByteArrayPool
import com.tans.tfiletransporter.netty.NetByteArray
import com.tans.tfiletransporter.netty.PackageData
import com.tans.tlrucache.memory.LruByteArrayPool

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

            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData, byteArrayPool: ByteArrayPool): T? {
                return packageData as? T
            }
        }

        @Suppress("UNCHECKED_CAST")
        private class StringDataBodyConverter : IBodyConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === String::class.java
            }
            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData, byteArrayPool: ByteArrayPool): T {
                val body = packageData.body
                val ret = String(body.value.value, 0, body.readSize, Charsets.UTF_8)
                byteArrayPool.put(body.value)
                return ret as T
            }
        }

        @Suppress("UNCHECKED_CAST")
        private class ByteArrayDataBodyConverter : IBodyConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === NetByteArray::class.java
            }

            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData, byteArrayPool: ByteArrayPool): T? {
                return packageData.body as T
            }

        }

        @Suppress("UNCHECKED_CAST")
        private class UnitDataBodyConverter : IBodyConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === Unit::class.java
            }

            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData, byteArrayPool: ByteArrayPool): T {
                return Unit as T
            }

        }

        private class MoshiBodyConverter : IBodyConverter {

            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean = true

            override fun <T> convert(type: Int, dataClass: Class<T>, packageData: PackageData, byteArrayPool: ByteArrayPool): T? {
                val body = packageData.body
                val str = String(body.value.value, 0, body.readSize, Charsets.UTF_8)
                byteArrayPool.put(body.value)
                return try {
                    defaultMoshi.adapter(dataClass)?.fromJson(str)
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
                dataClass: Class<T>,
                byteArrayPool: ByteArrayPool
            ): PackageData {
                val toWriteBytes = (data as String).toByteArray(Charsets.UTF_8)
                val byteArrayValue = byteArrayPool.get(toWriteBytes.size)
                System.arraycopy(toWriteBytes, 0, byteArrayValue.value, 0, toWriteBytes.size)
                return PackageData(
                    type = type,
                    messageId = messageId,
                    body = NetByteArray(
                        value = byteArrayValue,
                        readSize = toWriteBytes.size
                    )
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
                dataClass: Class<T>,
                byteArrayPool: ByteArrayPool
            ): PackageData {
                return PackageData(
                    type = type,
                    messageId = messageId,
                    body = NetByteArray(
                        LruByteArrayPool.Companion.ByteArrayValue(ByteArray(0)),
                        0
                    )
                )
            }
        }

        private class ByteArrayPackageDataConverter : IPackageDataConverter {
            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === NetByteArray::class.java
            }

            override fun <T> convert(
                type: Int,
                messageId: Long,
                data: T,
                dataClass: Class<T>,
                byteArrayPool: ByteArrayPool
            ): PackageData {
                return PackageData(
                    type = type,
                    messageId = messageId,
                    body = data as NetByteArray
                )
            }


        }

        private class PackageDataPackageDataConverter : IPackageDataConverter {

            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean {
                return dataClass === PackageData::class.java
            }

            override fun <T> convert(type: Int, messageId: Long, data: T, dataClass: Class<T>, byteArrayPool: ByteArrayPool): PackageData? {
                return data as? PackageData?
            }
        }

        private class MoshiPackageDataConverter : IPackageDataConverter {

            override fun couldHandle(type: Int, dataClass: Class<*>): Boolean = true

            override fun <T> convert(type: Int, messageId: Long, data: T, dataClass: Class<T>, byteArrayPool: ByteArrayPool): PackageData? {
                return try {
                    val json = defaultMoshi.adapter(dataClass)?.toJson(data)
                    if (json != null) {
                        val toWriteBytes = json.toByteArray(Charsets.UTF_8)
                        val byteArrayValue = byteArrayPool.get(toWriteBytes.size)
                        System.arraycopy(toWriteBytes, 0, byteArrayValue.value, 0, toWriteBytes.size)
                        PackageData(
                            type = type,
                            messageId = messageId,
                            body = NetByteArray(
                                byteArrayValue,
                                toWriteBytes.size
                            )
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