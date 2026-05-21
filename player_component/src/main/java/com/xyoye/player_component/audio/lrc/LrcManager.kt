package com.xyoye.player_component.audio.lrc

import com.xyoye.player_component.audio.model.AudioSong
import java.io.File

object LrcManager {

    private var appContext: android.content.Context? = null

    fun setApplicationContext(context: android.content.Context) {
        appContext = context.applicationContext
    }

    fun getLrcFilePath(song: AudioSong): String? {
        val uri = song.uri
        if (uri.startsWith("/") || uri.startsWith("file://")) {
            val filePath = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
            return findLrcByAudioPath(filePath)
        }
        return null
    }

    fun findLocalLrcFile(song: AudioSong): String? {
        val uri = song.uri
        if (uri.startsWith("/") || uri.startsWith("file://")) {
            val filePath = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
            return findLrcByAudioPath(filePath)
        }

        if (song.fileName.isNotEmpty()) {
            val lrcPath = findLrcByFileName(song.fileName)
            if (lrcPath != null) return lrcPath
        }

        return null
    }

    fun findCachedLrcFile(song: AudioSong): String? {
        val cacheDir = findLrcCacheDir() ?: return null
        if (!cacheDir.exists()) return null

        val cachedFile = File(cacheDir, "${song.uniqueKey}.lrc")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return cachedFile.absolutePath
        }

        return null
    }

    private fun findLrcByAudioPath(audioFilePath: String): String? {
        val audioFile = File(audioFilePath)
        val parentDir = audioFile.parentFile ?: return null
        val nameWithoutExt = audioFile.nameWithoutExtension
        if (nameWithoutExt.isEmpty()) return null

        val lrcFile = File(parentDir, "$nameWithoutExt.lrc")
        if (lrcFile.exists() && lrcFile.length() > 0) {
            return lrcFile.absolutePath
        }

        val lrcFileUpper = File(parentDir, "${nameWithoutExt}.LRC")
        if (lrcFileUpper.exists() && lrcFileUpper.length() > 0) {
            return lrcFileUpper.absolutePath
        }

        return null
    }

    private fun findLrcByFileName(fileName: String): String? {
        val nameWithoutExt = fileName.substringBeforeLast(".", "")
        if (nameWithoutExt.isEmpty()) return null

        val externalDir = android.os.Environment.getExternalStorageDirectory()
        val musicDir = File(externalDir, "Music")
        if (musicDir.exists()) {
            val lrcFile = File(musicDir, "$nameWithoutExt.lrc")
            if (lrcFile.exists() && lrcFile.length() > 0) {
                return lrcFile.absolutePath
            }
        }

        return null
    }

    private fun findLrcCacheDir(): File? {
        val context = appContext
        if (context != null) {
            val appLrcDir = File(context.cacheDir, "lrc_cache")
            if (appLrcDir.exists()) return appLrcDir
        }

        val externalLrcDir = File(android.os.Environment.getExternalStorageDirectory(), "NIplayer/lrc_cache")
        if (externalLrcDir.exists()) return externalLrcDir

        return null
    }

    fun saveLrcFile(song: AudioSong, content: String): File {
        val lrcDir = File(android.os.Environment.getExternalStorageDirectory(), "NIplayer/lrc")
        if (!lrcDir.exists()) lrcDir.mkdirs()
        val lrcFile = File(lrcDir, "${song.uniqueKey}.lrc")
        lrcFile.writeText(content)
        return lrcFile
    }
}
