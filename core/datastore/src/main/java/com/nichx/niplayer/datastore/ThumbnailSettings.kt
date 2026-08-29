package com.nichx.niplayer.datastore

import androidx.annotation.StringRes
import com.tencent.mmkv.MMKV

/**
 * 缩略图生成配置（MMKV），迁移自旧仓库 `ThumbnailConfigTable`。
 *
 * 旧仓库通过 `@MMKVKotlinClass` 注解生成 `ThumbnailConfig` 对象，v2 改为手动
 * MMKV 读写。
 *
 * 配置项：
 * - [generateThumbnail]：总开关
 * - [generateForVideo] / [generateForImage] / [generateForAudio]：按媒体类型开关
 * - [generationMode]：生成策略（全部生成 / 仅播放后生成 / 关闭）
 * - [saveInSameDir]：远端存储时是否将缩略图回写到服务器同目录（.thumb/ 下）
 * - [framePosition]：取帧位置策略
 * - [customPositionSeconds]：自定义取帧秒数（framePosition=CUSTOM 时生效）
 *
 * 存储源级生成策略覆盖见 [getLibraryGenerationMode]（旧版布尔开关自动迁移，
 * 见 [getLibraryGenerationMode] 注释）。
 */
object ThumbnailSettings {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    private const val KEY_GENERATE_THUMBNAIL = "generate_thumbnail"
    private const val KEY_GENERATE_FOR_VIDEO = "generate_for_video"
    private const val KEY_GENERATE_FOR_IMAGE = "generate_for_image"
    private const val KEY_GENERATE_FOR_AUDIO = "generate_for_audio"
    private const val KEY_SAVE_IN_SAME_DIR = "save_in_same_dir"
    private const val KEY_FRAME_POSITION = "thumbnail_frame_position"
    private const val KEY_CUSTOM_POSITION_SECONDS = "thumbnail_custom_position_seconds"
    private const val KEY_UPDATE_ON_EXIT = "thumbnail_update_on_exit"

    /** 总开关：是否生成缩略图。默认 true。 */
    var generateThumbnail: Boolean
        get() = mmkv.decodeBool(KEY_GENERATE_THUMBNAIL, true)
        set(value) { mmkv.encode(KEY_GENERATE_THUMBNAIL, value) }

    /** 为视频生成缩略图。默认 true。 */
    var generateForVideo: Boolean
        get() = mmkv.decodeBool(KEY_GENERATE_FOR_VIDEO, true)
        set(value) { mmkv.encode(KEY_GENERATE_FOR_VIDEO, value) }

    /** 为图片生成缩略图。默认 true。 */
    var generateForImage: Boolean
        get() = mmkv.decodeBool(KEY_GENERATE_FOR_IMAGE, true)
        set(value) { mmkv.encode(KEY_GENERATE_FOR_IMAGE, value) }

    /** 为音频生成缩略图（embedded picture）。默认 true。 */
    var generateForAudio: Boolean
        get() = mmkv.decodeBool(KEY_GENERATE_FOR_AUDIO, true)
        set(value) { mmkv.encode(KEY_GENERATE_FOR_AUDIO, value) }

    /** 远端存储时将缩略图回写到服务器 .thumb/ 目录。默认 true。 */
    var saveInSameDir: Boolean
        get() = mmkv.decodeBool(KEY_SAVE_IN_SAME_DIR, true)
        set(value) { mmkv.encode(KEY_SAVE_IN_SAME_DIR, value) }

    /** 取帧位置策略 key。默认 "5s"。 */
    var framePositionKey: String
        get() = mmkv.decodeString(KEY_FRAME_POSITION, "5s") ?: "5s"
        set(value) { mmkv.encode(KEY_FRAME_POSITION, value) }

    /** 自定义取帧秒数（仅 framePositionKey="custom" 时生效）。默认 10。 */
    var customPositionSeconds: Int
        get() = mmkv.decodeInt(KEY_CUSTOM_POSITION_SECONDS, 10)
        set(value) { mmkv.encode(KEY_CUSTOM_POSITION_SECONDS, value) }

