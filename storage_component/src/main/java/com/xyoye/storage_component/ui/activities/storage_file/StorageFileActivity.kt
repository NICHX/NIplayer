package com.xyoye.storage_component.ui.activities.storage_file

import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.android.arouter.facade.annotation.Autowired
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.google.android.material.appbar.AppBarLayout
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.AppConfig
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.horizontal
import com.xyoye.common_component.extension.setData

import com.xyoye.common_component.storage.Storage
import com.xyoye.common_component.storage.StorageFactory
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.impl.FtpStorage
import com.xyoye.common_component.utils.SupervisorScope
import com.xyoye.common_component.weight.BottomActionDialog
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.bean.SheetActionBean
import com.xyoye.data_component.bean.StorageFilePath
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.storage_component.BR
import com.xyoye.storage_component.R
import com.xyoye.storage_component.databinding.ActivityStorageFileBinding
import com.xyoye.storage_component.ui.fragment.storage_file.StorageFileFragment
import com.xyoye.storage_component.ui.weight.StorageFileMenus
import com.xyoye.storage_component.utils.storage.StorageFilePathAdapter
import com.xyoye.storage_component.utils.storage.StorageFileStyleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayList

@Route(path = RouteTable.Stream.StorageFile)
class StorageFileActivity : BaseActivity<StorageFileViewModel, ActivityStorageFileBinding>() {

    @Autowired
    @JvmField
    var storageLibrary: MediaLibraryEntity? = null

    lateinit var storage: Storage
        private set

    // 当前所处文件夹
    var directory: StorageFile? = null
        private set

    // 当前布局模式：true为网格视图，false为列表视图
    var isGridView: Boolean
        get() = AppConfig.isGridView()
        set(value) {
            AppConfig.putGridView(value)
        }

    // 标题栏菜单管理器
    private var mMenus: StorageFileMenus? = null

    // 文件Fragment列表
    private val mRouteFragmentMap = mutableMapOf<StorageFilePath, StorageFileFragment>()

    // 标题栏样式工具
    private val mToolbarStyleHelper: StorageFileStyleHelper by lazy {
        StorageFileStyleHelper(this, dataBinding)
    }

