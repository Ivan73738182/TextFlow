package com.ivangames.textflow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private val booksList = mutableListOf<Book>()
    private lateinit var adapter: BookAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var storage: BookStorage

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // Сохраняем разрешение на доступ к файлу, чтобы после перезагрузки он читался!
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // Если файл открыт через проводник, разрешение может не сохраниться, 
                    // но это не критично для локальных файлов в папке Download.
                }
                addBookFromUri(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storage = BookStorage(this)

        recyclerView = findViewById(R.id.booksRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // 1. Загружаем книги из памяти при запуске
        booksList.addAll(storage.loadBooks())

        adapter = BookAdapter(booksList) { book ->
            val intent = Intent(this, ReaderActivity::class.java).apply {
                putExtra("BOOK_TITLE", book.title)
                putExtra("BOOK_PATH", book.path)
            }
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        val fab: FloatingActionButton = findViewById(R.id.addBookFab)
        fab.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { // Используем OPEN_DOCUMENT для сохранения прав
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/x-fictionbook+xml", "application/octet-stream"))
            }
            pickFileLauncher.launch(intent)
        }
    }

    private fun addBookFromUri(uri: Uri) {
        var fileName = "Без названия"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        
        val newBook = Book(fileName, uri.toString())
        booksList.add(newBook)
        adapter.notifyItemInserted(booksList.size - 1)
        
        // 2. Сохраняем обновленный список в память!
        storage.saveBooks(booksList)
        
        Toast.makeText(this, "Добавлено: $fileName", Toast.LENGTH_SHORT).show()
    }
}
