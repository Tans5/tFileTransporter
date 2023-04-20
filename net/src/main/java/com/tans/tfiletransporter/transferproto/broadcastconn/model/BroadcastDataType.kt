package com.tans.tfiletransporter.transferproto.broadcastconn.model

enum class BroadcastDataType(val type: Int) {
    BroadcastMsg(0),
    TransferFileReq(1),
    TransferFileResp(2)
}