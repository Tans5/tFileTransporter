package com.tans.tfiletransporter.transferproto.filetransfer

import com.tans.tfiletransporter.transferproto.filetransfer.model.ErrorReq

sealed class FileTransferState {
    object NotExecute : FileTransferState()
    object Started : FileTransferState()
    object Canceled: FileTransferState()
    object Finished : FileTransferState()
    data class Error(val msg: String) : FileTransferState()
    data class RemoteError(val remoteError: ErrorReq) : FileTransferState()
}