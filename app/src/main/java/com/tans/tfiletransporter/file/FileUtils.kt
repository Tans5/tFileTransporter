package com.tans.tfiletransporter.file

import android.content.Context
import android.os.Build
import android.os.Environment
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreDir
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirReq
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirResp
import java.io.File

val LOCAL_DEVICE = "${Build.BRAND} ${Build.MODEL}"

fun ScanDirReq.scanChildren(context: Context): ScanDirResp {
    return try {
        val fileSeparator = File.separator
        if (requestPath.isBlank() || requestPath == fileSeparator) {
            val defaultStorageFile = Environment.getExternalStorageDirectory()
            val othersSdCardFiles =
                getSdCardPaths(context, true).map { File(it) }.filter { !defaultStorageFile.hasTargetParent(it) }
            ScanDirResp(
                path = fileSeparator,
                childrenFiles = emptyList(),
                childrenDirs = listOf(FileExploreDir(
                    name = "Default Storage",
                    path = defaultStorageFile.canonicalPath,
                    childrenCount = defaultStorageFile.listFiles()?.size ?: 0,
                    lastModify = defaultStorageFile.lastModified()
                )) + othersSdCardFiles.map { it.toFileExploreDir() }
            )
        } else {
            val currentFile = File(requestPath)
            if (currentFile.isDirectory && currentFile.canRead()) {
                val children = currentFile.listFiles() ?: emptyArray<File>()
                val childrenDirs = mutableListOf<FileExploreDir>()
                val childrenFiles = mutableListOf<FileExploreFile>()
                for (c in children) {
                    if (c.canRead()) {
                        if (c.isDirectory) {
                            childrenDirs.add(c.toFileExploreDir())
                        } else {
                            if (c.length() > 0) {
                                childrenFiles.add(c.toFileExploreFile())
                            }
                        }
                    }
                }
                ScanDirResp(
                    path = requestPath,
                    childrenDirs = childrenDirs,
                    childrenFiles = childrenFiles
                )
            } else {
                ScanDirResp(
                    path = requestPath,
                    childrenDirs = emptyList(),
                    childrenFiles = emptyList()
                )
            }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        ScanDirResp(
            path = requestPath,
            childrenDirs = emptyList(),
            childrenFiles = emptyList()
        )
    }
}

fun File.toFileExploreDir(): FileExploreDir {
    return if (isDirectory) {
        FileExploreDir(
            name = name,
            path = this.canonicalPath,
            childrenCount = listFiles()?.size ?: 0,
            lastModify = lastModified()
        )
    } else {
        error("${this.canonicalPath} is not dir.")
    }
}

fun File.toFileExploreFile(): FileExploreFile {
    return if (isFile) {
        FileExploreFile(
            name = name,
            path = this.canonicalPath,
            size = length(),
            lastModify = lastModified()
        )
    } else {
        error("${this.canonicalPath} is not file")
    }
}

fun List<FileLeaf.CommonFileLeaf>.toExploreFiles(): List<FileExploreFile> {
    return this.map {
        FileExploreFile(
            name = it.name,
            path = it.path,
            size = it.size,
            lastModify = it.lastModified
        )
    }
}

fun File.hasTargetParent(targetParent: File): Boolean {
    return if (canonicalPath == targetParent.canonicalPath) {
        true
    } else {
        val parent = parentFile
      if (parent == null) {
            false
        } else {
            if (parent.canonicalPath == targetParent.canonicalPath) {
                true
            } else {
                parent.hasTargetParent(targetParent)
            }
        }
    }
}