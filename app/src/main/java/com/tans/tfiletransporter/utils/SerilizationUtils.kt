package com.tans.tfiletransporter.utils

import com.tans.tfiletransporter.App


inline fun <reified T : Any> T.toJson(): String? {
    return try {
        App.defaultMoshi.adapter(T::class.java).toJson(this)
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

inline fun <reified T : Any> String.fromJson(): T? {
    return try {
        App.defaultMoshi.adapter(T::class.java).fromJson(this)
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

