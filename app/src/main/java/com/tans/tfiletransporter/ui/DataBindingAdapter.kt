package com.tans.tfiletransporter.ui

import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.tans.tfiletransporter.R
import org.threeten.bp.*
import org.threeten.bp.format.DateTimeFormatter

object DataBindingAdapter {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss")

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

    const val KB = 1024
    const val MB = 1024 * 1024
    const val GB = 1024 * 1024 * 1024
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
}