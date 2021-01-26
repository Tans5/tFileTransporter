package com.tans.tfiletransporter.ui.activity.filetransport

import com.tans.tfiletransporter.net.filetransporter.FileTransporterWriterHandle
import com.tans.tfiletransporter.net.model.ResponseFolderModel
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.channels.Channel

class FileTransportScopeData(
    val remoteDirSeparator: String,
    val writerHandleChannel: Channel<FileTransporterWriterHandle>
) {

    // Floating Action Button in Activity clicks event, Activity To Event
    val floatBtnEvent: Subject<Unit> = PublishSubject.create<Unit>().toSerialized()

    val remoteMessageEvent: Subject<String> = PublishSubject.create<String>().toSerialized()
    val remoteFolderModelEvent: Subject<ResponseFolderModel> = PublishSubject.create<ResponseFolderModel>().toSerialized()
}