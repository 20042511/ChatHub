package me.rerere.readstack.data.source.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.readstack.data.source.DocumentSource
import me.rerere.readstack.domain.model.DocFormat
import me.rerere.readstack.domain.model.DocumentRef
import me.rerere.readstack.domain.model.PagedResults
import me.rerere.readstack.domain.model.SearchResult
import me.rerere.readstack.domain.model.SourceKind
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Generic web search via DuckDuckGo's HTML endpoint. No API key required.
 * Each result's "downloadUrl" is the result page itself; when the user
 * taps Download we run the result page through the docs HTML→Markdown
 * converter so it can be read offline.
 */
class DuckDuckGoSearchAdapter(
    private val client: OkHttpClient,
) : DocumentSource {

    override val name: String = "Web search"

    override suspend fun search(query: String, page: Int): PagedResults = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext PagedResults(emptyList())
        val form = FormBody.Builder()
            .add("q", query)
            .add("kl", "us-en")
            .build()
        val req = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=${query.urlEncoded()}")
            .post(form)
            .header("Referer", "https://html.duckduckgo.com/")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext PagedResults(emptyList())
            val html = resp.body?.string().orEmpty()
            val doc = Jsoup.parse(html, "https://html.duckduckgo.com")
            val items = doc.select("div.result").mapNotNull { el ->
                val a = el.selectFirst("a.result__a") ?: return@mapNotNull null
                val title = a.text().trim()
                val href = a.absUrl("href").decodeRedirect()
                if (href.isBlank() || title.isBlank()) return@mapNotNull null
                val snippet = el.selectFirst("a.result__snippet, .result__snippet")?.text()?.trim()
                SearchResult(
                    ref = DocumentRef(
                        source = SourceKind.WEB_SEARCH,
                        externalId = href,
                        title = title,
                        subtitle = hostOf(href),
                        sourceUrl = href,
                        downloadUrl = href,
                        format = DocFormat.MARKDOWN,
                        description = snippet,
                        tags = listOf("web"),
                    ),
                    matchedSnippet = snippet,
                )
            }
            PagedResults(items = items, nextPage = if (items.isNotEmpty()) page + 1 else null)
        }
    }

    override suspend fun trending(limit: Int): PagedResults = PagedResults(emptyList())

    private fun String.urlEncoded(): String =
        java.net.URLEncoder.encode(this, "UTF-8")

    private fun String.decodeRedirect(): String =
        // DDG uses /?uddg=<encoded-url> as a redirect; unwrap if present
        Regex("uddg=([^&]+)").find(this)?.groupValues?.get(1)
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            ?: this

    private fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrDefault("")
}
