package me.rerere.readstack.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY added_at DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE external_id = :externalId LIMIT 1")
    suspend fun findByExternalId(externalId: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DocumentEntity): Long

    @Update
    suspend fun update(entity: DocumentEntity)

    @Query("UPDATE documents SET last_read_at = :ts, last_read_position = :pos, read_progress = :progress WHERE id = :id")
    suspend fun updateReadProgress(id: Long, pos: Int, progress: Float, ts: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM documents")
    fun observeCount(): Flow<Int>
}

@Dao
interface AnnotationDao {

    @Query("SELECT * FROM annotations WHERE document_id = :documentId ORDER BY location ASC")
    fun observeForDocument(documentId: Long): Flow<List<AnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AnnotationEntity): Long

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM annotations WHERE document_id = :documentId")
    suspend fun deleteAllForDocument(documentId: Long)
}
