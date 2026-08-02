# NIplayer 深色模式配色与横屏/大屏布局分析及优化方案

> 本文档基于 NIplayer v2 代码库进行系统分析，涵盖深色模式配色方案、横屏布局适配与大屏/平板布局优化三个方向。生成日期：2026-07-30。

---

## TOC

- [1. 深色模式配色分析](#1-深色模式配色分析)
  - [1.1 当前架构概述](#11-当前架构概述)
  - [1.2 深色色板核心特征](#12-深色色板核心特征)
  - [1.3 多配色方案体系](#13-多配色方案体系)
  - [1.4 存在风险与改进建议](#14-存在风险与改进建议)
- [2. 横屏布局分析](#2-横屏布局分析)
  - [2.1 当前实现概述](#21-当前实现概述)
  - [2.2 播放器横屏方案](#22-播放器横屏方案)
  - [2.3 非播放场景的横屏问题](#23-非播放场景的横屏问题)
  - [2.4 改进建议](#24-改进建议)
- [3. 大屏与平板布局分析](#3-大屏与平板布局分析)
  - [3.1 当前状态](#31-当前状态)
  - [3.2 核心问题清单](#32-核心问题清单)
  - [3.3 改进建议](#33-改进建议)
- [4. 综合优化路线图](#4-综合优化路线图)
  - [4.1 优先级划分](#41-优先级划分)
  - [4.2 实施路径](#42-实施路径)

---

## 1. 深色模式配色分析

### 1.1 当前架构概述

深色模式通过 `core/designsystem` 模块中的多层体系实现：

| 层级 | 位置 | 职责 |
|------|------|------|
| **入口** | [Theme.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/Theme.kt) `NiTheme()` | 根据 `darkTheme` 和 `scheme` 分发配色 |
| **基础色板** | [Color.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/Color.kt) | 品牌蓝的 Light/Dark `ColorScheme` |
| **多方案** | [NiColorSchemes.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/NiColorSchemes.kt) | 12 套配色方案的 `DarkBlueprint`、`AmbientPalette`、装饰色 |
| **扩展色** | [NiExtraColors.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/NiExtraColors.kt) | 品牌色阶、三级 surface、存储类型色、success 色 |
| **持久化** | [ThemeSettings.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/datastore/src/main/java/com/nichx/niplayer/datastore/ThemeSettings.kt) | MMKV 存储 `Mode.LIGHT/DARK/SYSTEM` + `NiScheme` |

### 1.2 深色色板核心特征

#### 1.2.1 背景策略：真黑背景

与大部分应用的深色模式不同，NIplayer 深色背景使用纯黑色 `#000000`（而非 `#121212` 之类深灰）：

```kotlin
// Color.kt
private val DarkBackground = Color(0xFF000000)   // background
private val DarkSurface = Color(0xFF0D0D0D)      // surface（略亮）
```

**优势：**

- **OLED 省电**：纯黑像素在 OLED 屏幕上完全不发光，视频类应用长时间使用时效果明显
- **无边界感**：视频播放时视频画面为全黑 letterbox，背景与画面无缝融合
- **对比度最大化**：白色文字（`#E6E1E5`）在纯黑背景上对比度高达 13.7:1，远超 WCAG AAA 标准

**风险：**

- **容易显得"廉价"**：纯黑背景若缺乏视觉层次，会让界面显得扁平、缺乏质感
- **滚动拖影**：OLED 纯黑到亮色的响应时间较慢，快速滚动时可能出现拖影

#### 1.2.2 表面层级：三级 surface

`NiExtraColors` 定义了三级表面高度：

```kotlin
// DarkExtra
surfaceLevel1 = Color(0xFF0D0D0D)   // 最底层（= surface）
surfaceLevel2 = Color(0xFF1A1A1A)   // 中层卡片、面板
surfaceLevel3 = Color(0xFF222222)   // 最顶层（弹出菜单、Dialog）
```

**分析：**

- 三级之间亮度差约为 8-9%，区别偏小。Material Design 3 推荐的高度映射亮度差约 15-20%
- 随着层级增多，应当有更明显的亮度梯度，否则用户难以感知层级关系

#### 1.2.3 描边色：outline 体系

```kotlin
outline = Color(0xFF94949E)         // 主要描边
outlineVariant = Color(0xFF5A5A64)  // 次要描边
// NiExtraColors 扩展
outlineStrong = Color(0xFF5C5C66)   // 强调描边
outlineSoft = Color(0xFF2A2A2A)     // 极弱描边
```

**分析：**

- `outline`（`#94949E`）在深色表面上的对比度约 3.2:1，达到 WCAG AA 组件边界要求
- `outlineSoft`（`#2A2A2A`）在 `surfaceLevel2`（`#1A1A1A`）上几乎不可见（对比度仅 1.2:1），仅适合作为极弱的分隔提示
- 四个描边层级思路合理，但 `outlineSoft` 的实际可见度需要按使用场景评估

#### 1.2.4 12 套配色方案的深色适配

所有 12 套配色方案在深色模式下都提供了独立的 `DarkBlueprint`，遵循统一的色彩策略：

- **主色（primary）**：降低饱和度并提高明度，确保在深色背景上可读。例如 BLUE 的 primary 从浅色的 `#2095F4` 变为深色的 `#9DCAFF`
- **容器色（primaryContainer）**：比主色更深（约 30-40% 亮度），作为次级元素的背景
- **背景/surface**：全部使用纯黑或近黑底色，保持一致性
- **每个方案的 `AmbientPalette`**：包含三层表面色、两种描边色、两个缩略图渐变端点，确保每个方案在深色下都有独立的氛围

#### 1.2.5 扩展色：品牌色阶与功能色

```kotlin
val brandScale: List<Color>  // generateTonalScale() 生成 10 级色阶
val success: Color = Color(0xFF7FE08A)    // 深色 success
val onSuccess: Color = Color(0xFF0B3000)  // success 上的内容色
```

**分析：**

- `generateTonalScale()` 使用 HSL 模型从主色自动生成 10 级色阶，覆盖 2%-95% 亮度范围
- 深色模式 success 使用亮绿色（`#7FE08A`），与深色背景形成良好对比
- 存储类型色在深色下整体提亮一级，保持与浅色一致的语义编码

### 1.3 多配色方案体系

#### 1.3.1 方案分类

```
冷色系: 蓝色 · 靛蓝 · 青色 · 石板
暖色系: 紫色 · 玫瑰 · 珊瑚 · 粉红
自然色系: 青绿 · 翠绿 · 森林 · 焦糖
```

#### 1.3.2 装饰色（accent）机制

每套方案有独立的 `accent`/`accentLight` 装饰色对，用于标签、徽章等装饰性元素：

```kotlin
// 示例：BLUE
accent = Color(0xFF54B0F7)     // 亮蓝
accentLight = Color(0xFFC5E2FF) // 极亮蓝
```

装饰色从主色衍生但不等同于主色，形成丰富的色彩层次。

#### 1.3.3 配色方案在深色模式下的表现评估

| 方案 | 深色 primary | 深色 surface | 氛围感受 | 评估 |
|------|-------------|-------------|----------|------|
| BLUE | `#9DCAFF` | `#0D0E12` | 专业、冷静 | 优秀（视频播放器首选） |
| INDIGO | `#BFC6FF` | `#0E0E16` | 稳重、可靠 | 优秀 |
| CYAN | `#4DD0E1` | `#0C1315` | 清新、活力 | 良好（点缀色偏亮） |
| SLATE | `#B0B8C4` | `#0E0F10` | 沉稳、极简 | 优秀（可读性佳） |
| PURPLE | `#EABEFF` | `#0F0D12` | 创意、个性 | 良好 |
| ROSE | `#FFB1C8` | `#180A10` | 温暖、柔和 | 良好 |
| CORAL | `#FFB09C` | `#18120F` | 热情、活力 | 良好 |
| PINK | `#FFB0D8` | `#181216` | 甜美、女性化 | 良好 |
| TEAL | `#80CBC4` | `#0C1211` | 自然、平衡 | 优秀 |
| GREEN | `#A1DAA4` | `#0B150C` | 健康、自然 | 优秀 |
| FOREST | `#34D399` | `#0C1512` | 深邃、沉稳 | 良好 |
| CARAMEL | `#E8C09E` | `#181410` | 温暖、舒适 | 良好 |

### 1.4 存在风险与改进建议

#### 1.4.1 纯黑背景的质感提升

**问题**：12 套方案中有 10 套的深色 background 使用纯黑（`#000000`），surface 为近黑，虽然 OLED 省电但视觉层次受限。

**建议**：
- 对**非播放场景**（首页、设置、文件浏览等），考虑将 background 调整为极深灰色（如 `#0A0A0A`），保留"近黑"的视觉感受但略增层次
- 对**播放场景**保持纯黑，以消除 letterbox 界缝

#### 1.4.2 表面层级区分度

**问题**：当前 surface 三级（`#0D0D0D` → `#1A1A1A` → `#222222`）亮度差偏小。

**建议**：调整为更明显的梯度：

```kotlin
surfaceLevel1 = Color(0xFF0D0D0D)   // 底层表面（不变）
surfaceLevel2 = Color(0xFF1E1E1E)   // 中层（原 1A → 1E）
surfaceLevel3 = Color(0xFF2D2D2D)   // 顶层（原 22 → 2D）
```

#### 1.4.3 方案差异化不足

**问题**：12 套方案在深色模式下，background/surface 差异过小，导致切换方案时"氛围变化"不明显。

**建议**：
- 适当加大各方案深色 surface 的色相偏向（而非仅亮度差异）
- 例如 CARAMEL 的 surface 偏一点暖棕，CYAN 偏一点冷蓝绿

#### 1.4.4 `NiExtraColors.DarkExtra` 未方案化

**问题**：`NiExtraColors` 中的 `DarkExtra` 是硬编码的 BLUE 方案值，与 `NiSchemes.buildDarkExtra()` 返回的方案化值不一致。

**建议**：这一设计实际上已通过 `NiSchemes.buildExtra()` 解决，但需确认所有消费侧使用的是 `NiExtraColors.current`（通过 `CompositionLocal` 注入），而非直接引用 `NiExtraColors.DarkExtra`。

#### 1.4.5 无障碍对比度审核

**问题**：部分配色方案在某些组合下可能不符合 WCAG AA 标准。

**建议**：建立自动化对比度测试方案，对 12 个方案的所有 `onXxx`/`Xxx` 组合进行验证，确保：
- normal text ≥ 4.5:1
- large text ≥ 3:1
- 组件边界（outline）≥ 3:1

---

## 2. 横屏布局分析

### 2.1 当前实现概述

#### 2.1.1 播放器横屏：独占式锁定

播放器使用 `Activity.setRequestedOrientation()` 强制锁定横屏：

```kotlin
// PlayerScreen.kt
DisposableEffect(Unit) {
    val original = activity?.requestedOrientation
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    onDispose {
        activity?.requestedOrientation = original
    }
}
```

- 进入播放时锁定为**传感器横屏**
- 用户可按 `F` 键或屏幕旋转按钮在竖屏/横屏之间切换
- 退出播放时恢复原始方向

#### 2.1.2 非播放页面：无横屏适配

其他所有页面（首页、文件浏览、设置等）**没有任何横屏布局适配**：

- 界面保持竖屏的单列 layout
- 没有判断 `Configuration.ORIENTATION_LANDSCAPE` 的逻辑
- 没有 `WindowSizeClass` 或 `BoxWithConstraints` 用于响应式调整
- 内容在横屏下会拉伸到全宽，但布局结构不变，导致视觉上"信息稀疏"

### 2.2 播放器横屏方案

#### 2.2.1 视频画面自适应

```kotlin
BoxWithConstraints(
    modifier = Modifier.fillMaxSize().background(Color.Black).clipToBounds()
) {
    val screenAspect = maxWidth.value / maxHeight.value
    // 根据 ScaleMode 计算视频表面尺寸
    when (scaleMode) {
        NxVideoScaleMode.Crop -> { /* 裁剪适配 */ }
        NxVideoScaleMode.Fit -> { /* 完整显示 */ }
        NxVideoScaleMode.Stretch -> { /* 拉伸填满 */ }
        NxVideoScaleMode.Ratio16_9 -> { /* 16:9 固定比例 */ }
    }
}
```

**分析**：视频画面本身的自适应做得较好，`BoxWithConstraints` 获取可用尺寸后，根据四种 ScaleMode 分别计算视频表面的宽高和位置。

#### 2.2.2 控制栏 Overlay

控制栏使用 `Box` + `Alignment` 绝对定位，布局结构如下：

```
┌─────────────────────────────────────┐
│  Top Gradient (120dp)                │
│  ┌─ Top Bar ──────────────────────┐  │
│  │ ← | 标题 | A-B | 网速 | 时钟 | ⋮ │  │
│  └──────────────────────────────────┘  │
│  ┌─ AB-Loop / Lock ─────────────────┐ │
│  │   [A] [B] [Loop]    [🔒]        │ │
│  └──────────────────────────────────┘ │
│                                       │
│           视频画面区域                   │
│                                       │
│  Bottom Gradient (160dp)              │
│  ┌─ Bottom Controls ───────────────┐  │
│  │  ████████░░░░░░░░░░░░ 进度条     │  │
│  │  01:23 / -02:10                  │  │
│  │  ⏪ ⏮ ⏸ ⏭ ⏩  🔈 🔉 🔊         │  │
│  └──────────────────────────────────┘  │
└─────────────────────────────────────┘
```

**分析**：
- 控制栏体积舒适（Top 120dp + Bottom 160dp 渐变遮罩）
- 顶部信息密度合理，关键控件（A-B、设置、锁屏）都在触手可及的位置
- 底部控制栏功能完整，播放/进度/倍速/音轨一应俱全

#### 2.2.3 存在的问题

**1. 竖屏播放模式未被充分利用**

用户通过 `F` 切换竖屏后，控制栏布局不做任何调整。竖屏时视频画面被严重压缩，而控制栏占用大量纵向空间。

**2. 小屏横屏控件拥挤**

在较小屏幕（< 6 英寸）横屏时，底部控制栏的多行布局可能让控件间距不足。

**3. 缺乏折叠屏适配**

折叠屏在展开状态下的横屏体验（如内屏 7-8 英寸）与控制栏布局未做优化。

### 2.3 非播放场景的横屏问题

非播放页面在横屏下存在以下问题：

| 页面 | 横屏问题 | 影响程度 |
|------|----------|---------|
| [HomeTabScreen.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/home/HomeTabScreen.kt) | LazyRow 横向滑动与横屏宽度冗余 | 中 |
| [LibraryScreen.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/LibraryScreen.kt) | 列表单列全宽，行高不压缩 | 高 |
| [StorageFileScreen.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/StorageFileScreen.kt) | Grid 固定 2 列，列表行全宽 | 高 |
| [QuickAccessScreen.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/quickaccess/QuickAccessScreen.kt) | Grid 固定 3 列 | 中 |
| [ThemeScreen.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/settings/ThemeScreen.kt) | 设置项全宽拉伸 | 低 |

### 2.4 改进建议

#### 2.4.1 短期：限制非播放页横屏

对于视频播放器应用，非播放页面在横屏下的使用场景有限。短期内可考虑：

- **方案 A**：将 `MainActivity` 固定为竖屏，仅播放器页面可以横屏
- **方案 B**：在 `AndroidManifest.xml` 中为 `MainActivity` 设置 `screenOrientation="portrait"`

**推荐采用方案 A**，通过 Activity 级配置实现：

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".MainActivity"
    android:screenOrientation="userPortrait"
    android:configChanges="orientation|screenSize" />
```

播放器页面通过 `setRequestedOrientation()` 动态切换。

#### 2.4.2 中期：非播放页基础横屏适配

若保留非播放页横屏能力，实施基础适配：

- 引入 `WindowSizeClass` 检测中等宽度（`600dp`）时切换布局
- 横屏时 `GridCells.Adaptive(minSize = 160.dp)` 替代 `GridCells.Fixed(2)`
- 横屏时 `screenOuter` 切换为 `screenOuterWide（24.dp）`
- 列表模式下行高使用 `heightIn(max = 64.dp)` 限制

#### 2.4.3 长期：播放器交互重构

- **竖屏模式**：视频画面采用"顶部视频 + 底部信息/评论/章节"的上下结构
- **Mini 播放器**：退出全屏后保持悬浮小窗播放，支持拖拽位置
- **分屏支持**：Split-screen 模式下自动收缩控制栏

---

## 3. 大屏与平板布局分析

### 3.1 当前状态

**NIplayer 目前完全没有针对大屏/平板的布局优化。**

核心证据：

| 检测项 | 状态 |
|--------|------|
| `WindowSizeClass` 使用 | ❌ 无 |
| `BoxWithConstraints` 响应式布局 | ⚠️ 仅在播放器视频尺寸计算中使用 |
| `GridCells.Adaptive` | ❌ 全部使用 `Fixed` |
| `screenOuterWide` 动态切换 | ❌ 定义但未被消费 |
| 平板专用 layout | ❌ 无 |
| 折叠屏适配 | ❌ 无 |
| 键盘/鼠标输入优化 | ❌ 无 |

### 3.2 核心问题清单

#### 3.2.1 Grid 列数固定

```kotlin
// StorageFileScreen.kt - 始终 2 列
columns = GridCells.Fixed(2)

// QuickAccessScreen.kt - 始终 3 列（未确认准确值，但为 Fixed）
```

在平板（10"+）上，固定 2 列的 Grid 导致单个卡片巨大，信息密度极低。

#### 3.2.2 横向空间浪费

```kotlin
// HomeTabScreen.kt - LazyRow 横向滑动
LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp))
```

LazyRow 在横屏或平板上仍限制在竖屏宽度内，横向大量空间闲置。

#### 3.2.3 间距未响应

所有 `screenOuter（16.dp）` 在平板上应适当增大（`screenOuterWide` 的 `24.dp` 或更大）。

#### 3.2.4 缺少 Master-detail 布局

文件浏览器场景非常适合 Master-detail 布局（左侧目录树/列表，右侧文件详情/预览），但当前未实现。

### 3.3 改进建议

#### 3.3.1 引入 Material3 WindowSizeClass

```kotlin
// build.gradle.kts 依赖
implementation("androidx.compose.material3:material3-adaptive")
```

```kotlin
@Composable
fun NiWindowSizeClass(content: @Composable () -> Unit) {
    val class = currentWindowAdaptiveInfo().windowSizeClass
    CompositionLocalProvider(LocalWindowSizeClass provides class) {
        content()
    }
}
```

宽度断点设计：

| 分类 | 宽度范围 | 设备 |
|------|---------|------|
| `Compact` | < 600dp | 手机竖屏 |
| `Medium` | 600-840dp | 手机横屏、小平板竖屏 |
| `Expanded` | > 840dp | 平板横屏、桌面 |

#### 3.3.2 Grid 响应式列数

将 `GridCells.Fixed` 替换为 `GridCells.Adaptive`：

```kotlin
// StorageFileScreen.kt
val columns = when (windowSizeClass.windowWidthSizeClass) {
    WindowWidthSizeClass.Compact -> GridCells.Fixed(2)
    WindowWidthSizeClass.Medium -> GridCells.Adaptive(minSize = 180.dp)
    WindowWidthSizeClass.Expanded -> GridCells.Adaptive(minSize = 160.dp)
    else -> GridCells.Fixed(2)
}
```

#### 3.3.3 响应式间距

```kotlin
// 根据窗口宽度类选择间距
val NiSpacings.screenOuter: Dp
    @Composable get() = when (LocalWindowSizeClass.current.windowWidthSizeClass) {
        WindowWidthSizeClass.Compact -> 16.dp
        WindowWidthSizeClass.Medium -> 20.dp
        WindowWidthSizeClass.Expanded -> 24.dp
        else -> 16.dp
    }
```

#### 3.3.4 Master-detail 文件浏览器

在 Expanded 宽度时，文件浏览器切换为 Master-detail 布局：

```
┌─────────────────────────────────────────┐
│  ← 返回  │  路径: /video/movies         │
├────────────┬────────────────────────────┤
│  导航侧栏    │  内容区域                   │
│  (280dp)   │                            │
│            │  ┌───┐ ┌───┐ ┌───┐ ┌───┐   │
│  📁 视频    │  │   │ │   │ │   │ │   │   │
│  📁 音乐    │  └───┘ └───┘ └───┘ └───┘   │
│  📁 下载    │  ┌───┐ ┌───┐               │
│  📁 ...     │  │   │ │   │               │
│             │  └───┘ └───┘               │
│             │                            │
│  ⚡️ 快速访问  │                            │
│  ┌───┐ ┌───┐│                            │
│  │   │ │   ││                            │
│  └───┘ └───┘│                            │
├────────────┴────────────────────────────┤
│  状态栏：100 个项目 | 已用 32GB / 128GB  │
└─────────────────────────────────────────┘
```

#### 3.3.5 首页适配方案

```kotlin
@Composable
fun HomeTabScreen() {
    val windowClass = LocalWindowSizeClass.current
    when (windowClass.windowWidthSizeClass) {
        WindowWidthSizeClass.Expanded -> HomeExpandedLayout()
        WindowWidthSizeClass.Medium -> HomeMediumLayout()
        else -> HomeCompactLayout()
    }
}
```

| 窗口宽度类 | 布局策略 |
|-----------|---------|
| **Compact** | 当前单列 LazyColumn（不变） |
| **Medium** | LazyVerticalGrid(2) 替代 LazyRow，提高信息密度 |
| **Expanded** | 三列 Grid + 左侧快捷入口侧栏 |

#### 3.3.6 播放器大屏优化

| 优化项 | 说明 |
|--------|------|
| 画中画 | 利用平板多任务优势，播放时支持 PiP |
| 控制栏放大 | 触摸目标从 44dp 增大到 48-56dp |
| 章节导航 | Expanded 下右侧显示章节列表/播放列表 |
| 键盘快捷键 | 空格暂停、←→ 快进快退、↑↓ 音量（基础已实现部分） |

#### 3.3.7 折叠屏适配

针对折叠屏的关键策略：

| 场景 | 处理方式 |
|------|---------|
| 折叠（手机模式） | 使用 Compact 布局 |
| 半展开（桌面模式） | Mediem 布局，注意屏幕 hinge 区域避让 |
| 完全展开（平板模式） | Expanded 布局，充分利用内屏面积 |

---

## 4. 综合优化路线图

### 4.1 优先级划分

```
P0 ───── 深色模式 BUG / 无障碍问题（影响现有用户体验）
P1 ───── 横屏基础限制 / 播放器体验优化
P2 ───── WindowSizeClass 引入 + 首页/文件页适配
P3 ───── Master-detail 布局 / 折叠屏适配
```

#### 完整优先级矩阵

| 改进项 | 优先级 | 工作量 | 收益 |
|--------|--------|--------|------|
| 非播放页禁止横屏 | P1 | 小 | 中 |
| 深色 surface 层级梯度调整 | P1 | 极小 | 高 |
| WCAG 对比度审计与修复 | P1 | 中 | 高 |
| `screenOuterWide` 落地消费 | P2 | 小 | 中 |
| WindowSizeClass 库引入 | P2 | 小 | 基础 |
| Grid 自适应列数 | P2 | 中 | 高 |
| `NiExtraColors` 方案一致性审查 | P2 | 极小 | 中 |
| 首页响应式 Grid 布局 | P2 | 中 | 高 |
| 播放器竖屏模式重构 | P3 | 大 | 高 |
| Master-detail 文件浏览器 | P3 | 大 | 高 |
| 折叠屏适配 | P3 | 大 | 中 |
| 播放器大屏控制栏优化 | P3 | 中 | 中 |
| 各方案深色差异化增强 | P3 | 中 | 低 |

### 4.2 实施路径

#### 第一阶段：加固现有体验（P1，预估 2-3 天）

1. 非播放页面禁止横屏（`AndroidManifest.xml` + Activity 配置）
2. 调整 surface 层级亮度差，改善深色层次感
3. 运行 WCAG 对比度自动化检查，修复不达标组合
4. 确认 `NiExtraColors` 消费路径正确

#### 第二阶段：响应式基础（P2，预估 5-7 天）

1. 引入 `material3-adaptive` 依赖
2. 创建 `LocalWindowSizeClass` CompositionLocal
3. 在 `NiTheme` 或 `MainActivity` 级别注入
4. StorageFileScreen Grid 改用 `Adaptive`
5. 首页 QuickAccess 改用响应式 Grid
6. `screenOuter` 变为响应式间距

#### 第三阶段：高级布局（P3，预估 10-15 天）

1. 播放器竖屏交互重构
2. 文件浏览器 Master-detail 布局
3. 折叠屏适配（Hinge 避让）
4. 大屏播放器控制栏优化

---

## 附录

### A. 涉及文件清单

| 文件路径 | 修改类型 | 优先级 |
|---------|---------|--------|
| [core/designsystem/.../Color.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/Color.kt) | 颜色微调 | P1 |
| [core/designsystem/.../NiExtraColors.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/NiExtraColors.kt) | surface 值调整 | P1 |
| [core/designsystem/.../NiColorSchemes.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/NiColorSchemes.kt) | 方案差异化增强 | P3 |
| [core/designsystem/.../Spacings.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/Spacings.kt) | 响应式间距 | P2 |
| [core/designsystem/.../Theme.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/Theme.kt) | WindowSizeClass  注入 | P2 |
| [feature/home/.../HomeTabScreen.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/home/HomeTabScreen.kt) | Grid 响应式 | P2 |
| [feature/home/.../StorageFileScreen.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/StorageFileScreen.kt) | Grid 响应式 + Master-detail | P2-P3 |
| [feature/home/.../QuickAccessScreen.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/quickaccess/QuickAccessScreen.kt) | Grid 响应式 | P2 |
| [feature/player/.../PlayerScreen.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerScreen.kt) | 竖屏布局 + 大屏优化 | P3 |
| [app/src/main/.../MainActivity.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/app/src/main/java/com/nichx/niplayer/MainActivity.kt) | 方向限制 | P1 |
| [app/src/main/AndroidManifest.xml](file:///Users/nichx/Documents/GitHub/NIplayer/v2/app/src/main/AndroidManifest.xml) | Activity 配置 | P1 |
| [gradle/libs.versions.toml](file:///Users/nichx/Documents/GitHub/NIplayer/v2/gradle/libs.versions.toml) | WindowSizeClass 依赖 | P2 |

### B. 参考资源

- [Material3 Dark Theme Guidelines](https://m3.material.io/styles/dark-theme)
- [Material3 Adaptive Layout](https://developer.android.com/develop/ui/compose/layouts/adaptive)
- [Material3 Window Size Classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/calculate-window-size-classes)
- [Android Large Screen Guidelines](https://developer.android.com/docs/quality-guidelines/large-screen-app-quality)
- [WCAG Contrast Checker](https://webaim.org/resources/contrastchecker/)
