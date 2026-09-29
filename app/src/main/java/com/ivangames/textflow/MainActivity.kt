package com.ivangames.textflow

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var infoText: TextView
    private lateinit var booksList: RecyclerView
    private val books = mutableListOf<Book>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            infoText = findViewById(R.id.infoText)
            booksList = findViewById(R.id.booksList)
            booksList.layoutManager = GridLayoutManager(this, 2)

            books.addAll(BookStorage.loadBooks(this))
            updateList()
        } catch (e: Exception) {
            showError(e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            books.clear()
            books.addAll(BookStorage.loadBooks(this))
            updateList()
        } catch (e: Exception) {
            android.util.Log.e("TextFlow", "onResume: ${e.message}")
        }
    }

    private fun showError(e: Exception) {
        val tv = TextView(this)
        tv.text = "ОШИБКА:\n\n${e.message}"
        tv.setTextColor(0xFFFF0000.toInt())
        tv.setBackgroundColor(0xFF000000.toInt())
        tv.setPadding(24, 24, 24, 24)
        tv.textSize = 12f
        setContentView(tv)
    }

    private fun updateList() {
        if (books.isEmpty()) {
            infoText.text = "Нет добавленных книг.\nОткрой .fb2 или .txt через файловый менеджер\nи выбери TextFlow."
        } else {
            infoText.text = "Книг: ${books.size}"
        }

        booksList.adapter = BookAdapter(books,
            onClick = { book ->
                try {
                    val intent = Intent(this, ReaderActivity::class.java)
                    intent.data = book.uri
                    intent.putExtra("book_title", book.title)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка открытия: ${e.message}", Toast.LENGTH_LONG).show()
                }
            },
            onLongClick = { book ->
                books.remove(book)
                BookStorage.saveBooks(this, books)
                updateList()
                Toast.makeText(this, "Удалено: ${book.title}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
