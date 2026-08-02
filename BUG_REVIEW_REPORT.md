# NIplayer v2 代码审查与 Bug 报告

> 审查范围：`app/`、`core/`(common, database, datastore, designsystem, network, storage, subtitle, thumbnail)、`feature/`(home, player)、`player/kernel/`、`core/navigation/` 等模块的 Kotlin 源码（排除 `build/generated` 产物与 `player/ffmpeg` 第三方代码）。
>
> 审查日期：2026-07-31
>
> 审查方式：四个并行子代理分模块全量阅读源码，仅记录有代码证据的真实缺陷，排除风格类问题。
>
> **核实状态（2026-07-31 第二轮）**：已对全部 8 个 P0 级 bug + 6 个代表性 Medium/Low bug 逐行回读源码核实，**全部确认属实**。其中 K2 根因描述已修正（见下文）。

## 概览汇总

| 严重度 | 数量 |
| --- | --- |
| High | 9 |
| Medium | 16 |
| Low | 12 |
| **总计** | **37** |

按模块分布：

- `core/storage`：12 条（High 3 / Medium 6 / Low 3）
- `feature/player`：12 条（High 2 / Medium 6 / Low 4）
- `feature/home`：14 条（High 1 / Medium 3 / Low 10 → 其中部分标 Low/Medium）
- `core/database` + `core/network` + `core/subtitle` + `player/kernel`：5 条（High 3 / Medium 1 / Low 1）

> 注：以下"行号"以审查时仓库当前源码为准；如文件后续被改动，行号可能漂移，请以文件链接位置为准。

---

## 一、core/storage 模块

### S1 [High] DownloadManager：打开输入流异常时任务卡在 DOWNLOADING 且资源泄漏
- 文件：`core/storage/src/main/java/com/nichx/niplayer/storage/download/DownloadManager.kt`
- 行号：`268`、`272-286`、`288`、`304-307`
- 类别：错误处理缺失 + 资源泄漏
- 描述：第 268 行已置状态为 `DOWNLOADING`，但 `inputStream` 的获取（272-286）位于 `try { ... }`（288 起）**之外**。若 `storage.openInputStream(storageFile)`（282/285）抛出非取消异常，异常会穿透 `processTask`：既不进入 catch（状态不会变 `FAILED`），也不进入 finally（`inputStream.close()`/`storage.close()` 不执行）。结果：① 任务永久停留 `DOWNLOADING`，调度只取 `WAITING`，该任务再不重启；② `storage` 及底层 SMB/OkHttp 连接泄漏。
- 修复建议：将 `inputStream` 获取移入 `try` 块；或整体自 268 行起用 try/catch/finally 包裹，失败时 `updateState(FAILED)` 并 `storage.close()`。

### S2 [High] DownloadManager：SAF 模式 offset==0 时以 append 写入，旧文件内容残留导致下载损坏
- 文件：`core/.../storage/download/DownloadManager.kt`
- 行号：`366-390`（尤其 `375-382`）
- 类别：逻辑错误 / 数据损坏
- 描述：`processToSaf` 中 `treeDoc.findFile(fileName) ?: treeDoc.createFile(...)`（375-377）在 `offset == 0`（全新下载或 `retryTask` 重置 `downloadedBytes=0`）时**未删除**已存在旧文件，随后以 `"wa"`（`O_WRONLY|O_APPEND`，不截断）打开（379-382），新内容追加到旧文件尾部。`processToCache`/`processToDirectPath` 在 `offset==0` 均先 `delete()` 再 `createNewFile()`，SAF 分支遗漏了这一步。
- 修复建议：`offset == 0` 且命中已存在 `targetDoc` 时，先 `targetDoc.delete()` 再 `createFile(...)`；或改用 `"rwt"` 截断模式。

### S3 [High] SmbStorage.createDirectory 任何异常均返回 true（误报目录已创建 + 吞 CancellationException）
- 文件：`core/.../storage/impl/SmbStorage.kt`
- 行号：`411-420`（尤其 `417-419`）
- 类别：错误处理 / 取消语义被破坏
- 描述：`catch (e: Exception) { true }` 把"权限不足 / share 不可达 / `CancellationException`"全部当作创建成功返回 `true`。调用方（如 ThumbnailManager 创建 `.thumb/`）误以为目录已建，后续 PUT 缩略图全失败；且 `CancellationException` 被吞，结构化并发被破坏。
- 修复建议：仅当"目录已存在（403/405）"时返回 `true`，其它失败返回 `false`；单独 `catch (CancellationException) { throw e }` rethrow。

### S4 [Medium] SmbParallelInputStreamWrapper.close 移除的是自身而非已加入列表的 delegate，导致 activeStreams 无限增长
- 文件：`core/.../storage/impl/SmbStorage.kt`
- 行号：移除 `566-569`；添加 `279-281`；`openInputStream` `260-271`
- 类别：内存/资源泄漏
- 描述：`openInputStreamInternal` 中 `activeStreams.add(stream)` 添加的是 **delegate（`SmbParallelInputStream`）**，而 wrapper 的 `close` 执行的是 `activeStreams.remove(this)`，`this` 是 wrapper，与列表元素既非同一对象也不 `equals`，`remove` 永远失败。每次开流都使 `activeStreams` 增长一项且永不缩减（直至 `SmbStorage.close()` 清空），已关闭的 `SmbParallelInputStream` 及引用被长期持有无法 GC。
- 修复建议：改为 `activeStreams.remove(delegate)`。

