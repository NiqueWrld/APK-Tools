package com.apkanalyser.data.repository

import android.content.Context
import android.os.Environment
import com.apkanalyser.data.model.FileItem
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
        return File(downloadsDir, "APKAnalyser").apply {
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
        val sanitizedName = appName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val zipFile = File(exportsDir, "${sanitizedName}_decompiled.zip")
        
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
    
    companion object {
        private const val OUTPUT_DIR = "decompiled"
    }
}
