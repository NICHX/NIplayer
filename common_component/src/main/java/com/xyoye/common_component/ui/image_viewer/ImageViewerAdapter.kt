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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

class ImageViewerAdapter(
    private val imagePaths: List<String>,
    private val context: Context,
    private val authHeader: String? = null
) : RecyclerView.Adapter<ImageViewerAdapter.ImageViewHolder>() {

    private val bitmapCache = LruCache<Int, Bitmap>(imagePaths.size.coerceAtMost(10))
    private val loadingSet = Collections.synchronizedSet(mutableSetOf<Int>())
    private var onPreloadListener: ((Int) -> Unit)? = null

    private val screenWidth: Int
    private val screenHeight: Int

    init {
        val displayMetrics = context.resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
    }

    fun setOnPreloadListener(listener: (Int) -> Unit) {
        onPreloadListener = listener
    }

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

            loadingSet.add(position)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = loadImage(imagePath, context.samplingSize())
                    if (bitmap != null) {
                        bitmapCache.put(position, bitmap)
                    }
                    loadingSet.remove(position)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            binding.photoView.setImageBitmap(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    loadingSet.remove(position)
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
            if (loadingSet.contains(pos)) continue
            loadingSet.add(pos)
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val bitmap = loadImage(imagePaths[pos], context.samplingSize())
                    if (bitmap != null) {
                        bitmapCache.put(pos, bitmap)
                        onPreloadListener?.invoke(pos)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    loadingSet.remove(pos)
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

    private fun Context.samplingSize(): Int {
        val maxDimension = maxOf(screenWidth, screenHeight)
        return if (maxDimension > 0) maxDimension else 1920
    }

    private suspend fun loadImage(path: String, maxSize: Int): Bitmap? {
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> {
                loadNetworkImage(path, maxSize)
            }
            path.startsWith("content://") -> {
                loadContentUri(path, maxSize)
            }
            else -> {
                loadLocalFile(path, maxSize)
            }
        }
    }

    private fun decodeSampledBitmap(source: () -> InputStream?, maxSize: Int): Bitmap? {
        try {
            val inputStream = source()
                ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
            options.inJustDecodeBounds = false

            val secondStream = source()
                ?: return null
            return BitmapFactory.decodeStream(secondStream, null, options)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun decodeSampledFile(path: String, maxSize: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)
            options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun loadLocalFile(path: String, maxSize: Int): Bitmap? {
        return decodeSampledFile(path, maxSize)
    }

    private fun loadContentUri(path: String, maxSize: Int): Bitmap? {
        return decodeSampledBitmap({
            try {
                val uri = Uri.parse(path)
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }, maxSize)
    }

    private suspend fun loadNetworkImage(urlStr: String, maxSize: Int): Bitmap? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (authHeader != null) {
                connection.setRequestProperty("Authorization", authHeader)
            }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                return null
            }

            val inputStream = connection.inputStream
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            inputStream.close()
            connection.disconnect()
            connection = null

            val imageData = outputStream.toByteArray()
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
            options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            connection?.disconnect()
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
