package com.ivangames.textflow

import java.io.File

object BookScanner {

    private val SUPPORTED_EXTENSIONS = listOf("txt", "fb2", "html", "htm")

    fun scanBooks(): List<Book> {
        val result = mutableListOf<Book>()
        val roots = listOf(
            File("/storage/emulated/0/"),
            File("/sdcard/"),
            File("/storage/emulated/0/Download/"),
            File("/storage/emulated/0/Books/"),
            File("/storage/emulated/0/Documents/")
        )
        for (root in roots) {
            if (!root.exists() || !root.canRead()) continue
            scanDirectory(root, result, 0, 4)
        }
        return result.distinctBy { it.file.absolutePath }.sortedBy { it.title.lowercase() }
    }

    private fun scanDirectory(dir: File, result: MutableList<Book>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name.startsWith(".") ||
                    file.name == "Android" ||
                    file.name == "data" ||
                    file.name == "obb") continue
                scanDirectory(file, result, depth + 1, maxDepth)
            } else {
                val ext = file.extension.lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    result.add(
                        Book(
                            file = file,
                            title = file.nameWithoutExtension,
                            format = ext,
                            sizeKb = file.length() / 1024
                        )
                    )
                }
            }
        }
    }
}