### S5 [Medium] SmbStorage.shareRootPrefix / playRootPrefix 线程安全问题
- 文件：`core/.../storage/impl/SmbStorage.kt`
- 行号：声明 `124-125`；读取 `134`（`buildSmbUrl`）；无锁写入 `181/239/355/393`
- 类别：可见性 / 竞态
- 描述：两个字段为普通 `var`，非 `@Volatile` 也不在所有写入点加锁。`ensureShare()` 在 `connectMutex` 内初始化是安全的，但 `buildSmbUrl`（134）无锁读取，各重试 catch（181/239/355/393）无锁置空。SMP 上其他线程可能读到陈旧 `""`，拼出错误 URL；重连竞态下可能拼出/share 重叠路径。
- 修复建议：声明为 `@Volatile`，所有写入通过 `connectMutex.withLock { ... }`；或迁移到一次性不可变结果。

### S6 [Medium] WebDavStorage.saveFile 重试路径吞掉 CancellationException
- 文件：`core/.../storage/impl/WebDavStorage.kt`
- 行号：`270-285`（尤其 `280-283`）
- 类别：取消了协程取消语义
- 描述：内层 `catch (e2: Exception) { ...; false }` 捕获 `CancellationException`（继承自 `IllegalStateException`），协程在重试中被取消时取消信号被吞，返回 `false` 而非抛出。
- 修复建议：先 `catch (e2: CancellationException) { throw e2 }`，再 catch 其它；或用 `currentCoroutineContext().ensureActive()`。

### S7 [Medium] WebDavStorage.createDirectory 用 runCatching 包裹 suspend 调用，吞掉取消
- 文件：`core/.../storage/impl/WebDavStorage.kt`
- 行号：`287-292`（尤其 `289`）
- 类别：取消了协程取消语义
- 描述：`runCatching { fileExists(path) }` 包裹 suspend 函数 `fileExists`，会捕获 `CancellationException`，将其静默转为 `getOrDefault(false)` 后继续执行 MKCOL 流程，取消语义丢失。
- 修复建议：改用显式 try/catch，单独 rethrow `CancellationException`。

### S8 [Medium] PrefetchInputStream 后台预读 IOException 不置 error/eof，消费端可能永久阻塞
- 文件：`core/.../storage/datasource/PrefetchInputStream.kt`
- 行号：`90-104`（尤其 `93-96`）
- 类别：错误处理缺失 / 潜在死锁
- 描述：`doPrefetch` 中 `catch (_: IOException)` 什么都不做，既未置 `error` 也未置 `eof`，线程直接退出。`read()`（140/164）的等待条件为 `while (availableBytes == 0 && !eof && error == null) { lock.wait() }`；当缓冲排空而预读线程因瞬时 IO 异常（非 close 触发）退出时，消费线程将永久 `wait()` 造成 hang。注释声称该分支用于"source 被并发关闭"，但未覆盖瞬时网络 IO 异常情景。
- 修复建议：`catch (e: IOException)` 中同样 `synchronized(lock) { error = e; lock.notifyAll() }`，让消费端感知并抛出；仅对确认"被关闭"导致的特定异常静默。

### S9 [Medium] DownloadManager：多协程并发对 `_taskProgress` 做 read-modify-write 竞态
- 文件：`core/.../storage/download/DownloadManager.kt`
- 行号：`424-429`；以及 `306`
- 类别：并发竞态
- 描述：`_taskProgress.value = _taskProgress.value.toMutableMap().apply { this[taskId] = totalRead }` 为读-改-写，多个并发下载协程同时执行会相互覆盖，导致部分任务实时进度丢失；`finally` 中 `remove`（306）同理。
- 修复建议：用 `_taskProgress.update { it.toMutableMap().apply { ... } }`（CAS 循环，原子），或 `ConcurrentHashMap` 自管理 + 单独 StateFlow。

### S10 [Low] WebDavStorage listCache / listCacheTimestamps 永不清除，随目录导航无限增长
- 文件：`core/.../storage/impl/WebDavStorage.kt`
- 行号：`107-108`（写入 `142-143`）
- 类别：内存泄漏
- 描述：两份 `ConcurrentHashMap` 缓存只在命中时覆盖，从不淘汰。浏览大量不同目录后持续累积 `StorageFile` 列表（含等价解码路径），长期内存占用增长。
- 修复建议：改用单 `LinkedHashMap` LRU 加 TTL，或定期清理过期项，`close()` 时清空。

### S11 [Low] SmbStorage.testConnection 无 share 配置时未真正探测即返回 true
- 文件：`core/.../storage/impl/SmbStorage.kt`
- 行号：`444-468`（尤其 `463-467`）；`ping()` `434-438`
- 类别：逻辑错误
- 描述：`shareName` 为空时仅 `SmbFile(testUrl, smbContext)` 构造（构造不发网络请求）后直接 `return true`，即便服务器不可达也"连接成功"。`ping()` 同问题。
- 修复建议：无 share 时做一次轻量探测（如 `smbFile.list()` 或 `exists()`），失败返回 `false`。

### S12 [Low] WebDavStorage.openMediaDataSource 直接透传 file.length，长度 0 时 MediaDataSource.getSize 返回 0
- 文件：`core/.../storage/impl/WebDavStorage.kt#L198-L200` 与 `core/.../storage/impl/WebDavMediaDataSource.kt#L337`
- 类别：MediaDataSource 边界实现
- 描述：`openMediaDataSource` 将 `file.length` 直接作为 `fileSize` 传给 `WebDavMediaDataSource`，`getSize()` 直接返回该值。当 PROPFIND 未返回 `getcontentlength` 时 `length==0`，`MediaMetadataRetriever` 通常视为空媒体放弃取帧。对比 `SmbStorage.openMediaDataSourceInternal` 显式 `if (file.length <= 0) return null` 保护，WebDAV 路径无保护。
- 修复建议：`openMediaDataSource` 中 `if (file.length <= 0) return null`，或 `getSize` 对 0 回退 `C.LENGTH_UNSET` 并在 readAt 按未知长度处理。

