package com.tans.tfiletransporter

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.androidCoreModule

class App : Application(), DIAware {

    override val di: DI by DI.lazy {
        import(androidCoreModule(this@App), allowOverride = true)
    }

    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this)
    }


}