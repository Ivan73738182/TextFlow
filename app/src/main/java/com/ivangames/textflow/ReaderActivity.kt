package com.ivangames.textflow

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ReaderActivity : AppCompatActivity() {

    private lateinit var readerTitle: TextView
    private lateinit var readerText: TextView
    private lateinit var pageInfo: TextView

    private var pages: List<String> = emptyList()
    private var currentPage = 0
    private lateinit var prefs: SharedPreferences
    private var bookKey = ""

    private val PAGE_SIZE = 1000

    private lateinit var gestureDetector: GestureDetector

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        readerTitle = findViewById(R.id.readerTitle)
        readerText = findViewById(R.id.readerText)
        pageInfo = findViewById(R.id.pageInfo)

        prefs = getSharedPreferences("reader_progress", MODE_PRIVATE)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 100) prevPage() else if (dx < -100) nextPage()
                    return true
                }
                return false
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val w = readerText.width
                if (e.x > w * 0.6f) nextPage()
                else if (e.x < w * 0.4f) prevPage()
                return true
            }
        })

        readerText.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        val uri: Uri? = intent?.data
        val path: String? = intent.getStringExtra("book_path")
        val title: String = intent.getStringExtra("book_title") ?: "Книга"

        readerTitle.text = "📖 $title"

        if (uri != null) {
            bookKey = uri.toString()
            loadFromUri(uri)
        } else if (path != null) {
            bookKey = path
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
                val uriString = uri.toString().lowercase()
                val text = if (uriString.contains(".fb2")) parseFb2(content) else content
                runOnUiThread { setupPages(text) }
            } catch (e: Exception) {
                runOnUiThread { readerText.text = "Ошибка: ${e.message}" }
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
                val content = file.readText()
                val text = if (path.lowercase().endsWith(".fb2")) parseFb2(content) else content
                runOnUiThread { setupPages(text) }
            } catch (e: Exception) {
                runOnUiThread { readerText.text = "Ошибка: ${e.message}" }
            }
        }.start()
    }

    // Простой парсер FB2: убираем теги, оставляем текст
    private fun parseFb2(xml: String): String {
        return try {
            xml
                // Убираем XML-заголовок и DOCTYPE
                .replace(Regex("<\\?xml[^>]*\\?>"), "")
                .replace(Regex("<!DOCTYPE[^>]*>"), "")
                // Убираем <binary> целиком (base64-картинки)
                .replace(Regex("<binary[^>]*>[\\s\\S]*?</binary>"), "")
                // Заменяем переносы в тегах
                .replace(Regex("<p[^>]*>"), "\n\n")
                .replace(Regex("<title[^>]*>"), "\n\n═══ ")
                .replace(Regex("</title>"), " ═══\n")
                .replace(Regex("<section[^>]*>"), "\n\n")
                .replace(Regex("<empty-line[^>]*/>"), "\n")
                // Убираем все остальные теги
                .replace(Regex("<[^>]+>"), "")
                // Убираем HTML-сущности
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'")
                // Нормализуем пробелы
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
                .ifEmpty { "Не удалось извлечь текст из FB2" }
        } catch (e: Exception) {
            "Ошибка парсинга FB2: ${e.message}"
        }
    }

    // Разбивка на страницы
    private fun setupPages(text: String) {
        if (text.isBlank()) {
            readerText.text = "Пустой файл"
            return
        }

        val newPages = mutableListOf<String>()
        var index = 0
        while (index < text.length) {
            val end = minOf(index + PAGE_SIZE, text.length)
            var cut = end
            if (end < text.length) {
                val searchStart = maxOf(index, end - 200)
                val spaceIndex = text.lastIndexOf(' ', end)
                val newlineIndex = text.lastIndexOf('\n', end)
                cut = maxOf(newlineIndex, spaceIndex)
                if (cut < searchStart) cut = end
            }
            newPages.add(text.substring(index, cut).trim())
            index = cut
        }

        pages = newPages
        currentPage = prefs.getInt(bookKey, 0).coerceIn(0, pages.size - 1)
        showPage()
    }

    private fun showPage() {
        if (pages.isEmpty()) return
        readerText.text = pages[currentPage]
        pageInfo.text = "${currentPage + 1} / ${pages.size}"
    }

    private fun nextPage() {
        if (currentPage < pages.size - 1) {
            currentPage++
            showPage()
            saveProgress()
        }
    }

    private fun prevPage() {
        if (currentPage > 0) {
            currentPage--
            showPage()
            saveProgress()
        }
    }

    private fun saveProgress() {
        prefs.edit().putInt(bookKey, currentPage).apply()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                nextPage()
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                prevPage()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
    }
}
