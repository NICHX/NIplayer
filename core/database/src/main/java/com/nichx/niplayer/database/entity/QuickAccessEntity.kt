package com.nichx.niplayer.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * 快速访问书签表。
 *
 * 替代旧仓库基于 MMKV 的 [QuickAccessItem] JSON 列表。改用 Room 结构化存储，
 * 享受 Flow 响应式刷新与唯一约束去重（`library_id + storage_path`）。
 *
 * 存储的是「媒体库引用 + 库内相对路径」组合，真正播放 URL 在打开时由
 * [com.nichx.niplayer.storage.Storage.createPlayUrl] 按需生成（同旧仓库）。
 *
 * 不冗余存储 libraryUrl / libraryDisplayName：列表页 ViewModel 通过 libraryId
 * 批量关联 [MediaLibraryEntity] 获取展示信息；若关联存储源已删除，UI 标记无效。
 *
 * @param libraryId 关联 [MediaLibraryEntity.id]
 * @param storagePath 库内相对路径（非绝对路径、非 URL）
 */
@Entity(
    tableName = "quick_access",
    indices = [Index(value = ["library_id", "storage_path"], unique = true)]
)
@JsonClass(generateAdapter = true)
data class QuickAccessEntity(
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "storage_path")
    val storagePath: String,

    @ColumnInfo(name = "is_directory")
    val isDirectory: Boolean,

    @ColumnInfo(name = "library_id")
    val libraryId: Int,

    @ColumnInfo(name = "add_time")
    val addTime: Date = Date(),

    /**
     * 用户自定义排序序号，越小越靠前。新增项默认取当前最大值 +1，
     * 拖拽排序后由 [com.nichx.niplayer.database.dao.QuickAccessDao.updateOrder] 批量刷新。
     */
    @ColumnInfo(name = "sort_index")
    val sortIndex: Int = 0,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    var updatedAt: Long = System.currentTimeMillis(),
)
