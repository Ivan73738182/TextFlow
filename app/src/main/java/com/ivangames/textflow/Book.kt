package com.ivangames.textflow

import java.io.File

data class Book(
    val file: File,
    val title: String,
    val format: String,
    val sizeKb: Long
)
