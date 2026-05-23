package com.xyoye.local_component.ui.fragment.mine

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.scrape.ScrapeEngine
import com.xyoye.common_component.scrape.TmdbMetadataSyncManager
import com.xyoye.common_component.storage.StorageFactory
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.entity.EpisodeEntity
import com.xyoye.data_component.entity.ScrapeMediaEntity
import com.xyoye.data_component.entity.TmdbSyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MineFragmentViewModel : BaseViewModel() {

    val currentType = MutableLiveData("movie")

    private val _scrapeMediaLiveData = MediatorLiveData<MutableList<ScrapeMediaEntity>>()
    val scrapeMediaLiveData: LiveData<MutableList<ScrapeMediaEntity>> = _scrapeMediaLiveData

    private var currentMediaSource: LiveData<MutableList<ScrapeMediaEntity>>? = null

    private val scrapeEngine = ScrapeEngine()
    private val metadataSyncManager = TmdbMetadataSyncManager()

    init {
        currentType.observeForever { type ->
            switchMediaSource(type)
        }
        metadataSyncManager.start()
    }

    override fun onCleared() {
        super.onCleared()
        metadataSyncManager.destroy()
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

                    for (media in matched) {
                        // 对电视剧，扫描并保存剧集文件到独立的 episode 表
                        if (type == "tv") {
                            val insertedMedia = DatabaseManager.instance.getScrapeMediaDao()
                                .getByPath(media.path, "tv") ?: continue

                            var episodeFiles = scrapeEngine.scanTvEpisodeFiles(storage, media.path)
                            if (episodeFiles.isEmpty()) {
                                val parentDir = media.path.trimEnd('/').substringBeforeLast('/')
                                if (parentDir.isNotEmpty()) {
                                    episodeFiles = scrapeEngine.scanTvEpisodeFiles(storage, parentDir)
                                }
                            }

                            if (episodeFiles.isNotEmpty()) {
                                val episodeEntities = episodeFiles.map { ep ->
                                    EpisodeEntity(
                                        mediaId = insertedMedia.id,
                                        seasonNumber = ep.seasonNumber,
                                        episodeNumber = if (ep.episodeNumber > 0) ep.episodeNumber else 1,
                                        filePath = ep.filePath,
                                        fileName = ep.fileName,
                                        fileSize = ep.fileSize
                                    )
                                }
                                DatabaseManager.instance.getEpisodeDao().insertEpisodes(episodeEntities)
                            }
                        }

                        // 如果有 TMDB ID，则入队异步元数据补全
                        val tmdbId = media.tmdbId
                        if (tmdbId != null && tmdbId > 0) {
                            metadataSyncManager.enqueueMedia(tmdbId)
                        }
                    }
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
        return com.xyoye.common_component.config.TmdbApiConfig.apiKey
    }
}
