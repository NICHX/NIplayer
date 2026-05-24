package com.xyoye.common_component.scrape

import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.utils.JsonHelper
import com.xyoye.data_component.entity.EpisodeEntity
import com.xyoye.data_component.entity.ScrapeMediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.util.Log

data class ScrapeProgress(
    val found: Int = 0,
    val matched: Int = 0,
    val total: Int = 0
)

data class ScrapeFileItem(
    val name: String,
    val path: String,
    val season: String = "1",
    val seasonPath: String = "",
    val provider: String = "",
    val size: Long = 0,
    val uniqueKey: String = ""
)

data class ScrapeSourceItem(
    val provider: String,
    val size: String,
    val path: String,
    val name: String,
    val seasonArr: List<SeasonItem>
)

data class SeasonItem(
    val name: String,
    val path: String,
    val season: String,
    val folderFileId: Int? = null
)

data class EpisodeFileInfo(
    val fileName: String,
    val filePath: String,
    val seasonNumber: Int,
    val episodeNumber: Int = 0,
    val fileSize: Long = 0
)

class ScrapeEngine(
    private val tmdbRepository: TmdbRepository = TmdbRepository()
) {

    companion object {
        private const val TAG = "ScrapeEngine"

        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "m2ts", "avi", "mov", "ts", "m3u8", "iso",
            "flv", "wmv", "rmvb", "3gp", "webm"
        )

        private val tmdbSemaphore = Semaphore(5)
    }

    suspend fun scanMovies(
        storage: Storage,
        rootPath: String,
        progress: (ScrapeProgress) -> Unit
    ): List<ScrapeFileItem> = coroutineScope {
        val movieArr = mutableListOf<ScrapeFileItem>()
        var foundCount = 0

        val rootFile = storage.pathFile(rootPath, true) ?: return@coroutineScope emptyList()
        val files = storage.openDirectory(rootFile, false)

        files.forEach { file ->
            if (file.isDirectory()) {
                foundCount = recursionMovie(storage, file, movieArr, foundCount, progress)
            } else if (isVideoFile(file.fileName())) {
                foundCount++
                val movieName = stripExtension(file.fileName())
                movieArr.add(
                    ScrapeFileItem(
                        name = movieName,
                        path = file.storagePath(),
                        size = file.fileLength(),
                        uniqueKey = file.uniqueKey()
                    )
                )
                progress(ScrapeProgress(found = foundCount))
            }
        }

        deduplicateMovieItems(movieArr)
    }

    private suspend fun recursionMovie(
        storage: Storage,
        dir: StorageFile,
        movieArr: MutableList<ScrapeFileItem>,
        foundCount: Int,
        progress: (ScrapeProgress) -> Unit
    ): Int {
        var count = foundCount
        val files = storage.openDirectory(dir, false)
        val videoFiles = files.filter { !it.isDirectory() && isVideoFile(it.fileName()) }

        if (videoFiles.isNotEmpty()) {
            count++
            movieArr.add(
                ScrapeFileItem(
                    name = stripExtension(dir.fileName()),
                    path = dir.storagePath(),
                    size = videoFiles.first().fileLength()
                )
            )
            progress(ScrapeProgress(found = count))
        }

        files.filter { it.isDirectory() }.forEach { subDir ->
            count = recursionMovie(storage, subDir, movieArr, count, progress)
        }

        return count
    }

    private fun deduplicateMovieItems(items: MutableList<ScrapeFileItem>): List<ScrapeFileItem> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<ScrapeFileItem>()
        for (item in items) {
            val key = "${item.path}::${item.name}"
            if (key !in seen) {
                seen.add(key)
                result.add(item)
            }
        }
        return result
    }

    suspend fun scanTv(
        storage: Storage,
        rootPath: String,
        progress: (ScrapeProgress) -> Unit
    ): List<ScrapeFileItem> = coroutineScope {
        val tvArr = mutableListOf<ScrapeFileItem>()
        var foundCount = 0

        val rootFile = storage.pathFile(rootPath, true) ?: return@coroutineScope emptyList()
        val files = storage.openDirectory(rootFile, false)

        files.forEach { file ->
            if (file.isDirectory()) {
                foundCount = recursionTv(storage, file, null, tvArr, foundCount, progress)
            } else if (isVideoFile(file.fileName())) {
                foundCount++
                val tvName = stripExtension(file.fileName())
                val season = SeasonExtractor.extractSeasonNumber(tvName)
                tvArr.add(
                    ScrapeFileItem(
                        name = tvName,
                        path = rootPath,
                        season = season,
                        seasonPath = rootPath,
                        size = file.fileLength()
                    )
                )
                progress(ScrapeProgress(found = foundCount))
            }
        }

        tvArr
    }

    private suspend fun recursionTv(
        storage: Storage,
        dir: StorageFile,
        parent: StorageFile?,
        tvArr: MutableList<ScrapeFileItem>,
        foundCount: Int,
        progress: (ScrapeProgress) -> Unit
    ): Int {
        var count = foundCount
        val children = storage.openDirectory(dir, false)

        var seasonItemAdded = false
        for (child in children) {
            if (child.isDirectory()) {
                count = recursionTv(storage, child, dir, tvArr, count, progress)
            } else if (!seasonItemAdded && isVideoFile(child.fileName())) {
                seasonItemAdded = true
                val effectiveParent = parent ?: dir
                val season = SeasonExtractor.extractSeasonNumber(dir.fileName())

                val item = ScrapeFileItem(
                    name = dir.fileName(),
                    path = dir.storagePath(),
                    season = season,
                    seasonPath = effectiveParent.storagePath() + "/" + dir.fileName(),
                    size = child.fileLength()
                )

                if (SeasonExtractor.startsWithSeasonFormat(dir.fileName())) {
                    tvArr.add(item)
                } else {
                    val parentName = FileNameParser.handleSeasonName(effectiveParent.fileName())
                    if (parentName != null && dir.fileName().contains(parentName)) {
                        tvArr.add(item)
                    } else {
                        tvArr.add(item.copy(season = "1", seasonPath = dir.filePath()))
                    }
                }
                count++
                progress(ScrapeProgress(found = count))
            }
        }
        return count
    }

    fun convertMovieItems(items: List<ScrapeFileItem>): List<ScrapeMediaEntity> {
        return items.map { item ->
            ScrapeMediaEntity(
                name = item.name,
                path = item.path,
                mediaType = "movie",
                playKey = item.uniqueKey.ifEmpty { null }
            )
        }
    }

    fun groupBySource(items: List<ScrapeFileItem>): List<ScrapeMediaEntity> {
        val chineseNumber = (1..99).associate {
            it.toString() to ChineseNumberMapper.numberToChinese(it.toString())
        }

        val sorted = items.sortedBy { it.name.lowercase().first() }
        val map = linkedMapOf<String, ScrapeMediaEntity>()

        sorted.forEach { item ->
            val isSeasonDirName = SeasonExtractor.startsWithSeasonFormat(item.name)

            val pathBasedInfo = run {
                val pathTrimmed = item.path.trimEnd('/')
                val lastSlash = pathTrimmed.lastIndexOf('/')
                if (lastSlash > 0) {
                    val dirName = pathTrimmed.substringAfterLast('/')
                    if (SeasonExtractor.startsWithSeasonFormat(dirName)) {
                        val parentPath = pathTrimmed.substring(0, lastSlash)
                        Pair(parentPath.substringAfterLast('/'), parentPath)
                    } else null
                } else null
            }

            val nameBasedInfo = FileNameParser.handleSeasonName(item.name)?.let {
                Pair(it, item.path)
            }

            val showInfo = if (isSeasonDirName && pathBasedInfo != null) {
                pathBasedInfo
            } else {
                nameBasedInfo ?: pathBasedInfo ?: return@forEach
            }

            val key = showInfo.first
            val showPath = showInfo.second

            if (!map.containsKey(key)) {
                map[key] = ScrapeMediaEntity(
                    name = key,
                    path = showPath,
                    mediaType = "tv"
                )
            }

            val existing = map[key]!!
            val existingSources = parseSourceJson(existing.sourceJson)
            val currentSource = existingSources.find { it.name == item.name && it.path == item.path }

            val seasonItem = SeasonItem(
                name = "第${chineseNumber[item.season] ?: item.season}季",
                path = item.seasonPath,
                season = item.season,
                folderFileId = null
            )

            val updatedSources = if (currentSource != null) {
                val updatedSeasonArr = (currentSource.seasonArr + seasonItem)
                    .sortedBy { it.season.toIntOrNull() ?: 0 }
                existingSources.map {
                    if (it == currentSource) it.copy(seasonArr = updatedSeasonArr) else it
                }
            } else {
                existingSources + ScrapeSourceItem(
                    provider = item.provider,
                    size = formatSize(item.size),
                    path = item.path,
                    name = item.name,
                    seasonArr = listOf(seasonItem)
                )
            }

            map[key] = existing.copy(sourceJson = JsonHelper.toJson(updatedSources) ?: "[]")
        }

        return map.values.toList()
    }

    suspend fun matchTmdbMetadata(
        items: List<ScrapeMediaEntity>,
        type: String,
        tmdbKey: String,
        existingData: List<ScrapeMediaEntity> = emptyList(),
        storage: Storage? = null
    ): List<ScrapeMediaEntity> = coroutineScope {
        Log.d(TAG, "matchTmdbMetadata: type=$type, items=${items.size}, apiKey=${tmdbKey.take(8)}...")
        items.map { item ->
            async(Dispatchers.IO) {
                try {
                    if (storage != null) {
                        val nfoContent = ScrapeFileManager.readNfo(storage, item.path, item.name, type)
                        if (nfoContent != null) {
                            val nfoData = NfoReader.parseNfo(nfoContent)
                            if (nfoData != null) {
                                Log.d(TAG, "Found NFO: ${item.name}, tmdbId=${nfoData.tmdbId}")
                                return@async item.copy(
                                    poster = nfoData.thumb,
                                    backdrop = nfoData.fanart,
                                    tmdbId = nfoData.tmdbId,
                                    voteAverage = nfoData.rating,
                                    releaseTime = nfoData.premiered,
                                    overview = nfoData.plot,
                                    mediaType = type
                                )
                            }
                        }
                    }

                    val existing = existingData.find { it.path == item.path }
                    if (existing != null && existing.tmdbId != null) {
                        Log.d(TAG, "Already exists: ${item.name}, tmdbId=${existing.tmdbId}")
                        return@async item.copy(
                            poster = existing.poster,
                            backdrop = existing.backdrop,
                            tmdbId = existing.tmdbId,
                            genreIds = existing.genreIds,
                            voteAverage = existing.voteAverage,
                            releaseTime = existing.releaseTime,
                            overview = existing.overview
                        )
                    }

                    val query = FileNameParser.handleSeasonName(item.name) ?: item.name
                    val year = FileNameParser.handleNameYear(item.name)
                    Log.d(TAG, "Searching TMDB: query=$query, year=$year, type=$type")
                    val (detectedType, searchResult) = tmdbSemaphore.withPermit {
                        tmdbRepository.searchWithFallback(query, year, tmdbKey)
                    }
                    val effectiveType = MediaTypeDetector.detectFromPath(item.path)
                        ?: detectedType

                    val matched = tmdbRepository.findBestMatch(
                        searchResult.results, query, effectiveType
                    )

                    if (matched != null) {
                        Log.d(TAG, "TMDB matched: ${item.name} -> id=${matched.id}, poster=${matched.poster_path}")

                        var posterUrl = matched.poster_path?.let {
                            TmdbRepository.TMDB_IMG_DOMAIN + "/t/p/w500$it"
                        }
                        var releaseTime: String? = null

                        if (effectiveType == "tv") {
                            val seasonNumber = item.season.toIntOrNull() ?: 1
                            if (seasonNumber > 1) {
                                try {
                                    val seasonDetail = tmdbSemaphore.withPermit {
                                        tmdbRepository.getSeasonDetail(
                                            matched.id, seasonNumber, tmdbKey
                                        )
                                    }
                                    posterUrl = seasonDetail.poster_path?.let {
                                        TmdbRepository.TMDB_IMG_DOMAIN + "/t/p/w500$it"
                                    } ?: posterUrl
                                    releaseTime = seasonDetail.air_date
                                    Log.d(TAG, "Season $seasonNumber poster: ${seasonDetail.poster_path}")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to get season detail for $seasonNumber", e)
                                }
                            }
                        }

                        val result = item.copy(
                            poster = posterUrl ?: "",
                            backdrop = matched.backdrop_path?.let {
                                TmdbRepository.TMDB_IMG_DOMAIN + "/t/p/w500$it"
                            },
                            tmdbId = matched.id,
                            genreIds = JsonHelper.toJson(matched.genre_ids) ?: "[]",
                            voteAverage = matched.vote_average,
                            releaseTime = releaseTime ?: matched.release_date ?: matched.first_air_date,
                            overview = matched.overview,
                            mediaType = type
                        )

                        if (storage != null) {
                            runCatching {
                                val detail = tmdbSemaphore.withPermit {
                                    tmdbRepository.getMediaDetail(matched.id, effectiveType, tmdbKey)
                                }
                                val nfoContent = if (effectiveType == "movie") {
                                    NfoWriter.generateMovieNfo(result, detail)
                                } else {
                                    NfoWriter.generateTvShowNfo(result, detail)
                                }
                                ScrapeFileManager.saveNfo(storage, item.path, item.name, nfoContent, effectiveType)
                                ScrapeFileManager.savePoster(storage, item.path, item.name, matched.poster_path, effectiveType)
                                ScrapeFileManager.saveBackdrop(storage, item.path, item.name, matched.backdrop_path, effectiveType)
                            }
                        }

                        result
                    } else {
                        Log.w(TAG, "No TMDB match for: $item.name (query=$query)")
                        item.copy(mediaType = type)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "matchTmdbMetadata failed for: ${item.name}", e)
                    item.copy(mediaType = type)
                }
            }
        }.awaitAll()
    }

    private fun isVideoFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return VIDEO_EXTENSIONS.contains(ext)
    }

    private fun stripExtension(name: String): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(0, dotIndex) else name
    }

    private fun normalizePath(base: String, name: String): String {
        return "${base.trimEnd('/')}/$name"
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
            "%.1f %s",
            size / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    private fun parseSourceJson(json: String): List<ScrapeSourceItem> {
        return try {
            JsonHelper.parseJsonList(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun extractSeasonNumberFromFileName(fileName: String): Int {
        val name = fileName.substringBeforeLast('.')
        val patterns = listOf(
            Regex("""[Ss](\d{1,2})[Ee]\d{1,3}"""),
            Regex("""(\d{1,2})x(\d{1,3})""")
        )
        for (pattern in patterns) {
            val match = pattern.find(name)
            if (match != null) {
                val season = match.groupValues[1].toIntOrNull()
                if (season != null && season in 1..99) return season
            }
        }
        val seasonPattern = Regex("""[Ss](\d{1,2})(?:\b|[^Ee])""")
        val seasonMatch = seasonPattern.find(name)
        if (seasonMatch != null) {
            val season = seasonMatch.groupValues[1].toIntOrNull()
            if (season != null && season in 1..99) return season
        }
        return 1
    }

    fun extractEpisodeNumber(fileName: String): Int {
        val name = fileName.substringBeforeLast('.')
        val patterns = listOf(
            Regex("""[Ss](\d{1,2})[Ee](\d{1,3})"""),
            Regex("""[Ee](\d{2,3})"""),
            Regex("""第(\d{1,3})[集话話]"""),
            Regex("""[Ee]pisode\s*(\d{1,3})"""),
            Regex("""(\d{1,3})\s*[Vv][Oo][Ll]""")
        )
        for (pattern in patterns) {
            val match = pattern.find(name)
            if (match != null) {
                val groups = match.groupValues
                val num = if (groups.size >= 3) groups[2] else groups[1]
                return num.toIntOrNull() ?: continue
            }
        }
        val digits = Regex("""(\d{2,3})$""").find(name)
        return digits?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    suspend fun scanTvEpisodeFiles(
        storage: Storage,
        seriesPath: String
    ): List<EpisodeFileInfo> = coroutineScope {
        val episodes = mutableListOf<EpisodeFileInfo>()

        val seriesDir = storage.pathFile(seriesPath, true) ?: return@coroutineScope emptyList()
        val children = storage.openDirectory(seriesDir, false)

        val videoFiles = children.filter { !it.isDirectory() && isVideoFile(it.fileName()) }
        videoFiles.forEach { file ->
            val fileName = file.fileName()
            val epNumber = extractEpisodeNumber(fileName)
            val seasonNumber = extractSeasonNumberFromFileName(fileName)
            episodes.add(
                EpisodeFileInfo(
                    fileName = fileName,
                    filePath = file.storagePath(),
                    seasonNumber = seasonNumber,
                    episodeNumber = epNumber,
                    fileSize = file.fileLength()
                )
            )
        }

        children.filter { it.isDirectory() }.forEach { seasonDir ->
            val seasonNumber = SeasonExtractor.extractSeasonNumber(seasonDir.fileName()).toIntOrNull() ?: 1
            val seasonFiles = storage.openDirectory(seasonDir, false)
            seasonFiles.filter { isVideoFile(it.fileName()) }.forEach { file ->
                val fileName = file.fileName()
                val epNumber = extractEpisodeNumber(fileName)
                episodes.add(
                    EpisodeFileInfo(
                        fileName = fileName,
                        filePath = file.storagePath(),
                        seasonNumber = seasonNumber,
                        episodeNumber = epNumber,
                        fileSize = file.fileLength()
                    )
                )
            }
        }

        episodes
    }
}
