package com.tans.tfiletransporter.net.netty.fileexplore

import com.tans.tfiletransporter.net.model.FileExploreContentModel
import com.tans.tfiletransporter.net.model.FileExploreHandshakeModel
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.atomic.AtomicBoolean

class FileExploreConnection(
    private val closeConnection: (notifyRemote: Boolean) -> Unit,
    private val sendFileExploreContent: (fileExploreContent: FileExploreContentModel, waitReplay: Boolean) -> Unit,
    private val isConnectionActiveCallback: () -> Boolean
) {


    private val stateSubject = PublishSubject.create<FileExploreConnectionState>().toSerialized()

    private val remoteFileExploreContentSubject = PublishSubject.create<FileExploreContentModel>().toSerialized()

    private val hasInvokeClose = AtomicBoolean(false)

    fun observeConnected(): Single<FileExploreHandshakeModel> = stateSubject
        .ofType<FileExploreConnectionState.Connected>()
        .map { it.handshakeModel }
        .firstOrError()

    fun observeRemoteFileExploreContent(): Observable<FileExploreContentModel> = remoteFileExploreContentSubject

    fun connectionActive(handshake: FileExploreHandshakeModel) {
        stateSubject.onNext(FileExploreConnectionState.Connected(handshake))
        hasInvokeClose.compareAndSet(true, false)
    }

    fun close(notifyRemote: Boolean = true) {
        if (hasInvokeClose.compareAndSet(false, true)) {
            closeConnection(notifyRemote)
            stateSubject.onNext(FileExploreConnectionState.Disconnected)
            stateSubject.onComplete()
            remoteFileExploreContentSubject.onComplete()
        }
    }

    fun newRemoteFileExploreContent(fileExploreContent: FileExploreContentModel) {
        remoteFileExploreContentSubject.onNext(fileExploreContent)
    }

    fun sendFileExploreContentToRemote(fileExploreContent: FileExploreContentModel, waitReplay: Boolean = false) {
        sendFileExploreContent(fileExploreContent, waitReplay)
    }

    fun isConnectionActive(): Boolean = isConnectionActiveCallback()

    companion object {
        sealed class FileExploreConnectionState {
            data class Connected(val handshakeModel: FileExploreHandshakeModel) : FileExploreConnectionState()
            object Disconnected : FileExploreConnectionState()
        }
    }
}