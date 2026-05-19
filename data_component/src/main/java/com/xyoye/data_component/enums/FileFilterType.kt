package com.xyoye.data_component.enums

enum class FileFilterType(val displayName: String) {
    ALL("全部"),
    FOLDER("文件夹"),
    VIDEO("视频"),
    IMAGE("图片"),
    AUDIO("音频");

    companion object {
        fun defaultSet(): Set<FileFilterType> = setOf(ALL)
    }
}
