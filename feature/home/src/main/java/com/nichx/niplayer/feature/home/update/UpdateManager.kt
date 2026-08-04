package com.nichx.niplayer.feature.home.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.nichx.niplayer.datastore.UpdateSettings
import com.nichx.niplayer.feature.home.R
import com.nichx.niplayer.network.update.GitHubApi
import com.nichx.niplayer.network.update.GitHubAsset
import com.nichx.niplayer.network.update.GitHubRelease
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * 应用在线更新管理器。
 *
 * 职责：
 * 1. 版本检测：[checkUpdate] 调用 [GitHubApi.getLatestRelease]，将 release 的 tag_name 与
 *    当前版本比较，判定是否有新版本可用。
 * 2. 下载更新：[startDownload] 通过系统 [DownloadManager] 后台下载 APK 到公共 Downloads 目录
 *    （无需存储权限，下载完成后系统通知可直接点击安装），进度由 [queryDownloadProgress] 查询，
 *    完成/失败通过 [downloadResult] 广播。
 * 3. 安装更新：[install] 用 FileProvider 暴露 APK 并拉起系统安装器；Android 8+ 若未授予
 *    "安装未知应用"权限，跳转系统设置页引导开启。
 *
 * 单例共享：MainActivity（启动自动检查）与设置页（手动检查）复用同一实例，避免重复下载。
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val githubApi: GitHubApi,
) {

    private val downloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private var currentDownloadId: Long = -1L

    private var receiverRegistered = false

    private val _downloadResult = MutableSharedFlow<DownloadOutcome>(extraBufferCapacity = 2)
    /** 下载终态广播：Completed / Failed（下载完成时进程存活的情况下触发）。 */
    val downloadResult: SharedFlow<DownloadOutcome> = _downloadResult.asSharedFlow()

    /**
     * 检查是否有新版本。
     *
     * @param auto 是否自动检查。为 true 时受 [UpdateSettings.AUTO_CHECK_INTERVAL_MS] 节流，
     *   距上次自动检查不足 24 小时直接返回 [UpdateCheckOutcome.Skipped]（不触网）。
     */
    suspend fun checkUpdate(auto: Boolean): UpdateCheckOutcome {
        if (auto) {
            val last = UpdateSettings.getLastAutoCheckTimestamp()
            if (System.currentTimeMillis() - last < UpdateSettings.AUTO_CHECK_INTERVAL_MS) {
                return UpdateCheckOutcome.Skipped
            }
            UpdateSettings.setLastAutoCheckTimestamp(System.currentTimeMillis())
        }
        return try {
            val release = githubApi.getLatestRelease()
            val latest = release.tagName?.trim()
            if (latest.isNullOrBlank()) return UpdateCheckOutcome.NoUpdate

            val remote = parseVersion(latest)
            val local = parseVersion(currentVersionName())
            if (compareVersions(remote, local) <= 0) {
                UpdateCheckOutcome.NoUpdate
            } else {
                val asset = release.assets.firstOrNull { it.isApk() }
                UpdateCheckOutcome.UpdateAvailable(
                    release = release,
                    latestVersion = stripVersionPrefix(latest),
                    asset = asset,
                )
            }
        } catch (e: Exception) {
            UpdateCheckOutcome.Error(e.message ?: context.getString(R.string.update_network_error))
        }
    }

    /**
     * 启动 APK 后台下载（公共 Downloads 目录，无需存储权限）。
     *
     * @param version 新版本号（用于记录待安装状态）
     * @return 入队成功返回下载任务 id；URL 缺失或入队失败返回 -1。
     */
    fun startDownload(release: GitHubRelease, asset: GitHubAsset, version: String): Long {
        val url = asset.browserDownloadUrl ?: return -1L
        val fileName = asset.name?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "NIplayer-$version.apk"
        registerReceiverIfNeeded()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(context.getString(R.string.update_notification_title, version))
            .setDescription(fileName)
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        return try {
            downloadManager.enqueue(request).also {
                currentDownloadId = it
                UpdateSettings.setDownloadedApk(version, fileName)
            }
        } catch (_: Exception) {
            -1L
        }
    }

    /** 查询当前下载任务进度。 */
    fun queryDownloadProgress(): DownloadProgress? {
        if (currentDownloadId < 0) return null
        val cursor = try {
            downloadManager.query(
                DownloadManager.Query().setFilterById(currentDownloadId)
            )
        } catch (_: Exception) {
            return null
        }
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded =
                it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val totalBytes =
                it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return DownloadProgress(
                status = status,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
            )
        }
    }

    /**
     * 拉起系统安装器安装已下载的 APK。
     *
     * Android 8+ 需要"安装未知应用"权限（[PackageManager.canRequestPackageInstalls]）。
     * 未授权时打开系统设置引导页并返回 [InstallOutcome.NeedPermission]。
     */
    fun install(): InstallOutcome {
        val fileName = UpdateSettings.getDownloadedFileName()
        val file = downloadedApkFile(fileName)
        if (fileName.isEmpty() || !file.exists()) {
            UpdateSettings.clearDownloadedApk()
            return InstallOutcome.NoFile
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return InstallOutcome.NeedPermission
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            // 已移交系统安装器：清除待安装记录，避免下次启动重复提示
            UpdateSettings.clearDownloadedApk()
            InstallOutcome.Started
        } catch (_: Exception) {
            UpdateSettings.clearDownloadedApk()
            InstallOutcome.NoFile
        }
    }

    /** 是否存在可安装的已下载 APK。 */
    fun hasDownloadedApk(): Boolean {
        val fileName = UpdateSettings.getDownloadedFileName()
        return fileName.isNotEmpty() && downloadedApkFile(fileName).exists()
    }

    /** 是否有正在进行的更新下载（含收起到后台的任务）。 */
    fun isDownloading(): Boolean = currentDownloadId >= 0

    /** 打开系统浏览器访问 GitHub Releases 页面（下载失败时的兜底通道）。 */
    fun openReleasesPage() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(GitHubApi.RELEASES_PAGE_URL),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** 取消当前更新下载（仅移除自身任务，不影响通知）。 */
    fun cancelDownload() {
        if (currentDownloadId >= 0) {
            runCatching { downloadManager.remove(currentDownloadId) }
            currentDownloadId = -1L
        }
        UpdateSettings.clearDownloadedApk()
    }

    /** 当前应用版本号（versionName）。 */
    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    /** 公共 Downloads 目录下的更新包文件。 */
    private fun downloadedApkFile(fileName: String): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

    /**
     * 注册下载完成广播接收器（应用进程内一次，使用 applicationContext 避免泄漏）。
     * 下载成功后触发 [DownloadOutcome.Completed]，由上层提示安装。
     *
     * Android 13+（targetSdk 34+）要求注册非系统专用广播时显式声明 exported 标志；
     * ACTION_DOWNLOAD_COMPLETE 由系统 DownloadManager 发送，使用 NOT_EXPORTED 可正常
     * 接收，同时防止第三方应用伪造下载完成广播。
     */
    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            updateReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id != currentDownloadId) return
            val progress = queryDownloadProgress() ?: return
            when (progress.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    _downloadResult.tryEmit(DownloadOutcome.Completed)
                }
                DownloadManager.STATUS_FAILED -> {
                    currentDownloadId = -1L
                    UpdateSettings.clearDownloadedApk()
                    _downloadResult.tryEmit(
                        DownloadOutcome.Failed(
                            reason = queryFailureReason(id),
                        )
                    )
                }
            }
        }
    }

    private fun queryFailureReason(id: Long): String {
        val cursor = try {
            downloadManager.query(DownloadManager.Query().setFilterById(id))
        } catch (_: Exception) {
            return context.getString(R.string.update_download_failed)
        }
        cursor.use {
            if (!it.moveToFirst()) return context.getString(R.string.update_download_failed)
            return it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                ?: context.getString(R.string.update_download_failed)
        }
    }

    private fun GitHubAsset.isApk(): Boolean = name?.endsWith(".apk", ignoreCase = true) == true
}

