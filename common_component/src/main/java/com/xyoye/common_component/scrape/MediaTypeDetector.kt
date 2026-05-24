package com.xyoye.common_component.scrape

object MediaTypeDetector {

    private val TYPE_BY_FOLDER_NAME: Map<String, String> = buildMap {
        fun putAll(type: String, names: List<String>) {
            names.forEach { name -> put(name.trim().lowercase(), type) }
        }
        putAll("movie", listOf("电影", "movie", "movies", "影片", "films", "film"))
        putAll("tv", listOf("电视剧", "剧集", "tv", "tv shows", "tv series", "series", "剧集库"))
        putAll("variety", listOf("综艺", "variety", "variety show", "真人秀"))
        putAll("documentary", listOf("纪录片", "纪实", "documentary", "documentaries"))
        putAll("anime", listOf("动漫", "动画", "番剧", "anime", "animation"))
        putAll("concert", listOf("演唱会", "音乐会", "concert", "concerts", "live show", "live"))
    }

    fun detectFromPath(filePath: String): String? {
        val normalized = filePath.replace('\\', '/').trim().trim('/')
        if (normalized.isEmpty()) return null
        val parts = normalized.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        for (i in parts.indices.reversed()) {
            val key = parts[i].lowercase()
            TYPE_BY_FOLDER_NAME[key]?.let { return it }
        }
        return null
    }
}