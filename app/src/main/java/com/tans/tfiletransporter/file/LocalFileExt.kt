package com.tans.tfiletransporter.file

import java.io.File


fun File.toFileLeaf(rootDirString: String): FileLeaf.CommonFileLeaf {
    return FileLeaf.CommonFileLeaf(
        name = name,
        path = canonicalPath.removePrefix(rootDirString),
        size = length(),
        lastModified = lastModified()
    )
}

fun File.toDirLeaf(rootDirString: String): FileLeaf.DirectoryFileLeaf {
    return FileLeaf.DirectoryFileLeaf(
        name = name,
        path = canonicalPath.removePrefix(rootDirString),
        childrenCount = listFiles()?.size?.toLong() ?: 0L,
        lastModified = lastModified()
    )
}

fun File.childrenLeafs(rootDirString: String): Pair<List<FileLeaf.DirectoryFileLeaf>, List<FileLeaf.CommonFileLeaf>> {
    val children = listFiles() ?: emptyArray<File>()
    val resultFiles = mutableListOf<FileLeaf.CommonFileLeaf>()
    val resultDirs = mutableListOf<FileLeaf.DirectoryFileLeaf>()
    for (c in children) {
        try {
            if (c.canRead()) {
                if (c.isFile) {
                    resultFiles.add(c.toFileLeaf(rootDirString))
                } else {
                    resultDirs.add(c.toDirLeaf(rootDirString))
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
    return resultDirs to resultFiles
}

fun createLocalRootTree(rootFile: File): FileTree {
    val fileSeparator = File.separator
    return if (rootFile.canRead()) {
        val (dirLeafs, fileLeafs) = rootFile.childrenLeafs(rootFile.canonicalPath)
        FileTree(
            dirLeafs = dirLeafs,
            fileLeafs = fileLeafs,
            path = fileSeparator,
            parentTree = null
        )
    } else {
        FileTree(
            dirLeafs = emptyList(),
            fileLeafs = emptyList(),
            path = fileSeparator,
            parentTree = null
        )
    }
}

fun FileTree.newLocalSubTree(
    dirLeaf: FileLeaf.DirectoryFileLeaf,
    rootFile: File): FileTree {
    val file = File(rootFile, dirLeaf.path)
    val (dirLeafs, fileLeafs) = file.childrenLeafs(rootFile.canonicalPath)
    return FileTree(
        dirLeafs = dirLeafs,
        fileLeafs = fileLeafs,
        path = file.canonicalPath.removePrefix(rootFile.canonicalPath),
        parentTree = this
    )
}