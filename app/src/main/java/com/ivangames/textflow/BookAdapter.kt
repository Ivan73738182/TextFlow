package com.ivangames.textflow

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BookAdapter(
    private val books: List<Book>,
    private val onClick: (Book) -> Unit,
    private val onLongClick: (Book) -> Unit
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    class BookViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.bookTitle)
        val info: TextView = view.findViewById(R.id.bookInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]
        holder.title.text = book.title
        val formatLabel = when (book.format) {
            "fb2" -> "📕 FB2"
            "txt" -> "📄 TXT"
            "html", "htm" -> "🌐 HTML"
            else -> "📖 " + book.format.uppercase()
        }
        holder.info.text = formatLabel

        holder.itemView.setOnClickListener { onClick(book) }
        holder.itemView.setOnLongClickListener {
            onLongClick(book)
            true
        }
    }

    override fun getItemCount(): Int = books.size
}
