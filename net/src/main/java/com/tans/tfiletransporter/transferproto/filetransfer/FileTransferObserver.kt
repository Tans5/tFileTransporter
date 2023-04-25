package com.tans.tfiletransporter.transferproto.filetransfer

import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile

interface FileTransferObserver {

    fun onNewState(s: FileTransferState)

    fun onStartFile(file: FileExploreFile)

    fun onProgressUpdate(file: FileExploreFile, progress: Long)

    fun onEndFile(file: FileExploreFile)
}