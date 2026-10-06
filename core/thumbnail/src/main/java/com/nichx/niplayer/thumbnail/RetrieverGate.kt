package com.nichx.niplayer.thumbnail

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 进程级 [android.media.MediaMetadataRetriever] 并发闸门。
 *
 * 系统对「同时活着的 retriever 实例数」有硬上限（实测 `MAX_METADATA_RESOURCE=4`），且是**全局**
 * 资源：一旦各方加起来超过上限，后来的实例会等 4s 超时并返回 null
 * （logcat: `Acquire metadata retriever resource timeout`）。
 *
 * 应用内多个互不相关的调用方都会用 retriever：缩略图取帧兜底、进入播放前的宽高比预读、
 * 退出播放更新缩略图、音频内嵌封面。它们此前各自限流、互不知情，叠加起来会占满全局名额。
 * 这里统一收口，保证本应用同时持有的 retriever 不超过 [PERMITS]，为系统/其它应用留出余量。
 *
 * 注意：`setDataSource` / `getFrameAtTime` 是阻塞 JNI，协程取消无法中断它；闸门只是限制
 * **并发占用数**，使等待发生在可控的协程侧，而不是内核侧 4s 超时失败。
 */
object RetrieverGate {

    /** 同时持有的 retriever 实例上限。上限 4，取 2 留一半余量。 */
    private const val PERMITS = 2

    private val gate = Semaphore(PERMITS)

    suspend fun <T> withPermit(block: suspend () -> T): T = gate.withPermit { block() }
}
