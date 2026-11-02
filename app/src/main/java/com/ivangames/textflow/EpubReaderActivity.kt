package com.ivangames.textflow

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commitNow
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.streamer.PublicationOpener

class EpubReaderActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_epub_reader)

        val bookUriString = intent.getStringExtra("BOOK_PATH")
        if (bookUriString == null) {
            Toast.makeText(this, "Ошибка: путь к EPUB не найден", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uri = Uri.parse(bookUriString)

        // Открываем EPUB через Readium
        val assetRetriever = AssetRetriever(contentResolver, httpClient = null)
        val publicationOpener = PublicationOpener(assetRetriever)

        // В реальном приложении это делается асинхронно (в корутине)
        // Здесь для простоты примера показываем логику
        Toast.makeText(this, "Загрузка EPUB...", Toast.LENGTH_SHORT).show()
        
        // Примечание: это упрощенный каркас. В следующем шаге мы добавим полную асинхронную загрузку.
    }
}
