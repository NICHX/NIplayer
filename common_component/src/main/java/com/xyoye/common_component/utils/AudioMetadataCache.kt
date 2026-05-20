package com.xyoye.common_component.utils

import com.xyoye.common_component.extension.toMetadataFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

data class AudioMetadata(
    val artist: String = "",
    val title: String = "",
    val duration: Long = 0L
)

object AudioMetadataCache {

    private val metadataCache = ConcurrentHashMap<String, AudioMetadata>()

    fun get(uniqueKey: String): AudioMetadata? {
        return metadataCache[uniqueKey]
    }

    fun put(uniqueKey: String, metadata: AudioMetadata) {
        metadataCache[uniqueKey] = metadata
    }

    fun remove(uniqueKey: String) {
        metadataCache.remove(uniqueKey)
    }

    fun clear() {
        metadataCache.clear()
    }

    fun saveToDisk(uniqueKey: String, metadata: AudioMetadata) {
        val metaFile = uniqueKey.toMetadataFile() ?: return
        try {
            metaFile.parentFile?.mkdirs()
            DataOutputStream(FileOutputStream(metaFile)).use { dos ->
                dos.writeUTF(metadata.artist)
                dos.writeUTF(metadata.title)
                dos.writeLong(metadata.duration)
            }
        } catch (_: Exception) {
        }
    }

    fun loadFromDisk(uniqueKey: String): AudioMetadata? {
        val metaFile = uniqueKey.toMetadataFile() ?: return null
        if (!metaFile.exists() || metaFile.length() == 0L) return null
        try {
            DataInputStream(FileInputStream(metaFile)).use { dis ->
                val artist = dis.readUTF()
                val title = dis.readUTF()
                val duration = dis.readLong()
                return AudioMetadata(artist, title, duration)
            }
        } catch (_: Exception) {
        }
        return null
    }
}
