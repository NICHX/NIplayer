package com.xyoye.player_component.ui.activities.player

import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.source.base.BaseVideoSource
import com.xyoye.data_component.bean.VideoTrackBean
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.data_component.enums.TrackType
import kotlinx.coroutines.launch

class PlayerViewModel : BaseViewModel() {

    fun storeTrackAdded(videoSource: BaseVideoSource, track: VideoTrackBean) {
        val uniqueKey = videoSource.getUniqueKey()
        val storageId = videoSource.getStorageId()
        val historyDao = DatabaseManager.instance.getPlayHistoryDao()

        viewModelScope.launch {
            val playHistory = DatabaseManager.instance
                .getPlayHistoryDao().getPlayHistory(uniqueKey, storageId)
                ?: PlayHistoryEntity(
                    0,
                    "",
                    "",
                    mediaType = videoSource.getMediaType(),
                    uniqueKey = uniqueKey,
                    storageId = storageId,
                )

            when (track.type) {
                TrackType.AUDIO -> {
                    val audioPath = track.type.getAudio(track.trackResource)
                    if (audioPath != null && audioPath != videoSource.getAudioPath()) {
                        videoSource.setAudioPath(audioPath)
                        playHistory.audioPath = audioPath
                    }
                }

                TrackType.SUBTITLE -> {
                    val subtitlePath = track.type.getSubtitle(track.trackResource)
                    if (subtitlePath != null && subtitlePath != videoSource.getSubtitlePath()) {
                        videoSource.setSubtitlePath(subtitlePath)
                        playHistory.subtitlePath = subtitlePath
                    }
                }

                else -> {
                }
            }

            historyDao.insert(playHistory)
            val newHistory = historyDao.getPlayHistory(uniqueKey, storageId)
            videoSource.updateFileHistory(newHistory)
        }
    }
}