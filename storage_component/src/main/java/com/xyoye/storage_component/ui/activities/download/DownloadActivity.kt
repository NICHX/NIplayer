package com.xyoye.storage_component.ui.activities.download

import android.content.Intent
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.isVisible
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.media.LocalFileVideoSource
import com.xyoye.common_component.utils.PathHelper
import com.xyoye.common_component.utils.formatFileSize
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.entity.DownloadState
import com.xyoye.data_component.entity.DownloadTaskEntity
import com.xyoye.storage_component.BR
import com.xyoye.storage_component.R
import com.xyoye.storage_component.databinding.ActivityDownloadBinding
import com.xyoye.storage_component.databinding.ItemDownloadTaskBinding
import com.xyoye.storage_component.databinding.ItemSectionHeaderBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Route(path = RouteTable.Stream.DownloadManager)
class DownloadActivity : BaseActivity<DownloadViewModel, ActivityDownloadBinding>() {

    override fun initViewModel() =
        ViewModelInit(BR.viewModel, DownloadViewModel::class.java)

    override fun getLayoutId() = R.layout.activity_download

    override fun finish() {
        super.finish()
        overridePendingTransition(0, com.xyoye.common_component.R.anim.anim_slide_out_down)
    }

    override fun initView() {
        overridePendingTransition(com.xyoye.common_component.R.anim.anim_slide_in_up, 0)
        title = "下载管理"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val adapter = DownloadListAdapter()
        dataBinding.downloadRv.layoutManager = LinearLayoutManager(this)
        dataBinding.downloadRv.adapter = adapter
        (dataBinding.downloadRv.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.supportsChangeAnimations = false
        dataBinding.downloadRv.itemAnimator = null

        lifecycleScope.launch {
            viewModel.displayItems.collectLatest { items ->
                adapter.submitList(items)
                dataBinding.emptyTv.isVisible = items.isEmpty()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_download, menu)
        menu.findItem(R.id.action_retry_failed)?.isVisible = false
        lifecycleScope.launch {
            viewModel.displayItems.collectLatest { items ->
                val hasFailed = items.any {
                    it is DownloadGroupedItem.Task && it.display.task.state == DownloadState.FAILED
                }
                menu.findItem(R.id.action_retry_failed)?.isVisible = hasFailed
            }
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear_completed -> {
                viewModel.removeCompleted()
                true
            }
            R.id.action_clear_failed -> {
                viewModel.clearFailed()
                true
            }
            R.id.action_retry_failed -> {
                viewModel.retryAllFailed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private inner class DownloadListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_TYPE_SECTION = 0
        private val VIEW_TYPE_TASK = 1

        private var items: List<DownloadGroupedItem> = emptyList()

        fun submitList(newItems: List<DownloadGroupedItem>) {
            val oldItems = items
            items = newItems
            if (oldItems.isEmpty()) {
                notifyDataSetChanged()
                return
            }

            val diffCallback = object : androidx.recyclerview.widget.DiffUtil.Callback() {
                override fun getOldListSize() = oldItems.size
                override fun getNewListSize() = newItems.size

                override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val oldItem = oldItems[oldPos]
                    val newItem = newItems[newPos]
                    return when {
                        oldItem is DownloadGroupedItem.Section && newItem is DownloadGroupedItem.Section ->
                            oldItem.title == newItem.title
                        oldItem is DownloadGroupedItem.Task && newItem is DownloadGroupedItem.Task ->
                            oldItem.display.task.id == newItem.display.task.id
                        else -> false
                    }
                }

                override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                    val oldItem = oldItems[oldPos]
                    val newItem = newItems[newPos]
                    return when {
                        oldItem is DownloadGroupedItem.Section && newItem is DownloadGroupedItem.Section ->
                            oldItem == newItem
                        oldItem is DownloadGroupedItem.Task && newItem is DownloadGroupedItem.Task ->
                            oldItem.display.task.state == newItem.display.task.state &&
                            oldItem.display.downloadedBytes == newItem.display.downloadedBytes &&
                            oldItem.display.speed == newItem.display.speed &&
                            oldItem.display.eta == newItem.display.eta &&
                            oldItem.display.progress == newItem.display.progress
                        else -> false
                    }
                }

                override fun getChangePayload(oldPos: Int, newPos: Int): Any? {
                    val oldItem = oldItems[oldPos]
                    val newItem = newItems[newPos]
                    if (oldItem is DownloadGroupedItem.Task && newItem is DownloadGroupedItem.Task) {
                        return newItem.display
                    }
                    return null
                }
            }

            val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback, false)
            diffResult.dispatchUpdatesTo(this)
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is DownloadGroupedItem.Section -> VIEW_TYPE_SECTION
                is DownloadGroupedItem.Task -> VIEW_TYPE_TASK
            }
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = android.view.LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_SECTION -> {
                    val binding = ItemSectionHeaderBinding.inflate(inflater, parent, false)
                    SectionViewHolder(binding)
                }
                else -> {
                    val binding = ItemDownloadTaskBinding.inflate(inflater, parent, false)
                    TaskViewHolder(binding)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is SectionViewHolder -> {
                    val item = items[position] as DownloadGroupedItem.Section
                    holder.binding.sectionTitleTv.text = "${item.title} (${item.count})"
                }
                is TaskViewHolder -> {
                    val item = items[position] as DownloadGroupedItem.Task
                    bindTask(holder.binding, item.display)
                }
            }
        }

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            if (payloads.isEmpty() || holder !is TaskViewHolder) {
                super.onBindViewHolder(holder, position, payloads)
                return
            }

            val display = payloads.firstOrNull() as? DownloadTaskDisplay
            if (display != null) {
                bindTaskProgress(holder.binding, display)
                bindActionButtons(holder.binding, display.task)
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        private fun bindTask(binding: ItemDownloadTaskBinding, display: DownloadTaskDisplay) {
            val task = display.task
            binding.fileNameTv.text = task.fileName
            bindTaskProgress(binding, display)

            binding.statusTv.text = getStateText(task)
            bindActionButtons(binding, task)
        }

        private fun bindTaskProgress(binding: ItemDownloadTaskBinding, display: DownloadTaskDisplay) {
            val task = display.task
            val progress = display.progress
            binding.progressBar.progress = progress
            binding.progressTv.text = "$progress%"

            val downloadedStr = formatFileSize(display.downloadedBytes)
            val totalStr = formatFileSize(task.totalBytes)
            binding.sizeTv.text = "$downloadedStr / $totalStr"

            binding.speedTv.text = display.speed
            binding.speedTv.isVisible = display.speed.isNotEmpty()
            binding.etaTv.text = display.eta
            binding.etaTv.isVisible = display.eta.isNotEmpty()

            binding.statusTv.text = getStateText(task)
        }

        private fun getStateText(task: DownloadTaskEntity): String {
            return when (task.state) {
                DownloadState.WAITING -> "等待中"
                DownloadState.DOWNLOADING -> "下载中"
                DownloadState.PAUSED -> "已暂停"
                DownloadState.COMPLETED -> "已完成"
                DownloadState.FAILED -> "失败: ${task.errorMessage ?: "未知错误"}"
                DownloadState.CANCELLED -> "已取消"
                else -> ""
            }
        }

        private fun bindActionButtons(binding: ItemDownloadTaskBinding, task: DownloadTaskEntity) {
            val isActive = task.state == DownloadState.WAITING || task.state == DownloadState.DOWNLOADING
            val isPaused = task.state == DownloadState.PAUSED
            val isCompleted = task.state == DownloadState.COMPLETED
            val isFailed = task.state == DownloadState.FAILED
            val isFinished = isCompleted || task.state == DownloadState.CANCELLED || isFailed

            binding.pauseBt.isVisible = isActive || isPaused
            binding.pauseBt.text = if (isPaused) "继续" else "暂停"
            binding.pauseBt.setOnClickListener {
                if (isActive) viewModel.pauseTask(task.id)
                else if (isPaused) viewModel.resumeTask(task.id)
            }

            binding.cancelBt.isVisible = isActive || isPaused
            binding.cancelBt.setOnClickListener {
                android.app.AlertDialog.Builder(this@DownloadActivity)
                    .setTitle("取消下载")
                    .setMessage("取消后已下载的部分文件将被删除，确定取消「${task.fileName}」吗？")
                    .setPositiveButton("确定取消") { _, _ ->
                        viewModel.cancelTask(task.id)
                    }
                    .setNegativeButton("继续下载", null)
                    .show()
            }

            binding.retryBt.isVisible = isFailed
            binding.retryBt.setOnClickListener {
                viewModel.retryTask(task.id)
            }

            binding.openBt.isVisible = isCompleted
            binding.openBt.setOnClickListener {
                openDownloadedFile(task, isLongPress = false)
            }
            binding.openBt.setOnLongClickListener {
                openDownloadedFile(task, isLongPress = true)
                true
            }

            binding.clearRecordBt.isVisible = isFinished
            binding.clearRecordBt.setOnClickListener {
                viewModel.clearRecord(task.id)
            }

            binding.deleteBt.isVisible = isFinished
            binding.deleteBt.setOnClickListener {
                android.app.AlertDialog.Builder(this@DownloadActivity)
                    .setTitle("删除文件")
                    .setMessage("确定要删除文件「${task.fileName}」吗？")
                    .setPositiveButton("删除") { _, _ ->
                        viewModel.deleteTask(task.id)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private class SectionViewHolder(val binding: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    private class TaskViewHolder(val binding: ItemDownloadTaskBinding) : RecyclerView.ViewHolder(binding.root)

    private fun openDownloadedFile(task: DownloadTaskEntity, isLongPress: Boolean) {
        val ext = task.fileName.substringAfterLast('.', "").lowercase()
        val isMediaType = ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "ts",
            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a",
            "jpg", "jpeg", "png", "gif", "bmp", "webp")

        if (isLongPress || !isMediaType) {
            showSystemPicker(task)
            return
        }

        val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "ts", "m3u8")
        val isAudio = ext in listOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a")

        when {
            isVideo || isAudio -> openInAppPlayer(task)
            else -> openInAppImageViewer(task)
        }
    }

    private fun getFileUri(task: DownloadTaskEntity): Uri? {
        val storageUrl = task.targetStorageUrl
        return if (storageUrl != null) {
            if (storageUrl.startsWith("file://")) {
                val dirPath = storageUrl.removePrefix("file://")
                val file = java.io.File(dirPath, task.fileName)
                if (file.exists()) Uri.fromFile(file) else null
            } else {
                val directFile = com.xyoye.common_component.utils.SafPathResolver.resolveTargetFile(
                    this, storageUrl, task.fileName
                )
                if (directFile != null && directFile.exists())
                    Uri.fromFile(directFile)
                else
                    DocumentFile.fromTreeUri(this, Uri.parse(storageUrl))
                        ?.findFile(task.fileName)?.uri
            }
        } else {
            val file = java.io.File(PathHelper.getCachePath(), "download/${task.fileName}")
            if (file.exists()) Uri.fromFile(file) else null
        }
    }

    private fun openInAppPlayer(task: DownloadTaskEntity) {
        val uri = getFileUri(task) ?: run {
            ToastCenter.showError("文件不存在")
            return
        }
        val filePath = uri.toString()
        val source = LocalFileVideoSource(filePath, task.fileName)
        VideoSourceManager.getInstance().setSource(source)
        ARouter.getInstance()
            .build(RouteTable.Player.PlayerCenter)
            .navigation()
    }

    private fun openInAppImageViewer(task: DownloadTaskEntity) {
        val uri = getFileUri(task) ?: run {
            ToastCenter.showError("文件不存在")
            return
        }
        ARouter.getInstance()
            .build(RouteTable.ImageViewer.Viewer)
            .withString(com.xyoye.common_component.ui.image_viewer.ImageViewerActivity.EXTRA_IMAGE_URI, uri.toString())
            .navigation()
    }

    private fun showSystemPicker(task: DownloadTaskEntity) {
        val uri = getFileUri(task)
        if (uri == null) {
            ToastCenter.showError("文件不存在")
            return
        }
        val mimeType = getMimeType(task.fileName)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(intent, "选择打开方式"))
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "ts", "m3u8" -> "video/*"
            "mp3", "wav", "flac", "aac", "ogg", "wma", "m4a" -> "audio/*"
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "image/*"
            "txt", "log", "md", "json", "xml", "csv" -> "text/plain"
            "pdf" -> "application/pdf"
            "zip", "rar", "7z", "tar", "gz" -> "application/zip"
            "srt", "ass", "ssa", "vtt" -> "text/plain"
            else -> "*/*"
        }
    }
}
