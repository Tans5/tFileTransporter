package com.tans.tfiletransporter

const val KB = 1024
const val MB = 1024 * 1024
const val GB = 1024 * 1024 * 1024


fun Long.toSizeString(): String {
    return when (this) {
        in 0 until KB -> String.format("%d B", this)
        in KB until MB -> String.format("%0.2f KB", this.toDouble() / KB)
        in MB until GB -> String.format("%0.2f MB", this.toDouble() / MB)
        in GB until Long.MAX_VALUE -> String.format("%0.2f GB", this.toDouble() / GB)
        else -> ""
    }
}