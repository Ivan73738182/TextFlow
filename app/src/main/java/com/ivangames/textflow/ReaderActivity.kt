package com.ivangames.textflow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStreamReader

class ReaderActivity : AppCompatActivity() {

    private var viewPager: ViewPager2? = null
    private var bookTitle: String = ""
    private lateinit var prefs: android.content.SharedPreferences
    private val notesMap = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        viewPager = findViewById(R.id.viewPager)
        prefs = getSharedPreferences("TextFlowPrefs", MODE_PRIVATE)

        var bookUri: Uri? = null
        
        if (intent?.action == Intent.ACTION_VIEW) {
            bookUri = intent.data
            bookTitle = getFileName(bookUri) ?: "Внешний файл"
        } else if (intent?.action == Intent.ACTION_SEND) {
            bookUri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
            bookTitle = getFileName(bookUri) ?: "Отправленный файл"
        } else {
            bookTitle = intent.getStringExtra("BOOK_TITLE") ?: "Книга"
            val path = intent.getStringExtra("BOOK_PATH")
            if (path != null) {
                bookUri = Uri.parse(path)
            }
        }

        supportActionBar?.title = bookTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (bookUri != null) {
            val lowerTitle = bookTitle.lowercase()
            if (lowerTitle.endsWith(".fb2") || lowerTitle.endsWith(".fb3")) {
                loadFb2Fb3(bookUri)
            } else {
                loadTxtOrFb(bookUri)
            }
        } else {
            Toast.makeText(this, "Файл не передан", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewPager != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    viewPager?.currentItem = (viewPager?.currentItem ?: 0) + 1
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    viewPager?.currentItem = (viewPager?.currentItem ?: 0) - 1
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setupPager(text: String) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val textSizePx = 18 * displayMetrics.scaledDensity

        val adapter = PageAdapter(text, screenWidth, screenHeight, textSizePx)
        viewPager?.adapter = adapter

        val savedPage = prefs.getInt("progress_$bookTitle", 0)
        if (savedPage in 0 until adapter.itemCount) {
            viewPager?.currentItem = savedPage
        }

        viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                prefs.edit().putInt("progress_$bookTitle", position).apply()
            }
        })
    }

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

    private fun loadTxtOrFb(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            var reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            var text = reader.readText()
            reader.close()

            if (text.trim().startsWith("<?xml")) {
                text = text.replace(Regex("<[^>]*>"), "")
            }

            if (text.contains("\uFFFD") || text.contains("РЎ")) {
                val inputStream2 = contentResolver.openInputStream(uri)
                if (inputStream2 != null) {
                    reader = BufferedReader(InputStreamReader(inputStream2, "Windows-1251"))
                    text = reader.readText()
                    reader.close()
                }
            }
            setupPager(text)
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка TXT/FB: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadFb2Fb3(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(inputStream, null)

            val htmlBuilder = StringBuilder()
            var eventType = parser.eventType
            var insideBody = false
            var skipBinary = false
            var insideNotes = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                
                if (eventType == XmlPullParser.START_TAG) {
                    if (tagName == "body") {
                        val name = parser.getAttributeValue(null, "name")
                        insideNotes = (name == "notes")
                        insideBody = true
                    }
                    if (tagName == "binary") skipBinary = true
                    
                    if (insideBody && !skipBinary) {
                        // Если мы в сносках, собираем их в словарь
                        if (insideNotes) {
                            if (tagName == "section") {
                                val id = parser.getAttributeValue(null, "id") ?: ""
                                htmlBuilder.append("\n<!-- NOTE:$id -->\n")
                            }
                        } else {
                            // Основной текст
                            when (tagName) {
                                "title" -> htmlBuilder.append("<br><h3>")
                                "section" -> htmlBuilder.append("<br>")
                                "p" -> htmlBuilder.append("<p>")
                                "emphasis" -> htmlBuilder.append("<i>")
                                "strong" -> htmlBuilder.append("<b>")
                                "subtitle" -> htmlBuilder.append("<b><i>")
                                "empty-line" -> htmlBuilder.append("<br><br>")
                                "poem" -> htmlBuilder.append("<br><i>")
                                "stanza" -> htmlBuilder.append("<br>")
                                "v" -> htmlBuilder.append("<br>")
                                "epigraph" -> htmlBuilder.append("<br><i>")
                                "a" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (href != null && href.contains("#")) {
                                        val id = href.substringAfter("#")
                                        htmlBuilder.append("<sup><a href=\"#note_$id\">[$id]</a></sup>")
                                    } else {
                                        htmlBuilder.append(" [")
                                    }
                                }
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (insideBody && !skipBinary) {
                        if (insideNotes) {
                            htmlBuilder.append(parser.text)
                        } else {
                            htmlBuilder.append(parser.text)
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (tagName == "binary") skipBinary = false
                    if (insideBody && !skipBinary) {
                        if (insideNotes) {
                            // Конец сноски — можно сохранить (пока пропускаем)
                        } else {
                            when (tagName) {
                                "title" -> htmlBuilder.append("</h3>")
                                "p" -> htmlBuilder.append("</p>")
                                "emphasis" -> htmlBuilder.append("</i>")
                                "strong" -> htmlBuilder.append("</b>")
                                "subtitle" -> htmlBuilder.append("</i></b>")
                                "poem" -> htmlBuilder.append("</i>")
                                "epigraph" -> htmlBuilder.append("</i>")
                                "a" -> htmlBuilder.append("]")
                            }
                        }
                    }
                    if (tagName == "body") {
                        insideBody = false
                        insideNotes = false
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()

            if (htmlBuilder.isNotEmpty()) {
                setupPager(htmlBuilder.toString())
            } else {
                Toast.makeText(this, "Текст не найден.", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка FB2/FB3: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