    /**
     * 退出播放后是否用最后一帧更新列表缩略图。默认 false（保持默认缩略图）。
     *
     * 开启后退出播放时把缩略图替换为播放最后一帧的画面。HDR 视频（Dolby
     * Vision / HDR10 / HLG）的最后一帧需要 MediaMetadataRetriever 远程重新取帧
     * （系统 tone map 后颜色才正确），退出后缩略图更新会有一段时间延迟。
     */
    var updateOnExit: Boolean
        get() = mmkv.decodeBool(KEY_UPDATE_ON_EXIT, false)
        set(value) { mmkv.encode(KEY_UPDATE_ON_EXIT, value) }

    // ---------- 生成策略 ----------

    private const val KEY_GENERATION_MODE = "thumbnail_generation_mode"

    /**
     * 全局缩略图生成策略。默认 [ThumbnailGenerationMode.ALL]（保持 v2 既有行为）。
     *
     * 设计背景：浏览目录时批量取帧会触发大量远程文件读取，易触发网盘封控；
     * 而"已播放"的文件已被读取过，播放后生成缩略图属正常行为，无额外风险。
     */
    var generationMode: ThumbnailGenerationMode
        get() = ThumbnailGenerationMode.fromKey(
            mmkv.decodeString(KEY_GENERATION_MODE, ThumbnailGenerationMode.ALL.key)
                ?: ThumbnailGenerationMode.ALL.key,
        )
        set(value) { mmkv.encode(KEY_GENERATION_MODE, value.key) }

    // ---------- 存储源级别覆盖 ----------

    private const val KEY_PREFIX_LIBRARY_MODE = "thumbnail_lib_mode_"
    private const val KEY_PREFIX_LIBRARY_ENABLED = "thumbnail_lib_enabled_"

    /**
     * 获取指定存储源的生成策略覆盖。
     * - 非 null：该存储源使用指定策略
     * - null：未设置覆盖，遵循全局 [generationMode]
     *
     * 兼容迁移：旧版布尔开关（[KEY_PREFIX_LIBRARY_ENABLED]）读取时映射为
     * `true → ALL`、`false → OFF`，无需用户重新配置。
     */
    fun getLibraryGenerationMode(libId: Int): ThumbnailGenerationMode? {
        val key = "$KEY_PREFIX_LIBRARY_MODE$libId"
        if (mmkv.contains(key)) {
            return ThumbnailGenerationMode.fromKey(mmkv.decodeString(key, "") ?: "")
        }
        val legacyKey = "$KEY_PREFIX_LIBRARY_ENABLED$libId"
        return if (mmkv.contains(legacyKey)) {
            if (mmkv.decodeBool(legacyKey)) ThumbnailGenerationMode.ALL else ThumbnailGenerationMode.OFF
        } else {
            null
        }
    }

    /** 设置指定存储源的生成策略覆盖。传 `null` 清除覆盖，恢复为遵循全局策略。 */
    fun setLibraryGenerationMode(libId: Int, mode: ThumbnailGenerationMode?) {
        val key = "$KEY_PREFIX_LIBRARY_MODE$libId"
        if (mode == null) {
            mmkv.removeValueForKey(key)
        } else {
            mmkv.encode(key, mode.key)
        }
    }

    /**
     * 导出所有存储源级别生成策略覆盖（供备份）。
     *
     * 遍历 MMKV 中所有 `thumbnail_lib_mode_*` 与旧版 `thumbnail_lib_enabled_*` 键，
     * 通过 [getLibraryGenerationMode] 取得有效策略（含旧版布尔开关迁移），
     * 返回 libId -> mode key 映射。空 map 表示无任何覆盖。
     */
    fun snapshotAllLibraryModes(): Map<Int, String> = buildMap {
        val keys = mmkv.allKeys() ?: return@buildMap
        val seen = mutableSetOf<Int>()
        for (key in keys) {
            val libId = when {
                key.startsWith(KEY_PREFIX_LIBRARY_MODE) ->
                    key.removePrefix(KEY_PREFIX_LIBRARY_MODE).toIntOrNull()
                key.startsWith(KEY_PREFIX_LIBRARY_ENABLED) ->
                    key.removePrefix(KEY_PREFIX_LIBRARY_ENABLED).toIntOrNull()
                else -> null
            }
            if (libId != null && seen.add(libId)) {
                getLibraryGenerationMode(libId)?.let { mode -> put(libId, mode.key) }
            }
        }
    }

    // ---------- 存储源级别回写覆盖 ----------

