package com.xyoye.common_component.source.factory

import com.xyoye.common_component.config.AppConfig
import com.xyoye.common_component.config.SubtitleConfig
import com.xyoye.common_component.source.media.StorageVideoSource
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.StorageSortOption
import com.xyoye.common_component.storage.file.StorageFile

/**
 * Created by xyoye on 2023/1/2.
 */

object StorageVideoSourceFactory {

    suspend fun create(file: StorageFile): StorageVideoSource? {
        val storage = file.storage
        val videoSources = getVideoSources(storage, file)
        val playUrl = storage.createPlayUrl(file) ?: return null
        val subtitlePath = getSubtitlePath(file, storage)
        val audioPath = file.playHistory?.audioPath
        return StorageVideoSource(
            playUrl,
            file,
            videoSources,
            subtitlePath,
            audioPath
        )
    }

    private suspend fun getSubtitlePath(file: StorageFile, storage: Storage): String? {
        //从播放记录读取字幕
        if (file.playHistory?.subtitlePath?.isNotEmpty() == true) {
            return file.playHistory?.subtitlePath
        }

        //是否匹配同文件夹内同名字幕
        if (SubtitleConfig.isAutoLoadSameNameSubtitle()) {
            return storage.cacheSubtitle(file)
        }

        return null
    }

    private fun getVideoSources(storage: Storage, currentFile: StorageFile): List<StorageFile> {
        val isAudio = currentFile.isAudioFile()
        return storage.directoryFiles
            .filter {
                if (isAudio) it.isAudioFile() else it.isVideoFile()
            }
            .filter { AppConfig.isShowHiddenFile() || !it.fileName().startsWith(".") }
            .sortedWith(StorageSortOption.comparator())
    }
}