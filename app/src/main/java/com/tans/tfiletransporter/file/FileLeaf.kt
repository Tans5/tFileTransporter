package com.tans.tfiletransporter.file

sealed class FileLeaf(
    val name: String,
    val belongTree: FileTree,
    val path: String
)

class CommonFileLeaf(name: String, belongTree: FileTree, path: String) : FileLeaf(name, belongTree, path)

class DirectoryFileLeaf(name: String, belongTree: FileTree, path: String) : FileLeaf(name, belongTree, path)