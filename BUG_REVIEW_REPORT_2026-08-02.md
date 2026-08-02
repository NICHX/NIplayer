# NIplayer v2 代码审查与 Bug 报告（第二轮）

> **审查范围**：`app/`、`core/`（common、database、datastore、designsystem、navigation、network、storage、subtitle、thumbnail）、`feature/`（home、player）、`player/kernel/` 全部 Kotlin 源码（排除 `build/generated` 产物与 `player/ffmpeg` 第三方代码）。
>
> **审查日期**：2026-08-02
>
> **审查方式**：五个并行子代理按模块全量阅读源码 + 主代理对全部 High 级与代表性 Medium 级缺陷逐行回读核实。
>
> **与上一轮（2026-07-31 的 `BUG_REVIEW_REPORT.md`）的关系**：本轮以当前磁盘代码为准，对旧报告 37 条缺陷逐条核实状态（已修复 / 部分修复 / 仍存在），并独立发现新增缺陷。

---

## 概览汇总

### 旧缺陷核实结论（2026-07-31 报告 43 条）

| 状态 | 数量 | 明细 |
| --- | --- | --- |
| 已彻底修复 | 19 | S1 S2 S3 S4 S6 S7 S8 S9 / K1 K3 / P2 P3 P5 P8 P9 / H1 H2 H3 H4 |
| 部分修复 | 6 | S5（可见性已修，无锁置空竞态残留）/ P1（泄漏已修，后台自动切歌仍失效）/ P4（源已更新，重试缺音频分支）/ P6（主路径已修，主线程解码兜底仍在）/ K2（括号感知已修，`\t` 时间窗 off-by-one 残留）/ K4（6 字符分支已加，ASS `&H` 仍失效且 SSA 引入错色） |
| 仍存在 | 18 | S10 S11 S12 / P7 P10 P11 P12 / H5 H6 H7 H8 H9 H10 H11 H12 H13 H14 / K5 |
| **合计** | **43** | 全部核实完毕 |

### 本轮新发现缺陷（复核前）

| 严重度 | 数量 |
| --- | --- |
| High | 4 |
| Medium | 23 |
| Low | 36 |
| **总计** | **63** |

> **复核更新（见下方"第二轮复核结论"）**：逐条代码复核后，P-N1 降为 Medium、P-N6 判定不成立、12 条 Medium 降为 Low，修正后分布为 **High 3 / Medium 17 / Low 42 / 不成立 1**。

### 第二轮复核结论（2026-08-02 追加）

> 复核方式：5 组并行验证代理对 63 条新发现缺陷逐条回读当前磁盘代码验证（CONFIRMED 确认 / CORRECTED 成立但严重度或描述需修正 / REJECTED 不成立），主代理对严重度大幅调整的条目复核证据链。所有行号均已按当前代码更新。

**复核结果统计：**

| 结论 | 数量 | 明细 |
| --- | --- | --- |
| CONFIRMED | 44 | K-N1 K-N2 K-N3 K-N5 K-N7 / T-1 T-2 T-4 T-5 T-6 T-7 T-8 T-9 / D-1 D-3 C-3 C-5 C-6 / B-1 B-2 B-4 B-5 B-6 B-7 B-9 B-10 B-11 / S-N3 S-N4 S-N7 S-N8 S-N9 S-N10 / P-N2 P-N3 P-N4 P-N5 P-N7 / H-N1 H-N3 H-N5 H-N6 H-N7 H-N8 |
| CORRECTED | 18 | K-N4 K-N6 / T-3 / D-2 C-1 C-2 C-4 / N-1 N-2 / B-3 B-8 / S-N1 S-N2 S-N5 S-N6 / P-N1 / H-N2 H-N4 |
| REJECTED | 1 | P-N6 |

**严重度修正后分布：High 3 / Medium 17 / Low 42 / 不成立 1（有效缺陷 62 条）**

按模块分布（修正后）：

- `core/thumbnail`：9 条（High 2 / Medium 2 / Low 5）
- `core/storage`：10 条（Medium 3 / Low 7）
- `feature/home`：8 条（Medium 2 / Low 6）
- `feature/player`：7 条（Medium 3 / Low 3 / 不成立 1）
- `core/database` + `core/network` + `player/kernel` + `core/subtitle`：7 条（High 1 / Medium 2 / Low 4）
- `core/designsystem` + `core/common` + `core/datastore` + `app` + `core/navigation`：22 条（Medium 5 / Low 17）

**重点修正明细（严重度或结论变化较大者）：**

| 编号 | 原严重度 | 复核后 | 修正原因（验证证据要点） |
| --- | --- | --- | --- |
| P-N1 | High | Medium | `SmbStorage.close()` 仅关闭活动流、不销毁连接，重开有 `MAX_RETRY=3` 自动重连；WebDAV/Local 音频（Http/Local source）`currentStorage=null` 完全不受影响。"重试必然失败"不成立，实际为 close 与新 open 的并发竞态，降为 Medium |
| P-N6 | Low | **不成立** | `PlayerGuardViewModel.computeTarget` 按 `isAudio` 分流，音频永不进入 PlayerScreen；`retryPlayback`/`restartFromStart` 对音频不可达，属防御性缺失而非可触发 bug |
| T-4 | Medium | Low | `cacheFile.delete()` 逃逸 `withLock` 串行保护属实，但属低概率竞态，正常结果仍正确 |
| T-5 | Medium | Low | 生成路径与查询路径判定不一致属实，但需写入中断才触发 |
| T-6 | Medium | Low | body==null 或失败分支未 `close()` 属实，但每次 API 失败仅泄漏一个连接 |
| S-N1 | Medium | Medium（范围修正） | 9 处 catch-all 中 `rename`/`move`/`uploadFile` 3 处已有前置取消 rethrow，实际仅 6 处需修（readFileBytes/deleteFile/ping/openPlayStream/openMediaDataSource/saveFile） |
| S-N2 | Medium | Low | 诊断性 OPTIONS/PROPFIND 探活路径，吞取消不影响数据正确性 |
| S-N5 | Medium | Low | 竞态真实但需"首启空表+多协程并发读快照"同时满足；实际影响是重复全量扫描与 id 置换，用户字段重置窗口很窄 |
| S-N6 | Low | Low（描述修正） | `parallelism=4 < maxBufferedChunks=16`，死 chunk 不阻塞预读，属容量/内存损耗（≤4MB/流） |
| H-N1 | Medium→Low | Low | SMB DataSource 分支 storage 随 source 传递有归属；WebDAV `close()` 为空实现（OkHttpClient 由 Hilt 管理），实际资源影响近乎为零 |
| H-N2 | Medium | Low | 三处无 close 属实，但 `WebDavStorage.close()` 为空实现，仅泄漏可 GC 对象本身 |
| H-N4 | Medium | Low | key 按 storageId 复用疑为刻意设计（返回 overlay 恢复浏览状态），非纯泄漏 |
| B-3 | Medium | Low | 注入期崩溃本质是依赖图配置错误（开发期 bug），捕获价值有限 |
| C-2 | Medium | Low | 唯一调用方 HomeScreen 的 currentRoute 与 tabs 同源必能匹配，`coerceAtLeast(0)` 当前不可达 |
| D-2 | Low/Medium | Low | `DarkExtra`/`LightExtra` 无任何业务引用（死代码），运行时无冲突 |
| K-N4 | Low | Low（描述修正） | 非"每帧"（`positionMs` 500ms 间隔驱动）非"全量"（headMap 前缀视图），为 500ms 线性前缀扫描 |
| K-N6 | Low | Low（描述修正） | `sync_delete_log` 无任何业务写入/读取方（预留表），当前不存在"删除标记缺失导致重新上传"的实际路径 |
| T-9 | Low | Low（描述修正） | 危害是旧 retriever（绑定 URL）泄漏而非功能失败，回退逻辑不受影响 |
| C-1 | Medium | Medium（行号修正） | NiFAB 实际行号为 L48/L110（非 L39/L69）；不一致结论成立 |
| C-4 | Low | Low（范围修正） | `iconTint`/`labelColor` 非全库死代码：PlayerDialogs 的 PlayerItemRow 有消费，仅 designsystem 的 NiDialogItemRow 未读取 |
| B-8 | Low | Low（定性修正） | 空输入不写入是显式防御（空白名单会使扫描失效），实质是 UX 无反馈而非数据缺陷 |
| N-1 | Low | Low（描述修正） | 当前唯一调用方总是传入非空 builder，崩溃不可达，属 API 默认值隐患（潜在） |
| N-2 | Low | Low（描述修正） | type 来源为 `MediaType.value` 固定安全字符，当前不可触发，属防御性改进 |

**三个 High 全部确认（K-N1 字幕搜索失效 / T-1 SMB 句柄泄漏 / T-2 Bitmap 误回收）**，无争议。

> 行号以 2026-08-02 仓库源码为准，后续改动可能导致行号漂移，请以文件链接位置为准。

---

## 一、core/storage 模块

### 旧缺陷状态核实

