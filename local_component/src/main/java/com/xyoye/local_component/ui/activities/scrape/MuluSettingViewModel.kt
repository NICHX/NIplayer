package com.xyoye.local_component.ui.activities.scrape

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.MuluConfigEntity
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MuluSettingViewModel : BaseViewModel() {

    private val _muluListLiveData = MediatorLiveData<MutableList<MuluConfigEntity>>()
    val muluListLiveData: LiveData<MutableList<MuluConfigEntity>> = _muluListLiveData

    private var currentSource: LiveData<MutableList<MuluConfigEntity>>? = null

    private val _availableLibraries = mutableListOf<MediaLibraryEntity>()

    fun loadMuluList(muluType: String) {
        currentSource?.let { _muluListLiveData.removeSource(it) }

        val source = DatabaseManager.instance.getMuluConfigDao().getByMuluType(muluType)
        currentSource = source
        _muluListLiveData.addSource(source) { data ->
            _muluListLiveData.postValue(data)
        }
    }

    fun loadAvailableLibraries(onLoaded: (List<MediaLibraryEntity>) -> Unit) {
        viewModelScope.launch(context = Dispatchers.IO) {
            val libraries = DatabaseManager.instance.getMediaLibraryDao().getAllSuspend()
            val filtered = libraries.filter {
                it.mediaType != MediaType.QUICK_ACCESS
            }
            _availableLibraries.clear()
            _availableLibraries.addAll(filtered)
            viewModelScope.launch(context = Dispatchers.Main) {
                onLoaded(filtered)
            }
        }
    }

    fun getAvailableLibraries(): List<MediaLibraryEntity> {
        return _availableLibraries.toList()
    }

    fun deleteMulu(data: MuluConfigEntity) {
        viewModelScope.launch(context = Dispatchers.IO) {
            DatabaseManager.instance.getMuluConfigDao().deleteById(data.id)
        }
    }

    fun addMulu(config: MuluConfigEntity) {
        viewModelScope.launch(context = Dispatchers.IO) {
            DatabaseManager.instance.getMuluConfigDao().insert(config)
        }
    }
}
