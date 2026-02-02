package com.niquewrld.apktools.data.model

/**
 * Represents the result of a decompilation operation
 */
sealed class DecompileResult {
    data class Success(val outputDir: String, val fileCount: Int) : DecompileResult()
    data class Error(val message: String, val exception: Throwable? = null) : DecompileResult()
    object Cancelled : DecompileResult()
}

/**
 * Progress updates during decompilation
 */
data class DecompileProgress(
    val currentFile: String,
    val processedFiles: Int,
    val totalFiles: Int
) {
    val percentage: Int
        get() = if (totalFiles > 0) (processedFiles * 100 / totalFiles) else 0
}
