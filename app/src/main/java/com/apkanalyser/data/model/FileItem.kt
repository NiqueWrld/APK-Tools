package com.apkanalyser.data.model

import java.io.File

/**
 * Represents a file or folder in the decompiled Smali output
 */
data class FileItem(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val path: String = file.absolutePath
) : Comparable<FileItem> {
    
    override fun compareTo(other: FileItem): Int {
        // Directories first, then alphabetical
        return when {
            isDirectory && !other.isDirectory -> -1
            !isDirectory && other.isDirectory -> 1
            else -> name.compareTo(other.name, ignoreCase = true)
        }
    }
}
