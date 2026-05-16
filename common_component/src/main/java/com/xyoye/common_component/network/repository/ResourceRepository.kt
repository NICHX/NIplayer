package com.xyoye.common_component.network.repository

import com.xyoye.common_component.network.Retrofit

/**
 * Created by xyoye on 2024/1/6.
 */

object ResourceRepository : BaseRepository() {

    /**
     * 匹配字幕，Shooter
     */
    suspend fun matchSubtitleFormShooter(fileHash: String, fileName: String) = request()
        .param("filehash", fileHash)
        .param("pathinfo", fileName)
        .param("format", "json")
        .param("lang", "Chn")
        .doPost {
            Retrofit.extendedService.matchSubtitleFormShooter(it)
        }

    /**
     * 搜索字幕
     */
    suspend fun searchSubtitle(
        token: String,
        keyword: String,
        page: Int
    ) = request()
        .param("token", token)
        .param("q", keyword)
        .param("pos", page)
        .doGet {
            Retrofit.extendedService.searchSubtitle(it)
        }

    /**
     * 字幕详情
     */
    suspend fun getSubtitleDetail(token: String, id: String) = request()
        .param("token", token)
        .param("id", id)
        .doGet {
            Retrofit.extendedService.searchSubtitleDetail(it)
        }

    /**
     * 获取资源响应
     */
    suspend fun getResourceResponse(url: String, headers: Map<String, String> = emptyMap()) = request()
        .doGet {
            Retrofit.extendedService.getResourceResponse(url, headers)
        }

    /**
     * 获取资源响应正文
     */
    suspend fun getResourceResponseBody(url: String, headers: Map<String, String> = emptyMap()) = request()
        .doGet {
            Retrofit.extendedService.getResourceResponseBody(url, headers)
        }
}