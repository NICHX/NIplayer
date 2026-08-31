package com.nichx.niplayer.datastore

import com.tencent.mmkv.MMKV

/** 控制功能可放置的面：HUD 左列 / HUD 右列 / 更多菜单。 */
enum class PlayerControlSurface { LEFT, RIGHT, MORE }

/** 屏幕方向：竖屏 / 横屏各自独立保存一套布局。 */
enum class PlayerControlOrientation { PORTRAIT, LANDSCAPE }

/** 单个播放器控制功能的布局配置。 */
data class PlayerControlEntry(
    val id: String,
    val surface: PlayerControlSurface,
    val visible: Boolean,
    val order: Int,
)

/**
 * 播放器控制功能的自定义布局（MMKV）。
 *
 * 把 HUD 侧边按钮与「更多」菜单里的功能统一纳入一份可自由移动的目录，且竖屏/横屏各存一份：
 * - [ALL_IDS] 是全部功能的稳定标识；
 * - 每个功能按 (方向,id) 持久化 `surface|visible|order`；
 * - 任意功能都可以放在 HUD 左列 / HUD 右列 / 更多菜单，三者之间自由互移，
 *   并在所在面内按 [order] 排序。
 *
 * 渲染端（播放器）按当前屏幕方向读取对应布局；设置端按「横屏/竖屏」两个 Tab 分别编辑，
 * 一趟读写一处同步。
 */
object PlayerControlLayout {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }
    private const val PREFIX = "player_ctrl_layout_"

    /** 全部功能 id（按此默认顺序展示与排序）。 */
    val ALL_IDS: List<String> = listOf(
        "rotate", "ab_loop", "black_bar_crop", "lock", "screenshot",
        "long_press_speed", "pip", "sleep_timer", "media_info", "bookmarks",
    )

    /** 全部可放置的面，固定顺序（用于循环切换）。 */
    val ALL_SURFACES: List<PlayerControlSurface> =
        listOf(PlayerControlSurface.LEFT, PlayerControlSurface.RIGHT, PlayerControlSurface.MORE)

    /** 持久化 key：按方向分桶。 */
    private fun key(id: String, orientation: PlayerControlOrientation): String = PREFIX + orientation.name + "_" + id

    /** 每个功能的默认面（未自定义 / 重置后恢复）。 */
    private val DEFAULT_SURFACE = mapOf(
        "rotate" to PlayerControlSurface.LEFT,
        "ab_loop" to PlayerControlSurface.LEFT,
        "black_bar_crop" to PlayerControlSurface.LEFT,
        "lock" to PlayerControlSurface.RIGHT,
        "screenshot" to PlayerControlSurface.RIGHT,
        "long_press_speed" to PlayerControlSurface.MORE,
        "pip" to PlayerControlSurface.MORE,
        "sleep_timer" to PlayerControlSurface.MORE,
        "media_info" to PlayerControlSurface.MORE,
        "bookmarks" to PlayerControlSurface.MORE,
    )

    /** 是否为 HUD 面（左/右列），即画面侧边的近场按钮。 */
    fun isHudSurface(surface: PlayerControlSurface): Boolean = surface != PlayerControlSurface.MORE

    /** 默认面：HUD 功能的默认列；更多功能一律返回 [PlayerControlSurface.MORE]。 */
    fun defaultSurface(id: String): PlayerControlSurface =
        DEFAULT_SURFACE[id] ?: PlayerControlSurface.MORE

    /** 读取某功能的配置；未自定义时返回默认（可见）。
     * @param defaultOrder 目录中的默认顺序（默认编序由调用方以 ALL_IDS 下标传入）。
     * @param orientation 所属屏幕方向（竖屏/横屏各自独立）。
     */
    fun loadEntry(id: String, defaultOrder: Int, orientation: PlayerControlOrientation): PlayerControlEntry {
        val raw = mmkv.decodeString(key(id, orientation))
            ?: return PlayerControlEntry(id = id, surface = defaultSurface(id), visible = true, order = defaultOrder)
        val parts = raw.split("|")
        val surface = runCatching { PlayerControlSurface.valueOf(parts.getOrElse(0) { "" }) }
            .getOrDefault(defaultSurface(id))
        val visible = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: true
        val order = parts.getOrNull(2)?.toIntOrNull() ?: defaultOrder
        return PlayerControlEntry(id = id, surface = surface, visible = visible, order = order)
    }

    /** 保存某功能的布局配置。 */
    fun saveEntry(
        id: String,
        surface: PlayerControlSurface,
        visible: Boolean,
        order: Int,
        orientation: PlayerControlOrientation,
    ) {
        mmkv.encode(key(id, orientation), "$surface|$visible|$order")
    }

    /** 恢复某个方向的默认：清空该方向全部自定义。 */
    fun reset(orientation: PlayerControlOrientation) {
        ALL_IDS.forEach { mmkv.removeValueForKey(key(it, orientation)) }
    }
}