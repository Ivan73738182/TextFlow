package com.ivangames.textflow

import android.net.Uri
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ReaderActivity : AppCompatActivity() {

    private lateinit var readerTitle: TextView
    private lateinit var readerText: TextView
    private lateinit var readerScroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        readerTitle = findViewById(R.id.readerTitle)
        readerText = findViewById(R.id.readerText)
        readerScroll = findViewById(R.id.readerScroll)

        // Файл может прийти двумя способами:
        val uri: Uri? = intent?.data
        val path: String? = intent.getStringExtra("book_path")
        val title: String = intent.getStringExtra("book_title") ?: "Книга"

        readerTitle.text = "📖 $title"

        if (uri != null) {
            loadFromUri(uri)
        } else if (path != null) {
            loadFromPath(path)
        } else {
            readerText.text = "Файл не передан"
        }
    }

    private fun loadFromUri(uri: Uri) {
        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    runOnUiThread { readerText.text = "Не удалось открыть файл" }
                    return@Thread
                }

                val content = inputStream.bufferedReader().use { it.readText() }

                // Определяем формат по URI (если .fb2 — парсим XML)
                val uriString = uri.toString().lowercase()
                val text = if (uriString.contains(".fb2")) {
                    parseFb2(content)
                } else {
                    content
                }

                runOnUiThread {
                    readerText.text = text
                }
            } catch (e: Exception) {
                runOnUiThread {
                    readerText.text = "Ошибка: ${e.message}"
                }
            }
        }.start()
    }

    private fun loadFromPath(path: String) {
        Thread {
            try {
                val file = File(path)
                if (!file.exists()) {
                    runOnUiThread { readerText.text = "Файл не найден: $path" }
                    return@Thread
                }

                val content = BufferedReader(InputStreamReader(file.inputStream())).use { it.readText() }
                val text = if (path.lowercase().endsWith(".fb2")) {
                    parseFb2(content)
                } else {
                    content
                }

                runOnUiThread {
                    readerText.text = text
                }
            } catch (e: Exception) {
                runOnUiThread {
                    readerText.text = "Ошибка: ${e.message}"
                }
            }
        }.start()
    }

    // Простой парсер FB2: вытаскиваем текст из тегов <p>, <section>, <title>
    private fun parseFb2(xml: String): String {
        try {
            // Убираем HTML/XML-теги, оставляя текст
            val text = xml
                .replace(Regex("<\\?xml[^>]*\\?>"), "")
                .replace(Regex("<!DOCTYPE[^>]*>"), "")
                .replace(Regex("<binary[^>]*>.*?</binary>", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("<[^>]+>"), "")  // убираем все теги
                .replace(Regex("\\s+"), " ")    // нормализуем пробелы
                .trim()

            return text.ifEmpty { "Не удалось извлечь текст из FB2" }
        } catch (e: Exception) {
            return "Ошибка парсинга FB2: ${e.message}"
        }
    }
}
