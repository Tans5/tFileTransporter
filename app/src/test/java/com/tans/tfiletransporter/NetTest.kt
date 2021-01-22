package com.tans.tfiletransporter

import com.tans.tfiletransporter.net.launchBroadcastSender
import com.tans.tfiletransporter.utils.copyAvailableBytes
import com.tans.tfiletransporter.utils.findLocalAddressV4
import com.tans.tfiletransporter.utils.getBroadcastAddress
import com.tans.tfiletransporter.utils.toBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SocketChannel

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

        val job2 = launch(Dispatchers.IO) {
            val dc = DatagramChannel.open()
            dc.setOption(StandardSocketOptions.SO_BROADCAST, true)
            dc.bind(InetSocketAddress(if (systemName?.contains("Windows") == true) local else broadcast, 6666))
            val byteBuffer = ByteBuffer.allocate(1024)
            while (true) {
                byteBuffer.clear()
                val remote = dc.receive(byteBuffer)
                byteBuffer.flip()
                val msg = String(byteBuffer.copyAvailableBytes(), Charsets.UTF_8)
                println("Broadcast message: $msg, Address: ${(remote as InetSocketAddress).address.hostAddress}")
            }
        }

        val job3 = launch (Dispatchers.IO) {
            try {
                delay(200)
                val client = SocketChannel.open()
                client.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                client.connect(InetSocketAddress(local, 6667))
                val buffer = ByteBuffer.allocate(1024 + 4)
                val sendData = "My_MAC-mini Client".toByteArray(Charsets.UTF_8)
                buffer.put(sendData.size.toBytes())
                buffer.put(sendData)
                buffer.flip()
                client.write(buffer)
                buffer.clear()
                buffer.position(1024 + 3)
                client.read(buffer)
                buffer.position(1024 + 3)
                val reply = buffer.get()
                println("Reply From Server: $reply")
                client.close()
            } catch(e: Exception) {
                println(e)
            }
        }

        job.join()
        job2.join()
        job3.join()

    }
}