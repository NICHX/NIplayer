package com.xyoye.common_component.utils

import com.xyoye.common_component.config.PlayHistorySyncConfig
import com.xyoye.common_component.config.WebDavBackupConfig
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.extension.toMd5String
import com.xyoye.common_component.network.helper.UnsafeOkHttpClient
import com.xyoye.data_component.entity.PlayHistoryEntity
import com.xyoye.data_component.enums.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

object PlayHistorySyncManager {

    private const val SYNC_FILE_NAME = "NIplayer_play_history.json"
    private val SYNC_MEDIA_TYPES = listOf(
        MediaType.SMB_SERVER,
        MediaType.FTP_SERVER,
        MediaType.WEBDAV_SERVER,
        MediaType.ALSIT_STORAGE
    )

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        try {
            val (serverUrl, account, password) = resolveWebDavServer()
            if (serverUrl.isNullOrEmpty()) {
                return@withContext SyncResult.Error("WebDAV服务器未配置")
            }

            val credential = if (!account.isNullOrEmpty()) {
                Credentials.basic(account, password ?: "")
            } else null

            val config = PlayHistorySyncConfig
            config.ensureDeviceId()
            val lastSyncTime = config.lastSyncTime

            val localDirty = DatabaseManager.instance.getPlayHistoryDao()
                .getModifiedSince(SYNC_MEDIA_TYPES, lastSyncTime)

            val cloudData = downloadFromWebDav(serverUrl, credential)

            val merged = if (cloudData != null) {
                mergeLocalToCloud(cloudData, localDirty)
            } else {
                buildSyncData(localDirty)
            }

            val uploadSuccess = uploadToWebDav(serverUrl, credential, merged)
            if (!uploadSuccess) {
                return@withContext SyncResult.Error("上传同步数据失败")
            }

            val remoteDirty = if (cloudData != null) {
                filterNewRecords(cloudData, lastSyncTime)
            } else {
                emptyList()
            }

            val appliedCount = mergeCloudToLocal(remoteDirty)

            config.lastSyncTime = System.currentTimeMillis()

            SyncResult.Success(localDirty.size, appliedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResult.Error("同步失败: ${e.message}")
        }
    }

    private fun buildSyncData(records: List<PlayHistoryEntity>): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        root.put("device_id", PlayHistorySyncConfig.deviceId)
        root.put("last_sync_time", isoFormat.format(Date()))
        val arr = JSONArray()
        for (record in records) {
            arr.put(recordToJson(record))
        }
        root.put("records", arr)
        return root
    }

    private fun mergeLocalToCloud(
        cloudData: JSONObject,
        localDirty: List<PlayHistoryEntity>
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
            val syncKey = buildSyncKey(local)
            val json = recordToJson(local)
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
        root.put("version", 1)
        root.put("device_id", PlayHistorySyncConfig.deviceId)
        root.put("last_sync_time", isoFormat.format(Date()))
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

        val libraries = mutableMapOf<Int, MediaType>()
        for (type in SYNC_MEDIA_TYPES) {
            val servers = libraryDao.getByMediaTypeSuspend(type)
            for (server in servers) {
                libraries[server.id] = server.mediaType
            }
        }

        for (remoteJson in remoteRecords) {
            val mediaTypeStr = remoteJson.optString("media_type")
            val mediaType = MediaType.fromValue(mediaTypeStr)
            if (mediaType !in SYNC_MEDIA_TYPES) continue

            val storagePath = remoteJson.optString("storage_path")
            if (storagePath.isEmpty()) continue

            val matchedStorageId = findMatchingStorageId(mediaType, storagePath, libraries)
            if (matchedStorageId == null) continue

            val uniqueKey = buildUniqueKey(matchedStorageId, storagePath)
            val existing = dao.getPlayHistory(uniqueKey, matchedStorageId)

            val remotePlayTime = parseIsoDate(remoteJson.optString("play_time"))
            if (existing != null && existing.playTime.time >= remotePlayTime) {
                continue
            }

            val entity = PlayHistoryEntity(
                id = 0,
                videoName = remoteJson.optString("video_name"),
                url = "",
                mediaType = mediaType,
                videoPosition = remoteJson.optLong("video_position"),
                videoDuration = remoteJson.optLong("video_duration"),
                playTime = Date(remotePlayTime),
                subtitlePath = remoteJson.optString("subtitle_path").ifEmpty { null },
                audioPath = remoteJson.optString("audio_path").ifEmpty { null },
                uniqueKey = uniqueKey,
                storagePath = storagePath,
                storageId = matchedStorageId
            )
            dao.insert(entity)
            appliedCount++
        }

        return appliedCount
    }

    private fun findMatchingStorageId(
        mediaType: MediaType,
        storagePath: String,
        libraries: Map<Int, MediaType>
    ): Int? {
        for ((id, type) in libraries) {
            if (type == mediaType) return id
        }
        return null
    }

    private fun buildSyncKey(entity: PlayHistoryEntity): String {
        val mediaType = entity.mediaType.value
        val storagePath = entity.storagePath ?: ""
        return "$mediaType:$storagePath"
    }

    private fun buildUniqueKey(storageId: Int, storagePath: String): String {
        return "$storageId-$storagePath".toMd5String()
    }

    private fun recordToJson(entity: PlayHistoryEntity): JSONObject {
        val json = JSONObject()
        json.put("sync_key", buildSyncKey(entity))
        json.put("media_type", entity.mediaType.value)
        json.put("storage_path", entity.storagePath ?: "")
        json.put("video_name", entity.videoName)
        json.put("video_position", entity.videoPosition)
        json.put("video_duration", entity.videoDuration)
        json.put("play_time", isoFormat.format(entity.playTime))
        json.put("subtitle_path", entity.subtitlePath ?: "")
        json.put("audio_path", entity.audioPath ?: "")
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
            isoFormat.parse(isoStr)?.time ?: 0L
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
        }
    }

    sealed class SyncResult {
        data class Success(val uploaded: Int, val downloaded: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
    }
}
