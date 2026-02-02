package com.niquewrld.apktools.ui.detail

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.niquewrld.apktools.R
import com.niquewrld.apktools.data.model.DecompileResult
import com.niquewrld.apktools.data.repository.FileRepository
import com.niquewrld.apktools.databinding.ActivityAppDetailBinding
import com.niquewrld.apktools.databinding.DialogProgressBinding
import com.niquewrld.apktools.domain.DecompileState
import com.niquewrld.apktools.domain.SmaliDecompiler
import com.niquewrld.apktools.ui.browser.FileBrowserActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppDetailActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AppDetailActivity"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_VERSION_NAME = "version_name"
        const val EXTRA_VERSION_CODE = "version_code"
        const val EXTRA_APK_PATH = "apk_path"
        const val EXTRA_APK_SIZE = "apk_size"
    }
    
    private lateinit var binding: ActivityAppDetailBinding
    private lateinit var fileRepository: FileRepository
    
    private var appPackageName: String = ""
    private var appDisplayName: String = ""
    private var appVersionName: String = ""
    private var appVersionCode: Long = 0
    private var appApkPath: String = ""
    private var appApkSize: Long = 0
    private var appIconDrawable: Drawable? = null
    
    private var decompileJob: Job? = null
    private var progressDialog: AlertDialog? = null
    private var pendingAction: (() -> Unit)? = null
    
    // Store permissions for dialog
    private var allPermissions: Array<String> = emptyArray()
    private var allPermissionFlags: IntArray = IntArray(0)
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingAction?.invoke()
        } else {
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show()
        }
        pendingAction = null
    }
    
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            pendingAction?.invoke()
        } else {
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_LONG).show()
        }
        pendingAction = null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        fileRepository = FileRepository(applicationContext)
        
        extractIntentData()
        setupToolbar()
        setupViews()
        setupButtons()
        updateDecompileStatus()
    }
    
    private fun extractIntentData() {
        appPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }
        appDisplayName = intent.getStringExtra(EXTRA_APP_NAME) ?: appPackageName
        appVersionName = intent.getStringExtra(EXTRA_VERSION_NAME) ?: "Unknown"
        appVersionCode = intent.getLongExtra(EXTRA_VERSION_CODE, 0)
        appApkPath = intent.getStringExtra(EXTRA_APK_PATH) ?: ""
        appApkSize = intent.getLongExtra(EXTRA_APK_SIZE, 0)
        
        // Load app icon from package manager
        try {
            appIconDrawable = packageManager.getApplicationIcon(appPackageName)
        } catch (e: Exception) {
            // Use default icon
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = appDisplayName
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
    
    private fun setupViews() {
        binding.apply {
            appIconDrawable?.let { appIcon.setImageDrawable(it) }
                ?: appIcon.setImageResource(R.mipmap.ic_launcher)
            
            appName.text = appDisplayName
            packageName.text = appPackageName
            versionName.text = appVersionName
            versionCode.text = appVersionCode.toString()
            apkSize.text = String.format("%.1f MB", appApkSize / (1024.0 * 1024.0))
            apkPath.text = appApkPath
        }
        
        loadExtendedAppInfo()
    }
    
    private fun loadExtendedAppInfo() {
        try {
            val flags = PackageManager.GET_PERMISSIONS or 
                        PackageManager.GET_ACTIVITIES or 
                        PackageManager.GET_SERVICES or 
                        PackageManager.GET_RECEIVERS or 
                        PackageManager.GET_PROVIDERS
            
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(appPackageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(appPackageName, flags)
            }
            
            val appInfo = packageInfo.applicationInfo
            
            setupSdkAndDates(packageInfo, appInfo)
            setupAppFlags(appInfo)
            setupComponents(packageInfo)
            setupPermissions(packageInfo)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupSdkAndDates(packageInfo: PackageInfo, appInfo: ApplicationInfo?) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        
        binding.apply {
            // SDK versions
            val minSdkVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo?.minSdkVersion ?: 1
            } else {
                1
            }
            minSdk.text = "$minSdkVersion (${getAndroidVersionName(minSdkVersion)})"
            targetSdk.text = "${appInfo?.targetSdkVersion ?: 0} (${getAndroidVersionName(appInfo?.targetSdkVersion ?: 0)})"
            
            // Dates
            installDate.text = dateFormat.format(Date(packageInfo.firstInstallTime))
            updateDate.text = dateFormat.format(Date(packageInfo.lastUpdateTime))
        }
    }
    
    private fun setupAppFlags(appInfo: ApplicationInfo?) {
        binding.flagsChipGroup.removeAllViews()
        
        if (appInfo == null) return
        
        val flags = mutableListOf<Pair<String, Int>>()
        
        // Check various app flags
        if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
            flags.add("System App" to android.R.color.holo_blue_dark)
        } else {
            flags.add("User App" to android.R.color.holo_green_dark)
        }
        
        if (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            flags.add("Debuggable" to android.R.color.holo_orange_dark)
        }
        
        if (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0) {
            flags.add("Updated System" to android.R.color.holo_purple)
        }
        
        if (appInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE != 0) {
            flags.add("External Storage" to android.R.color.darker_gray)
        }
        
        if (appInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP != 0) {
            flags.add("Large Heap" to android.R.color.darker_gray)
        }
        
        flags.forEach { (label, colorRes) ->
            val chip = Chip(this).apply {
                text = label
                isClickable = false
                setChipBackgroundColorResource(colorRes)
                setTextColor(ContextCompat.getColor(this@AppDetailActivity, android.R.color.white))
            }
            binding.flagsChipGroup.addView(chip)
        }
    }
    
    private fun setupComponents(packageInfo: PackageInfo) {
        binding.apply {
            activitiesCount.text = (packageInfo.activities?.size ?: 0).toString()
            servicesCount.text = (packageInfo.services?.size ?: 0).toString()
            receiversCount.text = (packageInfo.receivers?.size ?: 0).toString()
            providersCount.text = (packageInfo.providers?.size ?: 0).toString()
        }
    }
    
    private fun setupPermissions(packageInfo: PackageInfo) {
        allPermissions = packageInfo.requestedPermissions ?: emptyArray()
        allPermissionFlags = packageInfo.requestedPermissionsFlags ?: IntArray(0)
        
        binding.permissionsCount.text = "${allPermissions.size} permissions"
        binding.permissionsPreview.removeAllViews()
        
        // Show only first 3 permissions as preview
        val previewCount = minOf(3, allPermissions.size)
        for (i in 0 until previewCount) {
            val permission = allPermissions[i]
            val isGranted = if (i < allPermissionFlags.size) {
                allPermissionFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            } else {
                false
            }
            
            val simpleName = permission.substringAfterLast(".")
            val textView = android.widget.TextView(this).apply {
                text = "• $simpleName"
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@AppDetailActivity, 
                    if (isGranted) android.R.color.holo_green_dark else android.R.color.darker_gray))
                setPadding(0, 4, 0, 4)
            }
            binding.permissionsPreview.addView(textView)
        }
        
        // Show "View All" button only if there are more than 3 permissions
        binding.viewPermissionsButton.visibility = if (allPermissions.size > 3) View.VISIBLE else View.GONE
        binding.viewPermissionsButton.setOnClickListener { showPermissionsDialog() }
    }
    
    private fun showPermissionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_permissions, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.permissionsRecyclerView)
        val closeButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeButton)
        val titleView = dialogView.findViewById<android.widget.TextView>(R.id.dialogTitle)
        
        titleView.text = "${allPermissions.size} Permissions"
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = PermissionsAdapter(allPermissions, allPermissionFlags)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        closeButton.setOnClickListener { dialog.dismiss() }
        
        dialog.show()
        
        // Set dialog width
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }
    
    // Inner adapter class for permissions
    private inner class PermissionsAdapter(
        private val permissions: Array<String>,
        private val flags: IntArray
    ) : RecyclerView.Adapter<PermissionsAdapter.ViewHolder>() {
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameText: android.widget.TextView = itemView.findViewById(R.id.permissionName)
            val fullNameText: android.widget.TextView = itemView.findViewById(R.id.permissionFullName)
            val statusText: android.widget.TextView = itemView.findViewById(R.id.permissionStatus)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_permission, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val permission = permissions[position]
            val isGranted = if (position < flags.size) {
                flags[position] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            } else {
                false
            }
            
            holder.nameText.text = permission.substringAfterLast(".")
            holder.fullNameText.text = permission
            holder.statusText.text = if (isGranted) "✓ Granted" else "✗ Not Granted"
            holder.statusText.setTextColor(ContextCompat.getColor(this@AppDetailActivity,
                if (isGranted) android.R.color.holo_green_dark else android.R.color.holo_red_light))
        }
        
        override fun getItemCount() = permissions.size
    }
    
    private fun getAndroidVersionName(sdk: Int): String {
        return when (sdk) {
            1 -> "Android 1.0"
            2 -> "Android 1.1"
            3 -> "Android 1.5"
            4 -> "Android 1.6"
            5 -> "Android 2.0"
            6 -> "Android 2.0.1"
            7 -> "Android 2.1"
            8 -> "Android 2.2"
            9 -> "Android 2.3"
            10 -> "Android 2.3.3"
            11 -> "Android 3.0"
            12 -> "Android 3.1"
            13 -> "Android 3.2"
            14 -> "Android 4.0"
            15 -> "Android 4.0.3"
            16 -> "Android 4.1"
            17 -> "Android 4.2"
            18 -> "Android 4.3"
            19 -> "Android 4.4"
            20 -> "Android 4.4W"
            21 -> "Android 5.0"
            22 -> "Android 5.1"
            23 -> "Android 6.0"
            24 -> "Android 7.0"
            25 -> "Android 7.1"
            26 -> "Android 8.0"
            27 -> "Android 8.1"
            28 -> "Android 9"
            29 -> "Android 10"
            30 -> "Android 11"
            31 -> "Android 12"
            32 -> "Android 12L"
            33 -> "Android 13"
            34 -> "Android 14"
            35 -> "Android 15"
            else -> "Android"
        }
    }
    
    private fun setupButtons() {
        binding.apply {
            decompileButton.setOnClickListener { startDecompilation() }
            browseButton.setOnClickListener { openFileBrowser() }
            exportZipButton.setOnClickListener { requestStoragePermission { exportToZip() } }
            showZipInFolderButton.setOnClickListener { showZipInFolder() }
            extractApkButton.setOnClickListener { requestStoragePermission { extractApk() } }
            showApkInFolderButton.setOnClickListener { showApkInFolder() }
        }
        updateExtractedApkStatus()
        updateExportedZipStatus()
    }
    
    private fun updateDecompileStatus() {
        val isDecompiled = fileRepository.isDecompiled(appPackageName)
        
        binding.apply {
            if (isDecompiled) {
                statusIcon.visibility = View.VISIBLE
                statusIcon.setColorFilter(ContextCompat.getColor(this@AppDetailActivity, android.R.color.holo_green_dark))
                statusText.text = "Decompiled"
                statusText.setTextColor(ContextCompat.getColor(this@AppDetailActivity, android.R.color.holo_green_dark))
                
                decompileButton.text = getString(R.string.redecompile)
                browseButton.visibility = View.VISIBLE
                // Export ZIP visibility is handled by updateExportedZipStatus
            } else {
                statusIcon.visibility = View.GONE
                statusText.text = "Not decompiled"
                statusText.setTextColor(ContextCompat.getColor(this@AppDetailActivity, android.R.color.darker_gray))
                
                decompileButton.text = getString(R.string.decompile)
                browseButton.visibility = View.GONE
                exportZipButton.visibility = View.GONE
                showZipInFolderButton.visibility = View.GONE
            }
        }
        updateExtractedApkStatus()
        updateExportedZipStatus()
    }
    
    private fun updateExtractedApkStatus() {
        val extractedApk = fileRepository.getExtractedApkFile(appPackageName)
        binding.apply {
            if (extractedApk != null) {
                extractApkButton.visibility = View.GONE
                showApkInFolderButton.visibility = View.VISIBLE
            } else {
                extractApkButton.visibility = View.VISIBLE
                showApkInFolderButton.visibility = View.GONE
            }
        }
    }
    
    private fun updateExportedZipStatus() {
        val isDecompiled = fileRepository.isDecompiled(appPackageName)
        if (!isDecompiled) return
        
        val exportedZip = fileRepository.getExportedZipFile(appPackageName)
        binding.apply {
            if (exportedZip != null) {
                exportZipButton.visibility = View.GONE
                showZipInFolderButton.visibility = View.VISIBLE
            } else {
                exportZipButton.visibility = View.VISIBLE
                showZipInFolderButton.visibility = View.GONE
            }
        }
    }
    
    private fun startDecompilation() {
        Log.d(TAG, "startDecompilation() called")
        Log.d(TAG, "Package: $appPackageName")
        Log.d(TAG, "APK Path: $appApkPath")
        Log.d(TAG, "APK Size: $appApkSize bytes")
        
        val apkSizeMB = appApkSize / (1024.0 * 1024.0)
        Log.d(TAG, "APK Size: $apkSizeMB MB")
        
        if (apkSizeMB > 100) {
            Log.w(TAG, "APK too large, aborting decompilation")
            Toast.makeText(this, R.string.error_too_large, Toast.LENGTH_LONG).show()
            return
        }
        
        val dialogBinding = DialogProgressBinding.inflate(layoutInflater)
        
        progressDialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        dialogBinding.cancelButton.setOnClickListener {
            Log.d(TAG, "Decompilation cancelled by user")
            decompileJob?.cancel()
            progressDialog?.dismiss()
        }
        
        progressDialog?.show()
        Log.d(TAG, "Progress dialog shown, starting decompilation...")
        
        val decompiler = SmaliDecompiler(this, fileRepository)
        val appInfo = com.niquewrld.apktools.data.model.AppInfo(
            packageName = appPackageName,
            appName = appDisplayName,
            versionName = appVersionName,
            versionCode = appVersionCode,
            apkPath = appApkPath,
            apkSize = appApkSize,
            icon = appIconDrawable
        )
        
        decompileJob = lifecycleScope.launch {
            Log.d(TAG, "Decompile coroutine started")
            decompiler.decompile(appInfo).collectLatest { state ->
                when (state) {
                    is DecompileState.Starting -> {
                        Log.d(TAG, "Decompile state: Starting")
                        dialogBinding.progressText.text = "Starting..."
                        dialogBinding.progressBar.progress = 0
                    }
                    is DecompileState.Progress -> {
                        Log.v(TAG, "Decompile progress: ${state.progress.percentage}% - ${state.progress.currentFile}")
                        dialogBinding.progressText.text = state.progress.currentFile
                        dialogBinding.progressBar.progress = state.progress.percentage
                    }
                    is DecompileState.Finished -> {
                        Log.d(TAG, "Decompile state: Finished")
                        progressDialog?.dismiss()
                        handleDecompileResult(state.result)
                    }
                }
            }
        }
    }
    
    private fun handleDecompileResult(result: DecompileResult) {
        Log.d(TAG, "handleDecompileResult: $result")
        when (result) {
            is DecompileResult.Success -> {
                Log.i(TAG, "Decompilation SUCCESS - Output: ${result.outputDir}")
                Toast.makeText(this, R.string.success_decompile, Toast.LENGTH_SHORT).show()
                updateDecompileStatus()
            }
            is DecompileResult.Error -> {
                Log.e(TAG, "Decompilation ERROR: ${result.message}")
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
            is DecompileResult.Cancelled -> {
                Log.d(TAG, "Decompilation CANCELLED")
                // User cancelled
            }
        }
    }
    
    private fun openFileBrowser() {
        val intent = Intent(this, FileBrowserActivity::class.java).apply {
            putExtra(FileBrowserActivity.EXTRA_PACKAGE_NAME, appPackageName)
            putExtra(FileBrowserActivity.EXTRA_APP_NAME, appDisplayName)
        }
        startActivity(intent)
    }
    
    private fun requestStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                action()
            } else {
                pendingAction = action
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED) {
                action()
            } else {
                pendingAction = action
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
    
    private fun exportToZip() {
        lifecycleScope.launch {
            Toast.makeText(this@AppDetailActivity, R.string.exporting, Toast.LENGTH_SHORT).show()
            val zipPath = fileRepository.exportToZip(appPackageName, appDisplayName)
            if (zipPath != null) {
                Toast.makeText(this@AppDetailActivity, "Exported to: $zipPath", Toast.LENGTH_LONG).show()
                updateExportedZipStatus()
            } else {
                Toast.makeText(this@AppDetailActivity, R.string.error_export, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun extractApk() {
        lifecycleScope.launch {
            Toast.makeText(this@AppDetailActivity, R.string.extracting_apk, Toast.LENGTH_SHORT).show()
            val extractedPath = fileRepository.extractApk(appPackageName, appApkPath)
            if (extractedPath != null) {
                Toast.makeText(this@AppDetailActivity, "APK extracted to: $extractedPath", Toast.LENGTH_LONG).show()
                updateExtractedApkStatus()
            } else {
                Toast.makeText(this@AppDetailActivity, R.string.error_extract, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showApkInFolder() {
        val extractedApk = fileRepository.getExtractedApkFile(appPackageName)
        if (extractedApk != null) {
            val folderUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2FAPKTools")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, "vnd.android.document/directory")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback: Try opening Downloads folder
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary:Download"), "vnd.android.document/directory")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "APK location: ${extractedApk.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showZipInFolder() {
        val exportedZip = fileRepository.getExportedZipFile(appPackageName)
        if (exportedZip != null) {
            val folderUri = Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2FAPKTools")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, "vnd.android.document/directory")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback: Try opening Downloads folder
                try {
                    val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary:Download"), "vnd.android.document/directory")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    Toast.makeText(this, "ZIP location: ${exportedZip.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateDecompileStatus()
        
        // Check if permission was granted in settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
            Environment.isExternalStorageManager() && 
            pendingAction != null) {
            pendingAction?.invoke()
            pendingAction = null
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        decompileJob?.cancel()
        progressDialog?.dismiss()
    }
}
