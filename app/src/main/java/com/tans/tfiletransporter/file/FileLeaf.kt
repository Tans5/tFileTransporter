package com.tans.tfiletransporter.file

sealed class FileLeaf(
    val name: String,
    val path: String,
    val lastModified: Long
)

class CommonFileLeaf(name: String, path: String, val size: Long, lastModified: Long) : FileLeaf(name, path, lastModified)

class DirectoryFileLeaf(name: String, path: String, val childrenCount: Long, lastModified: Long) : FileLeaf(name, path, lastModified)