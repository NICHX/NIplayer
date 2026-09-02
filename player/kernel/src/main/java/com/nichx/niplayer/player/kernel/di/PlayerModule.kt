package com.nichx.niplayer.player.kernel.di

import android.annotation.SuppressLint
import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.nichx.niplayer.player.kernel.NxPlayer
import com.nichx.niplayer.player.kernel.NxPlayerBackend
import com.nichx.niplayer.player.kernel.media3.NxMedia3Player
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import dagger.multibindings.IntoSet

/**
 * :player:kernel Hilt 绑定模块。
 *
 * [bindNxPlayer] 不加 @Singleton：调用方（PlayerViewModel / PlayerService）按需 @Inject 创建实例，
 * 持有引用并在销毁时调用 [NxPlayer.release]。
 * 一个屏幕一个 Player 实例，避免跨页面共享已释放的 Player。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    abstract fun bindNxPlayer(impl: NxMedia3Player): NxPlayer

    /**
     * 注册默认 media3 内核为多内核集合成员。
     *
     * 当前该集合暂无消费方（能力解析器在第二内核落地时引入）；`@IntoSet` 多绑定在
     * `Set<NxPlayerBackend>` 被请求前不会被实例化，此处仅作结构预留，不产生运行时开销。
     */
    @Binds
    @IntoSet
    abstract fun bindMedia3Backend(impl: NxMedia3Player): NxPlayerBackend

    companion object {

        /** 播放器 HTTP 缓存目录名（与之前的实例私有 lazy 一致，复用既有缓存数据）。 */
        private const val EXO_MEDIA_CACHE_DIR = "exo_media_cache"

        /**
         * 进程级 [SimpleCache] 单例。
         *
         * media3 的 [SimpleCache] 对同一缓存目录是进程独占的（内部以静态注册表 + 文件锁保证），
         * 同一进程内同时创建两个指向 `exo_media_cache` 的实例会抛
         * `IllegalStateException: Another SimpleCache instance uses the folder`。
         *
         * 此前 [NxMedia3Player] 非单例、每个实例各自持有私有 lazy `mediaCache`，当视频/音频
         * 播放器实例生命周期重叠（例如全屏播放器进入均衡器再返回）时，第二个实例初始化缓存即崩。
         * 此处提升为全局单例，使所有 [NxMedia3Player] 实例共享同一个缓存实例与目录锁。
         */
        @Provides
        @Singleton
        @SuppressLint("UnsafeOptInUsageError")
        fun provideMediaCache(@ApplicationContext context: Context): SimpleCache {
            val cacheDir = File(context.cacheDir, EXO_MEDIA_CACHE_DIR)
            // 与 NxMedia3Player 旧的 CACHE_MAX_BYTES 保持一致（500MB LRU），复用既有缓存数据。
            return SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024),
                StandaloneDatabaseProvider(context),
            )
        }
    }
}
