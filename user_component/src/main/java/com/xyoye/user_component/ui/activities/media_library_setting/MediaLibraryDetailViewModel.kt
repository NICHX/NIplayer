package com.xyoye.user_component.ui.activities.media_library_setting

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.MuluConfigEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContentTypeOption(
    val label: String,
    val value: String
)

class MediaLibraryDetailViewModel : BaseViewModel() {

    private val _pathListLiveData = MediatorLiveData<MutableList<MuluConfigEntity>>()
    val pathListLiveData: LiveData<MutableList<MuluConfigEntity>> = _pathListLiveData

    private var currentType: String = "movie"
    private var currentSource: LiveData<MutableList<MuluConfigEntity>>? = null

    val contentTypes = listOf(
        ContentTypeOption("电影", "movie"),
        ContentTypeOption("电视剧", "tv"),
        ContentTypeOption("综艺", "variety"),
        ContentTypeOption("动漫", "anime"),
        ContentTypeOption("纪录片", "documentary"),
        ContentTypeOption("演唱会", "concert"),
        ContentTypeOption("其他", "other")
    )

    fun initType(type: String) {
        if (type == currentType && currentSource != null) return
        currentType = type
        loadPaths(type)
    }

    fun setContentType(type: String) {
        if (type == currentType) return
        currentType = type
        loadPaths(type)
    }

    fun getCurrentType(): String = currentType

    fun loadAvailableLibraries(onLoaded: (List<MediaLibraryEntity>) -> Unit) {
        viewModelScope.launch(context = Dispatchers.IO) {
            val libraries = DatabaseManager.instance.getMediaLibraryDao().getAllSuspend()
            withContext(Dispatchers.Main) {
                onLoaded(libraries)
            }
        }
    }

    fun addPath(path: String, mediaLibraryId: Int) {
        viewModelScope.launch(context = Dispatchers.IO) {
            val config = MuluConfigEntity(
                mediaLibraryId = mediaLibraryId,
                muluType = currentType,
                path = path
            )
            DatabaseManager.instance.getMuluConfigDao().insert(config)
        }
    }

    fun deletePath(data: MuluConfigEntity) {
        viewModelScope.launch(context = Dispatchers.IO) {
            DatabaseManager.instance.getMuluConfigDao().deleteById(data.id)
        }
    }

    private fun loadPaths(type: String) {
        currentSource?.let { _pathListLiveData.removeSource(it) }
        val source = DatabaseManager.instance.getMuluConfigDao().getByMuluType(type)
        currentSource = source
        _pathListLiveData.addSource(source) { data ->
            val filtered = data.filter { it.path.isNotBlank() }.toMutableList()
            _pathListLiveData.postValue(filtered)
        }
    }
}