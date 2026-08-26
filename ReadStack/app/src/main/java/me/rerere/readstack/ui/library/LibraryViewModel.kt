package me.rerere.readstack.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.readstack.data.repo.LibraryRepository
import me.rerere.readstack.domain.model.LocalDocument
import javax.inject.Inject

data class LibraryUiState(
    val documents: List<LocalDocument> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: LibraryRepository,
) : ViewModel() {
    val state: StateFlow<LibraryUiState> = repo.observeLibrary()
        .map { LibraryUiState(documents = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
