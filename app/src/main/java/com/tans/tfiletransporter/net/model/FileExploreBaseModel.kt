package com.tans.tfiletransporter.net.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

const val FILE_MODEL_TYPE_HANDSHAKE = 1

@JsonClass(generateAdapter = true)
data class FileExploreBaseModel(
    @Json(name = "type") val type: Int,
    @Json(name = "content") val content: String
)