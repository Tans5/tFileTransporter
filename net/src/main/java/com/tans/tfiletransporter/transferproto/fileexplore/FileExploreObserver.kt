package com.tans.tfiletransporter.transferproto.fileexplore

import com.tans.tfiletransporter.transferproto.fileexplore.model.SendMsgReq

interface FileExploreObserver {

    fun onNewState(state: FileExploreState)

    fun onNewMsg(msg: SendMsgReq)
}