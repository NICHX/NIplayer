package com.xyoye.local_component.ui.fragment.mine

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.utils.getFileName
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MineFragmentViewModel : BaseViewModel() {

    val mediaLibWithStatusLiveData = MediatorLiveData<MutableList<MediaLibraryEntity>>().apply {
        val mediaLibrariesLiveData = DatabaseManager.instance.getMediaLibraryDao().getAll()
        addSource(mediaLibrariesLiveData) { libraries ->
            val filteredList = filterMineLibraries(libraries)
            this.postValue(filteredList)
        }
    }

    private fun filterMineLibraries(libraries: MutableList<MediaLibraryEntity>): MutableList<MediaLibraryEntity> {
        val result = mutableListOf(MediaLibraryEntity.QUICK_ACCESS)
        val history = libraries.firstOrNull { it.mediaType == MediaType.OTHER_STORAGE }
        if (history != null) {
            result.add(history)
        }
        return result
    }

    fun initLocalStorage() {
        viewModelScope.launch(context = Dispatchers.IO) {
            DatabaseManager.instance.getMediaLibraryDao()
                .insert(
                    MediaLibraryEntity.HISTORY
                )
        }
    }

    fun deleteStorage(data: MediaLibraryEntity) {
        viewModelScope.launch(context = Dispatchers.IO) {
            DatabaseManager.instance.getMediaLibraryDao()
                .delete(data.url, data.mediaType)
        }
    }
}
