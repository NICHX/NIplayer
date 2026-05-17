package com.xyoye.user_component.ui.activities.backup_manager

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.tencent.mmkv.MMKV
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.config.ThumbnailServerConfig
import com.xyoye.common_component.config.WebDavBackupConfig
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.network.helper.UnsafeOkHttpClient
import com.xyoye.common_component.utils.EntropyUtils
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.entity.MediaLibraryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManagerViewModel : BaseViewModel() {

    companion object {
        private const val ENC_PREFIX = "__ENC__"
        private const val BACKUP_FILE_PREFIX = "NIplayer_config_"
        private const val BACKUP_FILE_SUFFIX = ".json"
    }

    private fun encryptField(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        val encrypted = EntropyUtils.aesEncode(null, value) ?: return value
        return "$ENC_PREFIX$encrypted"
    }

    private fun decryptField(value: String?): String? {
        if (value == null || !value.startsWith(ENC_PREFIX)) return value
        val encrypted = value.removePrefix(ENC_PREFIX)
        return EntropyUtils.aesDecode(null, encrypted) ?: value
    }

    private suspend fun collectAllConfigs(): JSONObject {
        return withContext(Dispatchers.IO) {
            val root = JSONObject()
            val mmkv = MMKV.defaultMMKV()

            root.put("version", 1)
            root.put("app_config", collectAppConfig(mmkv))
            root.put("player_config", collectPlayerConfig(mmkv))
            root.put("subtitle_config", collectSubtitleConfig(mmkv))
            root.put("user_config", collectUserConfig(mmkv))
            root.put("thumbnail_config", collectThumbnailConfig(mmkv))

            root.put("servers", collectServers())
            root.put("server_thumbnail_configs", collectServerThumbnailConfigs())
            root.put("webdav_backup_config", collectWebDavBackupConfig())
            root
        }
    }

    private fun collectAppConfig(mmkv: MMKV): JSONObject {
        val cfg = JSONObject()
        cfg.put("showHiddenFile", mmkv.decodeBool("showHiddenFile"))
        cfg.put("darkMode", mmkv.decodeInt("darkMode"))
        val folder1 = mmkv.decodeString("commonlyFolder1")
        if (folder1 != null) cfg.put("commonlyFolder1", folder1)
        val folder2 = mmkv.decodeString("commonlyFolder2")
        if (folder2 != null) cfg.put("commonlyFolder2", folder2)
        cfg.put("lastOpenFolderEnable", mmkv.decodeBool("lastOpenFolderEnable"))
        return cfg
    }

    private fun collectPlayerConfig(mmkv: MMKV): JSONObject {
        val cfg = JSONObject()
        val boolKeys = listOf(
            "allowOrientationChange", "useSurfaceView", "useMediaCodeC",
            "useMediaCodeCH265", "useOpenSlEs", "autoPlayNext", "backgroundPlay"
        )
        val intKeys = listOf("usePlayerType", "wifiCacheSize", "mobileCacheSize")
        val floatKeys = listOf("newVideoSpeed", "pressVideoSpeed")
        val strKeys = listOf("usePixelFormat", "useVLCPixelFormat", "useVLCHWDecoder", "useVLCAudioOutput")

        for (key in boolKeys) cfg.put(key, mmkv.decodeBool(key))
        for (key in intKeys) cfg.put(key, mmkv.decodeInt(key))
        for (key in floatKeys) cfg.put(key, mmkv.decodeFloat(key))
        for (key in strKeys) {
            val value = mmkv.decodeString(key)
            if (value != null) cfg.put(key, value)
        }
        return cfg
    }

    private fun collectSubtitleConfig(mmkv: MMKV): JSONObject {
        val cfg = JSONObject()
        val boolKeys = listOf("autoLoadSameNameSubtitle", "autoMatchSubtitle")
        val intKeys = listOf("textSize", "strokeWidth", "textColor", "strokeColor")
        val strKeys = listOf("subtitlePriority")
        for (key in boolKeys) cfg.put(key, mmkv.decodeBool(key))
        for (key in intKeys) cfg.put(key, mmkv.decodeInt(key))
        for (key in strKeys) {
            val value = mmkv.decodeString(key)
            if (value != null) cfg.put(key, value)
        }
        val shooterSecret = mmkv.decodeString("shooterSecret")
        if (!shooterSecret.isNullOrEmpty()) {
            cfg.put("shooterSecret", encryptField(shooterSecret) ?: "")
        }
        return cfg
    }

    private fun collectUserConfig(mmkv: MMKV): JSONObject {
        val cfg = JSONObject()
        cfg.put("storageFileGridView", mmkv.decodeBool("storageFileGridView"))
        cfg.put("mediaLibraryGridView", mmkv.decodeBool("mediaLibraryGridView"))
        return cfg
    }

    private fun collectThumbnailConfig(mmkv: MMKV): JSONObject {
        val cfg = JSONObject()
        val boolKeys = listOf(
            "generateThumbnail", "generateForImage",
            "generateForVideo", "generateForAudio", "saveInSameDir"
        )
        for (key in boolKeys) cfg.put(key, mmkv.decodeBool(key))
        return cfg
    }

    private suspend fun collectServers(): JSONArray {
        val serverTypes = listOf(
            com.xyoye.data_component.enums.MediaType.SMB_SERVER,
            com.xyoye.data_component.enums.MediaType.FTP_SERVER,
            com.xyoye.data_component.enums.MediaType.WEBDAV_SERVER,
            com.xyoye.data_component.enums.MediaType.ALSIT_STORAGE,
            com.xyoye.data_component.enums.MediaType.EXTERNAL_STORAGE
        )
        val allServers = mutableListOf<MediaLibraryEntity>()
        for (type in serverTypes) {
            allServers.addAll(DatabaseManager.instance.getMediaLibraryDao().getByMediaTypeSuspend(type))
        }

        val arr = JSONArray()
        for (server in allServers) {
            val obj = JSONObject()
            obj.put("displayName", server.displayName)
            obj.put("url", server.url)
            obj.put("mediaType", server.mediaType.value)
            obj.put("describe", server.describe ?: "")
            obj.put("account", encryptField(server.account) ?: "")
            obj.put("password", encryptField(server.password) ?: "")
            obj.put("isAnonymous", server.isAnonymous)
            obj.put("port", server.port)
            obj.put("isActiveFTP", server.isActiveFTP)
            obj.put("ftpAddress", server.ftpAddress)
            obj.put("ftpEncoding", server.ftpEncoding)
            obj.put("smbV2", server.smbV2)
            obj.put("smbSharePath", server.smbSharePath ?: "")
            obj.put("remoteSecret", encryptField(server.remoteSecret) ?: "")
            obj.put("webDavStrict", server.webDavStrict)
            obj.put("remoteAnimeGrouping", server.remoteAnimeGrouping)
            arr.put(obj)
        }
        return arr
    }

    private fun collectServerThumbnailConfigs(): JSONObject {
        val cfg = JSONObject()
        val mmkv = MMKV.defaultMMKV()
        val allKeys = mmkv.allKeys() ?: return cfg
        val prefix = "server_thumbnail_enabled_"
        for (key in allKeys) {
            if (key.startsWith(prefix)) {
                cfg.put(key, mmkv.decodeBool(key))
            }
        }
        return cfg
    }

    private fun collectWebDavBackupConfig(): JSONObject {
        val cfg = JSONObject()
        val config = WebDavBackupConfig
        cfg.put("enabled", config.enabled)
        cfg.put("serverMode", config.serverMode)
        cfg.put("customUrl", config.customUrl)
        cfg.put("customAccount", config.customAccount)
        cfg.put("customPassword", encryptField(config.customPassword) ?: "")
        cfg.put("existingServerId", config.existingServerId)
        cfg.put("directory", config.directory)
        cfg.put("keepCount", config.keepCount)
        return cfg
    }

    fun getBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "${BACKUP_FILE_PREFIX}$timestamp$BACKUP_FILE_SUFFIX"
    }

    fun exportConfig(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = collectAllConfigs().toString(2)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                }
                showLoading()
                delay(500)
                hideLoading()
                ToastCenter.showSuccess("配置已导出")
            } catch (e: Exception) {
                e.printStackTrace()
                ToastCenter.showError("导出失败: ${e.message}")
            }
        }
    }

    fun importConfig(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).readText()
                } ?: throw Exception("无法读取文件")

                val root = JSONObject(jsonString)
                val mmkv = MMKV.defaultMMKV()

                restoreAppConfig(root.optJSONObject("app_config"), mmkv)
                restorePlayerConfig(root.optJSONObject("player_config"), mmkv)
                restoreSubtitleConfig(root.optJSONObject("subtitle_config"), mmkv)
                restoreUserConfig(root.optJSONObject("user_config"), mmkv)
                restoreThumbnailConfig(root.optJSONObject("thumbnail_config"), mmkv)
                restoreServers(root.optJSONArray("servers"))
                restoreServerThumbnailConfigs(root.optJSONObject("server_thumbnail_configs"))
                restoreWebDavBackupConfig(root.optJSONObject("webdav_backup_config"))

                showLoading()
                delay(500)
                hideLoading()
                ToastCenter.showSuccess("配置已导入，重启应用后生效")
            } catch (e: Exception) {
                e.printStackTrace()
                ToastCenter.showError("导入失败: ${e.message}")
            }
        }
    }

    fun uploadBackupToWebDav(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout(10000L) {
                    showLoading()
                    doUploadBackup()
                    hideLoading()
                }
                ToastCenter.showSuccess("备份已上传至WebDAV服务器")
            } catch (e: TimeoutCancellationException) {
                hideLoading()
                ToastCenter.showError("服务器不在线")
            } catch (e: Exception) {
                e.printStackTrace()
                hideLoading()
                ToastCenter.showError("WebDAV备份失败: ${e.message}")
            }
        }
    }

    private suspend fun doUploadBackup() {
        val json = collectAllConfigs().toString(2)
        val data = json.toByteArray(Charsets.UTF_8)
        val fileName = getBackupFileName()

        val config = WebDavBackupConfig
        val targetDir = config.directory.trimEnd('/')

        val (serverUrl, account, password) = resolveWebDavServer()
        if (serverUrl.isNullOrEmpty()) {
            hideLoading()
            throw Exception("WebDAV服务器未配置")
        }

        val credential = if (!account.isNullOrEmpty()) {
            Credentials.basic(account, password ?: "")
        } else null

        val baseUrl = serverUrl.trimEnd('/')
        val dirUrl = "$baseUrl$targetDir"

        ensureDirectoryExists(dirUrl, credential)

        val fileUrl = "$dirUrl/$fileName"
        val putSuccess = uploadFile(fileUrl, data, credential)
        if (!putSuccess) {
            hideLoading()
            throw Exception("上传备份文件失败")
        }

        cleanupOldBackups(dirUrl, credential, config.keepCount)

        config.lastUploadTime = System.currentTimeMillis()
    }

    fun restoreFromWebDav(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withTimeout(10000L) {
                    showLoading()
                    doRestoreFromWebDav()
                    delay(500)
                    hideLoading()
                }
                ToastCenter.showSuccess("配置已从WebDAV恢复，重启应用后生效")
            } catch (e: TimeoutCancellationException) {
                hideLoading()
                ToastCenter.showError("服务器不在线")
            } catch (e: Exception) {
                e.printStackTrace()
                hideLoading()
                ToastCenter.showError("从WebDAV恢复失败: ${e.message}")
            }
        }
    }

    private suspend fun doRestoreFromWebDav() {
        val config = WebDavBackupConfig
        val targetDir = config.directory.trimEnd('/')

        val (serverUrl, account, password) = resolveWebDavServer()
        if (serverUrl.isNullOrEmpty()) {
            hideLoading()
            throw Exception("WebDAV服务器未配置")
        }

        val credential = if (!account.isNullOrEmpty()) {
            Credentials.basic(account, password ?: "")
        } else null

        val baseUrl = serverUrl.trimEnd('/')
        val dirUrl = "$baseUrl$targetDir"

        val listBuilder = Request.Builder()
            .url(dirUrl)
            .method("PROPFIND", null)
        credential?.let { listBuilder.addHeader("Authorization", it) }
        listBuilder.addHeader("Depth", "1")
        val listResponse = UnsafeOkHttpClient.client.newCall(listBuilder.build()).execute()
        if (!listResponse.isSuccessful) {
            listResponse.close()
            hideLoading()
            throw Exception("无法获取备份文件列表")
        }
        val body = listResponse.body?.string() ?: ""
        listResponse.close()

        val backupFiles = parseBackupFileNames(body)
        if (backupFiles.isEmpty()) {
            hideLoading()
            throw Exception("WebDAV服务器上没有找到备份文件")
        }

        val latestFile = backupFiles.sorted().last()
        val fileUrl = "$dirUrl/$latestFile"

        val downloadBuilder = Request.Builder()
            .url(fileUrl)
            .get()
        credential?.let { downloadBuilder.addHeader("Authorization", it) }
        val downloadResponse = UnsafeOkHttpClient.client.newCall(downloadBuilder.build()).execute()
        if (!downloadResponse.isSuccessful) {
            downloadResponse.close()
            hideLoading()
            throw Exception("下载备份文件失败")
        }
        val jsonString = downloadResponse.body?.string() ?: ""
        downloadResponse.close()

        val root = JSONObject(jsonString)
        val mmkv = MMKV.defaultMMKV()

        restoreAppConfig(root.optJSONObject("app_config"), mmkv)
        restorePlayerConfig(root.optJSONObject("player_config"), mmkv)
        restoreSubtitleConfig(root.optJSONObject("subtitle_config"), mmkv)
        restoreUserConfig(root.optJSONObject("user_config"), mmkv)
        restoreThumbnailConfig(root.optJSONObject("thumbnail_config"), mmkv)
        restoreServers(root.optJSONArray("servers"))
        restoreServerThumbnailConfigs(root.optJSONObject("server_thumbnail_configs"))
        restoreWebDavBackupConfig(root.optJSONObject("webdav_backup_config"))
    }

    private suspend fun resolveWebDavServer(): Triple<String?, String?, String?> {
        val config = WebDavBackupConfig
        if (config.serverMode == "existing" && config.existingServerId > 0) {
            val server = DatabaseManager.instance.getMediaLibraryDao()
                .getById(config.existingServerId)
            if (server != null) {
                val account = server.account
                val password = decryptField(server.password)
                return Triple(server.url, account, password)
            }
        }
        return Triple(
            config.customUrl.takeIf { it.isNotBlank() },
            config.customAccount.takeIf { it.isNotBlank() },
            config.customPassword.takeIf { it.isNotBlank() }
        )
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

    private suspend fun uploadFile(fileUrl: String, data: ByteArray, credential: String?): Boolean {
        return try {
            val requestBuilder = Request.Builder()
                .url(fileUrl)
                .put(data.toRequestBody("application/json".toMediaType()))
            credential?.let { requestBuilder.addHeader("Authorization", it) }
            val response = UnsafeOkHttpClient.client.newCall(requestBuilder.build()).execute()
            val success = response.isSuccessful
            if (!success) {
                android.util.Log.e("WebDavBackup", "Upload failed: HTTP ${response.code} for $fileUrl")
            }
            response.close()
            success
        } catch (e: Exception) {
            android.util.Log.e("WebDavBackup", "Upload exception", e)
            false
        }
    }

    private suspend fun cleanupOldBackups(dirUrl: String, credential: String?, keepCount: Int) {
        try {
            val requestBuilder = Request.Builder()
                .url(dirUrl)
                .method("PROPFIND", null)
            credential?.let { requestBuilder.addHeader("Authorization", it) }
            requestBuilder.addHeader("Depth", "1")
            val response = UnsafeOkHttpClient.client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                response.close()
                return
            }
            val body = response.body?.string() ?: ""
            response.close()

            val backupFiles = parseBackupFileNames(body)
            if (backupFiles.size <= keepCount) return

            val filesToDelete = backupFiles.sorted().dropLast(keepCount)
            for (fileName in filesToDelete) {
                try {
                    val deleteUrl = "$dirUrl/$fileName"
                    val deleteBuilder = Request.Builder()
                        .url(deleteUrl)
                        .delete()
                    credential?.let { deleteBuilder.addHeader("Authorization", it) }
                    val deleteResponse = UnsafeOkHttpClient.client.newCall(deleteBuilder.build()).execute()
                    deleteResponse.close()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun parseBackupFileNames(xmlBody: String): List<String> {
        val files = mutableListOf<String>()
        try {
            val hrefRegex = Regex("<\\w+:href>(.*?)</\\w+:href>")
            val matches = hrefRegex.findAll(xmlBody)
            for (match in matches) {
                val href = match.groupValues[1].trimEnd('/')
                val fileName = href.substringAfterLast('/')
                if (fileName.startsWith(BACKUP_FILE_PREFIX) && fileName.endsWith(BACKUP_FILE_SUFFIX)) {
                    files.add(fileName)
                }
            }
        } catch (_: Exception) {
        }
        return files
    }

    fun getWebDavServerDisplayName(): String {
        val config = WebDavBackupConfig
        if (config.serverMode == "existing" && config.existingServerId > 0) {
            val server = runBlockingOnIO {
                DatabaseManager.instance.getMediaLibraryDao()
                    .getById(config.existingServerId)
            }
            return server?.displayName ?: "请选择WebDAV服务器"
        }
        return "请选择WebDAV服务器"
    }

    fun syncPlayHistory(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!com.xyoye.common_component.config.PlayHistorySyncConfig.enabled) {
                com.xyoye.common_component.weight.ToastCenter.showError("请先开启播放记录同步")
                return@launch
            }
            showLoading()
            when (val result = com.xyoye.common_component.utils.PlayHistorySyncManager.sync()) {
                is com.xyoye.common_component.utils.PlayHistorySyncManager.SyncResult.Success -> {
                    hideLoading()
                    com.xyoye.common_component.weight.ToastCenter.showSuccess(
                        "同步完成，上传${result.uploaded}条，下载${result.downloaded}条"
                    )
                }
                is com.xyoye.common_component.utils.PlayHistorySyncManager.SyncResult.Error -> {
                    hideLoading()
                    com.xyoye.common_component.weight.ToastCenter.showError(result.message)
                }
            }
        }
    }

    private fun <T> runBlockingOnIO(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking(Dispatchers.IO) { block() }
    }

    private fun restoreAppConfig(json: JSONObject?, mmkv: MMKV) {
        if (json == null) return
        if (json.has("showHiddenFile")) mmkv.encode("showHiddenFile", json.optBoolean("showHiddenFile"))
        if (json.has("darkMode")) mmkv.encode("darkMode", json.optInt("darkMode"))
        if (json.has("commonlyFolder1")) mmkv.encode("commonlyFolder1", json.optString("commonlyFolder1"))
        if (json.has("commonlyFolder2")) mmkv.encode("commonlyFolder2", json.optString("commonlyFolder2"))
        if (json.has("lastOpenFolderEnable")) mmkv.encode("lastOpenFolderEnable", json.optBoolean("lastOpenFolderEnable"))
    }

    private fun restorePlayerConfig(json: JSONObject?, mmkv: MMKV) {
        if (json == null) return
        for (key in json.keys()) {
            val value = json.get(key)
            when (value) {
                is Boolean -> mmkv.encode(key, value)
                is Int -> mmkv.encode(key, value)
                is Double -> mmkv.encode(key, value.toFloat())
                is String -> mmkv.encode(key, value)
            }
        }
    }

    private fun restoreSubtitleConfig(json: JSONObject?, mmkv: MMKV) {
        if (json == null) return
        for (key in json.keys()) {
            if (key == "shooterSecret") {
                val value = decryptField(json.optString(key))
                if (value != null) mmkv.encode(key, value)
                continue
            }
            val value = json.get(key)
            when (value) {
                is Boolean -> mmkv.encode(key, value)
                is Int -> mmkv.encode(key, value)
                is String -> mmkv.encode(key, value)
            }
        }
    }

    private fun restoreUserConfig(json: JSONObject?, mmkv: MMKV) {
        if (json == null) return
        if (json.has("storageFileGridView")) mmkv.encode("storageFileGridView", json.optBoolean("storageFileGridView"))
        if (json.has("mediaLibraryGridView")) mmkv.encode("mediaLibraryGridView", json.optBoolean("mediaLibraryGridView"))
    }

    private fun restoreThumbnailConfig(json: JSONObject?, mmkv: MMKV) {
        if (json == null) return
        for (key in json.keys()) {
            mmkv.encode(key, json.optBoolean(key))
        }
    }

    private suspend fun restoreServers(json: JSONArray?) {
        if (json == null) return
        val dao = DatabaseManager.instance.getMediaLibraryDao()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val entity = MediaLibraryEntity(
                id = 0,
                displayName = obj.optString("displayName"),
                url = obj.optString("url"),
                mediaType = com.xyoye.data_component.enums.MediaType.fromValue(obj.optString("mediaType")),
                describe = obj.optString("describe"),
                account = decryptField(obj.optString("account")),
                password = decryptField(obj.optString("password")),
                isAnonymous = obj.optBoolean("isAnonymous"),
                port = obj.optInt("port"),
                isActiveFTP = obj.optBoolean("isActiveFTP"),
                ftpAddress = obj.optString("ftpAddress"),
                ftpEncoding = obj.optString("ftpEncoding", "UTF-8"),
                smbV2 = obj.optBoolean("smbV2", true),
                smbSharePath = obj.optString("smbSharePath"),
                remoteSecret = decryptField(obj.optString("remoteSecret")),
                webDavStrict = obj.optBoolean("webDavStrict", true),
                remoteAnimeGrouping = obj.optBoolean("remoteAnimeGrouping")
            )
            val existing = dao.getByUrl(entity.url, entity.mediaType)
            if (existing == null) {
                dao.insert(entity)
            }
        }
    }

    private fun restoreServerThumbnailConfigs(json: JSONObject?) {
        if (json == null) return
        for (key in json.keys()) {
            ThumbnailServerConfig.putServerThumbnailEnabled(
                key.removePrefix("server_thumbnail_enabled_").toIntOrNull() ?: continue,
                json.optBoolean(key)
            )
        }
    }

    private fun restoreWebDavBackupConfig(json: JSONObject?) {
        if (json == null) return
        val config = WebDavBackupConfig
        if (json.has("enabled")) config.enabled = json.optBoolean("enabled")
        if (json.has("serverMode")) config.serverMode = json.optString("serverMode")
        if (json.has("customUrl")) config.customUrl = json.optString("customUrl")
        if (json.has("customAccount")) config.customAccount = json.optString("customAccount")
        if (json.has("customPassword")) {
            config.customPassword = decryptField(json.optString("customPassword")) ?: ""
        }
        if (json.has("existingServerId")) config.existingServerId = json.optInt("existingServerId")
        if (json.has("directory")) config.directory = json.optString("directory")
        if (json.has("keepCount")) config.keepCount = json.optInt("keepCount")
    }
}