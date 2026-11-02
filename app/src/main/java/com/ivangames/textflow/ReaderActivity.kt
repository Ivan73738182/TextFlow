package com.ivangames.textflow

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Html
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStreamReader

class ReaderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        val textView = findViewById<TextView>(R.id.bookTextView)
        var bookUri: Uri? = null
        var bookTitle = "Книга"

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
            if (bookTitle.lowercase().endsWith(".fb2")) {
                loadFb2(bookUri, textView)
            } else {
                loadTxt(bookUri, textView)
            }
        } else {
            textView.text = "Ошибка: файл не передан."
        }
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

    private fun loadTxt(uri: Uri, textView: TextView) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            var reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            var text = reader.readText()
            reader.close()

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
            textView.text = "Ошибка TXT: ${e.message}"
        }
    }

    private fun loadFb2(uri: Uri, textView: TextView) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(inputStream, null)

            val htmlBuilder = StringBuilder()
            var eventType = parser.eventType
            var insideBody = false // Читаем только то, что внутри <body>

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                
                if (eventType == XmlPullParser.START_TAG) {
                    if (tagName == "body") {
                        insideBody = true
                    }
                    
                    if (insideBody) {
                        when (tagName) {
                            "title" -> htmlBuilder.append("<h2>")
                            "p" -> htmlBuilder.append("<p>")
                            "section" -> htmlBuilder.append("<br><br>")
                            "emphasis" -> htmlBuilder.append("<i>")
                            "strong" -> htmlBuilder.append("<b>")
                        }
                    }
                } else if (eventType == XmlPullParser.TEXT) {
                    if (insideBody) {
                        htmlBuilder.append(parser.text)
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (insideBody) {
                        when (tagName) {
                            "title" -> htmlBuilder.append("</h2><br>")
                            "p" -> htmlBuilder.append("</p><br>")
                            "emphasis" -> htmlBuilder.append("</i>")
                            "strong" -> htmlBuilder.append("</b>")
                        }
                    }
                    if (tagName == "body") {
                        insideBody = false
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()

            if (htmlBuilder.isNotEmpty()) {
                textView.text = Html.fromHtml(htmlBuilder.toString(), Html.FROM_HTML_MODE_LEGACY)
            } else {
                textView.text = "Текст не найден. Возможно, файл поврежден или пуст."
            }

        } catch (e: Exception) {
            textView.text = "Ошибка FB2: ${e.message}"
            Toast.makeText(this, "Не удалось прочитать FB2", Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
