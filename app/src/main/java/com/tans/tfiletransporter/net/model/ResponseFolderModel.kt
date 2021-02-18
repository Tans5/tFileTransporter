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
    val path: String,
    @Json(name = "child_count") val childCount: Long,
    @Json(name = "last_modify") val lastModify: OffsetDateTime
)

@JsonClass(generateAdapter = true)
data class File(
    val name: String,
    val path: String,
    val size: Long,
    @Json(name = "last_modify") val lastModify: OffsetDateTime
)

@JsonClass(generateAdapter = true)
data class FileMd5(
        /**
         * This is not file's md5, and use path of file calculate md5, because calculate big files' md5 too slow.
         */
        val md5: ByteArray,
        val file: File
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileMd5

        if (!md5.contentEquals(other.md5)) return false
        if (file != other.file) return false

        return true
    }

    override fun hashCode(): Int {
        var result = md5.contentHashCode()
        result = 31 * result + file.hashCode()
        return result
    }
}