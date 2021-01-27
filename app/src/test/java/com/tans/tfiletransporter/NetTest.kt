package com.tans.tfiletransporter

import com.tans.tfiletransporter.net.launchBroadcastSender
import com.tans.tfiletransporter.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.DatagramChannel
import java.nio.channels.FileChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*

class NetTest {

    @Test
    fun broadcastSenderTest() = runBlocking {

        val systemName = System.getProperty("os.name")
        val userName = System.getProperty("user.name")

        val allAddress = findLocalAddressV4()
        val local = allAddress[0]
        val broadcast = local.getBroadcastAddress()
        val job = launch {
            kotlin.runCatching {
                launchBroadcastSender(broadMessage = "$userName's $systemName", localAddress = local) { remoteAddress, remoteDevice ->
                    println("RemoteAddress: $remoteAddress, RemoteDevice: $remoteDevice")
                    false
                }
            }
        }

//        val job2 = launch(Dispatchers.IO) {
//            val dc = DatagramChannel.open()
//            dc.setOption(StandardSocketOptions.SO_BROADCAST, true)
//            dc.bind(InetSocketAddress(if (systemName?.contains("Windows") == true) local else broadcast, 6666))
//            val byteBuffer = ByteBuffer.allocate(1024)
//            while (true) {
//                byteBuffer.clear()
//                val remote = dc.receive(byteBuffer)
//                byteBuffer.flip()
//                val msg = String(byteBuffer.copyAvailableBytes(), Charsets.UTF_8)
//                println("Broadcast message: $msg, Address: ${(remote as InetSocketAddress).address.hostAddress}")
//            }
//        }
//
//        val job3 = launch (Dispatchers.IO) {
//            try {
//                delay(200)
//                val client = SocketChannel.open()
//                client.setOption(StandardSocketOptions.SO_REUSEADDR, true)
//                client.connect(InetSocketAddress(local, 6667))
//                val buffer = ByteBuffer.allocate(1024 + 4)
//                val sendData = "My_MAC-mini Client".toByteArray(Charsets.UTF_8)
//                buffer.put(sendData.size.toBytes())
//                buffer.put(sendData)
//                buffer.flip()
//                client.write(buffer)
//                buffer.clear()
//                buffer.position(1024 + 3)
//                client.read(buffer)
//                buffer.position(1024 + 3)
//                val reply = buffer.get()
//                println("Reply From Server: $reply")
//                client.close()
//            } catch(e: Exception) {
//                println(e)
//            }
//        }

        job.join()
//        job2.join()
//        job3.join()

    }

//    @Test
//    fun readLimitTest() = runBlocking {
//        val job = launch(Dispatchers.IO) {
//            val fc = FileChannel.open(Paths.get("a.text"), StandardOpenOption.READ)
//            fc.readDataLimit(2048) {
//                val scanner = Scanner(it)
//                scanner.use {
//                    while (scanner.hasNextLine()) {
//                        println(scanner.nextLine())
//                    }
//                }
//            }
//        }
//        job.join()
//    }

    @Test
    fun writeSuspendSizeTest() = runBlocking {
        val job = launch(Dispatchers.IO) {
            val path = Paths.get("a.text")
            if (!Files.exists(path)) {
                Files.createFile(path)
            }
            val fc = FileChannel.open(path, StandardOpenOption.WRITE)
            val data = "Hello, World!!!".toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(1024)
            fc.writeSuspendSize(buffer, arrayOf(data[0]).toByteArray())
            fc.close()
        }
        job.join()
    }

//    @Test
//    fun writeDataLimitTest() = runBlocking {
//        val path = Paths.get("a.text")
//        if (!Files.exists(path)) {
//            Files.createFile(path)
//        }
//        val fileChannel = FileChannel.open(path, StandardOpenOption.WRITE)
//        val result = kotlin.runCatching {
//            fileChannel.writeDataLimit(
//                    limit = 10,
//                    buffer = ByteBuffer.allocate(2)
//            ) {
//                val data = "fadfadsfas21312313131".toByteArray(Charsets.UTF_8)
//                it.write(data)
//            }
//        }
//        fileChannel.close()
//        //println(result)
//    }

    @Test
    fun fileNameTest() = runBlocking {
        // ((.|\s)+)(-\d+)?(\..+)?$
        val regex1 = "((.|\\s)+)-(\\d+)(\\..+)$".toRegex()
        val name = "3213131fasd.tans"
        if (regex1.matches(name)) {
            val i = regex1.find(name)
            i?.groupValues?.map {
                println(it)
            }
        }
        val regex2 = "((.|\\s)+)(\\..+)\$".toRegex()
        if (regex2.matches(name)) {
            val i = regex2.find(name)
            i?.groupValues?.map {
                println(it)
            }
        }
        val regex3 = "((.|\\s)+)-(\\d+)$".toRegex()

        if (regex3.matches(name)) {
            val i = regex3.find(name)
            i?.groupValues?.map {
                println(it)
            }
        }

        Unit

    }

    @Test
    fun fileCreateTest() {
        val parent = Paths.get("testdir")
        if (!Files.exists(parent)) {
            Files.createDirectory(parent)
        }
        parent.newChildFile("tea  fsda")
    }
}