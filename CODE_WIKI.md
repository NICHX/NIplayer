# NI Player 项目 Code Wiki

## 目录

1. [项目概述](#项目概述)
2. [项目架构](#项目架构)
3. [模块详情](#模块详情)
4. [核心功能](#核心功能)
5. [关键类与函数](#关键类与函数)
6. [依赖关系](#依赖关系)
7. [开发指南](#开发指南)
8. [运行方式](#运行方式)

---

## 项目概述

### 简介

NI Player 是一个基于弹弹play二次开发的本地多媒体播放器，专注于提供纯净、简洁的播放体验。支持视频、图片、音频播放，并具有丰富的字幕功能和多种存储源支持。

### 主要特性

- **视频播放**：双内核支持（IJKPlayer、ExoPlayer），适配常见视频格式
- **本地媒体库**：支持本地文件浏览和播放
- **服务器媒体库**：FTP、SMB、WebDAV、远程存储、AList
- **串流播放**：支持网络串流播放
- **磁链播放**：支持磁力链接播放
- **播放历史**：记录播放进度和历史
- **图片浏览**：支持常见图片格式，提供幻灯片和画廊视图
- **音频播放**：支持常见音频格式，独立音频播放界面
- **字幕功能**：自动匹配字幕、字幕搜索下载、字幕样式调整、外挂字幕

### 技术栈

- **语言**：Kotlin
- **架构**：MVVM + 组件化
- **最小SDK**：API 21 (Android 5.0)
- **目标SDK**：API 29 (Android 10)
- **编译SDK**：API 33 (Android 13)
- **包名**：com.nichx.niplayer

---

## 项目架构

### 架构设计

项目采用**组件化架构**，将不同功能模块化，提高代码复用性和可维护性。使用**MVVM**模式进行UI层与业务逻辑层的分离。

### 模块组织

```
NI Player/
├── app/                          # 应用入口模块
├── common_component/             # 公共组件模块
├── data_component/               # 数据模块
├── local_component/              # 本地模块
├── player_component/             # 播放器模块
├── storage_component/            # 存储模块
├── user_component/               # 用户模块
├── repository/                   # 第三方库封装
│   ├── immersion_bar/
│   ├── panel_switch/
│   ├── seven_zip/
│   ├── thunder/
│   └── video_cache/
├── buildSrc/                     # 构建配置
└── document/                     # 文档
```

### 依赖关系图

```
app
├── common_component
├── player_component
├── user_component
├── local_component
└── storage_component

common_component
├── data_component

player_component
├── common_component
└── data_component

user_component
├── common_component
└── data_component

local_component
├── common_component
└── data_component

storage_component
├── common_component
└── data_component
```

### 路由表

使用 Alibaba ARouter 进行页面路由，路由表定义在 `common_component/config/RouteTable.kt`。

主要路由路径：
- `RouteTable.Local.MediaFragment` - 媒体库页面
- `RouteTable.Local.MineFragment` - 本地页面
- `RouteTable.User.PersonalFragment` - 设置页面

---

## 模块详情

### 1. App 模块 (`app/`)

**职责**：应用入口模块，负责启动页、主界面框架、应用初始化

**关键文件**：
- [IApplication.kt](file:///d:/project/NIplayer/NIplayer/app/src/main/java/com/xyoye/dandanplay/app/IApplication.kt) - 应用初始化类
- [MainActivity.kt](file:///d:/project/NIplayer/NIplayer/app/src/main/java/com/xyoye/dandanplay/ui/main/MainActivity.kt) - 主界面
- [SplashActivity.kt](file:///d:/project/NIplayer/NIplayer/app/src/main/java/com/xyoye/dandanplay/ui/splash/SplashActivity.kt) - 启动页

**主要功能**：
- 初始化应用环境
- 底部导航栏管理（服务器、本地、设置三个页面）
- Fragment 切换与管理
- 双击返回键退出应用

### 2. Common Component 模块 (`common_component/`)

**职责**：公共组件模块，包含基类、通用工具、网络、数据库、存储等基础功能

**主要包结构**：
```
common_component/
├── base/                    # 基类（BaseActivity、BaseFragment、BaseViewModel）
├── utils/                   # 工具类集合
├── storage/                 # 存储抽象与实现
├── network/                 # 网络请求封装
├── database/                # 数据库相关
├── config/                  # 配置表
├── weight/                  # 自定义控件
├── extension/               # Kotlin 扩展函数
└── open_cc/                 # 简繁转换
```

**核心功能**：
- 各类工具类（文件、媒体、字幕、IO等）
- 存储抽象与实现（本地、FTP、SMB、WebDAV、AList等）
- 网络请求封装（Retrofit + OkHttp）
- 数据库管理（Room）
- 配置管理（MMKV）
- 简繁转换（OpenCC）

### 3. Data Component 模块 (`data_component/`)

**职责**：数据模块，包含数据实体类、枚举类等

**主要内容**：
- `bean/` - 数据Bean类
- `data/` - 数据模型
- `enums/` - 枚举类（播放状态、播放器类型、媒体类型等）
- `helper/` - 辅助类

**关键枚举**：
- [PlayState](file:///d:/project/NIplayer/NIplayer/data_component/src/main/java/com/xyoye/data_component/enums/PlayState.kt) - 播放状态
- [PlayerType](file:///d:/project/NIplayer/NIplayer/data_component/src/main/java/com/xyoye/data_component/enums/PlayerType.kt) - 播放器类型
- [MediaType](file:///d:/project/NIplayer/NIplayer/data_component/src/main/java/com/xyoye/data_component/enums/MediaType.kt) - 媒体类型
- [TrackType](file:///d:/project/NIplayer/NIplayer/data_component/src/main/java/com/xyoye/data_component/enums/TrackType.kt) - 轨道类型

### 4. Local Component 模块 (`local_component/`)

**职责**：本地数据模块，负责本地媒体库、播放历史、字幕下载等

**主要内容**：
- 本地媒体库浏览
- 播放历史管理
- 字幕搜索与下载
- 图片浏览（画廊、幻灯片）
- 音频播放

**关键文件**：
- 多个 Fragment 实现不同功能页面
- 对话框组件（磁链播放、字幕详情等）

### 5. Player Component 模块 (`player_component/`)

**职责**：播放器模块，核心播放功能实现

**主要包结构**：
```
player_component/
├── cache/                   # 视频缓存
├── player/                  # 播放器核心
│   ├── kernel/              # 播放器内核（IJK、Exo、VLC）
│   ├── controller/          # 播放控制器
│   ├── info/                # 播放器信息
│   ├── surface/             # 渲染视图
│   ├── utils/               # 工具类
│   └── wrapper/             # 包装类
├── subtitle/                # 字幕处理
│   ├── format/              # 字幕格式解析
│   └── info/                # 字幕信息
└── danmaku/                 # 弹幕（已移除）
```

**核心类**：
- [DanDanVideoPlayer.kt](file:///d:/project/NIplayer/NIplayer/player_component/src/main/java/com/xyoye/player/DanDanVideoPlayer.kt) - 主播放器类
- 多种字幕格式解析（ASS、SRT、SCC等）
- 音频焦点管理
- 播放进度记录

### 6. Storage Component 模块 (`storage_component/`)

**职责**：存储模块，负责各种存储源的管理

**主要功能**：
- 存储源管理（添加、编辑、删除）
- 远程文件浏览
- 存储类型支持：FTP、SMB、WebDAV、AList、远程存储、外部存储

**关键文件**：
- StorageFileActivity - 存储文件浏览
- 各类存储编辑对话框
- RadarScanView - 雷达扫描视图

### 7. User Component 模块 (`user_component/`)

**职责**：用户模块，负责用户信息、应用设置等

**主要功能**：
- 应用设置
- 播放器设置
- 字幕设置
- 缓存管理
- 扫描管理
- 常用文件夹
- 主题切换
- 许可证信息

### 8. Repository 模块 (`repository/`)

**职责**：第三方库封装，提供本地AAR依赖

**包含库**：
- `immersion_bar/` - 沉浸式状态栏
- `panel_switch/` - 面板切换
- `seven_zip/` - 7-Zip解压
- `thunder/` - 迅雷下载
- `video_cache/` - 视频缓存

---

## 核心功能

### 1. 视频播放

**双内核支持**：
- IJKPlayer（基于FFmpeg）
- ExoPlayer（Google官方）
- VLC（可选）

**播放功能**：
- 播放/暂停/停止
- 进度跳转
- 倍速播放
- 音量控制
- 屏幕缩放适配
- 音轨切换
- 字幕切换
- 循环播放
- 后台播放
- 播放进度记录

### 2. 存储源支持

**支持的存储类型**：
1. **本地存储** - 设备本地文件
2. **外部存储** - SD卡等
3. **FTP** - FTP服务器
4. **SMB** - 局域网共享
5. **WebDAV** - WebDAV协议
6. **AList** - AList网盘聚合
7. **远程存储** - 自定义远程
8. **磁链** - 磁力链接
9. **串流** - 网络串流

### 3. 字幕功能

**字幕格式支持**：
- SRT
- ASS
- SCC
- STL
- TTML

**字幕功能**：
- 自动匹配字幕
- 字幕搜索与下载
- 外挂字幕
- 字幕样式调整（大小、颜色、描边等）
- 字幕时间偏移
- 字幕编码识别

### 4. 图片浏览

**功能**：
- 画廊视图
- 幻灯片播放
- 图片缩放
- 图片切换动画

### 5. 音频播放

**功能**：
- 音频格式支持
- 独立播放界面
- 播放历史记录

---

## 关键类与函数

### 1. DanDanVideoPlayer

**位置**：[player_component/src/main/java/com/xyoye/player/DanDanVideoPlayer.kt](file:///d:/project/NIplayer/NIplayer/player_component/src/main/java/com/xyoye/player/DanDanVideoPlayer.kt)

**职责**：核心播放器类，整合播放器内核、渲染视图、控制器等

**主要方法**：

```kotlin
// 开始播放
fun start()

// 暂停播放
fun pause()

// 设置视频源
fun setVideoSource(source: BaseVideoSource)

// 设置控制器
fun setController(controller: VideoController?)

// 跳转进度
fun seekTo(timeMs: Long)

// 设置播放速度
fun setSpeed(speed: Float)

// 设置音量
fun setVolume(point: PointF)

// 设置屏幕缩放
fun setScreenScale(scaleType: VideoScreenScale)

// 记录播放信息
fun recordPlayInfo()

// 释放资源
fun release()
```

### 2. MainActivity

**位置**：[app/src/main/java/com/xyoye/dandanplay/ui/main/MainActivity.kt](file:///d:/project/NIplayer/NIplayer/app/src/main/java/com/xyoye/dandanplay/ui/main/MainActivity.kt)

**职责**：主界面，管理底部导航和Fragment切换

**主要方法**：

```kotlin
// 初始化视图
override fun initView()

// 切换Fragment
private fun switchFragment(tag: String)

// 添加Fragment
private fun addFragment(fragment: Fragment, tag: String)

// 获取Fragment（通过ARouter）
private fun getFragment(path: String): Fragment?

// 返回键处理
override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean
```

### 3. Storage 抽象

**位置**：[common_component/src/main/java/com/xyoye/common_component/storage/Storage.kt](file:///d:/project/NIplayer/NIplayer/common_component/src/main/java/com/xyoye/common_component/storage/Storage.kt)

**职责**：存储源抽象接口

**实现类**：
- `VideoStorage` - 本地视频存储
- `DocumentFileStorage` - 文档存储
- `FtpStorage` - FTP存储
- `SmbStorage` - SMB存储
- `WebDavStorage` - WebDAV存储
- `AlistStorage` - AList存储
- `RemoteStorage` - 远程存储
- `LinkStorage` - 链接存储
- `TorrentStorage` - 磁链存储

### 4. BaseVideoSource

**位置**：[common_component/src/main/java/com/xyoye/common_component/source/base/BaseVideoSource.kt](file:///d:/project/NIplayer/NIplayer/common_component/src/main/java/com/xyoye/common_component/source/base/BaseVideoSource.kt)

**职责**：视频源抽象基类

**主要实现**：
- `StorageVideoSource` - 存储视频源

### 5. 数据库相关

**DatabaseManager**
**位置**：[common_component/src/main/java/com/xyoye/common_component/database/DatabaseManager.kt](file:///d:/project/NIplayer/NIplayer/common_component/src/main/java/com/xyoye/common_component/database/DatabaseManager.kt)

**职责**：数据库管理，使用Room数据库

**主要DAO**：
- `VideoDao` - 视频数据
- `PlayHistoryDao` - 播放历史
- `MediaLibraryDao` - 媒体库
- `DanmuBlockDao` - 弹幕屏蔽
- 等等

### 6. 配置管理

**配置表**：
- `AppConfigTable` - 应用配置
- `PlayerConfigTable` - 播放器配置
- `DanmuConfigTable` - 弹幕配置
- `SubtitleConfigTable` - 字幕配置
- `UserConfigTable` - 用户配置
- `DatabaseConfigTable` - 数据库配置
- `DownloadConfigTable` - 下载配置
- `ThumbnailConfigTable` - 缩略图配置

**实现方式**：使用腾讯MMKV进行高性能键值存储

### 7. 网络请求

**Retrofit封装**
**位置**：[common_component/src/main/java/com/xyoye/common_component/network/Retrofit.kt](file:///d:/project/NIplayer/NIplayer/common_component/src/main/java/com/xyoye/common_component/network/Retrofit.kt)

**主要Service**：
- `DanDanService` - 弹弹play相关API
- `RemoteService` - 远程服务API
- `MagnetService` - 磁链服务API
- `AlistService` - AList API
- `ExtendedService` - 扩展服务API

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

| 库名 | 版本 | 用途 |
|-----|------|-----|
| Kotlin | 1.7.21 | 开发语言 |
| Kotlin Coroutines | 1.6.4 | 协程 |
| AndroidX Core | latest | Android核心库 |
| AndroidX Lifecycle | 2.5.1 | 生命周期 |
| AndroidX Room | 2.4.3 | 数据库 |
| AndroidX Paging | 3.x | 分页加载 |
| Material Components | latest | Material设计 |
| ARouter | 1.5.2 | 路由 |
| Retrofit | 2.9.0 | 网络请求 |
| Moshi | 1.14.0 | JSON解析 |
| ExoPlayer | 2.18.1 | 视频播放 |
| Coil | 2.2.2 | 图片加载 |
| Banner | 2.2.2 | 轮播图 |
| MMKV | 1.2.14 | 键值存储 |
| VLC | 4.0.0-eap9 | 视频播放 |
| Commons Net | 3.9.0 | FTP支持 |
| Smbj | latest | SMB支持 |
| NanoHttpd | 2.3.1 | 本地HTTP服务 |
| JUniversalChardet | 2.4.0 | 编码识别 |
| Jsoup | 1.15.3 | HTML解析 |
| PhotoView | 2.3.0 | 图片缩放 |

### 播放器内核

- **IJKPlayer** - 基于FFmpeg，支持格式多
- **ExoPlayer** - Google官方，兼容性好
- **VLC** - 可选，功能强大

### 本地AAR依赖

- immersion_bar.aar - 沉浸式状态栏
- panelSwitchHelper-androidx.aar - 面板切换
- sevenzipjbinding4Android.aar - 7-Zip解压
- thunder.aar - 迅雷下载
- library-release.aar - 视频缓存

---

## 开发指南

### 项目配置

**gradle.properties 配置项**：
```properties
# 日志开关（修改后需重新构建）
IS_DEBUG_MODE=true/false

# 模块单独编译开关
IS_APPLICATION_RUN=true/false
```

### 构建脚本

**buildSrc** 目录包含构建配置：
- [Dependencies.kt](file:///d:/project/NIplayer/NIplayer/buildSrc/src/main/java/Dependencies.kt) - 依赖版本管理
- [Versions.kt](file:///d:/project/NIplayer/NIplayer/buildSrc/src/main/java/Versions.kt) - 版本号管理

### 添加新的存储源

1. 在 `common_component/storage/` 下创建新的存储实现类，继承 `AbstractStorage`
2. 在 `StorageFactory` 中注册新的存储类型
3. 在 `storage_component` 中添加相应的UI（如果需要）

### 添加新的播放器内核

1. 在 `player_component/player/kernel/` 下创建新的内核实现
2. 实现 `AbstractVideoPlayer` 接口
3. 在 `PlayerFactory` 中注册新的播放器类型

### 代码规范

- 使用 Kotlin 语言
- 遵循 MVVM 架构
- 页面路由使用 ARouter
- 数据存储使用 MMKV 或 Room
- 网络请求使用 Retrofit + Kotlin Coroutines
- 日志使用 DDLog

---

## 运行方式

### 环境要求

- Android Studio Arctic Fox 或更高版本
- JDK 11 或更高版本
- Android SDK API 33
- Gradle 7.x

### 编译步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd NIplayer
```

2. **打开项目**
使用 Android Studio 打开项目根目录

3. **同步 Gradle**
等待 Gradle 同步完成，下载依赖

4. **连接设备或启动模拟器**
确保设备已连接或模拟器已启动

5. **构建并运行**
点击 Android Studio 的 Run 按钮，或使用命令：
```bash
./gradlew assembleDebug
./gradlew installDebug
```

### 构建变体

- **debug** - 调试版本
- **release** - 发布版本
- 按 ABI 拆分：armeabi-v7a、arm64-v8a、universal

### GitHub Actions 工作流

项目配置了两个 GitHub Actions 工作流：
- `package-beta.yml` - Beta 版本打包
- `package-release.yml` - Release 版本打包

---

## 许可证

本项目基于弹弹play二次开发，遵循原项目的许可证。

---

## 致谢

- [弹弹play](https://github.com/xyoye/DanDanPlayForAndroid) - 原始项目
- 所有第三方库的贡献者

---

*最后更新：2026-05-16*
