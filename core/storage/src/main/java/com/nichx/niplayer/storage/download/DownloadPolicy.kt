package com.nichx.niplayer.storage.download

/**
 * 下载链路的判定策略（纯函数，无 IO、无 Android 依赖，便于单元测试）。
 *
 * 抽出的动机：这两条判定原本内联在 [DownloadManager] 的协程与 IO 循环里，而该类依赖
 * `Context` + Room DAO + `StorageFactory`，使判定逻辑无法被单元测试覆盖。抽出后可用纯 JVM
 * 测试锁定「绝不产出损坏文件」这一不变式。
 */
internal object DownloadPolicy {

    /**
     * 是否需要放弃续传、把偏移重置为 0。
     *
     * 续传的前提是「本地文件的实际长度恰好等于记录的偏移」。二者不一致时（上次 flush 未落盘、
     * 进程被杀、文件被手动改动或删除、取消竞态遗留）以 append 模式写入会从文件真实末尾继续
     * 追加，导致写入位置与远程 offset 错位 —— 产出的文件从错位点起全是垃圾数据，却仍会被
     * 标记为 COMPLETED。
     *
     * @param recordedOffset DB 中记录的已下载字节数
     * @param localFileLength 本地目标文件的实际长度（文件不存在时为 0）
     * @return true 表示必须丢弃本地残片、从 0 重新下载
     */
    fun shouldResetResumeOffset(recordedOffset: Long, localFileLength: Long): Boolean =
        recordedOffset > 0 && localFileLength != recordedOffset

    /**
     * 是否真的下载完整。
     *
     * 输入流返回 -1（EOF）**不等于**下载完成：网络抖动、服务端提前断连、Range 响应不完整都会
     * 提前 EOF。只有已知总长度且已写字节数达到总长度时才可判定完成。
     *
     * @param totalRead 累计已写字节数（续传场景含起始 offset）
     * @param totalBytes 文件总字节数，<= 0 表示长度未知（此时不做判定，按完整处理）
     * @return true 表示可以置为 COMPLETED
     */
    fun isFullyDownloaded(totalRead: Long, totalBytes: Long): Boolean =
        totalBytes <= 0 || totalRead >= totalBytes
}
