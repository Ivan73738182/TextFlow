package com.ivangames.textflow

import android.net.Uri

data class Book(
    val uri: Uri,
    val title: String,
    val format: String,
    val coverPath: String? = null  // путь к сохранённой обложке (в кэше)
)
