# NIplayer v2 — Feature Design Document

> **版本**: 2.0.0-alpha.1
> **最后更新**: 2026-08-01
> **状态**: 现有功能分析 + 架构设计参考

---

## 目录

- [1. 产品定位](#1-产品定位)
- [2. 技术栈](#2-技术栈)
- [3. 架构概览](#3-架构概览)
- [4. 功能域设计](#4-功能域设计)
  - [4.1 视频播放器](#41-视频播放器)
  - [4.2 音频播放](#42-音频播放)
  - [4.3 存储与文件浏览](#43-存储与文件浏览)
  - [4.4 下载管理](#44-下载管理)
  - [4.5 字幕系统](#45-字幕系统)
  - [4.6 主页与导航](#46-主页与导航)
  - [4.7 设置中心](#47-设置中心)
- [5. 数据模型](#5-数据模型)
- [6. 模块依赖图](#6-模块依赖图)
- [7. 关键设计决策](#7-关键设计决策)

---

## 1. 产品定位

NIplayer v2 是一款 Android 全能媒体播放器，核心目标是**统一访问本地/SAF/SMB/WebDAV 四类存储并播放视频/音频/图片**。

| 维度 | 说明 |
|------|------|
| 目标用户 | 拥有 NAS / SMB / WebDAV 服务器、需要移动端直接播放远程媒体的用户 |
| 核心价值 | 远程直播 + 专业播放控制 + ASS 特效字幕 + 后台音频 + 安全凭据存储 |
| 竞品参考 | nPlayer / Infuse / MX Player / Kodi |

---

## 2. 技术栈

| 类别 | 选型 | 版本 |
|------|------|------|
| 语言 | Kotlin + Coroutines | 2.4.10 / 1.10.2 |
| UI | Jetpack Compose BOM + Material 3 | BOM 2026.06.00 |
| 导航 | Navigation Compose | 2.9.8 |
| 依赖注入 | Hilt + hilt-navigation-compose | 2.60.1 / 1.4.0 |
| 播放器 | Media3 (ExoPlayer / session / datasource-okhttp) | 1.10.1 |
| 软解扩展 | FFmpeg JNI (CMake + NDK, arm64-v8a / armeabi-v7a) | 自编译 |
| 数据库 | Room | 2.8.4 |
| KV 存储 | MMKV | 1.3.14 |
| 网络 | OkHttp + Retrofit + Moshi | 4.12.0 / 2.11.0 / 1.15.2 |
| 图片 | Coil 3 (compose + video + network-okhttp) | 3.5.0 |
| SMB | jcifs | 3.0.0 |
| 编码探测 | juniversalchardet | 2.4.0 |
| SDK | minSdk 26 / targetSdk 37 / compileSdk 37 |

---

## 3. 架构概览

### 3.1 分层架构

```
:app（入口层）
  ├─ :feature:home        ──┐
  ├─ :feature:player      ──┤  Feature 层（UI + ViewModel）
  │                         │
  ├─ :player:kernel       ──┤  Player 内核层（NxPlayer 抽象 + Media3 实现）
  ├─ :player:ffmpeg       ──┤  FFmpeg JNI 软解扩展
  │                         │
  ├─ :core:designsystem   ──┤
  ├─ :core:navigation     ──┤
  ├─ :core:database       ──┤
  ├─ :core:datastore      ──┤  Core 基础设施层
  ├─ :core:storage        ──┤
  ├─ :core:network        ──┤
  ├─ :core:subtitle       ──┤
  ├─ :core:thumbnail      ──┤
  └─ :core:common         ──┘
```

### 3.2 架构模式：MVVM + Compose + Flow

| 层 | 职责 | 技术 |
|----|------|------|
| **Model** | 数据持久化 + 网络访问 + 存储抽象 | Room (7 表) + MMKV (8 域) + Retrofit + Storage 接口 |
| **ViewModel** | 状态管理 + 业务逻辑 | Hilt `@HiltViewModel` + `StateFlow` / `SharedFlow` + `viewModelScope` |
| **View** | UI 渲染 + 用户交互 | Jetpack Compose + `collectAsStateWithLifecycle` |

### 3.3 模块清单（13 个模块）

| 模块 | 类型 | 职责 |
|------|------|------|
| `:app` | 入口 | Application + MainActivity + NavHost + MusicBar |
| `:feature:home` | Feature | 首页/媒体库/设置 + 历史/快速访问/存储管理/下载/图片查看 |
| `:feature:player` | Feature | 视频/音频播放 UI + ViewModel + AudioPlaybackService |
| `:player:kernel` | 内核 | NxPlayer 接口 + NxMedia3Player 实现 + PlaybackRequestHolder |
| `:player:ffmpeg` | 内核 | FFmpeg JNI 软解 (TrueHD / DTS-HD / E-AC-3 JOC) |
| `:core:database` | 基础 | Room v8, 7 表 + 7 Dao + Migration |
| `:core:datastore` | 基础 | MMKV 设置项 (8 域) |
| `:core:designsystem` | 基础 | Material 3 主题 + 15+ 自定义 Compose 组件 |
| `:core:navigation` | 基础 | 路由常量 + NavHost 宿主 |
| `:core:network` | 基础 | OkHttp/Retrofit 单例 + ASSRT 字幕 API |
| `:core:storage` | 基础 | Storage 接口 (Local/SAF/SMB/WebDAV) + DownloadManager |
| `:core:subtitle` | 基础 | ASS/SSA/SRT 自渲染引擎 + Override tags 动画 |
| `:core:thumbnail` | 基础 | 缩略图生成与缓存 (视频帧/音频封面) |

---

## 4. 功能域设计

### 4.1 视频播放器

**模块**: `:feature:player` + `:player:kernel`
**核心类**: `PlayerViewModel` / `PlayerScreen` / `NxMedia3Player`

#### 4.1.1 播放核心

| 功能 | 描述 | 实现 |
|------|------|------|
| 视频/音频分流 | 按 `isAudio` 路由到视频页或音频页 | `PlayerGuardScreen` 路由守卫 |
| 续播 | 进入时弹"接着上次看"，进度实时入库 | `play_history` 表 |
| 播放控制 | 播放/暂停/快进 10s/快退 10s/进度条定位 | 底部控制栏 + 键盘 |
| 缓冲进度 | 进度条次要色展示已缓冲位置 | Media3 `bufferedPosition` |

#### 4.1.2 倍速控制

| 功能 | 描述 |
|------|------|
| 常规倍速 | 菜单选择，记忆上次倍速索引 |
| 长按临时倍速 | 长按画面切到设定倍速 (1.5x~3.0x)，松手恢复 |
| 长按倍速锁定 | 长按拖到底部松手锁定，持续保持 |
| 倍速 OSD | 长按时显示当前倍速 + "拖到底部锁定"提示 |

#### 4.1.3 A-B 段循环

- 设置 A/B 点（任意顺序），两端设好后自动循环
- 实时显示 A/B 时间戳，一键清除

#### 4.1.4 睡眠定时

- N 分钟倒计时，顶栏显示剩余 mm:ss
- 归零自动暂停，可随时取消

#### 4.1.5 截图

- 截取当前画面 → PNG → `Pictures/NIplayer`
- 兼容 Android 8+ (公共目录) 与 Android 10+ (MediaStore 作用域存储)

#### 4.1.6 智能黑边检测与画面缩放

| 缩放模式 | 说明 |
|----------|------|
| Fit (适应) | 自动检测有效画面区域，用真实内容宽高比替代容器比例 |
| Crop (裁剪) | 裁剪填充 |
| Stretch (拉伸) | 强制拉伸 |

- 检测算法移植自 FFmpeg `cropdetect`
- 结果按视频唯一键缓存，跨横竖屏稳定
- Crop/Stretch → Fit 切换时自动重新检测
- Fit → Crop/Stretch 不重新检测

#### 4.1.7 手势控制

| 手势 | 功能 |
|------|------|
| 左半屏上下滑 | 调节亮度 (OSD 实时显示，记忆亮度) |
| 右半屏上下滑 | 调节音量 (OSD 实时显示) |
| 横向滑动 | 精确定位进度 (OSD 显示目标时间) |
| 单击 | 显示/隐藏控制栏 |
| 双击左/右半屏 | 快退/快进 10s |
| 长按 | 临时倍速 |

#### 4.1.8 锁屏防误触

- 画面锁定后隐藏所有控制栏，禁用手势

#### 4.1.9 横竖屏与方向

- 进入视频强制 `SENSOR_LANDSCAPE`，退出恢复
- 顶栏旋转按钮手动切换
- 键盘 F 键

#### 4.1.10 画中画 (PiP)

- 顶栏 PiP 按钮进入小窗，后台继续播放

#### 4.1.11 键盘/遥控器快捷键

| 按键 | 功能 |
|------|------|
| Space / K / Enter | 播放/暂停 |
| → / L | 快进 |
| ← / J | 快退 |
| ↑ / ↓ | 音量增减 |
| M | 静音切换 |
| F | 切换横屏 |
| 0~9 | 跳转到时长的 0%~90% |
| Esc | 返回 |

#### 4.1.12 音轨/字幕轨切换

- 音轨菜单：列出所有音轨，可选自动或指定
- 字幕菜单：内嵌 + 外挂字幕轨，可选自动/指定/关闭
- 字幕延迟调整（毫秒级，正延后/负提前）

#### 4.1.13 播放列表连播

- 自动构建同目录列表，底部显示"1/12"
- 上一首/下一首 + 播放列表抽屉
- 三种模式：顺序 / 随机 / 单曲循环
- 播放完毕自动下一首

#### 4.1.14 其他

| 功能 | 描述 |
|------|------|
| 媒体信息 | 编码、分辨率、码率、帧率、HDR 格式 |
| HDR 检测 | 首帧渲染后 OSD 提示 |
| 网络速度 | 顶栏实时下载速度 (B/s) |
| 下载当前文件 | 一键加入下载队列 |
| 后台音频桥接 | 退出视频页时音频桥接到后台服务 |

---

### 4.2 音频播放

**模块**: `:feature:player`
**核心类**: `AudioPlaybackManager` (@Singleton) / `AudioPlaybackService` / `AudioPlayerScreen`

#### 4.2.1 UI 特性

| 功能 | 描述 |
|------|------|
| 黑胶转盘 | 唱片旋转动画 (播放时旋转/暂停停止) + 唱针动画 |
| 专辑封面 | 唱片中央显示，无封面用占位图 |
| 模糊背景 | 模糊封面背景层 |
| 强制竖屏 | 音频页固定竖屏 |

#### 4.2.2 歌词

| 功能 | 描述 |
|------|------|
| LRC 解析 | 逐行高亮 + 自动滚动 |
| 点击跳转 | 点击任一行跳转到对应时间 |
| 显示/隐藏 | 切换歌词显示 |
| 本地优先 | 本地 LRC 优先，找不到时在线获取 |

#### 4.2.3 在线歌词/封面

- 可配置音乐元数据 API (URL + Auth 头)
- 按标题/艺术家/专辑/路径查询
- 自动提取音频内嵌封面

#### 4.2.4 通知栏与后台播放

| 功能 | 描述 |
|------|------|
| 前台服务 | `foregroundServiceType="mediaPlayback"` |
| MediaSession | 通知栏展示标题/艺术家/封面/控制按钮 |
| 锁屏控制 | 蓝牙/耳机按键控制 |
| 拔耳机暂停 | `handleAudioBecomingNoisy` |
| MusicBar | 非播放页底部常驻音乐浮条 |

#### 4.2.5 播放模式

- 顺序播放 / 随机播放 / 单曲循环

#### 4.2.6 错误提示

| HTTP 状态 | 提示 |
|-----------|------|
| 401 | 账号密码错误 |
| 403 | 无权限 |
| 404 | 文件不存在 |
| 5xx | 服务器错误 |
| 网络异常 | 网络异常 |

---

### 4.3 存储与文件浏览

**模块**: `:core:storage` + `:feature:home` (library)
**核心类**: `Storage` (接口) / `StorageFactory` / `SmbStorage` / `WebDavStorage`

#### 4.3.1 支持的存储协议

| 协议 | 实现类 | 播放方式 | 特性 |
|------|--------|----------|------|
| 本机存储 | `VideoStorage` | URL | MediaStore + 扩展目录合并 |
| SAF 外部存储 | `DocumentFileStorage` | URI | DocumentFile API |
| SMB | `SmbStorage` | DataSource | 并行输入流 + RandomAccess MediaDataSource |
| WebDAV | `WebDavStorage` | URL / DataSource | HTTP Range 断点续传 + 自签证书信任 |

#### 4.3.2 媒体库管理

| 功能 | 描述 |
|------|------|
| 存储源列表 | 按类型筛选 (全部/本机/SMB/WebDAV) + 搜索 |
| 添加/编辑/删除 | 类型/地址/账号/密码 + 连接测试 |
| 密码加密 | Android Keystore AES-256-GCM (`enc:v1:` 前缀) |

#### 4.3.3 文件浏览

| 功能 | 描述 |
|------|------|
| 目录导航 | 逐级进入子目录 + 返回上级 (目录栈) |
| 排序 | 名称/修改时间/文件大小/类型，升/降序 (目录始终在前) |
| 视图模式 | 列表/网格切换 |
| 媒体过滤 | 仅显示视频/音频/图片 |
| 隐藏文件 | 显示/隐藏 `.` 开头文件 |
| 文件搜索 | 递归子目录，最大 3 层 |
| 缩略图 | 视频帧/音频封面/图片缩略图 |
| 时长标识 | 视频 < 15s 标识 |
| 连接状态 | 远程存储心跳检测，顶栏显示 |
| 错误提示 | 中文友好 (无法连接/超时/401/403/404/5xx) |

#### 4.3.4 本地视频扫描

- 合并系统 MediaStore + 用户扩展目录
- 增量同步去重 (同文件 MediaStore 优先)
- 视频扩展名可配置 (默认 20 种)
- 扫描目录管理：添加扩展目录 + 屏蔽目录

#### 4.3.5 Storage 接口操作

```
listFiles / openInputStream(offset) / readFileHead / createPlayUrl
deleteFile / createDirectory / saveFile / exists / ping / close
```

---

### 4.4 下载管理

**模块**: `:core:storage` (download) + `:feature:home` (settings)
**核心类**: `DownloadManager` / `DownloadManagerViewModel`

#### 4.4.1 下载能力

| 功能 | 描述 |
|------|------|
| 多任务并发 | 调度循环，最多 N 个并发 |
| 三种目标 | 缓存目录 / 直 path (`file://`) / SAF (`content://`) |
| 断点续传 | HTTP Range (WebDAV) 从已下载字节继续 |
| 任务去重 | 同 uniqueKey + storageId 已有活跃任务则忽略 |

#### 4.4.2 任务状态

```
等待 → 下载中 → 已完成
              ↘ 已暂停 (保留文件)
              ↘ 失败 (可重试)
              ↘ 已取消 (删除文件)
```

#### 4.4.3 实时进度

- 每任务进度百分比
- 实时下载速度 (3s 滑动窗口, 300ms 采样)
- 剩余时间 ETA
- 按状态分组展示

#### 4.4.4 UI 入口

- 文件浏览页顶栏角标 (活跃任务数 > 0)
- 设置 → 存储 → 下载管理

---

### 4.5 字幕系统

**模块**: `:core:subtitle` + `:feature:player`
**核心类**: `SubtitleEngine` / `AssOverrideParser` / `SubtitleOverlay`

#### 4.5.1 格式支持

| 格式 | 解析器 | 渲染方式 | 特性 |
|------|--------|----------|------|
| ASS/SSA | `FormatASS.java` | 自研 SubtitleEngine | override tags 动画 (\fad/\move/\t/\frz/\pos) |
| SRT | `FormatSRT.java` | 自研 SubtitleEngine | 基本字幕 |
| TTML | `FormatTTML.java` | 自研 SubtitleEngine | 基本字幕 |
| 内嵌字幕 | Media3 | Media3 TextRenderer | 走系统渲染 |

#### 4.5.2 在线字幕搜索

- 集成 ASSRT API
- 默认用视频标题自动搜索，可修改关键词
- 点击结果 → 下载 → 自动应用
- 未配置 Token 时弹出设置对话框

#### 4.5.3 自动加载

- 播放时自动加载同文件夹同名字幕
- 可配置优先级 (如 `chs,cht`)
- 可在设置中关闭

#### 4.5.4 字幕偏移

- 毫秒级正负偏移 (正延后/负提前)，实时生效

#### 4.5.5 字幕样式配置 (7 项)

| 配置项 | 选项 |
|--------|------|
| 字体 | 默认无衬线 / 衬线 / 等宽 / 紧凑无衬线 |
| 字号 | 小 / 中 / 大 / 特大 |
| 文字颜色 | 白色 / 黄色 / 青色 / 浅灰 / 黑色 |
| 描边宽度 | 无 / 细 / 中 / 粗 |
| 描边颜色 | 黑色 / 白色 / 深灰 / 红色 |
| 底部边距 | 近 / 中 / 远 |
| 应用内嵌样式 | 开关：是否使用字幕文件自带颜色和样式 |

#### 4.5.6 渲染特性

- 8 方向描边避免对角锯齿
- 阴影跟随描边宽度
- 绝对定位 (ASS `\pos`)
- 渲染缓存 (无变化时不重复计算)
- 设置页修改后返回播放器立即生效 (ON_RESUME)

---

### 4.6 主页与导航

**模块**: `:feature:home` + `:core:navigation`
**核心类**: `HomeScreen` / `HomeTabViewModel` / `NiNavHost`

#### 4.6.1 底部三 Tab

| Tab | 内容 |
|-----|------|
| 首页 (HOME) | 最近播放 + 快速访问 + 存储源可达性 |
| 媒体库 (LIBRARY) | 存储源列表 + 文件浏览 |
| 设置 (SETTINGS) | 全局设置中心 |

#### 4.6.2 首页内容

| 功能 | 描述 |
|------|------|
| 最近播放 | 按视频/音频分组，缩略图+标题+存储源+播放时间，点击续播 |
| 快速访问 | 书签式入口，长按拖拽排序，编辑/删除 |
| 存储源检测 | 远程存储自动验证连接，不可达降低透明度 |
| 缩略图生成 | 本地缓存优先，无缓存时远程生成 |

#### 4.6.3 播放历史页

- 按日期分组
- 筛选：全部/视频/音频
- 点击续播，长按删除，清空全部

#### 4.6.4 快速访问页

- 网格布局，长按拖拽排序 (顺序持久化)
- 编辑模式：删除单项
- 文件夹/视频/音频/图片不同图标

#### 4.6.5 图片查看器

| 功能 | 描述 |
|------|------|
| 全屏浏览 | 双指缩放 (1x~5x) + 双击切换 (1x/3x) |
| 拖拽平移 | 缩放 > 1x 时单指拖拽 |
| 多图滑动 | HorizontalPager 左右翻页 |
| 多协议加载 | Local/DocumentFile/WebDAV 直接 URL，SMB 转 ByteArray |

#### 4.6.6 路由结构

```
Routes/
├── Home/          # 首页/历史/快速访问
├── Local/         # 本地存储
├── User/          # 用户存储 (SMB/WebDAV/SAF)
├── Player/        # 播放器 (视频/音频/路由守卫)
├── Stream/        # 流播放
└── ImageViewer/   # 图片查看器
```

---

### 4.7 设置中心

**模块**: `:core:datastore` + `:feature:home` (settings)
**核心类**: 各 `*Settings.kt` + `*SettingsScreen.kt`

#### 4.7.1 设置分组

```
设置
├── 播放
│   ├── 播放器设置      # 长按倍速值/亮度恢复/黑边检测/倍速记忆
│   ├── 音乐元数据      # LrcApi URL + Auth
│   ├── 扫描目录管理    # 扩展目录 + 屏蔽目录 + 视频扩展名
│   └── 缓存管理        # 扫描占用/按项清理/清理全部
├── 存储
│   └── 下载管理        # 下载目录 + 任务列表
├── 外观
│   └── 主题            # 浅色/暗色/跟随系统 + 配色方案
└── 关于
    ├── 关于 NIplayer   # 版本信息
    └── 开源许可证      # WebView 展示
```

#### 4.7.2 设置项详情

| 域 | 配置项 |
|----|--------|
| PlayerSettings | 长按倍速值 (1.5~3.0) / 亮度恢复 / 黑边检测开关 / 倍速索引记忆 / 黑边缓存 |
| SubtitleSettings | ASSRT Token / 自动加载同名字幕 / 字幕优先级 / 字号 / 字体族 / 颜色 / 描边 / 边距 / 内嵌样式 |
| ThemeSettings | 主题模式 (浅色/暗色/跟随系统) / 配色方案 |
| DownloadSettings | 下载目录 URI + 名称 |
| FileBrowserSettings | 排序字段 / 升降序 / 仅媒体 / 隐藏文件 / 网格列表 |
| LrcApiSettings | API URL / Auth 头 |
| ThumbnailSettings | 总开关 / 视频/图片/音频分开关 / 保存到服务器 / 取帧位置 / 存储源覆盖 |
| VideoExtensionSettings | 扩展名列表编辑 / 重置默认 |

---

## 5. 数据模型

### 5.1 Room 数据库 (v8, 7 表)

| 表 | 用途 | 关键字段 |
|----|------|----------|
| `media_library` | 存储源配置 | id, display_name, url, media_type, account, password(加密), port |
| `video` | 本地视频缓存 | — |
| `play_history` | 播放历史 | video_name, url, video_position, video_duration, subtitle_path, unique_key, storage_id |
| `extend_folder` | 扩展扫描目录 | folder_path (PK), child_count |
| `download_task` | 下载任务 | file_name, file_path, total_bytes, downloaded_bytes, state, target_storage_url |
| `quick_access` | 快速访问 (收藏) | name, storage_path, is_directory, library_id, sort_index |
| `sync_delete_log` | 删除同步日志 | — |

### 5.2 MMKV 设置项 (8 域)

`PlayerSettings` / `SubtitleSettings` / `ThemeSettings` / `DownloadSettings` / `FileBrowserSettings` / `LrcApiSettings` / `ThumbnailSettings` / `VideoExtensionSettings`

### 5.3 跨模块通信

| 持有者 | 类型 | 职责 |
|--------|------|------|
| `PlaybackRequestHolder` | @Singleton | Home 构造播放请求 → Player 消费 (取出即清空) |
| `PlaylistHolder` | @Singleton | 异步构建播放列表 → Player 读取 |

---

## 6. 模块依赖图

```
                         ┌─────────────┐
                         │    :app     │
                         └──────┬──────┘
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                  ▼
      ┌──────────────┐ ┌──────────────┐  ┌────────────────┐
      │:feature:home │ │:feature:player│  │  (player:kernel)│
      └──────┬───────┘ └──────┬───────┘  └───────┬────────┘
             │                │                  │
    ┌────────┴────────────────┴──────────────────┘
    │    Shared Core Infrastructure
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ :core:storage  :core:subtitle  :core:database       │
│ :core:network  :core:thumbnail :core:datastore      │
│ :core:designsystem  :core:navigation  :core:common  │
└─────────────────────────────────────────────────────┘
         │                                    │
         ▼                                    ▼
  ┌──────────────┐                   ┌──────────────┐
  │:player:kernel│                   │ :player:ffmpeg│
  └──────────────┘                   └──────────────┘
```

**依赖规则**：
- Feature 层可依赖 Core 层 + Player 内核层
- Core 层之间可互相依赖 (但避免循环)
- Player 内核层依赖 Core 层
- 所有模块通过 Hilt `@Module` 注入，`:app` 聚合

---

## 7. 关键设计决策

### 7.1 单一播放内核

废弃 v1 的 exo/ijk/vlc 三套实现，统一为 `NxPlayer` 接口 + `NxMedia3Player` 唯一实现。
- 不继承 View，通过 `attachSurface` 解耦 UI
- Hilt `@Binds` 注入，一屏一实例 (非 @Singleton)

### 7.2 存储协议抽象

`Storage` 接口统一四套协议，`StorageFactory` 按 `MediaType` 分发。
- `createPlayUrl` 返回可空：Local/WebDAV 返回 URL，SMB 返回 DataSource
- SMB 通过 `SmbMediaDataSource` 实现 RandomAccess，支持 media3 seek

### 7.3 字幕自渲染

`SubtitleEngine` 替代 Media3 TextRenderer 处理外挂 ASS/SSA/SRT。
- 支持 override tags 动画 (\fad/\move/\t/\frz/\pos)
- 渲染缓存避免 16ms 重复计算
- 内嵌字幕仍走 Media3

### 7.4 FFmpeg 软解

`:player:ffmpeg` 通过 CMake 编译 FFmpeg JNI 静态库。
- 支持 TrueHD / DTS-HD / E-AC-3 JOC 等高端音频格式
- `FfmpegRenderersFactory` 注入 Media3 Renderers

### 7.5 后台音频与视频分离

| 维度 | 视频 | 音频 |
|------|------|------|
| Player | `NxMedia3Player` (一屏一实例) | `AudioPlaybackManager` (@Singleton 单 ExoPlayer) |
| 生命周期 | ViewModel scope | Application scope |
| 服务 | 无 | `AudioPlaybackService` (Foreground + MediaSession) |
| 通信 | `PlaybackRequestHolder` | `AudioPlaybackManager` 回调 |

### 7.6 安全设计

- 存储源密码：Android Keystore AES-256-GCM 加密 (`enc:v1:` 前缀)
- 数据库导出后无法解密密码

### 7.7 结构化并发

- 全量 Kotlin Flow (无 LiveData)
- `StateFlow` (状态) + `SharedFlow` (事件) + `WhileSubscribed(5000)`
- `CancellationException` 重抛 + `Mutex` 串行化切歌 + `NonCancellable` 保护进度写入

### 7.8 路由守卫

`PlayerGuardScreen` + `PlayerGuardViewModel` 替代 v1 的 `PlayerInterceptorActivity`。
- 按 `isAudio` 分流到视频播放页或音频播放页
- 跨模块通过 `PlaybackRequestHolder` 传递请求

- [8. 功能增强提案](#8-功能增强提案)
- [9. 工程优化提案](#9-工程优化提案)
- [10. 已知限制与路线图](#10-已知限制与路线图)

---

## 8. 功能增强提案

> 基于现有架构与竞品分析，按优先级分三档提案。P0 = 高价值且改动集中，P1 = 明显提升体验，P2 = 锦上添花。

### 8.1 播放能力增强

| ID | 优先级 | 功能 | 描述 | 技术方案 |
|----|--------|------|------|----------|
| F-01 | P0 | 倍速音调修正 | 倍速播放时保持音调不变，避免变速后声音变尖/变沉 | Media3 `PlaybackParameters` 已支持，仅需在 `setPlaybackSpeed` 时启用 `SkipSilence`/`Pitch` 修正；音频走 ExoPlayer `setPitch(1.0f)` |
| F-02 | P0 | 音频均衡器 (EQ) | 5/10 频段均衡器，预设 + 自定义，提升音频听感 | Media3 `Equalizer` band（ExoPlayer 已内置 `android.media.audiofx.Equalizer`），在 `AudioPlaybackManager` 创建时 attach |
| F-03 | P0 | 音频可视化 | 播放时显示频谱波形/柱状图动画 | `Visualizer` class (API 21+)，取 FFT 数据驱动 Compose Canvas 绘制，替代/增强黑胶转盘的静态封面 |
| F-04 | P1 | 进度条缩略图预览 | 拖动进度条时显示对应帧的缩略图预览（类似 YouTube/B站） | 复用 `ThumbnailManager` 的取帧能力，预生成 10~20 帧缩略图缓存到 `.thumb/seek/`，进度条 hover 时异步加载 |
| F-05 | P1 | 视频滤镜/色调调节 | 亮度/对比度/饱和度/色温实时调节，支持预设 | Media3 `Effect` API (`BrightnessContrast`/`Saturation`/`RgbMatrix`)，挂到 `ExoPlayer.setVideoEffects()` |
| F-06 | P1 | HDR→SDR 色调映射 | 非 HDR 屏幕播放 HDR 视频时自动色调映射，避免过曝/过暗 | Media3 `HDR` Mode 自动 fallback；或在 `NxMedia3Player` 检测到 HDR + 非 HDR 屏幕时切换 `SurfaceView` 纹理模式 |
| F-07 | P2 | 3D/VR 视频支持 | SBS/Tab 3D 视频渲染，Cardboard/VR 模式 | Media3 `SphericalGLSurfaceView`，检测视频宽高比 (2:1) 自动提示切换 3D 模式 |
| F-08 | P2 | 音频淡入淡出 | 切歌/暂停/恢复时音量渐变，避免爆音 | `AudioPlaybackManager` 在 `play()/pause()` 前用 `Handler` 渐变 `player.volume` 0→1 / 1→0 (200ms) |

### 8.2 存储与协议扩展

| ID | 优先级 | 功能 | 描述 | 技术方案 |
|----|--------|------|------|----------|
| F-09 | P0 | FTP/SFTP 支持 | 扩展 `Storage` 接口支持 FTP/SFTP 协议 | 新增 `FtpStorage` (Apache Commons Net) / `SftpStorage` (JSch)，`StorageFactory` 注册新 `MediaType` |
| F-10 | P0 | WebDAV 文件管理 | 支持上传/移动/重命名/新建文件夹，不仅限于浏览 | `WebDavStorage` 已有 `createDirectory`/`saveFile`，补充 `move`/`rename` (WebDAV MOVE 方法) + UI 长按菜单 |
| F-11 | P1 | UPnP/DLNA 投屏 | 播放视频投屏到智能电视/DLNA 设备 | 集成 Cling (已停止维护) 或 fork；`MediaSessionManager` 的 `MediaRoute` + Cast SDK |
| F-12 | P1 | Alist/网盘支持 | 通过 Alist API 访问网盘 (阿里云盘/百度网盘等) | Alist 提供 WebDAV 兼容接口，可直接复用 `WebDavStorage`；或新增 `AlistStorage` 调用其 OpenAPI |
| F-13 | P2 | 存储源导入/导出 | 批量导入/导出存储源配置，方便换机 | JSON 序列化 `media_library` 表 (密码需重新输入)，复用备份模块 |

### 8.3 字幕与歌词增强

| ID | 优先级 | 功能 | 描述 | 技术方案 |
|----|--------|------|------|----------|
| F-14 | P0 | 字幕翻译 | 实时翻译外文字幕（双语显示） | 调用系统翻译 API 或第三方 (DeepL/Google)，`SubtitleEngine` 解析后对每条 caption 异步翻译缓存 |
| F-15 | P0 | VTT/PGS 字幕支持 | 补充 WebVTT 和 PGS 图形字幕格式 | `FormatVTT` 解析器 (类似 SRT)；PGS 需位图渲染，优先级可降 |
| F-16 | P1 | 字幕时间轴批量调整 | 整个字幕文件统一前移/后移 N 秒，不仅是当前会话偏移 | `SubtitleEngine` 的 offset 从运行时内存改为可持久化到 `SubtitleSettings`，并支持"应用到文件"导出修正后的字幕 |
| F-17 | P1 | 字幕样式预设 | 预设几套样式 (影院/明亮/简洁)，一键切换 | `SubtitleSettings` 增加 `preset` 字段，切换时批量覆盖 7 项配置 |
| F-18 | P2 | 歌词翻译/罗马音 | LRC 歌词附带翻译/罗马音行 | 扩展 `LrcParser` 支持多语言 LRC (带 `[tr:]`/`[romaji:]` 标签) |

### 8.4 用户体验增强

| ID | 优先级 | 功能 | 描述 | 技术方案 |
|----|--------|------|------|----------|
| F-19 | P0 | 视频书签/标记 | 在视频时间轴上打标记点，快速跳转 | 新增 `bookmark` 表 (video_unique_key, position_ms, label)，进度条显示标记点图标 |
| F-20 | P0 | 播放统计 | 记录观看时长/次数/最常看的类型，周/月报表 | `play_history` 表已有 `play_time`，新增聚合查询 + 统计页 |
| F-21 | P0 | 桌面 Widget | 4x1 音乐播放控件 + 4x2 最近播放 | `AppWidgetProvider` + RemoteViews (音乐控制)；Compose Glance (最近播放) |
| F-22 | P1 | 弹幕 (Danmaku) | 视频弹幕支持，弹幕源加载/发送/屏蔽 | 集成 DanmakuFlameMaster 或自研 Compose 弹幕层；弹幕源 API 可配置 |
| F-23 | P1 | 睡眠定时增强 | 播完当前视频后停止 / 播完当前集后停止 | `PlayerViewModel` 的 sleep timer 增加"播放结束后"模式，监听 `onPlaybackStateChanged → ENDED` 时才暂停 |
| F-24 | P1 | 快捷方式支持 | 桌面长按应用 → 快捷方式 (最近播放/存储源) | `ShortcutManager` 动态快捷方式，从 `play_history` / `quick_access` 取 top 3 |
| F-25 | P2 | 跨设备同步 | 通过 WebDAV 服务器同步播放进度/书签/快速访问 | 在 `sync_delete_log` 表基础上扩展同步协议，WebDAV 存 JSON diff |

### 8.5 系统集成增强

| ID | 优先级 | 功能 | 描述 | 技术方案 |
|----|--------|------|------|----------|
| F-26 | P1 | 备份与恢复 | 数据库 + 设置项导出/导入，换机迁移 | 重新启用 `:core:backup` 模块，JSON 序列化 Room + MMKV，密码字段单独处理 |
| F-27 | P1 | ASSRT 之外的字幕源 | 支持字幕组/伪射手/OpenSubtitles 等多源搜索 | `:core:network` 新增 `SubtitleSource` 接口，多源并发搜索 + 结果聚合 |
| F-28 | P2 | 插件化字幕源/元数据源 | 允许用户自定义字幕/歌词 API | 定义 `SubtitleSourcePlugin` / `MetadataSourcePlugin` 接口，运行时动态注册 |

---

## 9. 工程优化提案

> 基于代码审查（37 个 bug）与架构分析，按维度分类提案。每项标注影响范围与实施难度。

### 9.1 性能优化

| ID | 优先级 | 优化项 | 现状问题 | 方案 | 难度 |
|----|--------|--------|----------|------|------|
| O-01 | P0 | 启动优化 | `NiApplication.onCreate` 同步初始化 MMKV + 本地媒体库 + Coil | 改用 `AppInitializer` (Startup 库) 或 `DefaultDispatchers.IO` 异步初始化，MMKV 首次访问延迟到首页 | 低 |
| O-02 | P0 | SMB/WebDAV 连接池 | 每次操作新建 SmbFile/OkHttp 连接，无复用 | `SmbStorage` 维护 `SMBClient` 池 (smbj)，`WebDavStorage` 复用 `OkHttpClient` 连接池 (已部分复用) | 中 |
| O-03 | P0 | 远程文件预加载 | 打开文件浏览后逐个请求缩略图，串行慢 | `ThumbnailManager` 批量并发预加载当前可视区域 + 下 2 屏，`Dispatchers.IO` 限制并发 6 | 中 |
| O-04 | P1 | 字幕渲染缓存增强 | `SubtitleEngine` 按时间戳缓存，但 seek 后缓存全失效 | 改为按 caption index 缓存 `RenderableCaption`，seek 后只需重新计算当前帧而非全量 | 中 |
| O-05 | P1 | Room 查询优化 | `play_history` / `quick_access` 无复合索引，按日期分组查询全表扫描 | 索引: `(play_time DESC)` + `(storage_id, unique_key)`；`quick_access` 已有 `library_id` 索引 | 低 |
| O-06 | P1 | 图片加载下采样 | 远程大图直接全分辨率解码到内存 | Coil 已有 `size()` API，确认 `NiVideoThumbnail` / `ImageViewerViewModel` 传入目标尺寸，避免 OOM | 低 |
| O-07 | P2 | media3 缓冲策略调优 | 默认 `minBufferMs=50000` 对远程播放偏保守 | `LoadControl` 自定义：minBuffer 15s / maxBuffer 50s / bufferForPlayback 1s，平衡首帧速度与 seek 流畅度 | 低 |

### 9.2 内存优化

| ID | 优先级 | 优化项 | 现状问题 | 方案 | 难度 |
|----|--------|--------|----------|------|------|
| O-08 | P0 | Bitmap 池化 | 截图/缩略图/封面各自解码，无复用 | 引入 `BitmapPool` (LruCache<Bitmap>)，截图和缩略图共享池，`inBitmap` 复用 | 中 |
| O-09 | P0 | 大列表懒加载 | 文件浏览/历史页可能一次性加载数百项 Compose 节点 | 确认使用 `LazyColumn` / `LazyVerticalGrid` (应该已用)，`key()` 稳定，`contentType()` 复用 | 低 |
| O-10 | P1 | ExoPlayer 实例复用 | 每次进入播放页新建 `NxMedia3Player`，退出销毁 | 视频播放器可改为 ViewModel scope 复用 (当前一屏一实例)；音频已 @Singleton | 高 |
| O-11 | P1 | 缩略图磁盘缓存 | 仅有内存 LruCache (32MB)，进程退出后全丢 | Coil 磁盘缓存 (已配置 `diskCache`)，确认 `ThumbnailManager` 写入的 `.thumb/` 与 Coil 缓存协同 | 低 |

### 9.3 架构健壮性

| ID | 优先级 | 优化项 | 现状问题 | 方案 | 难度 |
|----|--------|--------|----------|------|------|
| O-12 | P0 | 全局异常捕获 | 无 Crash 上报机制，用户崩溃无反馈 | `Thread.setDefaultUncaughtExceptionHandler` + `NiApplication` 注册，崩溃日志写文件 + 下次启动提示上报 | 低 |
| O-13 | P0 | 统一 CoroutineScope 管理 | 多处 `CoroutineScope(Dispatchers.IO).launch { }` 游离作用域 (已在 bug 修复中部分处理) | 定义 `AppCoroutineScope` (@Singleton)，所有游离 launch 改为 `appScope.launch`，统一取消 | 中 |
| O-14 | P0 | ANR 监控 | 主线程 IO 无监控 (如 P6 主线程解码封面已在 bug 修复中处理) | 集成 `StrictMode` (debug 模式) + 自定义 `ANRWatchDog` (主线程定时 tick 检测) | 低 |
| O-15 | P1 | Hilt 作用域审查 | `AudioPlaybackManager` @Singleton 持有 ViewModel 回调导致泄漏 (已修复) | 建立规则: @Singleton 不得持有 ViewModel/Activity 引用，回调用 `WeakReference` 或 `StateFlow` 替代 | 中 |
| O-16 | P1 | CancellationException 统一处理 | 多处 `catch (Exception)` 吞掉取消异常 (已在 bug 修复中处理 S3/S6/S7) | 全局 lint 规则: `catch (Exception)` 前必须有 `catch (CancellationException) { throw it }`，CI 强制 | 低 |
| O-17 | P1 | Storage 资源生命周期管理 | Storage close 依赖手动调用，遗漏即泄漏 | `Storage` 实现 `Closeable`，`StorageFactory` 用引用计数，计数归零自动 close | 高 |

### 9.4 包体积与编译

| ID | 优先级 | 优化项 | 现状问题 | 方案 | 难度 |
|----|--------|--------|----------|------|------|
| O-18 | P0 | ABI Splits | FFmpeg so 同时打包 arm64 + armeabi-v7a，包体大 | `splits.abi` 按架构拆分 APK，或用 App Bundle 动态分发 | 低 |
| O-19 | P0 | 启用 R8/ProGuard | Release `isMinifyEnabled = false`，未裁剪未混淆 | 启用 `isMinifyEnabled = true` + `isShrinkResources = true`，编写 keep 规则 (Room Entity / Moshi / Hilt) | 中 |
| O-20 | P1 | 资源压缩 | 未启用 `resourcePrefix` / `shrinkResources` | 配合 R8 启用 `shrinkResources`，清理未引用的 drawable/string | 低 |
| O-21 | P1 | FFmpeg so 按需编译 | 当前编译了完整 libavcodec/libavutil/libswresample | 只编译需要的 decoder (TrueHD/DTS-HD/E-AC-3)，减小 so 体积 | 中 |

### 9.5 电量与网络

| ID | 优先级 | 优化项 | 现状问题 | 方案 | 难度 |
|----|--------|--------|----------|------|------|
| O-22 | P1 | 后台播放省电 | `AudioPlaybackService` 通知栏每次状态变化都更新通知 | `MediaSession` 已优化，确认 `notificationManager.notify` 仅在 metadata 变化时调用，不在进度更新时 | 低 |
| O-23 | P1 | 网络请求合并 | 缩略图 + 心跳 + 文件浏览各自请求，无合并 | `Storage` 心跳复用 listFiles 结果 (有数据则心跳通过)，缩略图批量请求合并为单个并发池 | 中 |
| O-24 | P2 | Doze 模式适配 | 后台音频在 Doze 模式下可能被限制 | `AudioPlaybackService` 申请 `WAKE_LOCK` (已有) + 确认 `foregroundServiceType=mediaPlayback` 豁免 Doze | 低 |

### 9.6 用户体验细节

| ID | 优先级 | 优化项 | 现状问题 | 方案 | 难度 |
|----|--------|--------|----------|------|------|
| O-25 | P0 | 错误提示统一化 | 各模块错误提示不一致 (有的 Toast 有的 Snackbar) | 统一用 `NiSnackbarHost`，定义 `ErrorType` 枚举 (网络/认证/文件/解码)，每类有默认文案 + 可展开详情 | 中 |
| O-26 | P0 | 空状态/加载状态规范 | 部分页面无空状态或加载骨架 | 所有列表页统一用 `NiEmptyState` + `NiLoadingSkeleton` (已存在组件)，`UiState<T>` sealed class 统一封装 | 中 |
| O-27 | P1 | 无障碍适配 | Compose 语义不完整， TalkBack 支持不足 | 关键交互元素补 `contentDescription` / `semantics`，播放器手势区域补 `clickLabel` | 中 |
| O-28 | P1 | 平板/折叠屏适配 | 当前为手机布局，未适配大屏 | `WindowSizeClass` (已存在) 实际应用: 大屏双栏 (列表+详情)，播放器控制栏宽度自适应 | 高 |

---

## 10. 已知限制与路线图

### 10.1 已知限制

| 维度 | 当前状态 | 说明 |
|------|----------|------|
| 备份与恢复 | 设置页占位 (开发中) | `:core:backup` 孤儿模块未注册 |
| 弹幕 | 未支持 | — |
| 投屏 | 未支持 | — |
| 插件化 | 未支持 | — |
| 核心转码 | 未支持 | — |
| R8 混淆 | 未启用 | Release 包未裁剪 |
| 平板适配 | 未优化 | 仅手机布局 |
| 无障碍 | 不完整 | TalkBack 支持不足 |

### 10.2 建议路线图

**Phase 1 — 稳定性与基础体验 ✅ 已完成**
- ✅ P0+P1 bug 修复 (已完成 16 项)
- ✅ O-12 全局异常捕获 (CrashHandler + 崩溃日志文件 + 启动提示)
- ✅ O-13 统一 CoroutineScope (AppCoroutineScope @Singleton + Hilt @Binds)
- ✅ O-19 启用 R8/ProGuard (isMinifyEnabled + isShrinkResources + keep 规则;release APK 19MB)
- ✅ O-25/O-26 错误提示与空状态统一 (AppError sealed class + NiMessage + UiState + NiSnackbarHost/NiEmptyState/NiListSkeleton)

**Phase 2 — 播放体验提升 ✅ 已完成**
- ✅ F-01 倍速音调修正（PlaybackParameters pitch 修正）
- ✅ F-02 音频均衡器（NiEqualizer + 7 内置预设 + 自定义频段增益 + 中线刻度 UI）
- ✅ F-19 视频书签（video_bookmark 表 + 进度条标记 + 书签列表弹窗 + BookmarkAdd 图标）
- ✅ F-20 播放统计（聚合查询 + 统计页 + Top N）
- ~~F-21 桌面 Widget~~（已移除：桌面 Widget 与核心播放体验无关，通知栏 + MediaSession 已覆盖后台控制需求）

**Phase 3 — 存储与文件管理 (当前阶段)**
- 远程文件管理（SMB + WebDAV 的 重命名 / 移动 / 新建文件夹 / 删除 + 文件浏览页长按菜单入口）
- F-11 UPnP/DLNA 投屏
- F-26 备份与恢复
- O-02 连接池优化

**Phase 4 — 字幕与内容**
- F-14 字幕翻译
- F-15 VTT/PGS 格式
- F-27 多字幕源搜索
- F-22 弹幕支持

**Phase 5 — 高级特性**
- F-04 进度条缩略图预览
- F-05 视频滤镜
- F-12 Alist/网盘支持
- O-28 平板/折叠屏适配
- F-25 跨设备同步
