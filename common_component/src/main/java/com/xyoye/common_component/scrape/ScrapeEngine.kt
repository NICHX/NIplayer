package com.xyoye.common_component.scrape

import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.network.repository.TmdbRepository
import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.utils.JsonHelper
import com.xyoye.data_component.entity.ScrapeMediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
    val size: Long = 0
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

class ScrapeEngine(
    private val tmdbRepository: TmdbRepository = TmdbRepository()
) {

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "m2ts", "avi", "mov", "ts", "m3u8", "iso",
            "flv", "wmv", "rmvb", "3gp", "webm"
        )
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
                        path = normalizePath(rootPath, movieName),
                        size = file.fileLength()
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
                    path = dir.filePath(),
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

        files.filter { it.isDirectory() }.forEach { dir ->
            foundCount = recursionTv(storage, dir, null, tvArr, foundCount, progress)
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

        var foundVideo = false
        for (child in children) {
            if (foundVideo) break
            if (child.isDirectory()) {
                count = recursionTv(storage, child, dir, tvArr, count, progress)
            } else if (isVideoFile(child.fileName())) {
                foundVideo = true
                val effectiveParent = parent ?: dir
                val season = SeasonExtractor.extractSeasonNumber(dir.fileName())

                val item = ScrapeFileItem(
                    name = dir.fileName(),
                    path = dir.filePath(),
                    season = season,
                    seasonPath = effectiveParent.filePath() + "/" + dir.fileName(),
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
                mediaType = "movie"
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
            val key = FileNameParser.handleSeasonName(item.name, reserve = true) ?: return@forEach

            if (!map.containsKey(key)) {
                map[key] = ScrapeMediaEntity(
                    name = key,
                    path = item.path,
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
        items.map { item ->
            async(Dispatchers.IO) {
                try {
                    val existing = existingData.find { it.path == item.path }
                    if (existing != null && existing.tmdbId != null) {
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

                    if (storage != null) {
                        val nfoContent = ScrapeFileManager.readNfo(storage, item.path, item.name, type)
                        if (nfoContent != null) {
                            val nfoData = NfoReader.parseNfo(nfoContent)
                            if (nfoData != null && nfoData.tmdbId != null) {
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

                    val query = FileNameParser.handleSeasonName(item.name) ?: item.name
                    val year = FileNameParser.handleNameYear(item.name)
                    val searchResult = tmdbRepository.search(query, year, type, tmdbKey)

                    val matched = when {
                        searchResult.results.size == 1 -> searchResult.results[0]
                        searchResult.results.size > 1 -> searchResult.results.find {
                            it.name?.contains(query) == true || it.title?.contains(query) == true
                        } ?: searchResult.results[0]
                        else -> null
                    }

                    if (matched != null) {
                        var poster = matched.poster_path
                        var releaseTime: String? = null

                        if (type == "tv" && item.season != "1") {
                            val seasonDetail = tmdbRepository.getSeasonDetail(
                                matched.id, item.season.toInt(), tmdbKey
                            )
                            poster = seasonDetail.poster_path ?: poster
                            releaseTime = seasonDetail.air_date
                        }

                        val result = item.copy(
                            poster = poster,
                            backdrop = matched.backdrop_path,
                            tmdbId = matched.id,
                            genreIds = JsonHelper.toJson(matched.genre_ids) ?: "[]",
                            voteAverage = matched.vote_average,
                            releaseTime = releaseTime ?: matched.release_date ?: matched.first_air_date,
                            overview = matched.overview,
                            mediaType = type
                        )

                        if (storage != null) {
                            try {
                                val detail = tmdbRepository.getMediaDetail(matched.id, type, tmdbKey)
                                val nfoContent = if (type == "movie") {
                                    NfoWriter.generateMovieNfo(result, detail)
                                } else {
                                    NfoWriter.generateTvShowNfo(result, detail)
                                }
                                ScrapeFileManager.saveNfo(storage, item.path, item.name, nfoContent, type)
                                ScrapeFileManager.savePoster(storage, item.path, item.name, poster, type)
                                ScrapeFileManager.saveBackdrop(storage, item.path, item.name, matched.backdrop_path, type)
                            } catch (_: Exception) {
                            }
                        }

                        result
                    } else {
                        item.copy(mediaType = type)
                    }
                } catch (e: Exception) {
                    item.copy(mediaType = type)
                }
            }
        }.awaitAll().filterNotNull()
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
}