---

## 二、feature/player 模块

### P1 [High] 后台音频自动切歌失效 + PlayerViewModel 内存泄漏
- 文件：`feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt`
- 行号：`init` `681-686`；`onCleared` `1753-1848`
- 类别：协程作用域生命周期 / 单例回调泄漏 / 功能失效
- 描述：`init` 把 `audioPlaybackManager.onPlayNextRequest / onPlayPreviousRequest / onPlaybackError` 设为捕获了 `this` 的 lambda。`AudioPlaybackManager` 为 `@Singleton`，而 `PlayerViewModel` 经 `hiltViewModel()` 绑定到 `AudioPlayerScreen` 的 `NavBackStackEntry`。用户 onBack 离开音频页时该 Entry 销毁触发 `onCleared`，`viewModelScope` 被取消，但回调仍持有旧 VM（已取消作用域）。当前曲目播完时 `STATE_ENDED → playNext() → requestNext() → onPlayNextRequest()` 进入旧 VM 的 `playNext() → playAtIndex()`，其中 `viewModelScope.launch { ... }` 因作用域已取消不执行，导致后台音频无法自动切下一首并停在结尾；同时单例长期持有旧 VM 造成内存泄漏。
- 修复建议：`onCleared` 中将上述三回调置 `null`；或将"切歌源重建"逻辑下沉到 `AudioPlaybackManager`/Service 自身，不依赖易被销毁的 ViewModel。

### P2 [High] 音频播放进度永远无法更新/续播
- 文件：`feature/.../feature/player/PlayerViewModel.kt`
- 行号：`saveProgress` `1667-1676`；`saveProgressSync` `1689-1704`；`onCleared` `1754-1755`、`1789-1796`
- 类别：进度落盘读取错误源
- 描述：音频播放走 `AudioPlaybackManager` 的 ExoPlayer，`player`（`NxPlayer`）从不用于音频，但 `saveProgress/saveProgressSync/onCleared` 一律读取 `player.positionMs.value`/`player.durationMs.value`，对音频恒为 0。`saveProgress` 因 `if (position <= 0) return`（1672）直接返回，`onCleared` 写入 position=0 被 `upsertProgress` 的 BUG-24 保护拒绝覆盖。结果：音频 `play_history.videoPosition` 始终停在 `recordPlayStart` 初始值（首次为 0），下次恢复永远从头播，听了 30 分钟的进度无法续播。
- 修复建议：`isAudioPlayback==true` 时从 `audioPlaybackManager.positionMs/durationMs` 读取后再调 `saveProgressInternal`。

### P3 [Medium] applyBlackBarDetection 提前返回路径泄漏传入的 Bitmap
- 文件：`feature/.../feature/player/PlayerViewModel.kt`
- 行号：`1425-1431`
- 类别：Bitmap 内存泄漏
- 描述：`applyBlackBarDetection(bitmap)` 约定由本方法 `bitmap.recycle()`（见 1440 `finally { bitmap.recycle() }`）。但提前返回分支——`if (!PlayerSettings.autoDetectBlackBars) { _effectiveVideoSize.value = null; return }`（1426-1429）与 `if (!currentSize.isValid) return`（1431）——既不 recycle 也不回交调用方，Bitmap 泄漏。用户关闭"智能去黑边"后每次首帧抓图都泄漏一张。
- 修复建议：在两个提前返回分支中 `bitmap.recycle()` 后再 `return`。

### P4 [Medium] retryPlayback / restartFromStart 使用过期且可能已关闭的源
- 文件：`feature/.../feature/player/PlayerViewModel.kt`
- 行号：`retryPlayback` `1314-1323`、`restartFromStart` `1330-1337`；`lastPlaybackRequest` 仅 `init` `657` 赋值；`playAtIndex` `917-1004` 未更新；`swapStorage` `1026-1035` 异步关闭旧 Storage
- 类别：状态过期 / 资源已释放后复用
- 描述：`lastPlaybackRequest` 只在 `init` 保存初始请求，`playAtIndex` 切到其他曲目时未更新，且 `swapStorage(...)` 会把旧 Storage 异步 `close()`。若用户切到第 N 集后出错，点"重试"会重新装载 `lastPlaybackRequest.source`（第 1 集的源），其 `NxMediaSource.DataSource.storage` 可能早已被关闭，导致重试失败甚至回跳到原曲目。
- 修复建议：`playAtIndex` 成功装载后更新 `lastPlaybackRequest`；重试时不复用已被 `swapStorage` 关闭的 Storage（重建或缓存新 source）。

### P5 [Medium] LrcParser 排序比较器在排序中对同一可变列表调用 indexOf
- 文件：`feature/.../feature/player/LrcParser.kt`
- 行号：`35`
- 类别：不一致比较器 / 潜在 IllegalArgumentException / O(n²)
- 描述：`lines.sortWith(compareBy({ it.timeMs }, { lines.indexOf(it) }))` 的二级 key `lines.indexOf(it)` 在被排序的同一 `MutableList` 上做线性扫描，排序过程中元素位置不断变化，同一对 (a,b) 在不同时刻比较结果不同，违反排序一致性契约；当多条 LRC 时间戳相同（典型 LRC）时 TimSort 可能抛 `IllegalArgumentException: Comparison method violates its general contract!`，且整体为 O(n²)。
- 修复建议：用稳定排序 `lines.sortBy { it.timeMs }`；如需保留原插入次序，可先 `mapIndexed` 捕获索引后再排序。

