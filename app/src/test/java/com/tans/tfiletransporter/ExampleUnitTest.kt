package com.tans.tfiletransporter

import com.tans.tfiletransporter.utils.findLocalAddressV4
import com.tans.tfiletransporter.utils.getBroadcastAddress
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() = runBlocking {
//        val localhost = InetAddress.getLocalHost()
//        val broadcastSender = BroadcastSender(broadMessage = "My MAC-mini", localAddress = localhost)
//        val job1 = launch(Dispatchers.IO) {
//            broadcastSender.startBroadcastSender()
//        }
//        val job2 = launch(Dispatchers.IO) {
//            val dc = DatagramChannel.open()
//            dc.setOption(StandardSocketOptions.SO_BROADCAST, true)
//            dc.bind(InetSocketAddress(broadcastSender.broadcastAddress, 6666))
//            val byteBuffer = ByteBuffer.allocate(1024)
//            while (true) {
//                byteBuffer.clear()
//                dc.receive(byteBuffer)
//                byteBuffer.flip()
//                val msg = String(byteBuffer.array(), Charsets.UTF_8)
//                println("Broadcast message: $msg")
//            }
//        }
//        job1.join()
//        job2.join()

//        val result = findLocalAddressV4()
//        val broadcast = result?.getBroadcastAddress()
//        println(result)
//        println(broadcast)
    }
}