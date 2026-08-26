package me.rerere.readstack.domain.model

/**
 * A lightweight, uniform result item for any of the four sources.
 * Adapters translate source-native objects into this shape.
 */
data class SearchResult(
    val ref: DocumentRef,
    val matchedSnippet: String? = null,   // highlighted excerpt
    val score: Double = 1.0,
)

/**
 * Generic paged search response.
 */
data class PagedResults(
    val items: List<SearchResult>,
    val total: Int? = null,
    val nextPage: Int? = null,
)
