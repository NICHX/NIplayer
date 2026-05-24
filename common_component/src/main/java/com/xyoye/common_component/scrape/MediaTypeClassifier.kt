package com.xyoye.common_component.scrape

object MediaTypeClassifier {

    fun resolve(
        filePath: String,
        fileMediaType: MediaType,
        tmdbInferredType: String?
    ): String {
        val pathHint = MediaTypeDetector.detectFromPath(filePath)
        if (pathHint != null) {
            return pathHint
        }

        when (fileMediaType) {
            MediaType.DOCUMENTARY,
            MediaType.VARIETY,
            MediaType.CONCERT,
            MediaType.ANIME -> return fileMediaType.dbValue
            else -> {}
        }

        if (tmdbInferredType != null) return tmdbInferredType

        return when (fileMediaType) {
            MediaType.TV_SHOW -> "tv"
            else -> "movie"
        }
    }
}