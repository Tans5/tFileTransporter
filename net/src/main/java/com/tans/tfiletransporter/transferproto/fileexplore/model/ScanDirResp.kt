package com.tans.tfiletransporter.transferproto.fileexplore.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class ScanDirResp(
    val path: String,
    val childrenDirs: List<FileExploreDir>,
    val childrenFiles: List<FileExploreFile>
)