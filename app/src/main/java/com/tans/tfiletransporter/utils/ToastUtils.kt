package com.tans.tfiletransporter.utils

import android.content.Context
import android.widget.Toast

fun Context.showToastShort(resString: Int) {
    Toast.makeText(this, resString, Toast.LENGTH_SHORT).show()
}