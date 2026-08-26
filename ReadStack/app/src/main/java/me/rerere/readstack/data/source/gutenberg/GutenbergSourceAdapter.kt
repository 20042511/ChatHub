package me.rerere.readstack.data.source.gutenberg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.readstack.data.source.DocumentSource
import me.rerere.readstack.domain.model.DocFormat
import me.rerere.readstack.domain.model.DocumentRef
import me.rerere.readstack.domain.model.PagedResults
import me.rerere.readstack.domain.model.SearchResult
import me.rerere.readstack.domain.model.SourceKind
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Source adapter for Project Gutenberg, the public-domain ebook library.
 *
 * Uses the on-site search page (no API key) and a predictable file URL
 * pattern: https://www.gutenberg.org/cache/epub/{id}/pg{id}-images-3.epub
 * (falls back to plain text if the EPUB doesn't exist).
 */
class GutenbergSourceAdapter(
    private val client: OkHttpClient,
) : DocumentSource {

    override val name: String = "Project Gutenberg"

    override suspend fun search(query: String, page: Int): PagedResults = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext popularList(limit = 30)
        }
        val url = "https://www.gutenberg.org/ebooks/search/?query=${query.urlEncoded()}" +
            "&submit_search=Go%21&page=${page}"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext PagedResults(emptyList())
            val html = resp.body?.string().orEmpty()
            val doc = Jsoup.parse(html, "https://www.gutenberg.org")
            val items = doc.select("li.booklink").mapNotNull { el ->
                val a = el.selectFirst("a.link") ?: return@mapNotNull null
                val href = a.absUrl("href")
                val id = href.substringAfterLast('/').takeWhile { it.isDigit() }
                if (id.isBlank()) return@mapNotNull null
                val title = el.selectFirst(".title")?.text()?.trim().orEmpty()
                val author = el.selectFirst(".subtitle")?.text()?.trim()
                SearchResult(
                    ref = DocumentRef(
                        source = SourceKind.GUTENBERG,
                        externalId = id,
                        title = title,
                        subtitle = author,
                        author = author,
                        sourceUrl = href,
                        downloadUrl = epubUrlFor(id),
                        format = DocFormat.EPUB,
                        tags = listOf("ebook", "gutenberg", "public-domain"),
                    ),
                )
            }
            PagedResults(items = items, nextPage = if (items.isNotEmpty()) page + 1 else null)
        }
    }

    override suspend fun trending(limit: Int): PagedResults = popularList(limit)

    private suspend fun popularList(limit: Int): PagedResults = withContext(Dispatchers.IO) {
        // The "popular" page exists at /browse/scores/top
        val req = Request.Builder()
            .url("https://www.gutenberg.org/browse/scores/top")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext PagedResults(emptyList())
            val doc = Jsoup.parse(resp.body?.string().orEmpty(), "https://www.gutenberg.org")
            val items = doc.select("ol.books-ol li a").take(limit).mapNotNull { a ->
                val href = a.absUrl("href")
                val id = href.substringAfterLast('/').takeWhile { it.isDigit() }
                val title = a.text().trim()
                if (id.isBlank() || title.isBlank()) null
                else SearchResult(
                    ref = DocumentRef(
                        source = SourceKind.GUTENBERG,
                        externalId = id,
                        title = title,
                        sourceUrl = href,
                        downloadUrl = epubUrlFor(id),
                        format = DocFormat.EPUB,
                        tags = listOf("ebook", "gutenberg"),
                    )
                )
            }
            PagedResults(items = items, total = items.size)
        }
    }

    /** Predictable EPUB URL — Project Gutenberg's CDN. */
    fun epubUrlFor(id: String): String =
        "https://www.gutenberg.org/cache/epub/$id/pg$id-images-3.epub"

    /** Predictable plain-text URL — fallback when EPUB isn't built. */
    fun textUrlFor(id: String): String =
        "https://www.gutenberg.org/files/$id/$id-0.txt"

    private fun String.urlEncoded(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
