package com.tans.tfiletransporter.netty

import com.tans.tfiletransporter.toBytes
import com.tans.tfiletransporter.toInt
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Enumeration

/**
 * return broadcast address and subnet mask.
 */
fun InetAddress.getBroadcastAddress()
        : Pair<InetAddress, Short> = NetworkInterface.getByInetAddress(this)?.interfaceAddresses
    ?.filter {
        val broadcast = it.broadcast
        val address = it.address
        address == this && broadcast != null && broadcast.address?.size == 4
    }?.firstNotNullOfOrNull {
        val broadcast = it.broadcast
        val maskLen = it.networkPrefixLength
        broadcast to maskLen
    } ?: (InetAddress.getByAddress((-1).toBytes()) to 24.toShort())


fun findLocalAddressV4(): List<InetAddress> {
    val interfaces: Enumeration<NetworkInterface>? = NetworkInterface.getNetworkInterfaces()
    val result = ArrayList<InetAddress>()
    if (interfaces != null) {
        while (interfaces.hasMoreElements()) {
            val inetAddresses = interfaces.nextElement().inetAddresses
            while (inetAddresses.hasMoreElements()) {
                val address = inetAddresses.nextElement()
                if (address.address.size == 4 && !address.isLinkLocalAddress && !address.isLoopbackAddress) {
                    result.add(address)
                }
            }
        }
    }
    return result
}

fun InetAddress.toInt(): Int {
    return address.toInt()
}

fun Int.toInetAddress(): InetAddress {
    return InetAddress.getByAddress(this.toBytes())
}