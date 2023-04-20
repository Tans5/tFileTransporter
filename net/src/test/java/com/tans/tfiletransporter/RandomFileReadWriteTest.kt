package com.tans.tfiletransporter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.*
import okio.Path.Companion.toOkioPath
import java.io.File

object RandomFileReadWriteTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val rootDir = File("./app/testdir")
        if (!rootDir.isDirectory) {
            rootDir.mkdirs()
        }
        val originFile = File(rootDir, "origin_file")
        if (!originFile.isFile) {
            println("OriginFile is not exit")
            return
        }
        val outputFile = File(rootDir, "copy_file")
        if (!outputFile.isFile) {
            outputFile.createNewFile()
        }
        val originFileSize = originFile.length()
        println("OriginFile size: $originFileSize Bytes")
        val minFrameSize = 1024 * 1024 * 10L // 10 MB
        val ioExecutor = Dispatchers.IO.asExecutor()
        val frames = createFrameRange(
            fileSize = originFileSize,
            frameCount = 30,
            minFrameSize = minFrameSize
        )
        val originFileHandle = FileSystem.SYSTEM.openReadWrite(originFile.toOkioPath())
        val outputFileHandle = FileSystem.SYSTEM.openReadWrite(outputFile.toOkioPath())
        outputFileHandle.resize(originFileSize)
        for (frame in frames) {
            ioExecutor.execute {
                val start = frame.first
                val end = frame.second
                val frameSize = end - start
                val bufferSize = 1024 * 256L // 256kb
                var hasRead = 0L
                originFileHandle.source(fileOffset = start).buffer().use { source ->
                    outputFileHandle.sink(fileOffset = start).buffer().use { sink ->
                        while (hasRead < frameSize) {
                            val thisTimeRead = if ((frameSize - hasRead) < bufferSize) {
                                frameSize - hasRead
                            } else {
                                bufferSize
                            }
                            val bytes = source.readByteArray(thisTimeRead)
                            hasRead += thisTimeRead
                            sink.write(bytes)
                        }
                    }
                }
                println("$frame, finished")
            }
        }
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }

    fun createFrameRange(
        fileSize: Long,
        frameCount: Int,
        minFrameSize: Long): List<Pair<Long, Long>> {
        if (fileSize <= 0) error("Wrong file size")
        return if (frameCount * minFrameSize > fileSize) {
            val lastFrameSize = fileSize % minFrameSize
            val realFrameCount = fileSize / minFrameSize + if (lastFrameSize > 0L) 1 else 0
            val result = mutableListOf<Pair<Long, Long>>()
            for (i in 0 until realFrameCount) {
                val start = i * minFrameSize
                val end = if (i != realFrameCount - 1) (i + 1) * minFrameSize else fileSize
                result.add(start to end)
            }
            result
        } else {
            val lastFrameSize = fileSize % frameCount
            val frameSize = if (lastFrameSize == 0L) {
                fileSize / frameCount
            }  else {
                (fileSize - lastFrameSize) / (frameCount - 1)
            }
            val result = mutableListOf<Pair<Long, Long>>()
            for (i in 0 until frameCount) {
                val start = i * frameSize
                val end = if (i != frameCount - 1) (i + 1) * frameSize else fileSize
                result.add(start to end)
            }
            result
        }
    }
}