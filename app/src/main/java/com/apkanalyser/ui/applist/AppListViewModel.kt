package com.apkanalyser.ui.applist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkanalyser.data.model.AppInfo
import com.apkanalyser.data.repository.AppRepository
import com.apkanalyser.data.repository.FileRepository
import com.apkanalyser.domain.DecompileState
import com.apkanalyser.domain.SmaliDecompiler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppListViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()
    
    private var appRepository: AppRepository? = null
    private var fileRepository: FileRepository? = null
    
    fun loadApps(context: Context) {
        initRepositories(context)
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val apps = appRepository?.getInstalledApps() ?: emptyList()
            val decompiledPackages = getDecompiledPackages(apps)
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    apps = apps,
                    decompiledPackages = decompiledPackages
                )
            }
        }
    }
    
    fun searchApps(context: Context, query: String) {
        initRepositories(context)
        
        viewModelScope.launch {
            val apps = appRepository?.searchApps(query) ?: emptyList()
            val decompiledPackages = getDecompiledPackages(apps)
            
            _uiState.update { 
                it.copy(
                    apps = apps,
                    decompiledPackages = decompiledPackages
                )
            }
        }
    }
    
    fun decompile(context: Context, appInfo: AppInfo): Flow<DecompileState> {
        initRepositories(context)
        val decompiler = SmaliDecompiler(context, fileRepository!!)
        return decompiler.decompile(appInfo)
    }
    
    fun refreshDecompiledStatus(context: Context) {
        initRepositories(context)
        
        viewModelScope.launch {
            val decompiledPackages = getDecompiledPackages(_uiState.value.apps)
            _uiState.update { it.copy(decompiledPackages = decompiledPackages) }
        }
    }
    
    suspend fun exportToZip(context: Context, appInfo: AppInfo): String? {
        initRepositories(context)
        return fileRepository?.exportToZip(appInfo.packageName, appInfo.appName)
    }
    
    private fun initRepositories(context: Context) {
        if (appRepository == null) {
            appRepository = AppRepository(context.applicationContext)
        }
        if (fileRepository == null) {
            fileRepository = FileRepository(context.applicationContext)
        }
    }
    
    private fun getDecompiledPackages(apps: List<AppInfo>): Set<String> {
        return apps
            .filter { fileRepository?.isDecompiled(it.packageName) == true }
            .map { it.packageName }
            .toSet()
    }
}

data class AppListUiState(
    val isLoading: Boolean = false,
    val apps: List<AppInfo> = emptyList(),
    val decompiledPackages: Set<String> = emptySet()
)
