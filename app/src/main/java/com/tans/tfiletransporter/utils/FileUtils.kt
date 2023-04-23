package com.tans.tfiletransporter.utils

import android.content.Context
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.file.FileConstants.GB
import com.tans.tfiletransporter.file.FileConstants.KB
import com.tans.tfiletransporter.file.FileConstants.MB
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreDir
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirReq
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirResp
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import kotlin.io.path.name

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

fun Context.getSizeString(size: Long): String = when (size) {
    in 0 until KB -> getString(R.string.size_B, size)
    in KB until MB -> getString(R.string.size_KB, size.toDouble() / KB)
    in MB until GB -> getString(R.string.size_MB, size.toDouble() / MB)
    in GB until Long.MAX_VALUE -> getString(R.string.size_GB, size.toDouble() / GB)
    else -> ""
}

// 1MB
const val FILE_BUFFER_SIZE: Int = 1024 * 1024

/**
 * return the size is 16.
 */
fun Path.getFileMd5(): ByteArray {
    if (!Files.exists(this) || Files.isDirectory(this)) {
        error("Wrong file path: ${this.toAbsolutePath()}")
    }
    val md5 = MessageDigest.getInstance("MD5")
    val fileChannel = FileChannel.open(this, StandardOpenOption.READ)
    val byteBuffer = ByteBuffer.allocate(FILE_BUFFER_SIZE)
    fileChannel.use {
        while (true) {
            byteBuffer.clear()
            if (fileChannel.read(byteBuffer) == -1) {
                break
            }
            byteBuffer.flip()
            md5.update(byteBuffer)
        }
    }
    return md5.digest()
}

/**
 * return the size is 16.
 */
fun Path.getFilePathMd5(): ByteArray {
    val pathStringData = toAbsolutePath().toString().toByteArray(Charsets.UTF_8)
    val md5 = MessageDigest.getInstance("MD5")
    return md5.digest(pathStringData)
}

fun ByteBuffer.readFrom(source: ByteBuffer, size: Int) {
    clear()
    val mySize = capacity()
    val sourceSize = source.capacity()
    if (mySize < size || sourceSize < size) {
        error("Wrong Size: $size")
    }
    for (i in 0 until size) {
        put(source.get())
    }
    flip()
}

fun ScanDirReq.scanChildren(rootDir: File): ScanDirResp {
    val rootDirString = rootDir.canonicalPath
    val currentFile = File(rootDir, requestPath)
    return if (currentFile.isDirectory && currentFile.canRead()) {
        try {
            val children = currentFile.listFiles() ?: emptyArray<File>()
            val childrenDirs = mutableListOf<FileExploreDir>()
            val childrenFiles = mutableListOf<FileExploreFile>()
            for (c in children) {
                if (c.canRead()) {
                    if (c.isDirectory) {
                        childrenDirs.add(c.toFileExploreDir(rootDirString))
                    } else {
                        childrenFiles.add(c.toFileExploreFile(rootDirString))
                    }
                }
            }
            ScanDirResp(
                path = requestPath,
                childrenDirs = childrenDirs,
                childrenFiles = childrenFiles
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            ScanDirResp(
                path = requestPath,
                childrenDirs = emptyList(),
                childrenFiles = emptyList()
            )
        }
    } else {
        ScanDirResp(
            path = requestPath,
            childrenDirs = emptyList(),
            childrenFiles = emptyList()
        )
    }
}

fun File.toFileExploreDir(rootDirString: String): FileExploreDir {
    return if (isDirectory) {
        FileExploreDir(
            name = name,
            path = this.canonicalPath.removePrefix(rootDirString),
            childrenCount = listFiles()?.size ?: 0,
            lastModify = lastModified()
        )
    } else {
        error("${this.canonicalPath} is not dir.")
    }
}

fun File.toFileExploreFile(rootDirString: String): FileExploreFile {
    return if (isFile) {
        FileExploreFile(
            name = name,
            path = this.canonicalPath.removePrefix(rootDirString),
            size = length(),
            lastModify = lastModified()
        )
    } else {
        error("${this.canonicalPath} is not file")
    }
}