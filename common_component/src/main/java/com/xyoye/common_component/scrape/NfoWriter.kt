package com.xyoye.common_component.scrape

import com.xyoye.data_component.entity.ScrapeMediaEntity
import com.xyoye.data_component.entity.TmdbMediaDetail

object NfoWriter {

    fun generateMovieNfo(entity: ScrapeMediaEntity, detail: TmdbMediaDetail? = null): String {
        val title = detail?.title ?: entity.name
        val year = entity.releaseTime?.substring(0, 4)?.takeIf { it.length == 4 } ?: ""
        val plot = detail?.overview ?: entity.overview ?: ""
        val rating = if (entity.voteAverage > 0) entity.voteAverage else detail?.vote_average ?: 0.0
        val tmdbId = entity.tmdbId ?: detail?.id
        val genres = detail?.genres?.joinToString(" / ") { it.name } ?: ""
        val premiered = entity.releaseTime ?: detail?.release_date ?: ""
        val poster = entity.poster ?: detail?.poster_path
        val backdrop = entity.backdrop ?: detail?.backdrop_path

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>")
            appendLine("<movie>")
            appendLine("  <title>${escapeXml(title)}</title>")
            if (year.isNotEmpty()) appendLine("  <year>${escapeXml(year)}</year>")
            if (plot.isNotEmpty()) appendLine("  <plot>${escapeXml(plot)}</plot>")
            if (rating > 0) appendLine("  <rating>${String.format("%.1f", rating)}</rating>")
            if (genres.isNotEmpty()) appendLine("  <genre>${escapeXml(genres)}</genre>")
            if (premiered.isNotEmpty()) appendLine("  <premiered>${escapeXml(premiered)}</premiered>")
            if (poster != null) appendLine("  <thumb>${escapeXml(poster)}</thumb>")
            if (backdrop != null) {
                appendLine("  <fanart>")
                appendLine("    <thumb>${escapeXml(backdrop)}</thumb>")
                appendLine("  </fanart>")
            }
            if (tmdbId != null) {
                appendLine("  <tmdbid>${tmdbId}</tmdbid>")
                appendLine("  <uniqueid type=\"tmdb\">${tmdbId}</uniqueid>")
            }
            appendLine("</movie>")
        }
    }

    fun generateTvShowNfo(entity: ScrapeMediaEntity, detail: TmdbMediaDetail? = null): String {
        val title = detail?.name ?: entity.name
        val plot = detail?.overview ?: entity.overview ?: ""
        val rating = if (entity.voteAverage > 0) entity.voteAverage else detail?.vote_average ?: 0.0
        val tmdbId = entity.tmdbId ?: detail?.id
        val genres = detail?.genres?.joinToString(" / ") { it.name } ?: ""
        val premiered = entity.releaseTime ?: detail?.first_air_date ?: ""
        val seasons = detail?.number_of_seasons ?: 1
        val poster = entity.poster ?: detail?.poster_path
        val backdrop = entity.backdrop ?: detail?.backdrop_path

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>")
            appendLine("<tvshow>")
            appendLine("  <title>${escapeXml(title)}</title>")
            if (plot.isNotEmpty()) appendLine("  <plot>${escapeXml(plot)}</plot>")
            if (rating > 0) appendLine("  <rating>${String.format("%.1f", rating)}</rating>")
            if (genres.isNotEmpty()) appendLine("  <genre>${escapeXml(genres)}</genre>")
            if (premiered.isNotEmpty()) appendLine("  <premiered>${escapeXml(premiered)}</premiered>")
            appendLine("  <season>$seasons</season>")
            if (poster != null) appendLine("  <thumb>${escapeXml(poster)}</thumb>")
            if (backdrop != null) {
                appendLine("  <fanart>")
                appendLine("    <thumb>${escapeXml(backdrop)}</thumb>")
                appendLine("  </fanart>")
            }
            if (tmdbId != null) {
                appendLine("  <tmdbid>${tmdbId}</tmdbid>")
                appendLine("  <uniqueid type=\"tmdb\">${tmdbId}</uniqueid>")
            }
            appendLine("</tvshow>")
        }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
