package com.xyoye.user_component.ui.activities.thumbnail_setting

import androidx.databinding.ObservableBoolean
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.config.ThumbnailConfig
import com.xyoye.common_component.config.ThumbnailServerConfig
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ThumbnailSettingViewModel : BaseViewModel() {

    val generateThumbnail = ObservableBoolean(ThumbnailConfig.isGenerateThumbnail())
    val generateForImage = ObservableBoolean(ThumbnailConfig.isGenerateForImage())
    val generateForVideo = ObservableBoolean(ThumbnailConfig.isGenerateForVideo())
    val saveInSameDir = ObservableBoolean(ThumbnailConfig.isSaveInSameDir())

    val serverItems = MediatorLiveData<List<ServerThumbnailItem>>().apply {
        val mediaLibrariesLiveData = DatabaseManager.instance.getMediaLibraryDao().getAll()
        addSource(mediaLibrariesLiveData) { libraries ->
            val serverTypes = listOf(
                MediaType.SMB_SERVER,
                MediaType.FTP_SERVER,
                MediaType.WEBDAV_SERVER,
                MediaType.ALSIT_STORAGE,
                MediaType.EXTERNAL_STORAGE
            )
            val items = libraries
                .filter { it.mediaType in serverTypes }
                .map { library ->
                    ServerThumbnailItem(
                        libraryId = library.id,
                        displayName = library.displayName,
                        mediaType = library.mediaType,
                        enabled = ThumbnailServerConfig.isServerThumbnailEnabled(library.id)
                    )
                }
            this.postValue(items)
        }
    }

    fun onGenerateThumbnailChanged(checked: Boolean) {
        generateThumbnail.set(checked)
        ThumbnailConfig.putGenerateThumbnail(checked)
    }

    fun onGenerateForImageChanged(checked: Boolean) {
        generateForImage.set(checked)
        ThumbnailConfig.putGenerateForImage(checked)
    }

    fun onGenerateForVideoChanged(checked: Boolean) {
        generateForVideo.set(checked)
        ThumbnailConfig.putGenerateForVideo(checked)
    }

    fun onSaveInSameDirChanged(checked: Boolean) {
        saveInSameDir.set(checked)
        ThumbnailConfig.putSaveInSameDir(checked)
    }

    fun onServerThumbnailChanged(libraryId: Int, checked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            ThumbnailServerConfig.putServerThumbnailEnabled(libraryId, checked)
            serverItems.postValue(serverItems.value?.map {
                if (it.libraryId == libraryId) it.copy(enabled = checked) else it
            })
        }
    }
}
