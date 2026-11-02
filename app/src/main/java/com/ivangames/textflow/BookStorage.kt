package com.ivangames.textflow

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class BookStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("TextFlowPrefs", Context.MODE_PRIVATE)
    private val keyBooks = "books_list"

    fun saveBooks(books: List<Book>) {
        val jsonArray = JSONArray()
        for (book in books) {
            val jsonObject = JSONObject()
            jsonObject.put("title", book.title)
            jsonObject.put("path", book.path)
            jsonArray.put(jsonObject)
        }
        prefs.edit().putString(keyBooks, jsonArray.toString()).apply()
    }

    fun loadBooks(): MutableList<Book> {
        val books = mutableListOf<Book>()
        val jsonString = prefs.getString(keyBooks, null)
        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val title = jsonObject.getString("title")
                    val path = jsonObject.getString("path")
                    books.add(Book(title, path))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return books
    }
}