    private const val KEY_PREFIX_LIBRARY_WRITEBACK = "thumbnail_lib_writeback_"

    /**
     * 获取指定存储源的"回写"开关覆盖（是否把生成的缩略图/封面回写到服务器）。
     * - 非 null：该存储源强制开启/关闭回写
     * - null：未设置覆盖，遵循全局 [saveInSameDir]
     */
    fun getLibraryWriteBack(libId: Int): Boolean? {
        val key = "$KEY_PREFIX_LIBRARY_WRITEBACK$libId"
        return if (mmkv.contains(key)) mmkv.decodeBool(key) else null
    }

    /** 设置指定存储源的"回写"开关覆盖。传 `null` 清除覆盖，恢复为遵循全局开关。 */
    fun setLibraryWriteBack(libId: Int, enabled: Boolean?) {
        val key = "$KEY_PREFIX_LIBRARY_WRITEBACK$libId"
        if (enabled == null) {
            mmkv.removeValueForKey(key)
        } else {
            mmkv.encode(key, enabled)
        }
    }

    /** 存储源生效的回写开关：存储源级覆盖优先，否则全局 [saveInSameDir]。 */
    fun effectiveWriteBack(libId: Int): Boolean =
        getLibraryWriteBack(libId) ?: saveInSameDir

    /**
     * 导出所有存储源级别回写覆盖（供备份）。
     *
     * 遍历 MMKV 中所有 `thumbnail_lib_writeback_*` 键，返回 libId -> 回写开关 映射。
     * 空 map 表示无任何覆盖。
     */
    fun snapshotAllLibraryWriteBacks(): Map<Int, Boolean> = buildMap {
        val keys = mmkv.allKeys() ?: return@buildMap
        for (key in keys) {
            if (!key.startsWith(KEY_PREFIX_LIBRARY_WRITEBACK)) continue
            val libId = key.removePrefix(KEY_PREFIX_LIBRARY_WRITEBACK).toIntOrNull() ?: continue
            put(libId, mmkv.decodeBool(key))
        }
    }

    // ---------- 生成策略生效 ----------

    /** 存储源生效的生成策略：存储源级覆盖优先，否则全局策略。 */
    fun effectiveMode(libId: Int): ThumbnailGenerationMode =
        getLibraryGenerationMode(libId) ?: generationMode

    /** 浏览目录时是否允许批量生成缩略图（仅 [ThumbnailGenerationMode.ALL] 模式）。 */
    fun shouldGenerateOnBrowse(libId: Int): Boolean =
        effectiveMode(libId) == ThumbnailGenerationMode.ALL

    /** 播放后是否允许生成缩略图（关闭之外均可：文件已被读取，无额外封控风险）。 */
    fun shouldGenerateOnPlayback(libId: Int): Boolean =
        effectiveMode(libId) != ThumbnailGenerationMode.OFF
}

/**
 * 缩略图生成策略。
 *
 * - [ALL]：浏览目录时预加载服务端缓存 + 批量生成 + 上传（适合本地 NAS / 不限流服务器）
 * - [AFTER_PLAY]：浏览时仅预加载服务端已有缓存，播放退出后按需生成并上传
 *   （文件已被读取，属正常行为，规避浏览时批量读取导致的网盘封控）
 * - [OFF]：不生成缩略图，仅使用已有缓存
 */
enum class ThumbnailGenerationMode(val key: String, @StringRes val labelRes: Int) {
    ALL("all", R.string.thumbnail_mode_all),
    AFTER_PLAY("after_play", R.string.thumbnail_mode_after_play),
    OFF("off", R.string.thumbnail_mode_off);

    companion object {
        fun fromKey(key: String): ThumbnailGenerationMode =
            entries.find { it.key == key } ?: ALL
    }
}

/** 取帧位置策略枚举。 */
enum class ThumbnailFramePosition(val key: String, @StringRes val labelRes: Int) {
    POS_5S("5s", R.string.thumbnail_frame_5s),
    POS_10_PCT("10pct", R.string.thumbnail_frame_10pct),
    POS_50_PCT("50pct", R.string.thumbnail_frame_50pct),
    POS_CUSTOM("custom", R.string.thumbnail_frame_custom);

    companion object {
        fun fromKey(key: String): ThumbnailFramePosition =
            entries.find { it.key == key } ?: POS_5S
    }
}
