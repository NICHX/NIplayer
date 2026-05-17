package com.xyoye.storage_component.ui.activities.download

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.extension.setData
import com.xyoye.common_component.extension.vertical
import com.xyoye.data_component.entity.DownloadState
import com.xyoye.data_component.entity.DownloadTaskEntity
import com.xyoye.storage_component.BR
import com.xyoye.storage_component.R
import com.xyoye.storage_component.databinding.ActivityDownloadBinding
import com.xyoye.storage_component.databinding.ItemDownloadTaskBinding
import androidx.core.view.isVisible
import com.xyoye.common_component.adapter.buildAdapter
import com.xyoye.common_component.adapter.addItem
import com.xyoye.common_component.adapter.setupDiffUtil
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.media.LocalFileVideoSource
import com.xyoye.common_component.utils.PathHelper
import com.xyoye.common_component.utils.formatFileSize
import com.xyoye.common_component.weight.ToastCenter
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

        dataBinding.downloadRv.layoutManager = dataBinding.downloadRv.vertical()
        dataBinding.downloadRv.adapter = createAdapter()

        lifecycleScope.launch {
            viewModel.allTasks.collectLatest { tasks ->
                dataBinding.downloadRv.setData(tasks)
                updateEmptyView(tasks.isEmpty())
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_download, menu)
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun createAdapter() = buildAdapter {
        setupDiffUtil {
            areItemsTheSame { old, new ->
                (old as DownloadTaskEntity).id == (new as DownloadTaskEntity).id
            }
            areContentsTheSame { old, new ->
                (old as DownloadTaskEntity).state == (new as DownloadTaskEntity).state &&
                        (old as DownloadTaskEntity).downloadedBytes == (new as DownloadTaskEntity).downloadedBytes
            }
        }
        addItem<DownloadTaskEntity, ItemDownloadTaskBinding>(R.layout.item_download_task) {
            initView { data, _, _ ->
                itemBinding.bindTaskViews(data)
            }
        }
    }

    private fun ItemDownloadTaskBinding.bindTaskViews(data: DownloadTaskEntity) {
        fileNameTv.text = data.fileName

        val progress = if (data.totalBytes > 0) {
            (data.downloadedBytes * 100 / data.totalBytes).toInt()
        } else 0
        progressBar.progress = progress
        progressTv.text = "$progress%"

        val downloadedStr = formatFileSize(data.downloadedBytes)
        val totalStr = formatFileSize(data.totalBytes)
        sizeTv.text = "$downloadedStr / $totalStr"

        statusTv.text = when (data.state) {
            DownloadState.WAITING -> "等待中"
            DownloadState.DOWNLOADING -> "下载中"
            DownloadState.PAUSED -> "已暂停"
            DownloadState.COMPLETED -> "已完成"
            DownloadState.FAILED -> "失败: ${data.errorMessage ?: "未知错误"}"
            DownloadState.CANCELLED -> "已取消"
            else -> ""
        }

        val isActive = data.state == DownloadState.WAITING || data.state == DownloadState.DOWNLOADING
        val isPaused = data.state == DownloadState.PAUSED
        val isFinished = data.state == DownloadState.COMPLETED || data.state == DownloadState.CANCELLED || data.state == DownloadState.FAILED

        pauseBt.isVisible = isActive || isPaused
        pauseBt.text = if (isPaused) "继续" else "暂停"
        pauseBt.setOnClickListener {
            if (isActive) viewModel.pauseTask(data.id)
            else if (isPaused) viewModel.resumeTask(data.id)
        }

        cancelBt.isVisible = isActive || isPaused
        cancelBt.setOnClickListener {
            viewModel.cancelTask(data.id)
        }

        openBt.isVisible = data.state == DownloadState.COMPLETED
        openBt.setOnClickListener {
            openDownloadedFile(data, isLongPress = false)
        }
        openBt.setOnLongClickListener {
            openDownloadedFile(data, isLongPress = true)
            true
        }

        clearRecordBt.isVisible = isFinished
        clearRecordBt.setOnClickListener {
            viewModel.clearRecord(data.id)
        }

        deleteBt.isVisible = isFinished
        deleteBt.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("删除文件")
                .setMessage("确定要删除文件「${data.fileName}」吗？")
                .setPositiveButton("删除") { _, _ ->
                    viewModel.deleteTask(data.id)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun openDownloadedFile(task: DownloadTaskEntity, isLongPress: Boolean) {
        val uri: Uri?
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
        return if (task.targetStorageUrl != null) {
            val treeUri = Uri.parse(task.targetStorageUrl)
            val treeDoc = DocumentFile.fromTreeUri(this, treeUri)
            treeDoc?.findFile(task.fileName)?.uri
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

    private fun updateEmptyView(isEmpty: Boolean) {
        dataBinding.emptyTv.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
    }
}
