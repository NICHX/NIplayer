package com.nichx.niplayer.network.subtitle

import com.squareup.moshi.JsonClass

/**
 * ASSRT 字幕搜索响应（`v1/sub/search`）。
 *
 * 响应结构：`{ sub: { subs: [...] } }`
 *
 * @see <a href="https://assrt.net">assrt.net</a>
 */
@JsonClass(generateAdapter = true)
data class AssrtSearchResponse(
    val sub: AssrtSubContainer? = null,
)

@JsonClass(generateAdapter = true)
data class AssrtSubContainer(
    val subs: List<AssrtSubDetail>? = null,
)

/**
 * ASSRT 字幕详情（搜索结果项 + 详情接口共用）。
 *
 * 搜索接口返回列表项含基础字段（id/videoname/native_name/upload_time/subtype/lang），
 * 详情接口额外返回下载链接（url）和压缩包内文件列表（filelist）。
 */
@JsonClass(generateAdapter = true)
data class AssrtSubDetail(
    val id: Int = 0,
    val videoname: String? = null,
    val native_name: String? = null,
    val upload_time: String? = null,
    val subtype: String? = null,
    val lang: AssrtLang? = null,
    /** 详情接口返回的压缩包下载地址。 */
    val url: String? = null,
    /** 详情接口返回的压缩包内文件列表。 */
    val filelist: List<AssrtSubFile>? = null,
)

@JsonClass(generateAdapter = true)
data class AssrtLang(
    val desc: String? = null,
)

/** 压缩包内单个字幕文件。 */
@JsonClass(generateAdapter = true)
data class AssrtSubFile(
    val url: String? = null,
    val f: String? = null,
    val s: String? = null,
)
