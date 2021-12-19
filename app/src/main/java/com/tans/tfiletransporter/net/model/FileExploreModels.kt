package com.tans.tfiletransporter.net.model

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


sealed class FileExploreContentModel

@Keep
@JsonClass(generateAdapter = true)
data class FileExploreHandshakeModel(
    @Json(name = "version") val version: Int,
    @Json(name = "path_separator") val pathSeparator: String
) : FileExploreContentModel()