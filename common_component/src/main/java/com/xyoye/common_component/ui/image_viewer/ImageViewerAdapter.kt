package com.xyoye.common_component.ui.image_viewer

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestOptions
import com.xyoye.common_component.databinding.ItemImageViewerBinding
import java.io.File

class ImageViewerAdapter(
    private val imagePaths: List<String>,
    private val authHeader: String? = null,
) : RecyclerView.Adapter<ImageViewerAdapter.ImageViewHolder>() {

    private var metricsInitialized = false
    private var actualScreenWidth = 0
    private var actualScreenHeight = 0

    private fun ensureMetrics(context: android.content.Context) {
        if (!metricsInitialized) {
            val displayMetrics = context.resources.displayMetrics
            actualScreenWidth = displayMetrics.widthPixels
            actualScreenHeight = displayMetrics.heightPixels
            metricsInitialized = true
        }
    }

    inner class ImageViewHolder(private val binding: ItemImageViewerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(imagePath: String, position: Int) {
            binding.photoView.scaleType = ImageView.ScaleType.FIT_CENTER
            binding.photoView.maximumScale = 10f
            binding.photoView.mediumScale = 5f
            binding.photoView.minimumScale = 1f

            ensureMetrics(binding.root.context)

            val glideUrl = buildGlideUrl(imagePath)

            Glide.with(binding.photoView)
                .load(glideUrl)
                .override(actualScreenWidth, actualScreenHeight)
                .apply(RequestOptions().apply {
                    format(DecodeFormat.PREFER_RGB_565)
                    dontAnimate()
                    diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                })
                .into(binding.photoView)
        }
    }

    fun preload(currentPosition: Int) {
        val preloadPositions = mutableListOf<Int>()
        if (currentPosition > 0) preloadPositions.add(currentPosition - 1)
        if (currentPosition < imagePaths.size - 1) preloadPositions.add(currentPosition + 1)

        for (pos in preloadPositions) {
            val path = imagePaths[pos]
            val glideUrl = buildGlideUrl(path)
            Glide.with(com.xyoye.common_component.base.app.BaseApplication.getAppContext())
                .load(glideUrl)
                .override(actualScreenWidth, actualScreenHeight)
                .apply(RequestOptions().apply {
                    format(DecodeFormat.PREFER_RGB_565)
                    diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC)
                })
                .preload()
        }
    }

    fun trimCache(currentPosition: Int) {
        // Glide 自动管理内存缓存，无需手动清理
    }

    private fun buildGlideUrl(imagePath: String): Any {
        return when {
            imagePath.startsWith("http://") || imagePath.startsWith("https://") -> {
                if (authHeader != null) {
                    GlideUrl(imagePath, LazyHeaders.Builder()
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .addHeader("Authorization", authHeader)
                        .build())
                } else {
                    imagePath
                }
            }
            imagePath.startsWith("content://") -> Uri.parse(imagePath)
            else -> File(imagePath)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        ensureMetrics(parent.context)
        val binding = ItemImageViewerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imagePaths[position], position)
    }

    override fun getItemCount(): Int = imagePaths.size
}
