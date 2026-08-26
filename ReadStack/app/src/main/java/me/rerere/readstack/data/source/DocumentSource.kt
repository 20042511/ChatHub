package me.rerere.readstack.data.source

import me.rerere.readstack.domain.model.PagedResults

/**
 * Common contract for any data source that can list / search documents.
 */
interface DocumentSource {
    val name: String
    suspend fun search(query: String, page: Int = 1): PagedResults
    suspend fun trending(limit: Int = 20): PagedResults
}
