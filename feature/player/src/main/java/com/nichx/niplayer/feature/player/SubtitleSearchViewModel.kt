package com.nichx.niplayer.feature.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nichx.niplayer.datastore.SubtitleSettings
import com.nichx.niplayer.network.subtitle.AssrtApi
import com.nichx.niplayer.network.subtitle.AssrtSubDetail
import com.nichx.niplayer.network.subtitle.AssrtSubFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

/**
 * 字幕搜索 ViewModel。
 *
 * 替代旧仓库 `BindSubtitleSourceFragmentViewModel` + `ShooterSubtitleViewModel`，
 * v2 整合为单一 ViewModel，仅支持 ASSRT 关键词搜索（射手网 hash 匹配已失效，不迁移）。
 *
 * 流程：
 * 1. [search]：调 ASSRT `v1/sub/search` 关键词搜索
 * 2. [loadDetail]：调 ASSRT `v1/sub/detail` 获取下载链接
 * 3. [downloadSubtitle]：下载字幕文件（直接下载单文件或压缩包内单文件）
 * 4. 下载后存入 `cacheDir/subtitle/`，通过 [onSubtitleDownloaded] 回调通知 UI 层
 *    调用 [com.nichx.niplayer.feature.player.PlayerViewModel.addSubtitle] 应用到播放器
 *    （外挂字幕统一由 SubtitleEngine 渲染）
 *
 * token 管理：从 [SubtitleSettings.assrtToken] 读取，空时 UI 层提示用户设置。
 */
@HiltViewModel
class SubtitleSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assrtApi: AssrtApi,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubtitleSearchUiState())
    val uiState: StateFlow<SubtitleSearchUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<SubtitleSearchEvent?>(null)
    val events: StateFlow<SubtitleSearchEvent?> = _events.asStateFlow()

    /** 下载完成回调，UI 层设置后用于通知 PlayerViewModel 应用字幕。 */
    var onSubtitleDownloaded: ((Uri, String) -> Unit)? = null

    /** 搜索字幕。 */
    fun search(query: String) {
        if (query.isBlank()) return

        val token = SubtitleSettings.assrtToken
        if (token.isBlank()) {
            _events.value = SubtitleSearchEvent.NeedToken
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            try {
                val response = withContext(Dispatchers.IO) {
                    assrtApi.search(token, query)
                }
                val results = response.sub?.subs.orEmpty()
                _uiState.update {
                    it.copy(
                        results = results,
                        isSearching = false,
                    )
                }
                if (results.isEmpty()) {
                    _events.value = SubtitleSearchEvent.NoResults
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        error = e.message ?: "搜索失败",
                    )
                }
            }
        }
    }

    /** 加载字幕详情（获取下载链接）。 */
    fun loadDetail(sub: AssrtSubDetail) {
        val token = SubtitleSettings.assrtToken
        if (token.isBlank()) {
            _events.value = SubtitleSearchEvent.NeedToken
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDetail = true, error = null) }
            try {
                val response = withContext(Dispatchers.IO) {
                    assrtApi.detail(token, sub.id)
                }
                val detail = response.sub?.subs?.firstOrNull()
                val downloadUrl = detail?.url
                if (downloadUrl != null) {
                    _uiState.update { it.copy(isLoadingDetail = false) }
                    // 有压缩包下载链接，直接下载压缩包内第一个字幕文件
                    val firstFile = detail.filelist?.firstOrNull()
                    val title = sub.native_name ?: sub.videoname ?: "subtitle"
                    if (firstFile?.url != null) {
                        downloadSubtitle(firstFile, title)
                    } else {
                        // 无 filelist，直接下载压缩包
                        downloadFromUrl(downloadUrl, title, detail.subtype ?: "srt")
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            error = "未找到下载链接",
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingDetail = false,
                        error = e.message ?: "加载详情失败",
                    )
                }
            }
        }
    }

    /** 下载压缩包内单个字幕文件。 */
    private fun downloadSubtitle(file: AssrtSubFile, title: String) {
        val url = file.url ?: return
        val ext = file.f?.substringAfterLast('.', "srt") ?: "srt"
        downloadFromUrl(url, title, ext)
    }

    /** 下载字幕文件到 cacheDir/subtitle/，下载完成后回调。 */
    private fun downloadFromUrl(url: String, title: String, ext: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, error = null) }
            try {
                val fileUri = withContext(Dispatchers.IO) {
                    downloadFile(url, title, ext)
                }
                _uiState.update { it.copy(isDownloading = false) }
                _events.value = SubtitleSearchEvent.DownloadSuccess(fileUri)
                onSubtitleDownloaded?.invoke(fileUri, mimeTypeForExt(ext))
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloading = false,
                        error = e.message ?: "下载失败",
                    )
                }
            }
        }
    }

    /** 保存 ASSRT token。 */
    fun saveToken(token: String) {
        SubtitleSettings.assrtToken = token
        _uiState.update { it.copy(showTokenDialog = false) }
    }

    /** 显示 token 设置弹窗。 */
    fun showTokenDialog() {
        _uiState.update { it.copy(showTokenDialog = true) }
    }

    /** 隐藏 token 设置弹窗。 */
    fun dismissTokenDialog() {
        _uiState.update { it.copy(showTokenDialog = false) }
    }

    /** 消费一次性事件。 */
    fun consumeEvent() {
        _events.value = null
    }

    private fun downloadFile(url: String, title: String, ext: String): Uri {
        val subtitleDir = File(context.cacheDir, "subtitle").apply { mkdirs() }
        val safeFileName = title.replace(Regex("[^\\w.-]"), "_").take(60)
        val file = File(subtitleDir, "${safeFileName}.$ext")

        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("下载失败：HTTP ${response.code}")
            }
            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: throw RuntimeException("下载失败：响应体为空")
        }

        return Uri.fromFile(file)
    }

    /** 按字幕文件扩展名推断 media3 MIME 类型。 */
    private fun mimeTypeForExt(ext: String): String = when (ext.lowercase()) {
        "srt" -> "application/x-subrip"
        "vtt" -> "text/vtt"
        "ass", "ssa" -> "text/x-ssa"
        else -> "application/x-subrip"
    }
}

/** 字幕搜索 UI 状态。 */
data class SubtitleSearchUiState(
    val results: List<AssrtSubDetail> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingDetail: Boolean = false,
    val isDownloading: Boolean = false,
    val error: String? = null,
    val showTokenDialog: Boolean = false,
)

/** 一次性事件。 */
sealed class SubtitleSearchEvent {
    /** 需要设置 ASSRT token。 */
    object NeedToken : SubtitleSearchEvent()

    /** 搜索无结果。 */
    object NoResults : SubtitleSearchEvent()

    /** 下载成功。 */
    data class DownloadSuccess(val uri: Uri) : SubtitleSearchEvent()
}
