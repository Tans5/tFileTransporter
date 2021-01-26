package com.tans.tfiletransporter.file

import android.os.Environment
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Paths

object FileConstants {
    val FILE_SYSTEM: FileSystem = FileSystems.getDefault()
    val FILE_SEPARATOR: String = FILE_SYSTEM.separator
    val homePathString = Environment.getExternalStorageDirectory().path
    val homePath = Paths.get(homePathString)
}