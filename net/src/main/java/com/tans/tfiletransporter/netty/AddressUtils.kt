package com.tans.tfiletransporter.netty

import com.tans.tfiletransporter.toBytes
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * return broadcast address and subnet mask.
 */
fun InetAddress.getBroadcastAddress()
        : Pair<InetAddress, Short> = NetworkInterface.getByInetAddress(this).interfaceAddresses
    .filter {
        val broadcast = it.broadcast
        broadcast != null && broadcast.address?.size == 4
    }.firstNotNullOfOrNull {
        val broadcast = it.broadcast
        val maskLen = it.networkPrefixLength
        broadcast to maskLen
    } ?: (InetAddress.getByAddress((-1).toBytes()) to 24.toShort())

fun getAndroidBroadcastAddress(): InetAddress = InetAddress.getByAddress((-1).toBytes())


fun findLocalAddressV4(): List<InetAddress> {
    val interfaces = NetworkInterface.getNetworkInterfaces()
    val result = ArrayList<InetAddress>()
    while (interfaces.hasMoreElements()) {
        val inetAddresses = interfaces.nextElement().inetAddresses
        while (inetAddresses.hasMoreElements()) {
            val address = inetAddresses.nextElement()
            if (address.address.size == 4 && !address.isLinkLocalAddress && !address.isLoopbackAddress) {
                result.add(address)
            }
        }
    }
    return result
}