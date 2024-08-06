package com.tans.tfiletransporter

import java.util.Locale

const val KB = 1024
const val MB = 1024 * 1024
const val GB = 1024 * 1024 * 1024


fun Long.toSizeString(): String {
    return when (this) {
        in 0 until KB -> String.format(Locale.US, "%d B", this)
        in KB until MB -> String.format(Locale.US,"%.2f KB", this.toDouble() / KB)
        in MB until GB -> String.format(Locale.US,"%.2f MB", this.toDouble() / MB)
        in GB until Long.MAX_VALUE -> String.format(Locale.US,"%.2f GB", this.toDouble() / GB)
        else -> ""
    }
}