package com.ivangames.textflow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class ReaderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        val textView = findViewById<TextView>(R.id.bookTextView)
        var bookUri: Uri? = null
        var bookTitle = "Книга"

        // Смотрим, откуда мы пришли: из библиотеки или из "Открыть с помощью"
        if (intent?.action == Intent.ACTION_VIEW) {
            bookUri = intent.data
            bookTitle = getFileName(bookUri) ?: "Внешний файл"
        } else if (intent?.action == Intent.ACTION_SEND) {
            bookUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            bookTitle = getFileName(bookUri) ?: "Отправленный файл"
        } else {
            // Обычный запуск из нашей библиотеки
            bookTitle = intent.getStringExtra("BOOK_TITLE") ?: "Книга"
            val path = intent.getStringExtra("BOOK_PATH")
            if (path != null) {
                bookUri = Uri.parse(path)
            }
        }

        supportActionBar?.title = bookTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (bookUri != null) {
            loadBookText(bookUri, textView)
        } else {
            textView.text = "Ошибка: файл не передан."
        }
    }

    // Вспомогательная функция: узнаем имя файла по его Uri
    private fun getFileName(uri: Uri?): String? {
        if (uri == null) return null
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    private fun loadBookText(uri: Uri, textView: TextView) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                textView.text = "Не удалось открыть файл."
                return
            }

            var reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            var text = reader.readText()
            reader.close()

            // Проверка на кодировку
            if (text.contains("\uFFFD") || text.contains("РЎ")) {
                val inputStream2 = contentResolver.openInputStream(uri)
                if (inputStream2 != null) {
                    reader = BufferedReader(InputStreamReader(inputStream2, "Windows-1251"))
                    text = reader.readText()
                    reader.close()
                }
            }

            textView.text = text

        } catch (e: Exception) {
            textView.text = "Ошибка при чтении: ${e.message}"
            Toast.makeText(this, "Ошибка чтения", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
