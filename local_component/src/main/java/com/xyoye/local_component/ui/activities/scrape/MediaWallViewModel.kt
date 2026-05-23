package com.xyoye.local_component.ui.activities.scrape

import android.util.Log
import androidx.lifecycle.LiveData
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class MediaSection(
    val typeName: String,
    val typeKey: String,
    val items: List<ScrapeMediaEntity>
)

class MediaWallViewModel : BaseViewModel() {

    companion object {
        private const val TAG = "MediaWallVM"
    }

    private val _sections = MutableLiveData<List<MediaSection>>()
    val sections: LiveData<List<MediaSection>> = _sections

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val sectionOrder = listOf(
        "movie" to "电影",
        "tv" to "电视剧"
    )

    private val scrapeEngine = ScrapeEngine()
    private val metadataSyncManager = TmdbMetadataSyncManager()

    private var isScraping = false

    init {
        metadataSyncManager.start()
    }

    override fun onCleared() {
        super.onCleared()
        metadataSyncManager.destroy()
    }

    fun loadMedia() {
        viewModelScope.launch(context = Dispatchers.IO) {
            _isRefreshing.postValue(true)
            try {
                val allMedia = DatabaseManager.instance.getScrapeMediaDao().getAllSuspend()
                Log.d(TAG, "loadMedia: total=${allMedia.size}")
                val sections = sectionOrder.mapNotNull { (typeKey, typeName) ->
                    val items = allMedia.filter { it.mediaType == typeKey }
                    Log.d(TAG, "  $typeName: ${items.size} items")
                    if (items.isEmpty()) null
                    else MediaSection(typeName, typeKey, items)
                }
                _sections.postValue(sections)
            } finally {
                _isRefreshing.postValue(false)
            }
        }
    }

    fun refreshScrapeData() {
        if (isScraping) {
            Log.d(TAG, "Already scraping, skipping duplicate")
            ToastCenter.showInfo("刮削任务正在进行中，请稍候")
            return
        }
        isScraping = true
        viewModelScope.launch(context = Dispatchers.IO) {
            showLoading("正在刮削媒体信息...")
            Log.d(TAG, "=== start refreshScrapeData ===")
            try {
                var totalNew = 0
                for ((typeKey, typeName) in sectionOrder) {
                    Log.d(TAG, "Processing type: $typeKey ($typeName)")
                    val configs = DatabaseManager.instance.getMuluConfigDao()
                        .getByMuluTypeSuspend(typeKey)

                    if (configs.isEmpty()) {
                        Log.d(TAG, "  No configs for $typeKey, clearing old data")
                        val deleted = DatabaseManager.instance.getScrapeMediaDao().deleteByMediaType(typeKey)
                        Log.d(TAG, "  Deleted $deleted stale $typeName entries")
                        continue
                    }

                    val deleted = DatabaseManager.instance.getScrapeMediaDao().deleteByMediaType(typeKey)
                    Log.d(TAG, "  Cleared $deleted old entries before re-scanning")

                    for (config in configs) {
                        Log.d(TAG, "  Config: path=${config.path}")
                        val library = DatabaseManager.instance.getMediaLibraryDao()
                            .getById(config.mediaLibraryId)

                        if (library == null) {
                            Log.w(TAG, "  Library not found: id=${config.mediaLibraryId}")
                            continue
                        }

                        val storage = StorageFactory.createStorage(library) ?: continue

                        val items = if (typeKey == "movie") {
                            scrapeEngine.scanMovies(storage, config.path) { }
                        } else {
                            scrapeEngine.scanTv(storage, config.path) { }
                        }

                        if (items.isEmpty()) {
                            Log.d(TAG, "  No files found")
                            continue
                        }
                        Log.d(TAG, "  Scanned ${items.size} items")

                        val grouped = if (typeKey == "movie") {
                            scrapeEngine.convertMovieItems(items)
                        } else {
                            scrapeEngine.groupBySource(items)
                        }
                        Log.d(TAG, "  Grouped into ${grouped.size} entries")

                        val tmdbKey = getTmdbApiKey()
                        Log.d(TAG, "  TMDB key available: ${tmdbKey.isNotEmpty()}")
                        val matched = if (tmdbKey.isNotEmpty()) {
                            scrapeEngine.matchTmdbMetadata(grouped, typeKey, tmdbKey, emptyList(), storage)
                        } else {
                            Log.w(TAG, "  No TMDB API key, skipping TMDB matching!")
                            grouped
                        }

                        for (media in matched) {
                            DatabaseManager.instance.getScrapeMediaDao().insert(media)
                            totalNew++
                            Log.d(TAG, "  Inserted: ${media.name}")
                        }

                        for (media in matched) {
                            if (typeKey == "tv") {
                                val insertedMedia = DatabaseManager.instance.getScrapeMediaDao()
                                    .getByPath(media.path, "tv")
                                if (insertedMedia == null) {
                                    Log.w(TAG, "  Cannot find inserted TV media: ${media.path}")
                                    continue
                                }

                                var episodeFiles = scrapeEngine.scanTvEpisodeFiles(storage, media.path)
                                if (episodeFiles.isEmpty()) {
                                    val parentDir = media.path.trimEnd('/').substringBeforeLast('/')
                                    if (parentDir.isNotEmpty()) {
                                        episodeFiles = scrapeEngine.scanTvEpisodeFiles(storage, parentDir)
                                    }
                                }
                                Log.d(TAG, "  Episodes found: ${episodeFiles.size}")

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

                            val tmdbId = media.tmdbId
                            if (tmdbId != null && tmdbId > 0) {
                                Log.d(TAG, "  Enqueue sync for tmdbId=$tmdbId")
                                metadataSyncManager.enqueueMedia(tmdbId)
                            }
                        }
                    }
                }

                if (totalNew > 0) {
                    ToastCenter.showSuccess("刮削完成，新增 $totalNew 个")
                    Log.d(TAG, "Scrape done: new=$totalNew")
                } else {
                    ToastCenter.showInfo("未找到新的媒体文件")
                    Log.d(TAG, "No new files found")
                }

                loadMedia()
            } catch (e: Exception) {
                Log.e(TAG, "Scrape failed", e)
                ToastCenter.showError("刮削失败: ${e.message}")
            } finally {
                isScraping = false
                hideLoading()
                Log.d(TAG, "=== end refreshScrapeData ===")
            }
        }
    }

    fun deleteMedia(media: ScrapeMediaEntity) {
        viewModelScope.launch(context = Dispatchers.IO) {
            Log.d(TAG, "Delete: ${media.name} (id=${media.id})")
            DatabaseManager.instance.getEpisodeDao().deleteEpisodesByMediaId(media.id)
            DatabaseManager.instance.getScrapeMediaDao().deleteById(media.id)
            loadMedia()
        }
    }

    private fun getTmdbApiKey(): String {
        return com.xyoye.common_component.config.TmdbApiConfig.apiKey
    }
}