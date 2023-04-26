package com.tans.tfiletransporter.transferproto.filetransfer


sealed class FileTransferState {
    object NotExecute : FileTransferState()
    object Started : FileTransferState()
    object Canceled: FileTransferState()
    object Finished : FileTransferState()
    data class Error(val msg: String) : FileTransferState()
    data class RemoteError(val msg: String) : FileTransferState()
}