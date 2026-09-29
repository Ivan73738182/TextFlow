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
            addBookFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        infoText = findViewById(R.id.infoText)
        booksList = findViewById(R.id.booksList)
        booksList.layoutManager = LinearLayoutManager(this)

        // Загружаем сохранённые книги
        books.addAll(BookStorage.loadBooks(this))
        updateList()

        // Кнопка "Добавить книгу"
        findViewById<com.google.android.material.button.MaterialButton>(R.id.addBookBtn)
            .setOnClickListener {
                pickFileLauncher.launch(arrayOf(
                    "text/plain",
                    "application/x-fictionbook+xml",
                    "application/fb2",
                    "text/html",
                    "application/octet-stream",
                    "*/*"
                ))
            }
    }

    private fun addBookFromUri(uri: Uri) {
        try {
            // Берём постоянный доступ к файлу
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Если не получилось — не критично
        }

        val name = getFileName(uri) ?: "Книга"
        val title = name.substringBeforeLast(".")
        val format = name.substringAfterLast(".", "").lowercase()

        if (format !in listOf("fb2", "txt", "html", "htm")) {
            Toast.makeText(this, "Формат не поддерживается: $format", Toast.LENGTH_LONG).show()
            return
        }

        // Проверяем, нет ли уже такой книги
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
        if (uri.scheme == "content") {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = it.getString(idx)
                }
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast("/")
        }
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
                val intent = Intent(this, ReaderActivity::class.java)
                intent.data = book.uri
                intent.putExtra("book_title", book.title)
                startActivity(intent)
            },
            onLongClick = { book ->
                // Долгий тап — удалить книгу
                books.remove(book)
                BookStorage.saveBooks(this, books)
                updateList()
                Toast.makeText(this, "Удалено: ${book.title}", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
