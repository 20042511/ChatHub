package me.rerere.readstack.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import me.rerere.readstack.data.db.AnnotationDao
import me.rerere.readstack.data.db.AnnotationEntity
import me.rerere.readstack.data.db.DocumentDao
import me.rerere.readstack.data.db.DocumentEntity
import me.rerere.readstack.data.download.DownloadScheduler
import me.rerere.readstack.data.source.docs.DocsSiteAdapter
import me.rerere.readstack.data.source.github.GitHubSourceAdapter
import me.rerere.readstack.data.source.gutenberg.GutenbergSourceAdapter
import me.rerere.readstack.data.source.search.DuckDuckGoSearchAdapter
import me.rerere.readstack.domain.model.Annotation
import me.rerere.readstack.domain.model.AnnotationKind
import me.rerere.readstack.domain.model.DocumentRef
import me.rerere.readstack.domain.model.LocalDocument
import me.rerere.readstack.domain.model.PagedResults
import me.rerere.readstack.domain.model.SearchResult
import me.rerere.readstack.domain.model.SourceKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for the UI layer. Wraps source adapters and Room.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val annotationDao: AnnotationDao,
    private val gitHub: GitHubSourceAdapter,
    private val docs: DocsSiteAdapter,
    private val gutenberg: GutenbergSourceAdapter,
    private val web: DuckDuckGoSearchAdapter,
    private val downloads: DownloadScheduler,
) {
    /* ----- library -------------------------------------------------- */
    fun observeLibrary(): Flow<List<LocalDocument>> =
        documentDao.observeAll().map { list -> list.map { it.toLocalDocument() } }

    fun observeCount(): Flow<Int> = documentDao.observeCount()

    fun observeDocument(id: Long): Flow<LocalDocument?> =
        documentDao.observeById(id).map { it?.toLocalDocument() }

    suspend fun delete(id: Long) = documentDao.deleteById(id)

    suspend fun updateProgress(id: Long, position: Int, progress: Float) =
        documentDao.updateReadProgress(id, position, progress, System.currentTimeMillis())

    /* ----- search --------------------------------------------------- */
    suspend fun search(source: SourceKind, query: String, page: Int = 1): PagedResults =
        when (source) {
            SourceKind.GITHUB -> gitHub.search(query, page)
            SourceKind.GUTENBERG -> gutenberg.search(query, page)
            SourceKind.WEB_SEARCH -> web.search(query, page)
            SourceKind.READ_THE_DOCS, SourceKind.GITBOOK, SourceKind.DOCUSAURUS -> docs.search(query, page)
        }

    suspend fun trending(source: SourceKind, limit: Int = 20): PagedResults =
        when (source) {
            SourceKind.GITHUB -> gitHub.trending(limit)
            SourceKind.GUTENBERG -> gutenberg.trending(limit)
            SourceKind.WEB_SEARCH -> web.trending(limit)
            else -> docs.trending(limit)
        }

    /* ----- search every source in parallel ------------------------- */
    suspend fun searchAll(query: String, page: Int = 1): Map<SourceKind, PagedResults> {
        if (query.isBlank()) return emptyMap()
        val results = listOf(
            SourceKind.GITHUB to runCatching { gitHub.search(query, page) }.getOrElse { PagedResults(emptyList()) },
            SourceKind.GUTENBERG to runCatching { gutenberg.search(query, page) }.getOrElse { PagedResults(emptyList()) },
            SourceKind.WEB_SEARCH to runCatching { web.search(query, page) }.getOrElse { PagedResults(emptyList()) },
            SourceKind.READ_THE_DOCS to runCatching { docs.search(query, page) }.getOrElse { PagedResults(emptyList()) },
        )
        return results.toMap()
    }

    /* ----- local full-text search ---------------------------------- */
    fun observeAllAsEntities(): Flow<List<DocumentEntity>> = documentDao.observeAll()

    fun searchLocal(query: String): Flow<List<LocalDocument>> {
        if (query.isBlank()) return observeLibrary()
        val q = query.trim()
        return documentDao.observeAll().map { all ->
            val needle = q.lowercase()
            all.filter { e ->
                e.title.lowercase().contains(needle) ||
                    (e.author?.lowercase()?.contains(needle) == true) ||
                    (e.description?.lowercase()?.contains(needle) == true) ||
                    e.tagsJson.lowercase().contains(needle)
            }.map { it.toLocalDocument() }
        }
    }

    /* ----- downloads ------------------------------------------------ */
    fun enqueueDownload(ref: DocumentRef) = downloads.enqueue(ref)
    fun cancelDownload(externalId: String) = downloads.cancel(externalId)
    fun observeDownload(externalId: String) = downloads.observe(externalId)
    fun observeAllDownloads() = downloads.observeAll()

    /* ----- annotations --------------------------------------------- */
    fun observeAnnotations(documentId: Long): Flow<List<Annotation>> =
        annotationDao.observeForDocument(documentId).map { list -> list.map { it.toDomain() } }

    suspend fun addHighlight(documentId: Long, location: Int, length: Int, text: String, color: Int) =
        annotationDao.insert(
            AnnotationEntity(
                documentId = documentId,
                kind = AnnotationKind.HIGHLIGHT.name,
                location = location,
                length = length,
                color = color,
                text = text,
                note = null,
                createdAtEpochMs = System.currentTimeMillis(),
            )
        )

    suspend fun addNote(documentId: Long, location: Int, body: String) =
        annotationDao.insert(
            AnnotationEntity(
                documentId = documentId,
                kind = AnnotationKind.NOTE.name,
                location = location,
                length = 0,
                color = 0,
                text = body,
                note = null,
                createdAtEpochMs = System.currentTimeMillis(),
            )
        )

    suspend fun deleteAnnotation(id: Long) = annotationDao.deleteById(id)
}
