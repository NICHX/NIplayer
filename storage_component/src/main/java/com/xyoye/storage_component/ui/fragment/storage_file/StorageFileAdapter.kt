package com.xyoye.storage_component.ui.fragment.storage_file

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.TextUtils
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.adapter.BaseAdapter
import com.xyoye.common_component.adapter.BaseViewHolderCreator
import com.xyoye.common_component.adapter.addEmptyView
import com.xyoye.common_component.adapter.addItem
import com.xyoye.common_component.adapter.buildAdapter
import com.xyoye.common_component.adapter.setupDiffUtil
import com.xyoye.common_component.adapter.setupVerticalAnimation
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.databinding.ItemStorageAudioBinding
import com.xyoye.common_component.databinding.ItemStorageAudioGridBinding
import com.xyoye.common_component.databinding.ItemStorageFolderBinding
import com.xyoye.common_component.databinding.ItemStorageFolderGridBinding
import com.xyoye.common_component.databinding.ItemStorageImageBinding
import com.xyoye.common_component.databinding.ItemStorageImageGridBinding
import com.xyoye.common_component.databinding.ItemStorageVideoBinding
import com.xyoye.common_component.databinding.ItemStorageVideoGridBinding
import com.xyoye.common_component.databinding.ItemStorageVideoTagBinding
import com.xyoye.common_component.extension.dp
import com.xyoye.common_component.extension.horizontal
import com.xyoye.common_component.extension.isInvalid
import com.xyoye.common_component.extension.loadStorageFileCover
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.extension.toFile
import com.xyoye.common_component.extension.toResColor
import com.xyoye.common_component.extension.toResDrawable
import com.xyoye.common_component.extension.toResString
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.download.DownloadManager
import com.xyoye.common_component.database.DatabaseManager
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.enums.MediaType
import com.xyoye.common_component.storage.file.subtitle
import com.xyoye.common_component.storage.impl.SmbStorage
import com.xyoye.common_component.storage.impl.WebDavStorage
import com.xyoye.common_component.utils.PlayHistoryUtils
import com.xyoye.common_component.utils.formatDuration
import com.xyoye.common_component.utils.formatFileSize
import com.xyoye.common_component.utils.getRecognizableFileName
import com.xyoye.common_component.utils.view.ItemDecorationOrientation
import com.xyoye.common_component.weight.BottomActionDialog
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.common_component.weight.dialog.FileManagerDialog
import com.xyoye.data_component.bean.SheetActionBean
import com.xyoye.data_component.bean.StorageFileInfo
import com.xyoye.data_component.bean.VideoTagBean
import com.xyoye.data_component.enums.FileManagerAction
import com.xyoye.data_component.enums.TrackType
import com.xyoye.storage_component.R
import com.xyoye.storage_component.ui.activities.storage_file.StorageFileActivity
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Created by xyoye on 2023/4/13
 */

