package com.tans.tfiletransporter.file

import androidx.annotation.Keep

@Keep
data class FileTree(
    val leafs: List<FileLeaf>,
    val dirLeafs: List<DirectoryFileLeaf>,
    val fileLeafs: List<CommonFileLeaf>,
    val notNeedRefresh: Boolean,
    val path: String,
    val parentTree: FileTree?
)


fun newRootFileTree(path: String = FileConstants.FILE_SEPARATOR): FileTree = FileTree(
    leafs = emptyList(),
    dirLeafs = emptyList(),
    fileLeafs = emptyList(),
    notNeedRefresh = false,
    path = path,
    parentTree = null)

fun FileTree.isRootFileTree(): Boolean = parentTree == null

fun DirectoryFileLeaf.newSubTree(parentTree: FileTree): FileTree {
    return FileTree(
        leafs = emptyList(),
        dirLeafs = emptyList(),
        fileLeafs = emptyList(),
        notNeedRefresh = false,
        path = path,
        parentTree = parentTree
    )
}

sealed class YoungLeaf

@Keep
data class DirectoryYoungLeaf(val name: String, val childrenCount: Long, val lastModified: Long) : YoungLeaf()

@Keep
data class FileYoungLeaf(val name: String, val size: Long, val lastModified: Long) : YoungLeaf()

fun List<YoungLeaf>.refreshFileTree(parentTree: FileTree, dirSeparator: String = FileConstants.FILE_SEPARATOR): FileTree {
    val leafs = this.map { youngLeaf ->
        when (youngLeaf) {
            is DirectoryYoungLeaf -> {
                DirectoryFileLeaf(
                    name = youngLeaf.name,
                    path = "${parentTree.path}${if (parentTree.path.endsWith(dirSeparator)) "" else dirSeparator}${youngLeaf.name}",
                    childrenCount = youngLeaf.childrenCount,
                    lastModified = youngLeaf.lastModified
                )
            }
            is FileYoungLeaf -> {
                CommonFileLeaf(
                    name = youngLeaf.name,
                    path = "${parentTree.path}${if (parentTree.path.endsWith(dirSeparator)) "" else dirSeparator}${youngLeaf.name}",
                    size = youngLeaf.size,
                    lastModified = youngLeaf.lastModified
                )
            }
        }
    }
    return parentTree.refreshFileTree(leafs)
}

fun FileTree.refreshFileTree(leafs: List<FileLeaf>): FileTree {
    return this.copy(leafs = leafs, dirLeafs = leafs.filterIsInstance<DirectoryFileLeaf>(), fileLeafs = leafs.filterIsInstance<CommonFileLeaf>(), notNeedRefresh = true)
}