    // 全局焦点变化监听，用于控制标题栏展开/收缩
    private val focusChangeListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        handleGlobalFocusChange(newFocus)
    }

    var shareStorageFile: StorageFile? = null

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            StorageFileViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_storage_file

    override fun onCreate(savedInstanceState: Bundle?) {
        ARouter.getInstance().inject(this)

        if (checkBundle().not()) {
            super.onCreate(savedInstanceState)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
    }

    override fun initView() {
        mToolbarStyleHelper.observerChildScroll()
        dataBinding.coordinatorLayout.viewTreeObserver
            .addOnGlobalFocusChangeListener(focusChangeListener)
        title = storageLibrary?.displayName
        updateToolbarSubtitle(0, 0)

        initPathRv()
        initListener()
        openDirectory(null)
    }

    private fun initPathRv() {
        dataBinding.pathRv.apply {
            layoutManager = horizontal()

            adapter = StorageFilePathAdapter.build(this@StorageFileActivity) {
                backToRouteFragment(it)
            }
        }
    }

    private fun initListener() {
        mToolbar?.setNavigationOnClickListener {
            if (popFragment().not()) {
                finish()
            }
        }

        dataBinding.pathRv.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 将RecyclerView焦点转移到子View
                currentFocus?.requestFocus(View.FOCUS_UP)
            }
        }

        viewModel.playLiveData.observe(this) {
            ARouter.getInstance()
                .build(RouteTable.Player.Player)
                .navigation()
        }

        if (storage is FtpStorage) {
            lifecycle.coroutineScope.launchWhenResumed {
                withContext(Dispatchers.IO) {
                    (storage as FtpStorage).completePending()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        mMenus = StorageFileMenus.inflater(this, menu).apply {
            onSearchTextChanged { onSearchTextChanged(it) }
            onSortTypeChanged { onSortOptionChanged() }
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        mMenus?.onOptionsItemSelected(item)
        return super.onOptionsItemSelected(item)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && mMenus?.handleBackPressed() == true) {
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && popFragment()) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        shareStorageFile = null
        if (this::storage.isInitialized) {
            SupervisorScope.IO.launch {
                storage.close()
            }
        }
        val observer = dataBinding.coordinatorLayout.viewTreeObserver
        if (observer.isAlive) {
            observer.removeOnGlobalFocusChangeListener(focusChangeListener)
        }
        super.onDestroy()
    }

    private fun checkBundle(): Boolean {
        storageLibrary
            ?: return false
        val storage = StorageFactory.createStorage(storageLibrary!!)
            ?: return false

        this.storage = storage
        return true
    }

    private fun pushFragment(path: StorageFilePath) {
        val fragment = StorageFileFragment.newInstance()
        mRouteFragmentMap[path] = fragment

        supportFragmentManager.beginTransaction().apply {
            // 添加前的最后一个Fragment，设置为STARTED状态
            supportFragmentManager.fragments.lastOrNull()?.let {
                setMaxLifecycle(it, Lifecycle.State.STARTED)
            }

            setCustomAnimations(R.anim.fragment_fade_in, R.anim.fragment_fade_out)
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            add(dataBinding.fragmentContainer.id, fragment, path.route)

            // 当前添加的Fragment，设置为RESUMED状态
            setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            commit()
        }

        onDisplayFragmentChanged()
    }

    private fun popFragment(): Boolean {
        if (mRouteFragmentMap.entries.size <= 1) {
            return false
        }
        val lastRoute = mRouteFragmentMap.keys.last()
        val fragment = mRouteFragmentMap.remove(lastRoute)
            ?: return true
        removeFragment(listOf(fragment))
        onDisplayFragmentChanged()

        // 恢复 storage.directoryFiles 为上一级目录的文件列表
        // 避免视频播放时使用残留的过期文件列表
        restoreDirectoryFiles()

        return true
    }

    private fun backToRouteFragment(target: StorageFilePath) {
        val fragments = mutableListOf<Fragment>()
        for (path in mRouteFragmentMap.keys.reversed()) {
            if (path == target) {
                break
            }
            mRouteFragmentMap.remove(path)?.let { fragment ->
                fragments.add(fragment)
            }
        }
        removeFragment(fragments)
        onDisplayFragmentChanged()

        // 恢复 storage.directoryFiles 为目标目录的文件列表
        // 避免视频播放时使用残留的过期文件列表
        restoreDirectoryFiles()
    }

    private fun restoreDirectoryFiles() {
        val previousFragment = mRouteFragmentMap.values.lastOrNull()
        if (previousFragment is StorageFileFragment) {
            storage.directoryFiles = previousFragment.getCurrentFileList()
        }
    }

    private fun removeFragment(fragments: List<Fragment>) {
        supportFragmentManager.beginTransaction().apply {
            setCustomAnimations(R.anim.fragment_fade_in, R.anim.fragment_fade_out)
            setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)

            fragments.forEach {
                remove(it)
                // 当前移除的Fragment，设置为CREATED状态
                setMaxLifecycle(it, Lifecycle.State.CREATED)
            }

            // 非移除的最后一个Fragment，设置为RESUMED状态
            supportFragmentManager.fragments
                .lastOrNull { it !in fragments }
                ?.let { setMaxLifecycle(it, Lifecycle.State.RESUMED) }
            commit()
        }
    }

    private fun onDisplayFragmentChanged() {
        val newPathData = StorageFilePathAdapter.buildPathData(mRouteFragmentMap)
        dataBinding.pathRv.setData(newPathData)
        dataBinding.pathRv.post {
            dataBinding.pathRv.smoothScrollToPosition(newPathData.size - 1)
        }
    }



    /**
     * 更新标题栏副标题
     */
    private fun updateToolbarSubtitle(videoCount: Int, directoryCount: Int) {
        supportActionBar?.subtitle = when {
            videoCount == 0 && directoryCount == 0 -> {
                "0视频"
            }

            directoryCount == 0 -> {
                "${videoCount}视频"
            }

            videoCount == 0 -> {
                "${directoryCount}文件夹"
            }

            else -> {
                "${videoCount}视频  ${directoryCount}文件夹"
            }
        }
    }

    /**
     * 搜索文案
     */
    private fun onSearchTextChanged(text: String) {
        mRouteFragmentMap.values.lastOrNull()?.search(text)
    }

    /**
     * 改变文件排序
     */
    private fun onSortOptionChanged() {
        mRouteFragmentMap.values.onEach { it.sort() }
    }

    /**
     * 焦点切换时，处理标题栏展开/收缩
     */
    private fun handleGlobalFocusChange(newFocus: View?) {
        if (newFocus == null) {
            return
        }
        when {
            // 焦点在标题栏区域
            hasAncestorOfType(newFocus, AppBarLayout::class.java) -> setToolbarExpanded(true)

            // 焦点在内容区列表
            hasAncestorOfType(newFocus, RecyclerView::class.java) -> setToolbarExpanded(false)
        }
    }

    /**
     * 根据焦点归属切换标题栏展开/收缩
     */
    private fun setToolbarExpanded(expand: Boolean) {
        val isExpanded = mToolbarStyleHelper.isToolbarCollapsed().not()
        if (expand && isExpanded) {
            return
        }
        dataBinding.appbarLayout.setExpanded(expand, true)
    }

    /**
     * 判断view是否存在指定类型的祖先
     */
    private fun hasAncestorOfType(view: View, clazz: Class<out View>): Boolean {
        var current: View? = view
        while (current != null) {
            if (clazz.isInstance(current)) {
                return true
            }
            current = current.parent as? View
        }
        return false
    }

    /**
     * 分发焦点到最后一个Fragment
     */
    fun dispatchFocus(reversed: Boolean = false) {
        mRouteFragmentMap.values.lastOrNull()?.requestFocus(reversed)
    }

    fun openDirectory(file: StorageFile?) {
        directory = file

        val route = file?.filePath() ?: "/"
        val name = file?.fileName() ?: "根目录"
        pushFragment(StorageFilePath(name, route))
    }

    fun onDirectoryOpened(fileList: List<StorageFile>) {
        val videoCount = fileList.count { it.isFile() }
        val directoryCount = fileList.count { it.isDirectory() }
        updateToolbarSubtitle(videoCount, directoryCount)
    }

    fun openFile(file: StorageFile) {
        when {
            file.isVideoFile() -> {
                viewModel.playItem(file)
            }
            file.isAudioFile() -> {
                viewModel.playItem(file)
            }
            file.isImageFile() -> {
                openImageFile(file)
            }
            else -> {
                viewModel.playItem(file)
            }
        }
    }

    private fun openImageFile(file: StorageFile) {
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            try {
                // 检查是否是本地文件系统
                val filePath = file.filePath()
                val localFile = File(filePath)
                
                if (filePath.isNotEmpty() && localFile.exists() && localFile.parentFile != null) {
                    // 本地存储 - 获取当前目录所有图片
                    val imageUrls = ArrayList<String>()
                    var currentPosition = 0
                    
                    val parentDir = localFile.parentFile
                    val files = parentDir?.listFiles() ?: emptyArray()
                    
                    files.filter { it.isFile && isImageFile(it.name) }
                        .sortedBy { it.name }
                        .forEachIndexed { index, f ->
                            imageUrls.add(f.absolutePath)
                            if (f.absolutePath == localFile.absolutePath) {
                                currentPosition = index
                            }
                        }
                    
                    if (imageUrls.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            ARouter.getInstance()
                                .build(RouteTable.ImageViewer.Viewer)
                                .withStringArrayList(
                                    com.xyoye.common_component.ui.image_viewer.ImageViewerActivity.EXTRA_IMAGE_URIS,
                                    imageUrls
                                )
                                .withInt(
                                    com.xyoye.common_component.ui.image_viewer.ImageViewerActivity.EXTRA_CURRENT_POSITION,
                                    currentPosition
                                )
                                .navigation()
                        }
                    }
                } else {
                    // 设备存储库或网络存储 - 从当前文件列表中获取所有图片，支持左右滑动切换
                    val currentFragment = mRouteFragmentMap.values.lastOrNull()
                    val allFiles = currentFragment?.getCurrentFileList() ?: emptyList()

                    val imageUrls = ArrayList<String>()
                    var currentPosition = 0

                    val imageFiles = allFiles.filter { it.isFile() && it.isImageFile() }
                    imageFiles.forEachIndexed { index, imageFile ->
                        val imageUrl = getImageUrl(imageFile)
                        if (imageUrl.isNotEmpty()) {
                            if (imageFile.uniqueKey() == file.uniqueKey()) {
                                currentPosition = imageUrls.size
                            }
                            imageUrls.add(imageUrl)
                        }
                    }

                    if (imageUrls.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            ARouter.getInstance()
                                .build(RouteTable.ImageViewer.Viewer)
                                .withStringArrayList(
                                    com.xyoye.common_component.ui.image_viewer.ImageViewerActivity.EXTRA_IMAGE_URIS,
                                    imageUrls
                                )
                                .withInt(
                                    com.xyoye.common_component.ui.image_viewer.ImageViewerActivity.EXTRA_CURRENT_POSITION,
                                    currentPosition
                                )
                                .navigation()
                        }
                    } else {
                        // 回退：单张图片
                        val imageUrl = getImageUrl(file)
                        if (imageUrl.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                ARouter.getInstance()
                                    .build(RouteTable.ImageViewer.Viewer)
                                    .withString(
                                        com.xyoye.common_component.ui.image_viewer.ImageViewerActivity.EXTRA_IMAGE_URI,
                                        imageUrl
                                    )
                                    .navigation()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                ToastCenter.showError("无法获取图片地址")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    ToastCenter.showError("打开图片失败: ${e.message}")
                }
            }
        }
    }

    private suspend fun getImageUrl(file: StorageFile): String {
        // 优先尝试本地文件路径
        val filePath = file.filePath()
        val localFile = File(filePath)
        if (filePath.isNotEmpty() && localFile.exists()) {
            return filePath
        }

        // 尝试使用 createPlayUrl 获取可访问的 HTTP URL
        val playUrl = file.storage.createPlayUrl(file)
        if (playUrl != null && playUrl.isNotEmpty()) {
            return playUrl
        }

        // 最后尝试 fileUrl() - 这个可能是 Content URI
        val fileUrl = file.fileUrl()
        if (fileUrl.isNotEmpty()) {
            return fileUrl
        }

        return ""
    }

    private fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val imageExtensions = arrayOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heif", "heic")
        return imageExtensions.contains(extension)
    }
}