| 编号 | 状态 | 说明 |
| --- | --- | --- |
| S1 | ✅ 已修复 | `inputStream` 获取已移入 `try` 内，失败置 `FAILED` 并 `storage.close()`（[DownloadManager.kt:273-295](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/download/DownloadManager.kt#L273-L295)） |
| S2 | ✅ 已修复 | SAF `offset<=0` 命中已存在文件先 `delete()` 再 `createFile()`（[:384-396](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/download/DownloadManager.kt#L384-L396)） |
| S3 | ✅ 已修复 | `createDirectory` 前置 `catch (CancellationException) { throw e }`，普通异常用 `exists()` 二次探测（[SmbStorage.kt:412-426](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/SmbStorage.kt#L412-L426)） |
| S4 | ✅ 已修复 | `close()` 改为 `activeStreams.remove(delegate)`（[:612-615](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/SmbStorage.kt#L612-L615)） |
| S5 | ⚠️ 部分修复 | `shareRootPrefix`/`playRootPrefix` 已加 `@Volatile`；但"重试失败置空 prefix"仍在 `connectMutex` 之外执行（[SmbStorage.kt:182](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/SmbStorage.kt#L182)），置空→重建窗口内 `buildSmbUrl` 可能拼出错误 URL |
| S6 | ✅ 已修复 | 重试路径已前置 `catch (CancellationException) { throw e2 }`（[WebDavStorage.kt:279-289](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L279-L289)） |
| S7 | ✅ 已修复 | `runCatching` 改为显式 try/catch + 取消 rethrow（[:292-304](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L292-L304)） |
| S8 | ✅ 已修复 | 预读 IOException 现置 `error` 并 `notifyAll()`（[PrefetchInputStream.kt:90-108](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/datasource/PrefetchInputStream.kt#L90-L108)） |
| S9 | ✅ 已修复 | `_taskProgress` 读写均改用 `MutableStateFlow.update{}` CAS（[DownloadManager.kt:444-448](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/download/DownloadManager.kt#L444-L448)） |
| S10 | ❌ 仍存在 | `listCache`/`listCacheTimestamps` 两个 ConcurrentHashMap 只增不减，`close()` 也不清空（[WebDavStorage.kt:106-107](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L106-L107)） |
| S11 | ❌ 仍存在 | 无 share 时 `SmbFile` 构造后直接 `return true`，未真正探测（[SmbStorage.kt:546-551](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/SmbStorage.kt#L546-L551)） |
| S12 | ❌ 仍存在 | WebDAV `openMediaDataSource` 直接透传 `file.length`，无 `<=0` 保护（[WebDavStorage.kt:197-199](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L197-L199)） |

### 新发现缺陷

#### S-N1 [Medium] SmbStorage 八处 catch-all 吞/包装 CancellationException
- 文件：[SmbStorage.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/SmbStorage.kt)
- 行号：`306-309`（readFileBytes）、`322-326`（openPlayStream 二次尝试包装成 IOException）、`356-360`（openMediaDataSource）、`383-385`（deleteFile）、`395-399`（saveFile）、`446-450`（rename）、`469-473`（move）、`496-499`（uploadFile）、`522-524`（ping）
- 类别：取消语义被破坏
- 描述：以上 catch-all 均未前置 `catch (CancellationException) { throw e }`。缩略图批量生成、用户退出页面触发协程取消时，取消被吞或包装成 IOException，调用方按"操作失败"继续后续逻辑（如重试上传），取消被延迟到更远的挂起点才生效，期间发起多余网络/IO 操作。
- 改进方向：与已修复的 S3/S6/S7 同模式，统一"每个 catch-all 前置取消 rethrow"（建议 CI lint 规则强制：`catch (Exception)` 前必须存在 `catch (CancellationException)`）。

#### S-N2 [Low] WebDavStorage.createDirectoryViaPutFallback 吞取消（复核后：Medium→Low）
- 文件：[WebDavStorage.kt:550-558](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L550-L558)、[:561-575](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L561-L575)
- 类别：取消语义被破坏
- 描述：MKCOL 失败进 PUT 回退后，两处诊断请求用 `catch (_: Exception) {}` 吞掉包括取消在内的所有异常，协程被取消时继续执行后续 PUT 策略与诊断请求。
- 改进方向：前置 `catch (e: CancellationException) { throw e }`。

#### S-N3 [Medium] DownloadManager 断点续传打开流的 CancellationException 被吞
- 文件：[DownloadManager.kt:275-278](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/download/DownloadManager.kt#L275-L278)
- 类别：取消语义被破坏 / 额外网络连接
- 描述：`storage.openInputStream(storageFile, actualOffset)`（suspend）的 `catch (_: Exception) { null }` 吞掉取消 → 走"不支持续传"分支从头下载，暂停/取消响应延迟且多产生一次完整下载连接。
- 改进方向：内层 catch 前置取消 rethrow。

#### S-N4 [Medium] pipelinedWriteLoop 提前 EOF 仍标记 COMPLETED，损坏文件入库
- 文件：[DownloadManager.kt:434-467](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/download/DownloadManager.kt#L434-L467)
- 类别：数据完整性
- 描述：`len == -1` 即无条件 `updateProgress(taskId, totalRead, COMPLETED)`，未校验 `totalBytes > 0 && totalRead < totalBytes`。SMB 场景服务器内容短于 `file.length`（共享文件被截断/静默 EOF）时，`SmbParallelInputStream.doPrefetch` 读到 EOF 且不置 readError（[:162-170](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/SmbParallelInputStream.kt#L162-L170)），不完整文件被标记为下载完成。
- 改进方向：循环结束后若 `totalBytes > 0 && totalRead < totalBytes` 则置 FAILED 并删除文件。

#### S-N5 [Low] VideoScanner 首启并发全量扫描 + REPLACE 重置用户字段（复核后：Medium→Low）
- 文件：[VideoStorage.kt:43-53](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/VideoStorage.kt#L43-L53)、[VideoScanner.kt:50-56](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/scanner/VideoScanner.kt#L50-L56)、[VideoDao.kt:60](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/database/src/main/java/com/nichx/niplayer/database/dao/VideoDao.kt#L60)
- 类别：并发 / 数据覆盖
- 描述：`video` 表为空时多个页面并发 `listFiles` 同时命中 `isEmpty()` 各自触发全量 `scan()`；两个 scan 基于同一"读快照"决策，后完成者 `@Insert(REPLACE)` 会覆盖先插入行，把未写入的 `filter`/`subtitle_path` 等用户字段重置为默认值，与 `syncToDatabase` 注释"已存在记录不更新"意图冲突。
- 改进方向：`VideoScanner` 内加 Mutex/AtomicBoolean 防并发扫描。

#### S-N6 [Low] SmbParallelInputStream.skip 与预读线程竞态（死 chunk 堆积 + 重复读取）
- 文件：[SmbParallelInputStream.kt:278-281](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/SmbParallelInputStream.kt#L278-L281)
- 类别：资源浪费
- 描述：`skip` 清空 `chunks` 与预读线程 `nextReadChunk.getAndIncrement()` 竞态，已取号未写入的线程仍会写入旧 seq 的 chunk 且永不消费（缓冲最多堆积 16MB 死数据），seek 频繁时内存与带宽浪费。
- 改进方向：skip 时用 generation 计数淘汰过期 chunk。

#### S-N7 [Low] SmbParallelInputStream.close 读取非 volatile channels/threads
- 文件：[SmbParallelInputStream.kt:78-80](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/SmbParallelInputStream.kt#L78-L80)、[:318-319](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/SmbParallelInputStream.kt#L318-L319)
- 类别：并发可见性 / 句柄泄漏
- 描述：`channels`/`threads` 非 `@Volatile`，`close()` 不在 `filesLock` 内读取；与 `ensureStarted()` 跨线程并发时可能读到 null，4 个 SmbRandomAccess 与预读线程不被关闭。
- 改进方向：声明为 `@Volatile` 或在 `filesLock` 内读取。

#### S-N8 [Low] StorageDataSource.open 超时取消后已创建的播放流泄漏
- 文件：[StorageDataSource.kt:75-89](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/datasource/StorageDataSource.kt#L75-L89)
- 类别：资源泄漏（窗口小）
- 描述：`withTimeoutOrNull` 超时取消时，`openPlayStreamInternal` 已把流加入 `playActiveStreams`（[SmbStorage.kt:336](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/SmbStorage.kt#L336)），无人持有也无人关闭。
- 改进方向：超时路径显式关闭已建流，或把注册动作放到成功返回前。

#### S-N9 [Low] WebDavStorage.uploadFile 的 RequestBody 在 writeTo 内关闭 inputStream
- 文件：[WebDavStorage.kt:389-401](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L389-L401)
- 类别：OkHttp 重试兼容
- 描述：`writeTo` 内 `inputStream.use` 关闭了调用方传入的流；OkHttp 连接重试时再次调用 `writeTo`，流已关闭导致上传失败且无法重试。
- 改进方向：流关闭责任移到 `uploadFile` 外层 finally，`writeTo` 只读不关。

#### S-N10 [Low] WebDavMediaDataSource.readAt 与 close 竞态
- 文件：[WebDavMediaDataSource.kt:206-228](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavMediaDataSource.kt#L206-L228)、[:339-348](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavMediaDataSource.kt#L339-L348)
- 类别：close 后读数据
- 描述：`readAt` 循环中 `if (closed) break` 后仍无条件写 `bufferStart` 并返回数据；若 `close()` 恰在此窗口执行，close 后仍可能返回一次"成功"读取，违反"close 后 readAt 返回 -1"的注释承诺。
- 改进方向：返回前再次检查 `closed`。

---

## 二、feature/player 模块

### 旧缺陷状态核实

| 编号 | 状态 | 说明 |
| --- | --- | --- |
| P1 | ⚠️ 部分修复 | 泄漏已修复：`onCleared` 已将三回调置 null（[PlayerViewModel.kt:1901-1905](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt#L1901-L1905)）。**功能仍失效**：后台自动切歌完全依赖 `onPlayNextRequest` 回调（[AudioPlaybackManager.kt:265-271](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlaybackManager.kt#L265-L271)），VM 销毁后回调为 null，后台音频停在曲尾；通知栏/ MusicBar"下一首"同样静默失效（索引已 +1 但 source 不变） |
| P2 | ✅ 已修复 | 音频进度保存已分流读 `audioPlaybackManager.positionMs/durationMs`（[:1790-1798](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt#L1790-L1798)），链路端到端打通 |
| P3 | ✅ 已修复 | 两个提前返回分支均先 `bitmap.recycle()`（[:1551-1571](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt#L1551-L1571)） |
| P4 | ⚠️ 部分修复 | `playAtIndex` 已更新 `lastPlaybackRequest`（[:1110-1116](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt#L1110-L1116)）；残留缺口：`retryPlayback`/`restartFromStart`（[:1440-1463](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt#L1440-L1463)）无条件 `player.setSource`，无 `request.isAudio` 分支（防御性缺口，当前 UI 音频重试走 manager） |
| P5 | ✅ 已修复 | 改为 `lines.sortBy { it.timeMs }` 稳定排序（[LrcParser.kt:36](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/LrcParser.kt#L36)） |
| P6 | ⚠️ 部分修复 | 主路径已修：`decodeCoverAsync` IO 预解码缓存（[AudioPlaybackManager.kt:344-353](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlaybackManager.kt#L344-L353)）；**兜底仍主线程**：缓存为 null 时 `loadCoverFromPath` 仍主线程 `BitmapFactory.decodeFile`（[AudioPlaybackService.kt:199-204](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlaybackService.kt#L199-L204)） |
| P7 | ❌ 仍存在 | `onCreate` 只有 `if (player != null)` 才 `startForeground`（[AudioPlaybackService.kt:54-90](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlaybackService.kt#L54-L90)）；`buildPlaceholderNotification` 存在但全文件**无任何调用点**，是死代码 |
| P8 | ✅ 已修复 | `closeScope` 结构化复用（[PlayerViewModel.kt:351](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt#L351)、[AudioPlaybackManager.kt:40](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlaybackManager.kt#L40)） |
| P9 | ✅ 已修复 | `stopService()` 先置 `mediaSession=null`，`onDestroy` 空安全跳过（[AudioPlaybackService.kt:42-51](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlaybackService.kt#L42-L51)） |
| P10 | ❌ 仍存在 | `rawDiscBitmap` 无 null 兜底（[VinylRecordPlayer.kt:95-100](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/VinylRecordPlayer.kt#L95-L100)） |
| P11 | ❌ 仍存在 | `key = { index, item -> item.filePath }` 重复 filePath 会崩（[PlaylistSheet.kt:133-135](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlaylistSheet.kt#L133-L135)） |
| P12 | ❌ 仍存在 | `getDeclaredField("responseCode") ?: superclass...` 反射回退死代码（[PlayerViewModel.kt:933-952](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt#L933-L952)） |

### 新发现缺陷

#### P-N1 [Medium] AudioPlaybackManager.retry() 与 play() 的 storage close 并发竞态（复核后：High→Medium）
- 文件：[AudioPlaybackManager.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlaybackManager.kt)
- 行号：`244-245`（play 内 `closeStorageAsync()` 后赋回 `currentStorage`）、`356-367`（retry 复用 `_currentSource`）、`382-388`（closeStorageAsync 无条件关闭）
- 类别：资源时序错误 / 功能失效
- 描述（**本人已核实**）：`retry()` 复用与 `currentStorage` **同一对象**的 source 调 `play()`；`play()` 先 `closeStorageAsync()` 把该 storage 异步 close，随后又把同一（已关闭）storage 赋回 `currentStorage` 并基于其 factory 建 MediaSource。对 SMB/WebDAV（`NxMediaSource.DataSource`）音频，close 与 ExoPlayer prepare 加载线程竞态，close 先完成则重试必以"连接已关闭"再次失败——**远程音频重试按钮实质不可用**。对比 [PlayerViewModel.swapStorage](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt#L1153-L1162) 已有 `old !== newStorage` 防御，manager 侧缺失。
- 改进方向：`if (currentStorage !== newStorage) closeStorageAsync()`，或 retry 路径跳过关闭。

#### P-N2 [Medium] 播放模式（顺序/随机/单曲循环）纯 UI 摆设
- 文件：[AudioPlayerScreen.kt:71-75](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlayerScreen.kt#L71-L75)、[:111](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlayerScreen.kt#L111)、[:166-182](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlayerScreen.kt#L166-L182)
- 类别：功能未接线
- 描述：`playMode` 仅为 `remember` 局部状态，从未传给 `AudioPlaybackManager`；`requestNext` 恒为顺序下一首，无 shuffle/单曲循环。用户切"随机/循环"仅图标变化，行为仍是顺序播放；且未用 `rememberSaveable`，页面重建即回退。
- 改进方向：将 playMode 下沉到 manager（StateFlow），requestNext 按模式分支；UI 用 `rememberSaveable`。

#### P-N3 [Medium] 封面预解码竞态：旧封面覆盖新封面 + 旧 Bitmap 不回收
- 文件：[AudioPlaybackManager.kt:344-353](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlaybackManager.kt#L344-L353)、[:326-328](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlaybackManager.kt#L326-L328)
- 类别：竞态 / 内存
- 描述：快速切歌时 `play()` 与 `updateCoverPath()` 各 launch 一个 IO 解码协程，完成顺序不定且无路径比对，较早启动的旧曲解码若后完成会覆盖新曲封面（通知栏封面与歌曲不符）；`setCoverBitmap` 替换引用时不 `recycle()` 旧 Bitmap。
- 改进方向：解码协程带封面路径参数，完成后比对 `_audioCoverPath.value` 才写入；替换前 recycle 旧值。

#### P-N4 [Low] 音频暂停态 seek 后进度条不回弹
- 文件：[AudioPlaybackManager.kt:171-180](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/AudioPlaybackManager.kt#L171-L180)
- 类别：UI 状态
- 描述：轮询只在 `p.isPlaying` 时更新 `_positionMs`；暂停时拖动进度条后 UI 滑块停留旧值直到恢复播放。
- 改进方向：轮询无条件更新 position（或 seekTo 后立即回写）。

#### P-N5 [Low] MusicBar 点击后 isInteracting 无重置，卡片永久不透明
- 文件：[MusicBar.kt:140-145](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/MusicBar.kt#L140-L145)、[:177-184](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/MusicBar.kt#L177-L184)
- 类别：UI 状态泄漏
- 描述：`onTap` 置 `isInteracting = true` 后立即导航，无延迟复位；若 MusicBar 未随导航离开组合，空闲淡出永久失效。
- 改进方向：onTap 中用 `delay` 复位或随导航重置。

#### P-N6 [不成立] retryPlayback / restartFromStart 缺音频分支（复核后：判定 REJECTED，无触发路径）
- 文件：[PlayerViewModel.kt:1440-1463](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt#L1440-L1463)
- 类别：防御性缺口
- 描述：两函数无视 `request.isAudio`，音频源一旦误入会把音频装进视频 NxPlayer。当前 UI 未触发。
- 改进方向：入口处 `if (request.isAudio) { audioPlaybackManager?.retry(); return }`。

#### P-N7 [Low] MusicMetadataService 网络请求不可取消
- 文件：[MusicMetadataService.kt:64](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/MusicMetadataService.kt#L64)、[:127](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/MusicMetadataService.kt#L127)
- 类别：协程取消
- 描述：`withContext(Dispatchers.IO)` 内阻塞 `newCall().execute()` 无法被协程取消，VM 销毁后 IO 线程仍被占用至 readTimeout。
- 改进方向：用 `enqueue` 或 `withTimeoutOrNull` 包裹。

---

## 三、feature/home 模块

### 旧缺陷状态核实

| 编号 | 状态 | 说明 |
| --- | --- | --- |
| H1 | ✅ 已修复 | events 已全分支消费（[PlayHistoryScreen.kt:96-105](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/history/PlayHistoryScreen.kt#L96-L105)） |
| H2 | ✅ 已修复 | `playbackRequestHolder.set` 后 `appScope.launch { buildAndSetPlaylist(...) }` 异步构造（[PlayStarter.kt:74-103](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/PlayStarter.kt#L74-L103)） |
| H3 | ✅ 已修复 | WebViewScreen 已从代码库移除 |
| H4 | ✅ 已修复 | `LruCache<String, ByteArray>(32MB)` + 重写 `sizeOf`（[ImageViewerViewModel.kt:52-54](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/imageviewer/ImageViewerViewModel.kt#L52-L54)） |
| H5 | ❌ 仍存在 | `_thumbnailProgress.value = -1` 在世代守卫之外（[StorageFileViewModel.kt:870-879](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/StorageFileViewModel.kt#L870-L879)） |
| H6 | ❌ 仍存在 | `entryPathStack.removeLast()` 在 launch 外同步执行，与 `directoryStack` 不同步（[:304-317](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/StorageFileViewModel.kt#L304-L317)） |
| H7 | ❌ 仍存在 | `initialFilePath = task.fileName` 裸文件名 vs 完整路径定位不匹配（[DownloadManagerViewModel.kt:270-275](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/settings/DownloadManagerViewModel.kt#L270-L275)） |
| H8 | ❌ 仍存在 | `resolveFileUri(task) ?: return` 静默失败（[:230](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/settings/DownloadManagerViewModel.kt#L230)） |
| H9 | ❌ 仍存在 | 撤销删除 `entity.copy(id = 0)` 新主键，关联表孤儿（[LibraryViewModel.kt:98-107](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/LibraryViewModel.kt#L98-L107)） |
| H10 | ❌ 仍存在 | `connectionsValidated` 触发时 `quickAccessItems` 恒为初始空列表（[HomeTabViewModel.kt:227-234](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/home/HomeTabViewModel.kt#L227-L234)） |
| H11 | ❌ 仍存在 | `initialized = false; initialized = true` 死代码（[StorageFileViewModel.kt:410-418](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/StorageFileViewModel.kt#L410-L418)） |
| H12 | ❌ 仍存在 | `addExtendFolder` 无防抖/Mutex（[ScanManagerViewModel.kt:78-105](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/settings/ScanManagerViewModel.kt#L78-L105)） |
| H13 | ❌ 仍存在 | 下载"打开"无防抖，连点重复 push 播放页（[DownloadManagerViewModel.kt:239-241](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/settings/DownloadManagerViewModel.kt#L239-L241)） |
| H14 | ❌ 仍存在 | `initialize` 内部与 `LaunchedEffect(initialPath)` 双重导航（[StorageFileScreen.kt:240-248](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/StorageFileScreen.kt#L240-L248)） |

### 新发现缺陷

#### H-N1 [Low] PlayStarter 创建的 Storage 生命周期归属不清（复核后：Medium→Low）
- 文件：[PlayStarter.kt:59](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/PlayStarter.kt#L59)、[:187](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/PlayStarter.kt#L187)
- 类别：资源生命周期（**经本人核实后修正为部分成立**）
- 描述：`startFromHistory`/`startFromQuickAccess` 创建 storage 后既不 close，也不在错误路径清理。但 [MediaSourceBuilder.kt:58-60](file:///Users/nichx/Documents/GitHub/NIplayer/v2/player/kernel/src/main/java/com/nichx/niplayer/player/kernel/MediaSourceBuilder.kt#L58-L60) 注释明确"storage 随 NxMediaSource 传递给 PlayerViewModel 统一关闭"——SMB（DataSource 分支）是**设计内转移所有权**，不算泄漏；但 **WebDAV（Http 分支）的 storage 不随 source 传递**（NxMediaSource.Http 无 storage 引用），该分支的 WebDavStorage（含 OkHttp 连接池与 listCache）无任何归属方，属真实缺口。此外 `buildMediaSource` 抛异常的错误路径 storage 也无人关闭。
- 改进方向：Http 分支由调用方（PlayStarter/PlayerViewModel）显式 close；错误路径 `catch` 中 close。

#### H-N2 [Low] BackupViewModel 三处 WebDAV Storage 连接泄漏（复核后：Medium→Low，close 为空实现）
- 文件：[BackupViewModel.kt:87-108](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/settings/BackupViewModel.kt#L87-L108)（exportToWebDav）、[:126-132](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/settings/BackupViewModel.kt#L126-L132)（loadWebDavBackupFiles）、[:149-169](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/settings/BackupViewModel.kt#L149-L169)（restoreFromWebDav）
- 类别：资源泄漏
- 描述：三处 `storageFactory.create(library)` 后均无 `finally { storage.close() }`，与 StoragePlusViewModel.testConnection 的 try-finally 模式形成对比。每次备份/列文件/恢复都泄漏 WebDAV 连接。
- 改进方向：统一 `try { ... } finally { storage.close() }`。

#### H-N3 [Medium] 快速访问页文件书签不直接播放；openItem 死代码且 events 无人收集
- 文件：[QuickAccessScreen.kt:189-191](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/quickaccess/QuickAccessScreen.kt#L189-L191)、[QuickAccessViewModel.kt:200-219](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/quickaccess/QuickAccessViewModel.kt#L200-L219)
- 类别：功能缺失 + 死代码
- 描述：① Screen 对媒体文件书签统一走"打开文件浏览器"而非直接播放（与首页快速访问行为不一致）；② `openItem`（含 `startFromQuickAccess` 播放分流）全模块无调用方，死代码；③ Screen 无 `LaunchedEffect` collect `viewModel.events`，导航事件一旦被触发将静默丢失。
- 改进方向：Screen 调 `viewModel.openItem` + 增加 events collect；或删除死代码。

#### H-N4 [Medium] FileBrowserOverlay 关闭后 ViewModel 与 Storage 连接不释放
- 文件：[StorageFileScreen.kt:163](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/StorageFileScreen.kt#L163)、[HomeScreen.kt:68-71](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/HomeScreen.kt#L68-L71)
- 类别：生命周期 / 连接泄漏
- 描述：`hiltViewModel(key = "file_browser_$storageId")` 的 StoreOwner 是 Activity（overlay 非导航路由），关闭 overlay 只清状态不销毁 ViewModel；每次浏览不同存储源后，其 SMB/WebDAV 长连接存活到 Activity 销毁。
- 改进方向：overlay 关闭时主动销毁 VM（如 owner 改为临时 NavBackStackEntry），或在 onCleared 时机之外提供显式 release。

#### H-N5 [Low-Medium] 缩略图批量生成阻塞 recentPlays/quickAccessItems collect
- 文件：[HomeTabViewModel.kt:160-187](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/home/HomeTabViewModel.kt#L160-L187)、[PlayHistoryViewModel.kt:118-147](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/history/PlayHistoryViewModel.kt#L118-L147)
- 类别：性能
- 描述：`generateRemoteThumbnails` 用 `coroutineScope` 同步等待全部完成（无世代取消机制），SMB 源 + 大历史列表时 collect 协程被挂起数秒。
- 改进方向：加世代号取消 + 限流并发。

#### H-N6 [Low] 首页/历史/搜索续播与快速访问打开均无防抖
- 文件：[HomeTabViewModel.kt:448-480](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/home/HomeTabViewModel.kt#L448-L480)、[PlayHistoryViewModel.kt:247-257](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/history/PlayHistoryViewModel.kt#L247-L257)、[SearchViewModel.kt:136-168](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/search/SearchViewModel.kt#L136-L168)
- 类别：重复导航副作用
- 描述：快速连点会重复 `playbackRequestHolder.set` + 重复 `NavigateToPlayer`，与 H13 同类。
- 改进方向：ViewModel 层加"导航中"标志位或事件去抖。

#### H-N7 [Low] QuickAccessScreen 拖拽排序的 orderedItems 会被 items 重置
- 文件：[QuickAccessScreen.kt:92-93](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/quickaccess/QuickAccessScreen.kt#L92-L93)
- 类别：拖拽竞态
- 描述：`LaunchedEffect(items) { orderedItems = items }` 在拖拽中 items 重发时覆盖拖拽中的排序，`persistOrder` 提交错误顺序。
- 改进方向：拖拽期间暂停 items 同步（isDragging 标志）。

#### H-N8 [Low] StoragePlusViewModel.save/delete 无防重入
- 文件：[StoragePlusViewModel.kt:173-202](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/StoragePlusViewModel.kt#L173-L202)、[:206-224](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/home/src/main/java/com/nichx/niplayer/feature/home/library/StoragePlusViewModel.kt#L206-L224)
- 类别：防重入
- 描述：仅靠按钮 enabled 防抖，极端快速双击可能重复 insert / 重复 emit Saved + NavigateBack。
- 改进方向：VM 内 `isSaving`/`isDeleting` 入口保护。

---

## 四、player/kernel + core/database + core/network + core/subtitle 模块

### 旧缺陷状态核实

| 编号 | 状态 | 说明 |
| --- | --- | --- |
| K1 | ✅ 已修复 | 6 张含 `updated_at` 实体全部声明 `@ColumnInfo(defaultValue = "0")`；迁移链 6→7→8→9 产物与 v9 schema 完全一致，`validateMigration` 通过（[NiplayerDatabase.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/database/src/main/java/com/nichx/niplayer/database/NiplayerDatabase.kt)） |
| K2 | ⚠️ 部分修复 | `scanTagsInBlock` 已改为括号感知扫描，`\t` 内嵌套 tag 不再拆碎；**残留**：`parseTransformTag(tagBody.substring(2))` 未剥前导 `(`（[AssOverrideParser.kt:399-401](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/subtitle/src/main/java/com/nichx/niplayer/subtitle/renderer/AssOverrideParser.kt#L399-L401)），`"t(0,1000,\fs40)"` → content 为 `"(0,1000,\fs40"`，`parts[0]="(0"` 解析失败 → `t1=t2=0`，**插值时间窗丢失，动画退化为瞬时切换**（本人已核实） |
| K3 | ✅ 已修复 | 改为遍历 `startMsToIndex.headMap(effectiveMs, true).values` 所有桶（[SubtitleEngine.kt:194-211](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/subtitle/src/main/java/com/nichx/niplayer/subtitle/renderer/SubtitleEngine.kt#L194-L211)） |
| K4 | ⚠️ 部分修复 | `parseStyleColor` 已加 6 字符分支；但 ASS `&H` 经 `Style.getRGBValue` 输出 5 字符畸变串仍解析失败（[Style.java:112-119](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/subtitle/src/main/java/com/nichx/niplayer/subtitle/info/Style.java#L112-L119)），且 SSA 分支 `substring(6)+charAt(4)+charAt(2)+"ff"` 只取一位导致错色（红 → 品红）——修复 6 字符后行为反而更糟 |
| K5 | ❌ 仍存在 | `intValue == 1` 对 null 返回 false 而非 null（[BooleanConverter.kt:15-17](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/database/src/main/java/com/nichx/niplayer/database/converter/BooleanConverter.kt#L15-L17)） |

### 新发现缺陷

#### K-N1 [High] assrt 搜索响应 `lang` 字段建模为对象导致整个响应解析失败
- 文件：[AssrtModels.kt:35](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/network/src/main/java/com/nichx/niplayer/network/subtitle/AssrtModels.kt#L35)、[:42-45](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/network/src/main/java/com/nichx/niplayer/network/subtitle/AssrtModels.kt#L42-L45)
- 类别：响应模型与真实 API 不符
- 描述（**本人已核实模型与调用链**）：`AssrtSubDetail.lang` 声明为 `AssrtLang?`（对象 `{ desc: String? }`），而 assrt.net 搜索接口实际返回字符串（`"chs"`/`"cht"`/`"eng"`）。Moshi codegen 将 STRING 反序列化为对象时抛 `JsonDataException("Expected an object but was STRING")`，`AssrtSearchResponse` 整体解析失败 → [SubtitleSearchViewModel.kt:73](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/SubtitleSearchViewModel.kt#L73) 的 `response.sub?.subs` 永远拿不到，**在线字幕搜索功能不可用**。
- 改进方向：改为 `val lang: String? = null`；若需展示语言描述再做映射。

#### K-N2 [Medium] VideoDao.deleteByPathPrefix 的 LIKE 通配符未转义
- 文件：[VideoDao.kt:70-71](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/database/src/main/java/com/nichx/niplayer/database/dao/VideoDao.kt#L70-L71)
- 类别：误删数据（**本人已核实**）
- 描述：`DELETE FROM video WHERE file_path LIKE (:folderPath) || '/%'`，`folderPath` 含 `_`/`%` 时（如共享名 `Movies_2024`）通配符扩大匹配范围误删其他目录视频。同模块 [PlayHistoryDao](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/database/src/main/java/com/nichx/niplayer/database/dao/PlayHistoryDao.kt) 已做 `ESCAPE '\'` 转义，此处未做，两处不一致。
- 改进方向：与 PlayHistoryDao 相同的 `ESCAPE` 处理。

#### K-N3 [Medium] NxMedia3Player.selectSubtitleTrack 对 currentTracks 无 null 防护
- 文件：[NxMedia3Player.kt:568-584](file:///Users/nichx/Documents/GitHub/NIplayer/v2/player/kernel/src/main/java/com/nichx/niplayer/player/kernel/media3/NxMedia3Player.kt#L568-L584)
- 类别：NPE（**本人已核实**）
- 描述：`exoPlayer.currentTracks.groups` 直接解引用，prepare 前 `currentTracks` 为 null 即 NPE。对照 [selectAudioTrack L546-548](file:///Users/nichx/Documents/GitHub/NIplayer/v2/player/kernel/src/main/java/com/nichx/niplayer/player/kernel/media3/NxMedia3Player.kt#L546-L548) 有 `?: return` 防护，字幕路径缺失。
- 改进方向：与音频分支同样加空安全。

#### K-N4 [Low] SubtitleEngine 每帧全量扫描 headMap
- 文件：[SubtitleEngine.kt:194-211](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/subtitle/src/main/java/com/nichx/niplayer/subtitle/renderer/SubtitleEngine.kt#L194-L211)
- 类别：性能
- 描述：渲染层按帧调用 `update`，`headMap(effectiveMs, true)` 每帧遍历所有已开始字幕桶，长字幕文件播放后期每帧 O(n)。
- 改进方向：`headMap(...).descendingMap()` 倒序遍历 + 时间间隙提前终止；或维护"当前活动字幕集"增量更新。

#### K-N5 [Low] Time.java 秒字段单数字解析为 0
- 文件：[Time.java:25-42](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/subtitle/src/main/java/com/nichx/niplayer/subtitle/info/Time.java#L25-L42)
- 类别：解析正确性
- 描述：`"0:00:5.00"`（秒单数字）`substring(0,2)` 得 `"5."` 抛 NumberFormatException → 被解析为 0ms；且 `value.split(".")` 是正则误用（`.` 匹配任意字符）。字幕时间错位。
- 改进方向：按 `:`/`.` 拆分后 `toIntOrNull` 容错。

#### K-N6 [Low] BackupManager 备份不含 sync_delete_log
- 文件：[BackupManager.kt:64-71](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/database/src/main/java/com/nichx/niplayer/database/backup/BackupManager.kt#L64-L71)
- 类别：设计取舍（观察项）
- 描述：导出仅含四类表，不含 `sync_delete_log`；恢复后同步删除标记缺失，可能重新上传播放历史/已删记录。
- 改进方向：确认是否应随备份；若是则补齐。

#### K-N7 [Low] NxMedia3Player.bytesSinceTick 跨线程读写无同步
- 文件：[NxMedia3Player.kt:195-210](file:///Users/nichx/Documents/GitHub/NIplayer/v2/player/kernel/src/main/java/com/nichx/niplayer/player/kernel/media3/NxMedia3Player.kt#L195-L210)、[:337-373](file:///Users/nichx/Documents/GitHub/NIplayer/v2/player/kernel/src/main/java/com/nichx/niplayer/player/kernel/media3/NxMedia3Player.kt#L337-L373)
- 类别：并发可见性
- 描述：传输线程写入、主线程读取普通 Long，32 位设备上 64 位读写可能撕裂（仅影响网速显示抖动）。
- 改进方向：`@Volatile`。

---

## 五、core/thumbnail + core/designsystem + core/common + core/datastore + app + core/navigation 模块

### core/thumbnail（ThumbnailManager.kt）

#### T-1 [High] generateThumbnailAt 中 MediaDataSource 从未 close，SMB 句柄/预读线程泄漏
- 文件：[ThumbnailManager.kt:1974-1981](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L1974-L1981)、[:1999-2001](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L1999-L2001)
- 类别：资源泄漏（**本人已核实**）
- 描述：seek 预览路径两处 `storage.openMediaDataSource(file)` 返回的 `MediaDataSource` 均未 `close()`，外层 finally 只 release retriever。SMB 的 `SmbParallelInputStream` 携带 4 个预读线程 + SMB file handle，**每次拖动进度条泄漏一份**。对照 `generateFromDataSource`（[:928-931](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L928-L931)）已在 finally 中 `dataSource.close()`。
- 改进方向：finally 中补 `dataSource?.close()`。

#### T-2 [High] saveThumbnailFromBitmap 经 scaleToMaxWidth 回收调用方 Bitmap
- 文件：[ThumbnailManager.kt:720-733](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L720-L733)、[:1148-1155](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L1148-L1155)
- 类别：Bitmap 生命周期（**本人已核实**）
- 描述：注释明确"不能回收原 bitmap（BUG-P4 竞态保护）"，但当 `isHdr=false` 且图宽 > 480 时 `compensated === bitmap`，`scaleToMaxWidth` 内部 `if (it !== src) src.recycle()` 直接回收**调用方的 lastFrameBitmap**，与 [PlayerViewModel.kt:1990-1996](file:///Users/nichx/Documents/GitHub/NIplayer/v2/feature/player/src/main/java/com/nichx/niplayer/feature/player/PlayerViewModel.kt#L1990-L1996) 的 BUG-P4 修复冲突，可能触发 "Cannot draw a recycled Bitmap"。
- 改进方向：`saveThumbnailFromBitmap` 入口 `val src = if (bitmap.width > MAX_WIDTH) scaleToMaxWidth(bitmap.copy(...)...)` 保证调用方 bitmap 不被回收，或让 scaleToMaxWidth 提供"不回收入参"重载。

#### T-3 [Medium] releaseMutexIfIdle 在非 suspend lambda 调 tryLock + 锁移除竞态（复核后确认：High→Medium）
- 文件：[ThumbnailManager.kt:152-161](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L152-L161)
- 类别：并发防重失效
- 描述（**本人已核实代码**）：`computeIfPresent` lambda 中调 `Mutex.tryLock()`。工程 coroutines 版本 1.10.2（[libs.versions.toml:5](file:///Users/nichx/Documents/GitHub/NIplayer/v2/gradle/libs.versions.toml#L5)）中 `tryLock` 已非 suspend，可编译（<1.9 会编译失败，版本兼容隐患）；但语义上存在竞态窗口：协程 B 已拿到 Mutex 引用尚未进入 `withLock` 时，协程 A `tryLock` 成功并移除 Mutex，此后协程 C 新建 Mutex 与 B 并发生成同一缓存文件，per-key 防重失效。降为 Medium。
- 改进方向：改用"等待者计数"方案，或直接移除该清理逻辑（mutexMap 条目泄漏远小于并发防重失效的代价）。

#### T-4 [Low] generateThumbnailAtMs 在获取 Mutex 之前删除缓存文件（复核后：Medium→Low）
- 文件：[ThumbnailManager.kt:649-655](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L649-L655)
- 类别：并发竞态 / 缓存丢失
- 描述：`cacheFile.delete()` 在 `getMutex`/`withLock` 之前；与普通生成流程并发时可能删除对方刚写完的产物，且取帧失败后缓存永久缺失。
- 改进方向：delete 移入锁内。

#### T-5 [Low] 缓存命中不校验文件有效性（复核后：Medium→Low，需写入中断才触发）
- 文件：[ThumbnailManager.kt:192](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L192)、[:1481](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L1481)、[:1754](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L1754)
- 类别：损坏文件永久命中
- 描述：三处仅 `exists()` 不校验 `length() > 0`，与 `getCachedThumbnailPath`（[:454-457](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L454-L457)）行为不一致；生成中断残留的 0 字节文件永久命中显示损坏图。
- 改进方向：统一 `exists() && length() > 0`。

#### T-6 [Low] fetchAudioCoverFromApi 存在 Response 未关闭路径（复核后：Medium→Low）
- 文件：[ThumbnailManager.kt:548-568](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L548-L568)
- 类别：连接泄漏
- 描述：`body == null` 提前 return 与 `!isSuccessful` 分支均未 `response.close()`，连接不归还连接池。
- 改进方向：统一 `response.use { }` 包裹。

#### T-7 [Medium] generateRemoteThumbnails 上传并非 fire-and-forget
- 文件：[ThumbnailManager.kt:1627-1646](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L1627-L1646)、[:1704-1723](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L1704-L1723)
- 类别：注释与行为不符
- 描述：内层 `coroutineScope` 会等待所有上传完成才返回，SMB 批量列表被拖慢；注释声称 fire-and-forget。
- 改进方向：去内层 `coroutineScope`（或改 supervisorScope + 不等待）。

#### T-8 [Low] trimCacheIfNeeded(audioCacheDir) 会淘汰 .no_cover 标记文件
- 文件：[ThumbnailManager.kt:791-806](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L791-L806)
- 类别：重复网络开销
- 描述：按 lastModified 淘汰最旧文件会误删 `.no_cover` 标记，已确认无封面的音频下次扫描重新全量读取。
- 改进方向：淘汰时排除标记文件。

#### T-9 [Low] generateThumbnailAt http 分支 urlHeadersSucceeded 语义错误
- 文件：[ThumbnailManager.kt:1940-1959](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/thumbnail/src/main/java/com/nichx/niplayer/thumbnail/ThumbnailManager.kt#L1940-L1959)
- 类别：实例并存
- 描述：`getFrameAtTime` 返回 null 时仍置 `urlHeadersSucceeded = true`，旧 retriever 不被 release/置 null，紧接着创建 retriever2 时两个 MediaMetadataRetriever 实例并存（持有多余原生句柄）。
- 改进方向：仅在 `frame != null` 时置 true。

### core/designsystem

#### D-1 [Medium] 浅色方案 Teal / Purple 的 background 沿用蓝色默认值
- 文件：[NiColorSchemes.kt:184-198](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/NiColorSchemes.kt#L184-L198)、[:260-274](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/NiColorSchemes.kt#L260-L274)
- 类别：视觉配色
- 描述：Purple/Teal 浅色方案未覆盖 `background`，仍用 Blue 的 `0xFFF4F7FB` 冷蓝灰，与主色色相冲突；其余 10 套已显式覆盖。
- 改进方向：补两处 `background` 覆盖。

#### D-2 [Low] 动态 buildDarkExtra 与静态 NiExtraColors.DarkExtra 的 accentLight 语义冲突（复核后：降为 Low，静态值为死代码）
- 文件：[NiExtraColors.kt:97](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/NiExtraColors.kt#L97)、[NiColorSchemes.kt:872-885](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/NiColorSchemes.kt#L872-L885)
- 类别：配色一致性
- 描述：静态 `DarkExtra.accentLight = 0xFF003065`（深蓝）与动态 `buildDarkExtra` 返回 `0xFFC5E2FF`（浅蓝）语义相反，页面混用两种获取方式时深色模式装饰色不一致。
- 改进方向：统一一处来源。

#### D-3 [Low] NiTheme 每次重组重建 ColorScheme 与 NiExtraColors
- 文件：[Theme.kt:28-29](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/theme/Theme.kt#L28-L29)
- 类别：性能
- 描述：`NiSchemes.buildDark/Light` + `buildDarkExtra`（含 tonal 计算、Brush 构造）在每次重组无条件执行，配置变化（旋转/字体缩放）触发全量重建。
- 改进方向：`remember(darkTheme, scheme) { ... }` 缓存。

### core/designsystem components

#### C-1 [Medium] NiPopupMenu / NiFAB 用 isSystemInDarkTheme()，与 NiDialog 的应用主题判断不一致
- 文件：[NiPopupMenu.kt:43](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiPopupMenu.kt#L43)、[:82](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiPopupMenu.kt#L82)、[:112](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiPopupMenu.kt#L112)、[NiFAB.kt:39](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiFAB.kt#L39)、[:69](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiFAB.kt#L69)
- 类别：强制主题场景下视觉不一致
- 描述：应用内强制浅/深色与系统主题不一致时，菜单/FAB 与页面配色相反。
- 改进方向：统一 `NiExtraColors.current.isDark`。

#### C-2 [Low] NiBottomBar 未匹配路由时 Pill 位置与选中态（复核后：Medium→Low，当前调用方不可达）
- 文件：[NiBottomBar.kt:87-89](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiBottomBar.kt#L87-L89)、[:167](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiBottomBar.kt#L167)
- 类别：导航视觉一致性
- 描述：`tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)` 在进入二级页面（如 `player/player`）时钳制为 0，Pill 指示器滑到第一个 tab，但 `isSelected` 判定为 false，出现"Pill 高亮第一项但内容全部未选中"的矛盾视觉。
- 改进方向：未匹配路由时不驱动 Pill（保持隐藏或上一位置）。

#### C-3 [Medium] NiDrawerPanel 快速开合时关闭协程未取消，新打开的面板意外消失
- 文件：[NiDrawerPanel.kt:79-90](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiDrawerPanel.kt#L79-L90)
- 类别：状态竞态
- 描述：`visible=false` 时 `rememberCoroutineScope().launch { delay(300); shouldRender = false }` 不在 `LaunchedEffect` 作用域内；300ms 退场动画期间再次打开，旧协程仍会在约 300ms 后把 `shouldRender` 置 false，**刚打开的面板直接消失**。
- 改进方向：关闭协程改为受 `visible` 控制的 `LaunchedEffect`（自动取消），或加"打开世代"守卫。

#### C-4 [Low] NiDialogItem.iconTint / labelColor 字段为死代码
- 文件：[NiDialog.kt:320-321](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiDialog.kt#L320-L321)
- 类别：死代码
- 描述：`NiDialogItem` 定义了 `iconTint`/`labelColor`，但 `NiDialogItemRow`（[:209-257](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiDialog.kt#L209-L257)）从未读取，调用方传入的自定义颜色被静默忽略。
- 改进方向：接线或在字段处注明未实现。

#### C-5 [Low] NiSnackbarHost 连续发送相同消息不会刷新显示时长
- 文件：[NiSnackbarHost.kt:167-171](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiSnackbarHost.kt#L167-L171)
- 类别：交互细节
- 描述：`LaunchedEffect(message)` 以数据相等性为 key，相同消息再次 emit 时 key 不变、计时器不重启。
- 改进方向：改用事件计数/版本号作 key。

#### C-6 [Low] NiAutoSizeText 容器变宽后字号不恢复
- 文件：[NiHomeCards.kt:516-533](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/designsystem/src/main/java/com/nichx/niplayer/designsystem/components/NiHomeCards.kt#L516-L533)
- 类别：自适应文本
- 描述：`remember(text)` 使字号只在 text 变化时重置；旋转/窗口变宽后已缩小的字号不恢复（`onTextLayout` 只有缩小分支无放大分支）。
- 改进方向：以容器宽度为 key 重算，或加放大回弹逻辑。

### core/navigation

#### N-1 [Low] NiNavHost 默认 builder 为空 lambda，未注册 destination 时运行期崩溃
- 文件：[NiNavHost.kt:20-21](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/navigation/src/main/java/com/nichx/niplayer/navigation/NiNavHost.kt#L20-L21)
- 类别：健壮性
- 描述：默认 `builder = {}` + `startDestination = Routes.Home.ROOT`，调用方仅用默认参数时运行期 `IllegalArgumentException`（找不到 home destination）。
- 改进方向：builder 设为必填参数或默认抛明确提示。

#### N-2 [Low] storagePlusRoute 的 type 参数未做 URI 编码
- 文件：[Routes.kt:102](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/navigation/src/main/java/com/nichx/niplayer/navigation/Routes.kt#L102)
- 类别：健壮性
- 描述：直接拼接 `?type=$type` 未 `Uri.encode`，与 `storageFileRoute` 对 path 的编码不一致；当前 `MediaType.value` 无特殊字符暂安全。
- 改进方向：统一编码。

### app + core/common + core/datastore

#### B-1 [Medium] ThumbnailSettings 清除存储源级覆盖时未删除旧版遗留键，"跟随全局"失效
- 文件：[ThumbnailSettings.kt:99-120](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/datastore/src/main/java/com/nichx/niplayer/datastore/ThumbnailSettings.kt#L99-L120)
- 类别：配置回退
- 描述：`getLibraryGenerationMode` 回退读旧键 `thumbnail_lib_enabled_<id>`，但 `setLibraryGenerationMode(libId, null)` 只删新键不删遗留键；老用户设"跟随全局"后 getter 仍返回 ALL/OFF，设置被静默忽略。
- 改进方向：写入/清除新键时同步 `removeValueForKey(legacyKey)`。

#### B-2 [Medium] NiApplication 启动后台任务无异常防护，Room 初始化失败直接杀进程
- 文件：[NiApplication.kt:52-62](file:///Users/nichx/Documents/GitHub/NIplayer/v2/app/src/main/java/com/nichx/niplayer/NiApplication.kt#L52-L62)、[AppCoroutineScope.kt:33-38](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/common/src/main/java/com/nichx/niplayer/common/coroutine/AppCoroutineScope.kt#L33-L38)
- 类别：启动稳定性
- 描述：`appScope.launch { ensureLocalStorageExists() }` 的 scope 无 `CoroutineExceptionHandler`，任何 DB 临时性失败（锁/迁移）都落到 CrashHandler 默认处理器 → 启动即闪退并弹崩溃对话框。
- 改进方向：`ensureLocalStorageExists` 包 `runCatching`，或给 AppScope 注入异常处理器。

#### B-3 [Low] CrashHandler 安装时机晚于 Hilt 注入阶段（复核后：Medium→Low，属开发期错误）
- 文件：[NiApplication.kt:52-56](file:///Users/nichx/Documents/GitHub/NIplayer/v2/app/src/main/java/com/nichx/niplayer/NiApplication.kt#L52-L56)
- 类别：崩溃捕获窗口
- 描述：`@HiltAndroidApp` 字段注入发生在 `super.onCreate()` 调用链中，而 `crashHandler.install()` 在其后；依赖图构建期崩溃无法捕获。
- 改进方向：静态初始化块中提前安装（不依赖 Hilt 注入的处理器）。

#### B-4 [Low] NiApplication.onCreate 主线程同步 IO（MMKV + 崩溃日志读写）
- 文件：[NiApplication.kt:56-61](file:///Users/nichx/Documents/GitHub/NIplayer/v2/app/src/main/java/com/nichx/niplayer/NiApplication.kt#L56-L61)、[CrashHandler.kt:57-65](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/common/src/main/java/com/nichx/niplayer/common/crash/CrashHandler.kt#L57-L65)
- 类别：启动性能
- 描述：`MMKV.initialize`、`ThemeSettings.themeFlow.value`、`checkPreviousCrash()`（文件读+删）同步主线程；文件为 KB 级实际耗时毫秒级，不构成 ANR，但崩溃日志读写属可后台化的磁盘 IO。
- 改进方向：`consumePreviousCrash` 移入后台协程后回主线程赋值。

#### B-5 [Low] MainActivity 崩溃提示在配置变更/重建后重复弹出
- 文件：[MainActivity.kt:104-106](file:///Users/nichx/Documents/GitHub/NIplayer/v2/app/src/main/java/com/nichx/niplayer/MainActivity.kt#L104-L106)
- 类别：UI 状态
- 描述：`previousCrashLog` 内存字段消费后不置 null，Activity 重建后 `remember` 重新读取再次弹窗。
- 改进方向：对话框 onDismiss 时置空字段，或用一次性标记。

#### B-6 [Low] StateFlow 读改写非原子（理论丢失更新）
- 文件：[FileBrowserSettings.kt:58-91](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/datastore/src/main/java/com/nichx/niplayer/datastore/FileBrowserSettings.kt#L58-L91)、[ThemeSettings.kt:52-61](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/datastore/src/main/java/com/nichx/niplayer/datastore/ThemeSettings.kt#L52-L61)
- 类别：并发健壮性（当前均为 UI 线程写入，不触发）
- 描述：`_flow.value = _flow.value.copy(...)` 非原子；MMKV 层本身线程安全，此条仅为提示。
- 改进方向：`MutableStateFlow.update {}`。

#### B-7 [Low] CrashHandler 缺幂等保护与文件写入原子性
- 文件：[CrashHandler.kt:38-49](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/common/src/main/java/com/nichx/niplayer/common/crash/CrashHandler.kt#L38-L49)
- 类别：健壮性
- 描述：`install()` 无幂等保护（多进程/重复调用时日志链式重复写入）；多线程同时崩溃时 `writeText()` 非原子。**无死循环**——写入异常已捕获，委托 previous 在 try 之外但 Android 默认 handler 不抛异常。
- 改进方向：`install` 加 AtomicBoolean；写日志用原子临时文件+rename。

#### B-8 [Low] VideoExtensionSettings 空输入无法持久化
- 文件：[VideoExtensionSettings.kt:41-50](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/datastore/src/main/java/com/nichx/niplayer/datastore/VideoExtensionSettings.kt#L41-L50)
- 类别：配置边界
- 描述：`normalized.isEmpty()` 时不写 MMKV，用户清空扩展名后 UI 仍显示默认列表。
- 改进方向：支持显式空列表语义（如 `""` 哨兵值）。

#### B-9 [Low] LrcApiSettings.isConfigured 对空白/非法串误判
- 文件：[LrcApiSettings.kt:20-21](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/datastore/src/main/java/com/nichx/niplayer/datastore/LrcApiSettings.kt#L20-L21)
- 类别：配置校验
- 描述：`apiUrl.isNotEmpty()` 把纯空白/非 URL 判为已配置。
- 改进方向：`isNotBlank()` + 基础 URL 校验。

#### B-10 [Low] AndroidManifest allowBackup=true 使凭证数据随云备份外泄
- 文件：[AndroidManifest.xml:20](file:///Users/nichx/Documents/GitHub/NIplayer/v2/app/src/main/AndroidManifest.xml#L20)
- 类别：安全
- 描述：MMKV（含 `assrt_token`、`api_auth`）随系统备份上传；建议通过 dataExtractionRules/backupRules 排除凭证或关闭备份。
- 改进方向：配置备份规则排除凭证相关文件。

#### B-11 [Low] PlayerSettings 黑边缓存 API 无调用方（死代码）
- 文件：[PlayerSettings.kt:98-117](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/datastore/src/main/java/com/nichx/niplayer/datastore/PlayerSettings.kt#L98-L117)
- 类别：死代码
- 描述：`saveBlackBarCache`/`loadBlackBarCache`/`clearBlackBarCache` 全库无调用者。
- 改进方向：补齐"智能黑边检测"缓存接线或移除。

---

## 六、修复优先级建议

### P0（功能性缺陷，直接影响可用性，建议立即修复）

> 复核后：三个 High 全部确认，无变更。

| 编号 | 问题 | 修复量级 |
| --- | --- | --- |
| K-N1 | assrt `lang` 建模为对象导致字幕搜索整体失效 | 1 行（改 `String?`） |
| T-1 | seek 预览 MediaDataSource 未 close（SMB 每次拖动泄漏 4 线程+句柄） | finally 补 close |
| T-2 | `saveThumbnailFromBitmap` 回收调用方 Bitmap（与 BUG-P4 冲突） | 入口 copy 保护 |

### P1（数据完整性 / 崩溃风险，建议优先处理）

| 编号 | 问题 |
| --- | --- |
| S-N4 | 下载提前 EOF 仍标记 COMPLETED，损坏文件入库 |
| K-N2 | `deleteByPathPrefix` LIKE 通配符未转义，可能误删视频记录 |
| K-N3 | `selectSubtitleTrack` 空指针（prepare 前切字幕崩溃） |
| P-N1 | retry 与 play 的 storage close 并发竞态（复核后由 P0 降入，加 `!==` 判断即防） |
| K2 残留 | `\t` 动画时间窗 off-by-one（`substring(2)` 未剥括号） |
| K4 残留 | ASS `&H` 颜色失效 + SSA 错色回归（`Style.getRGBValue` 取位错误） |
| B-2 | 启动后台 DB 失败经 CrashHandler 杀进程 |

### P2（并发/取消语义/连接生命周期）

- S-N1（6 处 catch-all 吞取消，统一前置 `catch (CancellationException) { throw e }`）
- P-N2（播放模式接线）、P-N3（封面解码竞态）、P7（前台服务占位通知接入）
- H-N3（快速访问直接播放 + events collect）
- T-3（mutex 移除竞态）

### P3（视觉/体验/健壮性）

- D-1（Teal/Purple 浅色背景）、D-3（主题缓存）、D-2（静态 DarkExtra 死代码清理）
- C-1（菜单/FAB 主题判断统一）、C-3（DrawerPanel 关闭协程竞态）、C-5（Snackbar 刷新）、C-6（AutoSize 回弹）、C-4（DialogItem 死字段）
- H5（进度 -1 移入世代守卫）、H6（goUp 双栈同步）、H14（路径双重导航）、H-N6/H13（导航防抖）
- 复核后由 P2/P1 降入的 Low 项：H-N2（Backup close 空实现，仅代码一致性）、H-N4（overlay VM 复用为刻意设计，建议补注释）、S-N2（诊断路径吞取消）、S-N5（首启并发扫描，建议加 Mutex 防重）、S-N6（skip 死 chunk 容量损耗）、T-4（锁外删缓存）、T-5（缓存有效性校验）、T-6（Response 关闭）、C-2（BottomBar Pill 兜底）、B-3（启动期崩溃捕获窗口）、B-8（扩展名空输入 UX 反馈）
- 其余 Low 项（S10/S11/S12、P10/P11/P12、K5、K-N4~K-N7、T-7~T-9、N-1/N-2、B-4~B-7、B-9~B-11）
- **P-N6 已判定不成立**（音频永不进入 PlayerScreen，retryPlayback/restartFromStart 对音频不可达），无需处理

---

## 附录：跨模块共性改进方向（长期）

1. **取消语义纪律**：全库 `catch (Exception)`/`runCatching` 统一要求前置 `catch (CancellationException) { throw e }`（storage 模块上一轮已修复 6 处、本轮确认仍残留 6 处：readFileBytes/deleteFile/ping/openPlayStream/openMediaDataSource/saveFile）。建议加 detekt/ktlint 自定义规则，或在 Review 清单中固定检查项。
2. **资源生命周期单一归属**：Storage 的创建/关闭应形成单一所有权链（谁创建谁负责 close，或显式转移所有权并记录）。复核后实际缺口收窄为：PlayStarter WebDAV Http 分支（SMB DataSource 分支已由 PlayerViewModel/AudioPlaybackManager 接管）与 `buildMediaSource` 错误路径；Backup 三处因 `close()` 为空实现仅属代码一致性。建议统一为"调用方 try-finally + 显式交接点"模式。
3. **导航副作用收敛**：所有 `_navigationEvent.emit` 建议统一走"一次性消费 + 防抖/导航中标志"，避免 H13/H14/H-N6 类重复 push。
4. **防重入**：ViewModel 层为耗时操作（save/delete/扫描/打开）统一加"进行中"标志位，不依赖按钮 enabled 状态。
5. **缓存一致性**：ThumbnailManager 缓存命中/查询/淘汰三处对"文件有效性"的判定需统一（`exists() && length() > 0`），标记文件（`.no_cover`）与缓存文件分开管理。
6. **测试补充建议**：为 AssrtModels 增加 Moshi 反序列化单测（固定真实 API 响应样本，覆盖 `lang` 为字符串的场景）；为 `parseTransformTag` 与 `Style.getRGBValue` 增加解析单测；为 Room `deleteByPathPrefix` 增加含 `_`/`%` 路径的用例；为 DownloadManager 增加"内容短于声明长度"的集成用例；为 VideoScanner 增加并发扫描防重单测。历史 schema JSON（5-8 被当前实体覆盖导出）建议重建发布时的真实快照，否则 MigrationTestHelper 校验会"虚假通过"。

---

> 生成方式：并行子代理全量阅读 + 主代理关键结论回读核实；2026-08-02 追加第二轮逐条代码复核（5 组验证代理，44 条 CONFIRMED / 18 条 CORRECTED / 1 条 REJECTED）。修正后有效缺陷 62 条（High 3 / Medium 17 / Low 42），个别行号可能因后续提交漂移。

---

## 七、全部 Bug 清单总表（按优先级 · 进度跟踪）

> 用途：将全部待处理缺陷按 P0~P3 优先级汇总为可标记的跟踪表。新缺陷编号与正文一~五章小节一一对应（⌘F 可直接定位）；旧缺陷编号对应 2026-07-31 报告（BUG_REVIEW_REPORT.md）。**状态列**建议填写 `待修复` / `进行中` / `已修复`；旧缺陷已预填核实结论（`部分修复` / `仍存在`），修复完成后改为 `已修复` 即可。

### 汇总统计

| 优先级 | 本轮新缺陷 | 旧缺陷待处理 | 合计 | 定位说明 |
| --- | --- | --- | --- | --- |
| P0 立即修复 | 3 | 0 | 3 | 功能不可用 / 高价值资源泄漏 |
| P1 数据/崩溃/核心功能 | 6 | 6 | 12 | 数据完整性 / 崩溃风险 / 核心功能失效 |
| P2 并发/取消/生命周期 | 8 | 8 | 16 | 并发竞态 / 取消语义 / 连接生命周期 |
| P3 视觉/体验/健壮性 | 45 | 10 | 55 | 其余全部 Low 项 |
| **合计** | **62** | **24** | **86** | 另有 P-N6 已判定不成立，无需处理 |

### P0（立即修复）

| 编号 | 严重度 | 模块 | 问题概述 | 状态 |
| --- | --- | --- | --- | --- |
| K-N1 | High | core/network | assrt 搜索 `lang` 建模为对象，整个响应解析失败（字幕搜索失效） |  |
| T-1 | High | core/thumbnail | `generateThumbnailAt` 中 MediaDataSource 从未 close，SMB 句柄/预读线程泄漏 |  |
| T-2 | High | core/thumbnail | `saveThumbnailFromBitmap` 经 scaleToMaxWidth 回收调用方 Bitmap |  |

### P1（数据完整性 / 崩溃风险 / 核心功能失效）

**本轮新缺陷**

| 编号 | 严重度 | 模块 | 问题概述 | 状态 |
| --- | --- | --- | --- | --- |
| S-N4 | Medium | core/storage | pipelinedWriteLoop 提前 EOF 仍标记 COMPLETED，损坏文件入库 |  |
| K-N2 | Medium | core/database | `deleteByPathPrefix` 的 LIKE 通配符未转义，可能误删视频记录 |  |
| K-N3 | Medium | player/kernel | `selectSubtitleTrack` 对 currentTracks 无 null 防护（prepare 前切字幕崩溃） |  |
| P-N1 | Medium | feature/player | retry() 与 play() 的 storage close 并发竞态，远程音频重试失效 |  |
| B-1 | Medium | core/datastore | ThumbnailSettings 清除存储源级覆盖未删旧版遗留键，"跟随全局"失效 |  |
| B-2 | Medium | app | NiApplication 启动后台任务无异常防护，Room 失败直接杀进程 |  |

**旧缺陷（部分修复）**

| 编号 | 严重度 | 模块 | 问题概述 | 状态 |
| --- | --- | --- | --- | --- |
| P1 | 部分修复 | feature/player | 后台自动切歌仍失效（VM 销毁后回调为 null，音频停在曲尾） | 部分修复 |
| K2 | 部分修复 | core/subtitle | `\t` 动画时间窗 off-by-one（substring(2) 未剥括号），退化为瞬时切换 | 部分修复 |
| K4 | 部分修复 | core/subtitle | ASS `&H` 颜色失效 + SSA 错色回归（getRGBValue 取位错误） | 部分修复 |
| S5 | 部分修复 | core/storage | prefix 置空在 connectMutex 之外，置空→重建窗口可能拼出错误 URL | 部分修复 |
| P4 | 部分修复 | feature/player | retryPlayback/restartFromStart 无 isAudio 分支（防御缺口） | 部分修复 |
| P6 | 部分修复 | feature/player | 封面缓存为 null 时仍主线程 BitmapFactory.decodeFile（卡顿兜底） | 部分修复 |

### P2（并发 / 取消语义 / 连接生命周期）

**本轮新缺陷**

| 编号 | 严重度 | 模块 | 问题概述 | 状态 |
| --- | --- | --- | --- | --- |
| S-N1 | Medium | core/storage | SmbStorage 6 处 catch-all 吞/包装 CancellationException |  |
| S-N3 | Medium | core/storage | 断点续传打开流的 CancellationException 被吞，暂停/取消响应延迟 |  |
| P-N2 | Medium | feature/player | 播放模式（顺序/随机/单曲循环）纯 UI 摆设，未接线 |  |
| P-N3 | Medium | feature/player | 封面预解码竞态：旧封面覆盖新封面 + 旧 Bitmap 不回收 |  |
| H-N3 | Medium | feature/home | 快速访问书签不直接播放；openItem 死代码且 events 无人收集 |  |
| H-N4 | Medium | feature/home | FileBrowserOverlay 关闭后 VM 与 Storage 连接不释放 |  |
| T-3 | Medium | core/thumbnail | releaseMutexIfIdle 在非 suspend lambda 调 tryLock + 锁移除竞态 |  |
| T-7 | Medium | core/thumbnail | generateRemoteThumbnails 上传并非 fire-and-forget |  |

**旧缺陷（仍存在）**

| 编号 | 严重度 | 模块 | 问题概述 | 状态 |
| --- | --- | --- | --- | --- |
| P7 | 仍存在 | feature/player | onCreate 仅 player!=null 才 startForeground；buildPlaceholderNotification 死代码 | 仍存在 |
| S10 | 仍存在 | core/storage | WebDAV listCache/listCacheTimestamps 只增不减，close 不清空 | 仍存在 |
| S11 | 仍存在 | core/storage | 无 share 时 SmbFile 构造后直接 return true，未真正探测 | 仍存在 |
| S12 | 仍存在 | core/storage | WebDAV openMediaDataSource 直接透传 length，无 <=0 保护 | 仍存在 |
| H5 | 仍存在 | feature/home | `_thumbnailProgress=-1` 在世代守卫之外 | 仍存在 |
| H6 | 仍存在 | feature/home | goUp 的 entryPathStack 移除与 directoryStack 不同步 | 仍存在 |
| H13 | 仍存在 | feature/home | 下载"打开"无防抖，连点重复 push 播放页 | 仍存在 |
| H14 | 仍存在 | feature/home | initialize 内部与 LaunchedEffect(initialPath) 双重导航 | 仍存在 |

### P3（视觉 / 体验 / 健壮性）

**本轮新缺陷**

| 编号 | 严重度 | 模块 | 问题概述 | 状态 |
| --- | --- | --- | --- | --- |
| S-N2 | Low | core/storage | WebDavStorage.createDirectoryViaPutFallback 吞取消 |  |
| S-N5 | Low | core/storage | VideoScanner 首启并发全量扫描 + REPLACE 重置用户字段 |  |
| S-N6 | Low | core/storage | SmbParallelInputStream.skip 与预读线程竞态（死 chunk 堆积） |  |
| S-N7 | Low | core/storage | close 读取非 volatile channels/threads，可能漏关句柄 |  |
| S-N8 | Low | core/storage | StorageDataSource.open 超时取消后已创建的播放流泄漏 |  |
| S-N9 | Low | core/storage | uploadFile 的 RequestBody 在 writeTo 内关闭 inputStream |  |
| S-N10 | Low | core/storage | WebDavMediaDataSource.readAt 与 close 竞态 |  |
| P-N4 | Low | feature/player | 音频暂停态 seek 后进度条不回弹 |  |
| P-N5 | Low | feature/player | MusicBar 点击后 isInteracting 无重置，卡片永久不透明 |  |
| P-N7 | Low | feature/player | MusicMetadataService 网络请求不可取消 |  |
| H-N1 | Low | feature/home | PlayStarter 创建的 Storage 生命周期归属不清（WebDAV Http 分支） |  |
| H-N2 | Low | feature/home | BackupViewModel 三处 WebDAV Storage 连接泄漏 |  |
| H-N5 | Low-Medium | feature/home | 缩略图批量生成阻塞 recentPlays/quickAccessItems collect |  |
| H-N6 | Low | feature/home | 首页/历史/搜索续播与快速访问打开均无防抖 |  |
| H-N7 | Low | feature/home | 拖拽排序的 orderedItems 会被 items 重置 |  |
| H-N8 | Low | feature/home | StoragePlusViewModel.save/delete 无防重入 |  |
| K-N4 | Low | core/subtitle | SubtitleEngine 500ms 线性前缀扫描 headMap |  |
| K-N5 | Low | core/subtitle | Time.java 秒字段单数字解析为 0 |  |
| K-N6 | Low | core/database | BackupManager 备份不含 sync_delete_log（预留表，无实际路径） |  |
| K-N7 | Low | player/kernel | NxMedia3Player.bytesSinceTick 跨线程读写无同步 |  |
| T-4 | Low | core/thumbnail | generateThumbnailAtMs 在获取 Mutex 之前删除缓存文件 |  |
| T-5 | Low | core/thumbnail | 缓存命中不校验文件有效性（需写入中断才触发） |  |
| T-6 | Low | core/thumbnail | fetchAudioCoverFromApi 存在 Response 未关闭路径 |  |
| T-8 | Low | core/thumbnail | trimCacheIfNeeded(audioCacheDir) 会淘汰 .no_cover 标记文件 |  |
| T-9 | Low | core/thumbnail | generateThumbnailAt http 分支 urlHeadersSucceeded 语义错误 |  |
| D-1 | Medium | core/designsystem | 浅色 Teal/Purple 的 background 沿用蓝色默认值 |  |
| D-2 | Low | core/designsystem | 动态 buildDarkExtra 与静态 DarkExtra 的 accentLight 语义冲突（死代码） |  |
| D-3 | Low | core/designsystem | NiTheme 每次重组重建 ColorScheme 与 NiExtraColors |  |
| C-1 | Medium | core/designsystem | NiPopupMenu/NiFAB 用 isSystemInDarkTheme()，与 NiDialog 不一致 |  |
| C-2 | Low | core/designsystem | NiBottomBar 未匹配路由时 Pill 位置与选中态（当前不可达） |  |
| C-3 | Medium | core/designsystem | NiDrawerPanel 快速开合时关闭协程未取消，面板意外消失 |  |
| C-4 | Low | core/designsystem | NiDialogItem.iconTint/labelColor 字段为死代码 |  |
| C-5 | Low | core/designsystem | NiSnackbarHost 相同消息不刷新显示时长 |  |
| C-6 | Low | core/designsystem | NiAutoSizeText 容器变宽后字号不恢复 |  |
| N-1 | Low | core/navigation | NiNavHost 默认 builder 为空 lambda（当前不可达） |  |
| N-2 | Low | core/navigation | storagePlusRoute 的 type 参数未做 URI 编码（当前不可触发） |  |
| B-3 | Low | app | CrashHandler 安装时机晚于 Hilt 注入阶段 |  |
| B-4 | Low | app | NiApplication.onCreate 主线程同步 IO（MMKV + 崩溃日志） |  |
| B-5 | Low | app | MainActivity 崩溃提示在配置变更后重复弹出 |  |
| B-6 | Low | core/common | StateFlow 读改写非原子（理论丢失更新） |  |
| B-7 | Low | app | CrashHandler 缺幂等保护与文件写入原子性 |  |
| B-8 | Low | core/datastore | VideoExtensionSettings 空输入无法持久化（无 UX 反馈） |  |
| B-9 | Low | core/datastore | LrcApiSettings.isConfigured 对空白/非法串误判 |  |
| B-10 | Low | app | AndroidManifest allowBackup=true 使凭证随云备份外泄 |  |
| B-11 | Low | core/datastore | PlayerSettings 黑边缓存 API 无调用方（死代码） |  |

> 注：D-1 / C-1 / C-3 为 Medium 严重度，因属视觉/交互一致性，按原报告归类于 P3。

**旧缺陷（仍存在）**

| 编号 | 严重度 | 模块 | 问题概述 | 状态 |
| --- | --- | --- | --- | --- |
| P10 | 仍存在 | feature/player | VinylRecordPlayer 黑胶盘 Bitmap 无 null 兜底 | 仍存在 |
| P11 | 仍存在 | feature/player | PlaylistSheet key 用 filePath，重复路径会崩 | 仍存在 |
| P12 | 仍存在 | feature/player | describePlaybackError 反射回退死代码 | 仍存在 |
| H7 | 仍存在 | feature/home | 下载"打开"裸文件名 vs 完整路径定位不匹配 | 仍存在 |
| H8 | 仍存在 | feature/home | open* 失败静默无反馈 | 仍存在 |
| H9 | 仍存在 | feature/home | 撤销删除 copy(id=0) 新主键，关联表孤儿 | 仍存在 |
| H10 | 仍存在 | feature/home | connectionsValidated 时机过早，quickAccessItems 恒空 | 仍存在 |
| H11 | 仍存在 | feature/home | initialized 死代码 + 重初始化被锁死 | 仍存在 |
| H12 | 仍存在 | feature/home | addExtendFolder 无防抖/Mutex | 仍存在 |
| K5 | 仍存在 | core/database | BooleanConverter intValue==1 对 null 返回 false 而非 null | 仍存在 |

> P-N6（retryPlayback / restartFromStart 缺音频分支）经第二轮复核判定**不成立**（音频永不进入 PlayerScreen，对音频不可达），不列入上表，无需处理。
