package com.tans.tfiletransporter.file

data class FileTree(
    val leafs: List<FileLeaf>,
    val dirLeafs: List<DirectoryFileLeaf>,
    val fileLeafs: List<CommonFileLeaf>,
    val notNeedRefresh: Boolean,
    val path: String,
    val parentTree: FileTree?
)


fun newRootFileTree(path: String = "/"): FileTree = FileTree(
    leafs = emptyList(),
    dirLeafs = emptyList(),
    fileLeafs = emptyList(),
    notNeedRefresh = false,
    path = path,
    parentTree = null)

fun FileTree.isRootFileTree(): Boolean = parentTree == null

fun DirectoryFileLeaf.newSubTree(): FileTree {
    val parent = this.belongTree
    return FileTree(
        leafs = emptyList(),
        dirLeafs = emptyList(),
        fileLeafs = emptyList(),
        notNeedRefresh = false,
        path = path,
        parentTree = parent
    )
}

sealed class YoungLeaf

data class DirectoryYoungLeaf(val name: String, val childrenCount: Long, val lastModified: Long) : YoungLeaf()
data class FileYoungLeaf(val name: String, val size: Long, val lastModified: Long) : YoungLeaf()

fun List<YoungLeaf>.generateNewFileTree(parentDir: DirectoryFileLeaf, dirSeparator: String = FileConstants.FILE_SEPARATOR): FileTree {
    val newTree = parentDir.newSubTree()
    return this.refreshFileTree(newTree, dirSeparator)
}

fun List<YoungLeaf>.refreshFileTree(parentTree: FileTree, dirSeparator: String = FileConstants.FILE_SEPARATOR): FileTree {
    val leafs = this.map { youngLeaf ->
        when (youngLeaf) {
            is DirectoryYoungLeaf -> {
                DirectoryFileLeaf(
                    name = youngLeaf.name,
                    belongTree = parentTree,
                    path = "${parentTree.path}${if (parentTree.path.endsWith(dirSeparator)) "" else dirSeparator}${youngLeaf.name}",
                    childrenCount = youngLeaf.childrenCount,
                    lastModified = youngLeaf.lastModified
                )
            }
            is FileYoungLeaf -> {
                CommonFileLeaf(
                    name = youngLeaf.name,
                    belongTree = parentTree,
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
    if (leafs.any { it.belongTree != this }) error("Wrong leafs for $this")
    val sortedLeafs = leafs.sortedByDescending { it.lastModified }
    return this.copy(leafs = sortedLeafs, dirLeafs = sortedLeafs.filterIsInstance<DirectoryFileLeaf>(), fileLeafs = sortedLeafs.filterIsInstance<CommonFileLeaf>(),notNeedRefresh = true)
}

fun FileTree.cleanFileTree(): FileTree = this.copy(leafs = emptyList(), dirLeafs = emptyList(), fileLeafs = emptyList(), notNeedRefresh = false)

fun String.generateTreeFromPath(dirSeparator: String = FileConstants.FILE_SEPARATOR, realRootDir: String = ""): FileTree {
    val rootThree = newRootFileTree(dirSeparator)
    val fixedPath = this.removePrefix(realRootDir).removePrefix(dirSeparator).removeSuffix(dirSeparator)
    return fixedPath.split(dirSeparator)
        .fold(rootThree) { parentTree, name ->
            FileTree(
                leafs = emptyList(),
                dirLeafs = emptyList(),
                fileLeafs = emptyList(),
                notNeedRefresh = false,
                path = "${parentTree.path}${if (parentTree.path.endsWith(dirSeparator)) "" else dirSeparator}$name",
                parentTree = parentTree
            )
        }
}

