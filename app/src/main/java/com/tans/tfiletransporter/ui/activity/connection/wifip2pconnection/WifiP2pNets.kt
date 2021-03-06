package com.tans.tfiletransporter.ui.activity.connection.wifip2pconnection

import android.net.wifi.p2p.WifiP2pInfo
import com.tans.tfiletransporter.net.FILE_WIFI_P2P_CONNECT_LISTEN_PORT
import com.tans.tfiletransporter.net.commonNetBufferPool
import com.tans.tfiletransporter.net.connection.RemoteDevice
import com.tans.tfiletransporter.utils.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions

suspend fun remoteDeviceInfo(
    connectionInfo: WifiP2pInfo,
    localDevice: String
) : Pair<RemoteDevice, InetAddress> {
    if (connectionInfo.isGroupOwner) {
        val ssc = openAsynchronousServerSocketChannelSuspend()
        ssc.use {
            ssc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
            ssc.bindSuspend(address = InetSocketAddress(connectionInfo.groupOwnerAddress, FILE_WIFI_P2P_CONNECT_LISTEN_PORT), 1)
            val sc = ssc.acceptSuspend()
            sc.use {
                val buffer = commonNetBufferPool.requestBuffer()
                try {
                    sc.readSuspendSize(buffer, 4)
                    val remoteDeviceInfoSize = buffer.asIntBuffer().get()
                    sc.readSuspendSize(buffer, remoteDeviceInfoSize)
                    val remoteDeviceInfo = buffer.copyAvailableBytes().toString(Charsets.UTF_8)
                    val localDeviceData = localDevice.toByteArray(Charsets.UTF_8)
                    buffer.clear()
                    buffer.put(localDeviceData.size.toBytes())
                    buffer.flip()
                    sc.writeSuspendSize(buffer)
                    buffer.clear()
                    buffer.put(localDeviceData)
                    buffer.flip()
                    sc.writeSuspendSize(buffer)
                    val remoteAddress = (sc.remoteAddress as InetSocketAddress).address
                    return (remoteAddress to remoteDeviceInfo) to connectionInfo.groupOwnerAddress
                } finally {
                    commonNetBufferPool.recycleBuffer(buffer)
                }
            }
        }
    } else {
        val sc = openAsynchronousSocketChannel()
        sc.use {
            sc.connectSuspend(InetSocketAddress(connectionInfo.groupOwnerAddress, FILE_WIFI_P2P_CONNECT_LISTEN_PORT))
            val buffer = commonNetBufferPool.requestBuffer()
            try {
                val localDeviceData = localDevice.toByteArray(Charsets.UTF_8)
                buffer.put(localDeviceData.size.toBytes())
                buffer.flip()
                sc.writeSuspendSize(buffer)
                buffer.clear()
                buffer.put(localDeviceData)
                buffer.flip()
                sc.writeSuspendSize(buffer)
                sc.readSuspendSize(buffer, 4)
                val remoteDeviceInfoSize = buffer.asIntBuffer().get()
                sc.readSuspendSize(buffer, remoteDeviceInfoSize)
                val remoteDeviceInfo = buffer.copyAvailableBytes().toString(Charsets.UTF_8)
                val localAddress = (sc.localAddress as InetSocketAddress).address
                return (connectionInfo.groupOwnerAddress to remoteDeviceInfo) to localAddress
            } finally {
                commonNetBufferPool.recycleBuffer(buffer)
            }
        }
    }
}