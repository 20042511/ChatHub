package me.rerere.readstack.ui

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Library : Route
    @Serializable data object Browse : Route
    @Serializable data object Search : Route
    @Serializable data object Settings : Route

    @Serializable data class SourceDetail(val source: String) : Route
    @Serializable data class Detail(
        val source: String,
        val externalId: String,
        val title: String,
        val subtitle: String? = null,
        val author: String? = null,
        val coverUrl: String? = null,
        val description: String? = null,
        val sourceUrl: String,
        val downloadUrl: String,
        val format: String,
        val language: String = "en",
        val tags: String = "",
    ) : Route

    @Serializable data class Reader(val documentId: Long) : Route
}
