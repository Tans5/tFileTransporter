package com.tans.tfiletransporter

import android.app.Application
import android.content.Context
import com.squareup.moshi.Moshi
import com.tans.tapm.autoinit.tApmAutoInit
import com.tans.tapm.monitors.CpuPowerCostMonitor
import com.tans.tapm.monitors.CpuUsageMonitor
import com.tans.tapm.monitors.ForegroundScreenPowerCostMonitor
import com.tans.tapm.monitors.MainThreadLagMonitor
import com.tans.tapm.monitors.MemoryUsageMonitor
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tuiutils.systembar.AutoApplySystemBarAnnotation

class App : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        tApmAutoInit.addBuilderInterceptor { builder ->
            if (BuildConfig.DEBUG) {
                builder
                    // CpuUsage
                    .addMonitor(CpuUsageMonitor().apply { setMonitorInterval(1000L * 10) })
                    // CpuPowerCost
                    .addMonitor(CpuPowerCostMonitor())
                    // ForegroundScreenPowerCost
                    .addMonitor(ForegroundScreenPowerCostMonitor())
                    // MainThreadLag
                    .addMonitor(MainThreadLagMonitor())
                    // MemoryUsage
                    .addMonitor(MemoryUsageMonitor())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AndroidLog.init(this)
        AutoApplySystemBarAnnotation.init(this)
        Settings.init(this)
    }

    companion object {

        val defaultMoshi: Moshi by lazy {
            Moshi.Builder().build()
        }
    }

}