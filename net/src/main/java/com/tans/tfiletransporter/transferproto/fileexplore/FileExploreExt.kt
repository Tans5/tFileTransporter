package com.tans.tfiletransporter.transferproto.fileexplore

import com.tans.tfiletransporter.resumeExceptionIfActive
import com.tans.tfiletransporter.resumeIfActive
import com.tans.tfiletransporter.transferproto.SimpleCallback
import com.tans.tfiletransporter.transferproto.fileexplore.model.DownloadFilesResp
import com.tans.tfiletransporter.transferproto.fileexplore.model.FileExploreFile
import com.tans.tfiletransporter.transferproto.fileexplore.model.ScanDirResp
import com.tans.tfiletransporter.transferproto.fileexplore.model.SendFilesResp
import com.tans.tfiletransporter.transferproto.fileexplore.model.SendMsgReq
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress

suspend fun FileExplore.bindSuspend(address: InetAddress) = suspendCancellableCoroutine { cont ->
    bind(address, object : SimpleCallback<Unit> {
        override fun onSuccess(data: Unit) {
            cont.resumeIfActive(data)
        }
        override fun onError(errorMsg: String) {
            cont.resumeExceptionIfActive(Throwable(errorMsg))
        }
    })
}

suspend fun FileExplore.connectSuspend(address: InetAddress) = suspendCancellableCoroutine { cont ->
    connect(address, object : SimpleCallback<Unit> {
        override fun onSuccess(data: Unit) {
            cont.resumeIfActive(Unit)
        }
        override fun onError(errorMsg: String) {
            cont.resumeExceptionIfActive(Throwable(errorMsg))
        }
    })
}

suspend fun FileExplore.waitHandshake(): Handshake = suspendCancellableCoroutine { cont ->
    addObserver(object : FileExploreObserver {
        init {
            cont.invokeOnCancellation {
                removeObserver(this)
            }
        }
        override fun onNewState(state: FileExploreState) {
            if (state is FileExploreState.Active) {
                cont.resumeIfActive(state.handshake)
                removeObserver(this)
            }
            if (state is FileExploreState.NoConnection) {
                cont.resumeExceptionIfActive(Throwable("Connection closed"))
                removeObserver(this)
            }
        }

        override fun onNewMsg(msg: SendMsgReq) {}
    })
}

suspend fun FileExplore.waitClose() = suspendCancellableCoroutine<Unit> { cont ->
    addObserver(object : FileExploreObserver {
        init {
            cont.invokeOnCancellation {
                removeObserver(this)
            }
        }
        override fun onNewState(state: FileExploreState) {
            if (state is FileExploreState.NoConnection) {
                cont.resumeIfActive(Unit)
                removeObserver(this)
            }
        }
        override fun onNewMsg(msg: SendMsgReq) {}
    })
}

suspend fun FileExplore.handshakeSuspend() = suspendCancellableCoroutine<Handshake> { cont ->
    requestHandshake(object : SimpleCallback<Handshake> {
        override fun onSuccess(data: Handshake) {
            cont.resumeIfActive(data)
        }
        override fun onError(errorMsg: String) {
            cont.resumeExceptionIfActive(Throwable(errorMsg))
        }
    })
}

suspend fun FileExplore.requestScanDirSuspend(dirPath: String) = suspendCancellableCoroutine<ScanDirResp> { cont ->
    requestScanDir(dirPath, object : SimpleCallback<ScanDirResp> {
        override fun onSuccess(data: ScanDirResp) {
            cont.resumeIfActive(data)
        }
        override fun onError(errorMsg: String) {
            cont.resumeExceptionIfActive(Throwable(errorMsg))
        }
    })
}

suspend fun FileExplore.requestSendFilesSuspend(sendFiles: List<FileExploreFile>, maxConnection: Int = 8) = suspendCancellableCoroutine { cont ->
    requestSendFiles(sendFiles, maxConnection, object : SimpleCallback<SendFilesResp> {
        override fun onSuccess(data: SendFilesResp) {
            cont.resumeIfActive(data)
        }

        override fun onError(errorMsg: String) {
            cont.resumeExceptionIfActive(Throwable(errorMsg))
        }
    })
}

suspend fun FileExplore.requestDownloadFilesSuspend(downloadFiles: List<FileExploreFile>, bufferSize: Int = 1024 * 512) = suspendCancellableCoroutine { cont ->
    requestDownloadFiles(downloadFiles, bufferSize, object : SimpleCallback<DownloadFilesResp> {
        override fun onSuccess(data: DownloadFilesResp) {
            cont.resumeIfActive(data)
        }

        override fun onError(errorMsg: String) {
            cont.resumeExceptionIfActive(Throwable(errorMsg))
        }
    })
}

suspend fun FileExplore.requestMsgSuspend(msg: String) = suspendCancellableCoroutine { cont ->
    requestMsg(msg, object : SimpleCallback<Unit> {
        override fun onSuccess(data: Unit) {
            cont.resumeIfActive(Unit)
        }

        override fun onError(errorMsg: String) {
            cont.resumeExceptionIfActive(Throwable(errorMsg))
        }
    })
}