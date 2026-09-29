package com.ivangames.textflow

import android.content.Context
import android.net.Uri

object BookStorage {

    private const val PREFS = "books_storage"
    private const val KEY_BOOKS = "books_list"

    // Формат хранения: "uri|title|format" через \n
    fun saveBooks(context: Context, books: List<Book>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val data = books.joinToString("\n") { "${it.uri}|${it.title}|${it.format}" }
        prefs.edit().putString(KEY_BOOKS, data).apply()
    }

    fun loadBooks(context: Context): MutableList<Book> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val data = prefs.getString(KEY_BOOKS, "") ?: ""
        val result = mutableListOf<Book>()
        if (data.isBlank()) return result

        for (line in data.split("\n")) {
            if (line.isBlank()) continue
            val parts = line.split("|")
            if (parts.size >= 3) {
                try {
                    val uri = Uri.parse(parts[0])
                    result.add(Book(
                        uri = uri,
                        title = parts[1],
                        format = parts[2]
                    ))
                } catch (e: Exception) {
                }
            }
        }
        return result
    }
}
