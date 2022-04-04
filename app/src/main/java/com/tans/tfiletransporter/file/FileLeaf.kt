package com.tans.tfiletransporter.file

import androidx.annotation.Keep
import com.tans.tfiletransporter.net.model.File
import org.threeten.bp.Instant
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneId

@Keep
sealed class FileLeaf(
    val name: String,
    val path: String,
    val lastModified: Long
)

@Keep
class CommonFileLeaf(name: String, path: String, val size: Long, lastModified: Long) : FileLeaf(name, path, lastModified)

@Keep
class DirectoryFileLeaf(name: String, path: String, val childrenCount: Long, lastModified: Long) : FileLeaf(name, path, lastModified)

fun CommonFileLeaf.toFile(): File {
    return File(
        name = name,
        path = path,
        size = size,
        lastModify = OffsetDateTime.ofInstant(Instant.ofEpochMilli(lastModified), ZoneId.systemDefault())
    )
}