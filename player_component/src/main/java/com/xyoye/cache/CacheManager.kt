package com.xyoye.cache

import com.danikula.videocache.HttpProxyCacheServer
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.common_component.config.PlayerConfig
import com.xyoye.common_component.utils.NetworkType
import com.xyoye.common_component.utils.NetworkTypeUtil
import com.xyoye.common_component.utils.PathHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

object CacheManager {

    private val headersInjector = VideoHeadersInjector()
    private var cacheServer: HttpProxyCacheServer? = null
    private var preCacheJob: kotlinx.coroutines.Job? = null

    private fun createCacheServer(): HttpProxyCacheServer {
        val maxSize = getMaxCacheSize()
        val server = HttpProxyCacheServer.Builder(BaseApplication.getAppContext())
            .headerInjector(headersInjector)
            .sourceFactory(OkHttpUrlSourceFactory())
            .maxCacheSize(maxSize)
            .build()
        cacheServer = server
        return server
    }

    fun getCacheUrl(url: String, headers: Map<String, String>? = null): String {
        val server = cacheServer ?: createCacheServer()
        if (headers != null && headers.isNotEmpty()) {
            headersInjector.registerHeader(url, headers)
        }
        return server.getProxyUrl(url)
    }

    fun release() {
        stopPreCache()
        cacheServer?.shutdown()
        cacheServer = null
    }

    private val preCacheClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun startPreCache(url: String, headers: Map<String, String>?) {
        stopPreCache()
        if (url.isEmpty() || !url.startsWith("http")) return

        val context = BaseApplication.getAppContext()
        val networkType = NetworkTypeUtil.getNetworkType(context)
        if (networkType == NetworkType.NONE) return

        val maxCacheSize = getMaxCacheSizeByNetwork(networkType)
        if (maxCacheSize <= 0) return

        preCacheJob = GlobalScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = PathHelper.getProxyCacheDirectory()
                cacheDir.mkdirs()

                val md5 = MessageDigest.getInstance("MD5").digest(url.toByteArray())
                val fileName = md5.joinToString("") { "%02x".format(it) }
                val cacheFile = File(cacheDir, fileName)
                val tempFile = File(cacheDir, "$fileName.tmp")

                if (cacheFile.exists() && cacheFile.length() >= maxCacheSize) return@launch

                val requestBuilder = Request.Builder().url(url)
                if (headers != null) {
                    for ((key, value) in headers) {
                        requestBuilder.addHeader(key, value)
                    }
                }

                val response = preCacheClient.newCall(requestBuilder.build()).execute()
                val body = response.body ?: return@launch

                val inputStream = body.byteStream()
                val buffer = ByteArray(8192)
                var totalRead = 0L

                val raf = RandomAccessFile(tempFile, "rw")
                raf.seek(tempFile.length())
                if (raf.filePointer > 0) {
                    inputStream.skip(raf.filePointer)
                    totalRead = raf.filePointer
                }

                while (isActive && totalRead < maxCacheSize) {
                    val maxRead = minOf(buffer.size.toLong(), maxCacheSize - totalRead).toInt()
                    val bytesRead = inputStream.read(buffer, 0, maxRead)
                    if (bytesRead == -1) break
                    raf.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }
                raf.close()
                inputStream.close()

                if (isActive) {
                    tempFile.renameTo(cacheFile)
                } else {
                    tempFile.delete()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stopPreCache() {
        preCacheJob?.cancel()
        preCacheJob = null
    }

    fun getProxyCacheDirectory(): File {
        return PathHelper.getProxyCacheDirectory()
    }

    private fun getMaxCacheSize(): Long {
        val context = BaseApplication.getAppContext()
        val networkType = NetworkTypeUtil.getNetworkType(context)
        return getMaxCacheSizeByNetwork(networkType)
    }

    private fun getMaxCacheSizeByNetwork(networkType: NetworkType): Long {
        return when (networkType) {
            NetworkType.WIFI -> PlayerConfig.getWifiCacheSize() * 1024L * 1024
            NetworkType.MOBILE -> PlayerConfig.getMobileCacheSize() * 1024L * 1024
            NetworkType.NONE -> 0
        }
    }
}