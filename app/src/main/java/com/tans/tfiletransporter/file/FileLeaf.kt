package com.tans.tfiletransporter.file

sealed class FileLeaf(
    val name: String,
    val belongTree: FileTree,
    val path: String,
    val lastModified: Long
)

class CommonFileLeaf(name: String, belongTree: FileTree, path: String, val size: Long, lastModified: Long) : FileLeaf(name, belongTree, path, lastModified)

class DirectoryFileLeaf(name: String, belongTree: FileTree, path: String, val childrenCount: Long, lastModified: Long) : FileLeaf(name, belongTree, path, lastModified)