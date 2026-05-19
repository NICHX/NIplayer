package com.xyoye.player_component.audio.lrc

import com.xyoye.player_component.audio.model.AudioSong
import java.io.File

object LrcManager {
    fun getLrcFilePath(song: AudioSong): String? {
        val uri = song.uri
        if (uri.startsWith("/") || uri.startsWith("file://")) {
            val filePath = if (uri.startsWith("file://")) uri.removePrefix("file://") else uri
            val audioFile = File(filePath)
            val lrcFile = File(audioFile.parent, "${audioFile.nameWithoutExtension}.lrc")
            if (lrcFile.exists()) {
                return lrcFile.absolutePath
            }
        }
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
