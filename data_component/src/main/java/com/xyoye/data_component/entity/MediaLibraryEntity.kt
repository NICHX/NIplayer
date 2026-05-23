package com.xyoye.data_component.entity

import android.os.Parcelable
import android.provider.MediaStore
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.xyoye.data_component.enums.MediaType
import com.xyoye.data_component.helper.BooleanConverter
import com.xyoye.data_component.helper.MediaTypeConverter
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * Created by xyoye on 2021/1/18.
 */

@Parcelize
@Entity(tableName = "media_library", indices = [Index(value = arrayOf("url", "media_type"), unique = true)])
@TypeConverters(BooleanConverter::class, MediaTypeConverter::class)
data class MediaLibraryEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    @ColumnInfo(name = "display_name")
    var displayName: String,

    @ColumnInfo(name = "url")
    var url: String,

    @ColumnInfo(name = "media_type")
    var mediaType: MediaType,

    @ColumnInfo(name = "account")
    var account: String? = null,

    @ColumnInfo(name = "password")
    var password: String? = null,

    @ColumnInfo(name = "is_anonymous")
    var isAnonymous: Boolean = false,

    @ColumnInfo(name = "port")
    var port: Int = 0,

    @ColumnInfo(name = "describe")
    var describe: String? = null,

    @ColumnInfo(name = "ftp_mode")
    var isActiveFTP: Boolean = false,

    @ColumnInfo(name = "ftp_address")
    var ftpAddress: String = "",

    @ColumnInfo(name = "ftp_encoding")
    var ftpEncoding: String = "UTF-8",

    @ColumnInfo(name = "smb_v2")
    var smbV2: Boolean = true,

    @ColumnInfo(name = "smb_share_path")
    var smbSharePath: String? = null,

    @ColumnInfo(name = "remote_secret")
    var remoteSecret: String? = null,

    @ColumnInfo(name = "web_dav_strict")
    var webDavStrict: Boolean = true,

    @ColumnInfo(name = "screencast_address")
    var screencastAddress: String = "",
) : Parcelable {

    companion object {
        val LOCAL = MediaLibraryEntity(
            id = 1,
            displayName = "本地媒体库",
            url = MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString(),
            mediaType = MediaType.LOCAL_STORAGE
        )
        val HISTORY = MediaLibraryEntity(
            id = 4,
            displayName = "播放历史",
            url = "",
            mediaType = MediaType.OTHER_STORAGE
        )
        val QUICK_ACCESS = MediaLibraryEntity(
            id = 5,
            displayName = "快速访问",
            url = "",
            mediaType = MediaType.QUICK_ACCESS
        )
    }

    @Ignore
    @IgnoredOnParcel
    var running: Boolean = false

    @IgnoredOnParcel
    val disPlayDescribe
        get() = when (mediaType) {
            MediaType.SMB_SERVER,
            MediaType.EXTERNAL_STORAGE -> describe

            else -> url
        }
}