package com.tans.tfiletransporter.file

import android.os.Environment
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths

object FileConstants {
    val FILE_SYSTEM: FileSystem = FileSystems.getDefault()
    val FILE_SEPARATOR: String = FILE_SYSTEM.separator
    val homePathString: String = Environment.getExternalStorageDirectory().path
    val homePath: Path = Paths.get(homePathString)
    const val KB = 1024
    const val MB = 1024 * 1024
    const val GB = 1024 * 1024 * 1024
}