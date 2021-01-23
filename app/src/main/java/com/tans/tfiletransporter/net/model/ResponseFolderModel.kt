package com.tans.tfiletransporter.net.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.threeten.bp.OffsetDateTime

@JsonClass(generateAdapter = true)
data class ResponseFolderModel(
    val path: String,
    @Json(name = "children_folders") val childrenFolders: List<Folder>,
    @Json(name = "children_files") val childrenFiles: List<File>
)

@JsonClass(generateAdapter = true)
data class Folder(
    val name: String,
    @Json(name = "child_count") val childCount: Long,
    @Json(name = "last_modify") val lastModify: OffsetDateTime
)

@JsonClass(generateAdapter = true)
data class File(
    val name: String,
    val size: Long,
    @Json(name = "last_modify") val lastModify: OffsetDateTime
)