package com.apkanalyser.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.apkanalyser.R
import com.apkanalyser.data.model.AppInfo
import com.apkanalyser.databinding.ActivityMainBinding
import com.apkanalyser.databinding.DialogProgressBinding
import com.apkanalyser.ui.applist.AppListAdapter
import com.apkanalyser.ui.applist.AppListViewModel
import com.apkanalyser.ui.browser.FileBrowserActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: AppListViewModel by viewModels()
    private lateinit var adapter: AppListAdapter
    
    private var decompileJob: Job? = null
    private var progressDialog: AlertDialog? = null
    private var pendingExportApp: AppInfo? = null
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingExportApp?.let { exportToZip(it) }
        } else {
            Toast.makeText(this, "Storage permission required to export", Toast.LENGTH_LONG).show()
        }
        pendingExportApp = null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        showDisclaimerOnFirstLaunch()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
        
        viewModel.loadApps(this)
    }
    
    private fun showDisclaimerOnFirstLaunch() {
        val prefs = getSharedPreferences("apk_analyser", MODE_PRIVATE)
        if (!prefs.getBoolean("disclaimer_shown", false)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.disclaimer_title)
                .setMessage(R.string.disclaimer_message)
                .setPositiveButton(R.string.accept) { _, _ ->
                    prefs.edit().putBoolean("disclaimer_shown", true).apply()
                }
                .setCancelable(false)
                .show()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = AppListAdapter(
            onDecompileClick = { app -> startDecompilation(app) },
            onBrowseClick = { app -> openFileBrowser(app) },
            onRedecompileClick = { app -> startDecompilation(app) },
            onExportClick = { app -> requestExport(app) }
        )
        
        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter
    }
    
    private fun setupSearch() {
        binding.searchInput.doAfterTextChanged { text ->
            viewModel.searchApps(this, text?.toString() ?: "")
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                binding.loadingIndicator.visibility = 
                    if (state.isLoading) View.VISIBLE else View.GONE
                binding.emptyText.visibility = 
                    if (!state.isLoading && state.apps.isEmpty()) View.VISIBLE else View.GONE
                binding.appList.visibility = 
                    if (state.apps.isNotEmpty()) View.VISIBLE else View.GONE
                
                adapter.submitList(state.apps, state.decompiledPackages)
            }
        }
    }
    
    private fun startDecompilation(app: AppInfo) {
        if (!app.isDecompilable) {
            Toast.makeText(this, R.string.error_too_large, Toast.LENGTH_LONG).show()
            return
        }
        
        val dialogBinding = DialogProgressBinding.inflate(layoutInflater)
        
        progressDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        dialogBinding.cancelButton.setOnClickListener {
            decompileJob?.cancel()
            progressDialog?.dismiss()
        }
        
        progressDialog?.show()
        
        decompileJob = lifecycleScope.launch {
            viewModel.decompile(this@MainActivity, app).collectLatest { state ->
                when (state) {
                    is com.apkanalyser.domain.DecompileState.Starting -> {
                        dialogBinding.progressText.text = "Starting..."
                        dialogBinding.progressBar.progress = 0
                    }
                    is com.apkanalyser.domain.DecompileState.Progress -> {
                        dialogBinding.progressText.text = state.progress.currentFile
                        dialogBinding.progressBar.progress = state.progress.percentage
                    }
                    is com.apkanalyser.domain.DecompileState.Finished -> {
                        progressDialog?.dismiss()
                        handleDecompileResult(state.result, app)
                    }
                }
            }
        }
    }
    
    private fun handleDecompileResult(
        result: com.apkanalyser.data.model.DecompileResult,
        app: AppInfo
    ) {
        when (result) {
            is com.apkanalyser.data.model.DecompileResult.Success -> {
                Toast.makeText(this, R.string.success_decompile, Toast.LENGTH_SHORT).show()
                viewModel.refreshDecompiledStatus(this)
                openFileBrowser(app)
            }
            is com.apkanalyser.data.model.DecompileResult.Error -> {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
            is com.apkanalyser.data.model.DecompileResult.Cancelled -> {
                // User cancelled, do nothing
            }
        }
    }
    
    private fun openFileBrowser(app: AppInfo) {
        val intent = Intent(this, FileBrowserActivity::class.java).apply {
            putExtra(FileBrowserActivity.EXTRA_PACKAGE_NAME, app.packageName)
            putExtra(FileBrowserActivity.EXTRA_APP_NAME, app.appName)
        }
        startActivity(intent)
    }
    
    private fun requestExport(app: AppInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - check MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                exportToZip(app)
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Storage Permission")
                    .setMessage("To export ZIP files, please grant storage access in settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        pendingExportApp = app
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            // Android 10 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED) {
                exportToZip(app)
            } else {
                pendingExportApp = app
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    
    private fun exportToZip(app: AppInfo) {
        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, R.string.exporting, Toast.LENGTH_SHORT).show()
            val zipPath = viewModel.exportToZip(this@MainActivity, app)
            if (zipPath != null) {
                Toast.makeText(
                    this@MainActivity, 
                    "Exported to: $zipPath", 
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.error_export, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check if permission was granted in settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
            Environment.isExternalStorageManager() && 
            pendingExportApp != null) {
            exportToZip(pendingExportApp!!)
            pendingExportApp = null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        decompileJob?.cancel()
        progressDialog?.dismiss()
    }
}
