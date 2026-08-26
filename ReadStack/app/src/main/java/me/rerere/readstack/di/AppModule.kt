package me.rerere.readstack.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import me.rerere.readstack.data.db.AnnotationDao
import me.rerere.readstack.data.db.DocumentDao
import me.rerere.readstack.data.db.ReadStackDatabase
import me.rerere.readstack.data.network.NetworkModule
import me.rerere.readstack.data.source.docs.DocsSiteAdapter
import me.rerere.readstack.data.source.github.GitHubSourceAdapter
import me.rerere.readstack.data.source.gutenberg.GutenbergSourceAdapter
import me.rerere.readstack.data.source.search.DuckDuckGoSearchAdapter
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = NetworkModule.buildOkHttp { "" }

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): ReadStackDatabase =
        Room.databaseBuilder(ctx, ReadStackDatabase::class.java, "readstack.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideDocumentDao(db: ReadStackDatabase): DocumentDao = db.documentDao()
    @Provides fun provideAnnotationDao(db: ReadStackDatabase): AnnotationDao = db.annotationDao()

    /* ---- Source adapters ---------------------------------------- */
    @Provides @Singleton
    fun provideGitHub(client: OkHttpClient, json: Json): GitHubSourceAdapter =
        GitHubSourceAdapter(client, json)

    @Provides @Singleton
    fun provideDocs(client: OkHttpClient): DocsSiteAdapter = DocsSiteAdapter(client)

    @Provides @Singleton
    fun provideGutenberg(client: OkHttpClient): GutenbergSourceAdapter =
        GutenbergSourceAdapter(client)

    @Provides @Singleton
    fun provideWebSearch(client: OkHttpClient): DuckDuckGoSearchAdapter =
        DuckDuckGoSearchAdapter(client)
}
