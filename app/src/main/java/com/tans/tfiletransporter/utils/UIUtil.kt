package com.tans.tfiletransporter.utils

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


fun Context.dp2px(dp: Int): Int {
    return (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics) + 0.5f).toInt()
}

fun Context.px2dp(px: Int): Int {
    return (px.toFloat() / (resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT).toFloat() + 0.5f).toInt()
}

fun RecyclerView.lastVisibleItemPosition(): Int {
    return (layoutManager as? LinearLayoutManager)?.findLastCompletelyVisibleItemPosition() ?: 0
}

fun RecyclerView.firstVisibleItemPosition(): Int {
    return (layoutManager as? LinearLayoutManager)?.findFirstCompletelyVisibleItemPosition() ?: 0
}