package com.tans.tfiletransporter

import com.tans.tfiletransporter.logs.ILog

object TestLog : ILog {

    override fun d(tag: String, msg: String) {
        println("[$tag][Debug]: $msg")
    }

    override fun e(tag: String, msg: String, throwable: Throwable?) {
        println("[$tag][Error]: $msg")
    }
}