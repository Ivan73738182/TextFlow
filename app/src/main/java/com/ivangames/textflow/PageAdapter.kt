package com.ivangames.textflow

import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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

    private val pages: List<String> = splitTextIntoPages(fullText, screenWidthPx, screenHeightPx, textSizePx)

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

    private fun splitTextIntoPages(text: String, width: Int, height: Int, textSize: Float): List<String> {
        val pages = mutableListOf<String>()
        if (text.isEmpty()) return pages

        val paint = TextPaint().apply {
            this.textSize = textSize
            isAntiAlias = true
        }
        
        val availableWidth = width - 64 // Отступы
        val availableHeight = height - 100 // Отступы + статус-бар

        var start = 0
        while (start < text.length) {
            // Создаем "виртуальный" лейаут, чтобы узнать, сколько влезет
            val layout = StaticLayout.Builder.obtain(text, start, text.length, paint, availableWidth)
                .setIncludePad(false)
                .setLineSpacing(6f, 1f) // lineSpacingExtra
                .build()

            // Получаем номер последней строки, которая помещается по высоте
            val lastLine = layout.getLineForVertical(availableHeight)
            
            // Получаем символ, на котором заканчивается эта строка
            var end = layout.getLineEnd(lastLine)
            
            if (end <= start) end = start + 100 // Защита от бесконечного цикла
            
            pages.add(text.substring(start, end).trim())
            start = end
        }
        return pages
    }
}
