package com.nichx.niplayer.network.update

import retrofit2.http.GET

/**
 * GitHub Releases API。
 *
 * 用于版本检测：查询仓库 [REPO] 的最新正式 release，其 APK 附件即在线更新下载源。
 * 官方接口无鉴权时限制 60 次/小时/IP，配合每日自动检查节流完全够用。
 */
interface GitHubApi {

    @GET("repos/$REPO/releases/latest")
    suspend fun getLatestRelease(): GitHubRelease

    companion object {
        /** 应用更新仓库（与 release.yml 发布的目标一致）。 */
        const val REPO = "NICHX/NIplayer"

        /** GitHub API baseUrl，Retrofit 要求以 / 结尾。 */
        const val BASE_URL = "https://api.github.com/"

        /** 仓库 Releases 页面（下载失败时浏览器兜底）。 */
        const val RELEASES_PAGE_URL = "https://github.com/$REPO/releases"
    }
}
