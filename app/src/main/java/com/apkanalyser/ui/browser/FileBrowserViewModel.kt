package com.apkanalyser.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkanalyser.data.model.FileItem
import com.apkanalyser.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Stack

class FileBrowserViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()
    
    private var fileRepository: FileRepository? = null
    private var rootDir: File? = null
    private val navigationStack = Stack<File>()
    
    fun loadDirectory(context: Context, packageName: String) {
        if (fileRepository == null) {
            fileRepository = FileRepository(context.applicationContext)
        }
        
        rootDir = fileRepository?.getAppOutputDir(packageName)
        rootDir?.let { navigateToDirectory(it) }
    }
    
    fun navigateToDirectory(directory: File) {
        viewModelScope.launch {
            navigationStack.push(directory)
            val files = fileRepository?.listFiles(directory) ?: emptyList()
            
            _uiState.update {
                it.copy(
                    currentPath = directory.absolutePath,
                    files = files,
                    currentDirectory = directory
                )
            }
        }
    }
    
    fun navigateUp(): Boolean {
        if (navigationStack.size <= 1) return false
        
        navigationStack.pop() // Remove current
        val parent = navigationStack.peek()
        
        viewModelScope.launch {
            val files = fileRepository?.listFiles(parent) ?: emptyList()
            
            _uiState.update {
                it.copy(
                    currentPath = parent.absolutePath,
                    files = files,
                    currentDirectory = parent
                )
            }
        }
        
        return true
    }
}

data class FileBrowserUiState(
    val currentPath: String = "",
    val files: List<FileItem> = emptyList(),
    val currentDirectory: File? = null
)
