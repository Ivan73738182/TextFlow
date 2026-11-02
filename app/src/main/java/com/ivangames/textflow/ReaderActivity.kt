package com.ivangames.textflow

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class ReaderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        val bookTitle = intent.getStringExtra("BOOK_TITLE") ?: "Книга"
        val bookPath = intent.getStringExtra("BOOK_PATH")
        val textView = findViewById<TextView>(R.id.bookTextView)

        // Устанавливаем заголовок окна
        supportActionBar?.title = bookTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (bookPath != null) {
            loadBookText(Uri.parse(bookPath), textView)
        } else {
            textView.text = "Ошибка: путь к книге не найден."
        }
    }

    private fun loadBookText(uri: Uri, textView: TextView) {
        try {
            // Пытаемся открыть поток
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                textView.text = "Не удалось открыть файл."
                return
            }

            // Читаем текст. Сначала пробуем UTF-8
            var reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            var text = reader.readText()
            reader.close()

            // Проверка на "кракозябры" (если много символов '�', значит кодировка не UTF-8)
            if (text.contains("\uFFFD") || text.contains("РЎ")) {
                // Если нашли битые символы, перечитываем в Windows-1251
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
