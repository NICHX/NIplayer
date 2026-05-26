# NI Player

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="NI Player Logo" width="120">
</p>

<h3 align="center">纯净的多媒体播放器</h3>

## 📱 简介

NI Player 是一个本地多媒体播放器，支持视频、图片、音频播放，基于弹弹play二次开发，致力于提供纯净、简洁的播放体验。

## ⬇️ 下载

安卓平台：

[Release 页面](https://github.com/NICHX/NIplayer/releases)

## ✨ 功能介绍

### 视频播放
- 🎬 三内核支持（IJKPlayer、ExoPlayer、VLC），适配常见视频格式
- 📁 本地媒体库
- 🌐 服务器媒体库（FTP、SMB、WebDAV、远程存储、AList）
- 📋 播放历史
- 🔒 播放器锁屏、浮窗播放

### 图片浏览
- 🖼️ 支持常见图片格式
- 🔄 幻灯片播放
- 🌓 画廊视图

### 音频播放
- 🎵 支持常见音频格式
- 🎨 音频播放界面
- 📋 播放列表管理（列表循环、单曲循环、随机播放）
- 📡 通知栏媒体控制（上一曲/下一曲/播放暂停）

### 字幕功能
- 🔍 支持根据视频自动匹配字幕
- 📥 支持字幕搜索、下载
- 🎨 支持字幕样式调整（大小、描边、颜色等）
- 📝 支持外挂字幕
- ⏱️ 支持字幕时间偏移调整
- 📂 支持多种字幕格式（SRT、ASS、TTML、STL、SCC）

## 🏗️ 项目架构

使用 Kotlin + MVVM + 组件化方案实现

## 📦 模块介绍

| 模块     | 说明                             |
| ------ | ------------------------------ |
| APP    | 项目入口，包含启动页及主框架                 |
| Local  | 本地数据模块，包含本地视频、字幕下载             |
| User   | 用户模块，包含应用设置等              |
| Player | 播放器模块（视频/音频播放、字幕）            |
| Common | 基础模块，包括基类、通用组件、工具类、存储源实现等      |
| Data   | 数据模块，包含普通Bean类、数据库Entity类、枚举类等 |
| Storage| 存储源模块，包含存储管理与文件浏览              |

## ⚙️ 项目配置

1. **日志开关**：根目录下 `gradle.properties` 文件，配置 `IS_DEBUG_MODE`，修改后 rebuild project

2. **单独编译模块**：根目录下 `gradle.properties` 文件，配置 `IS_APPLICATION_RUN`，设置 `true` 代表模块以应用类型编译，修改后 rebuild project

## 🔄 修改说明

此版本基于弹弹play进行了以下修改：
- ✅ 新增"本地"页面，整合本地媒体库和设备存储库
- ✅ 重构导航栏，分为"服务器"、"本地"、"设置"三个页面
- ✅ 新增图片浏览功能（支持幻灯片、画廊视图）
- ✅ 新增音频播放功能
- ✅ 新增通知栏媒体控制

## 📸 截图

<p align="center">
  <img width="200" alt="截图1" src="https://github.com/user-attachments/assets/0058d766-a555-48f1-be66-b86b2a24b02c" />
  <img width="200" alt="截图2" src="https://github.com/user-attachments/assets/94bfa284-4f9a-4722-8914-31cd2edb6fc2" />
  <img width="200" alt="截图4" src="https://github.com/user-attachments/assets/149af001-8fcd-40b4-aa2c-3aa3ced810f5" />
  <img width="200" alt="截图5" src="https://github.com/user-attachments/assets/8da41d08-c9c8-49ad-9340-56ca9f3f6631" />
</p>

<p align="center">
  <img width="420" alt="横屏截图" src="https://github.com/user-attachments/assets/03c17392-e7ce-4583-82b1-8bc745c7563c" />
</p>

<p align="center">
  <img width="200" alt="截图6" src="https://github.com/user-attachments/assets/64ee5801-52e1-4478-a1fb-c74a1c1e983b" />
  <img width="200" alt="截图7" src="https://github.com/user-attachments/assets/8f03f3cb-fbcf-47a4-84ab-4024e1c53fcb" />
  <img width="200" alt="截图8" src="https://github.com/user-attachments/assets/4c3df55f-7176-4ef3-9a0d-6b3b97ac2854" />
</p>


## 📄 许可证

本项目基于弹弹play二次开发，遵循原项目的许可证。

## 🙏 致谢

- [弹弹play](https://github.com/xyoye/DanDanPlayForAndroid) - 原始项目

---

<p align="center">
Made with ❤️ by NICHX
</p>
