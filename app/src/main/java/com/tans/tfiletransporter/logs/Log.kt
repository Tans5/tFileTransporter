package com.tans.tfiletransporter.logs

import android.util.Log

object Log {

    fun d(clazz: Class<*>, msg: String) {
        d(clazz.name, msg)
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    fun e(clazz: Class<*>, msg: String, throwable: Throwable) {
        e(clazz.name, msg, throwable)
    }

    fun e(tag: String, msg: String, throwable: Throwable) {
        Log.e(tag, msg, throwable)
    }

}