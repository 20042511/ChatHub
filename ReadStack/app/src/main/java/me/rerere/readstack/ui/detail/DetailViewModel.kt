package me.rerere.readstack.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.readstack.data.repo.LibraryRepository
import me.rerere.readstack.domain.model.DocFormat
import me.rerere.readstack.domain.model.DocumentRef
import me.rerere.readstack.domain.model.SourceKind
import androidx.work.WorkInfo
import javax.inject.Inject

data class DetailUiState(
    val ref: DocumentRef,
    val isAlreadyDownloaded: Boolean = false,
    val localId: Long? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val stage: String? = null,
    val progress: Int = 0,
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Enqueued : DownloadState
    data class Running(val progress: Int) : DownloadState
    data class Failed(val message: String) : DownloadState
    data object Succeeded : DownloadState
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: LibraryRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val ref: DocumentRef = run {
        val source = SourceKind.valueOf(checkNotNull(savedState.get<String>("source")))
        DocumentRef(
            source = source,
            externalId = checkNotNull(savedState.get<String>("externalId")),
            title = checkNotNull(savedState.get<String>("title")),
            subtitle = savedState.get<String>("subtitle"),
            author = savedState.get<String>("author"),
            coverUrl = savedState.get<String>("coverUrl"),
            description = savedState.get<String>("description"),
            sourceUrl = checkNotNull(savedState.get<String>("sourceUrl")),
            downloadUrl = checkNotNull(savedState.get<String>("downloadUrl")),
            format = DocFormat.valueOf(checkNotNull(savedState.get<String>("format"))),
            language = savedState.get<String>("language") ?: "en",
            tags = (savedState.get<String>("tags") ?: "")
                .split(',').filter { it.isNotBlank() },
        )
    }

    private val _state = MutableStateFlow(DetailUiState(ref = ref))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = repo.observeAllAsEntities().map { list ->
                list.firstOrNull { it.externalId == ref.externalId }
            }
            existing.collect { e ->
                _state.update { it.copy(
                    isAlreadyDownloaded = e != null,
                    localId = e?.id,
                ) }
            }
        }
        viewModelScope.launch {
            repo.observeDownload(ref.externalId).collect { infos ->
                val w = infos.firstOrNull() ?: return@collect
                val progress = w.progress.getInt(me.rerere.readstack.data.download.DocumentDownloadWorker.KEY_PROGRESS, 0)
                val stage = w.progress.getString(me.rerere.readstack.data.download.DocumentDownloadWorker.KEY_STAGE)
                val err = w.progress.getString(me.rerere.readstack.data.download.DocumentDownloadWorker.KEY_ERROR)
                _state.update {
                    it.copy(
                        downloadState = when (w.state) {
                            WorkInfo.State.ENQUEUED -> DownloadState.Enqueued
                            WorkInfo.State.RUNNING -> DownloadState.Running(progress)
                            WorkInfo.State.SUCCEEDED -> DownloadState.Succeeded
                            WorkInfo.State.FAILED -> DownloadState.Failed(err ?: "Download failed")
                            WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> DownloadState.Idle
                        },
                        stage = stage,
                        progress = progress,
                    )
                }
            }
        }
    }

    fun startDownload() {
        repo.enqueueDownload(ref)
    }

    fun cancelDownload() {
        repo.cancelDownload(ref.externalId)
    }
}
