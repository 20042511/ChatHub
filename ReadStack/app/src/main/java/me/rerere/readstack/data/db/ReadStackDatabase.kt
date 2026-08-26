package me.rerere.readstack.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentEntity::class,
        DocumentFtsEntity::class,
        AnnotationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ReadStackDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun annotationDao(): AnnotationDao
}
