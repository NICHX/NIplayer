package com.nichx.niplayer.network.subtitle

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * ASSRT 字幕 API（https://api.assrt.net）。
 *
 * 替代旧仓库 `ExtendedService` 中字幕相关接口。shooter.cn hash 匹配已失效，
 * v2 仅保留 ASSRT 关键词搜索 + 详情。
 *
 * 需要用户在 assrt.net 注册获取 token，通过 [SubtitleSettings.assrtToken] 持久化。
 * 旧仓库误命名为 `shooterSecret`，v2 纠正为 `assrtToken`。
 *
 * 注意：assrt.net 使用 HTTP（非 HTTPS），需在 network_security_config.xml 中配置明文流量。
 */
interface AssrtApi {

    /**
     * 关键词搜索字幕。
     *
     * @param token ASSRT API token
     * @param q 搜索关键词（通常是视频文件名）
     * @param pos 页码（从 1 开始）
     */
    @GET("v1/sub/search")
    suspend fun search(
        @Query("token") token: String,
        @Query("q") query: String,
        @Query("pos") pos: Int = 1,
    ): AssrtSearchResponse

    /**
     * 获取字幕详情（含下载链接和压缩包内文件列表）。
     *
     * @param token ASSRT API token
     * @param id 字幕 ID（来自 [AssrtSubDetail.id]）
     */
    @GET("v1/sub/detail")
    suspend fun detail(
        @Query("token") token: String,
        @Query("id") id: Int,
    ): AssrtSearchResponse
}
