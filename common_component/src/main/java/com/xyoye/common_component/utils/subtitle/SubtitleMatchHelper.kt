package com.xyoye.common_component.utils.subtitle

import com.xyoye.common_component.network.repository.ResourceRepository
import com.xyoye.common_component.utils.getFileName
import com.xyoye.common_component.utils.getFileNameNoExtension
import com.xyoye.data_component.data.SubtitleSourceBean

object SubtitleMatchHelper {

    suspend fun matchSubtitle(videoPath: String): MutableList<SubtitleSourceBean> {
        val sourceList = mutableListOf<SubtitleSourceBean>()
        sourceList.addAll(matchShooterSubtitle(videoPath))
        return sourceList
    }

    private suspend fun matchShooterSubtitle(videoPath: String): List<SubtitleSourceBean> {
        val videoHash = SubtitleHashUtils.getShooterHash(videoPath)
            ?: return emptyList()

        return ResourceRepository
            .matchSubtitleFormShooter(videoHash, getFileName(videoPath))
            .getOrNull()
            ?.filter { it.Files != null }
            ?.flatMap { files ->
                files.Files!!
                    .filter { it.Link != null }
                    .map {
                        val extension = it.Ext ?: ".ass"
                        val shooterName = getFileNameNoExtension(videoPath) + "." + extension
                        SubtitleSourceBean(
                            isMatch = true,
                            name = shooterName,
                            matchUrl = it.Link!!,
                            source = "射手网"
                        )
                    }
            } ?: emptyList()
    }
}