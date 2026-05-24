package com.xyoye.common_component.scrape

import android.util.Log
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.data_component.entity.TmdbMediaDetail
import com.xyoye.data_component.entity.TmdbSyncQueueEntity
import com.xyoye.data_component.entity.TmdbSyncQueueEntity.State
import com.xyoye.data_component.entity.TmdbSyncQueueEntity.TaskType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext

class TmdbMetadataSyncManager {

    companion object {
        private const val TAG = "TmdbSyncManager"
        private const val MAX_CONCURRENCY = 1
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2_000L
        private const val POLL_INTERVAL_MS = 1_000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val concurrencyGate = Semaphore(MAX_CONCURRENCY)

    @Volatile
    private var isRunning = false

    private var pollingJob: Job? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        pollingJob = scope.launch {
            startPolling()
        }
    }

    fun stop() {
        isRunning = false
        pollingJob?.cancel()
        pollingJob = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    fun enqueue(tmdbId: Int, taskType: TaskType, seasonNumber: Int? = null, priority: Int = 0) {
        val key = when (taskType) {
            TaskType.MEDIA -> TmdbSyncQueueEntity.mediaKey(tmdbId)
            TaskType.SEASON_EPISODES -> TmdbSyncQueueEntity.seasonKey(tmdbId, seasonNumber ?: 1)
        }
        scope.launch {
            doEnqueue(key, tmdbId, taskType, seasonNumber, priority)
        }
    }

    fun enqueueMedia(tmdbId: Int) {
        enqueue(tmdbId, TaskType.MEDIA)
    }

    fun enqueueSeasonEpisodes(tmdbId: Int, seasonNumber: Int) {
        enqueue(tmdbId, TaskType.SEASON_EPISODES, seasonNumber)
    }

    private suspend fun startPolling() {
        while (isRunning) {
            try {
                val pendingCount = DatabaseManager.instance.getTmdbSyncQueueDao().getPendingCount()
                if (pendingCount > 0) {
                    val task = DatabaseManager.instance.getTmdbSyncQueueDao().getNextPending(State.PENDING.name)
                    if (task != null) {
                        processTask(task)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Polling error: ${e.message}")
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun doEnqueue(
        key: String,
        tmdbId: Int,
        taskType: TaskType,
        seasonNumber: Int?,
        priority: Int
    ) {
        try {
            val existing = DatabaseManager.instance.getTmdbSyncQueueDao().isPendingOrRunning(key)
            if (existing) return
            DatabaseManager.instance.getTmdbSyncQueueDao().upsert(
                TmdbSyncQueueEntity(
                    key = key,
                    tmdbId = tmdbId,
                    taskType = taskType,
                    seasonNumber = seasonNumber,
                    priority = priority
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Enqueue error: ${e.message}")
        }
    }

    private suspend fun processTask(task: TmdbSyncQueueEntity) = withContext(Dispatchers.IO) {
        concurrencyGate.acquire()
        try {
            DatabaseManager.instance.getTmdbSyncQueueDao().updateState(
                task.key, State.RUNNING.name
            )
            when (task.taskType) {
                TaskType.MEDIA -> processMediaSync(task)
                TaskType.SEASON_EPISODES -> processSeasonSync(task)
            }
            DatabaseManager.instance.getTmdbSyncQueueDao().updateState(
                task.key, State.DONE.name
            )
        } catch (e: Exception) {
            Log.w(TAG, "Task failed: ${task.key}, error: ${e.message}")
            handleFailure(task, e.message)
        } finally {
            concurrencyGate.release()
        }
    }

    private suspend fun processMediaSync(task: TmdbSyncQueueEntity) {
        Log.d(TAG, "processMediaSync: tmdbId=${task.tmdbId}")
        val apiKey = getTmdbApiKey() ?: throw Exception("TMDB API key not configured")
        val media = DatabaseManager.instance.getScrapeMediaDao().getByTmdbId(task.tmdbId)
            ?: throw Exception("Media not found for tmdbId=${task.tmdbId}")

        Log.d(TAG, "  Media: name=${media.name}, type=${media.mediaType}")
        val repository = TmdbRepository()
        var type = media.mediaType
        val detail: TmdbMediaDetail

        try {
            detail = repository.getMediaDetail(task.tmdbId, type, apiKey)
        } catch (firstError: Exception) {
            val msg = firstError.message ?: ""
            if (msg.contains("404") || msg.contains("HTTP 404") || msg.contains("HTTP 404")) {
                val altType = if (type == "tv") "movie" else "tv"
                Log.w(TAG, "  404 for type=$type, retrying as $altType")
                try {
                    val altDetail = repository.getMediaDetail(task.tmdbId, altType, apiKey)
                    Log.i(TAG, "  Success with type=$altType, updating media type")
                    val typeUpdate = media.copy(mediaType = altType)
                    DatabaseManager.instance.getScrapeMediaDao().update(typeUpdate)
                    type = altType
                    val updatedEntity = media.copy(
                        overview = altDetail.overview ?: media.overview,
                        voteAverage = altDetail.vote_average,
                        poster = altDetail.poster_path?.let { TmdbRepository.TMDB_IMG_DOMAIN + "/t/p/w500$it" } ?: media.poster,
                        backdrop = altDetail.backdrop_path?.let { TmdbRepository.TMDB_IMG_DOMAIN + "/t/p/w500$it" } ?: media.backdrop,
                        releaseTime = (altDetail.release_date ?: altDetail.first_air_date) ?: media.releaseTime,
                        mediaType = altType,
                        updateTime = System.currentTimeMillis()
                    )
                    DatabaseManager.instance.getScrapeMediaDao().update(updatedEntity)
                    Log.d(TAG, "  Updated media with alt type: poster=${updatedEntity.poster?.take(60)}")

                    if (altType == "tv" && altDetail.number_of_seasons > 0) {
                        Log.d(TAG, "  Enqueueing ${altDetail.number_of_seasons} seasons for sync")
                        (1..altDetail.number_of_seasons).forEach { seasonNum ->
                            enqueue(task.tmdbId, TaskType.SEASON_EPISODES, seasonNum, priority = 1)
                        }
                    }
                    return
                } catch (_: Exception) {
                    throw firstError
                }
            }
            throw firstError
        }

        Log.d(TAG, "  Detail: poster=${detail.poster_path}, overview=${detail.overview?.take(50)}")

        val updateEntity = media.copy(
            overview = detail.overview ?: media.overview,
            voteAverage = detail.vote_average,
            poster = detail.poster_path?.let { TmdbRepository.TMDB_IMG_DOMAIN + "/t/p/w500$it" } ?: media.poster,
            backdrop = detail.backdrop_path?.let { TmdbRepository.TMDB_IMG_DOMAIN + "/t/p/w500$it" } ?: media.backdrop,
            releaseTime = (detail.release_date ?: detail.first_air_date) ?: media.releaseTime,
            updateTime = System.currentTimeMillis()
        )
        DatabaseManager.instance.getScrapeMediaDao().update(updateEntity)
        Log.d(TAG, "  Updated media: poster=${updateEntity.poster?.take(60)}")

        if (type == "tv" && detail.number_of_seasons > 0) {
            Log.d(TAG, "  Enqueueing ${detail.number_of_seasons} seasons for sync")
            (1..detail.number_of_seasons).forEach { seasonNum ->
                enqueue(task.tmdbId, TaskType.SEASON_EPISODES, seasonNum, priority = 1)
            }
        }
    }

    private suspend fun processSeasonSync(task: TmdbSyncQueueEntity) {
        val seasonNumber = task.seasonNumber ?: throw Exception("Season number is null")
        Log.d(TAG, "processSeasonSync: tmdbId=${task.tmdbId}, season=$seasonNumber")
        val apiKey = getTmdbApiKey() ?: throw Exception("TMDB API key not configured")

        val seasonDetail = TmdbRepository().getSeasonDetail(task.tmdbId, seasonNumber, apiKey)

        val media = DatabaseManager.instance.getScrapeMediaDao().getByTmdbId(task.tmdbId)
            ?: throw Exception("Media not found for tmdbId=${task.tmdbId}")

        val existingEpisodes = DatabaseManager.instance.getEpisodeDao().getEpisodesByMediaId(media.id)
        Log.d(TAG, "  Existing episodes: ${existingEpisodes.size}")
        val updatedEpisodes = existingEpisodes.filter { episode -> episode.seasonNumber == seasonNumber }.map { ep ->
            val found = seasonDetail.episodes.find { tmdbEp -> tmdbEp.episode_number == ep.episodeNumber }
            if (found != null) {
                ep.copy(
                    title = found.name.ifEmpty { ep.title },
                    overview = found.overview ?: ep.overview,
                    stillUrl = found.still_path?.let { path -> TmdbRepository.TMDB_IMG_DOMAIN + "/t/p/w300$path" } ?: ep.stillUrl
                )
            } else {
                ep
            }
        }
        Log.d(TAG, "  Updated ${updatedEpisodes.size} episodes for season $seasonNumber")

        if (updatedEpisodes.isNotEmpty()) {
            DatabaseManager.instance.getEpisodeDao().insertEpisodes(updatedEpisodes)
        }
    }

    private suspend fun handleFailure(task: TmdbSyncQueueEntity, error: String?) {
        val newAttemptCount = task.attemptCount + 1
        if (newAttemptCount >= MAX_RETRIES) {
            DatabaseManager.instance.getTmdbSyncQueueDao().markFailed(
                task.key, State.FAILED.name, error
            )
        } else {
            delay(RETRY_DELAY_MS)
            DatabaseManager.instance.getTmdbSyncQueueDao().upsert(
                task.copy(
                    attemptCount = newAttemptCount,
                    lastError = error,
                    state = State.PENDING,
                    updateTime = System.currentTimeMillis()
                )
            )
        }
    }

    private fun getTmdbApiKey(): String? {
        val key = com.xyoye.common_component.config.TmdbApiConfig.apiKey
        return if (key.isEmpty()) null else key
    }
}