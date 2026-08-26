package me.rerere.readstack.data.source.docs

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
import org.jsoup.nodes.Document

/**
 * Generic HTML adapter for Read-the-Docs / GitBook / Docusaurus / plain HTML docs.
 *
 * It does not implement real search across the open web (these sites have
 * no shared index). Instead the user can:
 *  - browse a curated list of popular documentation projects, or
 *  - paste a URL to a docs page; we will detect the kind, fetch the
 *    landing page (and convert to Markdown-ish text), and return a
 *    single DocumentRef that downloads the rendered content.
 */
class DocsSiteAdapter(
    private val client: OkHttpClient,
) : DocumentSource {

    override val name: String = "Official Docs"

    private val catalogue: List<DocCatalogueEntry> = listOf(
        DocCatalogueEntry(
            title = "Kotlin Docs",
            author = "JetBrains",
            url = "https://kotlinlang.org/docs/home.html",
            tags = listOf("kotlin", "jvm", "language"),
        ),
        DocCatalogueEntry(
            title = "Android Developers",
            author = "Google",
            url = "https://developer.android.com/develop",
            tags = listOf("android", "google"),
        ),
        DocCatalogueEntry(
            title = "Jetpack Compose",
            author = "Google",
            url = "https://developer.android.com/develop/ui/compose",
            tags = listOf("android", "compose", "ui"),
        ),
        DocCatalogueEntry(
            title = "Material Design 3",
            author = "Google",
            url = "https://m3.material.io/",
            tags = listOf("design", "material"),
        ),
        DocCatalogueEntry(
            title = "React",
            author = "Meta",
            url = "https://react.dev/learn",
            tags = listOf("react", "javascript"),
        ),
        DocCatalogueEntry(
            title = "Vue.js",
            author = "Evan You",
            url = "https://vuejs.org/guide/introduction.html",
            tags = listOf("vue", "javascript"),
        ),
        DocCatalogueEntry(
            title = "Django",
            author = "Django Software Foundation",
            url = "https://docs.djangoproject.com/",
            tags = listOf("python", "django", "web"),
        ),
        DocCatalogueEntry(
            title = "FastAPI",
            author = "Sebastián Ramírez",
            url = "https://fastapi.tiangolo.com/",
            tags = listOf("python", "fastapi", "web"),
        ),
        DocCatalogueEntry(
            title = "Spring Boot",
            author = "VMware",
            url = "https://spring.io/guides",
            tags = listOf("java", "spring", "web"),
        ),
        DocCatalogueEntry(
            title = "Rust by Example",
            author = "Rust Foundation",
            url = "https://doc.rust-lang.org/rust-by-example/",
            tags = listOf("rust", "language"),
        ),
        DocCatalogueEntry(
            title = "Go",
            author = "Google",
            url = "https://go.dev/doc/",
            tags = listOf("go", "language"),
        ),
        DocCatalogueEntry(
            title = "Hugging Face Transformers",
            author = "Hugging Face",
            url = "https://huggingface.co/docs/transformers/index",
            tags = listOf("ml", "ai", "python"),
        ),
        DocCatalogueEntry(
            title = "PyTorch",
            author = "Meta AI",
            url = "https://pytorch.org/docs/stable/index.html",
            tags = listOf("ml", "ai", "python"),
        ),
        DocCatalogueEntry(
            title = "Docker",
            author = "Docker Inc.",
            url = "https://docs.docker.com/",
            tags = listOf("devops", "docker"),
        ),
        DocCatalogueEntry(
            title = "Kubernetes",
            author = "CNCF",
            url = "https://kubernetes.io/docs/home/",
            tags = listOf("devops", "k8s"),
        ),
        DocCatalogueEntry(
            title = "PostgreSQL",
            author = "PG Global Dev Group",
            url = "https://www.postgresql.org/docs/",
            tags = listOf("sql", "database"),
        ),
        DocCatalogueEntry(
            title = "SQLite",
            author = "SQLite Consortium",
            url = "https://www.sqlite.org/docs.html",
            tags = listOf("sql", "database"),
        ),
        DocCatalogueEntry(
            title = "Tailwind CSS",
            author = "Tailwind Labs",
            url = "https://tailwindcss.com/docs/installation",
            tags = listOf("css", "design"),
        ),
        DocCatalogueEntry(
            title = "TypeScript",
            author = "Microsoft",
            url = "https://www.typescriptlang.org/docs/",
            tags = listOf("typescript", "javascript"),
        ),
        DocCatalogueEntry(
            title = "MDN Web Docs",
            author = "Mozilla",
            url = "https://developer.mozilla.org/",
            tags = listOf("web", "html", "css", "javascript"),
        ),
    )

    override suspend fun search(query: String, page: Int): PagedResults {
        if (query.isBlank()) {
            return PagedResults(items = catalogue.map { it.toSearchResult() })
        }
        val q = query.lowercase()
        val matches = catalogue.filter { c ->
            c.title.lowercase().contains(q) ||
                c.author.lowercase().contains(q) ||
                c.tags.any { it.lowercase().contains(q) }
        }
        return PagedResults(items = matches.map { it.toSearchResult() })
    }

    override suspend fun trending(limit: Int): PagedResults =
        PagedResults(items = catalogue.take(limit).map { it.toSearchResult() })

    /** Fetch a URL and convert the main content into Markdown for offline reading. */
    suspend fun fetchAsMarkdown(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Fetch failed (${resp.code}) for $url")
            val html = resp.body?.string().orEmpty()
            htmlToMarkdown(Jsoup.parse(html, url))
        }
    }

    /** Detect which kind of docs site this is, based on host/path. */
    fun detectKind(url: String): SourceKind {
        val host = url.lowercase()
        return when {
            "gitbook.io" in host || "/gitbook/" in host -> SourceKind.GITBOOK
            "docusaurus" in host || "docusaurus.io" in host -> SourceKind.DOCUSAURUS
            "readthedocs" in host -> SourceKind.READ_THE_DOCS
            else -> SourceKind.READ_THE_DOCS // generic
        }
    }

    private fun DocCatalogueEntry.toSearchResult() = SearchResult(
        ref = DocumentRef(
            source = detectKind(url),
            externalId = url,
            title = title,
            subtitle = author,
            author = author,
            sourceUrl = url,
            downloadUrl = url,
            format = DocFormat.MARKDOWN,
            tags = tags,
        )
    )
}

