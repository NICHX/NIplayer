package com.xyoye.local_component.ui.fragment.mine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.scrape.ScrapeEngine
import com.xyoye.common_component.storage.StorageFactory
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.entity.ScrapeMediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MineFragmentViewModel : BaseViewModel() {

    val currentType = MutableLiveData("movie")

    private val _scrapeMediaLiveData = MediatorLiveData<MutableList<ScrapeMediaEntity>>()
    val scrapeMediaLiveData: LiveData<MutableList<ScrapeMediaEntity>> = _scrapeMediaLiveData

    private var currentMediaSource: LiveData<MutableList<ScrapeMediaEntity>>? = null

    private val scrapeEngine = ScrapeEngine()

    init {
        currentType.observeForever { type ->
            switchMediaSource(type)
        }
    }

    private fun switchMediaSource(type: String) {
        currentMediaSource?.let { _scrapeMediaLiveData.removeSource(it) }

        val source = DatabaseManager.instance.getScrapeMediaDao().getByMediaType(type)
        currentMediaSource = source
        _scrapeMediaLiveData.addSource(source) { data ->
            _scrapeMediaLiveData.postValue(data)
        }
    }

    fun switchType(type: String) {
        if (currentType.value != type) {
            currentType.postValue(type)
        }
    }

    fun refreshScrapeData() {
        val type = currentType.value ?: "movie"
        viewModelScope.launch(context = Dispatchers.IO) {
            try {
                val configs = DatabaseManager.instance.getMuluConfigDao()
                    .getByMuluTypeSuspend(type)

                if (configs.isEmpty()) {
                    ToastCenter.showWarning("未找到刮削目录配置，请先添加目录")
                    return@launch
                }

                for (config in configs) {
                    val library = DatabaseManager.instance.getMediaLibraryDao()
                        .getById(config.mediaLibraryId)

                    if (library == null) {
                        ToastCenter.showError("媒体库不存在: id=${config.mediaLibraryId}")
                        continue
                    }

                    val storage = StorageFactory.createStorage(library)

                    if (storage == null) {
                        ToastCenter.showError("无法创建存储: ${library.displayName}")
                        continue
                    }

                    val items = if (type == "movie") {
                        scrapeEngine.scanMovies(storage, config.path) { }
                    } else {
                        scrapeEngine.scanTv(storage, config.path) { }
                    }

                    if (items.isEmpty()) {
                        ToastCenter.showWarning("目录中未找到视频文件: ${config.path}")
                        continue
                    }

                    val grouped = if (type == "movie") {
                        scrapeEngine.convertMovieItems(items)
                    } else {
                        scrapeEngine.groupBySource(items)
                    }

                    val existingList = DatabaseManager.instance.getScrapeMediaDao()
                        .getByMediaTypeSuspend(type)

                    val tmdbKey = getTmdbApiKey()
                    val matched = if (tmdbKey.isNotEmpty()) {
                        scrapeEngine.matchTmdbMetadata(grouped, type, tmdbKey, existingList, storage)
                    } else {
                        grouped
                    }

                    DatabaseManager.instance.getScrapeMediaDao().insert(*matched.toTypedArray())
                    ToastCenter.showSuccess("刮削完成，共${matched.size}个${if (type == "movie") "电影" else "电视剧"}")
                }
            } catch (e: Exception) {
                ToastCenter.showError("刮削失败: ${e.message}")
            }
        }
    }

    fun deleteScrapeMedia(data: ScrapeMediaEntity) {
        viewModelScope.launch(context = Dispatchers.IO) {
            DatabaseManager.instance.getScrapeMediaDao().deleteById(data.id)
        }
    }

    private fun getTmdbApiKey(): String {
        return com.tencent.mmkv.MMKV.defaultMMKV()
            ?.decodeString("tmdb_api_key", "") ?: ""
    }
}
