package com.tans.tfiletransporter

import okio.*
import okio.Path.Companion.toOkioPath
import java.io.File

object FileTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val f = File("test.txt")
        if (!f.exists()) {
            f.createNewFile()
        }
        val fileHandle = FileSystem.SYSTEM.openReadWrite(f.toOkioPath())
        fileHandle.resize(1024 * 1024 * 10)
        fileHandle.sink(100).buffer().use {

        }
        fileHandle.source()
    }
}