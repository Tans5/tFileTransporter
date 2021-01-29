package com.tans.tfiletransporter.ui.activity.filetransport

import com.tans.tfiletransporter.net.filetransporter.FileTransporter
import com.tans.tfiletransporter.net.model.ResponseFolderModel
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject

class FileTransportScopeData(
    val remoteDirSeparator: String,
    val fileTransporter: FileTransporter
) {

    // Floating Action Button in Activity clicks event, Activity To Event
    val floatBtnEvent: Subject<Unit> = PublishSubject.create<Unit>().toSerialized()

    val remoteMessageEvent: Subject<String> = PublishSubject.create<String>().toSerialized()
    val remoteFolderModelEvent: Subject<ResponseFolderModel> = PublishSubject.create<ResponseFolderModel>().toSerialized()
}