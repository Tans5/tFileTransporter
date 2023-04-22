package com.tans.tfiletransporter.transferproto.fileexplore

data class Handshake(
    val remoteFileSeparator: String
)

sealed class FileExploreState {
    object NoConnection : FileExploreState()
    object Requesting : FileExploreState()
    object Connected : FileExploreState()
    data class Active(val handshake: Handshake) : FileExploreState()
}