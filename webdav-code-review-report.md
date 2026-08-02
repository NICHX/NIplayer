# WebDAV 实现代码审查报告

## 审查范围
- [WebDavStorage.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt)
- [WebDavMediaDataSource.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavMediaDataSource.kt)
- [WebDavHttpException.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavHttpException.kt)（WebDavStorage.kt 内声明）
- [Storage.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/Storage.kt)（接口定义）
- [StorageFactory.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/StorageFactory.kt)（创建入口）
- [AbstractStorage.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/AbstractStorage.kt)
- [NetworkModule.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/network/src/main/java/com/nichx/niplayer/network/NetworkModule.kt)（OkHttp 客户端配置）

---

## 高危 (Critical)

### C-01. `SimpleDateFormat` 线程不安全 **[已修复]**

**文件**: [WebDavStorage.kt:L850-L856](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L850-L856)

**问题**: `DATE_FORMATS` 定义为 `companion object` 的 `val`，包含三个 `SimpleDateFormat` 实例。`SimpleDateFormat` 是**有状态非线程安全类**，其 `parse()` 修改内部 `Calendar` 字段。

```kotlin
private val DATE_FORMATS = listOf(
    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
).also { it.forEach { fmt -> fmt.timeZone = TimeZone.getTimeZone("GMT") } }

private fun parseHttpDate(value: String?): Long? {
    if (value == null) return null
    for (format in DATE_FORMATS) {
        try {
            return format.parse(value)?.time  // ← 并发调用 parse() 产生数据竞争
        } catch (_: Exception) {}
    }
    return null
}
```

`parseHttpDate` 被 `parseResponseEntry` 调用，而 `parseResponseEntry` 在 `propfind` 的 XML 解析路径中。`listFiles` 是 `suspend` 函数，多个协程可以**并发**调用，共享的 `SimpleDateFormat` 实例会同时被多个线程操作，导致：
- 日期解析返回错误结果
- 抛出 `NumberFormatException` 或 `ArrayIndexOutOfBoundsException`

**修复**: 将 `DATE_FORMATS` 改为 `ThreadLocal` 包装，每个线程拥有独立的 `SimpleDateFormat` 副本，消除并发竞争。

---

### C-02. `WebDavMediaDataSource.trimCache()` 在并发清空时抛 `NoSuchElementException` **[已修复]**

