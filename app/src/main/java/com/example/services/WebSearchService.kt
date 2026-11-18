package com.example.services

import com.example.data.WebSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun searchLiveWeb(query: String): Result<List<WebSource>> = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        if (cleaned.isBlank()) return@withContext Result.success(emptyList())

        val sources = mutableListOf<WebSource>()
        var lastError: Exception? = null

        // 1. Live Wikipedia OpenSearch + Extracts API (real live web facts & URLs)
        try {
            val wikiUrl = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "query")
                .addQueryParameter("generator", "search")
                .addQueryParameter("gsrsearch", cleaned)
                .addQueryParameter("gsrlimit", "5")
                .addQueryParameter("prop", "extracts|info")
                .addQueryParameter("exintro", "1")
                .addQueryParameter("explaintext", "1")
                .addQueryParameter("exsentences", "2")
                .addQueryParameter("inprop", "url")
                .addQueryParameter("format", "json")
                .build()

            val request = Request.Builder()
                .url(wikiUrl)
                .header("User-Agent", "JarvisAI-Android/1.0")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
                    if (pages != null) {
                        val keys = pages.keys()
                        while (keys.hasNext()) {
                            val page = pages.optJSONObject(keys.next()) ?: continue
                            val title = page.optString("title", "").trim()
                            val fullUrl = page.optString("fullurl", "").trim()
                            val extract = page.optString("extract", "").trim()
                            if (title.isNotBlank() && fullUrl.startsWith("https://")) {
                                sources.add(
                                    WebSource(
                                        title = title,
                                        url = fullUrl,
                                        snippet = extract.ifBlank { "Live Wikipedia article." }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            lastError = e
        }

        // 2. Live DuckDuckGo Instant Answer API (additional real links & abstract)
        try {
            val ddgUrl = "https://api.duckduckgo.com/".toHttpUrl().newBuilder()
                .addQueryParameter("q", cleaned)
                .addQueryParameter("format", "json")
                .addQueryParameter("no_html", "1")
                .addQueryParameter("skip_disambig", "1")
                .build()

            val request = Request.Builder()
                .url(ddgUrl)
                .header("User-Agent", "JarvisAI-Android/1.0")
                .build()

            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val abstractUrl = json.optString("AbstractURL", "").trim()
                    val heading = json.optString("Heading", "").trim()
                    val abstractText = json.optString("AbstractText", "").trim()
                    if (abstractUrl.startsWith("https://") && sources.none { it.url == abstractUrl }) {
                        sources.add(
                            0,
                            WebSource(
                                title = heading.ifBlank { "Web Result" },
                                url = abstractUrl,
                                snippet = abstractText.ifBlank { "DuckDuckGo live reference." }
                            )
                        )
                    }
                    val related: JSONArray? = json.optJSONArray("RelatedTopics")
                    if (related != null) {
                        for (i in 0 until minOf(related.length(), 4)) {
                            val item = related.optJSONObject(i) ?: continue
                            val firstUrl = item.optString("FirstURL", "").trim()
                            val text = item.optString("Text", "").trim()
                            if (firstUrl.startsWith("https://") && text.isNotBlank() && sources.none { it.url == firstUrl }) {
                                sources.add(
                                    WebSource(
                                        title = text.substringBefore(" - ").take(80),
                                        url = firstUrl,
                                        snippet = text.take(240)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (lastError == null) lastError = e
        }

        if (sources.isEmpty() && lastError != null) {
            Result.failure(lastError)
        } else {
            Result.success(sources.take(6))
        }
    }
}
