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

    // Рассчитываем примерное количество символов на страницу
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
        
        // Отнимаем отступы (padding 16dp с каждой стороны) + примерная высота статус-бара
        val usableWidth = width - 64f 
        val usableHeight = height - 100f 

        // Примерное количество символов в одной строке (ширина / средняя ширина символа)
        // 0.55f — это эмпирический коэффициент для шрифта размера 18sp
        val charsPerLine = (usableWidth / (textSize * 0.55f)).toInt()
        
        // Количество строк на экране (высота / высота строки)
        // lineSpacingExtra = 6dp, значит строка занимает textSize + 6dp (примерно 1.4 от размера шрифта)
        val linesPerPage = (usableHeight / (textSize * 1.6f)).toInt()

        // Максимальное количество символов на страницу
        val maxCharsPerPage = charsPerLine * linesPerPage

        if (maxCharsPerPage <= 0) {
            pages.add(fullText) // Защита от деления на ноль
            return pages
        }

        var start = 0
        while (start < fullText.length) {
            var end = start + maxCharsPerPage
            if (end > fullText.length) end = fullText.length
            
            // Ищем ближайший пробел или перенос строки, чтобы не рвать слова
            var lastSpace = -1
            for (i in end downTo start) {
                if (i < fullText.length && (fullText[i] == ' ' || fullText[i] == '\n')) {
                    lastSpace = i
                    break
                }
            }
            
            if (lastSpace != -1 && lastSpace > start) {
                end = lastSpace
            }
            
            pages.add(fullText.substring(start, end).trim())
            start = end
        }
        return pages
    }
}
