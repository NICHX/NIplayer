package com.nichx.niplayer.feature.home.update

import android.app.DownloadManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.datastore.UpdateSettings
import com.nichx.niplayer.network.update.GitHubAsset
import com.nichx.niplayer.network.update.GitHubRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用更新 ViewModel。
 *
 * 状态机：[Idle] → [Checking] → [UpdateAvailable] / [AlreadyLatest] / [CheckFailed]
 * → [Downloading] → [DownloadReady] / [DownloadFailed] → [Idle]（安装/取消）。
 *
 * 单例 [UpdateManager] 负责网络与系统下载；本类仅维护 UI 状态与用户交互。
 * init 时恢复"待安装更新包"提示（进程被杀后重启，下载已完成则直接提示安装）。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager,
) : ViewModel() {

    /** 更新流程 UI 状态。 */
    sealed interface UpdateUiState {
        data object Idle : UpdateUiState
        data object Checking : UpdateUiState

        /** 检测到新版本。 */
        data class UpdateAvailable(
            val latestVersion: String,
            val notes: String,
            val sizeText: String,
            val asset: GitHubAsset?,
            val release: GitHubRelease,
        ) : UpdateUiState

        /** 当前已是最新版本。 */
        data class AlreadyLatest(val currentVersion: String) : UpdateUiState

        /** 检查失败。 */
        data class CheckFailed(val message: String) : UpdateUiState

        /** 正在下载更新包。 */
        data class Downloading(val progress: Int) : UpdateUiState

        /** 下载已收起到后台（系统通知栏可见进度），完成后自动弹出安装提示。 */
        data object DownloadingInBackground : UpdateUiState

        /** 下载完成，等待用户确认安装。 */
        data class DownloadReady(val version: String) : UpdateUiState

        /** 下载失败。 */
        data class DownloadFailed(val message: String) : UpdateUiState

        /** 缺少"安装未知应用"权限，已引导用户去系统设置开启。 */
        data class InstallBlocked(val message: String) : UpdateUiState
    }

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** 当前应用版本号（供弹窗展示"当前版本 → 新版本"对比）。 */
    val currentVersion: String
        get() = updateManager.currentVersionName()

    private var pendingRelease: GitHubRelease? = null
    private var pendingAsset: GitHubAsset? = null
    private var pendingVersion: String? = null
    private var progressJob: Job? = null

    init {
        // 恢复待安装提示：上次下载完成但未安装（或进程被杀后重启）
        if (updateManager.hasDownloadedApk()) {
            _uiState.value = UpdateUiState.DownloadReady(UpdateSettings.getDownloadedVersion())
        } else if (updateManager.isDownloading()) {
            // ViewModel 重建（如离开设置页后返回）：恢复下载进度弹窗与轮询
            _uiState.value = UpdateUiState.Downloading(0)
            observeDownload()
        }
    }

    /**
     * 检查更新。
     *
     * @param auto 自动检查（启动时）：节流命中或失败时静默、不显示加载态，不打扰用户。
     */
    fun checkUpdate(auto: Boolean) {
        // 已有待安装更新包、正在下载或后台下载中时，忽略重复触发
        if (_uiState.value is UpdateUiState.DownloadReady ||
            _uiState.value is UpdateUiState.Downloading ||
            _uiState.value is UpdateUiState.DownloadingInBackground
        ) {
            return
        }
        // 已下载但尚未安装的更新包存在时（如关闭权限弹窗后再点检查），
        // 直接恢复安装提示，避免重复触网和重复下载
        if (updateManager.hasDownloadedApk()) {
            _uiState.value = UpdateUiState.DownloadReady(UpdateSettings.getDownloadedVersion())
            return
        }
        if (!auto) {
            _uiState.value = UpdateUiState.Checking
        }
        viewModelScope.launch {
            when (val result = updateManager.checkUpdate(auto)) {
                UpdateCheckOutcome.Skipped -> _uiState.value = UpdateUiState.Idle
                UpdateCheckOutcome.NoUpdate -> _uiState.value = if (auto) {
                    UpdateUiState.Idle
                } else {
                    UpdateUiState.AlreadyLatest(updateManager.currentVersionName())
                }
                is UpdateCheckOutcome.UpdateAvailable -> {
                    pendingRelease = result.release
                    pendingAsset = result.asset
                    pendingVersion = result.latestVersion
                    _uiState.value = UpdateUiState.UpdateAvailable(
                        latestVersion = result.latestVersion,
                        notes = result.release.body.orEmpty().trim(),
                        sizeText = result.asset?.let { formatSize(it.size) }.orEmpty(),
                        asset = result.asset,
                        release = result.release,
                    )
                }
                is UpdateCheckOutcome.Error -> _uiState.value = if (auto) {
                    UpdateUiState.Idle
                } else {
                    UpdateUiState.CheckFailed(result.message)
                }
            }
        }
    }

    /** 关闭当前对话框并回到空闲态。 */
    fun dismiss() {
        progressJob?.cancel()
        progressJob = null
        _uiState.value = UpdateUiState.Idle
    }

    /** 开始下载更新包。 */
    fun startDownload() {
        val release = pendingRelease ?: run { dismiss(); return }
        val asset = pendingAsset
        if (asset == null) {
            // Release 无 APK 附件：引导浏览器访问 Releases 页面
            updateManager.openReleasesPage()
            dismiss()
            return
        }
        val id = updateManager.startDownload(release, asset, pendingVersion ?: latestVersion())
        if (id < 0) {
            _uiState.value = UpdateUiState.DownloadFailed("无法创建下载任务，请重试或前往浏览器下载")
            return
        }
        _uiState.value = UpdateUiState.Downloading(0)
        observeDownload()
    }

    /**
     * 收起下载进度弹窗，下载转入后台继续（系统通知栏可见进度）。
     * 轮询不中断，下载完成/失败时自动弹出结果提示。
     */
    fun downloadInBackground() {
        if (_uiState.value is UpdateUiState.Downloading) {
            _uiState.value = UpdateUiState.DownloadingInBackground
        }
    }

    /** 取消下载并回到空闲态。 */
    fun cancelDownload() {
        updateManager.cancelDownload()
        dismiss()
    }

    /** 确认安装已下载的更新包。 */
    fun install() {
        when (updateManager.install()) {
            InstallOutcome.Started -> _uiState.value = UpdateUiState.Idle
            InstallOutcome.NeedPermission -> _uiState.value = UpdateUiState.InstallBlocked(
                "请在系统设置中允许 NIplayer 安装未知应用，然后点击重新安装"
            )
            InstallOutcome.NoFile -> _uiState.value = UpdateUiState.DownloadFailed(
                "安装包不存在，请重新下载"
            )
        }
    }

    /** 打开浏览器访问 GitHub Releases 页面并关闭对话框。 */
    fun openReleasesPage() {
        updateManager.openReleasesPage()
        dismiss()
    }

    private fun latestVersion(): String = UpdateSettings.getDownloadedVersion()

    /**
     * 轮询下载进度直到终态。
     *
     * 由于 [UpdateManager] 单例共享，下载可能在 ViewModel 之外持续进行
     * （如检查页退出后再进入），轮询查询始终以当前下载 id 为准。
     */
    private fun observeDownload() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val progress = updateManager.queryDownloadProgress()
                if (progress == null) {
                    // 查询失败：任务仍在（如瞬时异常）则继续轮询，任务已结束则退出
                    if (updateManager.isDownloading()) {
                        delay(500)
                        continue
                    }
                    break
                }
                when (progress.status) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING -> {
                        // 弹窗可见时更新进度；后台下载状态保持隐藏，不重新弹出
                        if (_uiState.value is UpdateUiState.Downloading) {
                            _uiState.value = UpdateUiState.Downloading(progress.percent)
                        }
                    }

                    DownloadManager.STATUS_SUCCESSFUL -> {
                        _uiState.value = UpdateUiState.DownloadReady(
                            UpdateSettings.getDownloadedVersion()
                        )
                        break
                    }

                    DownloadManager.STATUS_FAILED -> {
                        _uiState.value = UpdateUiState.DownloadFailed(
                            "下载失败，请重试或前往浏览器下载"
                        )
                        break
                    }
                }
                delay(500)
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes >= 1000 * 1000 * 1000 -> "${"%.2f".format(bytes / (1000.0 * 1000 * 1000))} GB"
        bytes >= 1000 * 1000 -> "${"%.1f".format(bytes / (1000.0 * 1000))} MB"
        else -> "${bytes / 1000} KB"
    }
}
