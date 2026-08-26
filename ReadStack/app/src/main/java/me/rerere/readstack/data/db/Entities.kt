package me.rerere.readstack.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.readstack.domain.model.Annotation
import me.rerere.readstack.domain.model.AnnotationKind
import me.rerere.readstack.domain.model.DocFormat
import me.rerere.readstack.domain.model.DocumentRef
import me.rerere.readstack.domain.model.LocalDocument
import me.rerere.readstack.domain.model.SourceKind

/* ---------------------------------------------------------------- *
 * DocumentEntity — one row per downloaded document
 * ---------------------------------------------------------------- */
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["source"]),
        Index(value = ["external_id"], unique = true),
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "external_id") val externalId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "subtitle") val subtitle: String?,
    @ColumnInfo(name = "author") val author: String?,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String,
    @ColumnInfo(name = "download_url") val downloadUrl: String,
    @ColumnInfo(name = "format") val format: String,
    @ColumnInfo(name = "language") val language: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "local_path") val localPath: String,
    @ColumnInfo(name = "added_at") val addedAtEpochMs: Long,
    @ColumnInfo(name = "last_read_at") val lastReadAtEpochMs: Long?,
    @ColumnInfo(name = "last_read_position") val lastReadPosition: Int = 0,
    @ColumnInfo(name = "read_progress") val readProgress: Float = 0f,
    @ColumnInfo(name = "tags") val tagsJson: String = "[]",
) {
    fun toLocalDocument(): LocalDocument = LocalDocument(
        id = id,
        ref = DocumentRef(
            source = SourceKind.valueOf(source),
            externalId = externalId,
            title = title,
            subtitle = subtitle,
            author = author,
            coverUrl = coverUrl,
            description = description,
            sourceUrl = sourceUrl,
            downloadUrl = downloadUrl,
            format = DocFormat.valueOf(format),
            language = language,
            tags = runCatching {
                Json.decodeFromString<List<String>>(tagsJson)
            }.getOrDefault(emptyList())
        ),
        localPath = localPath,
        sizeBytes = sizeBytes,
        addedAtEpochMs = addedAtEpochMs,
        lastReadAtEpochMs = lastReadAtEpochMs,
        lastReadPosition = lastReadPosition,
        readProgress = readProgress,
    )

    companion object {
        fun fromRef(ref: DocumentRef, localPath: String, sizeBytes: Long, addedAt: Long) =
            DocumentEntity(
                source = ref.source.name,
                externalId = ref.externalId,
                title = ref.title,
                subtitle = ref.subtitle,
                author = ref.author,
                coverUrl = ref.coverUrl,
                description = ref.description,
                sourceUrl = ref.sourceUrl,
                downloadUrl = ref.downloadUrl,
                format = ref.format.name,
                language = ref.language,
                sizeBytes = sizeBytes,
                localPath = localPath,
                addedAtEpochMs = addedAt,
                tagsJson = Json.encodeToString(ref.tags),
            )
    }
}

/* ---------------------------------------------------------------- *
 * FTS4 virtual table — full-text search across downloaded content
 * Mapped 1:1 to DocumentEntity (contentEntity = DocumentEntity::class).
 * Columns MUST be a subset of DocumentEntity's columns.
 * We index title, author, description.
 * ---------------------------------------------------------------- */
@Fts4(contentEntity = DocumentEntity::class)
@Entity(tableName = "documents_fts")
data class DocumentFtsEntity(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String?,
    @ColumnInfo(name = "description") val description: String?,
)

/* ---------------------------------------------------------------- *
 * AnnotationEntity — highlights / notes / bookmarks
 * ---------------------------------------------------------------- */
@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("document_id")]
)
data class AnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "location") val location: Int,
    @ColumnInfo(name = "length") val length: Int,
    @ColumnInfo(name = "color") val color: Int,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "created_at") val createdAtEpochMs: Long,
) {
    fun toDomain() = Annotation(
        id = id,
        documentId = documentId,
        kind = AnnotationKind.valueOf(kind),
        location = location,
        length = length,
        color = color,
        text = text,
        note = note,
        createdAtEpochMs = createdAtEpochMs,
    )
}
