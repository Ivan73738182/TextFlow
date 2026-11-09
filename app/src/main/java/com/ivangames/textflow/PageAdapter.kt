package com.ivangames.textflow

import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PageAdapter(
    private val fullText: String,
    screenWidthPx: Int,
    screenHeightPx: Int,
    textSizePx: Float
) : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

    private val pages: List<String> = splitText(fullText, screenWidthPx, screenHeightPx, textSizePx)

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.pageTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.page_text, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val spanned = Html.fromHtml(pages[position], Html.FROM_HTML_MODE_LEGACY)
        holder.textView.text = spanned
        holder.textView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
    }

    override fun getItemCount() = pages.size

    private fun splitText(text: String, width: Int, height: Int, textSize: Float): List<String> {
        val pages = mutableListOf<String>()
        if (text.isEmpty()) return pages

        val usableWidth = width - 64f
        val usableHeight = height - 100f
        val charsPerLine = (usableWidth / (textSize * 0.55f)).toInt().coerceAtLeast(10)
        val linesPerPage = (usableHeight / (textSize * 1.6f)).toInt().coerceAtLeast(5)
        val maxCharsPerPage = charsPerLine * linesPerPage

        var start = 0
        while (start < text.length) {
            var end = start + maxCharsPerPage
            if (end > text.length) end = text.length
            
            if (end < text.length) {
                var lastSpace = -1
                for (i in end downTo start) {
                    if (i < text.length && (text[i] == ' ' || text[i] == '\n')) {
                        lastSpace = i
                        break
                    }
                }
                if (lastSpace > start) {
                    end = lastSpace
                }
            }
            
            pages.add(text.substring(start, end).trim())
            start = end
        }
        return pages
    }
}
