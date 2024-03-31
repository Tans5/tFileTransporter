package com.tans.tfiletransporter.transferproto.fileexplore

data class Handshake(
    val remoteFileSeparator: String
)

sealed class FileExploreState {
    data object NoConnection : FileExploreState()
    data object Requesting : FileExploreState()
    data object Connected : FileExploreState()
    data class Active(val handshake: Handshake) : FileExploreState()
}