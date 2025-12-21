package com.karatefile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FileIndexViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(FileIndexState())
    val state: StateFlow<FileIndexState> = _state.asStateFlow()

    fun refreshIfNeeded() {
        if (_state.value.categories.isEmpty() && _state.value.permissionGranted) {
            refresh(force = true)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted) }
        if (granted) {
            refresh(force = true)
        }
    }

    fun refresh(force: Boolean = false) {
        if (_state.value.isLoading && !force) return

        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val indexer = FileIndexer(getApplication<Application>().contentResolver)
                val categories = FileCategory.values().associateWith { category ->
                    indexer.loadCategory(category)
                }
                _state.update { it.copy(isLoading = false, categories = categories, errorMessage = null) }
            } catch (exception: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Nie udało się odczytać plików: ${exception.localizedMessage}",
                    )
                }
            }
        }
    }
}
