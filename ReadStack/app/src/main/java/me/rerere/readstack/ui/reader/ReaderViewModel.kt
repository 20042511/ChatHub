package me.rerere.readstack.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.readstack.data.repo.LibraryRepository
import me.rerere.readstack.domain.model.Annotation
import me.rerere.readstack.domain.model.DocFormat
import me.rerere.readstack.domain.model.LocalDocument
import javax.inject.Inject

data class ReaderUiState(
    val document: LocalDocument? = null,
    val content: String = "",
    val annotations: List<Annotation> = emptyList(),
    val scrollIndex: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repo: LibraryRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val docId: Long = checkNotNull(savedState.get<Long>("documentId"))

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeDocument(docId).collect { doc ->
                if (doc == null) {
                    _state.update { it.copy(isLoading = false) }
                    return@collect
                }
                if (_state.value.document?.id != doc.id) {
                    val content = loadContent(doc)
                    _state.update { it.copy(document = doc, content = content, isLoading = false) }
                } else {
                    _state.update { it.copy(document = doc) }
                }
            }
        }
        viewModelScope.launch {
            repo.observeAnnotations(docId).collect { anns ->
                _state.update { it.copy(annotations = anns) }
            }
        }
    }

    fun onScroll(index: Int, progress: Float) {
        val id = _state.value.document?.id ?: return
        viewModelScope.launch { repo.updateProgress(id, index, progress) }
    }

    fun addHighlight(start: Int, end: Int, text: String) {
        val id = _state.value.document?.id ?: return
        viewModelScope.launch {
            repo.addHighlight(
                documentId = id,
                location = start,
                length = end - start,
                text = text,
                color = 0xFFFFE082.toInt(),
            )
        }
    }

    fun addNote(body: String) {
        val id = _state.value.document?.id ?: return
        viewModelScope.launch { repo.addNote(id, _state.value.scrollIndex, body) }
    }

    fun deleteAnnotation(id: Long) {
        viewModelScope.launch { repo.deleteAnnotation(id) }
    }

    private suspend fun loadContent(doc: LocalDocument): String = withContext(Dispatchers.IO) {
        val file = java.io.File(doc.localPath)
        if (!file.exists()) return@withContext "(file missing on disk)"
        when (doc.ref.format) {
            DocFormat.MARKDOWN, DocFormat.TEXT, DocFormat.HTML -> file.readText()
            DocFormat.PDF -> "(PDF reader — ${doc.ref.title})"
            DocFormat.EPUB -> extractEpubText(file)
        }
    }

    private fun extractEpubText(file: java.io.File): String {
        // Lightweight EPUB → text via regex (no images). We don't ship a full
        // EPUB library in the MVP — this is enough for searching/note-taking.
        return runCatching {
            val raw = file.readText()
            raw.replace(Regex("<[^>]+>"), " ")
                .replace(Regex("&[a-zA-Z]+;"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }.getOrElse { "(unable to parse EPUB — ${it.message})" }
    }
}
