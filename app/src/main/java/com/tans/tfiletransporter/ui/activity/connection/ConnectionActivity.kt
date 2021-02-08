package com.tans.tfiletransporter.ui.activity.connection

import android.Manifest
import android.os.Build
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ConnectionActivityBinding
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import org.kodein.di.DI
import org.kodein.di.android.di
import org.kodein.di.android.retainedSubDI
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.singleton

class ConnectionActivity : BaseActivity<ConnectionActivityBinding, Unit>(
    layoutId = R.layout.connection_activity,
    defaultState = Unit
) {

    override val di: DI by retainedSubDI(di()) {
        bind<Subject<Unit>>() with singleton { BehaviorSubject.createDefault(defaultState).toSerialized() }
    }

    override val stateStore: Subject<Unit> by instance()

    override fun firstLaunchInitData() {
        launch {
            val grant = RxPermissions(this@ConnectionActivity).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    it.request(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    it.request(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.firstOrError().await()
            if (!grant) {
                finish()
            }
        }
    }

}