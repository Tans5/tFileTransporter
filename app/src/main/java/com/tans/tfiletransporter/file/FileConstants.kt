package com.tans.tfiletransporter.file

import java.nio.file.FileSystems

object FileConstants {
    val FILE_SYSTEM = FileSystems.getDefault()
    val FILE_SEPARATOR = FILE_SYSTEM.separator
}