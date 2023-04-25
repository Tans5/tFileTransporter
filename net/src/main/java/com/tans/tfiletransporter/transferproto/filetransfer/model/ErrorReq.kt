package com.tans.tfiletransporter.transferproto.filetransfer.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class ErrorReq(
    val errorMsg: String
)