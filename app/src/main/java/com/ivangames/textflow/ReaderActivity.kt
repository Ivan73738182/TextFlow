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
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader

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

    // Картинки, найденные в FB2
    private val images = mutableMapOf<String, Drawable>()

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
                val text = if (uriString.contains(".fb2")) {
                    parseFb2(content)
                } else {
                    SpannableStringBuilder(content)
                }
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
                    runOnUiThread { readerText.text = "Файл не найден" }
                    return@Thread
                }
                val content = file.readText()
                val text = if (path.lowercase().endsWith(".fb2")) {
                    parseFb2(content)
                } else {
                    SpannableStringBuilder(content)
                }
                runOnUiThread { setupPages(text) }
            } catch (e: Exception) {
                runOnUiThread { readerText.text = "Ошибка: ${e.message}" }
            }
        }.start()
    }
// ============ ПРАВИЛЬНЫЙ ПАРСЕР FB2 ============

private fun parseFb2(xml: String): SpannableStringBuilder {
    images.clear()
    val sb = SpannableStringBuilder()

    try {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var currentTag = ""
        var inBinary = false
        var currentBinaryId = ""
        val binaryData = StringBuilder()
        var inTitle = false
        var inBody = false
        var inParagraph = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name.lowercase()
                    when (currentTag) {
                        "body" -> inBody = true
                        "binary" -> {
                            inBinary = true
                            currentBinaryId = parser.getAttributeValue(null, "id") ?: ""
                            binaryData.clear()
                        }
                        "title" -> if (inBody) {
                            inTitle = true
                            sb.append("\n\n═══ ")
                        }
                        "p" -> if (inBody && !inBinary) {
                            inParagraph = true
                            sb.append("\n\n")
                        }
                        "empty-line" -> if (inBody) sb.append("\n")
                        "image" -> {
                            if (inBody) {
                                val href = parser.getAttributeValue(null, "l:href")
                                    ?: parser.getAttributeValue(null, "href")
                                    ?: parser.getAttributeValue(
                                        "http://www.w3.org/1999/xlink",
                                        "href"
                                    )
                                if (href != null) {
                                    val imageId = href.removePrefix("#")
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
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (inBinary) {
                        binaryData.append(text)
                    } else if (inBody && (inTitle || inParagraph)) {
                        sb.append(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    when (tag) {
                        "body" -> inBody = false
                        "binary" -> {
                            if (inBinary && currentBinaryId.isNotEmpty()) {
                                val drawable = decodeBase64Image(binaryData.toString())
                                if (drawable != null) {
                                    images[currentBinaryId] = drawable
                                }
                            }
                            inBinary = false
                            currentBinaryId = ""
                            binaryData.clear()
                        }
                        "title" -> if (inBody) {
                            inTitle = false
                            sb.append(" ═══")
                        }
                        "p" -> inParagraph = false
                    }
                }
            }
            eventType = parser.next()
        }
    } catch (e: Exception) {
        sb.append("\n\nОшибка парсинга FB2: ${e.message}")
    }

    // Убираем лишние переносы
    val cleaned = sb.toString()
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    // Пересобираем, но уже с картинками (нужно второй проход)
    // Проще: сначала парсим картинки, потом парсим текст заново
    return if (images.isEmpty() && sb.isEmpty()) {
        SpannableStringBuilder("Не удалось прочитать FB2")
    } else {
        // Второй проход: теперь картинки в images, вставляем их
        parseFb2WithImagesSecondPass(xml)
    }
}

// Второй проход — вставляем картинки, т.к. они приходят до текста
private fun parseFb2WithImagesSecondPass(xml: String): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    try {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var inBody = false
        var inTitle = false
        var inParagraph = false
        var inBinary = false
        var currentBinaryId = ""
        val binaryData = StringBuilder()

        // Сначала соберём все картинки (первый проход)
        // Второй проход: собираем текст

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase()
                    when (tag) {
                        "body" -> inBody = true
                        "binary" -> {
                            inBinary = true
                            currentBinaryId = parser.getAttributeValue(null, "id") ?: ""
                            binaryData.clear()
                        }
                        "title" -> if (inBody) {
                            inTitle = true
                            sb.append("\n\n")
                            val start = sb.length
                            sb.append(" ")
                            sb.setSpan(
                                RelativeSizeSpan(1.4f),
                                start, sb.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                            sb.setSpan(
                                StyleSpan(Typeface.BOLD),
                                start, sb.length,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        "p" -> if (inBody && !inBinary) {
                            inParagraph = true
                            sb.append("\n\n")
                        }
                        "empty-line" -> if (inBody) sb.append("\n")
                        "image" -> {
                            if (inBody) {
                                val href = parser.getAttributeValue(null, "l:href")
                                    ?: parser.getAttributeValue(null, "href")
                                if (href != null) {
                                    val imageId = href.removePrefix("#")
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
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (inBinary) {
                        binaryData.append(text)
                    } else if (inBody && (inTitle || inParagraph)) {
                        sb.append(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.lowercase()
                    when (tag) {
                        "body" -> inBody = false
                        "binary" -> {
                            if (currentBinaryId.isNotEmpty() && images[currentBinaryId] == null) {
                                val drawable = decodeBase64Image(binaryData.toString())
                                if (drawable != null) {
                                    images[currentBinaryId] = drawable
                                }
                            }
                            inBinary = false
                            currentBinaryId = ""
                            binaryData.clear()
                        }
                        "title" -> inTitle = false
                        "p" -> inParagraph = false
                    }
                }
            }
            eventType = parser.next()
        }
    } catch (e: Exception) {
        sb.append("\n\nОшибка: ${e.message}")
    }

    return sb
}

// Декодирование base64 в Drawable с ограничением размера
private fun decodeBase64Image(base64: String): Drawable? {
    return try {
        val cleanBase64 = base64.replace(Regex("\\s"), "")
        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap == null) return null
        val drawable = BitmapDrawable(resources, bitmap)

        // Максимальная ширина — 80% экрана
        val screenWidth = resources.displayMetrics.widthPixels
        val maxWidth = (screenWidth * 0.8f).toInt()
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

        // Находим все позиции заголовков глав
        val chapterPositions = mutableListOf<Int>()
        val chapterPattern = Regex("═══[^═]+═══")
        for (m in chapterPattern.findAll(textStr)) {
            chapterPositions.add(m.range.first)
        }

        var index = 0
        while (index < textStr.length) {
            // Проверяем, не начинается ли глава в этом месте
            val chapterStart = chapterPositions.firstOrNull { it >= index }
            val end = minOf(index + PAGE_SIZE, textStr.length)

            var cut: Int

            // Если глава начинается в середине страницы — режем перед главой
            if (chapterStart != null && chapterStart > index && chapterStart < end) {
                cut = chapterStart
            } else {
                // Обычная обрезка по границе
                cut = end
                if (end < textStr.length) {
                    val searchStart = maxOf(index, end - 200)
                    val spaceIndex = textStr.lastIndexOf(' ', end)
                    val newlineIndex = textStr.lastIndexOf('\n', end)
                    cut = maxOf(newlineIndex, spaceIndex)
                    if (cut < searchStart) cut = end
                }
            }

            if (cut <= index) {
                // Защита от бесконечного цикла
                cut = end
            }

            // Копируем кусок Spannable с сохранением спанов (картинок, стилей)
            val pageText = SpannableStringBuilder(fullText, index, cut)
            newPages.add(pageText)
            index = cut
        }

        pages = newPages

        // Восстанавливаем страницу
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
