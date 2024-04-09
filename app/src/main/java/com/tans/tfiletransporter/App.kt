package com.tans.tfiletransporter

import android.app.Application
import com.squareup.moshi.Moshi
import com.tans.tuiutils.systembar.AutoApplySystemBarAnnotation
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

        bind<Moshi>() with singleton { defaultMoshi }
    }

    override fun onCreate() {
        super.onCreate()
        AutoApplySystemBarAnnotation.init(this)
        Settings.init(this)
    }

    companion object {

        val defaultMoshi: Moshi by lazy {
            Moshi.Builder().build()
        }
    }

}