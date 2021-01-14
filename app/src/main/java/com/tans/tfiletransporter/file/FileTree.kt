package com.tans.tfiletransporter.file

data class FileTree(
    val leafs: List<FileLeaf>,
    val isLoadLeafs: Boolean,
    val path: String,
    val parentTree: FileTree?
)


fun newRootFileTree(path: String = "/"): FileTree = FileTree(
    leafs = emptyList(),
    isLoadLeafs = false,
    path = path,
    parentTree = null)

fun FileTree.isRootFileTree(): Boolean = parentTree == null

fun DirectoryFileLeaf.newSubTree(): FileTree {
    val parent = this.belongTree
    return FileTree(
        leafs = emptyList(),
        isLoadLeafs = false,
        path = path,
        parentTree = parent
    )
}

/**
 * @receiver First: FileName, Second: isDirectory
 */
fun List<Pair<String, Boolean>>.generateNewFileTree(parentDir: DirectoryFileLeaf): FileTree {
    val newTree = parentDir.newSubTree()
    return this.refreshFileTree(newTree)
}

/**
 * @receiver Leafs, First: FileName, Second: isDirectory
 */
fun List<Pair<String, Boolean>>.refreshFileTree(parentTree: FileTree, dirSeparator: String = "/"): FileTree {
    val leafs = this.map { (name, isDir) ->
        if (isDir) {
            DirectoryFileLeaf(
                name = name,
                belongTree = parentTree,
                path = "${parentTree.path}$dirSeparator$name"
            )
        } else {
            CommonFileLeaf(
                name = name,
                belongTree = parentTree,
                path = "${parentTree.path}$dirSeparator$name"
            )
        }
    }
    return parentTree.refreshFileTree(leafs)
}

fun FileTree.refreshFileTree(leafs: List<FileLeaf>): FileTree {
    if (leafs.any { it.belongTree != this }) error("Wrong leafs for $this")
    return this.copy(leafs = leafs, isLoadLeafs = true)
}

fun FileTree.cleanFileTree(): FileTree = this.copy(leafs = emptyList(), isLoadLeafs = false)

fun String.generateTreeFromPath(dirSeparator: String = "/"): FileTree {
    val rootThree = newRootFileTree(path = dirSeparator)
    return this.split(dirSeparator)
        .fold(rootThree) { parentTree, name ->
            FileTree(
                leafs = emptyList(),
                isLoadLeafs = false,
                path = "${rootThree.path}$dirSeparator$name",
                parentTree = parentTree
            )
        }
}

