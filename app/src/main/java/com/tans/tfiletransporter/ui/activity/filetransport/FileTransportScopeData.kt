package com.tans.tfiletransporter.ui.activity.filetransport

import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject

class FileTransportScopeData {

    // Floating Action Button in Activity clicks event, Activity To Event
    val floatBtnEvent: Subject<Unit> = PublishSubject.create<Unit>().toSerialized()

}