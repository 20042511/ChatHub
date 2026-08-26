package me.rerere.readstack.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.readstack.domain.model.DocumentRef
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public API for the rest of the app: enqueue downloads and observe progress.
 */
@Singleton
class DownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    fun enqueue(ref: DocumentRef): UUID {
        val data = androidx.work.Data.Builder()
            .putString(DocumentDownloadWorker.KEY_REF, json.encodeToString(ref))
            .build()
        val req = OneTimeWorkRequestBuilder<DocumentDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(data)
            .addTag(TAG)
            .addTag("doc:${ref.externalId}")
            .build()
        workManager.enqueueUniqueWork(
            "download-${ref.externalId}",
            ExistingWorkPolicy.KEEP,
            req,
        )
        return req.id
    }

    fun cancel(externalId: String) {
        workManager.cancelUniqueWork("download-$externalId")
    }

    fun observe(externalId: String): Flow<List<WorkInfo>> {
        val tag = "doc:$externalId"
        return workManager.getWorkInfosByTagFlow(tag)
    }

    fun observeAll(): Flow<List<WorkInfo>> {
        return workManager.getWorkInfosByTagFlow(TAG).map { infos ->
            infos.sortedByDescending { it.state.ordinal }
        }
    }

    companion object {
        private const val TAG = "readstack-download"
    }
}
