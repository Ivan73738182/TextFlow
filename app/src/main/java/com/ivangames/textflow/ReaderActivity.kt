package com.ivangames.textflow

import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class ReaderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        val statusText = findViewById<TextView>(R.id.readerStatus)

        // Способ 1: файл передан через Intent (из файлового менеджера)
        val uri: Uri? = intent?.data
        if (uri != null) {
            statusText.text = "Открываю из файла:\n$uri"
            Toast.makeText(this, "Получен URI: $uri", Toast.LENGTH_LONG).show()
            return
        }

        // Способ 2: файл передан через extras (из MainActivity)
        val path = intent.getStringExtra("book_path")
        val title = intent.getStringExtra("book_title") ?: "Книга"

        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                statusText.text = "Открываю книгу:\n$title\n\nПуть: $path"
            } else {
                statusText.text = "Файл не найден: $path"
            }
        } else {
            statusText.text = "Файл не передан"
        }
    }
}
