package com.xyoye.storage_component.ui.fragment.storage_file

import android.view.LayoutInflater
import android.view.ViewGroup
import android.content.res.Configuration
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xyoye.common_component.base.BaseFragment
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
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 4
            } else {
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 3 else 2
            }
        }
    
    private var currentFileList: List<StorageFile> = emptyList()

    fun getCurrentFileList(): List<StorageFile> = currentFileList
    
    private val fileIndexMap = HashMap<String, Int>()
    
    private var isScrolling = false
    
    private var isThumbnailPaused = false
    
    private val pendingThumbnailFiles = mutableSetOf<String>()

    private val pendingThumbnailUpdates = mutableSetOf<Int>()
    private val thumbnailUpdateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val thumbnailBatchRunnable = Runnable {
        if (pendingThumbnailUpdates.isEmpty()) return@Runnable
        val adapter = dataBinding.storageFileRv.adapter ?: return@Runnable
        val sorted = pendingThumbnailUpdates.toList().sorted().distinct()
        pendingThumbnailUpdates.clear()

        var rangeStart = sorted[0]
        var rangeEnd = sorted[0]
        for (i in 1 until sorted.size) {
            if (sorted[i] == rangeEnd + 1) {
                rangeEnd = sorted[i]
            } else {
                adapter.notifyItemRangeChanged(rangeStart, rangeEnd - rangeStart + 1, "thumbnail_updated")
                rangeStart = sorted[i]
                rangeEnd = sorted[i]
            }
        }
        adapter.notifyItemRangeChanged(rangeStart, rangeEnd - rangeStart + 1, "thumbnail_updated")
    }

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            StorageFileFragmentViewModel::class.java
        )

    override fun getLayoutId() = R.layout.fragment_storage_file

    override fun initView() {
        initRecyclerView()

        viewModel.storage = ownerActivity.storage

        scrollToTopBt?.setOnClickListener {
            if (!isDestroyed()) {
                dataBinding.storageFileRv.scrollToPosition(0)
            }
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
            dataBinding.storageFileRv.post {
                val visibleKeys = getVisibleFileKeys()
                ThumbnailGeneratorManager.reprioritize(visibleKeys)
            }
            dataBinding.storageFileRv.postDelayed({ requestFocus() }, 500)
        }

        dataBinding.refreshLayout.setColorSchemeResources(R.color.theme)
        dataBinding.refreshLayout.setOnRefreshListener {
            viewModel.listFile(directory, refresh = true)
        }

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
        refreshViewMode()
    }

    fun refreshViewMode() {
        dataBinding.storageFileRv.apply {
            layoutManager = if (isGridView) gridEmpty(gridSpanCount) else vertical()
            adapter = StorageFileAdapter(ownerActivity, viewModel, isGridView).create()
            viewModel.fileLiveData.value?.let { setData(it) }
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
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            
            layoutManager = if (isGridView) gridEmpty(gridSpanCount) else vertical()
            adapter = StorageFileAdapter(ownerActivity, viewModel, isGridView).create()

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var scrollDistanceAccumulator = 0
                private var lastScrollDirection = 0

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    when (newState) {
                        RecyclerView.SCROLL_STATE_IDLE -> {
                            isScrolling = false
                            isThumbnailPaused = false
                            scrollDistanceAccumulator = 0
                            lastScrollDirection = 0
                            val visibleKeys = getVisibleFileKeys()
                            ThumbnailGeneratorManager.reprioritize(visibleKeys)
                            ThumbnailGeneratorManager.resumeGenerateThumbnails()
                            ThumbnailGeneratorManager.continueGenerateThumbnails()
                            displayPendingThumbnails()
                        }
                        RecyclerView.SCROLL_STATE_DRAGGING -> {
                            isScrolling = true
                            if (!isThumbnailPaused) {
                                isThumbnailPaused = true
                                ThumbnailGeneratorManager.pauseGenerateThumbnails()
                            }
                        }
                        RecyclerView.SCROLL_STATE_SETTLING -> {
                            isScrolling = true
                            if (!isThumbnailPaused) {
                                isThumbnailPaused = true
                                ThumbnailGeneratorManager.pauseGenerateThumbnails()
                            }
                        }
                    }
                }

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy != 0) {
                        val currentDirection = if (dy > 0) 1 else -1
                        if (currentDirection != lastScrollDirection) {
                            lastScrollDirection = currentDirection
                            scrollDistanceAccumulator = 0
                            val visibleKeys = getVisibleFileKeys()
                            ThumbnailGeneratorManager.reprioritize(visibleKeys)
                        }
                    }
                    scrollDistanceAccumulator += kotlin.math.abs(dy)
                    if (scrollDistanceAccumulator > recyclerView.height) {
                        scrollDistanceAccumulator = 0
                        val visibleKeys = getVisibleFileKeys()
                        ThumbnailGeneratorManager.reprioritize(visibleKeys)
                    }
                    updateScrollToTopVisibility(recyclerView)
                }
            })
        }
    }

    private val scrollToTopBt: View?
        get() = requireActivity().findViewById(R.id.scroll_to_top_bt)

    private fun updateScrollToTopVisibility(recyclerView: RecyclerView) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
        val bt = scrollToTopBt ?: return
        if (firstVisible > 3) {
            if (bt.visibility != View.VISIBLE) {
                bt.visibility = View.VISIBLE
                bt.animate().cancel()
                bt.alpha = 0f
                bt.animate().alpha(1f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
            }
        } else {
            if (bt.visibility == View.VISIBLE) {
                bt.animate().cancel()
                bt.animate().alpha(0f).setDuration(200).setInterpolator(DecelerateInterpolator())
                    .withEndAction { bt.visibility = View.GONE }
                    .start()
            }
        }
    }

    fun requestFocus(reversed: Boolean = false) {
        if (isDestroyed()) {
            return
        }
        val targetIndex = if (reversed) dataBinding.storageFileRv.childCount - 1 else 0
        dataBinding.storageFileRv.getChildAt(targetIndex)?.requestFocus()
    }

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

    fun search(text: String) {
        dataBinding.refreshLayout.isEnabled = text.isEmpty()
        viewModel.searchByText(text)
    }

    fun sort() {
        viewModel.changeSortOption()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        thumbnailUpdateHandler.removeCallbacks(thumbnailBatchRunnable)
        pendingThumbnailUpdates.clear()
        ThumbnailGeneratorManager.setThumbnailCallback(null)
        ThumbnailGeneratorManager.clearPendingTasks()
    }
    
    override fun onThumbnailGenerated(file: StorageFile) {
        if (isDestroyed()) {
            return
        }
        
        if (isScrolling) {
            pendingThumbnailFiles.add(file.uniqueKey())
        } else {
            val position = fileIndexMap[file.uniqueKey()] ?: return
            pendingThumbnailUpdates.add(position)
            thumbnailUpdateHandler.removeCallbacks(thumbnailBatchRunnable)
            thumbnailUpdateHandler.postDelayed(thumbnailBatchRunnable, 80)
        }
    }
    
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

        val positions = filesToDisplay.mapNotNull { uniqueKey ->
            val position = fileIndexMap[uniqueKey] ?: return@mapNotNull null
            if (position in firstVisible..lastVisible) position else null
        }.sorted().distinct()

        if (positions.isEmpty()) return
        
        val adapter = dataBinding.storageFileRv.adapter ?: return
        var rangeStart = positions[0]
        var rangeEnd = positions[0]
        for (i in 1 until positions.size) {
            if (positions[i] == rangeEnd + 1) {
                rangeEnd = positions[i]
            } else {
                adapter.notifyItemRangeChanged(rangeStart, rangeEnd - rangeStart + 1, "thumbnail_updated")
                rangeStart = positions[i]
                rangeEnd = positions[i]
            }
        }
        adapter.notifyItemRangeChanged(rangeStart, rangeEnd - rangeStart + 1, "thumbnail_updated")
    }
}
