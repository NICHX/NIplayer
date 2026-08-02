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
class PlayerSettingsViewModel @Inject constructor(
    mediaLibraryDao: MediaLibraryDao,
) : ViewModel() {

    val libraries: StateFlow<List<MediaLibraryEntity>> = mediaLibraryDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
}
