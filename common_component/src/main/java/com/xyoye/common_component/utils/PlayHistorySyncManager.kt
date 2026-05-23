package com.xyoye.common_component.utils

import com.xyoye.common_component.config.PlayHistorySyncConfig
import com.xyoye.common_component.config.WebDavBackupConfig
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.extension.toMd5String
import com.xyoye.common_component.network.helper.UnsafeOkHttpClient
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

object PlayHistorySyncManager {

    private const val SYNC_FILE_NAME = "NIplayer_play_history.json"
    private const val SYNC_MIN_INTERVAL_MS = 5 * 60 * 1000L
    private val SYNC_MEDIA_TYPES = listOf(
        MediaType.SMB_SERVER,
        MediaType.FTP_SERVER,
        MediaType.WEBDAV_SERVER,
        MediaType.ALSIT_STORAGE
    )

    private val isoFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val isSyncing = AtomicBoolean(false)

    private fun isoFormat(): SimpleDateFormat = isoFormat.get()

    sealed class SyncProgress {
        object DownloadingCloud : SyncProgress()
        data class Uploading(val recordCount: Int) : SyncProgress()
        data class ApplyingRemote(val recordCount: Int) : SyncProgress()
    }

    sealed class SyncResult {
        data class Success(val uploaded: Int, val downloaded: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object Skipped : SyncResult()
    }

    private suspend fun buildStorageUrlMap(): Map<Int, String> {
        val libraryDao = DatabaseManager.instance.getMediaLibraryDao()
        val urlMap = mutableMapOf<Int, String>()
        for (type in SYNC_MEDIA_TYPES) {
            val servers = libraryDao.getByMediaTypeSuspend(type)
            for (server in servers) {
                urlMap[server.id] = server.url.trimEnd('/')
            }
        }
        return urlMap
    }

    suspend fun sync(
        force: Boolean = false,
        onProgress: ((SyncProgress) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!isSyncing.compareAndSet(false, true)) {
            return@withContext SyncResult.Error("正在同步中，请稍后再试")
        }
        try {
            withTimeout(10000L) {
                doSync(force, onProgress)
            }
        } catch (e: TimeoutCancellationException) {
            SyncResult.Error("服务器不在线")
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResult.Error("同步失败: ${e.message}")
        } finally {
            isSyncing.set(false)
        }
    }

    private suspend fun doSync(
        force: Boolean,
        onProgress: ((SyncProgress) -> Unit)?
    ): SyncResult {
        if (!PlayHistorySyncConfig.enabled) {
            return SyncResult.Error("播放记录同步未开启")
        }

        val now = System.currentTimeMillis()
        if (!force) {
            val lastAttempt = PlayHistorySyncConfig.lastSyncAttemptTime
            if (now - lastAttempt < SYNC_MIN_INTERVAL_MS) {
                return SyncResult.Skipped
            }
        }
        PlayHistorySyncConfig.lastSyncAttemptTime = now

        val (serverUrl, account, password) = resolveWebDavServer()
        if (serverUrl.isNullOrEmpty()) {
            return SyncResult.Error("WebDAV服务器未配置")
        }

        val credential = if (!account.isNullOrEmpty()) {
            Credentials.basic(account, password ?: "")
        } else null

        val config = PlayHistorySyncConfig
        config.ensureDeviceId()
        val lastSyncTime = config.lastSyncTime

        val localDirty = DatabaseManager.instance.getPlayHistoryDao()
            .getModifiedSince(SYNC_MEDIA_TYPES, lastSyncTime)

        val urlMap = buildStorageUrlMap()

        onProgress?.invoke(SyncProgress.DownloadingCloud)
        val cloudData = downloadWithCache(serverUrl, credential)

        if (localDirty.isEmpty() && cloudData != null) {
            val remoteDirty = filterNewRecords(cloudData, lastSyncTime)
            val appliedCount = mergeCloudToLocal(remoteDirty)
            config.lastSyncTime = now
            return SyncResult.Success(0, appliedCount)
        }

        val merged = if (cloudData != null) {
            mergeLocalToCloud(cloudData, localDirty, urlMap)
        } else {
            buildSyncData(localDirty, urlMap)
        }

        val uploadCount = merged.optJSONArray("records")?.length() ?: 0
        onProgress?.invoke(SyncProgress.Uploading(uploadCount))

        val uploadSuccess = uploadToWebDav(serverUrl, credential, merged)
        if (!uploadSuccess) {
            return SyncResult.Error("上传同步数据失败")
        }

        val remoteDirty = if (cloudData != null) {
            filterNewRecords(cloudData, lastSyncTime)
        } else {
            emptyList()
        }

        onProgress?.invoke(SyncProgress.ApplyingRemote(remoteDirty.size))
        val appliedCount = mergeCloudToLocal(remoteDirty)

        config.lastSyncTime = now

        return SyncResult.Success(localDirty.size, appliedCount)
    }

