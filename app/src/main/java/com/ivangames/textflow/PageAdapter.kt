package com.ivangames.textflow

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PageAdapter(private val text: String) : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

    // Разбиваем текст на страницы примерно по 1500 символов
    private val pages: List<String> = splitIntoPages(text)

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.pageTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.page_text, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        // Превращаем HTML (от парсера FB2) в форматированный текст
        holder.textView.text = Html.fromHtml(pages[position], Html.FROM_HTML_MODE_LEGACY)
    }

    override fun getItemCount() = pages.size

    private fun splitIntoPages(fullText: String): List<String> {
        val pages = mutableListOf<String>()
        val pageSize = 1500 // Количество символов на страницу (можно настроить)

        if (fullText.length <= pageSize) {
            pages.add(fullText)
            return pages
        }

        var start = 0
        while (start < fullText.length) {
            var end = start + pageSize
            if (end > fullText.length) end = fullText.length
            
            // Пытаемся разбить по пробелу, чтобы не рвать слова
            val lastSpace = fullText.lastIndexOf(' ', end)
            if (lastSpace > start) {
                end = lastSpace
            }
            
            pages.add(fullText.substring(start, end))
            start = end
        }
        return pages
    }
}
