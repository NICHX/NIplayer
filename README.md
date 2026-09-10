# 🎬 NIplayer v2

![Version](https://img.shields.io/badge/Version-2.5.7-blue)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF)
![UI](https://img.shields.io/badge/UI-Compose%20%2B%20Material%203-4285F4)
![Player](https://img.shields.io/badge/Player-Media3-1DB954)
![Android](https://img.shields.io/badge/Android-8.0%2B-orange)
![License](https://img.shields.io/badge/License-Apache%202.0-red)

> **一站式媒体播放体验** —— 统一访问本地 / SAF / SMB / WebDAV 四类存储，轻松播放视频、音频与图片，内置专业级播放控制、ASS 特效字幕引擎与 WebDAV 云同步，支持多语言与应用内在线更新。

---

## ✨ 亮点速览

| 领域 | 核心能力 |
|------|----------|
| 🎬 **视频播放** | 手势控制 · 倍速不变调 · A-B 循环 · 黑边检测 · 画中画 · 独立窗口转场 · 控制栏自定义 |
| 🎵 **音频播放** | 黑胶转盘 · LRC 歌词 · 均衡器 · 沉浸式横屏 · 通知栏后台播放 |
| 📝 **字幕系统** | ASS/SSA 特效自研引擎 · 样式自定义 · Assrt 在线搜索 |
| 💾 **多源存储** | 本地 / SAF / SMB / WebDAV 统一浏览与播放 · 远程文件管理 |
| 🔒 **文件夹加密** | 目录密码门禁 · 离开自动上锁 · 加密内容不记历史 |
| 🔄 **云同步** | WebDAV 播放历史同步 · 冲突处理 · 备份一键导入导出 |
| ⬇️ **下载与更新** | 断点续传 · 批量下载 · 应用内在线更新 |
| 🌍 **多语言** | 内置简体中文 / English，应用内一键切换 |
| 🎨 **个性化** | 多套预设配色 · 播放统计 · 视频书签 · 快捷访问 |
| 🧊 **液态玻璃 UI** | 真实背景模糊 · 悬浮玻璃导航/弹窗/底栏 · 通透度可调 |

---

## 📸 界面预览

**视频播放器 · 横屏沉浸回放**

<img width="760" src="screenshots/player.jpg" alt="视频播放器（横屏）"/>

| 首页 | 媒体库 | 主题设置 |
|:---:|:---:|:---:|
| <img width="230" src="screenshots/home.jpg" alt="首页"/> | <img width="230" src="screenshots/library.jpg" alt="媒体库"/> | <img width="230" src="screenshots/theme.jpg" alt="主题设置"/> |

| 黑胶唱片 · 音频 | LRC 歌词 | 设置 |
|:---:|:---:|:---:|
| <img width="230" src="screenshots/vinyl.jpg" alt="黑胶唱片"/> | <img width="230" src="screenshots/lyrics.jpg" alt="歌词"/> | <img width="230" src="screenshots/settings.jpg" alt="设置"/> |

> ⚠️ **版权免责声明**：截图中出现的影视、音乐及封面内容均为示例素材，仅用于展示本应用的功能与界面效果。所有影视作品、音乐、专辑封面之版权归各自版权方所有，请确保你拥有相应的播放与个人使用权限。

---

## 🛠 快速开始

```bash
# Debug 构建（使用项目内置 debug.keystore）
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest
```

> **注意**：FFmpeg 软解模块通过 CMake 编译，首次构建需要 NDK 与 CMake 环境。

### 📦 下载安装

正式版 APK 由 GitHub Actions 在打 tag 时自动构建并签名发布，可在 [Releases](https://github.com/nichx/NIplayer/releases) 页面下载（支持 Android 8.0+，覆盖安装保留历史数据）。

<details>
<summary><b>📦 技术概览</b>（点击展开）</summary>

| 类别 | 选型 |
|------|------|
| 支持版本 | Android 8.0（API 26）及以上 |
| 语言 | Kotlin + Coroutines |
| UI | Jetpack Compose (Material 3) |
| 播放器 | Media3 (ExoPlayer) + FFmpeg 软解扩展 |
| 数据 | Room + MMKV |
| 网络 | OkHttp / Retrofit / Moshi |
| 图片 | Coil 3 |
| DI | Hilt |
| 架构 | 多模块分层，播放内核统一抽象、存储协议可插拔 |

</details>

---

## License

Apache 2.0