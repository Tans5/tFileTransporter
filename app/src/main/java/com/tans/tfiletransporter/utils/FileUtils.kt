package com.tans.tfiletransporter.utils

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
                newChildFile("${values[1]}-${values[3].toInt() + 1}${values[4]}")
            }
            regex2.matches(name) -> {
                val values = regex2.find(name)!!.groupValues
                newChildFile("${values[1]}-1${values[3]}")
            }
            regex3.matches(name) -> {
                val values = regex3.find(name)!!.groupValues
                newChildFile("${values[1]}-${values[3].toInt() + 1}")
            }
            else -> newChildFile("$name-1")
        }
    } else {
        Files.createFile(childPath)
        childPath
    }
}