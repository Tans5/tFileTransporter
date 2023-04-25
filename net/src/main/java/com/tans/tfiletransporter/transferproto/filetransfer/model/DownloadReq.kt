package com.tans.tfiletransporter.transferproto.filetransfer.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile

@Keep
@JsonClass(generateAdapter = true)
data class DownloadReq(
    val file: FileExploreFile,
    val start: Long,
    val end: Long
)