package com.tans.tfiletransporter.file

import java.nio.file.FileSystem
import java.nio.file.FileSystems

object FileConstants {
    val FILE_SYSTEM: FileSystem = FileSystems.getDefault()
    val FILE_SEPARATOR: String = FILE_SYSTEM.separator
}