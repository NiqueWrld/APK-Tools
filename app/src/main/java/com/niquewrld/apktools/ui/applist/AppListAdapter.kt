package com.niquewrld.apktools.ui.applist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.niquewrld.apktools.R
import com.niquewrld.apktools.data.model.AppInfo
import com.niquewrld.apktools.databinding.ItemAppBinding

class AppListAdapter(
    private val onItemClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {
    
    private var decompiledPackages: Set<String> = emptySet()
    
    fun submitList(apps: List<AppInfo>, decompiled: Set<String>) {
        decompiledPackages = decompiled
        // Submit a copy of the list to force DiffUtil to run
        super.submitList(apps.toList()) {
            // After list is submitted, notify all items to rebind with new decompiled status
            notifyItemRangeChanged(0, itemCount)
        }
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
                // Card click to open detail page
                root.setOnClickListener { onItemClick(app) }
                
                appName.text = app.appName
                packageName.text = app.packageName
                appSize.text = "v${app.versionName} • ${String.format("%.1f", app.apkSizeMB)} MB"
                
                app.icon?.let { appIcon.setImageDrawable(it) }
                    ?: appIcon.setImageResource(R.mipmap.ic_launcher)
                
                // Show checkmark if decompiled
                val isDecompiled = decompiledPackages.contains(app.packageName)
                decompiledIcon.visibility = if (isDecompiled) View.VISIBLE else View.GONE
                if (isDecompiled) {
                    decompiledIcon.setColorFilter(
                        ContextCompat.getColor(root.context, android.R.color.holo_green_dark)
                    )
                }
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