**文件**: [WebDavMediaDataSource.kt:L321-L325](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavMediaDataSource.kt#L321-L325)

**问题**: `trimCache()` 使用先检查 `size > MAX_CACHE_ENTRIES`、后调 `firstKey()` 的 check-then-act 模式。`ConcurrentSkipListMap` 的方法间不具备原子性，若两个预读线程同时完成并同时调用 `trimCache()`：

```kotlin
private fun trimCache() {
    if (prefetchCache.size < MAX_CACHE_ENTRIES) return
    if (prefetchBase >= 0) {
        prefetchCache.headMap(prefetchBase).clear()  // 线程A 清空所有条目
    }
    while (prefetchCache.size > MAX_CACHE_ENTRIES) {
        val first = prefetchCache.firstKey()  // ← 线程B 此时 Map 已空，抛 NoSuchElementException
        prefetchCache.remove(first)
    }
}
```

`doPrefetchChunk` 只 catch `IOException`，`NoSuchElementException` 会传播到 `ExecutorService` 的未捕获异常处理器，后台线程静默消亡，预读任务丢失。

**修复**: 改用 `pollFirstEntry()` 替代 `firstKey()` + `remove(first)`，在 Map 为空时返回 `null` 不抛异常。

---

### C-03. `parseResponseEntry` 自身引用目录过滤失效 **[已修复]**

**文件**: [WebDavStorage.kt:L640-L647](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L640-L647)

**问题**: `parseResponseEntry` 通过 `rawHref == requestedPath` 过滤 PROPFIND 返回的"当前目录自身"条目。但 href 的格式完全取决于服务器实现（完整 URL、路径、反向代理等），与 `requestedPath`（请求 URL 的路径部分）格式不一致时比较失效，自身目录作为普通目录出现在文件列表中。

```kotlin
val rawHref = href?.trimEnd('/') ?: return null
if (rawHref == requestedPath) return null  // ← 格式不一致时永远不相等
```

例如，4 级以下目录文件较少，多余的自身条目更容易被用户注意到，表现为"多显示一个上级目录"。

**修复**: 改用 `computeRelativePath(href)` 计算相对路径后，与 `requestedPath` 剥离 baseUrlPath 后得到的目录路径比较。无论 href 是完整 URL、路径、还是反向代理路径，只要指向的是被列目录自身，其相对路径必然与被列目录的内部路径一致，过滤逻辑对所有服务器实现皆可靠。

---

## 中危 (Medium)

### M-01. `launchPrefetch` Cancel Flag 竞态 **[已修复]**

**文件**: [WebDavMediaDataSource.kt:L244-L253](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavMediaDataSource.kt#L244-L253)

**问题**: `launchPrefetch` 通过 `prefetchCancelFlag.set(true)` 然后立即 `set(false)` 来"取消"旧批次的预读。但这个窗内没有等待旧线程实际看到 true 标志：

```kotlin
private fun launchPrefetch(basePos: Long) {
    prefetchCancelFlag.set(true)   // ← 设置取消
    prefetchCancelFlag.set(false)  // ← 立即重置，旧线程可能还没检查标志
    prefetchBase = basePos
    ...
}
```

旧批次中尚未开始执行的 `FutureTask`（在 `ExecutorService` 队列中等待），在开始执行时看到的是 `false`，因此它们会继续执行，造成 3 个无用的 HTTP 请求和 CPU 浪费。

**修复**: 将 `AtomicBoolean` flag 改为 `AtomicLong` 世代计数器。`launchPrefetch` 在启动时 `incrementAndGet()` 新世代号，传递给 `doPrefetchChunk`。后台任务在入口处比对 `prefetchGeneration.get() == generation`，不一致则放弃。取消语义改为 `incrementAndGet()`，无需 set/reset 切换。

---

### M-02. `WebDavHttpException` 继承 `IOException` 设计提醒

**文件**: [WebDavStorage.kt:L48-L70](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L48-L70)

**问题**: `WebDavHttpException` 继承 `IOException`，被 catch 为 `IOException` 时调用方可能误认为这是网络异常并重试，但 401/403 不应重试。当前所有调用方已正确先 catch `WebDavHttpException` 再 catch `IOException`，设计上无问题。**仅作提醒**，无需代码修复。

---

### M-03. `openInputStream` 对 Range-ignorant 服务器无续传回退 **[已修复]**

**文件**: [WebDavStorage.kt:L181-L182](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L181-L182)

**问题**: `if (offset <= 0) return null`——当 `offset == 0` 时直接返回 null。当前调用方（DownloadManager）保证 offset>0 时才调用此方法，所以无实际风险，但语义不严谨。

**修复**: 将条件改为 `if (offset < 0) return null`，`offset == 0` 时回退到 `openInputStream(file)`。

---

## 低危 (Low)

### L-01. `saveFile` 重试路径打印完整异常栈

**文件**: [WebDavStorage.kt:L270-L285](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L270-L285)

`saveFile` 的重试路径在 IOException 时打印完整异常栈。初次异常和重试异常都打印堆栈，日志中可能产生噪声。**不影响功能**。

---

### L-02. `createDirectory` 的 PROPFIND 检查不与 `fileExists` 复用 **[已修复]**

**文件**: [WebDavStorage.kt:L286-L292](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L286-L292)

`createDirectory` 在检查目录是否存在时，手动实现了 PROPFIND 请求而非调用 `fileExists`，违反 DRY 原则。

**修复**: 改为调用 `runCatching { fileExists(path) }.getOrDefault(false)`，复用 `fileExists` 已有实现。

---

### L-03. `propfind` 方法未用 `response.use()` 包装 **[已修复]**

**文件**: [WebDavStorage.kt:L504-L518](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L504-L518)

`propfind` 方法手动管理 `response.close()` 和 `body.use {}`，与其他方法（`httpPut`、`fileExists`、`deleteFile`）的 `response.use {}` 模式不一致。

**修复**: 改为 `client.newCall(request).execute().use { response -> ... }` 统一模式。

---

### L-04. `httpGet` 返回 `ResponseInputStream` 持有 response 引用

**文件**: [WebDavStorage.kt:L870-L909](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt#L870-L909)

`ResponseInputStream` 包装了 `body` 和 `response`。若调用方忘记 close 这个 InputStream，将导致 HTTP 连接泄漏。当前所有调用方都用 `use {}` 确保 close，无实际问题。

---

### L-05. `thumbnailGeneration` AtomicLong 世代检查

`thumbnailGeneration.incrementAndGet()` 在 `listDirectory` 中调用，`generateThumbnailUrls` 是 fire-and-forget 的 Job。通过 `generation == thumbnailGeneration.get()` 在 finally 块检查世代，**当前实现正确**。

---

## 设计建议 (Suggestion)

### S-01. `Dispatcher.maxRequestsPerHost = 10` 与 `thumbnailConcurrency = 6` 组合

[NetworkModule.kt:L46](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/network/src/main/java/com/nichx/niplayer/network/NetworkModule.kt#L46) 设置了 `maxRequestsPerHost = 10`。6 路缩略图 + 列表浏览 PROPFIND + 文件下载 GET + 心跳 HEAD 同时进行时，总请求数可能接近或超过 10。如遇请求排队可考虑增大到 16。

---

### S-02. `WebDavMediaDataSource` 的 `readAt` 在 200 响应时全量下载但只读 1MB

当服务器不支持 Range（返回 200 而非 206），代码只读取 `BUFFER_SIZE = 1MB` 到 `bufferBytes`，剩余响应体被丢弃，浪费大量带宽。

---

### S-03. 预读 buffer 最大占用内存 ~2.5MB 无动态调整

`WebDavMediaDataSource` 固定分配 1MB `bufferBytes` + 最多 3×512KB = 1.5MB prefetchCache + 64KB readBuffer ≈ 2.56MB。在低端设备上批量生成缩略图时多个实例可能导致 OOM。

---

## 修复总结

| 编号 | 等级 | 问题 | 修复状态 |
|------|------|------|----------|
| C-01 | 高危 | `SimpleDateFormat` 线程不安全 | **已修复** |
| C-02 | 高危 | `trimCache()` NoSuchElementException | **已修复** |
| C-03 | 高危 | `parseResponseEntry` 自身引用过滤失效 | **已修复** |
| M-01 | 中危 | `launchPrefetch` cancel flag 竞态 | **已修复** |
| M-02 | 中危 | `WebDavHttpException` 继承设计提醒 | 已确认，无需修改 |
| M-03 | 中危 | `openInputStream` offset 边界 | **已修复** |
| L-01 | 低危 | `saveFile` 重试日志 | 已确认，影响低 |
| L-02 | 低危 | `createDirectory` DRY 违规 | **已修复** |
| L-03 | 低危 | `propfind` response 管理模式 | **已修复** |
| L-04 | 低危 | ResponseInputStream 泄漏风险 | 已确认，当前调用方正确 |
| L-05 | 低危 | thumbnailGeneration 世代检查 | 无需修复 |

**涉及修改的文件：**
- [WebDavMediaDataSource.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavMediaDataSource.kt) — C-02, M-01
- [WebDavStorage.kt](file:///Users/nichx/Documents/GitHub/NIplayer/v2/core/storage/src/main/java/com/nichx/niplayer/storage/impl/WebDavStorage.kt) — C-01, C-03, M-03, L-02, L-03

**编译验证**: `./gradlew :core:storage:compileDebugKotlin` ✅ 通过
