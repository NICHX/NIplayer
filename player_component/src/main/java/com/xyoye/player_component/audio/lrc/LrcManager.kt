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
            val lrcPath = findLrcByAudioPath(filePath)
            if (lrcPath != null) return lrcPath
        }

        if (song.fileName.isNotEmpty()) {
            val lrcPath = findLrcByFileName(song.fileName)
            if (lrcPath != null) return lrcPath
        }
        
        if (song.title.isNotEmpty()) {
            val lrcPath = findLrcByTitle(song.title)
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
    
    fun getLrcCacheDir(context: android.content.Context): File {
        val appLrcDir = File(context.cacheDir, "lrc_cache")
        if (!appLrcDir.exists()) {
            appLrcDir.mkdirs()
        }
        return appLrcDir
    }

    private fun findLrcByAudioPath(audioFilePath: String): String? {
        val audioFile = File(audioFilePath)
        val parentDir = audioFile.parentFile ?: return null
        val nameWithoutExt = audioFile.nameWithoutExtension
        if (nameWithoutExt.isEmpty()) return null

        val lrcPatterns = listOf(
            "$nameWithoutExt.lrc",
            "$nameWithoutExt.LRC",
            "lyrics.lrc",
            "lyrics.LRC",
            "Lyrics.lrc",
            "Lyrics.LRC",
            " lyrics.lrc",
            " lyrics.LRC"
        )
        
        for (pattern in lrcPatterns) {
            val lrcFile = File(parentDir, pattern)
            if (lrcFile.exists() && lrcFile.length() > 0) {
                return lrcFile.absolutePath
            }
        }
        
        val parentParentDir = parentDir.parentFile
        if (parentParentDir != null) {
            for (pattern in lrcPatterns) {
                val lrcFile = File(parentParentDir, pattern)
                if (lrcFile.exists() && lrcFile.length() > 0) {
                    return lrcFile.absolutePath
                }
            }
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
            
            val lrcFileUpper = File(musicDir, "$nameWithoutExt.LRC")
            if (lrcFileUpper.exists() && lrcFileUpper.length() > 0) {
                return lrcFileUpper.absolutePath
            }
        }
        
        val downloadDir = File(externalDir, "Download")
        if (downloadDir.exists()) {
            val lrcFile = File(downloadDir, "$nameWithoutExt.lrc")
            if (lrcFile.exists() && lrcFile.length() > 0) {
                return lrcFile.absolutePath
            }
        }

        return null
    }
    
    private fun findLrcByTitle(title: String): String? {
        val externalDir = android.os.Environment.getExternalStorageDirectory()
        val lrcDirs = listOf(
            File(externalDir, "Lyrics"),
            File(externalDir, "lyrics"),
            File(externalDir, "Music"),
            File(externalDir, "Music/Lyrics")
        )
        
        val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
        
        for (dir in lrcDirs) {
            if (!dir.exists()) continue
            
            val lrcFile = File(dir, "$cleanTitle.lrc")
            if (lrcFile.exists() && lrcFile.length() > 0) {
                return lrcFile.absolutePath
            }
            
            val lrcFileUpper = File(dir, "$cleanTitle.LRC")
            if (lrcFileUpper.exists() && lrcFileUpper.length() > 0) {
                return lrcFileUpper.absolutePath
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
    
    fun saveLrcToCache(uniqueKey: String, content: String): File? {
        val context = appContext ?: return null
        val cacheDir = getLrcCacheDir(context)
        val lrcFile = File(cacheDir, "$uniqueKey.lrc")
        try {
            lrcFile.writeText(content)
            return lrcFile
        } catch (_: Exception) {
            return null
        }
    }
}
