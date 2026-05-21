package com.xyoye.player_component.audio.model

data class AudioSong(
    val uri: String,
    val title: String,
    val artist: String = "",
    val coverPath: String? = null,
    val coverBytes: ByteArray? = null,
    val duration: Long = 0L,
    val uniqueKey: String,
    val fileName: String = "",
    val lrcFilePath: String? = null,
    val lrcContent: String? = null,
    val lrcUrl: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioSong
        if (uri != other.uri) return false
        if (title != other.title) return false
        if (artist != other.artist) return false
        if (coverPath != other.coverPath) return false
        if (coverBytes != null) {
            if (other.coverBytes == null) return false
            if (!coverBytes.contentEquals(other.coverBytes)) return false
        } else if (other.coverBytes != null) return false
        if (duration != other.duration) return false
        if (uniqueKey != other.uniqueKey) return false
        if (fileName != other.fileName) return false
        if (lrcFilePath != other.lrcFilePath) return false
        if (lrcContent != other.lrcContent) return false
        if (lrcUrl != other.lrcUrl) return false
        return true
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + (coverPath?.hashCode() ?: 0)
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        result = 31 * result + duration.hashCode()
        result = 31 * result + uniqueKey.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + (lrcFilePath?.hashCode() ?: 0)
        result = 31 * result + (lrcContent?.hashCode() ?: 0)
        result = 31 * result + (lrcUrl?.hashCode() ?: 0)
        return result
    }

    fun withMetadata(
        title: String? = null,
        artist: String? = null,
        coverPath: String? = null,
        coverBytes: ByteArray? = null,
        duration: Long? = null,
        lrcFilePath: String? = null,
        lrcContent: String? = null,
        lrcUrl: String? = null
    ): AudioSong {
        return AudioSong(
            uri = this.uri,
            title = title ?: this.title,
            artist = artist ?: this.artist,
            coverPath = coverPath ?: this.coverPath,
            coverBytes = coverBytes ?: this.coverBytes,
            duration = duration ?: this.duration,
            uniqueKey = this.uniqueKey,
            fileName = this.fileName,
            lrcFilePath = lrcFilePath ?: this.lrcFilePath,
            lrcContent = lrcContent ?: this.lrcContent,
            lrcUrl = lrcUrl ?: this.lrcUrl
        )
    }
}
