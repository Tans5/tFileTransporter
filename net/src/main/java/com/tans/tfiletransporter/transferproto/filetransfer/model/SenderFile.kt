package com.tans.tfiletransporter.transferproto.filetransfer.model

import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import java.io.File

data class SenderFile(
    val realFile: File,
    val exploreFile: FileExploreFile
)