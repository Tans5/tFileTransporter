package com.tans.tfiletransporter.transferproto.fileexplore.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class SendFilesReq(
    val sendFiles: List<FileExploreFile>,
    val maxConnection: Int
)