package com.tans.tfiletransporter.transferproto.filetransfer.model

enum class FileTransferDataType(val type: Int) {
    DownloadReq(0),
    DownloadResp(1),
    SendReq(2),
    SendResp(3),
    FinishedReq(4),
    FinishedResp(6),
    ErrorReq(7),
    ErrorResp(8)
}