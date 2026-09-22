package com.nichx.niplayer.datastore

/**
 * 配色方案存储迁移：旧版 ordinal → 稳定 key（2026-09-22）。
 *
 * ## 为什么需要它
 *
 * [ThemeSettings] 原先存的是 `:core:designsystem` 里 `NiScheme` 的 **ordinal**。
 * 2026-09-22 把配色从 18 套精简到 12 套（删除整个「糖果系 / 薄荷曼波」）后，
 * 枚举序号整体前移 —— 若不做迁移，已保存过主题的用户会**静默换成另一套配色**
 * （序号越界则回落默认），且编译 / lint / detekt 都不会报错。
 *
 * 因此存储改为稳定 key 字符串，并在首次读取时用本表把遗留的旧序号一次性换算过去。
 *
 * ## 换算规则
 *
 * 每个旧序号映射到**保留方案里主色相最接近**的一套（按 HSL 色相角比较），
 * 而不是一律回落到默认的雾蓝；保留的 12 套映射到自身，用户原来的选择完全不变。
 *
 * ⚠️ 本表是**冻结的历史契约**：一旦发布过带本迁移的版本，就不要再改动其中的对应关系，
 * 否则老设备的主题会再次错位。`ThemeSchemeMigrationTest` 把 18 项内容钉死。
 */
internal object ThemeSchemeMigration {

    /**
     * 旧版 18 套配色的 ordinal → 保留方案的 key，**顺序即旧枚举的声明顺序**。
     *
     * 被删除的 6 套按主色相就近落位：
     * - 薄荷糖（薄荷绿 #5CBFA0，色相 161°）→ [FOREST]（136°）
     * - 樱花布丁（樱粉 #F08FB4，337°）→ [STRAWBERRY]（348°）
     * - 香芋慕斯（薰衣草紫 #9290D6，242°）→ [BLUEBERRY]（226°）
     * - 青柠薄荷（薄荷绿 #2FBF8F，160°）→ [FOREST]
     * - 泡泡糖（泡泡糖粉 #F26BB0，329°）→ [STRAWBERRY]
     * - 橘夏汽水（汽水橙 #F2873F，24°）→ [CARAMEL]（30°）
     */
    val LEGACY_ORDINAL_KEYS: List<String> = listOf(
        // 冷色系
        "MISTY", "BLUEBERRY", "DENIM",
        // 暖色系
        "ROSE_DUST", "STRAWBERRY", "CORAL",
        // 自然色系
        "FOREST", "MATCHA", "CARAMEL",
        // 糖果系 / 薄荷曼波（已删除，就近映射）
        "FOREST", "STRAWBERRY", "BLUEBERRY", "FOREST", "STRAWBERRY", "CARAMEL",
        // 莫兰迪
        "ALMOND", "MAUVE", "SAGE",
    )

    /** 旧序号换算为 key；越界（数据损坏 / 被未来版本写入）回落默认方案。 */
    fun keyFromLegacyOrdinal(ordinal: Int): String =
        LEGACY_ORDINAL_KEYS.getOrElse(ordinal) { ThemeSettings.DEFAULT_SCHEME_KEY }

    /**
     * 决定本次生效的配色方案 key（**纯函数**，MMKV 的读写留在调用方）。
     *
     * 抽成纯函数是为了让三条分支都能被单测覆盖 —— MMKV 需要 Android 运行时，
     * 在纯 JVM 单测里跑不起来：
     * 1. 已有稳定 key → 直接采用（迁移不会覆盖用户当前选择）；
     * 2. 只有遗留的旧 ordinal（[legacyOrdinal] 非 null）→ 换算为 key；
     * 3. 两者都没有（全新安装）→ 默认方案。
     *
     * @param storedKey MMKV 里读到的稳定 key；缺失或空白视为没有
     * @param legacyOrdinal 旧版 ordinal 键的值；键不存在时传 null
     */
    fun resolveKey(storedKey: String?, legacyOrdinal: Int?): String = when {
        !storedKey.isNullOrBlank() -> storedKey
        legacyOrdinal != null -> keyFromLegacyOrdinal(legacyOrdinal)
        else -> ThemeSettings.DEFAULT_SCHEME_KEY
    }
}
