package com.niquewrld.apktools.ui.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.niquewrld.apktools.R
import com.niquewrld.apktools.data.model.FileItem
import com.niquewrld.apktools.databinding.ItemFileBinding

class FileListAdapter(
    private val onItemClick: (FileItem) -> Unit
) : ListAdapter<FileItem, FileListAdapter.FileViewHolder>(FileDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class FileViewHolder(
        private val binding: ItemFileBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: FileItem) {
            binding.apply {
                fileName.text = item.name
                fileIcon.setImageResource(
                    if (item.isDirectory) R.drawable.ic_folder else R.drawable.ic_file
                )
                
                root.setOnClickListener { onItemClick(item) }
            }
        }
    }
    
    private class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.path == newItem.path
        }
        
        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem == newItem
        }
    }
}
