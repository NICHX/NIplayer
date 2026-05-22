package com.xyoye.storage_component.ui.fragment.storage_file

import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.config.AppConfig
import com.xyoye.common_component.config.ThumbnailConfig
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.StorageSortOption
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.utils.ThumbnailGeneratorManager
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.data_component.enums.FileFilterType
import com.xyoye.data_component.enums.MediaType
import com.xyoye.data_component.enums.TrackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StorageFileFragmentViewModel : BaseViewModel() {
    companion object {
        private val lastPlayDirectory = PlayHistoryEntity(
            url = "",
            mediaType = MediaType.OTHER_STORAGE,
            videoName = ""
        ).apply {
            isLastPlay = true
        }
    }

    private val _fileLiveData = MutableLiveData<List<StorageFile>>()
    val fileLiveData: LiveData<List<StorageFile>> = _fileLiveData

    private val _filterTypes = MutableLiveData<Set<FileFilterType>>(emptySet())
    val filterTypes: LiveData<Set<FileFilterType>> = _filterTypes

    lateinit var storage: Storage

    //当前媒体库中最后一次播放记录
    private var storageLastPlay: PlayHistoryEntity? = null

    // 是否隐藏.开头的文件
    private val hidePointFile = AppConfig.isShowHiddenFile().not()

    // 文件列表快照
    private var filesSnapshot = listOf<StorageFile>()

    // 完整文件列表（未过滤）
    private var _allFiles = listOf<StorageFile>()

    /**
     * 展开文件夹
     */
    fun listFile(directory: StorageFile?, refresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val target = directory ?: storage.getRootFile()
            if (target == null) {
                emptyList<StorageFile>()
                    .apply { _fileLiveData.postValue(this) }
                    .also { filesSnapshot = it }
                return@launch
            }

            refreshStorageLastPlay()
            val originalFileList = storage.openDirectory(target, refresh)
            val fileList = originalFileList
                .filter { isDisplayFile(it) }
                .sortedWith(StorageSortOption.comparator())
                .map { updateStorageFileHistory(it, getHistory(it)) }
                .also { filesSnapshot = it }

            _allFiles = fileList

            ThumbnailGeneratorManager.preloadExistingThumbs(originalFileList, storage)
            ThumbnailGeneratorManager.preloadCoverPaths(originalFileList)
            _fileLiveData.postValue(fileList)
            ThumbnailGeneratorManager.startGenerateThumbnails(fileList, storage, allFiles = originalFileList)
        }
    }

    /**
     * 修改文件排序
     */
    fun changeSortOption() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentFiles = _allFiles
            val sorted = mutableListOf<StorageFile>()
                .plus(currentFiles)
                .sortedWith(StorageSortOption.comparator())
                .also { filesSnapshot = it }
            _allFiles = sorted
            applyFilter()
        }
    }

    /**
     * 搜索文件
     */
    fun searchByText(text: String) {
        //媒体库支持文件搜索，由媒体库处理搜索
        if (storage.supportSearch()) {
            viewModelScope.launch(Dispatchers.IO) {
                refreshStorageLastPlay()
                storage.search(text)
                    .filter { isDisplayFile(it) }
                    .sortedWith(StorageSortOption.comparator())
                    .map { updateStorageFileHistory(it, getHistory(it)) }
                    .let { _fileLiveData.postValue(it) }
            }
            return
        }

        //搜索条件为空，返回文件列表快照
        if (text.isEmpty()) {
            filesSnapshot = _allFiles
            applyFilter()
            return
        }

        //在当前文件列表进行搜索
        val currentFiles = _allFiles
        val searched = mutableListOf<StorageFile>()
            .plus(currentFiles)
            .filter { it.fileName().contains(text) }
            .also { filesSnapshot = it }
        applyFilter()
    }

    /**
     * 绑定音频文件
     */
    fun bindAudioSource(file: StorageFile, audioPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val playHistory = getStorageFileHistory(file)
            playHistory.audioPath = audioPath
            DatabaseManager.instance.getPlayHistoryDao().insert(playHistory)

            // 更新文件列表的播放历史
            updateHistory()
        }
    }

    /**
     * 解绑资源文件
     */
    fun unbindExtraSource(file: StorageFile, resource: TrackType) {
        viewModelScope.launch(Dispatchers.IO) {
            when (resource) {
                TrackType.SUBTITLE -> {
                    DatabaseManager.instance.getPlayHistoryDao().updateSubtitle(
                        file.uniqueKey(), storage.library.id, null
                    )
                }

                TrackType.AUDIO -> {
                    DatabaseManager.instance.getPlayHistoryDao().updateAudio(
                        file.uniqueKey(), file.storage.library.id, null
                    )
                }

                else -> {
                    // DANMU track type is no longer supported
                }
            }
            updateHistory()
        }
    }

    /**
     * 更新文件相关的播放历史
     */
    fun updateHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val fileList = _allFiles
            if (fileList.isEmpty()) return@launch

            refreshStorageLastPlay()
            val updated = fileList
                .map { updateStorageFileHistory(it, getHistory(it)) }
                .also { filesSnapshot = it }
            _allFiles = updated
            applyFilter()
        }
    }

    fun setFilterTypes(types: Set<FileFilterType>) {
        _filterTypes.value = types
        applyFilter()
    }

    private fun applyFilter() {
        val currentTypes = _filterTypes.value ?: emptySet()
        val files = filesSnapshot

        val filtered = if (currentTypes.isEmpty()) {
            files
        } else {
            files.filter { file ->
                when {
                    file.isDirectory() -> currentTypes.contains(FileFilterType.FOLDER)
                    file.isVideoFile() -> currentTypes.contains(FileFilterType.VIDEO)
                    file.isAudioFile() -> currentTypes.contains(FileFilterType.AUDIO)
                    file.isImageFile() -> currentTypes.contains(FileFilterType.IMAGE)
                    else -> false
                }
            }
        }
        _fileLiveData.postValue(filtered)
    }

    /**
     * 获取文件播放历史
     */
    private suspend fun getHistory(file: StorageFile): PlayHistoryEntity? {
        if (file.isDirectory()) {
            return lastPlayDirectoryHistory(file)
        }
        if (file.isVideoFile().not()) {
            return null
        }
        var history = DatabaseManager.instance
            .getPlayHistoryDao()
            .getPlayHistory(file.uniqueKey(), file.storage.library.id)
        if (history == null) {
            //这是一步补救措施，数据库11版本之前，没有存储storageId字段
            //因此为了避免弹幕等历史数据无法展示，依旧需要通过mediaType查询
            history = DatabaseManager.instance
                .getPlayHistoryDao()
                .getPlayHistory(file.uniqueKey(), file.storage.library.mediaType)
            //补充storageId字段
            if (history != null) {
                history.storageId = file.storage.library.id
                DatabaseManager.instance.getPlayHistoryDao().insert(history)
            }
        }
        if (history != null && storageLastPlay != null) {
            history.isLastPlay = history.id == storageLastPlay!!.id
        }
        return history
    }

    /**
     * 刷新最后一次播放记录
     */
    private suspend fun refreshStorageLastPlay() {
        storageLastPlay = DatabaseManager.instance
            .getPlayHistoryDao()
            .gitStorageLastPlay(storage.library.id)
        storageLastPlay?.isLastPlay = true
    }

    /**
     * 文件夹是否为最后一次播放记录的父文件夹
     * 是：返回最后播放的标签
     * 否：null
     */
    private fun lastPlayDirectoryHistory(file: StorageFile): PlayHistoryEntity? {
        val lastPlayStoragePath = storageLastPlay?.storagePath
            ?: return null
        if (TextUtils.isEmpty(lastPlayStoragePath)) {
            return null
        }
        if (file.isStoragePathParent(lastPlayStoragePath).not()) {
            return null
        }
        return lastPlayDirectory
    }

    /**
     * 是否可展示的文件
     */
    private fun isDisplayFile(storageFile: StorageFile): Boolean {
        //.开头的文件，根据配置展示
        if (hidePointFile && storageFile.fileName().startsWith(".")) {
            return false
        }
        //以-thumb.jpg结尾的文件，仅在为视频生成缩略图时隐藏
        if (storageFile.fileName().endsWith("-thumb.jpg")) {
            if (ThumbnailConfig.isGenerateThumbnail() && ThumbnailConfig.isGenerateForVideo()) {
                return false
            }
        }
        //文件夹，展示
        if (storageFile.isDirectory()) {
            return true
        }
        //视频文件，展示
        if (storageFile.isVideoFile()) {
            return true
        }
        //音频文件，展示
        if (storageFile.isAudioFile()) {
            return true
        }
        //图片文件，展示
        if (storageFile.isImageFile()) {
            return true
        }
        return false
    }

    private suspend fun getStorageFileHistory(storageFile: StorageFile): PlayHistoryEntity {
        return DatabaseManager.instance.getPlayHistoryDao().getPlayHistory(
            storageFile.uniqueKey(),
            storageFile.storage.library.id
        ) ?: PlayHistoryEntity(
            0,
            "",
            "",
            mediaType = storageFile.storage.library.mediaType,
            uniqueKey = storageFile.uniqueKey(),
            storageId = storageFile.storage.library.id,
        )
    }

    private fun updateStorageFileHistory(
        file: StorageFile,
        history: PlayHistoryEntity?
    ): StorageFile {
        file.storage.updateFileHistory(file, history)
        return file.clone().apply {
            playHistory = history
        }
    }
}