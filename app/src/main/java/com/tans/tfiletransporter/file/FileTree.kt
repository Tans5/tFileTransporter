package com.tans.tfiletransporter.file

data class FileTree(
    val leafs: List<FileLeaf>,
    val notNeedRefresh: Boolean,
    val path: String,
    val parentTree: FileTree?
)


fun newRootFileTree(path: String = ""): FileTree = FileTree(
    leafs = emptyList(),
    notNeedRefresh = false,
    path = path,
    parentTree = null)

fun FileTree.isRootFileTree(): Boolean = parentTree == null

fun DirectoryFileLeaf.newSubTree(): FileTree {
    val parent = this.belongTree
    return FileTree(
        leafs = emptyList(),
        notNeedRefresh = false,
        path = path,
        parentTree = parent
    )
}

sealed class YoungLeaf

data class DirectoryYoungLeaf(val name: String, val childrenCount: Long, val lastModified: Long) : YoungLeaf()
data class FileYoungLeaf(val name: String, val size: Long, val lastModified: Long) : YoungLeaf()

/**
 * @receiver First: FileName, Second: isDirectory
 */
fun List<YoungLeaf>.generateNewFileTree(parentDir: DirectoryFileLeaf): FileTree {
    val newTree = parentDir.newSubTree()
    return this.refreshFileTree(newTree)
}

/**
 * @receiver Leafs, First: FileName, Second: isDirectory
 */
fun List<YoungLeaf>.refreshFileTree(parentTree: FileTree, dirSeparator: String = "/"): FileTree {
    val leafs = this.map { youngLeaf ->
        when (youngLeaf) {
            is DirectoryYoungLeaf -> {
                DirectoryFileLeaf(
                    name = youngLeaf.name,
                    belongTree = parentTree,
                    path = "${parentTree.path}$dirSeparator${youngLeaf.name}",
                    childrenCount = youngLeaf.childrenCount,
                    lastModified = youngLeaf.lastModified
                )
            }
            is FileYoungLeaf -> {
                CommonFileLeaf(
                    name = youngLeaf.name,
                    belongTree = parentTree,
                    path = "${parentTree.path}$dirSeparator${youngLeaf.name}",
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
    return this.copy(leafs = leafs, notNeedRefresh = true)
}

fun FileTree.cleanFileTree(): FileTree = this.copy(leafs = emptyList(), notNeedRefresh = false)

fun String.generateTreeFromPath(dirSeparator: String = "/"): FileTree {
    val rootThree = newRootFileTree(path = dirSeparator)
    return this.split(dirSeparator)
        .fold(rootThree) { parentTree, name ->
            FileTree(
                leafs = emptyList(),
                notNeedRefresh = false,
                path = "${rootThree.path}$dirSeparator$name",
                parentTree = parentTree
            )
        }
}

