package com.niquewrld.apktools.data.repository

import android.content.Context
import android.os.Environment
import com.niquewrld.apktools.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Repository for managing decompiled Smali file storage
 */
class FileRepository(private val context: Context) {
    
    /**
     * Get the base output directory for all decompiled apps
     */
    fun getOutputBaseDir(): File {
        return File(context.filesDir, OUTPUT_DIR).apply {
            if (!exists()) mkdirs()
        }
    }
    
    /**
     * Get output directory for a specific app
     */
    fun getAppOutputDir(packageName: String): File {
        return File(getOutputBaseDir(), packageName).apply {
            if (!exists()) mkdirs()
        }
    }
    
    /**
     * Check if an app has been decompiled
     */
    fun isDecompiled(packageName: String): Boolean {
        val dir = getAppOutputDir(packageName)
        return dir.exists() && dir.listFiles()?.isNotEmpty() == true
    }
    
    /**
     * List files in a directory
     */
    suspend fun listFiles(directory: File): List<FileItem> = withContext(Dispatchers.IO) {
        directory.listFiles()
            ?.map { FileItem(it) }
            ?.sorted()
            ?: emptyList()
    }
    
    /**
     * Read file contents
     */
    suspend fun readFile(file: File): String = withContext(Dispatchers.IO) {
        file.readText()
    }
    
    /**
     * Delete decompiled output for an app
     */
    suspend fun deleteDecompiledApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val dir = getAppOutputDir(packageName)
        dir.deleteRecursively()
    }
    
    /**
     * Get total storage used by decompiled files
     */
    suspend fun getTotalStorageUsed(): Long = withContext(Dispatchers.IO) {
        getOutputBaseDir().walkTopDown().sumOf { it.length() }
    }
    
    /**
     * Clear all decompiled files
     */
    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        getOutputBaseDir().deleteRecursively()
    }
    
    /**
     * Get exports directory in Downloads
     */
    fun getExportsDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, "APKTools").apply {
            if (!exists()) mkdirs()
        }
    }
    
    /**
     * Export decompiled app to ZIP file
     * Returns the path to the ZIP file or null if failed
     */
    suspend fun exportToZip(packageName: String, appName: String): String? = withContext(Dispatchers.IO) {
        val sourceDir = getAppOutputDir(packageName)
        if (!sourceDir.exists() || sourceDir.listFiles()?.isEmpty() == true) {
            return@withContext null
        }
        
        val exportsDir = getExportsDir()
        // Use packageName for ZIP filename to ensure uniqueness
        val zipFile = File(exportsDir, "${packageName}_decompiled.zip")
        
        // Delete existing file if present
        if (zipFile.exists()) {
            zipFile.delete()
        }
        
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                sourceDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(sourceDir).path
                        val zipEntry = ZipEntry(relativePath)
                        zipOut.putNextEntry(zipEntry)
                        file.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }
            zipFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Extract/copy APK file to Downloads folder
     * Returns the path to the extracted APK or null if failed
     */
    suspend fun extractApk(packageName: String, apkPath: String): String? = withContext(Dispatchers.IO) {
        val exportsDir = getExportsDir()
        val sourceFile = File(apkPath)
        
        if (!sourceFile.exists()) {
            return@withContext null
        }
        
        val destFile = File(exportsDir, "${packageName}.apk")
        
        try {
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Check if extracted APK exists in Downloads
     */
    fun getExtractedApkFile(packageName: String): File? {
        val apkFile = File(getExportsDir(), "${packageName}.apk")
        return if (apkFile.exists()) apkFile else null
    }
    
    /**
     * Check if exported ZIP exists in Downloads
     */
    fun getExportedZipFile(packageName: String): File? {
        val zipFile = File(getExportsDir(), "${packageName}_decompiled.zip")
        return if (zipFile.exists()) zipFile else null
    }
    
    companion object {
        private const val OUTPUT_DIR = "decompiled"
    }
}
