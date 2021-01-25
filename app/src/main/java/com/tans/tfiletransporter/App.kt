package com.tans.tfiletransporter

import android.app.Application
import com.jakewharton.threetenabp.AndroidThreeTen
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.android.androidCoreModule
import org.kodein.di.android.x.androidXModule
import org.kodein.di.bind
import org.kodein.di.singleton
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter

class OffsetDataTimeJsonAdapter : JsonAdapter<OffsetDateTime>() {

    override fun fromJson(reader: JsonReader): OffsetDateTime? {
        val dateString = reader.nextNull<String>()
        return if (dateString != null) {
            OffsetDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME)
        } else {
            null
        }
    }

    override fun toJson(writer: JsonWriter, value: OffsetDateTime?) {
        writer.value(if (value != null) DateTimeFormatter.ISO_DATE_TIME.format(value) else null)
    }

}

val moshi = Moshi.Builder()
        .add(OffsetDateTime::class.java, OffsetDataTimeJsonAdapter())
        .build()

class App : Application(), DIAware {

    override val di: DI by DI.lazy {
        import(androidCoreModule(this@App), allowOverride = true)
        import(androidXModule(this@App), allowOverride = true)


        bind<Moshi>() with singleton { moshi }
    }

    override fun onCreate() {
        super.onCreate()
        AndroidThreeTen.init(this)
    }

}