package com.tans.tfiletransporter

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen
import com.squareup.moshi.Moshi
import com.tans.tfiletransporter.netty.extensions.DefaultConverterFactory
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.androidCoreModule
import org.kodein.di.android.x.androidXModule
import org.kodein.di.bind
import org.kodein.di.singleton

class App : Application(), DIAware {

    override val di: DI by DI.lazy {
        import(androidCoreModule(this@App), allowOverride = true)
        import(androidXModule(this@App), allowOverride = true)


        bind<Moshi>() with singleton { DefaultConverterFactory.defaultMoshi }
    }

    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this)
    }

}