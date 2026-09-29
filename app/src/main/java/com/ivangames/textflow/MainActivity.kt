package com.ivangames.textflow

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var infoText: TextView
    private lateinit var booksList: RecyclerView
    private val books = mutableListOf<Book>()

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                addBookFromUri(uri)
            } catch (e: Exception) {
                Toast.makeText(this, "Ошибка добавления: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_main)

            infoText = findViewById(R.id.infoText)
            booksList = findViewById(R.id.booksList)
            booksList.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)

            // Пробуем загрузить сохранённые книги
            try {
                books.addAll(BookStorage.loadBooks(this))
            } catch (e: Exception) {
                android.util.Log.e("TextFlow", "Ошибка загрузки книг: ${e.message}")
            }

            updateList()

            findViewById<android.widget.Button>(R.id.addBookBtn).setOnClickListener {
                try {
                    pickFileLauncher.launch(arrayOf("*/*"))
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            showError(e)
        }
    }

    private fun showError(e: Exception) {
        val tv = TextView(this)
        tv.text = "ОШИБКА:\n\n${e.message}\n\n${e.stackTraceToString().take(2000)}"
        tv.setTextColor(0xFFFF0000.toInt())
        tv.setBackgroundColor(0xFF000000.toInt())
        tv.setPadding(24, 24, 24, 24)
        tv.textSize = 12f
        setContentView(tv)
    }

    private fun addBookFromUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Не критично
        }

        val name = getFileName(uri) ?: "Книга"
        val title = name.substringBeforeLast(".")
        val format = name.substringAfterLast(".", "").lowercase()

        if (format !in listOf("fb2", "txt", "html", "htm")) {
            Toast.makeText(this, "Формат не поддерживается: $format", Toast.LENGTH_LONG).show()
            return
        }

        if (books.any { it.uri == uri }) {
            Toast.makeText(this, "Книга уже добавлена", Toast.LENGTH_SHORT).show()
            return
        }

        books.add(Book(uri, title, format))
        BookStorage.saveBooks(this, books)
        updateList()
        Toast.makeText(this, "Добавлено: $title", Toast.LENGTH_SHORT).show()
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        try {
            if (uri.scheme == "content") {
                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = it.getString(idx)
                    }
                }
            }
        } catch (e: Exception) {
        }
        if (name == null) name = uri.path?.substringAfterLast("/")
        return name
    }

    private fun updateList() {
        if (books.isEmpty()) {
            infoText.text = "Нет добавленных книг.\nНажми «Добавить книгу»."
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