/** 更新检查结果。 */
sealed interface UpdateCheckOutcome {
    /** 自动检查被节流跳过（距上次检查不足 24h）。 */
    data object Skipped : UpdateCheckOutcome

    /** 无新版本。 */
    data object NoUpdate : UpdateCheckOutcome

    /** 存在新版本。 */
    data class UpdateAvailable(
        val release: GitHubRelease,
        val latestVersion: String,
        val asset: GitHubAsset?,
    ) : UpdateCheckOutcome

    /** 检查失败。 */
    data class Error(val message: String) : UpdateCheckOutcome
}

/** 下载进度快照（[android.app.DownloadManager.COLUMN_STATUS] 状态码）。 */
data class DownloadProgress(
    val status: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
) {
    /** 百分比进度 0-100，总大小未知时为 0。 */
    val percent: Int
        get() = if (totalBytes > 0) {
            ((bytesDownloaded.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
        } else 0
}

/** 下载终态。 */
sealed interface DownloadOutcome {
    data object Completed : DownloadOutcome
    data class Failed(val reason: String) : DownloadOutcome
}

/** 安装结果。 */
enum class InstallOutcome {
    /** 已拉起系统安装器。 */
    Started,

    /** APK 文件不存在或拉起安装器失败。 */
    NoFile,

    /** 缺少"安装未知应用"权限，已引导用户去系统设置开启。 */
    NeedPermission,
}

/**
 * 解析版本号为可比较的数字段列表 + 预发布等级。
 *
 * "v2.0.1-alpha.1" → 数字段 [2, 0, 1] + 预发布等级 alpha=1。
 * 数字段取自 tag_name 中的数字序列；alpha < beta < rc < 正式版(4)。
 */
private fun parseVersion(version: String): Pair<List<Long>, Int> {
    val v = version.trim().trimStart('v', 'V')
    val numericSegments = Regex("\\d+").findAll(v).map { it.value.toLong() }.toList()
    if (numericSegments.isEmpty()) return emptyList<Long>() to 4
    val preReleaseRank = when {
        Regex("(?i)alpha").containsMatchIn(v) -> 1
        Regex("(?i)beta").containsMatchIn(v) -> 2
        Regex("(?i)rc|pre").containsMatchIn(v) -> 3
        else -> 4
    }
    return numericSegments to preReleaseRank
}

/**
 * 版本比较：a > b 返回正数，a < b 返回负数，相等返回 0。
 * 段缺失按 0 计；同号段下 正式版 > rc > beta > alpha。
 */
private fun compareVersions(a: Pair<List<Long>, Int>, b: Pair<List<Long>, Int>): Int {
    val (aSegments, aRank) = a
    val (bSegments, bRank) = b
    val maxLen = max(aSegments.size, bSegments.size)
    for (i in 0 until maxLen) {
        val av = aSegments.getOrElse(i) { 0L }
        val bv = bSegments.getOrElse(i) { 0L }
        if (av != bv) return av.compareTo(bv)
    }
    if (aRank != bRank) return aRank.compareTo(bRank)
    return 0
}

/** 去除 tag 前缀（v2.0.1 → 2.0.1），供 UI 展示。 */
private fun stripVersionPrefix(tag: String): String = tag.trim().trimStart('v', 'V')
