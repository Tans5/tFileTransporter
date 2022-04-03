package com.tans.tfiletransporter.logs

import android.util.Log

object Log {
    private const val TAG = "tFileTransfer"

    fun d(msg: String) {
        Log.d(TAG, msg)
    }

    fun e(msg: String, throwable: Throwable?) {
        Log.d(TAG, msg, throwable)
    }

}