package com.apkanalyser.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.apkanalyser.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository for fetching installed apps from the device
 */
class AppRepository(private val context: Context) {
    
    private val packageManager: PackageManager = context.packageManager
    
    /**
     * Get all installed apps (excluding system apps by default)
     */
    suspend fun getInstalledApps(includeSystem: Boolean = false): List<AppInfo> = 
        withContext(Dispatchers.IO) {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }
            
            packages
                .filter { includeSystem || !isSystemApp(it) }
                .mapNotNull { appInfo -> 
                    try {
                        createAppInfo(appInfo)
                    } catch (e: Exception) {
                        null // Skip apps we can't read
                    }
                }
                .sortedBy { it.appName.lowercase() }
        }
    
    /**
     * Search apps by name or package
     */
    suspend fun searchApps(query: String, includeSystem: Boolean = false): List<AppInfo> {
        val apps = getInstalledApps(includeSystem)
        if (query.isBlank()) return apps
        
        val lowerQuery = query.lowercase()
        return apps.filter { 
            it.appName.lowercase().contains(lowerQuery) ||
            it.packageName.lowercase().contains(lowerQuery)
        }
    }
    
    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }
    
    private fun createAppInfo(appInfo: ApplicationInfo): AppInfo {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                appInfo.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(appInfo.packageName, 0)
        }
        
        val apkFile = File(appInfo.sourceDir)
        
        return AppInfo(
            packageName = appInfo.packageName,
            appName = packageManager.getApplicationLabel(appInfo).toString(),
            versionName = packageInfo.versionName ?: "Unknown",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            },
            apkPath = appInfo.sourceDir,
            apkSize = apkFile.length(),
            icon = try {
                packageManager.getApplicationIcon(appInfo)
            } catch (e: Exception) {
                null
            }
        )
    }
}
