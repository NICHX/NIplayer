package com.xyoye.local_component.ui.activities.quick_access

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.factory.StorageVideoSourceFactory
import com.xyoye.common_component.storage.StorageFactory
import com.xyoye.common_component.utils.QuickAccessHelper
import com.xyoye.common_component.utils.ThumbnailGeneratorManager
import com.xyoye.common_component.utils.isAudioFile
import com.xyoye.common_component.utils.isImageFile
import com.xyoye.common_component.utils.isVideoFile
import com.xyoye.data_component.bean.QuickAccessItem
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.enums.FileFilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickAccessViewModel : BaseViewModel() {

    private val _quickAccessLiveData = MutableLiveData<List<QuickAccessItem>>()
    val quickAccessLiveData = _quickAccessLiveData

    var isGridView: Boolean = false

    private val _filterTypes = MutableLiveData<Set<FileFilterType>>(emptySet())
    val filterTypes: LiveData<Set<FileFilterType>> = _filterTypes

    private var _allItems = listOf<QuickAccessItem>()

    fun loadQuickAccessItems() {
        val items = QuickAccessHelper.getQuickAccessList()
        _allItems = items
        applyFilter()

        viewModelScope.launch(Dispatchers.IO) {
            preloadFileThumbnails(items)
        }
    }

    private suspend fun preloadFileThumbnails(items: List<QuickAccessItem>) {
        var hasUpdates = false
        val mutableItems = items.toMutableList()

        items.filter { !it.isDirectory }.groupBy { it.libraryId }.forEach { (libraryId, libItems) ->
            val library = DatabaseManager.instance.getMediaLibraryDao().getById(libraryId.toInt()) ?: return@forEach
            val storage = StorageFactory.createStorage(library) ?: return@forEach
            try {
                for (item in libItems) {
                    val file = storage.pathFile(item.storagePath, false) ?: continue
                    ThumbnailGeneratorManager.ensureThumbnail(file)

                    if (item.uniqueKey.isEmpty()) {
                        val correctKey = file.uniqueKey()
                        if (correctKey.isNotEmpty()) {
                            val index = mutableItems.indexOf(item)
                            if (index >= 0) {
                                mutableItems[index] = item.copy(uniqueKey = correctKey)
                                hasUpdates = true
                            }
                        }
                    }
                }
            } finally {
                storage.close()
            }
        }

        if (hasUpdates) {
            QuickAccessHelper.saveQuickAccessItems(mutableItems)
        }
        _quickAccessLiveData.postValue(QuickAccessHelper.getQuickAccessList())
    }

    fun toggleViewMode() {
        isGridView = !isGridView
    }

    fun setFilterTypes(types: Set<FileFilterType>) {
        _filterTypes.value = types
        applyFilter()
    }

    private fun applyFilter() {
        val currentTypes = _filterTypes.value ?: emptySet()
        val items = _allItems

        val filtered = if (currentTypes.isEmpty()) {
            items
        } else {
            items.filter { item ->
                when {
                    item.isDirectory -> currentTypes.contains(FileFilterType.FOLDER)
                    isVideoFile(item.name) -> currentTypes.contains(FileFilterType.VIDEO)
                    isAudioFile(item.name) -> currentTypes.contains(FileFilterType.AUDIO)
                    isImageFile(item.name) -> currentTypes.contains(FileFilterType.IMAGE)
                    else -> false
                }
            }
        }
        _quickAccessLiveData.postValue(filtered)
    }

    fun openItem(item: QuickAccessItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val library = DatabaseManager.instance.getMediaLibraryDao()
                .getById(item.libraryId.toInt())
            if (library == null) return@launch

            if (item.isDirectory) {
                withContext(Dispatchers.Main) {
                    ARouter.getInstance()
                        .build(RouteTable.Stream.StorageFile)
                        .withParcelable("storageLibrary", library)
                        .withString("initialStoragePath", item.storagePath)
                        .navigation()
                }
            } else {
                playFile(library, item.storagePath)
            }
        }
    }

    private suspend fun playFile(library: MediaLibraryEntity, storagePath: String) {
        val storage = StorageFactory.createStorage(library) ?: return
        val file = storage.pathFile(storagePath, false)
        if (file == null) {
            storage.close()
            return
        }

        val playHistory = DatabaseManager.instance.getPlayHistoryDao().getPlayHistory(
            file.uniqueKey(), library.id
        )
        file.playHistory = playHistory

        val source = StorageVideoSourceFactory.create(file)
        if (source != null) {
            VideoSourceManager.getInstance().setSource(source)
            withContext(Dispatchers.Main) {
                ARouter.getInstance()
                    .build(RouteTable.Player.Player)
                    .navigation()
            }
        }
    }

    fun removeItem(item: QuickAccessItem) {
        QuickAccessHelper.removeQuickAccess(item)
        loadQuickAccessItems()
    }
}