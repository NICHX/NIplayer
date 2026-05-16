package com.xyoye.player.utils

import com.xyoye.common_component.network.helper.UnsafeOkHttpClient
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import okhttp3.Request
import java.io.BufferedInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class VlcProxyServer private constructor(port: Int = randomPort()) : NanoHTTPD(port) {
    private val urlMap = ConcurrentHashMap<String, String>()
    private val headersMap = ConcurrentHashMap<String, Map<String, String>>()
    private val keyCounter = AtomicLong(0)

    private object Holder {
        val instance = VlcProxyServer()
    }

    companion object {
        private const val STREAM_BUFFER_SIZE = 256 * 1024

        private fun randomPort() = Random.nextInt(30001, 40000)

        @JvmStatic
        fun getInstance() = Holder.instance
    }

    init {
    }

    private fun updatePort(newPort: Int) {
        try {
            val portField = NanoHTTPD::class.java.getDeclaredField("myPort")
            portField.isAccessible = true
            portField.setInt(this, newPort)
        } catch (ignored: Exception) {
        }
    }

    fun safeStart() {
        if (isAlive) return
        for (attempt in 0..5) {
            try {
                start()
                return
            } catch (e: java.io.IOException) {
                stop()
                val newPort = Random.nextInt(30001, 40000)
                updatePort(newPort)
            }
        }
    }

    override fun serve(session: IHTTPSession?): Response {
        session ?: return super.serve(session)

        val key = session.uri.trimStart('/')
        val proxyUrl = urlMap[key]
        val proxyHeaders = headersMap[key]
        if (proxyUrl == null || proxyHeaders == null) {
            return newFixedLengthResponse(Status.NOT_FOUND, "*/*", "proxy url not found")
        }

        val proxyResponse = getProxyResponse(proxyUrl, proxyHeaders, session)
        val bodyStream = proxyResponse.body?.byteStream()
        val bufferedStream = if (bodyStream != null) {
            BufferedInputStream(bodyStream, STREAM_BUFFER_SIZE)
        } else {
            null
        }
        val response = newFixedLengthResponse(
            Status.lookup(proxyResponse.code) ?: Status.OK,
            proxyResponse.header("Content-Type"),
            bufferedStream,
            proxyResponse.body?.contentLength() ?: 0
        )
        val responseHeaders = proxyResponse.headers

        for (index in 0 until responseHeaders.size) {
            val keyName = responseHeaders.name(index)
            val value = responseHeaders.value(index)
            response.addHeader(keyName, value)
        }

        return response
    }

    fun getInputStreamUrl(url: String, headers: Map<String, String>): String {
        val key = keyCounter.incrementAndGet().toString()
        urlMap[key] = url
        headersMap[key] = headers
        return "http://127.0.0.1:$listeningPort/$key"
    }

    private fun getProxyResponse(
        url: String,
        headers: Map<String, String>,
        session: IHTTPSession
    ): okhttp3.Response {
        val requestBuilder = Request.Builder()
        headers.forEach {
            requestBuilder.header(it.key, it.value)
        }
        session.headers.forEach {
            requestBuilder.header(it.key, it.value)
        }
        requestBuilder.apply {
            removeHeader("host")
            removeHeader("remote-addr")
            removeHeader("http-client-ip")
        }
        val request = requestBuilder.url(url).build()

        val call = UnsafeOkHttpClient.client.newCall(request)
        return call.execute()
    }

    fun release() {
        urlMap.clear()
        headersMap.clear()
        stop()
    }
}
