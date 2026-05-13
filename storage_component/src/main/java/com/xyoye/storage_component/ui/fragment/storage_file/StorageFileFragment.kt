package com.xyoye.storage_component.ui.fragment.storage_file

import android.content.res.Configuration
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xyoye.common_component.base.BaseFragment
import com.xyoye.common_component.extension.grid
import com.xyoye.common_component.extension.gridEmpty
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.extension.vertical
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.utils.ThumbnailGeneratorManager
import com.xyoye.storage_component.BR
import com.xyoye.storage_component.R
import com.xyoye.storage_component.databinding.FragmentStorageFileBinding
import com.xyoye.storage_component.ui.activities.storage_file.StorageFileActivity

class StorageFileFragment :
    BaseFragment<StorageFileFragmentViewModel, FragmentStorageFileBinding>(),
    ThumbnailGeneratorManager.ThumbnailCallback {

    private val directory: StorageFile? by lazy { ownerActivity.directory }

    companion object {

        fun newInstance() = StorageFileFragment()
    }

    private val ownerActivity by lazy {
        requireActivity() as StorageFileActivity
    }

    private val isGridView: Boolean
        get() = ownerActivity.isGridView

    private val isTablet: Boolean
        get() = resources.configuration.smallestScreenWidthDp >= 600

    private val gridSpanCount: Int
        get() {
            return if (isTablet) {
                // 平板：竖屏4列，横屏6列
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 4
            } else {
                // 手机：竖屏3列，横屏4列
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 4 else 3
            }
        }
    
    // 保存当前文件列表引用
    private var currentFileList: List<StorageFile> = emptyList()
    
    // 文件唯一键到列表位置的映射，用于O(1)查找
    private val fileIndexMap = HashMap<String, Int>()
    
    // 跟踪滚动状态
    private var isScrolling = false
    
    // 缓存已生成但尚未显示的缩略图文件
    private val pendingThumbnailFiles = mutableSetOf<String>()

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            StorageFileFragmentViewModel::class.java
        )

    override fun getLayoutId() = R.layout.fragment_storage_file

    override fun initView() {
        initRecyclerView()

        viewModel.storage = ownerActivity.storage

        dataBinding.viewToggleBt.setOnClickListener {
            toggleViewMode()
        }

        viewModel.fileLiveData.observe(this) {
            currentFileList = it
            fileIndexMap.clear()
            it.forEachIndexed { index, file ->
                fileIndexMap[file.uniqueKey()] = index
            }
            dataBinding.loading.isVisible = false
            dataBinding.refreshLayout.isVisible = true
            dataBinding.refreshLayout.isRefreshing = false
            ownerActivity.onDirectoryOpened(it)
            dataBinding.storageFileRv.setData(it)
            // 布局完成后，将可见项移到队列前面优先处理
            dataBinding.storageFileRv.post {
                val visibleKeys = getVisibleFileKeys()
                ThumbnailGeneratorManager.reprioritize(visibleKeys)
            }
            //延迟500毫秒，等待列表加载完成后，再请求焦点
            dataBinding.storageFileRv.postDelayed({ requestFocus() }, 500)
        }

        dataBinding.refreshLayout.setColorSchemeResources(R.color.theme)
        dataBinding.refreshLayout.setOnRefreshListener {
            viewModel.listFile(directory, refresh = true)
        }

        // 设置缩略图生成回调
        ThumbnailGeneratorManager.setThumbnailCallback(this)
        
        viewModel.listFile(directory)
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateHistory()
        setRecyclerViewItemFocusAble(true)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isGridView) {
            dataBinding.storageFileRv.apply {
                layoutManager = gridEmpty(gridSpanCount)
                adapter = StorageFileAdapter(ownerActivity, viewModel, true).create()
                viewModel.fileLiveData.value?.let { setData(it) }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        setRecyclerViewItemFocusAble(false)
    }

    private fun setRecyclerViewItemFocusAble(focusAble: Boolean) {
        dataBinding.storageFileRv.children.forEach {
            it.isFocusable = focusAble
        }
    }

    private fun initRecyclerView() {
        dataBinding.storageFileRv.apply {
            layoutManager = if (isGridView) gridEmpty(gridSpanCount) else vertical()
            adapter = StorageFileAdapter(ownerActivity, viewModel, isGridView).create()

            // 添加滚动监听，用于继续生成缩略图
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            isScrolling = false
                            // 当滚动停止时，优先生成当前可见项的缩略图
                            val visibleKeys = getVisibleFileKeys()
                            ThumbnailGeneratorManager.reprioritize(visibleKeys)
                            ThumbnailGeneratorManager.continueGenerateThumbnails(0)
                            // 显示所有待处理的缩略图
                            displayPendingThumbnails()
                        }
                        RecyclerView.SCROLL_STATE_DRAGGING,
                        RecyclerView.SCROLL_STATE_SETTLING -> {
                            isScrolling = true
                        }
                    }
                }
            })
        }
        updateToggleButtonIcon()
    }

    private fun updateToggleButtonIcon() {
        dataBinding.viewToggleBt.setImageResource(
            if (isGridView) R.drawable.ic_view_list else R.drawable.ic_view_grid
        )
    }

    private fun toggleViewMode() {
        ownerActivity.isGridView = !ownerActivity.isGridView
        dataBinding.storageFileRv.apply {
            if (ownerActivity.isGridView) {
                layoutManager = gridEmpty(gridSpanCount)
                adapter = StorageFileAdapter(ownerActivity, viewModel, true).create()
                dataBinding.viewToggleBt.setImageResource(R.drawable.ic_view_list)
            } else {
                layoutManager = vertical()
                adapter = StorageFileAdapter(ownerActivity, viewModel, false).create()
                dataBinding.viewToggleBt.setImageResource(R.drawable.ic_view_grid)
            }
        }
        viewModel.fileLiveData.value?.let {
            dataBinding.storageFileRv.setData(it)
        }
    }

    fun requestFocus(reversed: Boolean = false) {
        if (isDestroyed()) {
            return
        }
        val targetIndex = if (reversed) dataBinding.storageFileRv.childCount - 1 else 0
        dataBinding.storageFileRv.getChildAt(targetIndex)?.requestFocus()
    }

    /**
     * 获取当前屏幕可见文件的唯一键集合，用于优先生成缩略图
     */
    private fun getVisibleFileKeys(): Set<String> {
        val layoutManager = dataBinding.storageFileRv.layoutManager ?: return emptySet()
        if (layoutManager !is androidx.recyclerview.widget.LinearLayoutManager) {
            return emptySet()
        }
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (firstVisible < 0 || lastVisible < 0) {
            return emptySet()
        }
        return (firstVisible..lastVisible)
            .mapNotNull { index -> currentFileList.getOrNull(index)?.uniqueKey() }
            .toSet()
    }

    /**
     * 搜索
     */
    fun search(text: String) {
        //存在搜索条件时，不允许下拉刷新
        dataBinding.refreshLayout.isEnabled = text.isEmpty()
        viewModel.searchByText(text)
    }

    /**
     * 修改文件排序
     */
    fun sort() {
        viewModel.changeSortOption()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 清理待处理的缩略图任务和回调
        ThumbnailGeneratorManager.setThumbnailCallback(null)
        ThumbnailGeneratorManager.clearPendingTasks()
    }
    
    /**
     * 缩略图生成完成回调，直接更新可见的ViewHolder
     */
    /**
     * 缩略图生成完成回调
     * 滚动时只缓存，停止时才显示
     */
    override fun onThumbnailGenerated(file: StorageFile) {
        if (isDestroyed()) {
            return
        }
        
        if (isScrolling) {
            // 正在滚动，只缓存缩略图文件的唯一键
            pendingThumbnailFiles.add(file.uniqueKey())
        } else {
            // 没有滚动，直接显示
            displayThumbnailForFile(file)
        }
    }
    
    /**
     * 显示单个文件的缩略图
     */
    private fun displayThumbnailForFile(file: StorageFile) {
        val position = fileIndexMap[file.uniqueKey()] ?: return
        val layoutManager = dataBinding.storageFileRv.layoutManager
        if (layoutManager is androidx.recyclerview.widget.LinearLayoutManager) {
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            if (position in firstVisible..lastVisible) {
                dataBinding.storageFileRv.adapter?.notifyItemChanged(position, "thumbnail_updated")
            }
        }
    }
    
    /**
     * 显示所有待处理的缩略图
     */
    private fun displayPendingThumbnails() {
        if (pendingThumbnailFiles.isEmpty()) {
            return
        }
        
        val filesToDisplay = pendingThumbnailFiles.toList()
        pendingThumbnailFiles.clear()
        
        val layoutManager = dataBinding.storageFileRv.layoutManager
        if (layoutManager !is androidx.recyclerview.widget.LinearLayoutManager) {
            return
        }
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (firstVisible < 0 || lastVisible < 0) return
        
        filesToDisplay.forEach { uniqueKey ->
            val position = fileIndexMap[uniqueKey] ?: return@forEach
            if (position in firstVisible..lastVisible) {
                dataBinding.storageFileRv.adapter?.notifyItemChanged(position, "thumbnail_updated")
            }
        }
    }
}