package com.tans.tfiletransporter.net.netty.fileexplore

import com.tans.tfiletransporter.net.model.FileExploreHandshakeModel
import io.reactivex.subjects.PublishSubject

class FileExploreConnection(
    private val closeConnection: (notifyRemote: Boolean) -> Unit
) {


    private val stateSubject = PublishSubject.create<FileExploreConnectionState>().toSerialized()

    fun connectionActive(handshake: FileExploreHandshakeModel) {
        stateSubject.onNext(FileExploreConnectionState.Connected(handshake))
    }

    fun close(notifyRemote: Boolean = true) {
        closeConnection(notifyRemote)
        stateSubject.onNext(FileExploreConnectionState.Disconnected)
        stateSubject.onComplete()
    }

    companion object {
        sealed class FileExploreConnectionState {
            data class Connected(val handshakeModel: FileExploreHandshakeModel) : FileExploreConnectionState()

            object Disconnected : FileExploreConnectionState()
        }
    }
}