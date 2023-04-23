package com.tans.tfiletransporter.file

import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreDir
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirResp


fun List<FileExploreDir>.toDirLeafs(): List<FileLeaf.DirectoryFileLeaf> {
    return this.map {
        FileLeaf.DirectoryFileLeaf(
            name = it.name,
            path = it.path,
            childrenCount = it.childrenCount.toLong(),
            lastModified = it.lastModify
        )
    }
}

fun List<FileExploreFile>.toFileLeafs(): List<FileLeaf.CommonFileLeaf> {
    return this.map {
        FileLeaf.CommonFileLeaf(
            name = it.name,
            path = it.path,
            size = it.size,
            lastModified = it.lastModify
        )
    }
}

fun createRemoteRootTree(s: ScanDirResp): FileTree {
    return FileTree(
        dirLeafs = s.childrenDirs.toDirLeafs(),
        fileLeafs = s.childrenFiles.toFileLeafs(),
        path = s.path,
        parentTree = null
    )
}

fun FileTree.newRemoteSubTree(s: ScanDirResp): FileTree {
    return FileTree(
        dirLeafs = s.childrenDirs.toDirLeafs(),
        fileLeafs = s.childrenFiles.toFileLeafs(),
        path = s.path,
        parentTree = this
    )
}