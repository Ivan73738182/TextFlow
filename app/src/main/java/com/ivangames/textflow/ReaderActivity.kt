package com.ivangames.textflow

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Base64
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
            saveBookToLibrary(uri, title)
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
                val text = if (uriString.contains(".fb2")) parseFb2Full(content) else SpannableStringBuilder(content)
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
                val text = if (path.lowercase().endsWith(".fb2")) parseFb2Full(content) else SpannableStringBuilder(content)
                runOnUiThread { setupPages(text) }
            } catch (e: Exception) {
                runOnUiThread { readerText.text = "Ошибка: ${e.message}" }
            }
        }.start()
    }
    // ============ ПАРСЕР FB2 С КАРТИНКАМИ И ГЛАВАМИ ============

    private fun parseFb2Full(xml: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()

        try {
            // 1. Собираем ВСЕ картинки из <binary>
            val images = mutableMapOf<String, Drawable>()
            val binaryPattern = Regex(
                "<binary\\s+[^>]*id=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</binary>",
                RegexOption.IGNORE_CASE
            )
            for (m in binaryPattern.findAll(xml)) {
                val id = m.groupValues[1]
                val base64 = m.groupValues[2].replace(Regex("\\s"), "")
                val drawable = decodeBase64Image(base64)
                if (drawable != null) {
                    images[id] = drawable
                }
            }

            // 2. Убираем <binary> из текста
            val bodyStart = xml.indexOf("<body", ignoreCase = true)
            val bodyEnd = xml.lastIndexOf("</body>", ignoreCase = true)
            if (bodyStart == -1 || bodyEnd == -1) {
                return SpannableStringBuilder("Не удалось найти <body> в FB2")
            }
            val bodyXml = xml.substring(bodyStart, bodyEnd)

            // 3. Разбираем тело
            // Заменяем <image .../> на маркеры
            val imagePattern = Regex(
                "<image\\s+[^>]*?l:href=\"#([^\"]+)\"[^>]*/>",
                RegexOption.IGNORE_CASE
            )
            var bodyWithMarkers = imagePattern.replace(bodyXml) { matchResult ->
                "[IMG:${matchResult.groupValues[1]}]"
            }

            // Заголовки — на новой странице
            bodyWithMarkers = bodyWithMarkers
                .replace(Regex("<title[^>]*>", RegexOption.IGNORE_CASE), "\n\n[[CHAPTER]]")
                .replace(Regex("</title>", RegexOption.IGNORE_CASE), "[[/CHAPTER]]\n\n")

            // Параграфы
            bodyWithMarkers = bodyWithMarkers
                .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "\n\n")
                .replace(Regex("<empty-line[^>]*/>", RegexOption.IGNORE_CASE), "\n")

            // Все остальные теги — убираем
            bodyWithMarkers = bodyWithMarkers
                .replace(Regex("<[^>]+>"), "")

            // HTML-сущности
            bodyWithMarkers = bodyWithMarkers
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")

            // Нормализуем
            bodyWithMarkers = bodyWithMarkers
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()

            // 4. Строим Spannable — текст + картинки + главы
            var lastIndex = 0
            val tokenPattern = Regex("\\[IMG:([^\\]]+)\\]|\\[\\[CHAPTER\\]\\]|\\[\\[/CHAPTER\\]\\]")
            for (m in tokenPattern.findAll(bodyWithMarkers)) {
                // Текст до токена
                sb.append(bodyWithMarkers.substring(lastIndex, m.range.first))

                when (m.value) {
                    "[[CHAPTER]]" -> {
                        // Начинаем главу — большой жирный текст
                        // Просто добавляем перенос
                        sb.append("\n\n")
                    }
                    "[[/CHAPTER]]" -> {
                        sb.append("\n\n")
                    }
                    else -> {
                        // Картинка
                        val imageId = m.groupValues[1]
                        val drawable = images[imageId]
                        if (drawable != null) {
                            sb.append("\n")
                            val start = sb.length
                            sb.append(" ")
                            sb.setSpan(
                                ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
                                start, sb.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            sb.append("\n")
                        }
                    }
                }

                lastIndex = m.range.last + 1
            }
            sb.append(bodyWithMarkers.substring(lastIndex))

        } catch (e: Exception) {
            return SpannableStringBuilder("Ошибка парсинга FB2: ${e.message}")
        }

        return sb
    }

    // Декодирование base64 в Drawable с ограничением размера
    private fun decodeBase64Image(base64: String): Drawable? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap == null) return null

            val drawable = BitmapDrawable(resources, bitmap)
            val screenWidth = resources.displayMetrics.widthPixels
            val maxWidth = (screenWidth * 0.85f).toInt()

            if (bitmap.width > maxWidth) {
                val ratio = maxWidth.toFloat() / bitmap.width
                drawable.setBounds(0, 0, maxWidth, (bitmap.height * ratio).toInt())
            } else {
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            }
            drawable
        } catch (e: Exception) {
            null
        }
    }

    // ============ РАЗБИВКА НА СТРАНИЦЫ С ГЛАВАМИ ============

    private fun setupPages(fullText: SpannableStringBuilder) {
        if (fullText.isEmpty()) {
            readerText.text = "Пустой файл"
            return
        }

        val textStr = fullText.toString()
        val newPages = mutableListOf<SpannableStringBuilder>()

        var index = 0
        while (index < textStr.length) {
            val end = minOf(index + PAGE_SIZE, textStr.length)
            var cut = end

            if (end < textStr.length) {
                // Ищем ближайший перенос или пробел
                val searchStart = maxOf(index, end - 200)
                val spaceIndex = textStr.lastIndexOf(' ', end)
                val newlineIndex = textStr.lastIndexOf('\n', end)
                cut = maxOf(newlineIndex, spaceIndex)
                if (cut < searchStart) cut = end
            }

            // Копируем Spannable кусок с сохранением картинок
            val page = SpannableStringBuilder(fullText, index, cut)
            newPages.add(page)
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

    private fun saveBookToLibrary(uri: Uri, title: String) {
        try {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
            }

            val format = when {
                uri.toString().lowercase().contains(".fb2") -> "fb2"
                uri.toString().lowercase().contains(".txt") -> "txt"
                uri.toString().lowercase().contains(".html") -> "html"
                else -> "unknown"
            }

            val books = BookStorage.loadBooks(this)
            if (books.any { it.uri == uri }) return

            books.add(Book(uri, title, format))
            BookStorage.saveBooks(this, books)
        } catch (e: Exception) {
        }
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
