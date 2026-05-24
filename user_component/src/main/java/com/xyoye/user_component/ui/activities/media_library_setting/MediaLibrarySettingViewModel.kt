package com.xyoye.user_component.ui.activities.media_library_setting

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.scrape.ScrapeEngine
import com.xyoye.common_component.storage.StorageFactory
import com.xyoye.data_component.entity.MuluConfigEntity
import com.xyoye.data_component.entity.ScrapeMediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaLibraryItem(
    val type: String,
    val typeLabel: String,
    val pathCount: Int
)

sealed class ScanState {
    object Idle : ScanState()
    data class InProgress(val message: String) : ScanState()
    data class Done(val success: Boolean, val message: String) : ScanState()
}

enum class ScanMode {
    SCAN_ONLY,
    SCAN_AND_SCRAPE
}

class MediaLibrarySettingViewModel : BaseViewModel() {

    private val _libraryListLiveData = MediatorLiveData<MutableList<MediaLibraryItem>>()
    val libraryListLiveData: LiveData<MutableList<MediaLibraryItem>> = _libraryListLiveData

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    private var librarySource: LiveData<MutableList<MuluConfigEntity>>? = null

    val allTypeOptions = listOf(
        "movie" to "电影",
        "tv" to "电视剧",
        "variety" to "综艺",
        "anime" to "动漫",
        "documentary" to "纪录片",
        "concert" to "演唱会",
        "other" to "其他"
    )

    init {
        loadLibraryList()
    }

    fun loadLibraryList() {
        librarySource?.let { _libraryListLiveData.removeSource(it) }
        val source = DatabaseManager.instance.getMuluConfigDao().getAll()
        librarySource = source
        _libraryListLiveData.addSource(source) { allEntities ->
            val grouped = allEntities
                .filter { it.path.isNotBlank() }
                .groupBy { it.muluType }
                .map { (type, items) ->
                    val label = allTypeOptions.firstOrNull { it.first == type }?.second ?: type
                    MediaLibraryItem(type, label, items.size)
                }
                .sortedBy { it.type }
            _libraryListLiveData.postValue(grouped.toMutableList())
        }
    }

    fun startScan(mode: ScanMode) {
        if (_scanState.value is ScanState.InProgress) return
        _scanState.postValue(ScanState.InProgress("准备扫描..."))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val muluConfigs = DatabaseManager.instance.getMuluConfigDao().getAllSuspend()
                    .filter { it.path.isNotBlank() }

                if (muluConfigs.isEmpty()) {
                    _scanState.postValue(ScanState.Done(false, "没有已配置的扫描目录，请先添加媒体库目录"))
                    return@launch
                }

                val libraries = DatabaseManager.instance.getMediaLibraryDao().getAllSuspend()
                val scrapeEngine = ScrapeEngine()
                var totalFound = 0

                val grouped = muluConfigs.groupBy { it.muluType }

                for ((type, configs) in grouped) {
                    val libraryIds = configs.map { it.mediaLibraryId }.distinct()

                    for (libraryId in libraryIds) {
                        val library = libraries.find { it.id == libraryId } ?: continue
                        val storage = StorageFactory.createStorage(library) ?: continue

                        val typePaths = configs
                            .filter { it.mediaLibraryId == libraryId }
                            .map { it.path }

                        for (path in typePaths) {
                            _scanState.postValue(ScanState.InProgress("扫描: ${library.displayName} - ${path}"))

                            val scannedItems = if (type == "movie") {
                                scrapeEngine.scanMovies(storage, path) { }
                            } else {
                                scrapeEngine.scanTv(storage, path) { }
                            }

                            if (scannedItems.isEmpty()) continue

                            val entities = if (type == "movie") {
                                scrapeEngine.convertMovieItems(scannedItems)
                            } else {
                                scrapeEngine.groupBySource(scannedItems)
                            }

                            val typeLabel = allTypeOptions.firstOrNull { it.first == type }?.second ?: type
                            for (entity in entities) {
                                DatabaseManager.instance.getScrapeMediaDao().insert(
                                    entity.copy(mediaType = type)
                                )
                            }

                            totalFound += scannedItems.size
                            _scanState.postValue(ScanState.InProgress("已扫描 (${typeLabel}): $path — ${scannedItems.size}个文件"))
                        }
                    }
                }

                if (mode == ScanMode.SCAN_AND_SCRAPE) {
                    _scanState.postValue(ScanState.InProgress("开始刮削 ${totalFound} 个媒体项..."))

                    val tmdbApiKey = com.xyoye.common_component.config.TmdbApiConfig.apiKey
                    if (tmdbApiKey.isEmpty()) {
                        _scanState.postValue(ScanState.Done(
                            true,
                            "扫描完成，共发现 $totalFound 个媒体文件\n刮削已跳过：未配置TMDB API Key"
                        ))
                        return@launch
                    }

                    var scrapedCount = 0
                    for ((type, configs) in grouped) {
                        val libraryIds = configs.map { it.mediaLibraryId }.distinct()

                        for (libraryId in libraryIds) {
                            val library = libraries.find { it.id == libraryId } ?: continue
                            val storage = StorageFactory.createStorage(library) ?: continue

                            val typePaths = configs
                                .filter { it.mediaLibraryId == libraryId }
                                .map { it.path }

                            val allScanned = mutableListOf<ScrapeMediaEntity>()

                            for (path in typePaths) {
                                val scannedItems = if (type == "movie") {
                                    scrapeEngine.scanMovies(storage, path) { }
                                } else {
                                    scrapeEngine.scanTv(storage, path) { }
                                }
                                if (scannedItems.isEmpty()) continue

                                val entities = if (type == "movie") {
                                    scrapeEngine.convertMovieItems(scannedItems)
                                } else {
                                    scrapeEngine.groupBySource(scannedItems)
                                }
                                allScanned.addAll(entities.map { it.copy(mediaType = type) })
                            }

                            if (allScanned.isEmpty()) continue

                            val existingData = DatabaseManager.instance.getScrapeMediaDao()
                                .getByMediaTypeSuspend(type)

                            _scanState.postValue(ScanState.InProgress("刮削: ${typeLabel(type)}..."))

                            val matched = withContext(Dispatchers.IO) {
                                scrapeEngine.matchTmdbMetadata(
                                    items = allScanned,
                                    type = type,
                                    tmdbKey = tmdbApiKey,
                                    existingData = existingData,
                                    storage = storage
                                )
                            }

                            for (entity in matched) {
                                DatabaseManager.instance.getScrapeMediaDao().insert(entity)
                            }
                            scrapedCount += matched.size
                        }
                    }

                    _scanState.postValue(ScanState.Done(
                        true,
                        "扫描完成，共发现 $totalFound 个媒体文件\n刮削完成，共匹配 $scrapedCount 个媒体项"
                    ))
                } else {
                    _scanState.postValue(ScanState.Done(true, "扫描完成，共发现 $totalFound 个媒体文件"))
                }
            } catch (e: Exception) {
                _scanState.postValue(ScanState.Done(false, "扫描失败: ${e.message}"))
            }
        }
    }

    fun resetScanState() {
        _scanState.postValue(ScanState.Idle)
    }

    private fun typeLabel(type: String): String {
        return allTypeOptions.firstOrNull { it.first == type }?.second ?: type
    }
}