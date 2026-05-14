package com.xyoye.user_component.ui.activities.thumbnail_setting

import androidx.databinding.ObservableBoolean
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.config.ThumbnailConfig

class ThumbnailSettingViewModel : BaseViewModel() {

    val generateThumbnail = ObservableBoolean(ThumbnailConfig.isGenerateThumbnail())
    val generateForImage = ObservableBoolean(ThumbnailConfig.isGenerateForImage())
    val generateForVideo = ObservableBoolean(ThumbnailConfig.isGenerateForVideo())
    val saveInSameDir = ObservableBoolean(ThumbnailConfig.isSaveInSameDir())

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
}
