package com.xyoye.local_component.ui.fragment.mine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.scrape.ScrapeEngine
import com.xyoye.common_component.storage.StorageFactory
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
            val configs = DatabaseManager.instance.getMuluConfigDao()
                .getByMuluTypeSuspend(type)

            if (configs.isEmpty()) return@launch

            for (config in configs) {
                val library = DatabaseManager.instance.getMediaLibraryDao()
                    .getById(config.mediaLibraryId) ?: continue

                val storage = StorageFactory.createStorage(library) ?: continue

                val items = if (type == "movie") {
                    scrapeEngine.scanMovies(storage, config.path) { }
                } else {
                    scrapeEngine.scanTv(storage, config.path) { }
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
