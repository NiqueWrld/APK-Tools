package com.niquewrld.apktools.ui.browser

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.niquewrld.apktools.data.model.FileItem
import com.niquewrld.apktools.databinding.ActivityFileBrowserBinding
import com.niquewrld.apktools.ui.viewer.CodeViewerActivity
import com.niquewrld.apktools.ui.viewer.MediaViewerActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class FileBrowserActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFileBrowserBinding
    private val viewModel: FileBrowserViewModel by viewModels()
    private lateinit var adapter: FileListAdapter
    
    private lateinit var packageName: String
    private lateinit var appName: String
    
    // File extensions for different media types
    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
    private val videoExtensions = setOf("mp4", "3gp", "mkv", "webm", "avi", "mov")
    private val audioExtensions = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }
        appName = intent.getStringExtra(EXTRA_APP_NAME) ?: packageName
        
        setupToolbar()
        setupSearchBar()
        setupRecyclerView()
        setupBackNavigation()
        observeViewModel()
        
        viewModel.loadDirectory(this, packageName)
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = appName
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
    
    private fun setupSearchBar() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.search(s?.toString() ?: "")
            }
        })
        
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.search(binding.searchInput.text.toString())
                true
            } else {
                false
            }
        }
    }
    
    private fun setupRecyclerView() {
        adapter = FileListAdapter { fileItem ->
            if (fileItem.isDirectory) {
                binding.searchInput.text?.clear()
                viewModel.navigateToDirectory(fileItem.file)
            } else {
                openFile(fileItem.file)
            }
        }
        
        binding.fileList.layoutManager = LinearLayoutManager(this)
        binding.fileList.adapter = adapter
    }
    
    private fun openFile(file: File) {
        val extension = file.extension.lowercase()
        
        when {
            imageExtensions.contains(extension) -> openMediaViewer(file, MediaViewerActivity.MediaType.IMAGE)
            videoExtensions.contains(extension) -> openMediaViewer(file, MediaViewerActivity.MediaType.VIDEO)
            audioExtensions.contains(extension) -> openMediaViewer(file, MediaViewerActivity.MediaType.AUDIO)
            else -> openCodeViewer(file)
        }
    }
    
    private fun openMediaViewer(file: File, mediaType: String) {
        val intent = Intent(this, MediaViewerActivity::class.java).apply {
            putExtra(MediaViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
            putExtra(MediaViewerActivity.EXTRA_FILE_NAME, file.name)
            putExtra(MediaViewerActivity.EXTRA_MEDIA_TYPE, mediaType)
        }
        startActivity(intent)
    }
    
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!viewModel.navigateUp()) {
                    finish()
                }
            }
        })
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.currentPath.text = state.currentPath
                adapter.submitList(state.files)
                
                // Show/hide search results count
                if (state.isSearching) {
                    binding.searchResultsCount.visibility = View.VISIBLE
                    binding.searchResultsCount.text = "Found ${state.searchResultCount} files"
                } else {
                    binding.searchResultsCount.visibility = View.GONE
                }
            }
        }
    }
    
    private fun openCodeViewer(file: File) {
        val intent = Intent(this, CodeViewerActivity::class.java).apply {
            putExtra(CodeViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
            putExtra(CodeViewerActivity.EXTRA_FILE_NAME, file.name)
        }
        startActivity(intent)
    }
    
    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
    }
}
