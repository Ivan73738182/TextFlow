package com.ivangames.textflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var infoText: TextView
    private lateinit var booksList: RecyclerView

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadBooks()
        else Toast.makeText(this, "Без разрешения книги не найти 😢", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        infoText = findViewById(R.id.infoText)
        booksList = findViewById(R.id.booksList)
        booksList.layoutManager = LinearLayoutManager(this)

        checkAndLoad()
    }

    private fun checkAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadBooks()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun loadBooks() {
        infoText.text = "Сканирую..."
        Thread {
            val books = BookScanner.scanBooks()
            runOnUiThread {
                if (books.isEmpty()) {
                    infoText.text = "Книг не найдено. Положи .txt или .fb2 в память телефона."
                } else {
                    infoText.text = "Найдено книг: ${books.size}"
                    booksList.adapter = BookAdapter(books) { book ->
                        val intent = Intent(this, ReaderActivity::class.java)
                        intent.putExtra("book_path", book.file.absolutePath)
                        intent.putExtra("book_title", book.title)
                        startActivity(intent)
                    }
                }
            }
        }.start()
    }
}
