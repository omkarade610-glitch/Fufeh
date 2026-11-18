package com.example.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

data class ParsedDocument(
    val fileName: String,
    val mimeType: String,
    val extractedText: String,
    val pdfPageBitmaps: List<Bitmap> = emptyList()
)

object DocumentParser {

    suspend fun parseDocument(context: Context, uri: Uri): Result<ParsedDocument> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val fileName = queryFileName(context, uri) ?: "document"
            val lower = fileName.lowercase()

            when {
                lower.endsWith(".pdf") || mimeType.contains("pdf") -> {
                    val bitmaps = mutableListOf<Bitmap>()
                    resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            val maxPages = minOf(renderer.pageCount, 3)
                            for (i in 0 until maxPages) {
                                renderer.openPage(i).use { page ->
                                    val width = minOf(page.width * 2, 1200)
                                    val height = (page.height.toFloat() / page.width.toFloat() * width).toInt().coerceAtLeast(100)
                                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bmp)
                                    canvas.drawColor(Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bitmaps.add(bmp)
                                }
                            }
                        }
                    }
                    Result.success(
                        ParsedDocument(
                            fileName = fileName,
                            mimeType = mimeType,
                            extractedText = "Rendered ${bitmaps.size} PDF page(s) for Gemini multimodal analysis.",
                            pdfPageBitmaps = bitmaps
                        )
                    )
                }

                lower.endsWith(".docx") || lower.endsWith(".xlsx") || lower.endsWith(".pptx") ||
                    mimeType.contains("officedocument") -> {
                    val sb = StringBuilder()
                    resolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                val name = entry.name.lowercase()
                                if (name.endsWith(".xml") &&
                                    (name.contains("document") || name.contains("slide") || name.contains("sharedstrings") || name.contains("sheet"))
                                ) {
                                    val xml = zip.bufferedReader().readText()
                                    val stripped = xml
                                        .replace(Regex("<[^>]+>"), " ")
                                        .replace(Regex("&amp;"), "&")
                                        .replace(Regex("&lt;"), "<")
                                        .replace(Regex("&gt;"), ">")
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                                    if (stripped.isNotBlank()) {
                                        sb.appendLine(stripped)
                                    }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                    }
                    val text = sb.toString().trim().take(20_000)
                    Result.success(
                        ParsedDocument(
                            fileName = fileName,
                            mimeType = mimeType,
                            extractedText = text.ifBlank { "No readable text nodes found inside Office XML archive." }
                        )
                    )
                }

                else -> {
                    val text = resolver.openInputStream(uri)?.use { input ->
                        BufferedReader(InputStreamReader(input)).readText()
                    }.orEmpty().take(20_000)
                    Result.success(
                        ParsedDocument(
                            fileName = fileName,
                            mimeType = mimeType,
                            extractedText = text.ifBlank { "File is empty or binary." }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }
}
