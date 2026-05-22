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
                val currentIndex = source.getGroupIndex()
                AudioPlayManager.setPendingSource(source)

                lifecycleScope.launch(Dispatchers.IO) {
                    val songs = mutableListOf<AudioSong>()
                    var currentSong: AudioSong? = null

                    for (i in 0 until groupSize) {
                        try {
                            val storageFile = source.indexStorageFile(i)
                            val isCurrent = i == currentIndex
                            val playUrl = if (isCurrent) {
                                val url = source.getVideoUrl()
                                currentSong = AudioSong(
                                    uri = url,
                                    title = storageFile.fileName() ?: "",
                                    uniqueKey = storageFile.uniqueKey(),
                                    fileName = storageFile.fileName() ?: "",
                                    lrcUrl = try {
                                        storageFile.storage.cacheLrc(storageFile)
                                    } catch (_: Exception) { null }
                                )
                                url
                            } else {
                                ""
                            }
                            songs.add(
                                AudioSong(
                                    uri = playUrl,
                                    title = storageFile.fileName() ?: "",
                                    uniqueKey = storageFile.uniqueKey(),
                                    fileName = storageFile.fileName() ?: ""
                                )
                            )
                        } catch (_: Exception) { }
                    }

                    withContext(Dispatchers.Main) {
                        if (currentSong != null) {
                            AudioPlayManager.setPlaylist(listOf(currentSong!!), 0)
                            AudioPlayManager.updatePlaylist(songs, currentIndex)
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
                    for (i in 0 until groupSize) {
                        if (i == currentIndex) continue
                        try {
                            val storageFile = source.indexStorageFile(i)
                            val playUrl = storageFile.storage.createPlayUrl(storageFile)
                            if (playUrl != null) {
                                AudioPlayManager.updateSongUri(i, playUrl)
                            }
                        } catch (_: Exception) { }
                    }
                }
            } else {
                if (source is StorageVideoSource) {
                    AudioPlayManager.setPendingSource(source)
                }
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
