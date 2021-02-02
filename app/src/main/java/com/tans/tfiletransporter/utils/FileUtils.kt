package com.tans.tfiletransporter.utils

import android.content.Context
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.file.FileConstants.GB
import com.tans.tfiletransporter.file.FileConstants.KB
import com.tans.tfiletransporter.file.FileConstants.MB
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun Path.newChildFile(name: String): Path {
    val childPath = Paths.get(toAbsolutePath().toString(), name)
    return if (Files.exists(childPath)) {
        val regex1 = "((.|\\s)+)-(\\d+)(\\..+)$".toRegex()
        val regex2 = "((.|\\s)+)(\\..+)\$".toRegex()
        val regex3 = "((.|\\s)+)-(\\d+)$".toRegex()
        when {
            regex1.matches(name) -> {
                val values = regex1.find(name)!!.groupValues
                newChildFile("${values[1]}-${values[3].toIntOrNull() ?: 0 + 1}${values[4]}")
            }
            regex2.matches(name) -> {
                val values = regex2.find(name)!!.groupValues
                newChildFile("${values[1]}-1${values[3]}")
            }
            regex3.matches(name) -> {
                val values = regex3.find(name)!!.groupValues
                newChildFile("${values[1]}-${values[3].toIntOrNull() ?: 0 + 1}")
            }
            else -> newChildFile("$name-1")
        }
    } else {
        Files.createFile(childPath)
        childPath
    }
}

fun Context.getSizeString(size: Long): String = when (size) {
    in 0 until KB -> getString(R.string.size_B, size)
    in KB until MB -> getString(R.string.size_KB, size.toDouble() / KB)
    in MB until GB -> getString(R.string.size_MB, size.toDouble() / MB)
    in GB until Long.MAX_VALUE -> getString(R.string.size_GB, size.toDouble() / GB)
    else -> ""
}