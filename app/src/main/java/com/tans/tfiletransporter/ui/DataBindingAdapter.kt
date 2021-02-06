package com.tans.tfiletransporter.ui

import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.StreamLocalUriFetcher
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.file.FileConstants.GB
import com.tans.tfiletransporter.file.FileConstants.KB
import com.tans.tfiletransporter.file.FileConstants.MB
import org.threeten.bp.*
import org.threeten.bp.format.DateTimeFormatter
import java.lang.Exception

object DataBindingAdapter {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    @JvmStatic
    @BindingAdapter("app:dateText")
    fun dateText(view: TextView, time: Long) {
        val modifiedOffsetDateTime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault())
        val modifiedLocalDate = modifiedOffsetDateTime.toLocalDate()
        val nowLocalDate = OffsetDateTime.now(ZoneId.systemDefault()).toLocalDate()
        val p = Period.between(modifiedLocalDate, nowLocalDate)
        if (p.days < 1) {
            view.text = modifiedOffsetDateTime.format(timeFormatter)
        } else {
            view.text = modifiedOffsetDateTime.format(dateFormatter)
        }
    }


    @JvmStatic
    @BindingAdapter("app:fileSizeText")
    fun fileSizeText(view: TextView, size: Long) {
        val context = view.context
        view.text = when (size) {
            in 0 until KB -> context.getString(R.string.file_size_B, size)
            in KB until MB -> context.getString(R.string.file_size_KB, size.toDouble() / KB)
            in MB until GB -> context.getString(R.string.file_size_MB, size.toDouble() / MB)
            in GB until Long.MAX_VALUE -> context.getString(R.string.file_size_GB, size.toDouble() / GB)
            else -> ""
        }
    }

    @JvmStatic
    @BindingAdapter("app:imageUrl")
    fun imageUrl(image: ImageView, url: String?) {
        if (!url.isNullOrBlank()) {
            println("Start Load: $url")
            val uri = Uri.parse(url)
//            val inputStream = try {
//                image.context.contentResolver.openInputStream(uri)
//            } catch (e: Exception) {
//                println("Can't open")
//                null
//            }
//            println(inputStream?.available() ?: 0)
//            inputStream?.close()
//            if (uri.scheme == "content") {
//                Glide.with(image)
//                    .load()
//                    .into(image)
//            } else {
//                Glide.with(image)
//                    .load(url)
//                    .into(image)
//            }
        }
    }
}