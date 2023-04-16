package com.tans.tfiletransporter.logs

import android.util.Log

object AndroidLog : ILog {
    private const val TAG = "tFileTransfer"

    fun d(msg: String) {
        d(TAG, msg)
    }

    fun e(msg: String, throwable: Throwable?) {
        e(TAG, msg, throwable)
    }

    override fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        Log.e(tag, msg, throwable)
    }

}