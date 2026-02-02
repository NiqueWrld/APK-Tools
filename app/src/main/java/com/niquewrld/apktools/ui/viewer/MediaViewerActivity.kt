package com.niquewrld.apktools.ui.viewer

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import androidx.appcompat.app.AppCompatActivity
import com.niquewrld.apktools.databinding.ActivityMediaViewerBinding
import java.io.File

class MediaViewerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMediaViewerBinding
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMediaViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: run {
            finish()
            return
        }
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: "Media"
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE) ?: MediaType.UNKNOWN
        
        setupToolbar(fileName)
        
        when (mediaType) {
            MediaType.IMAGE -> showImage(filePath)
            MediaType.VIDEO -> showVideo(filePath)
            MediaType.AUDIO -> showAudio(filePath, fileName)
            else -> showError("Cannot preview this file type")
        }
    }
    
    private fun setupToolbar(fileName: String) {
        binding.toolbar.title = fileName
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
    
    private fun showImage(filePath: String) {
        binding.loadingIndicator.visibility = View.VISIBLE
        
        try {
            val file = File(filePath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(filePath)
                if (bitmap != null) {
                    binding.imageView.setImageBitmap(bitmap)
                    binding.imageView.visibility = View.VISIBLE
                } else {
                    showError("Cannot decode image")
                }
            } else {
                showError("File not found")
            }
        } catch (e: Exception) {
            showError("Error loading image: ${e.message}")
        } finally {
            binding.loadingIndicator.visibility = View.GONE
        }
    }
    
    private fun showVideo(filePath: String) {
        binding.loadingIndicator.visibility = View.VISIBLE
        
        try {
            val file = File(filePath)
            if (file.exists()) {
                binding.videoView.visibility = View.VISIBLE
                
                val mediaController = MediaController(this)
                mediaController.setAnchorView(binding.videoView)
                
                binding.videoView.setMediaController(mediaController)
                binding.videoView.setVideoURI(Uri.fromFile(file))
                
                binding.videoView.setOnPreparedListener { mp ->
                    binding.loadingIndicator.visibility = View.GONE
                    mp.isLooping = false
                    binding.videoView.start()
                }
                
                binding.videoView.setOnErrorListener { _, _, _ ->
                    binding.loadingIndicator.visibility = View.GONE
                    showError("Cannot play video")
                    true
                }
            } else {
                showError("File not found")
            }
        } catch (e: Exception) {
            binding.loadingIndicator.visibility = View.GONE
            showError("Error loading video: ${e.message}")
        }
    }
    
    private fun showAudio(filePath: String, fileName: String) {
        binding.audioContainer.visibility = View.VISIBLE
        binding.audioFileName.text = fileName
        
        try {
            val file = File(filePath)
            if (file.exists()) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    
                    setOnCompletionListener {
                        this@MediaViewerActivity.isPlaying = false
                        binding.playPauseButton.text = "Play"
                    }
                }
                
                binding.playPauseButton.setOnClickListener {
                    mediaPlayer?.let { mp ->
                        if (this@MediaViewerActivity.isPlaying) {
                            mp.pause()
                            binding.playPauseButton.text = "Play"
                        } else {
                            mp.start()
                            binding.playPauseButton.text = "Pause"
                        }
                        this@MediaViewerActivity.isPlaying = !this@MediaViewerActivity.isPlaying
                    }
                }
            } else {
                showError("File not found")
            }
        } catch (e: Exception) {
            showError("Error loading audio: ${e.message}")
        }
    }
    
    private fun showError(message: String) {
        binding.loadingIndicator.visibility = View.GONE
        binding.imageView.visibility = View.GONE
        binding.videoView.visibility = View.GONE
        binding.audioContainer.visibility = View.GONE
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }
    
    override fun onPause() {
        super.onPause()
        binding.videoView.pause()
        mediaPlayer?.pause()
        isPlaying = false
        binding.playPauseButton.text = "Play"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
    
    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_MEDIA_TYPE = "media_type"
    }
    
    object MediaType {
        const val IMAGE = "image"
        const val VIDEO = "video"
        const val AUDIO = "audio"
        const val UNKNOWN = "unknown"
    }
}
