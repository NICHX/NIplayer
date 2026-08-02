# NIplayer v2

> 一款 Android 全能媒体播放器。统一访问本地 / SAF / SMB / WebDAV 四类存储，一站式播放视频、音频与图片，内置专业级播放控制、ASS 特效字幕引擎与后台音频服务。完全重构自 [NIplayer v1.x](https://github.com/nichx/NIplayer)。

## 功能特性

### 📺 视频播放

- **手势操控**：左半屏上下滑调亮度、右半屏上下滑调音量、横向滑动精确定位进度；单击显示 / 隐藏控制栏，双击快进 / 快退 10 秒
- **倍速播放**：支持多档倍速；长按画面临时倍速、拖到底部锁定常驻；音调修正，变速不变调
- **A-B 循环**：任意设置起止点自动循环，一键清除
- **智能黑边检测**：自动识别画面有效区域，Fit / Crop / Stretch 三种缩放模式
- **睡眠定时**：15 / 30 / 60 / 90 / 120 分钟倒计时，到点自动暂停
- **截图**：一键截取当前画面保存至相册
- **画中画**：一键进入小窗，后台继续播放
- **键盘 / 遥控器快捷键**：空格播放暂停、方向键快进快退、数字键按百分比跳转等
- **音轨 / 字幕轨切换**，字幕毫秒级延迟微调
- **播放列表连播**：自动构建同目录播放列表，顺序 / 随机 / 单曲循环
- **断点续播**：再次打开弹窗"接着上次看"
- **媒体信息**：编码 / 分辨率 / 码率 / 帧率 / HDR 格式
- **锁屏防误触**：画面锁定后隐藏控制栏、禁用手势

### 🎵 音频播放

- **黑胶转盘界面**：唱片旋转 + 唱针动画，中央展示专辑封面，模糊封面背景
- **LRC 歌词**：逐行高亮自动滚动，点击任意行跳转；本地歌词优先、在线歌词兜底
- **均衡器**：内置多套预设 + 自定义频段调节
- **后台播放**：前台服务 + 通知栏控制，支持锁屏 / 蓝牙耳机按键，拔耳机自动暂停
- **迷你播放条**：非播放页底部常驻，随时切歌
- **在线元数据**：可配置音乐元数据 API，自动获取在线歌词与封面

### 📝 字幕

- **ASS / SSA 特效字幕自研引擎**：完整支持淡入淡出、移动、旋转、定位等动画
- **多格式**：ASS / SSA / SRT / TTML，多字幕轨切换
- **样式自定义**：字体 / 字号 / 颜色 / 描边 / 阴影 / 边距
- **在线搜索**：集成 Assrt 字幕库，搜索 → 下载 → 一键应用
- **自动加载**：自动匹配同目录同名字幕，可配置语言优先级

### 💾 存储与媒体库

- **四类存储**：本地 / SAF / SMB / WebDAV，统一文件浏览与播放
- **远程文件管理**：SMB / WebDAV 支持重命名、移动、新建文件夹、删除
- **文件浏览**：目录导航、排序、列表 / 网格视图、媒体类型过滤、递归搜索
- **本地扫描**：MediaStore + 扩展目录合并，增量去重
- **播放历史**：按日期分组，视频 / 音频筛选，点击续播
- **快速访问**：书签式收藏，长按拖拽排序
- **图片查看器**：双指缩放（最高 5x）、双击切换、多图左右翻页，支持四类存储加载
- **连接检测**：远程存储自动心跳检测，不可达时降透明度提示

### ⬇️ 下载与备份

- **下载管理**：多任务并发、断点续传、实时进度 / 速度 / 剩余时间
- **数据备份**：数据库 JSON 导出 / 导入，换机迁移

### 🎨 个性化

- **主题**：浅色 / 暗色 / 跟随系统，Material 3 动态取色（Android 12+）
- **播放统计**：观看次数 / 时长、媒体类型与存储分布、Top 观看榜单
- **视频书签**：时间轴打点标记，快速跳转

### 🔒 安全

- 存储源密码使用 Android Keystore AES-256-GCM 加密存储
- 全局崩溃捕获，自动记录崩溃日志

## 快速开始

```bash
# Debug 构建（使用项目内置 debug.keystore）
./gradlew assembleDebug
```

> 注意：FFmpeg 软解模块通过 CMake 编译，首次构建需要 NDK 环境。

## 技术概览

Kotlin + Jetpack Compose (Material 3) + Media3 (ExoPlayer) + FFmpeg 软解扩展，Room 数据库 + MMKV 存储，Hilt 依赖注入。多模块分层架构，播放内核统一抽象、存储协议可插拔，具体设计详见 [docs/FEATURE_DESIGN.md](docs/FEATURE_DESIGN.md)。

## CI 与发布

基于 GitHub Actions，仓库根目录 `.github/workflows/` 下包含两个流水线：

### CI（`ci.yml`）
- 触发：PR 到 `main`、推送 `main`
- 任务：
  - `build-and-test`：JDK 17 下运行单元测试 + 构建 Debug APK，上传 APK 与测试报告
  - `lint`：JDK 21 下运行 lint（AGP 9.3.0 的 lint 工具需 JDK 21，见 `app/build.gradle.kts` 内注释），上传 lint 报告

### Release（`release.yml`）
- 触发：推送 `v*` tag（如 `v2.0.0`）
- 流程：单元测试 → 构建签名 Release APK + AAB → 上传构建产物 → 自动创建 GitHub Release

### 正式版签名

签名配置读取顺序：**CI 环境变量 > 本地 `keystore.properties`**（均已被 `.gitignore` 忽略，keystore 文件不入库）。

1. 生成正式签名文件（一次性）：
   ```bash
   keytool -genkey -v -keystore release.keystore -alias niplayer -keyalg RSA -keysize 2048 -validity 10000
   ```
2. 在 GitHub 仓库 Settings → Secrets and variables → Actions 配置 4 个 Secrets：
   - `RELEASE_KEYSTORE`：keystore 文件的 base64 内容（`base64 -i release.keystore`）
   - `RELEASE_STORE_PASSWORD`：keystore 密码
   - `RELEASE_KEY_ALIAS`：别名（如 `niplayer`）
   - `RELEASE_KEY_PASSWORD`：密钥密码
3. 本地构建可选：在项目根目录创建 `keystore.properties`：
   ```properties
   storeFile=../release.keystore
   storePassword=xxxx
   keyAlias=niplayer
   keyPassword=xxxx
   ```

未配置签名信息时，`assembleRelease` 产出未签名 APK（仅用于验证），发布时会在 CI 日志中给出警告。

## License

Apache 2.0
