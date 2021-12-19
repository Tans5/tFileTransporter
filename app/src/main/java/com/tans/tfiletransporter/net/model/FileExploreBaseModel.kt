package com.tans.tfiletransporter.net.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

const val FILE_MODEL_TYPE_HANDSHAKE = 1

const val FILE_MODEL_TYPE_REQUEST_FOLDER = 2

const val FILE_MODEL_TYPE_SHARE_FOLDER = 3

const val FILE_MODEL_TYPE_REQUEST_FILES = 4

const val FILE_MODEL_TYPE_SHARE_FILES = 5

const val FILE_MODEL_TYPE_MESSAGE = 6

@JsonClass(generateAdapter = true)
data class FileExploreBaseModel(
    @Json(name = "type") val type: Int,
    @Json(name = "content") val content: String
)