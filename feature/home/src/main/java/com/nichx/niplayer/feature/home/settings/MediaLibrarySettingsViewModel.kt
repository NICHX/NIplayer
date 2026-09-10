package com.nichx.niplayer.feature.home.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MediaLibrarySettingsViewModel @Inject constructor(
    mediaLibraryDao: MediaLibraryDao,
) : ViewModel() {

    /** 全部存储源，供“存储源缩略图策略”分组展示各存储库独立生成模式。 */
    val libraries: StateFlow<List<MediaLibraryEntity>> = mediaLibraryDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
}
