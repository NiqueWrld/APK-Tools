package com.apkanalyser.ui.applist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.apkanalyser.R
import com.apkanalyser.data.model.AppInfo
import com.apkanalyser.databinding.ItemAppBinding

class AppListAdapter(
    private val onDecompileClick: (AppInfo) -> Unit,
    private val onBrowseClick: (AppInfo) -> Unit,
    private val onRedecompileClick: (AppInfo) -> Unit = {},
    private val onExportClick: (AppInfo) -> Unit = {}
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {
    
    private var decompiledPackages: Set<String> = emptySet()
    
    fun submitList(apps: List<AppInfo>, decompiled: Set<String>) {
        decompiledPackages = decompiled
        submitList(apps)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class AppViewHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(app: AppInfo) {
            binding.apply {
                appName.text = app.appName
                packageName.text = app.packageName
                appSize.text = "v${app.versionName} • ${String.format("%.1f", app.apkSizeMB)} MB"
                
                app.icon?.let { appIcon.setImageDrawable(it) }
                    ?: appIcon.setImageResource(R.mipmap.ic_launcher)
                
                val isDecompiled = decompiledPackages.contains(app.packageName)
                
                if (isDecompiled) {
                    // Show Browse/Export buttons and Redecompile
                    decompileButton.visibility = View.GONE
                    redecompileButton.visibility = View.VISIBLE
                    actionButtonsLayout.visibility = View.VISIBLE
                    
                    redecompileButton.setOnClickListener { onRedecompileClick(app) }
                    browseButton.setOnClickListener { onBrowseClick(app) }
                    exportButton.setOnClickListener { onExportClick(app) }
                } else {
                    // Show only Decompile button
                    decompileButton.visibility = View.VISIBLE
                    redecompileButton.visibility = View.GONE
                    actionButtonsLayout.visibility = View.GONE
                    
                    decompileButton.setOnClickListener { onDecompileClick(app) }
                }
                
                // Enable/disable based on size limit
                decompileButton.isEnabled = app.isDecompilable
                decompileButton.alpha = if (app.isDecompilable) 1f else 0.5f
                redecompileButton.isEnabled = app.isDecompilable
                redecompileButton.alpha = if (app.isDecompilable) 1f else 0.5f
            }
        }
    }
    
    private class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }
        
        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}
