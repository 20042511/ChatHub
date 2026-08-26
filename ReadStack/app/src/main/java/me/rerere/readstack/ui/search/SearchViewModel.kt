package me.rerere.readstack.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.readstack.data.repo.LibraryRepository
import me.rerere.readstack.domain.model.LocalDocument
import me.rerere.readstack.domain.model.PagedResults
import me.rerere.readstack.domain.model.SourceKind
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val local: List<LocalDocument> = emptyList(),
    val web: Map<SourceKind, PagedResults> = emptyMap(),
    val isSearching: Boolean = false,
    val scope: SearchScope = SearchScope.WEB,
)

enum class SearchScope { LOCAL, WEB }

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var localJob: Job? = null
    private var webJob: Job? = null

    init {
        observeLocal("")
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        localJob?.cancel()
        webJob?.cancel()
        observeLocal(q)
        if (q.isNotBlank()) searchWeb(q) else _state.update { it.copy(web = emptyMap()) }
    }

    fun setScope(scope: SearchScope) {
        _state.update { it.copy(scope = scope) }
    }

    private fun observeLocal(q: String) {
        localJob = viewModelScope.launch {
            repo.searchLocal(q).collectLatest { docs ->
                _state.update { it.copy(local = docs) }
            }
        }
    }

    private fun searchWeb(q: String) {
        webJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            val results = repo.searchAll(q)
            _state.update { it.copy(web = results, isSearching = false) }
        }
    }
}
