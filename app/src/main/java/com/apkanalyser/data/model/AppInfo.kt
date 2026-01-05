package com.apkanalyser.data.model

import android.graphics.drawable.Drawable

/**
 * Represents an installed app on the device
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val apkPath: String,
    val apkSize: Long,
    val icon: Drawable?
) {
    val apkSizeMB: Double
        get() = apkSize / (1024.0 * 1024.0)
    
    val isDecompilable: Boolean
        get() = apkSize <= MAX_APK_SIZE
    
    companion object {
        const val MAX_APK_SIZE = 100L * 1024 * 1024 // 100 MB limit
    }
}