### P6 [Medium] 通知每次刷新都在主线程解码封面 Bitmap（ANR/卡顿风险）
- 文件：`feature/.../feature/player/AudioPlaybackService.kt`
- 行号：`notificationListener` `111-123` 触发 `buildNotification` → `loadCoverFromPath` `183-189`；`AudioPlaybackManager.setCoverBitmap` `308-312` 从未被调用，故 `getCoverBitmap()` 恒为 null
- 类别：主线程阻塞 / Bitmap 解码
- 描述：每次播放状态切换（`onPlaybackStateChanged`、`onIsPlayingChanged`、`onMediaItemTransition`）都重建通知并 `BitmapFactory.decodeFile(path)` 解码完整封面。该路径运行在主线程（media3 `Player.Listener` 在应用 Looper 上回调），大封面或频繁状态变化时明显卡顿甚至 ANR。`playbackManager.getCoverBitmap()` 本意缓存但未写入。
- 修复建议：`AudioPlaybackManager` 内维护已解码封面（路径变化时在 IO 线程解码后缓存），通知构建时直接取缓存；或 `onMediaItemTransition`/封面路径变化时用协程在 IO 线程解码后 `pushNotification`。

### P7 [Medium] AudioPlaybackService.onCreate 在 player 为 null 时跳过 startForeground
- 文件：`feature/.../feature/player/AudioPlaybackService.kt`
- 行号：`59-91`；触发方 `AudioPlaybackManager.startService` `332-339` 用 `startForegroundService`
- 类别：前台服务启动契约违例 / RemoteServiceException
- 描述：`onCreate` 中只有 `if (player != null) { ... startForeground(...) }`。当 Service 被 `startForegroundService` 拉起但 `playbackManager.getPlayer()` 为 null（系统/路径重建、stop 后再拉起等竞态）时整个块被跳过，Android 8+ 在约 5 秒后抛 `ForegroundServiceDidNotStartInTimeException`。
- 修复建议：无论 player 是否就绪都先 `startForeground` 用占位通知；player 就绪后再 `pushNotification` 更新真实内容。

### P8 [Medium] swapStorage / closeStorageAsync / onCleared 频繁创建无结构化生命周期的 CoroutineScope
- 文件：`feature/.../feature/player/AudioPlaybackManager.kt` `345-351`；`feature/.../feature/player/PlayerViewModel.kt` `1031-1033`、`1769-1771`
- 类别：结构化并发缺失 / 资源关闭可能被中断
- 描述：每次切源都用 `CoroutineScope(Dispatchers.IO + NonCancellable).launch { storage.close() }` 新建一次性作用域，无父 Job 也无清理入口。进程退出/异常时这些协程可能被中断，导致 SMB/WebDAV 连接未完整关闭泄漏；多次切歌累积游离作用域。
- 修复建议：在 `AudioPlaybackManager`/`PlayerViewModel` 内维护受监督的 `closeScope`（或复用 supervisor scope）统一管理 storage 关闭任务。

### P9 [Low-Medium] AudioPlaybackManager.release 后 Service.onDestroy 对已释放 player 调用 removeListener
- 文件：`feature/.../feature/player/AudioPlaybackService.kt` `209-219`；`AudioPlaybackManager.release` `300-306`
- 类别：资源释放后访问 / 潜在 IllegalStateException
- 描述：`release()` 先 `stopService()`（触发 `stopSelf` → 异步 `onDestroy`），随后同步 `exoPlayer?.release()` 并置 null。`onDestroy` 中 `mediaSession?.player?.removeListener(notificationListener)` 通过 `ForwardingPlayer` 委托到底层已释放的 ExoPlayer，media3 对已释放 player 调 `removeListener` 可能抛 `IllegalStateException`。`stopService()` 把 `mediaSession` 置 null 后可避免，但时序取决于 `onDestroy` 是否先执行。
- 修复建议：`stopService()`/`release()` 中先 `sessionPlayer=null`、`mediaSession=null` 并移除监听器后再释放底层 player；`onDestroy` 中再次判空。

### P10 [Low] VinylRecordPlayer 黑胶盘 Bitmap 解码无 null 兜底
- 文件：`feature/.../feature/player/VinylRecordPlayer.kt`
- 行号：解码 `99-100`，使用 `184`；唱针 `94-97` 有兜底
- 类别：空指针 / 解码失败
- 描述：`BitmapFactory.decodeResource` 在资源缺失或 OOM 时返回 null。唱针已用 `createFallbackNeedleBitmap` 兜底，但 `rawDiscBitmap` 没有，后续 `rawDiscBitmap.asImageBitmap()` 会 NPE。
- 修复建议：为 `rawDiscBitmap` 增加 null 判空与兜底绘制（或直接 `drawCircle` 画黑色圆盘）。

### P11 [Low] PlaylistSheet LazyColumn key 在重复 filePath 时会重复
- 文件：`feature/.../feature/player/PlaylistSheet.kt`
- 行号：`135` `key = { index, item -> item.filePath }`
- 类别：重复键 / IllegalArgumentException
- 描述：播放列表中若存在两个 `filePath` 相同的项（符号链接、重复条目），LazyColumn 会因重复 key 抛 `IllegalArgumentException: Key "xxx" was already used`。`LyricsView`（81）已用 `"${line.timeMs}_${index}"` 处理，PlaylistSheet 未处理。
- 修复建议：改为 `"${item.filePath}_$index"` 或用其它唯一标识。