data class DocCatalogueEntry(
    val title: String,
    val author: String,
    val url: String,
    val tags: List<String>,
)

/**
 * Lightweight HTML→Markdown converter that pulls `<main>` / `<article>` / `<body>`
 * and walks the DOM. Not a full HTML→MD engine; good enough for offline reading.
 */
internal fun htmlToMarkdown(doc: Document): String {
    val root = doc.selectFirst("main, article, [role=main], .document, .markdown-body, body")
        ?: doc.body() ?: return ""
    return walk(root).trim()
}

private fun walk(node: org.jsoup.nodes.Node, depth: Int = 0): String {
    if (node is org.jsoup.nodes.TextNode) return node.text().replace(WHITESPACE, " ")
    if (node !is org.jsoup.nodes.Element) return ""
    val tag = node.tagName().lowercase()
    val children = node.childNodes().joinToString("") { walk(it, depth) }
    return when (tag) {
        "script", "style", "noscript", "nav", "footer", "header", "aside" -> ""
        "h1" -> "\n\n# $children\n\n"
        "h2" -> "\n\n## $children\n\n"
        "h3" -> "\n\n### $children\n\n"
        "h4" -> "\n\n#### $children\n\n"
        "h5" -> "\n\n##### $children\n\n"
        "h6" -> "\n\n###### $children\n\n"
        "p" -> "\n\n$children\n\n"
        "br" -> "\n"
        "hr" -> "\n\n---\n\n"
        "strong", "b" -> "**$children**"
        "em", "i" -> "*$children*"
        "code" -> if (node.parent()?.tagName() == "pre") children else "`$children`"
        "pre" -> "\n\n```\n$children\n```\n\n"
        "a" -> {
            val href = node.attr("href")
            if (href.isBlank() || children.isBlank()) children
            else "[$children]($href)"
        }
        "ul" -> "\n\n" + node.children().filter { it.tagName() == "li" }
            .joinToString("") { "\n- ${walk(it, depth + 1).trim()}" } + "\n\n"
        "ol" -> "\n\n" + node.children().filter { it.tagName() == "li" }
            .mapIndexed { i, it -> "\n${i + 1}. ${walk(it, depth + 1).trim()}" }
            .joinToString("") + "\n\n"
        "li" -> children
        "img" -> {
            val src = node.attr("src")
            val alt = node.attr("alt")
            if (src.isBlank()) "" else "![${alt}](${src})"
        }
        "blockquote" -> "\n\n" + children.lines().joinToString("\n") { "> $it" } + "\n\n"
        "table" -> {
            // Simplistic: just dump inner text
            "\n\n" + children + "\n\n"
        }
        else -> children
    }
}

private val WHITESPACE = Regex("\\s+")
