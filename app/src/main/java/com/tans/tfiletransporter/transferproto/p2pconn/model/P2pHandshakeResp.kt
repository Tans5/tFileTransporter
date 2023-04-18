package com.tans.tfiletransporter.transferproto.p2pconn.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class P2pHandshakeResp(
    val deviceName: String,
    val clientAddress: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as P2pHandshakeResp

        if (deviceName != other.deviceName) return false
        if (!clientAddress.contentEquals(other.clientAddress)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = deviceName.hashCode()
        result = 31 * result + clientAddress.contentHashCode()
        return result
    }
}