### P12 [Low] describePlaybackError 反射中 superclass 回退为死代码
- 文件：`feature/.../feature/player/PlayerViewModel.kt`
- 行号：`824-825`
- 类别：反射逻辑错误
- 描述：`getDeclaredField("responseCode") ?: cause.javaClass.superclass?.getDeclaredField(...)` 中 `getDeclaredField` 在字段不存在时抛 `NoSuchFieldException`，永不返回 null，`?:` 后的 superclass 回退分支不可达，无法从父类读取 `responseCode`。
- 修复建议：改显式 try/catch，捕获 `NoSuchFieldException` 后再尝试 `superclass`，或递归遍历继承链。

---

## 三、feature/home 模块

### H1 [High] PlayHistoryScreen 的 events 未消费，"继续播放"无法导航到播放页
- 文件：`feature/home/src/main/java/com/nichx/niplayer/feature/home/history/PlayHistoryScreen.kt`
- 行号：`96-103`（`when` 分支）；`71` 声明的 `onNavigateToPlayVideo` 从未被使用
- 类别：事件未被消费 / 导航失效
- 描述：`LaunchedEffect` collect `viewModel.events` 时 `when` 只处理 `PlayHistoryEvent.Toast`，其余（`NavigateToPlayer`/`ShowError`）落入 `else -> {}` 被吞。用户在历史页点"继续播放"调用 `viewModel.resumePlay(item)` 后，VM 写入 `PlaybackRequestHolder` 并 emit `NavigateToPlayer`，但 UI 永不导航——历史续播在该页面完全失效。
- 修复建议：`when` 中增加 `is PlayHistoryEvent.NavigateToPlayer -> onNavigateToPlayVideo()` 与 `ShowError -> snackbarHostState.showSnackbar(event.message)` 分支。

### H2 [Medium] PlayStarter 续播被 playlist 同步构造阻塞，与注释承诺相反
- 文件：`feature/home/.../feature/home/PlayStarter.kt`
- 行号：`88-94`
- 类别：协程 / 续播性能
- 描述：注释声称（BUG-22 修复）"先 set Holder 让 UI 立即导航，playlist 在后台异步构造（不阻塞返回）"，但实现是 `playbackRequestHolder.set(...)` 之后**同步**调用 `suspend fun buildAndSetPlaylist(...)` 才返回 `Success`。`buildAndSetPlaylist` 内部对 SMB/WebDAV 执行 `storage.listFiles(parentDir)`，大目录可能 1-3 秒。`HomeTabViewModel.resumePlay` 在 `when (val result = playStarter.startFromHistory(...))` 处会阻塞到 playlist 构造完成才 emit `NavigateToPlayer`，UI 不会立即跳转——原 BUG-22 仍存在。
- 修复建议：将 `buildAndSetPlaylist` 包入独立协程异步执行（fire-and-forget），`startFromHistory` 在 `playbackRequestHolder.set` 后立即返回 `Success`。

### H3 [Medium] WebViewScreen 内存泄漏（无 DisposableEffect/destroy）
- 文件：`feature/home/.../feature/home/settings/WebViewScreen.kt`
- 行号：`40-50`
- 类别：WebView 内存泄漏 / 生命周期
- 描述：`AndroidView` 创建 `WebView` 但无 `DisposableEffect` 在离开组合时调用 `WebView.destroy()`/移除引用，`settings.javaScriptEnabled = true` 也未关闭。未 destroy 的 WebView 持有 Activity/Context 引用，反复进入"开源许可证"页会累积 WebView 实例，长期内存泄漏与原生崩溃风险。
- 修复建议：用 `DisposableEffect(url) { webView -> onDispose { webView.destroy() } }`，或外部 Activity 管理 WebView 生命周期；按需关闭 JS。

### H4 [Medium] ImageViewerViewModel LruCache 以条目个数为容量上限，大图易 OOM
- 文件：`feature/home/.../feature/home/imageviewer/ImageViewerViewModel.kt`
- 行号：`52`
- 类别：内存 / LruCache 配置错误
- 描述：`LruCache<String, ByteArray>(5)` 以条目个数（默认 `sizeOf` 返回 1）为上限，最多缓存 5 张完整图片的 `ByteArray`。SMB/大图场景单张数 MB～数十 MB，5 张即可触发 OOM。
- 修复建议：改为基于字节大小限制（如 32MB），重写 `sizeOf` 返回 `ByteArray.size`，并考虑 VM 内存压力。

### H5 [Medium] StorageFileViewModel.generateThumbnailUrls 旧世代的 finally 重置进度竞态
- 文件：`feature/home/.../feature/home/library/StorageFileViewModel.kt`
- 行号：`804-813`
- 类别：状态竞态
- 描述：`finally` 块在 `flusher.cancel()` 后无条件执行 `_thumbnailProgress.value = -1`。用户快速切目录时旧世代 Job 被取消，但旧 Job 的 `finally` 仍会跑——即使新世代已将 `_thumbnailProgress` 设为 0/50%，旧 Job 的 finally 会把它强行重置为 -1，进度条短暂闪烁/丢失。`flushBatch` 已用世代号守卫，但 `_thumbnailProgress.value = -1` 没有。
- 修复建议：将 `_thumbnailProgress.value = -1` 也放入 `if (generation <= 0 || generation == thumbnailGeneration.get())` 守卫内。

### H6 [Medium] StorageFileViewModel.goUp 中 entryPathStack 移除时机与 directoryStack 不同步
- 文件：`feature/home/.../feature/home/library/StorageFileViewModel.kt`
- 行号：`264-277`
- 类别：栈一致性 / 竞态
- 描述：`goUp()` 对 `directoryStack` 在 `listDirectory` 成功后的 `stackOp` 内才 `removeLast`，但 `entryPathStack.removeLast()` 在 `viewModelScope.launch` **之前**同步执行。若随后 `listDirectory(parent)` 失败，`entryPathStack` 已丢入口路径，下次再 `goUp()` 会取错误入口，导致返回上级后滚动定位偏移甚至异常。
- 修复建议：把 `entryPathStack` 的移除也放入 `listDirectory` 成功后的 `stackOp` 回调，与 `directoryStack` 同步。

