package me.rerere.readstack.data.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.readstack.data.db.DocumentEntity
import me.rerere.readstack.data.db.ReadStackDatabase
import me.rerere.readstack.data.source.docs.DocsSiteAdapter
import me.rerere.readstack.data.source.github.GitHubSourceAdapter
import me.rerere.readstack.data.source.gutenberg.GutenbergSourceAdapter
import me.rerere.readstack.domain.model.DocFormat
import me.rerere.readstack.domain.model.DocumentRef
import me.rerere.readstack.domain.model.SourceKind
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * One worker per download. The class re-dispatches to the right source
 * adapter based on the [SourceKind] so we can implement the right
 * fetch strategy per source (GitHub README, HTML→Markdown scrape, EPUB, etc.).
 */
@HiltWorker
class DocumentDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val http: OkHttpClient,
    private val db: ReadStackDatabase,
    private val json: Json,
    private val gutenberg: GutenbergSourceAdapter,
    private val docs: DocsSiteAdapter,
    private val gitHub: GitHubSourceAdapter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val refJson = inputData.getString(KEY_REF) ?: return Result.failure()
        val ref = json.decodeFromString<DocumentRef>(refJson)

        val docsDir = File(applicationContext.filesDir, "library").apply { mkdirs() }
        val target = File(docsDir, "${ref.source.name.lowercase()}_${sanitize(ref.externalId)}.${ref.format.extension}")

        setProgress(workDataOf(KEY_PROGRESS to 0))

        val fetched = try {
            when (ref.source) {
                SourceKind.GITHUB -> {
                    val parts = ref.externalId.split("/")
                    if (parts.size != 2) error("Bad GitHub id ${ref.externalId}")
                    setProgress(workDataOf(KEY_STAGE to "Fetching README from GitHub…"))
                    gitHub.fetchReadme(parts[0], parts[1])
                }
                SourceKind.GUTENBERG -> {
                    setProgress(workDataOf(KEY_STAGE to "Downloading EPUB from Project Gutenberg…"))
                    downloadBinary(ref.downloadUrl, target)
                    null // binary file, already saved
                }
                SourceKind.WEB_SEARCH, SourceKind.READ_THE_DOCS, SourceKind.GITBOOK, SourceKind.DOCUSAURUS -> {
                    setProgress(workDataOf(KEY_STAGE to "Fetching & converting page…"))
                    docs.fetchAsMarkdown(ref.downloadUrl)
                }
            }
        } catch (e: IOException) {
            Timber.w(e, "Download failed for ${ref.externalId}")
            return Result.retry()
        } catch (t: Throwable) {
            Timber.e(t, "Hard failure for ${ref.externalId}")
            return Result.failure(workDataOf(KEY_ERROR to (t.message ?: "unknown")))
        }

        // Persist to disk
        val sizeBytes: Long = when (ref.format) {
            DocFormat.PDF, DocFormat.EPUB -> {
                target.length()
            }
            DocFormat.MARKDOWN, DocFormat.HTML, DocFormat.TEXT -> {
                target.writeText(fetched ?: "")
                target.length()
            }
        }

        val now = System.currentTimeMillis()
        val entity = DocumentEntity.fromRef(ref, target.absolutePath, sizeBytes, now)
        db.documentDao().insert(entity)

        return Result.success(workDataOf(KEY_LOCAL_PATH to target.absolutePath))
    }

    /**
     * Streams a binary file with periodic progress callbacks.
     * We don't do true resumable downloads in the MVP (would need
     * Range support + persisted offset); we do retry on failure.
     */
    private suspend fun downloadBinary(url: String, target: File): Long {
        val req = Request.Builder().url(url).build()
        return http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} for $url")
            val body = resp.body ?: error("Empty body for $url")
            val total = body.contentLength().takeIf { it > 0 } ?: -1L
            target.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(16 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                            setProgress(workDataOf(KEY_PROGRESS to pct))
                        }
                    }
                    out.flush()
                }
            }
            target.length()
        }
    }

    private fun sanitize(s: String): String =
        s.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)

    companion object {
        const val KEY_REF = "ref"
        const val KEY_PROGRESS = "progress"
        const val KEY_STAGE = "stage"
        const val KEY_ERROR = "error"
        const val KEY_LOCAL_PATH = "local_path"

        fun workDataOf(vararg pairs: Pair<String, Any?>): Data {
            val b = Data.Builder()
            for ((k, v) in pairs) when (v) {
                null -> {}
                is Int -> b.putInt(k, v)
                is Long -> b.putLong(k, v)
                is Float -> b.putFloat(k, v)
                is String -> b.putString(k, v)
                is Boolean -> b.putBoolean(k, v)
            }
            return b.build()
        }
    }
}
