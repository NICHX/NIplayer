package com.xyoye.common_component.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.xyoye.common_component.config.AppConfig
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.data_component.bean.QuickAccessItem
import com.xyoye.data_component.bean.QuickAccessItemJsonAdapter
import java.lang.reflect.Type

object QuickAccessHelper {

    private val QUICK_ACCESS_MOSHI = Moshi.Builder()
        .add(object : JsonAdapter.Factory {
            override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
                if (Types.getRawType(type) == QuickAccessItem::class.java) {
                    return QuickAccessItemJsonAdapter(moshi)
                }
                return null
            }
        })
        .build()

    private const val KEY_SEPARATOR = "::"

    fun getQuickAccessList(): List<QuickAccessItem> {
        val json = AppConfig.getQuickAccessItems() ?: return emptyList()
        return parseJsonList(json)
    }

    private fun saveQuickAccessList(items: List<QuickAccessItem>) {
        val json = toJson(items) ?: "[]"
        AppConfig.putQuickAccessItems(json)
    }

    private inline fun <reified T> toJson(t: T?): String? {
        t ?: return null
        return try {
            QUICK_ACCESS_MOSHI.adapter(T::class.java).toJson(t)
        } catch (e: Exception) {
            null
        }
    }

    private inline fun <reified T> parseJsonList(jsonStr: String): List<T> {
        if (jsonStr.isEmpty()) return emptyList()
        return try {
            val type = Types.newParameterizedType(List::class.java, T::class.java)
            QUICK_ACCESS_MOSHI.adapter<List<T>>(type).fromJson(jsonStr) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun buildItemKey(item: QuickAccessItem): String {
        return "${item.libraryId}$KEY_SEPARATOR${item.storagePath}"
    }

    private fun buildItemKey(libraryId: Long, storagePath: String): String {
        return "$libraryId$KEY_SEPARATOR$storagePath"
    }

    private fun itemKey(file: StorageFile): String {
        return "${file.storage.library.id}$KEY_SEPARATOR${file.storagePath()}"
    }

    fun addQuickAccess(file: StorageFile) {
        val items = getQuickAccessList().toMutableList()
        val key = itemKey(file)
        if (items.none { buildItemKey(it) == key }) {
            items.add(
                QuickAccessItem(
                    name = file.fileName(),
                    storagePath = file.storagePath(),
                    isDirectory = file.isDirectory(),
                    libraryId = file.storage.library.id.toLong(),
                    libraryUrl = file.storage.library.url,
                    libraryDisplayName = file.storage.library.displayName,
                    uniqueKey = file.uniqueKey()
                )
            )
            saveQuickAccessList(items)
        }
    }

    fun removeQuickAccess(file: StorageFile) {
        val items = getQuickAccessList().toMutableList()
        val key = itemKey(file)
        items.removeAll { buildItemKey(it) == key }
        saveQuickAccessList(items)
    }

    fun removeQuickAccess(item: QuickAccessItem) {
        val items = getQuickAccessList().toMutableList()
        val key = buildItemKey(item)
        items.removeAll { buildItemKey(it) == key }
        saveQuickAccessList(items)
    }

    fun saveQuickAccessItems(items: List<QuickAccessItem>) {
        saveQuickAccessList(items)
    }

    fun isQuickAccess(file: StorageFile): Boolean {
        val key = itemKey(file)
        return getQuickAccessList().any { buildItemKey(it) == key }
    }

    fun getQuickAccessForLibrary(libraryId: Long): List<QuickAccessItem> {
        return getQuickAccessList().filter { it.libraryId == libraryId }
    }
}