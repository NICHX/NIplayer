package com.xyoye.common_component.ui.image_viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.xyoye.common_component.databinding.ItemImageViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class ImageViewerAdapter(
    private val imagePaths: List<String>
) : RecyclerView.Adapter<ImageViewerAdapter.ImageViewHolder>() {

    private val bitmapCache = LruCache<Int, Bitmap>(imagePaths.size.coerceAtMost(10))

    inner class ImageViewHolder(private val binding: ItemImageViewerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(imagePath: String, position: Int) {
            binding.photoView.scaleType = ImageView.ScaleType.FIT_CENTER
            binding.photoView.maximumScale = 10f
            binding.photoView.mediumScale = 5f
            binding.photoView.minimumScale = 1f

            val cached = bitmapCache.get(position)
            if (cached != null && !cached.isRecycled) {
                binding.photoView.setImageBitmap(cached)
                return
            }

            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = loadImage(imagePath, binding.root.context)
                    if (bitmap != null) {
                        bitmapCache.put(position, bitmap)
                    }
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            binding.photoView.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun preload(currentPosition: Int) {
        val preloadPositions = mutableListOf<Int>()
        if (currentPosition > 0) preloadPositions.add(currentPosition - 1)
        if (currentPosition < imagePaths.size - 1) preloadPositions.add(currentPosition + 1)

        for (pos in preloadPositions) {
            if (bitmapCache.get(pos) != null) continue
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = loadImage(imagePaths[pos], null)
                    if (bitmap != null) {
                        bitmapCache.put(pos, bitmap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun trimCache(currentPosition: Int) {
        val keysToKeep = mutableSetOf(currentPosition)
        if (currentPosition > 0) keysToKeep.add(currentPosition - 1)
        if (currentPosition < imagePaths.size - 1) keysToKeep.add(currentPosition + 1)
        val keysToRemove = mutableListOf<Int>()
        for (key in bitmapCache.snapshot().keys) {
            if (key !in keysToKeep) {
                keysToRemove.add(key)
            }
        }
        for (key in keysToRemove) {
            bitmapCache.remove(key)
        }
    }

    private suspend fun loadImage(path: String, context: Context?): Bitmap? {
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> {
                loadNetworkImage(path)
            }
            path.startsWith("content://") -> {
                loadContentUri(path, context)
            }
            else -> {
                loadLocalFile(path)
            }
        }
    }

    private fun loadLocalFile(path: String): Bitmap? {
        val file = File(path)
        if (file.exists()) {
            try {
                return BitmapFactory.decodeFile(path)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun loadContentUri(path: String, context: Context?): Bitmap? {
        if (context == null) return null
        var inputStream: InputStream? = null
        try {
            val uri = Uri.parse(path)
            inputStream = context.contentResolver.openInputStream(uri)
            return BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun loadNetworkImage(urlStr: String): Bitmap? {
        var inputStream: InputStream? = null
        var connection: HttpURLConnection? = null

        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()

            inputStream = connection.inputStream
            return BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                inputStream?.close()
                connection?.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
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
