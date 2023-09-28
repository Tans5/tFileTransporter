package com.tans.tfiletransporter.utils

import java.util.Locale

val mediaFileSuffixAndMimeType: Map<String, String> = mapOf(
    "aac" to "audio/aac",
    "avi" to "video/x-msvideo",
    "bmp" to "image/bmp",
    "dv" to "video/x-dv",
    "git" to "image/gif",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "mid" to "audio/midi",
    "midi" to "audio/midi",
    "mp3" to "audio/mpeg",
    "mp4" to "video/mp4",
    "mp4a" to "audio/mp4",
    "mpeg" to "audio/mpeg",
    "mpg" to "audio/mpeg",
    "mov" to "video/quicktime",
    "mpeg" to "video/mpeg",
    "oga" to "audio/ogg",
    "ogv" to "video/ogg",
    "png" to "image/png",
    "svg" to "image/svg-xml",
    "tif" to "image/tiff",
    "tiff" to "image/tiff",
    "wav" to "audio/wav",
    "wama" to "audio/x-ms-wma",
    "weba" to "audio/webm",
    "webm" to "video/webm",
    "webp" to "image/webp",
    "wm" to "video/x-ms-wmv",
    "flv" to "video/x-flv",
    "mkv" to "video/x-matroska",
    "3gp" to "video/3gp",
    "3g2" to "video/3g2",
    "flac" to "audio/flac"
)

enum class MediaType {
    Audio,
    Video,
    Image
}

/**
 * @return Media file Mimetype and MediaType
 */
fun getMediaMimeTypeWithFileName(fileName: String): Pair<String, MediaType>? {
    val fileSuffixRegex = ".*\\.(.+)$".toRegex()
    val matchResult = fileSuffixRegex.find(fileName)
    return matchResult?.groupValues?.get(1)?.lowercase(Locale.US).let { suffix ->
        val mimeType = mediaFileSuffixAndMimeType[suffix]
        when {
            mimeType?.startsWith("audio") == true -> mimeType to MediaType.Audio
            mimeType?.startsWith("video") == true -> mimeType to MediaType.Video
            mimeType?.startsWith("image") == true -> mimeType to MediaType.Image
            else -> null
        }
    }
}