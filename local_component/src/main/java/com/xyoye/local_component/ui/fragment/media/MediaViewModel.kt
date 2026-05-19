package com.xyoye.local_component.ui.fragment.media

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Created by xyoye on 2020/7/27.
 */

class MediaViewModel : BaseViewModel() {

    val mediaLibWithStatusLiveData = MediatorLiveData<MutableList<MediaLibraryEntity>>().apply {
        val mediaLibrariesLiveData = DatabaseManager.instance.getMediaLibraryDao().getAll()
        //服务器数据源，只显示服务器相关
        addSource(mediaLibrariesLiveData) { libraries ->
            val filteredList = filterServerLibraries(libraries)
            this.postValue(filteredList)
        }
    }

    private fun filterServerLibraries(libraries: MutableList<MediaLibraryEntity>): MutableList<MediaLibraryEntity> {
        val serverTypes = listOf(
            MediaType.FTP_SERVER,
            MediaType.SMB_SERVER,
            MediaType.WEBDAV_SERVER,
            MediaType.ALSIT_STORAGE,
            MediaType.OTHER_STORAGE
        )
        val result = libraries.filter { serverTypes.contains(it.mediaType) }.toMutableList()
        result.add(MediaLibraryEntity.QUICK_ACCESS)
        return result
    }

    fun initLocalStorage() {
        viewModelScope.launch(context = Dispatchers.IO) {
            //播放历史首条记录
            DatabaseManager.instance.getPlayHistoryDao().gitLastPlay(
                MediaType.LOCAL_STORAGE,
                MediaType.OTHER_STORAGE,
                MediaType.FTP_SERVER,
                MediaType.SMB_SERVER,
                MediaType.WEBDAV_SERVER
            )?.apply {
                MediaLibraryEntity.HISTORY.url = url
            }

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