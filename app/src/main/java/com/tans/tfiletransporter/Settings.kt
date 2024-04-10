package com.tans.tfiletransporter

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tuiutils.state.CoroutineState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

object Settings : CoroutineState<Settings.SettingsData> by CoroutineState(SettingsData()) {

    private val ioExecutor: Executor by lazy {
        Dispatchers.IO.asExecutor()
    }

    private val sp: AtomicReference<SharedPreferences?> by lazy {
        AtomicReference(null)
    }

    val defaultDownloadDir: String by lazy {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "tFileTransfer").canonicalPath
    }

    fun init(context: Context) {
        ioExecutor.execute {

            File(defaultDownloadDir).apply {
                if (!this.exists()) {
                    this.mkdirs()
                }
            }

            val sp = context.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE)
            this.sp.set(sp)

            val storedDownloadDir = sp.getString(DOWNLOAD_DIR_KEY, defaultDownloadDir)?.let {
                if (isDirWriteable(it)) {
                    it
                } else {
                    sp.edit().let { et ->
                        et.putString(DOWNLOAD_DIR_KEY, defaultDownloadDir)
                        et.apply()
                    }
                    defaultDownloadDir
                }
            } ?: defaultDownloadDir

            val storedShareMyDir = sp.getBoolean(SHARE_MY_DIR_KEY, true)
            val storedTransferFileMaxConnection = sp.getInt(MAX_CONNECTION_KEY, DEFAULT_CONNECTION_SIZE)
            updateState { oldState ->
                oldState.copy(
                    downloadDir = storedDownloadDir,
                    shareMyDir = storedShareMyDir,
                    transferFileMaxConnection = storedTransferFileMaxConnection
                )
            }
        }
    }

    fun getDownloadDir(): String = stateFlow.value.downloadDir

    fun isShareMyDir(): Boolean = stateFlow.value.shareMyDir

    fun transferFileMaxConnection(): Int = stateFlow.value.transferFileMaxConnection

    fun updateDownloadDir(dir: String): Boolean  {
        return if (isDirWriteable(dir)) {
            updateState { s ->
                if (dir != s.downloadDir) {
                    sp.get()?.edit()?.let {
                        it.putString(DOWNLOAD_DIR_KEY, dir)
                        it.apply()
                    }
                    s.copy(downloadDir = dir)
                } else {
                    s
                }
            }
            true
        } else {
            AndroidLog.e(TAG, "$dir is not writeable dir.")
            false
        }
    }

    fun updateShareDir(shareDir: Boolean)  {
        updateState { s ->
            if (shareDir != s.shareMyDir) {
                sp.get()?.edit()?.let {
                    it.putBoolean(SHARE_MY_DIR_KEY, shareDir)
                    it.apply()
                }
                s.copy(shareMyDir = shareDir)
            } else {
                s
            }
        }
    }

    fun updateTransferFileMaxConnection(maxConnection: Int) {
        val fixedMaxConnection = max(MIN_CONNECTION_SIZE, min(MAX_CONNECTION_SIZE, maxConnection))
        updateState { s ->
            if (s.transferFileMaxConnection != fixedMaxConnection) {
                sp.get()?.edit()?.let {
                    it.putInt(MAX_CONNECTION_KEY, fixedMaxConnection)
                    it.apply()
                }
                s.copy(transferFileMaxConnection = fixedMaxConnection)
            } else {
                s
            }
        }
    }

    fun fixTransferFileConnectionSize(needToFix: Int): Int {
        return if (needToFix in MIN_CONNECTION_SIZE .. MAX_CONNECTION_SIZE) {
            needToFix
        } else {
            DEFAULT_CONNECTION_SIZE
        }
    }

    fun isDirWriteable(dir: String): Boolean {
        val f = File(dir)
        return f.isDirectory && f.canWrite()
    }

    data class SettingsData(
        val downloadDir: String = "",
        val shareMyDir: Boolean = true,
        val transferFileMaxConnection: Int = DEFAULT_CONNECTION_SIZE,
    )

    const val DEFAULT_CONNECTION_SIZE = 6
    const val MIN_CONNECTION_SIZE = 1
    const val MAX_CONNECTION_SIZE = 8

    private const val SP_FILE_NAME = "settings"

    private const val DOWNLOAD_DIR_KEY = "download_dir"
    private const val SHARE_MY_DIR_KEY = "share_my_dir"
    private const val MAX_CONNECTION_KEY = "max_connection"

    private const val TAG = "Settings"
}