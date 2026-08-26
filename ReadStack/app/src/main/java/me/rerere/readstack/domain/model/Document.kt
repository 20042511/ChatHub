package me.rerere.readstack.domain.model

import kotlinx.serialization.Serializable

/**
 * Where a document comes from. Drives both search adapter logic and the
 * detail page's download strategy.
 */
@Serializable
enum class SourceKind {
    GITHUB,
    READ_THE_DOCS,
    GITBOOK,
    DOCUSAURUS,
    GUTENBERG,
    WEB_SEARCH,
}

/**
 * A reference to a document/book found in any source.
 * The same model is used for "in the wild" results and "already in library" items;
 * the latter will have a non-null [localId] / [localPath].
 */
@Serializable
data class DocumentRef(
    val source: SourceKind,
    val externalId: String,         // e.g. GitHub "owner/repo", Gutenberg numeric id, URL hash
    val title: String,
    val subtitle: String? = null,   // author / short description
    val author: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val sourceUrl: String,          // canonical webpage for this doc
    val downloadUrl: String,        // direct file URL (PDF/EPUB/zip) or page URL to scrape
    val format: DocFormat,
    val language: String = "en",
    val sizeBytes: Long? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
enum class DocFormat {
    PDF, EPUB, MARKDOWN, HTML, TEXT;
    val mime: String
        get() = when (this) {
            PDF -> "application/pdf"
            EPUB -> "application/epub+zip"
            MARKDOWN -> "text/markdown"
            HTML -> "text/html"
            TEXT -> "text/plain"
        }
    val extension: String
        get() = name.lowercase()
}

/**
 * A document that has been downloaded to the device.
 */
data class LocalDocument(
    val id: Long,
    val ref: DocumentRef,
    val localPath: String,
    val sizeBytes: Long,
    val addedAtEpochMs: Long,
    val lastReadAtEpochMs: Long?,
    val lastReadPosition: Int = 0,  // page number for PDF, line for text/markdown
    val readProgress: Float = 0f,
)

/**
 * A single highlight or note attached to a local document.
 */
data class Annotation(
    val id: Long,
    val documentId: Long,
    val kind: AnnotationKind,
    val location: Int,              // page for PDF, char offset for text/markdown
    val length: Int = 0,            // selection length; 0 for point annotations
    val color: Int,                 // ARGB
    val text: String,               // selected text or note body
    val note: String? = null,       // extra comment for note kind
    val createdAtEpochMs: Long,
)

enum class AnnotationKind { HIGHLIGHT, NOTE, BOOKMARK }
