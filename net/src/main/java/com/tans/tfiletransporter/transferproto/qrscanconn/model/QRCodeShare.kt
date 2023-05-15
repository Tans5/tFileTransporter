package com.tans.tfiletransporter.transferproto.qrscanconn.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class QRCodeShare(
    val version: Int,
    val deviceName: String,
    val address: Int
)