package com.tans.tfiletransporter.file

import android.content.Context
import android.os.Environment
import java.io.File


fun File.toFileLeaf(): FileLeaf.CommonFileLeaf {
    return FileLeaf.CommonFileLeaf(
        name = name,
        path = canonicalPath,
        size = length(),
        lastModified = lastModified()
    )
}

fun File.toDirLeaf(): FileLeaf.DirectoryFileLeaf {
    return FileLeaf.DirectoryFileLeaf(
        name = name,
        path = canonicalPath,
        childrenCount = listFiles()?.size?.toLong() ?: 0L,
        lastModified = lastModified()
    )
}

fun File.childrenLeafs(): Pair<List<FileLeaf.DirectoryFileLeaf>, List<FileLeaf.CommonFileLeaf>> {
    val children = listFiles() ?: emptyArray<File>()
    val resultFiles = mutableListOf<FileLeaf.CommonFileLeaf>()
    val resultDirs = mutableListOf<FileLeaf.DirectoryFileLeaf>()
    for (c in children) {
        try {
            if (c.canRead()) {
                if (c.isFile) {
                    if (c.length() > 0) {
                        resultFiles.add(c.toFileLeaf())
                    }
                } else {
                    resultDirs.add(c.toDirLeaf())
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
    return resultDirs to resultFiles
}

fun createLocalRootTree(context: Context): FileTree {
    val fileSeparator = File.separator
    val defaultStorageFile = Environment.getExternalStorageDirectory()
    val othersSdCardFiles =
        getSdCardPaths(context, true).map { File(it) }.filter { !defaultStorageFile.hasTargetParent(it) }
    val defaultStorageLeaf = if (defaultStorageFile.canRead()) {
        listOf(
            FileLeaf.DirectoryFileLeaf(
                name = "Default Storage",
                path = defaultStorageFile.canonicalPath,
                childrenCount = defaultStorageFile.listFiles()?.size?.toLong() ?: 0L,
                lastModified = defaultStorageFile.lastModified()
            )
        )
    } else {
        emptyList()
    }
    val sdCardLeafs = othersSdCardFiles.map {
        it.toDirLeaf()
    }
    return FileTree(
        dirLeafs = defaultStorageLeaf + sdCardLeafs,
        fileLeafs = emptyList(),
        path = fileSeparator,
        parentTree = null
    )
}

fun FileTree.newLocalSubTree(
    dirLeaf: FileLeaf.DirectoryFileLeaf): FileTree {
    val file = File(dirLeaf.path)
    val (dirLeafs, fileLeafs) = file.childrenLeafs()
    return FileTree(
        dirLeafs = dirLeafs,
        fileLeafs = fileLeafs,
        path = file.canonicalPath,
        parentTree = this
    )
}