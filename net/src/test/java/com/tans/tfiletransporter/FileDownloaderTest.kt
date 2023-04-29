package com.tans.tfiletransporter

import com.tans.tfiletransporter.netty.findLocalAddressV4
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.filetransfer.FileDownloader
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferObserver
import com.tans.tfiletransporter.transferproto.filetransfer.FileTransferState
import com.tans.tfiletransporter.transferproto.filetransfer.SpeedCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

object FileDownloaderTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val rootDir = File("./net/testdir")
        if (!rootDir.isDirectory) {
            rootDir.mkdirs()
        }
        val test1File = File(rootDir, "test.mp4")
        if (!test1File.isFile) {
            println("TestFile1 is not exit")
            return
        }
        val test2File = File(rootDir, "test2.mp4")
        if (!test2File.isFile) {
            println("TestFile2 is not exit")
            return
        }
        val downloadDir = File(rootDir, "download")
        val localAddress = findLocalAddressV4()[0]
        val downloader = FileDownloader(
            downloadDir = downloadDir,
            files = listOf(FileExploreFile(
                name = test1File.name,
                path = test1File.name,
                size = test1File.length(),
                lastModify = test1File.lastModified()),
                FileExploreFile(
                    name = test2File.name,
                    path = test2File.name,
                    size = test2File.length(),
                    lastModify = test2File.lastModified()
                )
            ),
            connectAddress = localAddress,
            maxConnectionSize = 8,
            log = TestLog
        )
        val speedCalculator = SpeedCalculator()

        speedCalculator.addObserver(object : SpeedCalculator.Companion.SpeedObserver {
            override fun onSpeedUpdated(speedInBytes: Long, speedInString: String) {
                println("Downloader speed: $speedInString")
            }
        })

        downloader.addObserver(object : FileTransferObserver {
            override fun onNewState(s: FileTransferState) {
                if (s is FileTransferState.Started) {
                    speedCalculator.start()
                } else if (s !is FileTransferState.NotExecute) {
                    speedCalculator.stop()
                }
                println("Downloader state: $s")
            }

            override fun onStartFile(file: FileExploreFile) {
                println("Downloader start ${file.name}")
            }

            override fun onProgressUpdate(file: FileExploreFile, progress: Long) {
                speedCalculator.updateCurrentSize(progress)
                println("Downloader process ${progress.toDouble() / file.size.toDouble()}")
            }

            override fun onEndFile(file: FileExploreFile) {
                speedCalculator.reset()
                println("Downloader end: ${file.name}")
            }

        })
        downloader.start()
        runBlocking {
            delay(60 * 1000 * 5)
        }
    }
}