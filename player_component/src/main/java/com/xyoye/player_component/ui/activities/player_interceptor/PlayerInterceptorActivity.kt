package com.xyoye.player_component.ui.activities.player_interceptor

import androidx.lifecycle.lifecycleScope
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.gyf.immersionbar.BarHide
import com.gyf.immersionbar.ImmersionBar
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.common_component.source.VideoSourceManager
import com.xyoye.common_component.source.media.StorageVideoSource
import com.xyoye.common_component.utils.AudioMetadataCache
import com.xyoye.common_component.utils.isAudioFile
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.player_component.BR
import com.xyoye.player_component.R
import com.xyoye.player_component.audio.manager.AudioPlayManager
import com.xyoye.player_component.audio.model.AudioSong
import com.xyoye.player_component.databinding.ActivityPlayerInterceptorBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Route(path = RouteTable.Player.Player)
class PlayerInterceptorActivity :
    BaseActivity<PlayerInterceptorViewModel, ActivityPlayerInterceptorBinding>() {

    override fun initViewModel() =
        ViewModelInit(
            BR.viewModel,
            PlayerInterceptorViewModel::class.java
        )

    override fun getLayoutId() = R.layout.activity_player_interceptor

    override fun initStatusBar() {
        ImmersionBar.with(this)
            .fullScreen(true)
            .hideBar(BarHide.FLAG_HIDE_STATUS_BAR)
            .init()
    }

    override fun initView() {
        val source = VideoSourceManager.getInstance().getSource() ?: run {
            ToastCenter.showError("播放参数错误，无法播放视频")
            finish()
            return
        }

        val url = source.getVideoUrl()
        val title = source.getVideoTitle()
        if (isAudioFile(url) || isAudioFile(title)) {
            AudioPlayManager.init(this)

            val groupSize = source.getGroupSize()
            if (groupSize > 1 && source is StorageVideoSource) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val currentIndex = source.getGroupIndex()
                    var currentSong: AudioSong? = null

                    try {
                        val currentFile = source.indexStorageFile(currentIndex)
                        val playUrl = currentFile.storage.createPlayUrl(currentFile)
                        if (playUrl != null) {
                            val lrcUrl = currentFile.storage.cacheLrc(currentFile)
                            val cachedMetadata = AudioMetadataCache.get(currentFile.uniqueKey())
                            currentSong = AudioSong(
                                uri = playUrl,
                                title = cachedMetadata?.title?.takeIf { it.isNotEmpty() } ?: currentFile.fileName() ?: "",
                                artist = cachedMetadata?.artist ?: "",
                                coverPath = currentFile.fileCover(),
                                duration = cachedMetadata?.duration ?: 0L,
                                uniqueKey = currentFile.uniqueKey(),
                                fileName = currentFile.fileName() ?: "",
                                lrcFilePath = lrcUrl
                            )
                        }
                    } catch (_: Exception) { }

                    withContext(Dispatchers.Main) {
                        if (currentSong != null) {
                            AudioPlayManager.setPlaylist(listOf(currentSong!!), 0)
                            ARouter.getInstance()
                                .build(RouteTable.Player.AudioPlayer)
                                .navigation()
                            overridePendingTransition(R.anim.slide_in_bottom, 0)
                        }
                        finish()
                    }
                }

                val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                bgScope.launch {
                    val songs = mutableListOf<AudioSong>()
                    for (i in 0 until groupSize) {
                        try {
                            val storageFile = source.indexStorageFile(i)
                            val playUrl = storageFile.storage.createPlayUrl(storageFile)
                            if (playUrl != null) {
                                val lrcUrl = storageFile.storage.cacheLrc(storageFile)
                                val cachedMetadata = AudioMetadataCache.get(storageFile.uniqueKey())
                                songs.add(
                                    AudioSong(
                                        uri = playUrl,
                                        title = cachedMetadata?.title?.takeIf { it.isNotEmpty() } ?: storageFile.fileName() ?: "",
                                        artist = cachedMetadata?.artist ?: "",
                                        coverPath = storageFile.fileCover(),
                                        duration = cachedMetadata?.duration ?: 0L,
                                        uniqueKey = storageFile.uniqueKey(),
                                        fileName = storageFile.fileName() ?: "",
                                        lrcFilePath = lrcUrl
                                    )
                                )
                            }
                        } catch (_: Exception) { }
                    }
                    withContext(Dispatchers.Main) {
                        AudioPlayManager.updatePlaylist(songs)
                    }
                }
            } else {
                AudioPlayManager.setPlaylist(listOf(
                    AudioSong(
                        uri = url,
                        title = title ?: "",
                        uniqueKey = source.getUniqueKey(),
                        fileName = title ?: ""
                    )
                ), 0)
                ARouter.getInstance()
                    .build(RouteTable.Player.AudioPlayer)
                    .navigation()
                finish()
            }
            return
        }

        ARouter.getInstance()
            .build(RouteTable.Player.PlayerCenter)
            .navigation()
        finish()
    }
}
