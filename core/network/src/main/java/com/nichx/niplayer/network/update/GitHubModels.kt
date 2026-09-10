package com.nichx.niplayer.network.update

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * GitHub Releases API 返回的 release 对象（[GitHubApi.getLatestRelease]）。
 *
 * 仅声明应用更新检测所需的字段，其余字段由 Moshi 忽略。
 */
@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "html_url") val htmlUrl: String? = null,
    @Json(name = "assets") val assets: List<GitHubAsset> = emptyList(),
)

/** GitHub release 附件（APK/AAB）。 */
@JsonClass(generateAdapter = true)
data class GitHubAsset(
    @Json(name = "name") val name: String? = null,
    @Json(name = "browser_download_url") val browserDownloadUrl: String? = null,
    @Json(name = "size") val size: Long = 0L,
)
