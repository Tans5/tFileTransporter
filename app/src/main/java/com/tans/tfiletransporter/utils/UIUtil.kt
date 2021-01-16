package com.tans.tfiletransporter.utils

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue


fun Context.dp2px(dp: Int): Int {
    return (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics) + 0.5f).toInt()
}

fun Context.px2dp(px: Int): Int {
    return (px.toFloat() / (resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT).toFloat() + 0.5f).toInt()
}