package com.xyoye.user_component.ui.activities.backup_manager

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.tencent.mmkv.MMKV
import com.xyoye.common_component.base.BaseViewModel
import com.xyoye.common_component.config.ThumbnailServerConfig
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.common_component.utils.EntropyUtils
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.entity.MediaLibraryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class BackupManagerViewModel : BaseViewModel() {

    companion object {
        private const val ENC_PREFIX = "__ENC__"
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
}
