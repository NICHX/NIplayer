package com.nichx.niplayer

import android.app.Application
import android.provider.MediaStore
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.nichx.niplayer.common.coroutine.AppCoroutineScope
import com.nichx.niplayer.common.crash.CrashHandler
import com.nichx.niplayer.database.dao.MediaLibraryDao
import com.nichx.niplayer.database.entity.MediaLibraryEntity
import com.nichx.niplayer.database.enums.MediaType
import com.nichx.niplayer.datastore.ThemeSettings
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * NIplayer Application 入口。
 *
 * @HiltAndroidApp 触发 Hilt 依赖图生成。启动时：
 * 1. [CrashHandler.install] 注册全局未捕获异常处理器（O-12），早于其他初始化以捕获启动期崩溃
 * 2. 初始化 MMKV（供 [com.nichx.niplayer.datastore.SubtitleSettings] 等持久化配置使用）
 * 3. 通过 [ensureLocalStorageExists] 确保"本地媒体库"系统项存在于 media_library 表
 *    （url 固定为 [MediaStore.Video.Media.EXTERNAL_CONTENT_URI]，不可删除），供用户直接浏览本地视频。
 * 4. 通过 [SingletonImageLoader.Factory] 注册 [VideoFrameDecoder]，使 Coil 可从本地视频
 *    （content:// 或 file://）提取帧作为缩略图，供文件浏览页和播放历史页使用。
 *
 * 后台任务统一使用注入的 [appScope]（[AppCoroutineScope]），替代原 private applicationScope（O-13）。
 */
@HiltAndroidApp
class NiApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var mediaLibraryDao: MediaLibraryDao

    @Inject
    lateinit var crashHandler: CrashHandler

    @Inject
    lateinit var appScope: AppCoroutineScope

    /** 上次崩溃日志，启动时由 [checkPreviousCrash] 填充，供 UI 层提示用户上报。 */
    @Volatile
    var previousCrashLog: String? = null
        private set

    override fun onCreate() {
        super.onCreate()
        // O-12：尽早安装崩溃捕获（Hilt 字段注入在 super.onCreate() 中完成，此后才可使用），
        // 覆盖后续 MMKV / 本地存储初始化阶段
        crashHandler.install()
        MMKV.initialize(this)
        // 主动触发 ThemeSettings 初始化，确保 MMKV 就绪后才加载主题模式配置
        ThemeSettings.themeFlow.value
        checkPreviousCrash()
        appScope.launch { ensureLocalStorageExists() }
    }

    /**
     * 检查上次崩溃日志并消费（O-12）。
     *
     * 若存在未读崩溃，填充 [previousCrashLog]，UI 层（MainActivity）据此弹出提示对话框。
     * 消费即删除，避免下次启动重复提示。
     */
    private fun checkPreviousCrash() {
        previousCrashLog = crashHandler.consumePreviousCrash()
    }

    /**
     * 注册 [VideoFrameDecoder]，使 Coil 的 AsyncImage 可直接加载本地视频帧缩略图。
     *
     * 仅对 content:// 和 file:// URI 生效，远端协议（SMB/WebDAV）不触发视频帧解码。
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
                add(OkHttpNetworkFetcherFactory())
            }
            .crossfade(true)
            .build()
    }

    /**
     * 确保本地媒体库系统项存在。
     *
     * 以 url + mediaType 为唯一键去重：若已存在则跳过，否则插入。
     * 对应旧仓库 `MediaViewModel.initLocalStorage()` 的系统项初始化逻辑。
     */
    private suspend fun ensureLocalStorageExists() {
        val localUrl = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()
        if (mediaLibraryDao.getByUrl(localUrl, MediaType.LOCAL_STORAGE) == null) {
            mediaLibraryDao.insert(
                MediaLibraryEntity(
                    displayName = "本地媒体库",
                    url = localUrl,
                    mediaType = MediaType.LOCAL_STORAGE,
                )
            )
        }
    }
}
