package com.ivangames.textflow

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PageAdapter(
    private val text: String,
    screenWidthPx: Int,
    screenHeightPx: Int,
    textSizePx: Float
) : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

    private val pages: List<String> = calculatePages(text, screenWidthPx, screenHeightPx, textSizePx)

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.pageTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.page_text, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.textView.text = Html.fromHtml(pages[position], Html.FROM_HTML_MODE_LEGACY)
    }

    override fun getItemCount() = pages.size

    private fun calculatePages(fullText: String, width: Int, height: Int, textSize: Float): List<String> {
        val pages = mutableListOf<String>()
        val usableWidth = width - 64f 
        val usableHeight = height - 100f 
        val charsPerLine = (usableWidth / (textSize * 0.55f)).toInt()
        val linesPerPage = (usableHeight / (textSize * 1.6f)).toInt()
        val maxCharsPerPage = charsPerLine * linesPerPage

        if (maxCharsPerPage <= 0) {
            pages.add(fullText)
            return pages
        }

        var start = 0
        while (start < fullText.length) {
            var end = start + maxCharsPerPage
            if (end > fullText.length) end = fullText.length
            
            // Ищем последний пробел или перенос строки СТРОГО ДО лимита
            if (end < fullText.length) {
                var lastBreak = -1
                for (i in end downTo start) {
                    if (i < fullText.length && (fullText[i] == ' ' || fullText[i] == '\n')) {
                        lastBreak = i
                        break
                    }
                }
                if (lastBreak > start) {
                    end = lastBreak
                }
            }
            
            pages.add(fullText.substring(start, end).trim())
            start = end
        }
        return pages
    }
}
