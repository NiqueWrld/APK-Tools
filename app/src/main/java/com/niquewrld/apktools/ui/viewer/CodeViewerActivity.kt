package com.niquewrld.apktools.ui.viewer

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.niquewrld.apktools.databinding.ActivityCodeViewerBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CodeViewerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCodeViewerBinding
    private val viewModel: CodeViewerViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCodeViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            finish()
            return
        }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Code"
        
        setupToolbar(fileName)
        observeViewModel()
        
        viewModel.loadFile(filePath)
    }
    
    private fun setupToolbar(fileName: String) {
        binding.toolbar.title = fileName
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.codeView.text = state.content
            }
        }
    }
    
    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_NAME = "file_name"
    }
}