### H7 [Low-Medium] DownloadManagerViewModel.openImageInApp 定位路径不匹配，总显示首张
- 文件：`feature/home/.../feature/home/settings/DownloadManagerViewModel.kt`
- 行号：`259-282`；配合 `feature/home/.../imageviewer/ImageViewerViewModel.kt#L105-L106`
- 类别：资源路径不匹配 / UI 行为错误
- 描述：`openImageInApp` 把 `task.fileName` 作为 `initialFilePath` 传给 `ImageViewerRequest`，`directoryPath = ""`。而 `ImageViewerViewModel.loadImages` 用 `images.indexOfFirst { it.path == request.initialFilePath }` 定位，Storage 返回的 `StorageFile.path` 是完整路径（含目录前缀），不会等于裸 `fileName`，导致 `initialPosition` 永远 `coerceAtLeast(0) == 0`，下载管理点"打开图片"无法定位到所点图片，总显示目录第一张。
- 修复建议：保存并回传完整 `filePath`（或目录 + 文件名拼装路径），用与 Storage `path` 形式一致的字段做 `initialFilePath`。

### H8 [Low] DownloadManagerViewModel open* 失败静默无反馈
- 文件：`feature/home/.../feature/home/settings/DownloadManagerViewModel.kt`
- 行号：`229-256`
- 类别：静默失败 / UX
- 描述：`openVideoInApp`/`openAudioInApp`/`openWithSystemIntent` 在 `resolveFileUri(task)` 返回 null 时直接 `return`，既不发导航事件也不提示。SAF 场景下文件被外部删除/重命名时，点"打开"无任何反馈，`progress` 仍显示"已完成"。
- 修复建议：resolve 失败时 emit Toast/Snackbar 事件（"文件已不存在"），或将任务状态回写 `FAILED`。

### H9 [Low] LibraryViewModel 删除撤销产生新主键 id，关联表缺 cleanup
- 文件：`feature/home/.../feature/home/library/LibraryViewModel.kt`
- 行号：`88-118`
- 类别：数据一致性
- 描述：删除走 `deleteById`，撤销 `undoDelete` 用 `entity.copy(id = 0)` 重新插入会生成**新主键**。但 `quickAccessDao`/`playHistoryDao`/`downloadTaskDao` 中关联记录的 `libraryId` 仍指向旧 id；`quickAccessDao.deleteByLibrary` 等只在 `confirmDelete` 中调用（撤销后不执行）。结果：被撤销恢复的存储源与原快速访问/历史下载"失联"，旧 id 关联数据成为孤儿。
- 修复建议：撤销时优先尝试带原 id 恢复（或迁移关联表 `libraryId` 到新 id）；或删除前将关联记录一起"软删"保留。

### H10 [Low] HomeTabViewModel.connectionsValidated 一次性验证时机过早
- 文件：`feature/home/.../feature/home/home/HomeTabViewModel.kt`
- 行号：`226-234` 与 `411-442`
- 类别：一次性验证 / 数据竞争
- 描述：`connectionsValidated` 保证可达性验证只跑一次，触发时机是 `recentPlays` 首次发射。但首次发射时 `quickAccessItems.value` 的 `WhileSubscribed(5000)` StateFlow 通常是 `initialValue = emptyList()`（combine 未产出真值），所以 `quickItems.filter { it.libraryValid }` 为空——快速访问中引用的远程存储 id 不会被纳入可达性验证，"快速访问"卡片可能始终不带"离线"识别（重启 App 也因 `connectionsValidated` 锁死而无法重跑）。
- 修复建议：用 `combine(recentPlays, quickAccessItems)` 触发首验，或延迟到两个 Flow 均有非初始值时再跑。

### H11 [Low] StorageFileViewModel.retryLoadCurrent 死代码 + 重初始化被锁死
- 文件：`feature/home/.../feature/home/library/StorageFileViewModel.kt`
- 行号：`373-384`；初始化守卫 `100-106`
- 类别：死代码 / 重入隐患
- 描述：`retryLoadCurrent()` 中 `initialized = false` 紧接 `initialized = true` 是死代码（瞬间赋值无效），随后 `loadRoot()`。`loadRoot()` 不检查 `initialized`，逻辑上 `loadRoot` 完成后 `initialized` 仍为 true，导致后续真正 `initialize(...)` 调用被 `if (initialized) return` 拒绝，无法重新初始化。
- 修复建议：删除两行死代码；若希望 `retryLoadCurrent` 重新走完整初始化，应真正复位 `initialized = false` 并走 `initialize(...)` 路径。

### H12 [Low] ScanManagerViewModel.addExtendFolder 并发重复扫描风险
- 文件：`feature/home/.../feature/home/settings/ScanManagerViewModel.kt`
- 行号：`78-105`
- 类别：并发 / 重复扫描
- 描述：`addExtendFolder` 先主线程做 `File` 存在性校验，再 `viewModelScope.launch { scanner.scanExtendFolder(path); extendFolderDao.insert(...) }`。用户连点"添加"会触发多次 `scanExtendFolder`，并发都将视频插入 `video` 表，并尝试插入同 `folderPath` 的 `ExtendFolderEntity`（若有唯一约束第二次 insert 抛异常被吞，但 scanner 已写重复 video）。
- 修复建议：ViewModel 层加防抖/`Mutex`；或插入前 `extendFolderDao.getByPath(path)` 已存在时提示并短路且不重扫。