class StorageFileAdapter(
    private val activity: StorageFileActivity,
    private val viewModel: StorageFileFragmentViewModel,
    private val isGridView: Boolean = false
) {

    private enum class ManageAction(val title: String, val icon: Int) {
        BIND_SUBTITLE("手动查找字幕", com.xyoye.common_component.R.drawable.ic_bind_subtitle),
        BIND_AUDIO("添加音频文件", com.xyoye.common_component.R.drawable.ic_bind_audio),
        UNBIND_SUBTITLE("移除字幕绑定", com.xyoye.common_component.R.drawable.ic_unbind_subtitle),
        UNBIND_AUDIO("移除音频绑定", com.xyoye.common_component.R.drawable.ic_unbind_subtitle),
        DOWNLOAD("下载", com.xyoye.common_component.R.drawable.ic_arrow_down),
        FILE_INFO("文件信息", com.xyoye.common_component.R.drawable.ic_tag),
        DELETE("删除", com.xyoye.common_component.R.drawable.ic_delete_red);

        fun toAction() = SheetActionBean(this, title, icon)
    }

    private val tagDecoration = ItemDecorationOrientation(5.dp(), 0, RecyclerView.HORIZONTAL)

    fun create(): BaseAdapter {
        return if (isGridView) {
            createGridAdapter()
        } else {
            createListAdapter()
        }
    }

    private fun createListAdapter(): BaseAdapter {
        return buildAdapter {
            // 移除垂直动画在滚动时可能导致卡顿，移除或简化动画

            setupDiffUtil {
                newDataInstance { it }
                areItemsTheSame(isSameStorageFileItem())
                areContentsTheSame(isSameStorageFileContent())
            }

            addEmptyView(R.layout.layout_empty) {
                initEmptyView {
                    itemBinding.emptyTv.text = "空文件夹"
                }
            }

            addItem<StorageFile, ItemStorageFolderBinding>(R.layout.item_storage_folder) {
                checkType { data -> isDirectoryItem(data) }
                initView(directoryListItem())
            }
            addItem<StorageFile, ItemStorageVideoBinding>(R.layout.item_storage_video) {
                checkType { data -> isVideoItem(data) }
                initView(videoListItem())
                initViewForPayload { data, _, payloads ->
                    if (payloads.contains("thumbnail_updated")) {
                        itemBinding.coverIv.loadStorageFileCover(data)
                    }
                }
            }
            addItem<StorageFile, ItemStorageAudioBinding>(R.layout.item_storage_audio) {
                checkType { data -> isAudioItem(data) }
                initView(audioListItem())
                initViewForPayload { data, _, payloads ->
                    if (payloads.contains("thumbnail_updated")) {
                        itemBinding.coverIv.loadStorageFileCover(data)
                    }
                }
            }
            addItem<StorageFile, ItemStorageImageBinding>(R.layout.item_storage_image) {
                checkType { data -> isImageItem(data) }
                initView(imageListItem())
                initViewForPayload { data, _, payloads ->
                    if (payloads.contains("thumbnail_updated")) {
                        itemBinding.coverIv.loadStorageFileCover(data)
                    }
                }
            }
        }
    }

    private fun createGridAdapter(): BaseAdapter {
        return buildAdapter {
            // 移除垂直动画在滚动时可能导致卡顿，移除或简化动画

            setupDiffUtil {
                newDataInstance { it }
                areItemsTheSame(isSameStorageFileItem())
                areContentsTheSame(isSameStorageFileContent())
            }

            addEmptyView(R.layout.layout_empty) {
                initEmptyView {
                    itemBinding.emptyTv.text = "空文件夹"
                }
            }

            addItem<StorageFile, ItemStorageFolderGridBinding>(R.layout.item_storage_folder_grid) {
                checkType { data -> isDirectoryItem(data) }
                initView(directoryGridItem())
            }
            addItem<StorageFile, ItemStorageVideoGridBinding>(R.layout.item_storage_video_grid) {
                checkType { data -> isVideoItem(data) }
                initView(videoGridItem())
                initViewForPayload { data, _, payloads ->
                    if (payloads.contains("thumbnail_updated")) {
                        itemBinding.coverIv.loadStorageFileCover(data)
                    }
                }
            }
            addItem<StorageFile, ItemStorageAudioGridBinding>(R.layout.item_storage_audio_grid) {
                checkType { data -> isAudioItem(data) }
                initView(audioGridItem())
                initViewForPayload { data, _, payloads ->
                    if (payloads.contains("thumbnail_updated")) {
                        itemBinding.coverIv.loadStorageFileCover(data)
                    }
                }
            }
            addItem<StorageFile, ItemStorageImageGridBinding>(R.layout.item_storage_image_grid) {
                checkType { data -> isImageItem(data) }
                initView(imageGridItem())
                initViewForPayload { data, _, payloads ->
                    if (payloads.contains("thumbnail_updated")) {
                        itemBinding.coverIv.loadStorageFileCover(data, 256)
                    }
                }
            }
        }
    }

    private fun isSameStorageFileItem() = { old: Any, new: Any ->
        (old as? StorageFile)?.uniqueKey() == (new as? StorageFile)?.uniqueKey()
    }

    private fun isSameStorageFileContent() = { old: Any, new: Any ->
        val oldItem = old as? StorageFile?
        val newItem = new as? StorageFile?
        oldItem?.fileUrl() == newItem?.fileUrl()
                && oldItem?.fileName() == newItem?.fileName()
                && oldItem?.childFileCount() == newItem?.childFileCount()
                && oldItem?.playHistory == newItem?.playHistory
                && oldItem?.playHistory?.isLastPlay == newItem?.playHistory?.isLastPlay
    }

    private fun isDirectoryItem(data: Any) = data is StorageFile && data.isDirectory()

    private fun isVideoItem(data: Any) = data is StorageFile && data.isFile() && data.isVideoFile()

    private fun isAudioItem(data: Any) = data is StorageFile && data.isFile() && data.isAudioFile()

    private fun isImageItem(data: Any) = data is StorageFile && data.isFile() && data.isImageFile()

    private fun BaseViewHolderCreator<ItemStorageFolderBinding>.directoryListItem() =
        { data: StorageFile ->
            val childFileCount = data.childFileCount()
            val fileCount = if (childFileCount > 0)
                "${childFileCount}文件"
            else
                "目录"
            itemBinding.folderTv.text = getRecognizableFileName(data)
            itemBinding.folderTv.setTextColor(getTitleColor(data))
            itemBinding.fileCountTv.text = fileCount
            itemBinding.itemLayout.setOnClickListener {
                activity.openDirectory(data)
            }
            itemBinding.itemLayout.setOnLongClickListener {
                showMoreAction(data, null)
                return@setOnLongClickListener true
            }
        }

    private fun BaseViewHolderCreator<ItemStorageFolderGridBinding>.directoryGridItem() =
        { data: StorageFile ->
            val childFileCount = data.childFileCount()
            val fileCount = if (childFileCount > 0)
                "${childFileCount}文件"
            else
                "目录"
            itemBinding.folderTv.text = getRecognizableFileName(data)
            itemBinding.folderTv.setTextColor(getTitleColor(data))
            itemBinding.fileCountTv.text = fileCount
            itemBinding.itemLayout.setOnClickListener {
                activity.openDirectory(data)
            }
            itemBinding.itemLayout.setOnLongClickListener {
                showMoreAction(data, null)
                return@setOnLongClickListener true
            }
        }

    private fun BaseViewHolderCreator<ItemStorageVideoBinding>.videoListItem() = { data: StorageFile ->
        itemBinding.run {
            coverIv.loadStorageFileCover(data)
            playOverlayIv.isVisible = true

            titleTv.text = data.fileName()
            titleTv.setTextColor(getTitleColor(data))

            val duration = getDuration(data)
            durationTv.text = duration
            durationTv.isVisible = duration.isNotEmpty()

            setupVideoTag(tagRv, data)

            mainActionFl.setOnClickListener {
                activity.openFile(data)
            }

            moreActionIv.setOnClickListener {
                showMoreAction(data, createShareOptions(itemLayout))
            }

            mainActionFl.setOnLongClickListener {
                showMoreAction(data, createShareOptions(itemLayout))
                return@setOnLongClickListener true
            }
        }
    }

    private fun BaseViewHolderCreator<ItemStorageVideoGridBinding>.videoGridItem() = { data: StorageFile ->
        itemBinding.run {
            coverIv.loadStorageFileCover(data)
            playOverlayIv.isVisible = true

            titleTv.text = data.fileName()
            titleTv.setTextColor(getTitleColor(data))

            val duration = getDuration(data)
            durationTv.text = duration
            durationTv.isVisible = duration.isNotEmpty()

            mainActionFl.setOnClickListener {
                activity.openFile(data)
            }

            mainActionFl.setOnLongClickListener {
                showMoreAction(data, null)
                return@setOnLongClickListener true
            }
        }
    }

    private fun BaseViewHolderCreator<ItemStorageAudioBinding>.audioListItem() = { data: StorageFile ->
        itemBinding.run {
            coverIv.loadStorageFileCover(data)

            titleTv.text = data.fileName()
            titleTv.setTextColor(getTitleColor(data))

            mainActionFl.setOnClickListener {
                activity.openFile(data)
            }

            moreActionIv.setOnClickListener {
                showMoreAction(data, null)
            }

            mainActionFl.setOnLongClickListener {
                showMoreAction(data, null)
                return@setOnLongClickListener true
            }
        }
    }

    private fun BaseViewHolderCreator<ItemStorageAudioGridBinding>.audioGridItem() = { data: StorageFile ->
        itemBinding.run {
            coverIv.loadStorageFileCover(data)

            titleTv.text = data.fileName()
            titleTv.setTextColor(getTitleColor(data))

            mainActionFl.setOnClickListener {
                activity.openFile(data)
            }

            mainActionFl.setOnLongClickListener {
                showMoreAction(data, null)
                return@setOnLongClickListener true
            }
        }
    }

    private fun BaseViewHolderCreator<ItemStorageImageBinding>.imageListItem() = { data: StorageFile ->
        itemBinding.run {
            coverIv.loadStorageFileCover(data)

            titleTv.text = data.fileName()
            titleTv.setTextColor(getTitleColor(data))

            mainActionFl.setOnClickListener {
                activity.openFile(data)
            }

            moreActionIv.setOnClickListener {
                showMoreAction(data, null)
            }

            mainActionFl.setOnLongClickListener {
                showMoreAction(data, null)
                return@setOnLongClickListener true
            }
        }
    }

    private fun BaseViewHolderCreator<ItemStorageImageGridBinding>.imageGridItem() = { data: StorageFile ->
        itemBinding.run {
            coverIv.loadStorageFileCover(data, 256)

            titleTv.text = data.fileName()
            titleTv.setTextColor(getTitleColor(data))

            mainActionFl.setOnClickListener {
                activity.openFile(data)
            }

            mainActionFl.setOnLongClickListener {
                showMoreAction(data, null)
                return@setOnLongClickListener true
            }
        }
    }

    private fun setupVideoTag(tagRv: RecyclerView, data: StorageFile) {
        // 检查是否已经设置过 adapter，避免重复创建
        if (tagRv.adapter == null) {
            tagRv.apply {
                layoutManager = horizontal()
                adapter = buildAdapter {
                    addItem(R.layout.item_storage_video_tag) { initView(tagItem()) }
                }
                removeItemDecoration(tagDecoration)
                addItemDecoration(tagDecoration)
            }
        }
        // 只更新数据 - 使用 items.clear() 和 addAll() 来更新
        val adapter = tagRv.adapter
        if (adapter is BaseAdapter) {
            adapter.items.clear()
            adapter.items.addAll(generateVideoTags(data))
            adapter.notifyDataSetChanged()
        }
    }

    private fun BaseViewHolderCreator<ItemStorageVideoTagBinding>.tagItem() =
        { data: VideoTagBean ->
            val background = R.drawable.background_video_tag.toResDrawable()
            background?.colorFilter = PorterDuffColorFilter(data.color, PorterDuff.Mode.SRC)
            itemBinding.textView.background = background
            itemBinding.textView.text = data.tag
        }

    private fun generateVideoTags(data: StorageFile): List<VideoTagBean> {
        val tagList = mutableListOf<VideoTagBean>()
        if (isShowSubtitle(data)) {
            tagList.add(VideoTagBean("字幕", R.color.orange.toResColor()))
        }
        if (isShowAudio(data)) {
            tagList.add(VideoTagBean("音频", R.color.pink.toResColor()))
        }
        val progress = getProgress(data)
        if (progress.isNotEmpty()) {
            tagList.add(VideoTagBean(progress, R.color.black_alpha.toResColor()))
        }
        val lastPlayTime = getPlayTime(data)
        if (lastPlayTime.isNotEmpty()) {
            tagList.add(VideoTagBean(lastPlayTime, R.color.black_alpha.toResColor()))
        }
        return tagList
    }

    private fun getTitleColor(file: StorageFile): Int {
        return when (file.playHistory?.isLastPlay == true) {
            true -> com.xyoye.common_component.R.color.text_theme.toResColor(activity)
            else -> com.xyoye.common_component.R.color.text_black.toResColor(activity)
        }
    }

    private fun getProgress(file: StorageFile): String {
        val position = file.playHistory?.videoPosition ?: 0
        val duration = file.playHistory?.videoDuration ?: 0
        if (position == 0L || duration == 0L) {
            return ""
        }

        var progress = (position * 100f / duration).toInt()
        if (progress == 0) {
            progress = 1
        }
        return "进度 $progress%"
    }

    private fun getDuration(file: StorageFile): String {
        val position = file.playHistory?.videoPosition ?: 0
        var duration = file.playHistory?.videoDuration ?: 0
        if (duration == 0L) {
            duration = file.videoDuration()
        }
        return if (position > 0 && duration > 0) {
            "${formatDuration(position)}/${formatDuration(duration)}"
        } else if (duration > 0) {
            formatDuration(duration)
        } else {
            ""
        }
    }

    private fun getPlayTime(file: StorageFile): String {
        // Url为空，意味着该历史记录为资源绑定记录，非播放记录
        if (TextUtils.isEmpty(file.playHistory?.url)) {
            return ""
        }
        return file.playHistory?.playTime?.run {
            PlayHistoryUtils.formatPlayTime(this)
        } ?: ""
    }

    private fun isShowSubtitle(file: StorageFile): Boolean {
        return file.playHistory?.subtitlePath?.isNotEmpty() == true
    }

    private fun isShowAudio(file: StorageFile): Boolean {
        return file.playHistory?.audioPath?.isNotEmpty() == true
    }

    private fun showMoreAction(file: StorageFile, options: ActivityOptionsCompat?) {
        BottomActionDialog(activity, getMoreActions(file)) {
            when (it.actionId) {
                ManageAction.BIND_SUBTITLE -> {
                    if (options != null) {
                        bindExtraSource(file, options)
                    } else {
                        ToastCenter.showError("无法启动字幕选择")
                    }
                }
                ManageAction.BIND_AUDIO -> bindAudioSource(file)
                ManageAction.UNBIND_SUBTITLE -> viewModel.unbindExtraSource(file, TrackType.SUBTITLE)
                ManageAction.UNBIND_AUDIO -> viewModel.unbindExtraSource(file, TrackType.AUDIO)
                ManageAction.DOWNLOAD -> downloadFile(file)
                ManageAction.FILE_INFO -> showFileInfo(file)
                ManageAction.DELETE -> confirmDelete(file)
            }
            return@BottomActionDialog true
        }.show()
    }


    private fun getMoreActions(file: StorageFile) =
        mutableListOf<SheetActionBean>().apply {
            if (file.isImageFile().not() && file.isAudioFile().not()) {
                add(ManageAction.BIND_SUBTITLE.toAction())
                add(ManageAction.BIND_AUDIO.toAction())
            }
            if (file.subtitle != null) {
                add(ManageAction.UNBIND_SUBTITLE.toAction())
            }
            if (file.playHistory?.audioPath != null) {
                add(ManageAction.UNBIND_AUDIO.toAction())
            }
            if (file.isFile()) {
                add(ManageAction.DOWNLOAD.toAction())
            }
            if (viewModel.storage is SmbStorage || viewModel.storage is WebDavStorage) {
                add(ManageAction.FILE_INFO.toAction())
                add(ManageAction.DELETE.toAction())
            }
        }

    private fun showFileInfo(file: StorageFile) {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val info = viewModel.storage.fileInfo(file)
            withContext(Dispatchers.Main) {
                if (info != null) {
                    showFileInfoDialog(info)
                } else {
                    ToastCenter.showError("获取文件信息失败")
                }
            }
        }
    }

    private fun showFileInfoDialog(info: StorageFileInfo) {
        val sizeStr = if (info.isDirectory) {
            "${info.childCount} 项"
        } else {
            formatFileSize(info.fileSize)
        }
        val message = buildString {
            append("名称: ${info.name}\n")
            val dirPath = if (info.isDirectory) info.path else {
                val lastSlash = info.path.lastIndexOf('/')
                if (lastSlash > 0) info.path.substring(0, lastSlash) else ""
            }
            append("路径: $dirPath\n")
            val typeLabel = when {
                info.isDirectory -> "文件夹"
                info.isVideo -> "视频文件"
                info.isAudio -> "音频文件"
                info.isImage -> "图片文件"
                else -> "文件"
            }
            append("类型: $typeLabel\n")
            append("大小: $sizeStr\n")
            if (info.isVideo) {
                if (info.videoWidth > 0 && info.videoHeight > 0) {
                    append("分辨率: ${info.videoWidth}×${info.videoHeight}\n")
                }
                if (info.durationMs > 0) {
                    val totalSec = info.durationMs / 1000
                    val hours = totalSec / 3600
                    val minutes = (totalSec % 3600) / 60
                    val seconds = totalSec % 60
                    val durationStr = if (hours > 0) {
                        String.format("%d:%02d:%02d", hours, minutes, seconds)
                    } else {
                        String.format("%d:%02d", minutes, seconds)
                    }
                    append("时长: $durationStr\n")
                }
                if (info.bitrate > 0) {
                    append("总码率: ${info.bitrate / 1000} kbps\n")
                }
                if (info.videoCodec != null) {
                    append("封装格式: ${info.videoCodec}\n")
                }
                if (info.frameRate != null) {
                    append("帧率: ${info.frameRate} fps\n")
                }
                if (info.sampleRate > 0) {
                    append("采样率: ${info.sampleRate / 1000} kHz\n")
                }
            }
            if (info.isAudio) {
                if (info.durationMs > 0) {
                    val totalSec = info.durationMs / 1000
                    val minutes = totalSec / 60
                    val seconds = totalSec % 60
                    append("时长: ${minutes}:${String.format("%02d", seconds)}\n")
                }
                if (info.bitrate > 0) {
                    append("码率: ${info.bitrate / 1000} kbps\n")
                }
                if (info.audioCodec != null) {
                    append("音频编码: ${info.audioCodec}\n")
                }
                if (info.sampleRate > 0) {
                    append("采样率: ${info.sampleRate / 1000} kHz\n")
                }
            }
            if (info.isImage) {
                if (info.videoWidth > 0 && info.videoHeight > 0) {
                    append("分辨率: ${info.videoWidth}×${info.videoHeight}\n")
                }
            }
        }
        android.app.AlertDialog.Builder(activity)
            .setTitle("文件信息")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }

    private fun confirmDelete(file: StorageFile) {
        val type = if (file.isDirectory()) "文件夹" else "文件"
        android.app.AlertDialog.Builder(activity)
            .setTitle("确认删除")
            .setMessage("确定要删除${type} \"${file.fileName()}\" 吗？${if (file.isDirectory()) "文件夹内的所有内容将被删除。" else ""}")
            .setPositiveButton("删除") { _, _ ->
                performDelete(file)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun performDelete(file: StorageFile) {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val success = viewModel.storage.delete(file)
            withContext(Dispatchers.Main) {
                if (success) {
                    ToastCenter.showSuccess("删除成功")
                    viewModel.listFile(viewModel.storage.directory, refresh = true)
                } else {
                    ToastCenter.showError("删除失败")
                }
            }
        }
    }

    private fun downloadFile(file: StorageFile) {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val externalLibraries = DatabaseManager.instance.getMediaLibraryDao()
                .getByMediaTypeSuspend(MediaType.EXTERNAL_STORAGE)

            if (externalLibraries.isEmpty()) {
                withContext(Dispatchers.Main) {
                    android.app.AlertDialog.Builder(activity)
                        .setTitle("无法下载")
                        .setMessage("请先在「媒体」页面点击 + 号添加「设备存储库」，设置保存路径后才能下载文件")
                        .setPositiveButton("确定", null)
                        .show()
                }
                return@launch
            }

            val actions = externalLibraries.map { library ->
                SheetActionBean(library, library.displayName, library.mediaType.cover)
            }

            withContext(Dispatchers.Main) {
                BottomActionDialog(activity, actions, "选择保存位置") {
                    val selectedLibrary = it.actionId as MediaLibraryEntity
                    DownloadManager.addTask(
                        storageId = viewModel.storage.library.id,
                        filePath = file.storagePath(),
                        fileName = file.fileName(),
                        uniqueKey = file.uniqueKey(),
                        totalBytes = file.fileLength(),
                        targetStorageUrl = selectedLibrary.url,
                        targetStorageName = selectedLibrary.displayName
                    )
                    ToastCenter.showSuccess("已添加到下载队列")
                    return@BottomActionDialog true
                }.show()
            }
        }
    }

    private fun bindExtraSource(
        file: StorageFile,
        options: ActivityOptionsCompat
    ) {
        activity.shareStorageFile = file
        ARouter.getInstance()
            .build(RouteTable.Local.BindExtraSource)
            .withOptionsCompat(options)
            .navigation(activity)
    }

    private fun createShareOptions(
        itemLayout: ConstraintLayout,
    ) = ActivityOptionsCompat.makeSceneTransitionAnimation(
        activity,
        Pair(itemLayout, itemLayout.transitionName),
    )

    private fun bindAudioSource(file: StorageFile) {
        FileManagerDialog(
            activity,
            FileManagerAction.ACTION_SELECT_AUDIO
        ) {
            if (it.toFile().isInvalid()) {
                ToastCenter.showError("绑定音频失败，音频不存在或内容为空")
                return@FileManagerDialog
            }
            viewModel.bindAudioSource(file, it)
        }.show()
    }
}