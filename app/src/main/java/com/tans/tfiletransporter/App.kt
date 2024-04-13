package com.tans.tfiletransporter

import android.app.Application
import com.squareup.moshi.Moshi
import com.tans.tuiutils.systembar.AutoApplySystemBarAnnotation

class App : Application() {

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