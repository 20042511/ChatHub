package me.rerere.readstack.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.readstack.data.repo.LibraryRepository
import me.rerere.readstack.domain.model.PagedResults
import me.rerere.readstack.domain.model.SourceKind
import javax.inject.Inject

data class BrowseUiState(
    val items: List<SourceCategory> = SourceCategory.builtIn,
    val trendingBySource: Map<SourceKind, PagedResults> = emptyMap(),
    val isLoading: Boolean = false,
    val focusedSource: SourceKind? = null,
)

data class SourceCategory(
    val source: SourceKind,
    val title: String,
    val description: String,
    val accent: Long,
) {
    companion object {
        val builtIn = listOf(
            SourceCategory(SourceKind.GITHUB, "GitHub", "Repos, READMEs, releases", 0xFF4F46E5),
            SourceCategory(SourceKind.READ_THE_DOCS, "Official docs", "Read the Docs, GitBook, Docusaurus", 0xFFEA8A14),
            SourceCategory(SourceKind.GUTENBERG, "Project Gutenberg", "70,000+ public-domain ebooks", 0xFF6B73F2),
            SourceCategory(SourceKind.WEB_SEARCH, "The web", "Search anything via DuckDuckGo", 0xFFD64545),
        )
    }
}

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repo: LibraryRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init {
        loadTrendingForAll()
    }

    fun loadTrendingForAll() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val out = SourceCategory.builtIn.associate { cat ->
                cat.source to runCatching {
                    repo.trending(cat.source, limit = 8)
                }.getOrElse { PagedResults(emptyList()) }
            }
            _state.update { it.copy(trendingBySource = out, isLoading = false) }
        }
    }

    fun focus(source: SourceKind) {
        _state.update { it.copy(focusedSource = source) }
    }
}
