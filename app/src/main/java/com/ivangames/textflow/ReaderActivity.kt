package com.ivangames.textflow

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.Base64
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.regex.Pattern

class ReaderActivity : AppCompatActivity() {

    private lateinit var readerTitle: TextView
    private lateinit var readerText: TextView
    private lateinit var pageInfo: TextView

    private var pages: List<SpannableStringBuilder> = emptyList()
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
                val text = if (uriString.contains(".fb2")) parseFb2WithImages(content) else plainText(content)
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
                val text = if (path.lowercase().endsWith(".fb2")) parseFb2WithImages(content) else plainText(content)
                runOnUiThread { setupPages(text) }
            } catch (e: Exception) {
                runOnUiThread { readerText.text = "Ошибка: ${e.message}" }
            }
        }.start()
    }
    // ============ ПАРСЕР FB2 С КАРТИНКАМИ ============

    private fun parseFb2WithImages(xml: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()

        try {
            // 1. Собираем картинки из <binary id="...">...</binary>
            val images = mutableMapOf<String, BitmapDrawable>()
            val binaryPattern = Pattern.compile(
                "<binary\\s+id=\"([^\"]+)\"[^>]*>([^<]+)</binary>",
                Pattern.DOTALL
            )
            val binaryMatcher = binaryPattern.matcher(xml)
            while (binaryMatcher.find()) {
                val id = binaryMatcher.group(1) ?: continue
                val base64Data = binaryMatcher.group(2)?.trim() ?: continue
                try {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        val drawable = BitmapDrawable(resources, bitmap)
                        // Ограничиваем размер картинки (не больше 500px по ширине)
                        val maxWidth = 500
                        if (bitmap.width > maxWidth) {
                            val ratio = maxWidth.toFloat() / bitmap.width
                            drawable.setBounds(0, 0, maxWidth, (bitmap.height * ratio).toInt())
                        } else {
                            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                        }
                        images[id] = drawable
                    }
                } catch (e: Exception) {
                    // Пропускаем битые картинки
                }
            }

            // 2. Убираем <binary> из текста
            val bodyStart = xml.indexOf("<body")
            val bodyEnd = xml.indexOf("</body>")
            if (bodyStart == -1 || bodyEnd == -1) {
                return SpannableStringBuilder("Не удалось найти <body> в FB2")
            }
            val bodyXml = xml.substring(bodyStart, bodyEnd)

            // 3. Заменяем <image .../> на маркеры [IMG:id]
            val imagePattern = Pattern.compile("<image\\s+[^>]*l:href=\"#([^\"]+)\"[^>]*/>")
            val imageMatcher = imagePattern.matcher(bodyXml)
            val bodyWithMarkers = imageMatcher.replaceAll("[IMG:$1]")

            // 4. Убираем все XML-теги, но сохраняем переносы для <p>, <title>, <section>
            val cleaned = bodyWithMarkers
                .replace(Regex("<p[^>]*>"), "\n\n")
                .replace(Regex("<title[^>]*>"), "\n\n═══ ")
                .replace(Regex("</title>"), " ═══\n")
                .replace(Regex("<section[^>]*>"), "\n\n")
                .replace(Regex("<empty-line[^>]*/>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()

            // 5. Строим текст, вставляя картинки
            var lastIndex = 0
            val markerPattern = Pattern.compile("\\[IMG:([^\\]]+)\\]")
            val markerMatcher = markerPattern.matcher(cleaned)

            while (markerMatcher.find()) {
                // Текст до картинки
                sb.append(cleaned.substring(lastIndex, markerMatcher.start()))

                // Картинка
                val imageId = markerMatcher.group(1)
                val drawable = images[imageId]
                if (drawable != null) {
                    val start = sb.length
                    sb.append(" ")  // placeholder для картинки
                    sb.setSpan(
                        ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                        start, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.append("\n")
                } else {
                    sb.append("[картинка]")
                }

                lastIndex = markerMatcher.end()
            }
            // Остаток
            sb.append(cleaned.substring(lastIndex))

        } catch (e: Exception) {
            return SpannableStringBuilder("Ошибка парсинга FB2: ${e.message}")
        }

        return sb
    }

    private fun plainText(text: String): SpannableStringBuilder {
        return SpannableStringBuilder(text)
    }

    // ============ РАЗБИВКА НА СТРАНИЦЫ ============

    private fun setupPages(fullText: SpannableStringBuilder) {
        if (fullText.isEmpty()) {
            readerText.text = "Пустой файл"
            return
        }

        val newPages = mutableListOf<SpannableStringBuilder>()
        var index = 0
        val textStr = fullText.toString()

        while (index < textStr.length) {
            val end = minOf(index + PAGE_SIZE, textStr.length)
            var cut = end
            if (end < textStr.length) {
                val searchStart = maxOf(index, end - 200)
                val spaceIndex = textStr.lastIndexOf(' ', end)
                val newlineIndex = textStr.lastIndexOf('\n', end)
                cut = maxOf(newlineIndex, spaceIndex)
                if (cut < searchStart) cut = end
            }

            // Копируем кусок Spannable с сохранением спанов (картинок)
            val pageText = SpannableStringBuilder(fullText, index, cut)

            // Проверяем: если картинка попала на границу — сдвигаем cut
            // (упрощённо: оставляем как есть)
            newPages.add(pageText)
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
