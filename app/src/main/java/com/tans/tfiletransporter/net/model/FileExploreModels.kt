package com.tans.tfiletransporter.net.model

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


sealed class FileExploreContentModel

@Keep
@JsonClass(generateAdapter = true)
data class FileExploreHandshakeModel(
    @Json(name = "version") val version: Int,
    @Json(name = "path_separator") val pathSeparator: String,
    @Json(name = "device_name") val deviceName: String
) : FileExploreContentModel()

@Keep
@JsonClass(generateAdapter = true)
data class RequestFolderModel(
    @Json(name = "request_path") val requestPath: String
) : FileExploreContentModel()


@Keep
@JsonClass(generateAdapter = true)
data class ShareFolderModel(
    val path: String,
    @Json(name = "children_folders") val childrenFolders: List<Folder>,
    @Json(name = "children_files") val childrenFiles: List<File>
) : FileExploreContentModel()

@Keep
@JsonClass(generateAdapter = true)
data class RequestFilesModel(
    @Json(name = "request_files") val requestFiles: List<FileMd5>
) : FileExploreContentModel()

@Keep
@JsonClass(generateAdapter = true)
data class ShareFilesModel(
    @Json(name = "share_files") val shareFiles: List<FileMd5>
) : FileExploreContentModel()


@Keep
@JsonClass(generateAdapter = true)
data class MessageModel(
    @Json(name = "message") val message: String
) : FileExploreContentModel()