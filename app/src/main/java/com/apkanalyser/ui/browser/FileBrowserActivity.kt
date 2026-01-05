package com.apkanalyser.ui.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.apkanalyser.data.model.FileItem
import com.apkanalyser.databinding.ActivityFileBrowserBinding
import com.apkanalyser.ui.viewer.CodeViewerActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class FileBrowserActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFileBrowserBinding
    private val viewModel: FileBrowserViewModel by viewModels()
    private lateinit var adapter: FileListAdapter
    
    private lateinit var packageName: String
    private lateinit var appName: String
    
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
        setupRecyclerView()
        setupBackNavigation()
        observeViewModel()
        
        viewModel.loadDirectory(this, packageName)
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = appName
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }
    
    private fun setupRecyclerView() {
        adapter = FileListAdapter { fileItem ->
            if (fileItem.isDirectory) {
                viewModel.navigateToDirectory(fileItem.file)
            } else {
                openCodeViewer(fileItem.file)
            }
        }
        
        binding.fileList.layoutManager = LinearLayoutManager(this)
        binding.fileList.adapter = adapter
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
