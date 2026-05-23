package com.xyoye.common_component.network.repository

import com.xyoye.common_component.network.Retrofit

/**
 * Created by xyoye on 2024/1/6.
 */

object OtherRepository : BaseRepository() {

    /**
     * 获取分词结果
     */
    suspend fun getSegmentWords(text: String) = request()
        .param("text", text)
        .param("tasks", listOf("tok"))
        .doPost {
            Retrofit.extendedService.segmentWords(it)
        }


}