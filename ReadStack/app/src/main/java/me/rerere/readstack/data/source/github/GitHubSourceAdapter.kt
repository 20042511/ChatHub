package me.rerere.readstack.data.source.github

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.readstack.data.source.DocumentSource
import me.rerere.readstack.domain.model.DocFormat
import me.rerere.readstack.domain.model.DocumentRef
import me.rerere.readstack.domain.model.PagedResults
import me.rerere.readstack.domain.model.SearchResult
import me.rerere.readstack.domain.model.SourceKind
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Talks to the public GitHub REST API to find repos and their README.
 * No auth required for low rate limits; we don't try to do large batch ops.
 */
class GitHubSourceAdapter(client: OkHttpClient, json: Json) : DocumentSource {

    override val name: String = "GitHub"

    private val api: GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GitHubApi::class.java)

    private val rawHttp = client

    override suspend fun search(query: String, page: Int): PagedResults {
        val resp = api.searchRepos(query = query, page = page, perPage = PER_PAGE)
        return PagedResults(
            items = resp.items.map { it.toSearchResult() },
            total = resp.totalCount,
            nextPage = if (resp.items.size == PER_PAGE) page + 1 else null,
        )
    }

    override suspend fun trending(limit: Int): PagedResults {
        val resp = api.searchRepos(query = "stars:>1000", page = 1, perPage = limit)
        return PagedResults(
            items = resp.items.map { it.toSearchResult() },
            total = resp.totalCount,
        )
    }

    /** Fetch the raw README.md bytes for a given owner/repo. */
    suspend fun fetchReadme(owner: String, repo: String): String {
        val meta = api.getRepo(owner, repo)
        val url = "https://raw.githubusercontent.com/$owner/$repo/${meta.defaultBranch}/README.md"
        val req = Request.Builder().url(url).build()
        return rawHttp.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("README fetch failed: ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    private fun GhRepo.toSearchResult() = SearchResult(
        ref = DocumentRef(
            source = SourceKind.GITHUB,
            externalId = "$ownerLogin/$name",
            title = fullName,
            subtitle = description,
            author = ownerLogin,
            coverUrl = ownerAvatarUrl,
            description = description,
            sourceUrl = htmlUrl,
            downloadUrl = "https://raw.githubusercontent.com/$ownerLogin/$name/$defaultBranch/README.md",
            format = DocFormat.MARKDOWN,
            language = language ?: "en",
            tags = listOfNotNull(language, "github").distinct(),
        ),
        score = stargazersCount.toDouble(),
    )

    companion object { private const val PER_PAGE = 30 }
}

interface GitHubApi {
    @GET("search/repositories")
    suspend fun searchRepos(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
    ): GhSearchResponse

    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GhRepo
}

@Serializable
data class GhSearchResponse(
    val total_count: Int = 0,
    val items: List<GhRepo> = emptyList(),
) {
    val totalCount: Int get() = total_count
}

@Serializable
data class GhRepo(
    val name: String,
    val full_name: String,
    val description: String? = null,
    val html_url: String,
    val stargazers_count: Int = 0,
    val language: String? = null,
    val default_branch: String = "main",
    val owner: GhOwner,
) {
    val fullName: String get() = full_name
    val htmlUrl: String get() = html_url
    val stargazersCount: Int get() = stargazers_count
    val defaultBranch: String get() = default_branch
    val ownerLogin: String get() = owner.login
    val ownerAvatarUrl: String get() = owner.avatarUrl
}

@Serializable
data class GhOwner(
    val login: String,
    val avatar_url: String,
) {
    val avatarUrl: String get() = avatar_url
}
