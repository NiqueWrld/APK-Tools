package com.niquewrld.apktools.ui.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CodeViewerViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(CodeViewerUiState())
    val uiState: StateFlow<CodeViewerUiState> = _uiState.asStateFlow()
    
    fun loadFile(filePath: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val content = withContext(Dispatchers.IO) {
                try {
                    File(filePath).readText()
                } catch (e: Exception) {
                    "Error loading file: ${e.message}"
                }
            }
            
            _uiState.update {
                it.copy(
                    isLoading = false,
                    content = content
                )
            }
        }
    }
}

data class CodeViewerUiState(
    val isLoading: Boolean = false,
    val content: String = ""
)
