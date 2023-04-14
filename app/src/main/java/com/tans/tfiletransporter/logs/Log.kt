package com.tans.tfiletransporter.logs

import android.util.Log

object Log {
    private const val TAG = "tFileTransfer"

    fun d(msg: String) {
        d(TAG, msg)
    }

    fun e(msg: String, throwable: Throwable?) {
        e(TAG, msg, throwable)
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        Log.e(tag, msg, throwable)
    }

}