### H13 [Low] DownloadManagerScreen pendingSetDownloadDir 死状态 + 无法重选目录
- 文件：`feature/home/.../feature/home/settings/DownloadManagerScreen.kt`
- 行号：`94-108` 与 `185-191`
- 类别：死状态 / 误用
- 描述：`pendingSetDownloadDir` 被 set true 又在 launcher 回调里 set false，但该状态从未被读取，对逻辑无影响，属纯死代码。且 `DownloadDirCard` 已有目录时整体 `clickable` 仅在 `!hasDir` 时触发"设置"，无"更改"入口（只能"清除"），缺少重新选择下载目录的能力。
- 修复建议：删除 `pendingSetDownloadDir` 死状态；允许 `hasDir` 时点击右侧"更改"按钮重选目录。

### H14 [Low] StorageFileScreen 路径初始化重复导航
- 文件：`feature/home/.../feature/home/library/StorageFileScreen.kt`
- 行号：`188-196`；配合 `StorageFileViewModel.kt#L100-L106`、`212-214`
- 类别：重复导航 / 冗余副作用
- 描述：`FileBrowserOverlay` 中 `LaunchedEffect(storageId) { viewModel.initialize(storageId, initialPath) }` 已在 `initialize → loadRoot` 内部根据 `_initialPath` 调 `navigateToPathSegments(_initialPath)`；紧接着 `if (initialPath.isNotEmpty()) { LaunchedEffect(initialPath) { viewModel.navigateToPath(initialPath) } }` 又调一次。两者都会进入 `navigateToPathSegments`，存在条件性重复触发，增加不必要 `listFiles` 调用与竞态面。
- 修复建议：二选一——`loadRoot` 处理初始路径或 `LaunchedEffect(initialPath)` 处理，不要双重处理。

---

## 四、player/kernel + core/database + core/network + core/subtitle 模块

### K1 [High] Room 迁移 v6→v7 后 `updated_at` 默认值与实体不匹配，升级用户首启崩溃
- 文件：`core/database/src/main/java/com/nichx/niplayer/database/NiplayerDatabase.kt`
- 行号：`79-101`
- 配合实体：`MediaLibraryEntity.kt#L83-L84`、`PlayHistoryEntity.kt#L67-L68`、`QuickAccessEntity.kt#L54-L55`、`ExtendFolderEntity.kt#L21-L22`、`DownloadTaskEntity.kt#L49-L50`
- 类别：Room schema/migration 默认值不一致
- 描述：迁移对 5 张表执行 `ALTER TABLE ... ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0`，但实体 `updatedAt` 字段未声明 `@ColumnInfo(defaultValue = "0")`，导出 schema 期望该列 `defaultValue = null`。Room 2.8.4 启动校验比较 `dflt_value`：迁移后实际列 `"0"`、期望 `null` → 不等 → 抛 `IllegalStateException("Migration didn't properly handle: ...")`。`fallbackToDestructiveMigration(true)` **不会**补救"已跑完的迁移 vs 实体不匹配"（它仅在找不到迁移路径时触发）。结果：任何从 v6 升级上来的老用户首次启动崩溃；新装机直接 `CREATE TABLE`（无 DEFAULT）不受影响，故开发者本地重装易漏测。
- 修复建议：给 5 张受影响实体的 `updatedAt` 字段加 `@ColumnInfo(name = "updated_at", defaultValue = "0")`，使导出 schema 与迁移产生的列默认对齐；Kotlin 默认 `System.currentTimeMillis()` 仍用于新插入（Room 显式写值）。

