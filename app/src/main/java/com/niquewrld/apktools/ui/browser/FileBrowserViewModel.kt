package com.niquewrld.apktools.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niquewrld.apktools.data.model.FileItem
import com.niquewrld.apktools.data.repository.FileRepository
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
    private var currentQuery = ""
    
    fun loadDirectory(context: Context, packageName: String) {
        if (fileRepository == null) {
            fileRepository = FileRepository(context.applicationContext)
        }
        
        rootDir = fileRepository?.getAppOutputDir(packageName)
        rootDir?.let { navigateToDirectory(it) }
    }
    
    fun navigateToDirectory(directory: File) {
        // Clear search when navigating
        currentQuery = ""
        
        viewModelScope.launch {
            navigationStack.push(directory)
            val files = fileRepository?.listFiles(directory) ?: emptyList()
            
            _uiState.update {
                it.copy(
                    currentPath = directory.absolutePath,
                    files = files,
                    currentDirectory = directory,
                    isSearching = false,
                    searchResultCount = 0
                )
            }
        }
    }
    
    fun search(query: String) {
        currentQuery = query
        
        if (query.isEmpty()) {
            // Restore current directory listing
            _uiState.value.currentDirectory?.let { dir ->
                viewModelScope.launch {
                    val files = fileRepository?.listFiles(dir) ?: emptyList()
                    _uiState.update {
                        it.copy(
                            files = files,
                            isSearching = false,
                            searchResultCount = 0
                        )
                    }
                }
            }
            return
        }
        
        viewModelScope.launch {
            val results = searchFilesRecursively(rootDir, query.lowercase())
            _uiState.update {
                it.copy(
                    files = results,
                    isSearching = true,
                    searchResultCount = results.size
                )
            }
        }
    }
    
    private fun searchFilesRecursively(directory: File?, query: String): List<FileItem> {
        if (directory == null || !directory.exists()) return emptyList()
        
        val results = mutableListOf<FileItem>()
        
        directory.walkTopDown().forEach { file ->
            if (file.name.lowercase().contains(query)) {
                results.add(FileItem(file))
            }
        }
        
        return results.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
    
    fun navigateUp(): Boolean {
        // If searching, clear search first
        if (currentQuery.isNotEmpty()) {
            currentQuery = ""
            _uiState.value.currentDirectory?.let { dir ->
                viewModelScope.launch {
                    val files = fileRepository?.listFiles(dir) ?: emptyList()
                    _uiState.update {
                        it.copy(
                            files = files,
                            isSearching = false,
                            searchResultCount = 0
                        )
                    }
                }
            }
            return true
        }
        
        if (navigationStack.size <= 1) return false
        
        navigationStack.pop() // Remove current
        val parent = navigationStack.peek()
        
        viewModelScope.launch {
            val files = fileRepository?.listFiles(parent) ?: emptyList()
            
            _uiState.update {
                it.copy(
                    currentPath = parent.absolutePath,
                    files = files,
                    currentDirectory = parent,
                    isSearching = false,
                    searchResultCount = 0
                )
            }
        }
        
        return true
    }
}

data class FileBrowserUiState(
    val currentPath: String = "",
    val files: List<FileItem> = emptyList(),
    val currentDirectory: File? = null,
    val isSearching: Boolean = false,
    val searchResultCount: Int = 0
)
