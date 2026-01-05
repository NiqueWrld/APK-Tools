package com.apkanalyser.domain

import android.content.Context
import com.apkanalyser.data.model.AppInfo
import com.apkanalyser.data.model.DecompileProgress
import com.apkanalyser.data.model.DecompileResult
import com.apkanalyser.data.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.iface.DexFile
import java.io.File
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/**
 * Engine for decompiling APK files to Smali code using baksmali/dexlib2
 */
class SmaliDecompiler(
    private val context: Context,
    private val fileRepository: FileRepository
) {
    
    /**
     * Decompile an APK to Smali code with progress updates
     */
    fun decompile(appInfo: AppInfo): Flow<DecompileState> = flow {
        emit(DecompileState.Starting)
        
        try {
            // Check APK size limit
            if (!appInfo.isDecompilable) {
                emit(DecompileState.Finished(
                    DecompileResult.Error("APK too large (${appInfo.apkSizeMB.toInt()}MB). Max: 100MB")
                ))
                return@flow
            }
            
            val outputDir = fileRepository.getAppOutputDir(appInfo.packageName)
            
            // Clear previous decompilation
            outputDir.deleteRecursively()
            outputDir.mkdirs()
            
            emit(DecompileState.Progress(DecompileProgress("Extracting DEX files...", 0, 100)))
            
            // Extract and decompile all DEX files
            val dexFiles = extractDexFiles(appInfo.apkPath)
            var totalClasses = 0
            var processedClasses = 0
            
            // Count total classes first
            dexFiles.forEach { dexFile ->
                val dex = loadDexFile(dexFile)
                totalClasses += dex.classes.size
            }
            
            emit(DecompileState.Progress(
                DecompileProgress("Found $totalClasses classes", 0, totalClasses)
            ))
            
            // Decompile each DEX file
            dexFiles.forEachIndexed { index, dexFile ->
                coroutineContext.ensureActive() // Support cancellation
                
                val dex = loadDexFile(dexFile)
                val options = createBaksmaliOptions()
                
                emit(DecompileState.Progress(
                    DecompileProgress(
                        "Decompiling ${dexFile.name}...",
                        processedClasses,
                        totalClasses
                    )
                ))
                
                // Use baksmali to disassemble
                val success = Baksmali.disassembleDexFile(
                    dex,
                    outputDir,
                    Runtime.getRuntime().availableProcessors(),
                    options
                )
                
                if (!success) {
                    emit(DecompileState.Finished(
                        DecompileResult.Error("Failed to decompile ${dexFile.name}")
                    ))
                    return@flow
                }
                
                processedClasses += dex.classes.size
                
                emit(DecompileState.Progress(
                    DecompileProgress(
                        "Completed ${dexFile.name}",
                        processedClasses,
                        totalClasses
                    )
                ))
                
                // Clean up temp DEX file
                dexFile.delete()
            }
            
            // Extract AndroidManifest.xml and resources
            emit(DecompileState.Progress(
                DecompileProgress("Extracting AndroidManifest.xml and resources...", processedClasses, totalClasses)
            ))
            extractManifestAndResources(appInfo.apkPath, outputDir)
            
            // Count output files
            val fileCount = outputDir.walkTopDown().count { it.isFile }
            
            emit(DecompileState.Finished(
                DecompileResult.Success(outputDir.absolutePath, fileCount)
            ))
            
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                emit(DecompileState.Finished(DecompileResult.Cancelled))
            } else {
                emit(DecompileState.Finished(
                    DecompileResult.Error(e.message ?: "Unknown error", e)
                ))
            }
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Extract all DEX files from an APK
     */
    private suspend fun extractDexFiles(apkPath: String): List<File> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "temp_dex")
        tempDir.deleteRecursively()
        tempDir.mkdirs()
        
        val dexFiles = mutableListOf<File>()
        
        ZipFile(apkPath).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.endsWith(".dex") }
                .forEach { entry ->
                    val dexFile = File(tempDir, entry.name)
                    zip.getInputStream(entry).use { input ->
                        dexFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    dexFiles.add(dexFile)
                }
        }
        
        // Sort to ensure classes.dex comes first, then classes2.dex, etc.
        dexFiles.sortedBy { it.name }
    }
    
    /**
     * Load a DEX file using dexlib2
     */
    private fun loadDexFile(dexFile: File): DexFile {
        return DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
    }
    
    /**
     * Create baksmali options for decompilation
     */
    private fun createBaksmaliOptions(): BaksmaliOptions {
        return BaksmaliOptions().apply {
            deodex = false
            implicitReferences = false
            parameterRegisters = true
            localsDirective = true
            sequentialLabels = true
            debugInfo = true
            codeOffsets = false
            accessorComments = true
            registerInfo = 0
        }
    }
    
    /**
     * Extract AndroidManifest.xml (decoded) and res/ folder from APK
     */
    private suspend fun extractManifestAndResources(apkPath: String, outputDir: File) = withContext(Dispatchers.IO) {
        val xmlDecoder = BinaryXmlDecoder()
        
        ZipFile(apkPath).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                // Skip directories and DEX files (already processed as Smali)
                if (entry.isDirectory || entry.name.endsWith(".dex")) {
                    return@forEach
                }
                
                val outputFile = File(outputDir, entry.name)
                outputFile.parentFile?.mkdirs()
                
                when {
                    // Decode AndroidManifest.xml
                    entry.name == "AndroidManifest.xml" -> {
                        try {
                            val binaryXml = zip.getInputStream(entry).readBytes()
                            val decodedXml = xmlDecoder.decode(binaryXml)
                            outputFile.writeText(decodedXml)
                        } catch (e: Exception) {
                            outputFile.writeText("<!-- Failed to decode: ${e.message} -->")
                        }
                    }
                    
                    // Decode XML files in res/ folder
                    entry.name.startsWith("res/") && entry.name.endsWith(".xml") -> {
                        try {
                            val binaryXml = zip.getInputStream(entry).readBytes()
                            val decodedXml = xmlDecoder.decode(binaryXml)
                            outputFile.writeText(decodedXml)
                        } catch (e: Exception) {
                            // If not binary XML or decode fails, copy as-is
                            zip.getInputStream(entry).use { input ->
                                outputFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                    
                    // Copy everything else as-is:
                    // - res/ non-XML files (images, etc.)
                    // - assets/ folder
                    // - lib/ folder (native .so libraries)
                    // - META-INF/ (signatures, certificates)
                    // - resources.arsc
                    // - kotlin/ metadata
                    // - Any other files
                    else -> {
                        zip.getInputStream(entry).use { input ->
                            outputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * State of the decompilation process
 */
sealed class DecompileState {
    object Starting : DecompileState()
    data class Progress(val progress: DecompileProgress) : DecompileState()
    data class Finished(val result: DecompileResult) : DecompileState()
}
