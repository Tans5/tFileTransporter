package com.tans.tfiletransporter.transferproto.fileexplore.model

enum class FileExploreDataType(val type: Int) {
    HandshakeReq(0),
    HandshakeResp(1),
    ScanDirReq(2),
    ScanDirResp(3),
    SendFilesReq(4),
    SendFilesResp(5),
    DownloadFilesReq(6),
    DownloadFilesResp(7),
    SendMsgReq(8),
    SendMsgResp(9)
}