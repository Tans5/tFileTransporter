package com.tans.tfiletransporter.transferproto.p2pconn.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Keep
data class P2pHandshakeReq(
    val version: Int,
    val devicesName: String
)