### K2 [High] ASS `\t` 动画解析完全失效（朴素 split 破坏嵌套 `\` tag）
- 文件：`core/subtitle/src/main/java/com/nichx/niplayer/subtitle/renderer/AssOverrideParser.kt`
- 行号：`352-356` 与 `415-444`
- 类别：ASS override tag 解析 / 正则边界
- 描述（已核实修正）：`scanTagsInBlock` 在第 256 行用 `block.split('\\')` 朴素拆分整个 override 块。ASS 的 `\t(0,1000,\fs40)` 内部包含嵌套的 `\`，朴素 split 会把它拆成 `"t(0,1000,"` 和 `"fs40)"` 两个独立元素：
  - `"t(0,1000,"` 走 `startsWith("t(")` 分支，`substring(2)` 得 `"0,1000,"`，`splitTransformContent` 按逗号拆出 `["0","1000",""]`，`tagsStr` 为空——**所有插值目标值（`\fs/\c/\1c/\3c/\1a/\frz/\bord`）全部为 null**；
  - `"fs40)"` 被当作独立 `\fs` tag 处理，`"40)".toFloatOrNull()` → null，也无法解析为静态字号。
  于是 `\t(...)` 动画整体失效，`SubtitleEngine.applyTransforms` 永不应用任何插值。
- 修复建议：`scanTagsInBlock` 需正确处理 `\t(...)` 内的嵌套 `\` tag——在遇到 `t(` 时应按括号配对提取完整括号内容（如 `extractParenParams` 那样用 `indexOf('(')`/`lastIndexOf(')')`），再在括号内内容上调用 `parseTransformTag`，而非朴素 `split('\\')` 后逐元素处理。

### K3 [High] 同时段重叠字幕只渲染"起始时间最晚"的一条
- 文件：`core/subtitle/src/main/java/com/nichx/niplayer/subtitle/renderer/SubtitleEngine.kt`
- 行号：`191-208`
- 类别：字幕时间区间查询逻辑
- 描述：`update` 只取 `startMsToIndex.floorEntry(effectiveMs)`（≤当前时间的最大 startMs 桶），随后**只遍历这一个桶**内 caption 并做 `effectiveMs < startMs || effectiveMs > endMs` 过滤。但 A(startMs=1000, endMs=10000) 与 B(startMs=5000, endMs=8000) 在 effectiveMs=7000 时，`floorEntry` 指向 5000 桶（B），A 在 1000 桶被完全跳过——尽管 A 仍在显示区间内。ASS 多行重叠事件（OP/ED 歌词、双行对白、分层 `\pos` 特效）极常见，后果是只显示一条。
- 修复建议：改为遍历 `startMsToIndex.headMap(effectiveMs, true).values`（或从 floorEntry 向 lower entries 迭代），保留所有满足 `startMs <= effectiveMs && endMs >= effectiveMs` 的 caption；或用按 endMs 排序的优化结构。

### K4 [Medium] ASS/SSA Style 内嵌默认色始终无法被解析（落到用户配置色）
- 文件：`core/subtitle/.../SubtitleEngine.kt#L443-L456` 与 `core/subtitle/.../AssOverrideParser.kt#L534-L548`
- 类别：颜色解析
- 描述：两处 `parseStyleColor` 仅处理 `color.length == 8`（RRGGBBAA）；否则返回 null，`applyAnimation` 回退到 `cfg.primaryColor/outlineColor`。但上游 `Style.getRGBValue` 把 SSA `&HBBGGRR` 映射为 **6 字符** `RRGGBB`，ASS `&HAABBGGRR` 因 `substring(6)+charAt(4)+charAt(2)+"ff"` 拼接为 ≤5 字符的畸变串。两类 `Style.color/backgroundColor` 都不是 8 字符 → 解析始终为 null → `applyEmbeddedStyles=true` 形同虚设，ASS Style 自带 primary/outline 色从不生效（行内 override `\c/\1c` 仍走 `parseAssColor` 正常工作，故多数字幕表面看仍正确）。
- 修复建议：`parseStyleColor` 支持 6 字符 `RRGGBB`（追加 `alpha=FF`），并对悬挂 `&H`/短串过滤；若想根治，应修正 `Style.getRGBValue` 使其统一输出 8 字符 `RRGGBBAA`。

### K5 [Low] BooleanConverter.intToBoolean 丢失可空语义
- 文件：`core/database/src/main/java/com/nichx/niplayer/database/converter/BooleanConverter.kt`
- 行号：`14-17`
- 类别：Type converter 正确性
- 描述：`return intValue == 1`，当 `intValue == null` 时 `null == 1` 在 Kotlin 中为 `false`，于是非空 `Boolean`（`false`）而非 `null`。当前实体列均为 `Boolean`（NOT NULL）未崩；但该 converter 声明 `Boolean?`，一旦出现可空布尔列或直接 SQL 写入 NULL，会在读端静默把 NULL 误判为 `false`。
- 修复建议：`return intValue?.let { it == 1 }`，保持 `null → null` 双向往返一致；或写入端 `requireNotNull` 并将类型收窄为 `Boolean`。

---

## 修复优先级建议

按"影响面 + 严重度"建议优先级如下：

1. **P0（必修，影响功能/崩溃）**
   - H1 历史续播无法导航（功能完全失效）
   - P1 后台音频自动切歌失效 + 内存泄漏
   - P2 音频进度无法续播
   - K1 Room 升级用户首启崩溃
   - S1 DownloadManager 输入流异常卡死 + 泄漏
   - S2 SAF 下载文件损坏
   - K3 重叠字幕丢失
   - K2 ASS `\t` 动画失效

2. **P1（高，应尽快修）**
   - H2 PlayStarter 续播阻塞导航
   - S3 SmbStorage.createDirectory 误报成功
   - P7 前台服务启动契约违例
   - P6 主线程解码封面（ANR 风险）
   - P4 retry 使用过期/已关闭源
   - H3 WebView 内存泄漏
   - H4 图片 LruCache OOM
   - K4 ASS Style 内嵌色失效

3. **P2（中，择机修）**：S4-S9、P3、P5、P8、H5-H7 等并发/资源类。

4. **P3（低，清理类）**：S10-S12、P9-P12、H8-H14、K5 等静默失败/死代码/边界。

---

## 附：已确认非 Bug（仅记录背景）

- `NxMedia3Player.release()` 顺序（`isReleased`/`_state=Idle` → `removeCallbacks` → `removeListener` → `exoPlayer.release()`）正确；`positionTicker` 用 `isReleased` 双重短路并吞 `IllegalStateException`，符合 Media3 生命周期约定。
- `attachSurface(null)` 走 `setVideoSurface(null)` 解绑，符合 Media3 用法。
- `DateConverter` 双向 `null` 处理正确；`MediaTypeConverter.fromValue` 走 `when` 兜底 `OTHER_STORAGE`，对未知/NULL 字符串安全。
- `NetworkModule` trust-all 相关代码仅在 `mediaSource.trustAllCertificates=true` 时派生，与"自签 WebDAV"开关一致；ASSRT token 经 HTTP query 传输是 API 设计本身，未发现密钥硬编码/泄漏。
- `PlaybackRequestHolder`/`PlaylistHolder` 的 `@Volatile` + 主线程约定满足可见性；`ThumbnailManager` 的 `mutexMap` 使用 `computeIfAbsent`/`computeIfPresent` 配对 `tryLock/unlock`，未发现锁泄漏。
- `SubtitleStyleDialog`/`SubtitleSettings` 等多处直接读 MMKV（非响应式）并通过 `ON_RESUME` 自增 `settingsVersion` 触发重组以 workaround（`SubtitleOverlay.kt:83-99`）；`PlayerScreen` 中的 `SubtitleView`（678-694）也直接读 `SubtitleSettings` 但没有等价失效机制，设置页改样式后内嵌字幕样式不会即时更新——按审查要求仅记作背景信息。