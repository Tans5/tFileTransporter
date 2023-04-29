package com.tans.tfiletransporter.file

import android.os.Build
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreDir
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirReq
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirResp
import java.io.File

val LOCAL_DEVICE = "${Build.BRAND} ${Build.MODEL}"

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
                        if (c.length() > 0) {
                            childrenFiles.add(c.toFileExploreFile(rootDirString))
                        }
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