package com.xyoye.storage_component.ui.weight

import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.appcompat.widget.SearchView.SearchAutoComplete
import com.xyoye.common_component.config.AppConfig
import com.xyoye.common_component.storage.StorageSortOption
import com.xyoye.data_component.enums.FileFilterType
import com.xyoye.data_component.enums.StorageSort
import com.xyoye.storage_component.R
import com.xyoye.storage_component.ui.activities.storage_file.StorageFileActivity

class StorageFileMenus private constructor(
    private val activity: StorageFileActivity,
    menu: Menu
) {

    companion object {
        fun inflater(activity: StorageFileActivity, menu: Menu): StorageFileMenus {
            activity.menuInflater.inflate(R.menu.menu_storage_file, menu)
            return StorageFileMenus(activity, menu)
        }
    }

    private val searchItem = menu.findItem(R.id.item_search)

    private val filterFolderItem = menu.findItem(R.id.action_filter_folder)
    private val filterVideoItem = menu.findItem(R.id.action_filter_video)
    private val filterImageItem = menu.findItem(R.id.action_filter_image)
    private val filterAudioItem = menu.findItem(R.id.action_filter_audio)

    private val sortNameItem = menu.findItem(R.id.action_sort_by_name)
    private val sortSizeItem = menu.findItem(R.id.action_sort_by_size)
    private val sortOrderAsc = menu.findItem(R.id.action_sort_order_asc)
    private val sortDirectoryFirst = menu.findItem(R.id.action_sort_directory_first)
    private val toggleViewItem = menu.findItem(R.id.action_toggle_view)

    private var mSearchView = searchItem.actionView as SearchView
    private var onTextChanged: ((String) -> Unit)? = null
    private var onSortChanged: (() -> Unit)? = null
    private var onFilterChanged: ((Set<FileFilterType>) -> Unit)? = null
    private var onToggleView: (() -> Unit)? = null

    init {
        initSearchView()
        updateSortItem()
        updateToggleViewItem()
    }

    private fun initSearchView() {
        mSearchView.apply {
            isIconified = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            queryHint = getSearchHintText()
            findViewById<SearchAutoComplete>(R.id.search_src_text)?.textSize = 16f
        }

        mSearchView.setOnQueryTextListener(object : OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                mSearchView.clearFocus()
                return false
            }

            override fun onQueryTextChange(keyword: String): Boolean {
                onTextChanged?.invoke(keyword)
                return false
            }
        })
    }

    private fun updateSortItem() {
        when (StorageSortOption.getSort()) {
            StorageSort.NAME -> sortNameItem
            StorageSort.SIZE -> sortSizeItem
        }.isChecked = true

        sortOrderAsc.isChecked = StorageSortOption.isAsc()
        sortDirectoryFirst.isChecked = StorageSortOption.isDirectoryFirst()
    }

    private fun getSearchHintText(): String {
        if (activity.storage.supportSearch()) {
            return "搜索当前媒体库"
        }
        return "搜索当前目录"
    }

    fun onOptionsItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.action_filter_folder,
            R.id.action_filter_video,
            R.id.action_filter_image,
            R.id.action_filter_audio -> {
                item.isChecked = !item.isChecked
                notifyFilterChanged()
            }

            R.id.action_toggle_view -> {
                onToggleView?.invoke()
            }

            R.id.action_sort_by_name -> {
                StorageSortOption.setSort(StorageSort.NAME)
                updateSortItem()
                onSortChanged?.invoke()
            }

            R.id.action_sort_by_size -> {
                StorageSortOption.setSort(StorageSort.SIZE)
                updateSortItem()
                onSortChanged?.invoke()
            }

            R.id.action_sort_order_asc -> {
                StorageSortOption.changeAsc()
                updateSortItem()
                onSortChanged?.invoke()
            }

            R.id.action_sort_directory_first -> {
                StorageSortOption.changeDirectoryFirst()
                updateSortItem()
                onSortChanged?.invoke()
            }
        }
    }

    private fun notifyFilterChanged() {
        val selectedTypes = mutableSetOf<FileFilterType>()
        if (filterFolderItem.isChecked) selectedTypes.add(FileFilterType.FOLDER)
        if (filterVideoItem.isChecked) selectedTypes.add(FileFilterType.VIDEO)
        if (filterImageItem.isChecked) selectedTypes.add(FileFilterType.IMAGE)
        if (filterAudioItem.isChecked) selectedTypes.add(FileFilterType.AUDIO)
        onFilterChanged?.invoke(selectedTypes)
    }

    fun handleBackPressed(): Boolean {
        if (mSearchView.isIconified) {
            return false
        }
        mSearchView.onActionViewCollapsed()
        return true
    }

    fun onSearchTextChanged(block: (String) -> Unit) {
        onTextChanged = block
    }

    fun onSortTypeChanged(block: () -> Unit) {
        onSortChanged = block
    }

    fun onFilterChanged(block: (Set<FileFilterType>) -> Unit) {
        onFilterChanged = block
    }

    fun onToggleView(block: () -> Unit) {
        onToggleView = block
    }

    fun updateToggleViewItem() {
        toggleViewItem.title = if (AppConfig.isGridView()) "列表视图" else "网格视图"
    }

    fun updateFilterItems(types: Set<FileFilterType>) {
        filterFolderItem.isChecked = types.contains(FileFilterType.FOLDER)
        filterVideoItem.isChecked = types.contains(FileFilterType.VIDEO)
        filterImageItem.isChecked = types.contains(FileFilterType.IMAGE)
        filterAudioItem.isChecked = types.contains(FileFilterType.AUDIO)
    }
}
