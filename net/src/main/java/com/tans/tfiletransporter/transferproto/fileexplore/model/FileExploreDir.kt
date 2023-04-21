package com.tans.tfiletransporter.transferproto.fileexplore.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class FileExploreDir(
    val name: String,
    val path: String,
    val childrenCount: Int,
    val lastModify: Long
)