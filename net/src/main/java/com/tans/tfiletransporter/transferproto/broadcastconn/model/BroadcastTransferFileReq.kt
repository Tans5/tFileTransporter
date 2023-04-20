package com.tans.tfiletransporter.transferproto.broadcastconn.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class BroadcastTransferFileReq(
    val version: Int,
    val deviceName: String
)