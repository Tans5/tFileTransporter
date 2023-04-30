package com.tans.tfiletransporter

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.tans.tfiletransporter.core.Stateable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

object Settings : Stateable<Settings.SettingsData> {

    override val stateStore: Subject<SettingsData> by lazy {
        BehaviorSubject.createDefault(SettingsData()).toSerialized()
    }

    private val ioExecutor: Executor by lazy {
        Dispatchers.IO.asExecutor()
    }

    private val sp: AtomicReference<SharedPreferences?> by lazy {
        AtomicReference(null)
    }

    private val defaultDownloadDir by lazy {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "tFileTransfer").canonicalPath
    }

    private const val defaultConnectionSize = 8

    private const val defaultBufferSize = 1024L * 512L

    const val minConnectionSize = 1
    const val maxConnectionSize = defaultConnectionSize
    const val minBufferSize = 1024
    const val maxBufferSize = defaultBufferSize
    fun init(context: Context) {
        ioExecutor.execute {
            val sp = context.getSharedPreferences(SP_FILE_NAME, Context.MODE_PRIVATE)
            this.sp.set(sp)
            val s = updateState {
                SettingsData(
                    downloadDir = sp.getString(DOWNLOAD_DIR_KEY, defaultDownloadDir) ?: defaultDownloadDir,
                    shareMyDir = sp.getBoolean(SHARE_MY_DIR_KEY, true),
                    transferFileMaxConnection = sp.getInt(MAX_CONNECTION_KEY, defaultConnectionSize),
                    transFileBufferSize = sp.getLong(BUFFER_SIZE_KEY, defaultBufferSize)
                )
            }.blockingGet()
        }
    }

    fun getSettings(): Single<SettingsData> = stateStore.firstOrError()

    fun getDownloadDir(): Single<String> = stateStore.firstOrError().map { it.downloadDir }

    fun isShareMyDir(): Single<Boolean> = stateStore.firstOrError().map { it.shareMyDir }

    fun transferFileMaxConnection(): Single<Int> = stateStore.firstOrError()
        .map { fixTransferFileConnectionSize(it.transferFileMaxConnection) }

    fun transferFileBufferSize(): Single<Long> = stateStore
        .firstOrError()
        .map { fixTransferFileBufferSize(it.transFileBufferSize) }

    fun updateDownloadDir(dir: String) = updateState { s ->
        sp.get()?.edit()?.let {
            it.putString(DOWNLOAD_DIR_KEY, dir)
            it.apply()
        }
        s.copy(downloadDir = dir)
    }

    fun updateShareDir(shareDir: Boolean) = updateState { s ->
        sp.get()?.edit()?.let {
            it.putBoolean(SHARE_MY_DIR_KEY, shareDir)
            it.apply()
        }
        s.copy(shareMyDir = shareDir)
    }

    fun updateTransferFileMaxConnection(maxConnection: Int) = updateState { s ->
        sp.get()?.edit()?.let {
            it.putInt(MAX_CONNECTION_KEY, maxConnection)
            it.apply()
        }
        s.copy(transferFileMaxConnection = maxConnection)
    }

    fun updateTransferFileBufferSize(bufferSize: Long) = updateState { s ->
        sp.get()?.edit()?.let {
            it.putLong(BUFFER_SIZE_KEY, bufferSize)
            it.apply()
        }
        s.copy(transFileBufferSize = bufferSize)
    }

    fun fixTransferFileConnectionSize(needToFix: Int): Int {
        return if (needToFix in minConnectionSize .. maxConnectionSize) {
            needToFix
        } else {
            defaultConnectionSize
        }
    }

    fun fixTransferFileBufferSize(needToFix: Long): Long {
        return if (needToFix in minBufferSize .. maxBufferSize) {
            needToFix
        } else {
            defaultBufferSize
        }
    }

    data class SettingsData(
        val downloadDir: String = defaultDownloadDir,
        val shareMyDir: Boolean = true,
        val transferFileMaxConnection: Int = defaultConnectionSize,
        val transFileBufferSize: Long = defaultBufferSize
    )

    private const val SP_FILE_NAME = "settings"

    private const val DOWNLOAD_DIR_KEY = "download_dir"
    private const val SHARE_MY_DIR_KEY = "share_my_dir"
    private const val MAX_CONNECTION_KEY = "max_connection"
    private const val BUFFER_SIZE_KEY = "buffer_size"
}