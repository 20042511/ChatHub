package me.rerere.readstack.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.readstack.data.repo.LibraryRepository
import javax.inject.Inject

data class SettingsUiState(
    val docCount: Int = 0,
    val totalBytes: Long = 0L,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: LibraryRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = repo.observeLibrary()
        .map { list -> SettingsUiState(docCount = list.size, totalBytes = list.sumOf { it.sizeBytes }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun clearCache(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dir = java.io.File(context.filesDir, "library")
                dir.walkBottomUp().forEach { it.delete() }
            }
        }
    }
}
