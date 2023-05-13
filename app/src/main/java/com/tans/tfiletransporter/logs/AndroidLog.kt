package com.tans.tfiletransporter.logs

import android.util.Log
import com.tans.tfiletransporter.ILog

object AndroidLog : ILog {

    override fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        Log.e(tag, msg, throwable)
    }

}