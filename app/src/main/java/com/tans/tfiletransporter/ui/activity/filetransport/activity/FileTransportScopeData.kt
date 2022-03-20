package com.tans.tfiletransporter.ui.activity.filetransport.activity

import com.tans.tfiletransporter.net.model.FileExploreHandshakeModel
import com.tans.tfiletransporter.net.model.ResponseFolderModel
import com.tans.tfiletransporter.net.netty.fileexplore.FileExploreConnection
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.net.InetAddress

class FileTransportScopeData(
    val handshakeModel: FileExploreHandshakeModel,
    val fileExploreConnection: FileExploreConnection,
    val localAddress: InetAddress,
    val remoteAddress: InetAddress
) {

    // Floating Action Button in Activity clicks event, Activity To Event
    val floatBtnEvent: Subject<Unit> = PublishSubject.create<Unit>().toSerialized()

    val messagesEvent: Subject<List<Message>> = BehaviorSubject.createDefault<List<Message>>(emptyList()).toSerialized()
    val remoteFolderModelEvent: Subject<ResponseFolderModel> = PublishSubject.create<ResponseFolderModel>().toSerialized()

    companion object {
        data class Message(
            val isRemote: Boolean,
            val timeMilli: Long,
            val message: String
        )
    }

}