package com.nichx.niplayer.network

import com.nichx.niplayer.network.subtitle.AssrtApi
import com.nichx.niplayer.network.update.GitHubApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * :core:network Hilt 模块，提供共享网络客户端。
 *
 * 替代旧仓库分散的 3 个 OkHttpClient 实例：
 * - `common_component/network/helper/UnsafeOkHttpClient`（信任所有证书，WebDAV 用）
 * - `Retrofit.commonClient`（10s/10s/4s，extendedService/alistService 用）
 * - `Retrofit.downloadClient`（15s/120s/30s，下载用）
 *
 * 本仓库统一提供单一 [OkHttpClient] 单例，由 :core:network / :core:storage /
 * :player:kernel 共享。WebDAV 自签证书支持将在 [WebDavStorage] 实现时通过
 * @Qualifier 单独提供（不污染默认客户端）。
 *
 * 超时配置沿用旧仓库 downloadClient（15s connect / 120s read / 30s write），
 * 适配大文件流式播放场景。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        // 连接超时 10s：服务器不可达时快速失败（网络存储连接体验优化）
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // W-N4 修复：OkHttp 默认 Dispatcher.maxRequestsPerHost=5，而项目
        // Storage.thumbnailConcurrency=6，6 路并发缩略图拉取会有 1 个排队。
        // 调整为 10 覆盖 6 缩略图并发 + 浏览/播放的并发请求，避免 host 限流。
        // maxRequests 保持默认 64（足够支撑 6 缩略图 + 其他请求）。
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 10 })
        .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()

    /**
     * ASSRT 字幕 API Retrofit 实例。
     *
     * assrt.net 使用 HTTP（非 HTTPS），baseURL 为 `http://api.assrt.net/`。
     * 共享全局 [OkHttpClient]，超时配置足以应对字幕搜索/下载场景。
     */
    @Provides
    @Singleton
    fun provideAssrtApi(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): AssrtApi = Retrofit.Builder()
        .baseUrl("http://api.assrt.net/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(AssrtApi::class.java)

    /**
     * GitHub Releases API（版本检测 / 在线更新）。
     *
     * baseURL 为 [GitHubApi.BASE_URL]（HTTPS），共享全局 [OkHttpClient]。
     */
    @Provides
    @Singleton
    fun provideGitHubApi(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): GitHubApi = Retrofit.Builder()
        .baseUrl(GitHubApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApi::class.java)
}