    private suspend fun downloadWithCache(
        serverUrl: String,
        credential: String?
    ): JSONObject? {
        val freshData = downloadFromWebDav(serverUrl, credential)
        if (freshData != null) {
            PlayHistorySyncConfig.cachedCloudData = freshData.toString()
            return freshData
        }
        val cached = PlayHistorySyncConfig.cachedCloudData
        if (cached.isNotEmpty()) {
            return try {
                JSONObject(cached)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun buildSyncData(records: List<PlayHistoryEntity>, urlMap: Map<Int, String>): JSONObject {
        val root = JSONObject()
        root.put("version", 2)
        root.put("device_id", PlayHistorySyncConfig.deviceId)
        root.put("last_sync_time", isoFormat().format(Date()))
        val arr = JSONArray()
        for (record in records) {
            arr.put(recordToJson(record, urlMap))
        }
        root.put("records", arr)
        return root
    }

    private fun mergeLocalToCloud(
        cloudData: JSONObject,
        localDirty: List<PlayHistoryEntity>,
        urlMap: Map<Int, String>
    ): JSONObject {
        val cloudRecords = parseRecords(cloudData.optJSONArray("records"))
        val recordMap = mutableMapOf<String, JSONObject>()
        for (record in cloudRecords) {
            val key = record.optString("sync_key")
            if (key.isNotEmpty()) {
                recordMap[key] = record
            }
        }

        for (local in localDirty) {
            val syncKey = buildSyncKey(local, urlMap)
            val json = recordToJson(local, urlMap)
            val existing = recordMap[syncKey]
            if (existing == null) {
                recordMap[syncKey] = json
            } else {
                val cloudTime = parseIsoDate(existing.optString("play_time"))
                val localTime = local.playTime.time
                if (localTime > cloudTime) {
                    recordMap[syncKey] = json
                }
            }
        }

        val root = JSONObject()
        root.put("version", 2)
        root.put("device_id", PlayHistorySyncConfig.deviceId)
        root.put("last_sync_time", isoFormat().format(Date()))
        val arr = JSONArray()
        for (value in recordMap.values) {
            arr.put(value)
        }
        root.put("records", arr)
        return root
    }

    private fun filterNewRecords(cloudData: JSONObject, sinceTimestamp: Long): List<JSONObject> {
        val records = parseRecords(cloudData.optJSONArray("records"))
        val myDeviceId = PlayHistorySyncConfig.deviceId
        val result = mutableListOf<JSONObject>()
        for (record in records) {
            val deviceId = record.optString("device_id")
            if (deviceId == myDeviceId) continue
            val playTime = parseIsoDate(record.optString("play_time"))
            if (playTime > sinceTimestamp) {
                result.add(record)
            }
        }
        return result
    }

    private suspend fun mergeCloudToLocal(remoteRecords: List<JSONObject>): Int {
        if (remoteRecords.isEmpty()) return 0

        var appliedCount = 0
        val dao = DatabaseManager.instance.getPlayHistoryDao()
        val libraryDao = DatabaseManager.instance.getMediaLibraryDao()

        val urlToId = mutableMapOf<String, Int>()
        val idToUrl = mutableMapOf<Int, String>()
        for (type in SYNC_MEDIA_TYPES) {
            val servers = libraryDao.getByMediaTypeSuspend(type)
            for (server in servers) {
                urlToId[server.url.trimEnd('/')] = server.id
                idToUrl[server.id] = server.url.trimEnd('/')
            }
        }

        for (remoteJson in remoteRecords) {
            val mediaTypeStr = remoteJson.optString("media_type")
            val mediaType = MediaType.fromValue(mediaTypeStr)
            if (mediaType !in SYNC_MEDIA_TYPES) continue

            val storagePath = remoteJson.optString("storage_path")
            if (storagePath.isEmpty()) continue

            val matchedStorageId = resolveStorageId(remoteJson, urlToId)
            if (matchedStorageId == null) continue

            val uniqueKey = buildUniqueKey(matchedStorageId, storagePath)
            val existing = dao.getPlayHistory(uniqueKey, matchedStorageId)

            val remotePlayTime = parseIsoDate(remoteJson.optString("play_time"))
            if (existing != null && existing.playTime.time >= remotePlayTime) {
                continue
            }

            if (existing != null) {
                dao.update(existing.copy(
                    videoName = remoteJson.optString("video_name"),
                    url = remoteJson.optString("url"),
                    mediaType = mediaType,
                    videoPosition = remoteJson.optLong("video_position"),
                    videoDuration = remoteJson.optLong("video_duration"),
                    playTime = Date(remotePlayTime),
                    subtitlePath = remoteJson.optString("subtitle_path").ifEmpty { null },
                    audioPath = remoteJson.optString("audio_path").ifEmpty { null },
                    torrentPath = remoteJson.optString("torrent_path").ifEmpty { null },
                    torrentIndex = remoteJson.optInt("torrent_index", -1),
                    httpHeader = remoteJson.optString("http_header").ifEmpty { null }
                ))
                appliedCount++
            } else {
                val entity = PlayHistoryEntity(
                    id = 0,
                    videoName = remoteJson.optString("video_name"),
                    url = remoteJson.optString("url"),
                    mediaType = mediaType,
                    videoPosition = remoteJson.optLong("video_position"),
                    videoDuration = remoteJson.optLong("video_duration"),
                    playTime = Date(remotePlayTime),
                    subtitlePath = remoteJson.optString("subtitle_path").ifEmpty { null },
                    audioPath = remoteJson.optString("audio_path").ifEmpty { null },
                    torrentPath = remoteJson.optString("torrent_path").ifEmpty { null },
                    torrentIndex = remoteJson.optInt("torrent_index", -1),
                    httpHeader = remoteJson.optString("http_header").ifEmpty { null },
                    uniqueKey = uniqueKey,
                    storagePath = storagePath,
                    storageId = matchedStorageId
                )
                dao.insert(entity)
                appliedCount++
            }
        }

        return appliedCount
    }

    private fun resolveStorageId(
        remoteJson: JSONObject,
        urlToId: Map<String, Int>
    ): Int? {
        val cloudStorageId = if (remoteJson.has("storage_id") && !remoteJson.isNull("storage_id")) {
            remoteJson.optInt("storage_id", -1)
        } else {
            -1
        }
        if (cloudStorageId > 0 && cloudStorageId in urlToId.values) {
            return cloudStorageId
        }
        val serverUrl = remoteJson.optString("server_url")
        if (serverUrl.isNotEmpty()) {
            return urlToId[serverUrl]
        }
        return null
    }

    private fun buildSyncKey(entity: PlayHistoryEntity, urlMap: Map<Int, String>): String {
        val serverUrl = entity.storageId?.let { urlMap[it] } ?: ""
        val storagePath = entity.storagePath ?: ""
        return "$serverUrl:$storagePath"
    }

    private fun buildUniqueKey(storageId: Int, storagePath: String): String {
        return "$storageId-$storagePath".toMd5String()
    }

    private fun recordToJson(entity: PlayHistoryEntity, urlMap: Map<Int, String>): JSONObject {
        val json = JSONObject()
        val serverUrl = entity.storageId?.let { urlMap[it] } ?: ""
        json.put("sync_key", "$serverUrl:${entity.storagePath ?: ""}")
        json.put("server_url", serverUrl)
        json.put("storage_id", entity.storageId ?: -1)
        json.put("storage_path", entity.storagePath ?: "")
        json.put("media_type", entity.mediaType.value)
        json.put("video_name", entity.videoName)
        json.put("url", entity.url)
        json.put("video_position", entity.videoPosition)
        json.put("video_duration", entity.videoDuration)
        json.put("play_time", isoFormat().format(entity.playTime))
        json.put("subtitle_path", entity.subtitlePath ?: "")
        json.put("audio_path", entity.audioPath ?: "")
        json.put("torrent_path", entity.torrentPath ?: "")
        json.put("torrent_index", entity.torrentIndex)
        json.put("http_header", entity.httpHeader ?: "")
        json.put("device_id", PlayHistorySyncConfig.deviceId)
        return json
    }

    private fun parseRecords(array: JSONArray?): List<JSONObject> {
        if (array == null) return emptyList()
        val list = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            list.add(array.getJSONObject(i))
        }
        return list
    }

    private fun parseIsoDate(isoStr: String): Long {
        return try {
            isoFormat().parse(isoStr)?.time ?: 0L
        } catch (e: Exception) {
            android.util.Log.e("PlayHistorySync", "parseIsoDate failed: $isoStr, ${e.message}")
            0L
        }
    }

    private suspend fun resolveWebDavServer(): Triple<String?, String?, String?> {
        val config = WebDavBackupConfig
        if (config.serverMode == "existing" && config.existingServerId > 0) {
            val server = DatabaseManager.instance.getMediaLibraryDao()
                .getById(config.existingServerId)
            if (server != null) {
                return Triple(server.url, server.account, server.password)
            }
        }
        return Triple(
            config.customUrl.takeIf { it.isNotBlank() },
            config.customAccount.takeIf { it.isNotBlank() },
            config.customPassword.takeIf { it.isNotBlank() }
        )
    }

    private suspend fun downloadFromWebDav(
        serverUrl: String,
        credential: String?
    ): JSONObject? {
        return try {
            val config = WebDavBackupConfig
            val baseUrl = serverUrl.trimEnd('/')
            val dirUrl = "$baseUrl${config.directory.trimEnd('/')}"
            val fileUrl = "$dirUrl/$SYNC_FILE_NAME"

            val requestBuilder = Request.Builder().url(fileUrl).get()
            credential?.let { requestBuilder.addHeader("Authorization", it) }
            val response = UnsafeOkHttpClient.client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val body = response.body?.string() ?: ""
            response.close()
            if (body.isEmpty()) return null
            JSONObject(body)
        } catch (e: Exception) {
            android.util.Log.e("PlayHistorySync", "WebDAV download failed: ${e.message}")
            null
        }
    }

    private suspend fun uploadToWebDav(
        serverUrl: String,
        credential: String?,
        data: JSONObject
    ): Boolean {
        return try {
            val config = WebDavBackupConfig
            val baseUrl = serverUrl.trimEnd('/')
            val dirUrl = "$baseUrl${config.directory.trimEnd('/')}"

            ensureDirectoryExists(dirUrl, credential)

            val fileUrl = "$dirUrl/$SYNC_FILE_NAME"
            val bytes = data.toString(2).toByteArray(Charsets.UTF_8)

            val requestBuilder = Request.Builder()
                .url(fileUrl)
                .put(bytes.toRequestBody("application/json".toMediaType()))
            credential?.let { requestBuilder.addHeader("Authorization", it) }
            val response = UnsafeOkHttpClient.client.newCall(requestBuilder.build()).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun ensureDirectoryExists(dirUrl: String, credential: String?) {
        try {
            val requestBuilder = Request.Builder()
                .url(dirUrl)
                .method("MKCOL", null)
            credential?.let { requestBuilder.addHeader("Authorization", it) }
            val response = UnsafeOkHttpClient.client.newCall(requestBuilder.build()).execute()
            val code = response.code
            response.close()
            if (code == 409) {
                val parentDir = dirUrl.substringBeforeLast('/')
                if (parentDir.length > 8) {
                    ensureDirectoryExists(parentDir, credential)
                    val retryBuilder = Request.Builder()
                        .url(dirUrl)
                        .method("MKCOL", null)
                    credential?.let { retryBuilder.addHeader("Authorization", it) }
                    val retryResponse = UnsafeOkHttpClient.client.newCall(retryBuilder.build()).execute()
                    retryResponse.close()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PlayHistorySync", "ensureDirectoryExists failed: ${e.message}")
        }
    }
}