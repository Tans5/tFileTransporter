package com.tans.tfiletransporter.net.netty.fileexplore

import com.tans.tfiletransporter.net.model.FileExploreContentModel
import com.tans.tfiletransporter.net.model.FileExploreHandshakeModel
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.rxkotlin.ofType
import io.reactivex.subjects.PublishSubject

class FileExploreConnection(
    private val closeConnection: (notifyRemote: Boolean) -> Unit,
    private val sendFileExploreContent: (fileExploreContent: FileExploreContentModel, waitReplay: Boolean) -> Unit
) {


    private val stateSubject = PublishSubject.create<FileExploreConnectionState>().toSerialized()

    private val remoteFileExploreContentSubject = PublishSubject.create<FileExploreContentModel>().toSerialized()

    fun observeConnected(): Single<FileExploreHandshakeModel> = stateSubject
        .ofType<FileExploreConnectionState.Connected>()
        .map { it.handshakeModel }
        .firstOrError()

    fun observeDisconnected(): Single<Unit> = stateSubject
        .ofType<FileExploreConnectionState.Disconnected>()
        .map { Unit }
        .firstOrError()

    fun observeRemoteFileExploreContent(): Observable<FileExploreContentModel> = remoteFileExploreContentSubject

    fun connectionActive(handshake: FileExploreHandshakeModel) {
        stateSubject.onNext(FileExploreConnectionState.Connected(handshake))
    }

    fun close(notifyRemote: Boolean = true) {
        closeConnection(notifyRemote)
        stateSubject.onNext(FileExploreConnectionState.Disconnected)
        stateSubject.onComplete()
        remoteFileExploreContentSubject.onComplete()
    }

    fun newRemoteFileExploreContent(fileExploreContent: FileExploreContentModel) {
        remoteFileExploreContentSubject.onNext(fileExploreContent)
    }

    fun sendFileExploreContentToRemote(fileExploreContent: FileExploreContentModel, waitReplay: Boolean = false) {
        sendFileExploreContent(fileExploreContent, waitReplay)
    }

    companion object {
        sealed class FileExploreConnectionState {
            data class Connected(val handshakeModel: FileExploreHandshakeModel) : FileExploreConnectionState()
            object Disconnected : FileExploreConnectionState()
        }
    }
}