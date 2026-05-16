# NI Player Code Wiki

## 目录

1. [项目概述](#项目概述)
2. [项目架构](#项目架构)
3. [模块详解](#模块详解)
4. [核心类与接口](#核心类与接口)
5. [播放器子系统](#播放器子系统)
6. [存储子系统](#存储子系统)
7. [字幕子系统](#字幕子系统)
8. [网络层](#网络层)
9. [数据库](#数据库)
10. [配置系统](#配置系统)
11. [依赖关系](#依赖关系)
12. [构建与运行](#构建与运行)

---

## 项目概述

NI Player 是一个基于 [弹弹play (DanDanPlayForAndroid)](https://github.com/xyoye/DanDanPlayForAndroid) 二次开发的 Android 本地多媒体播放器。项目使用 **Kotlin + MVVM + 组件化** 架构，支持视频、图片、音频播放，具备丰富的字幕功能和多种远程存储源支持。

### 修改说明（相对于原弹弹play）

| 变更项 | 说明 |
|-------|------|
| 移除 Bilibili 弹幕 | 去除 Bilibili 弹幕相关功能模块 |
| 移除投屏功能 | 去除 DLNA/投屏相关代码 |
| 新增本地页面 | 整合本地媒体库和设备存储库 |
| 重构导航栏 | 改为"服务器"、"本地"、"设置"三个页面 |
| 新增图片浏览 | 支持幻灯片、画廊视图 |
| 新增音频播放 | 独立的音频播放界面和播放历史 |

### 技术栈

| 类别 | 技术选型 |
|------|---------|
| 语言 | Kotlin 1.7.21 + Java |
| 架构 | MVVM (ViewModel + LiveData + DataBinding) |
| 组件化 | 多模块 Gradle 项目 |
| 最小 SDK | API 21 (Android 5.0 Lollipop) |
| 目标 SDK | API 29 (Android 10) |
| 编译 SDK | API 33 (Android 13) |
| 包名 | com.nichx.niplayer |

---

## 项目架构

### 架构设计理念

项目采用 **组件化 + MVVM** 架构，将功能拆分为多个 Gradle Module，每个模块职责单一，通过接口和依赖注入进行解耦。UI 层采用 DataBinding 实现视图与数据的双向绑定，ViewModel 管理业务逻辑和 UI 状态。

### 模块组织

```
NI Player/
├── app/                              # 应用入口模块
├── common_component/                 # 公共基础模块（核心层）
├── data_component/                   # 数据定义模块
├── player_component/                 # 播放器模块
├── user_component/                   # 用户设置模块
├── local_component/                  # 本地功能模块
├── storage_component/                # 远程存储模块
├── buildSrc/                         # 构建配置（Gradle Kotlin DSL）
│   ├── Dependencies.kt               # 依赖版本管理
│   ├── Versions.kt                   # 版本号配置
│   └── src/main/java/setup/          # 自定义 Gradle 插件
├── repository/                       # 第三方库 AAR 封装
│   ├── panel_switch/                 # 面板切换库
│   ├── seven_zip/                    # 7-Zip 解压库
│   ├── thunder/                      # 迅雷下载库
│   └── video_cache/                  # 视频缓存库
└── document/                         # 文档
```

### 模块依赖关系

```
                    app
           ┌───────┼──────────┐
           │       │          │
    common_component  │  player_component
           │       │          │
    data_component   │          │
           │       │          │
    ┌──────┴──┐    │          │
    │         │    │          │
 local_component storage_component  user_component
    │         │    │          │
    └─────────┴────┴──────────┘
           │       │
           └── data_component (被所有业务模块依赖)
```

---

## 模块详解

### 1. App 模块 (`app/`)

应用入口模块，仅包含启动类和应用初始化逻辑。

**关键文件：**

| 文件 | 功能 |
|------|------|
| [IApplication.kt](file:///d:/project/NIplayer/NIplayer/app/src/main/java/com/xyoye/dandanplay/app/IApplication.kt) | 应用 Application 类，继承 `BaseApplication`，初始化 MultiDex |
| [SplashActivity.kt](file:///d:/project/NIplayer/NIplayer/app/src/main/java/com/xyoye/dandanplay/ui/splash/SplashActivity.kt) | 启动页 Activity，展示动画 Logo |
| [MainActivity.kt](file:///d:/project/NIplayer/NIplayer/app/src/main/java/com/xyoye/dandanplay/ui/main/MainActivity.kt) | 主界面，底部导航栏管理三个 Fragment 切换 |
| [MainViewModel.kt](file:///d:/project/NIplayer/NIplayer/app/src/main/java/com/xyoye/dandanplay/ui/main/MainViewModel.kt) | 主界面 ViewModel |

**启动页动画：** 使用自定义 SVG 路径动画（`AnimatedSvgView`）和文字路径动画（`TextPathAnimView`）实现启动 Logo 展示。

### 2. Common Component (`common_component/`)

项目的核心基础模块，被所有业务模块依赖。按功能分为以下子包：

#### 2.1 基类层 (`base/`)

```
base/
├── app/
│   ├── BaseApplication.kt        # 基础 Application（全局上下文、初始化链）
│   ├── BaseInitializer.kt        # 初始化器接口
├── BaseActivity.kt               # 泛型基类 Activity（集成 ViewModel + DataBinding）
├── BaseAppCompatActivity.kt      # 进一步封装的 Activity 基类
├── BaseFragment.kt               # 基础 Fragment（带 ViewModel 集成）
├── BaseAppFragment.kt            # DataBinding Fragment 基类
├── BaseViewModel.kt              # 基础 ViewModel（加载状态管理）
├── BaseLoadingDialog.kt          # 加载对话框基类
```

**BaseActivity** 是项目中所有 Activity 的基类，提供：
- 通过 `ViewModelProvider` 自动注入 ViewModel
- 通过 `DataBinding` 将 ViewModel 绑定到布局
- 统一的沉浸式状态栏管理（`ImmersionBar`）
- 统一的加载对话框观察（`loadingObserver`）

**BaseViewModel** 提供统一的 Loading 状态管理：
```kotlin
abstract class BaseViewModel : ViewModel() {
    val loadingObserver: MutableLiveData<Pair<Int, String?>>
    protected fun showLoading(msg: String)
    protected fun showLoading()
    protected fun hideLoading()
    protected fun hideLoadingSuccess()
    protected fun hideLoadingFailed()
}
```

#### 2.2 配置系统 (`config/`)

基于 MMKV 注解的配置表，编译时生成对应的 `Config` 类：

| 配置表 | 说明 |
|-------|------|
| `AppConfigTable` | 应用通用配置 |
| `PlayerConfigTable` | 播放器核心配置 |
| `DanmuConfigTable` | 弹幕配置 |
| `SubtitleConfigTable` | 字幕配置 |
| `UserConfigTable` | 用户配置 |
| `DatabaseConfigTable` | 数据库配置 |
| `DownloadConfigTable` | 下载配置 |
| `ThumbnailConfigTable` | 缩略图配置 |
| `DefaultConfig` | 默认值定义 |

#### 2.3 网络层 (`network/`)

**核心封装：** [Retrofit.kt](file:///d:/project/NIplayer/NIplayer/common_component/src/main/java/com/xyoye/common_component/network/Retrofit.kt)

基于 Retrofit + OkHttp + Moshi 的网络请求封装，提供 5 个 API Service：

| Service | 用途 |
|---------|------|
| `DanDanService` | 弹弹play 开放 API |
| `MagnetService` | 磁力链接搜索 API |
| `ExtendedService` | 第三方扩展字幕 API |
| `RemoteService` | 远程媒体库 API |
| `AlistService` | AList 网盘 API |

**OkHttp 拦截器链：**

```
请求 → SignatureInterceptor(签名) → AgentInterceptor(User-Agent)
     → AuthInterceptor(认证) → DecompressInterceptor(解压缩)
     → BackupDomainInterceptor(备用域名) → LoggerInterceptor(日志)
     → DynamicBaseUrlInterceptor(动态BaseURL)
```

#### 2.4 数据库层 (`database/`)

使用 **Room** 数据库，数据库文件名为 `rood_db`，当前版本为 13。

**DAO 接口：**

| DAO | 操作实体 |
|-----|---------|
| `VideoDao` | 视频文件数据 |
| `PlayHistoryDao` | 播放历史 |
| `MediaLibraryDao` | 媒体库配置 |
| `DanmuBlockDao` | 弹幕屏蔽关键词 |
| `ExtendFolderDao` | 扩展扫描文件夹 |
| `MagnetScreenDao` | 磁力筛选规则 |
| `MagnetSearchHistoryDao` | 磁链搜索历史 |
| `AnimeSearchHistoryDao` | 番剧搜索历史 |

**数据迁移历史（12次迁移，从版本1到13）：**
- 版本 1→2: media_library 添加 remote_secret 字段
- 版本 2→3: 更新 media_library 唯一索引
- 版本 3→4: play_history 重构（添加 extra、修改唯一约束）
- 版本 4→5: media_library 添加 web_dav_strict 字段
- 版本 5→6: play_history 添加 torrent_path、torrent_index、http_header
- 版本 6→7: play_history 添加 unique_key，修改唯一约束
- 版本 7→8: media_library 添加 screencast_address
- 版本 8→9: media_library 添加 remote_anime_grouping
- 版本 9→10: play_history 添加 is_last_play
- 版本 10→11: play_history 添加 storage_path、storage_id
- 版本 11→12: play_history 重构（episode_id 改为 TEXT、修改唯一约束）
- 版本 12→13: play_history 添加 audio_path

#### 2.5 扩展函数 (`extension/`)

包含大量 Kotlin 扩展函数，按功能分类：

| 文件 | 主要扩展 |
|------|---------|
| `FileExt.kt` | 文件操作扩展 |
| `ImageViewExt.kt` | ImageView 图片加载扩展 |
| `StringExt.kt` | 字符串处理扩展 |
| `DateExt.kt` | 日期格式化扩展 |
| `RecyclerViewExt.kt` | RecyclerView 动画扩展 |
| `ContextExt.kt` | Context 工具方法 |
| `FlowExt.kt` | Kotlin Flow 扩展 |
| `MediaTypeExt.kt` | 媒体类型判断扩展 |
| `NotificationExtensions.kt` | 通知栏扩展 |

#### 2.6 工具类 (`utils/`)

**核心工具类：**

| 类 | 功能 |
|----|------|
| `DDLog.kt` | 日志工具封装 |
| `FileUtils.kt` | 文件操作工具 |
| `MediaUtils.kt` | 媒体处理工具（截图、缩略图） |
| `PathHelper.kt` | 路径管理工具 |
| `DiskUtils.kt` | 磁盘信息工具 |
| `ScreenUtils.kt` | 屏幕参数工具 |
| `KeyboardUtils.kt` | 键盘状态工具 |
| `ActivityHelper.kt` | Activity 跳转辅助 |
| `AppUtils.kt` | 应用信息工具 |
| `JsonHelper.kt` | JSON 解析（Moshi 封装） |
| `SecurityHelper.kt` | 安全/加密辅助 |
| `SupervisorScope.kt` | 协程 SupervisorScope 管理 |
| `ThumbnailGeneratorManager.kt` | 缩略图生成管理器 |
| `ThumbnailMemoryCache.kt` | 缩略图内存缓存 |

**缩略图生成管理器 (`ThumbnailGeneratorManager`)：**
- 支持本地、SMB、FTP、WebDav 等多种存储类型的缩略图生成
- 使用 `MediaMetadataRetriever` 提取视频帧
- 支持滚动时暂停生成（性能优化）
- Bitmap 复用池减少 GC 压力
- 批次处理（BATCH_SIZE = 6）
- 最大重试次数 2 次
- 缩略图缓存到本地私有目录

#### 2.7 自定义控件 (`weight/`)

| 控件 | 用途 |
|-----|------|
| `CircleImageView.java` | 圆形 ImageView |
| `ColorSeekBar.java` | 颜色选择滑块 |
| `LabelsView.java` | 标签布局 |
| `MarqueeTextView.java` | 跑马灯 TextView |
| `SlantedTextView.kt` | 倾斜文本视图 |
| `ToastCenter.kt` | 居中 Toast |
| `CenterLayoutManager.kt` | 居中布局管理器 |
| `BottomActionDialog.kt` | 底部操作弹窗 |
| `BaseBottomDialog.kt` | 底部弹窗基类 |
| `CommonDialog.kt` | 通用对话框（支持倒计时确认） |
| `CommonEditDialog.kt` | 通用输入对话框 |
| `FileManagerDialog.kt` | 文件管理器对话框 |

#### 2.8 视频源系统 (`source/`)

```
source/
├── base/
│   ├── BaseVideoSource.kt      # 视频源抽象基类
│   ├── GroupVideoSource.kt     # 分组视频源（多集）
├── inter/
│   ├── VideoSource.kt          # 视频源接口
│   ├── ExtraSource.kt          # 扩展资源接口（弹幕、字幕、音频）
│   ├── GroupSource.kt          # 分组接口
├── media/
│   ├── StorageVideoSource.kt   # 存储视频源实现
├── factory/
│   ├── StorageVideoSourceFactory.kt  # 视频源工厂
├── VideoSourceManager.kt       # 视频源管理单例
```

#### 2.9 中文简繁转换 (`open_cc/`)

基于 OpenCC 库的简繁中文转换，包含 `STCharacters.ocd2`、`STPhrases.ocd2`、`TSCharacters.ocd2`、`TSPhrases.ocd2` 等数据文件。

#### 2.10 其他

| 子包 | 说明 |
|------|------|
| `adapter/` | 通用 RecyclerView Adapter（支持 Paging3） |
| `application/` | 应用上下文 + 权限管理 |
| `bridge/` | 跨模块通信桥接 |
| `notification/` | 通知管理 |
| `receiver/` | 广播接收器（电量、耳机、通知、屏幕） |
| `resolver/` | 媒体文件解析器 |
| `services/` | 存储文件 Provider 接口 |
| `ui/image_viewer/` | 图片查看器 Activity |

---

### 3. Data Component (`data_component/`)

纯数据定义模块，不包含业务逻辑。

```
data_component/
├── bean/              # 数据传输对象（DTO）
│   ├── CacheBean.kt
│   ├── ColorRange.kt
│   ├── EditBean.kt
│   ├── FilePathBean.kt
│   ├── FolderBean.kt
│   └── VideoTagBean.kt
├── data/              # 数据模型
│   ├── AnimeCidData.kt
│   ├── AnimeData.kt
│   ├── AnimeTagData.kt
│   ├── BannerData.kt
│   ├── DanmuData.kt
│   └── LoginData.kt
├── enums/             # 枚举定义
│   ├── CacheType.kt
│   ├── HistorySort.kt
│   ├── MediaType.kt
│   ├── PixelFormat.kt
│   ├── PlayState.kt
│   ├── PlayerType.kt
│   ├── StorageSort.kt
│   ├── SurfaceType.kt
│   ├── TrackType.kt
│   └── VLCHWDecode.kt
└── helper/
    └── Loading.kt     # Loading 状态常量
```

**主要枚举值：**

| 枚举 | 关键值 |
|------|--------|
| `PlayerType` | TYPE_IJK_PLAYER, TYPE_EXO_PLAYER, TYPE_VLC_PLAYER |
| `PlayState` | STATE_IDLE, STATE_PREPARING, STATE_PREPARED, STATE_PLAYING, STATE_PAUSED, STATE_BUFFERING_PAUSED, STATE_BUFFERING_PLAYING, STATE_COMPLETED, STATE_ERROR, STATE_START_ABORT |
| `MediaType` | LOCAL_STORAGE, EXTERNAL_STORAGE, FTP_SERVER, SMB_SERVER, WEBDAV_SERVER, REMOTE_STORAGE, MAGNET_LINK, STREAM_LINK, ALSIT_STORAGE, OTHER_STORAGE |
| `TrackType` | VIDEO, AUDIO, SUBTITLE |
| `SurfaceType` | VIEW_TEXTURE, VIEW_SURFACE |
| `VideoScreenScale` | SCREEN_SCALE_DEFAULT, SCREEN_SCALE_16_9, SCREEN_SCALE_4_3, SCREEN_SCALE_FULL, SCREEN_SCALE_ORIGIN |

---

### 4. Storage Component (`storage_component/`)

远程存储管理模块，负责各种存储源的 UI 交互。

**Activity：**

| 类 | 功能 |
|----|------|
| `StorageFileActivity.kt` | 存储文件浏览主界面 |
| `StoragePlusActivity.kt` | 存储源管理（添加/编辑/删除） |
| `RemoteScanActivity.kt` | 远程存储扫描 |

**存储编辑对话框：**

| 对话框 | 存储类型 |
|--------|---------|
| `FTPStorageEditDialog.kt` | FTP 服务器 |
| `SmbStorageEditDialog.kt` | SMB 共享 |
| `WebDavStorageEditDialog.kt` | WebDAV 服务器 |
| `AlistStorageEditDialog.kt` | AList 网盘 |
| `RemoteStorageEditDialog.kt` | 远程存储 |
| `ExternalStorageEditDialog.kt` | 外部存储 |
| `StorageEditDialog.kt` | 通用存储编辑基类 |

**文件浏览：**

- `StorageFileFragment.kt` - 文件浏览 Fragment（支持列表/网格切换）
- `StorageFileAdapter.kt` - 文件列表适配器（支持视频标签、多种布局）
- `StorageFileViewModel.kt` - 文件浏览 ViewModel

**自定义控件：**

- `RadarScanView.kt` - 雷达扫描动画视图
- `ScanWindowView.kt` - 扫描窗口视图
- `StorageFileBehavior.kt` - 文件列表 Behavior
- `StorageFileMenus.kt` - 文件菜单管理

---

### 5. Local Component (`local_component/`)

本地功能模块，负责本地媒体管理、播放历史、字幕下载等。

**Fragment：**

| 类 | 功能 |
|----|------|
| `MediaFragment.kt` | 媒体库浏览（视频、音频、图片分类） |
| `MineFragment.kt` | "我的"页面 |
| `BindSubtitleSourceFragment.kt` | 绑定字幕源 |

**Activity：**

| 类 | 功能 |
|----|------|
| `PlayHistoryActivity.kt` | 播放历史管理 |
| `BindExtraSourceActivity.kt` | 绑定额外资源（字幕、弹幕、音轨） |
| `ShooterSubtitleActivity.kt` | 射手字幕搜索下载 |

**对话框：**

| 类 | 功能 |
|----|------|
| `MagnetPlayDialog.kt` | 磁力链播放对话框 |
| `StreamLinkDialog.kt` | 串流链接对话框 |
| `SubtitleDetailDialog.kt` | 字幕详情对话框 |
| `SubtitleFileListDialog.kt` | 字幕文件列表选择 |
| `SegmentWordDialog.kt` | 分词对话框 |
| `ShooterSecretDialog.kt` | 射手网密钥输入 |

**工具类：**
- `EpisodeUtils.kt` - 剧集解析工具
- `HistorySortOption.kt` - 历史排序选项

---

### 6. User Component (`user_component/`)

用户设置模块，包含应用设置、播放器设置、字幕设置等。

**Fragment：**

| 类 | 功能 |
|----|------|
| `PersonalFragment.kt` | 个人设置主页 |
| `AppSettingFragment.kt` | 应用设置 |
| `PlayerSettingFragment.kt` | 播放器设置 |
| `SubtitleSettingFragment.kt` | 字幕设置 |
| `ScanExtendFragment.kt` | 扫描扩展目录 |
| `ScanFilterFragment.kt` | 扫描过滤规则 |

**Activity：**

| 类 | 功能 |
|----|------|
| `SettingPlayerActivity.kt` | 播放器详细设置 |
| `SettingAppActivity.kt` | 应用详细设置 |
| `ScanManagerActivity.kt` | 扫描管理 |
| `CacheManagerActivity.kt` | 缓存管理 |
| `CommonlyFolderActivity.kt` | 常用文件夹管理 |
| `SwitchThemesActivity.kt` | 主题切换 |
| `ThumbnailSettingActivity.kt` | 缩略图设置 |
| `LicenseActivity.kt` | 开源许可证 |
| `WebViewActivity.kt` | WebView 通用页面 |

---

### 7. Player Component (`player_component/`)

播放器核心模块，结构最为复杂。

```
player_component/src/main/java/com/xyoye/
├── cache/                        # 视频缓存
│   ├── CacheManager.kt
│   ├── OkHttpUrlSource.kt
│   ├── OkHttpUrlSourceFactory.kt
│   └── VideoHeadersInjector.kt
├── player/                       # 播放器核心
│   ├── DanDanVideoPlayer.kt      # 顶层播放器类
│   ├── controller/               # 控制器层
│   │   ├── VideoController.kt    # 主控制器
│   │   ├── base/                 # 控制器基类
│   │   │   ├── BaseVideoController.kt
│   │   │   ├── GestureVideoController.kt
│   │   │   └── TvVideoController.kt
│   │   ├── video/                # 视频控制视图
│   │   │   ├── PlayerTopView.kt
│   │   │   ├── PlayerBottomView.kt
│   │   │   ├── PlayerControlView.kt
│   │   │   ├── PlayerGestureView.kt
│   │   │   ├── PlayerPopupControlView.kt
│   │   │   ├── LoadingView.kt
│   │   │   ├── InterControllerView.kt
│   │   │   ├── InterGestureView.kt
│   │   │   └── MessageContainer.kt
│   │   ├── setting/              # 设置视图
│   │   │   ├── SettingController.kt
│   │   │   ├── SettingOffsetTimeView.kt
│   │   │   ├── SettingSubtitleStyleView.kt
│   │   │   ├── SettingTracksView.kt
│   │   │   ├── SettingVideoAspectView.kt
│   │   │   ├── SettingVideoSpeedView.kt
│   │   │   ├── SwitchSourceView.kt
│   │   │   ├── SwitchVideoSourceView.kt
│   │   │   ├── ScreenShotView.kt
│   │   │   ├── PlayerSettingView.kt
│   │   │   └── BaseSettingView.kt
│   │   └── subtitle/             # 字幕控制器
│   │       ├── SubtitleController.kt
│   │       ├── SubtitleTextView.kt
│   │       ├── SubtitleImageView.kt
│   │       └── ExternalSubtitleView.kt
│   ├── kernel/                   # 播放器内核
│   │   ├── inter/
│   │   │   ├── AbstractVideoPlayer.kt      # 抽象播放器
│   │   │   └── VideoPlayerEventListener.kt # 播放器事件监听
│   │   ├── facoty/
│   │   │   └── PlayerFactory.kt             # 播放器工厂
│   │   └── impl/
│   │       ├── ijk/
│   │       │   ├── IjkVideoPlayer.kt
│   │       │   ├── IjkPlayerFactory.kt
│   │       │   └── RawDataSourceProvider.kt
│   │       ├── exo/
│   │       │   ├── ExoVideoPlayer.kt
│   │       │   ├── ExoPlayerFactory.kt
│   │       │   └── ExoMediaSourceHelper.kt
│   │       └── vlc/
│   │           ├── VlcVideoPlayer.kt
│   │           ├── VlcPlayerFactory.kt
│   │           └── VlcProxyServer.kt
│   ├── surface/                  # 渲染视图
│   │   ├── InterSurfaceView.kt    # 渲染视图接口
│   │   ├── SurfaceFactory.kt     # 渲染视图工厂
│   │   ├── SurfaceViewFactory.kt # SurfaceView 实现
│   │   ├── TextureViewFactory.kt # TextureView 实现
│   │   ├── VLCViewFactory.kt     # VLC 专用渲染
│   │   ├── RenderSurfaceView.kt
│   │   ├── RenderTextureView.kt
│   │   └── RenderVLCView.kt
│   ├── info/                     # 播放器配置
│   │   ├── PlayerInitializer.kt
│   │   ├── SettingAction.kt
│   │   ├── SettingActionType.kt
│   │   └── SettingItem.kt
│   ├── utils/                    # 播放器工具
│   │   ├── AudioFocusHelper.kt
│   │   ├── LongPressAccelerator.kt
│   │   ├── MessageTime.kt
│   │   ├── OrientationHelper.kt
│   │   ├── PlayerConstant.kt
│   │   ├── ProgressObserver.kt
│   │   ├── RenderMeasureHelper.kt
│   │   ├── TimeUtils.kt
│   │   ├── VideoLog.kt
│   │   ├── VlcEventLog.kt
│   │   └── VlcProxyServer.kt
│   └── wrapper/                  # 包装器
│       ├── ControlWrapper.kt     # 控制器包装器
│       ├── InterVideoPlayer.kt   # 视频播放器接口
│       ├── InterVideoController.kt # 视频控制器接口
│       ├── InterVideoTrack.kt    # 轨道接口
│       ├── InterSubtitleController.kt # 字幕控制器接口
│       └── InterSettingController.kt # 设置控制器接口
├── subtitle/                     # 字幕解析
│   ├── format/                   # 格式解析器
│   │   ├── FormatFactory.kt      # 格式工厂
│   │   ├── FormatASS.java
│   │   ├── FormatSRT.java
│   │   ├── FormatSCC.java
│   │   ├── FormatSTL.java
│   │   └── FormatTTML.java
│   ├── info/                     # 字幕数据模型
│   │   ├── Caption.java
│   │   ├── Style.java
│   │   ├── Time.java
│   │   └── TimedTextObject.java
│   ├── BaseSubtitleView.kt
│   ├── ExternalSubtitleManager.kt
│   ├── MixedSubtitle.kt
│   ├── SubtitleText.kt
│   ├── SubtitleType.kt
│   └── SubtitleUtils.kt
└── player_component/             # 播放器 UI
    ├── ui/activities/
    │   ├── player/PlayerActivity.kt
    │   ├── player_intent/PlayerIntentActivity.kt
    │   └── player_interceptor/PlayerInterceptorActivity.kt
    ├── utils/
    │   ├── BatteryHelper.kt
    │   └── PlayRecorder.kt
    └── widgets/
        ├── BatteryView.kt
        └── popup/
            ├── PlayerPopupManager.kt
            ├── PlayerPopupView.kt
            ├── PointEvaluator.kt
            ├── PopupGestureHandler.kt
            ├── PopupOutlineProvider.kt
            └── PopupPositionListener.kt
```

---

## 核心类与接口

### 1. DanDanVideoPlayer

播放器系统的顶层类，继承自 `FrameLayout`。它是连接播放器内核、渲染视图、控制器的桥梁。

**职责：**
- 管理播放器生命周期（初始化、播放、暂停、释放）
- 管理渲染视图（SurfaceView / TextureView）
- 处理音频焦点
- 转发播放事件到控制器
- 管理轨道切换

**核心状态：**
```kotlin
private var mCurrentPlayState = PlayState.STATE_IDLE
private var mVideoController: VideoController? = null
private var mRenderView: InterSurfaceView? = null
private lateinit var mVideoPlayer: AbstractVideoPlayer
private var mPlayerReleased = false
private lateinit var videoSource: BaseVideoSource
```

### 2. 播放器内核体系

#### AbstractVideoPlayer（抽象基类）

所有播放器内核的抽象基类，定义播放器核心接口：

```kotlin
abstract class AbstractVideoPlayer : InterVideoTrack {
    abstract fun initPlayer()
    abstract fun setOptions()
    abstract fun setDataSource(path: String, headers: Map<String, String>? = null)
    abstract fun setSurface(surface: Surface?)
    abstract fun prepareAsync()
    abstract fun start()
    abstract fun pause()
    abstract fun stop()
    abstract fun reset()
    abstract fun release()
    abstract fun seekTo(timeMs: Long)
    abstract fun setSpeed(speed: Float)
    abstract fun setVolume(leftVolume: Float, rightVolume: Float)
    abstract fun setLooping(isLooping: Boolean)
    abstract fun setSubtitleOffset(offsetMs: Long)
    abstract fun isPlaying(): Boolean
    abstract fun getCurrentPosition(): Long
    abstract fun getDuration(): Long
    abstract fun getSpeed(): Float
    abstract fun getVideoSize(): Point
    abstract fun getBufferedPercentage(): Int
    abstract fun getTcpSpeed(): Long
}
```

#### 三种内核实现

**IjkVideoPlayer**（基于 IJKPlayer/FFmpeg）
- 使用 `IjkMediaPlayer` 作为底层播放引擎
- 支持硬解码（MediaCodec）和 H.265 硬解码
- 支持 OpenSL ES 音频输出
- 支持多种像素格式配置
- 支持帧跳过（framedrop）优化
- 支持网络重连

**ExoVideoPlayer**（基于 Google ExoPlayer）
- 使用 `ExoPlayer` + `FfmpegRenderersFactory`
- 支持 FFmpeg 扩展渲染器
- 内置字幕解析（`onCues` 回调）
- 支持字幕类型自动识别（Bitmap/Text）
- 首选中文/日语字幕轨道
- 默认缓冲策略：5s~15s

**VlcVideoPlayer**（基于 VLC libvlc）
- 使用 `LibVLC` + `MediaPlayer`
- 支持硬件加速配置
- 支持自定义像素格式（RGB32、RV16、RV32）
- 支持代理服务器设置请求头
- 支持 content:// URI
- 内置连续错误保护（最大 5 次连续错误后自动停止）

#### PlayerFactory（工厂模式）

```kotlin
fun getFactory(playerType: PlayerType): PlayerFactory {
    return when (playerType) {
        PlayerType.TYPE_EXO_PLAYER -> ExoPlayerFactory()
        PlayerType.TYPE_IJK_PLAYER -> IjkPlayerFactory()
        PlayerType.TYPE_VLC_PLAYER -> VlcPlayerFactory()
        else -> IjkPlayerFactory()
    }
}
```

### 3. 渲染视图体系

#### InterSurfaceView（接口）

```kotlin
interface InterSurfaceView {
    fun getView(): View
    fun attachPlayer(player: AbstractVideoPlayer)
    fun setVideoSize(width: Int, height: Int)
    fun setScaleType(scaleType: VideoScreenScale)
    fun setVideoRotation(rotation: Int)
    fun refresh()
    fun release()
}
```

**三种渲染实现：**
- `RenderSurfaceView` - 基于 SurfaceView
- `RenderTextureView` - 基于 TextureView
- `RenderVLCView` - VLC 专用渲染（使用 `VLCVideoLayout`）

### 4. 控制器体系

#### VideoController

播放器的主控制器，集成以下子控制器：

- **GestureVideoController**（基类）- 手势控制逻辑
- **SubtitleController** - 字幕渲染控制
- **SettingController** - 设置面板控制

**控制视图组件：**
- `PlayerTopView` - 顶部控制栏（标题、返回、电量、时间）
- `PlayerBottomView` - 底部控制栏（播放/暂停、进度条、全屏）
- `PlayerControlView` - 中间控制按钮
- `PlayerGestureView` - 手势操作（亮度、音量、进度）
- `LoadingView` - 加载动画
- `PlayerPopupControlView` - 悬浮窗模式控制

#### ControlWrapper（外观模式）

将所有控制器（播放器、控制器、字幕、设置）统一封装，对外提供单一接口。

### 5. 播放器初始配置 (PlayerInitializer)

全局单例，管理播放器运行时配置：

```kotlin
object PlayerInitializer {
    var isPrintLog: Boolean
    var isOrientationEnabled: Boolean
    var isEnableAudioFocus: Boolean
    var isLooping: Boolean
    var playerType: PlayerType          // 默认 VLC
    var surfaceType: SurfaceType        // 默认 TextureView
    var screenScale: VideoScreenScale

    object Player {
        var isMediaCodeCEnabled         // IJK 硬解码
        var isMediaCodeCH265Enabled     // H.265 硬解码
        var isOpenSLESEnabled           // OpenSL ES
        var pixelFormat                 // 像素格式
        var vlcPixelFormat              // VLC 像素格式
        var vlcHWDecode                 // VLC 硬件加速
        var videoSpeed, pressVideoSpeed // 播放速度
        var vlcAudioOutput              // VLC 音频输出
        var isAutoPlayNext              // 自动播放下一个
        var wifiCacheSize, mobileCacheSize  // 缓存大小
    }

    object Subtitle {
        var offsetPosition              // 字幕时间偏移
        var textSize, strokeWidth       // 字幕样式
        var textColor, strokeColor
    }
}
```

---

## 存储子系统

### Storage 接口体系

```
Storage（接口）
  └── AbstractStorage（抽象类）
      ├── VideoStorage          # 本地视频存储
      ├── DocumentFileStorage   # 外部存储（SAF）
      ├── FtpStorage            # FTP 服务器
      ├── SmbStorage            # SMB 共享
      ├── WebDavStorage         # WebDAV 服务器
      ├── AlistStorage          # AList 网盘
      ├── RemoteStorage         # 远程存储
      ├── LinkStorage           # 流媒体链接
      └── TorrentStorage        # 磁力链接
```

### Storage 接口定义

```kotlin
interface Storage {
    var library: MediaLibraryEntity
    var directory: StorageFile?
    var directoryFiles: List<StorageFile>
    var rootUri: Uri

    suspend fun getRootFile(): StorageFile?
    suspend fun openFile(file: StorageFile): InputStream?
    suspend fun openFile(file: StorageFile, offset: Long): InputStream?
    suspend fun saveFile(path: String, data: ByteArray): Boolean
    suspend fun fileExists(path: String): Boolean
    suspend fun openDirectory(file: StorageFile, refresh: Boolean): List<StorageFile>
    suspend fun createDirectory(path: String): Boolean
    suspend fun pathFile(path: String, isDirectory: Boolean): StorageFile?
    suspend fun historyFile(history: PlayHistoryEntity): StorageFile?
    suspend fun createPlayUrl(file: StorageFile): String?
    suspend fun cacheSubtitle(file: StorageFile): String?
    fun getNetworkHeaders(): Map<String, String>?
    fun supportSearch(): Boolean
    suspend fun search(keyword: String): List<StorageFile>
    suspend fun test(): Boolean
    fun updateFileHistory(file: StorageFile, history: PlayHistoryEntity?)
    fun close()
}
```

### StorageFile 接口

```kotlin
interface StorageFile {
    var storage: Storage
    var playHistory: PlayHistoryEntity?

    fun filePath(): String
    fun fileUrl(): String
    fun fileCover(): String?
    fun storagePath(): String
    fun isDirectory(): Boolean
    fun isFile(): Boolean
    fun fileName(): String
    fun fileLength(): Long
    fun uniqueKey(): String         // 基于 libraryId + fileUrl 的 MD5
    fun isRootFile(): Boolean
    fun canRead(): Boolean
    fun childFileCount(): Int
    fun <T> getFile(): T?
    fun clone(): StorageFile
    fun isVideoFile(): Boolean
    fun isAudioFile(): Boolean
    fun isImageFile(): Boolean
    fun isStoragePathParent(childPath: String): Boolean
    fun close()
    fun videoDuration(): Long
}
```

### AbstractStorage

提供默认实现和公共方法：
- 目录导航（`openDirectory`）
- 字幕自动匹配缓存（`cacheSubtitle`）
- 路径解析（`resolvePath`）
- 播放历史更新（`updateFileHistory`）
- 默认网络请求头（null）

### StorageFactory

```kotlin
fun createStorage(library: MediaLibraryEntity): Storage? {
    return when (library.mediaType) {
        MediaType.EXTERNAL_STORAGE -> DocumentFileStorage(library)
        MediaType.WEBDAV_SERVER -> WebDavStorage(library)
        MediaType.SMB_SERVER -> SmbStorage(library)
        MediaType.FTP_SERVER -> FtpStorage(library)
        MediaType.LOCAL_STORAGE -> VideoStorage(library)
        MediaType.REMOTE_STORAGE -> RemoteStorage(library)
        MediaType.MAGNET_LINK -> TorrentStorage(library)
        MediaType.STREAM_LINK -> LinkStorage(library)
        MediaType.ALSIT_STORAGE -> AlistStorage(library)
        else -> null
    }
}
```

---

## 字幕子系统

### 字幕格式支持

| 格式 | 解析器 | 说明 |
|------|--------|------|
| SRT | `FormatSRT.java` | 最通用的字幕格式 |
| ASS | `FormatASS.java` | Advanced SubStation Alpha，支持富样式 |
| SCC | `FormatSCC.java` | Scenarist Closed Caption |
| STL | `FormatSTL.java` | EBU STL 字幕格式 |
| TTML | `FormatTTML.java` | W3C Timed Text Markup Language (XML) |

### FormatFactory

```kotlin
fun findFormat(path: String): TimedTextFileFormat? {
    return when (getFileExtension(path).uppercase()) {
        "ASS" -> FormatASS()
        "SCC" -> FormatSCC()
        "SRT" -> FormatSRT()
        "STL" -> FormatSTL()
        "XML" -> FormatTTML()
        else -> null
    }
}
```

### 字幕数据模型

| 类 | 说明 |
|----|------|
| `TimedTextObject` | 完整的字幕对象，包含 Caption 列表和 Style 列表 |
| `Caption.java` | 单条字幕（时间戳 + 内容） |
| `Style.java` | 字幕样式定义 |
| `Time.java` | 时间戳对象 |

### SubtitleFinder（字幕智能匹配）

根据视频文件名和字幕优先级规则自动选择最合适的字幕文件：
1. 筛选以视频名为前缀的字幕文件
2. 根据配置的优先级规则排序（如 "chs", "eng" 等关键字）
3. 返回最匹配的字幕

### 字幕匹配服务

| 服务 | 说明 |
|------|------|
| `SubtitleSearchHelper.kt` | 字幕搜索辅助（集成多个源） |
| `SubtitleMatchHelper.kt` | 字幕匹配辅助 |
| `SubtitleHashUtils.kt` | 字幕文件哈希计算（用于射手网API） |

---

## 网络层

### API 端点

| 端点 | URL | 用途 |
|------|-----|------|
| DAN_DAN_OPEN | `https://api.dandanplay.net/` | 弹弹play 公开 API |
| DAN_DAN_SPARE | `http://139.217.235.62:16001/` | 弹弹play 备用 API |
| DAN_DAN_RES | `http://res.acplay.net/` | 弹弹play 资源 API |
| THUNDER_SUB | `http://sub.xmp.sandai.net:8000/subxl/` | 迅雷字幕 |
| SHOOTER_SUB | `https://www.shooter.cn/api/subapi.php/` | 射手网字幕 |
| ASSRT_SUB | `http://api.assrt.net/` | ASSRT 字幕 |

### OkHttp 拦截器详解

| 拦截器 | 功能 |
|--------|------|
| `SignatureInterceptor` | API 请求签名生成 |
| `AgentInterceptor` | 设置 User-Agent |
| `AuthInterceptor` | 添加认证 Token |
| `DecompressInterceptor` | 响应解压缩 |
| `BackupDomainInterceptor` | 主域名不可用时的自动切换 |
| `DynamicBaseUrlInterceptor` | 动态 Base URL 支持 |
| `LoggerInterceptor` | 网络请求日志 |
| `RedirectAuthorizationInterceptor` | 重定向时保持认证信息 |

### Repository 层

```
network/repository/
├── BaseRepository.kt        # Repository 基类
├── AnimeRepository.kt       # 番剧数据
├── MagnetRepository.kt      # 磁力链接搜索
├── RemoteRepository.kt      # 远程存储数据
├── AlistRepository.kt       # AList API 封装
├── UserRepository.kt        # 用户 API
├── OtherRepository.kt       # 其他 API
└── ResourceRepository.kt    # 资源管理
```

---

## 数据库

### 实体关系

```
media_library (媒体库配置)
  ├── id          (PRIMARY KEY)
  ├── url         (UNIQUE with media_type)
  ├── media_type  (UNIQUE with url)
  ├── name
  ├── remote_secret
  ├── web_dav_strict
  ├── screencast_address
  └── remote_anime_grouping

play_history (播放历史)
  ├── id          (PRIMARY KEY)
  ├── video_name
  ├── url
  ├── media_type
  ├── video_position
  ├── video_duration
  ├── play_time
  ├── danmu_path
  ├── episode_id  (TEXT)
  ├── subtitle_path
  ├── torrent_path
  ├── torrent_index
  ├── http_header
  ├── unique_key  (UNIQUE with storage_id)
  ├── storage_path
  ├── storage_id  (UNIQUE with unique_key)
  ├── is_last_play
  └── audio_path
```

---

## 配置系统

配置系统基于腾讯 MMKV 键值存储框架，通过编译时的注解处理器生成对应的配置类。

### 配置表清单

| 配置表 | 生成类 | 关键配置项 |
|--------|--------|-----------|
| `PlayerConfigTable` | `PlayerConfig` | 播放器类型、硬解码、像素格式、缓存大小 |
| `AppConfigTable` | `AppConfig` | 应用通用设置 |
| `DanmuConfigTable` | `DanmuConfig` | 弹幕相关设置 |
| `SubtitleConfigTable` | `SubtitleConfig` | 字幕字体大小、颜色、描边、优先级 |
| `UserConfigTable` | `UserConfig` | 用户信息 |
| `DatabaseConfigTable` | `DatabaseConfig` | 数据库配置 |
| `DownloadConfigTable` | `DownloadConfig` | 下载设置 |
| `ThumbnailConfigTable` | `ThumbnailConfig` | 缩略图生成设置 |

---

## 依赖关系

### 内部模块依赖

```gradle
// app/build.gradle.kts
implementation(project(":common_component"))
implementation(project(":player_component"))
implementation(project(":user_component"))
implementation(project(":local_component"))
implementation(project(":storage_component"))
```

### 主要外部依赖

| 组 | 库 | 版本 | 用途 |
|----|----|------|------|
| Kotlin | kotlin-stdlib | 1.7.21 | 开发语言 |
| | kotlinx-coroutines | 1.6.4 | 异步协程 |
| AndroidX | core-ktx | 1.9.0 | Android 核心库 |
| | appcompat | 1.5.1 | 兼容组件 |
| | lifecycle-viewmodel | 2.5.1 | MVVM 架构 |
| | room-runtime | 2.4.3 | 数据库 |
| | paging-runtime | 3.1.1 | 分页加载 |
| | recyclerview | 1.2.1 | RecyclerView |
| | constraintlayout | 2.1.4 | 约束布局 |
| Google | material | 1.8.0-alpha01 | Material Design |
| | exoplayer | 2.18.1 | 视频播放 |
| Square | retrofit | 2.9.0 | 网络请求 |
| | moshi | 1.14.0 | JSON 解析 |
| Alibaba | arouter-api | 1.5.2 | 页面路由 |
| Tencent | mmkv-static | 1.2.14 | 键值存储 |
| | bugly | 4.1.9 | 崩溃上报 |
| VLC | libvlc-all | 4.0.0-eap9 | VLC 播放引擎 |
| Coil | coil | 2.2.2 | 图片加载 |
| Banner | banner | 2.2.2 | 轮播图 |
| Apache | commons-net | 3.9.0 | FTP 协议 |
| NanoHttpd | nanohttpd | 2.3.1 | HTTP 代理服务 |
| SMBJ | smbj | 0.10.0 | SMB 协议 |
| Jsoup | jsoup | 1.15.3 | HTML 解析 |
| PhotoView | PhotoView | 2.3.0 | 图片缩放 |

### 本地 AAR 依赖

| AAR | 用途 |
|-----|------|
| immersion_bar.aar | 沉浸式状态栏 |
| panelSwitchHelper-androidx.aar | 面板切换辅助 |
| sevenzipjbinding4Android.aar | 7-Zip 解压 |
| thunder.aar | 迅雷下载引擎 |
| library-release.aar | 视频缓存 |

### 预编译 Native 库

```
libs/
├── arm64-v8a/
│   ├── libavcodec.so          # FFmpeg 解码
│   ├── libavutil.so           # FFmpeg 工具
│   ├── libffmpeg_jni.so       # FFmpeg JNI
│   ├── libijkffmpeg.so        # IJK FFmpeg
│   ├── libijkplayer.so        # IJK Player
│   ├── libijksdl.so           # IJK SDL
│   ├── libswresample.so       # FFmpeg 重采样
│   ├── libopen_cc.so          # OpenCC 简繁转换
│   └── libsecurity.so         # 安全库
└── armeabi-v7a/               # 同上，32位版本
```

---

## 构建与运行

### 环境要求

- Android Studio Arctic Fox (2020.3.1) 或更高版本
- JDK 11+
- Android SDK API 33
- Gradle 7.4.2
- Kotlin 1.7.21

### 构建命令

```bash
# 完整调试构建
./gradlew assembleDebug

# ABI 分包构建
./gradlew assembleArm64_v8aDebug
./gradlew assembleArmeabi_v7aDebug

# 发布构建
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

### Gradle 配置开关

位于 `gradle.properties`：

```properties
# 日志开关（修改后需重新构建）
IS_DEBUG_MODE=true/false

# 模块单独编译开关
IS_APPLICATION_RUN=true/false
```

### 构建变体

- **debug** - 调试版本，包含日志
- **release** - 发布版本，混淆优化
- **ABI 拆分**：armeabi-v7a、arm64-v8a、universal（全架构）

### 路由注册

页面路由使用 ARouter，需在编译时注册：

```kotlin
kapt {
    arguments {
        arg("AROUTER_MODULE_NAME", project.name)
    }
}
```

路由表定义在 [RouteTable.kt](file:///d:/project/NIplayer/NIplayer/common_component/src/main/java/com/xyoye/common_component/config/RouteTable.kt) 中，按功能分组：
- `RouteTable.Anime.*` - 番剧相关
- `RouteTable.Local.*` - 本地功能
- `RouteTable.User.*` - 用户设置
- `RouteTable.Player.*` - 播放器
- `RouteTable.Stream.*` - 存储流
- `RouteTable.ImageViewer.*` - 图片查看器

---

## 核心数据流

### 视频播放流程

```
用户操作 → VideoController
     → ControlWrapper
         ├── InterVideoPlayer (DanDanVideoPlayer)
         │   → AbstractVideoPlayer (Ijk/Exo/VLC)
         ├── InterSubtitleController (SubtitleController)
         └── InterSettingController (SettingController)

DanDanVideoPlayer
    → PlayerFactory.getFactory(playerType).createPlayer()
    → SurfaceFactory.getFactory().createRenderView()
    → mVideoPlayer.setDataSource(url, headers)
    → mVideoPlayer.prepareAsync()
    → mVideoPlayer.start()
```

### 文件浏览流程

```
StorageFileFragment
    → StorageFileViewModel
        → StorageFactory.createStorage(library)
            → Storage.openDirectory(file)
                → StorageFile list
                    → StorageFileAdapter 渲染
                        → ThumbnailGeneratorManager 缩略图
```

### 字幕匹配流程

```
SubtitleFinder.preferred(subtitles, videoName)
    → 筛选候选字幕（文件名以视频名开头）
    → 根据优先级规则匹配（配置的 SubtitlePriority）
    → 返回最佳匹配字幕文件
```

---

## 单元测试

项目各模块包含基本的单元测试：

| 模块 | 测试文件 |
|------|---------|
| `data_component` | `ExampleUnitTest.kt` |
| `local_component` | `ExampleUnitTest.java` |
| `user_component` | `ExampleUnitTest.java` |
| `storage_component` | `ExampleUnitTest.java` |

---

## LICENSE

本项目基于 [弹弹play (DanDanPlayForAndroid)](https://github.com/xyoye/DanDanPlayForAndroid) 二次开发，遵循原项目的开源许可证。

---

*最后更新：2026-05-16*
