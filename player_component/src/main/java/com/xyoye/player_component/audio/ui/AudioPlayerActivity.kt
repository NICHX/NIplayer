package com.xyoye.player_component.audio.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.view.View
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alibaba.android.arouter.facade.annotation.Route
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.gyf.immersionbar.ImmersionBar
import com.xyoye.common_component.base.BaseActivity
import com.xyoye.common_component.config.RouteTable
import com.xyoye.player_component.BR
import com.xyoye.player_component.R
import com.xyoye.player_component.audio.lrc.LrcManager
import com.xyoye.player_component.audio.manager.AudioPlayManager
import java.io.File
import com.xyoye.player_component.audio.model.AudioPlayMode
import com.xyoye.player_component.audio.model.AudioPlayState
import com.xyoye.player_component.audio.model.AudioSong
import com.xyoye.player_component.databinding.ActivityAudioPlayerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Route(path = RouteTable.Player.AudioPlayer)
class AudioPlayerActivity : BaseActivity<AudioPlayerViewModel, ActivityAudioPlayerBinding>() {

    private var isDraggingProgress = false
    private var isShowCover = true
    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val defaultCoverBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.bg_playing_default_cover)
    }
    private val defaultBgBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.bg_playing_default)
    }

    override fun initViewModel() = ViewModelInit(
        BR.viewModel,
        AudioPlayerViewModel::class.java
    )

    override fun getLayoutId() = R.layout.activity_audio_player

    override fun initStatusBar() {
        ImmersionBar.with(this)
            .fullScreen(true)
            .transparentNavigationBar()
            .statusBarDarkFont(false)
            .navigationBarDarkIcon(false)
            .init()
    }

    override fun initView() {
        adjustNavBarPadding()
        initTitle()
        initCoverAndLrc()
        initVolume()
        initControls()
        observeData()
    }

    private fun adjustNavBarPadding() {
        val navHeight = ImmersionBar.getNavigationBarHeight(this)
        if (navHeight > 0) {
            dataBinding.controlLayout.setPadding(
                dataBinding.controlLayout.paddingLeft,
                dataBinding.controlLayout.paddingTop,
                dataBinding.controlLayout.paddingRight,
                navHeight
            )
        }
    }

    private fun initTitle() {
        dataBinding.ivClose.setOnClickListener {
            finish()
        }
    }

    private fun initCoverAndLrc() {
        setDefaultCover()
        val playState = AudioPlayManager.playState.value
        dataBinding.albumCoverView.initNeedle(playState.isPlaying)
        dataBinding.albumCoverView.setOnClickListener {
            toggleCoverAndLrc()
        }
        dataBinding.lrcView.setOnTapListener { _, _, _ ->
            toggleCoverAndLrc()
            true
        }
        dataBinding.lrcView.setDraggable(true) { _, time ->
            val state = AudioPlayManager.playState.value
            if (state.isPlaying || state.isPausing) {
                AudioPlayManager.seekTo(time.toInt())
                if (state.isPausing) {
                    AudioPlayManager.playPause()
                }
                return@setDraggable true
            }
            return@setDraggable false
        }
    }

    private fun initVolume() {
        dataBinding.sbVolume.max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        dataBinding.sbVolume.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        dataBinding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar ?: return
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    seekBar.progress,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                )
            }
        })
    }

    private fun initControls() {
        dataBinding.ivMode.setOnClickListener {
            val mode = when (AudioPlayManager.playMode.value) {
                AudioPlayMode.Loop -> AudioPlayMode.Shuffle
                AudioPlayMode.Shuffle -> AudioPlayMode.Single
                AudioPlayMode.Single -> AudioPlayMode.Loop
            }
            AudioPlayManager.setPlayMode(mode)
        }

        dataBinding.flPlay.setOnClickListener {
            AudioPlayManager.playPause()
        }

        dataBinding.ivPrev.setOnClickListener {
            AudioPlayManager.prev()
        }

        dataBinding.ivNext.setOnClickListener {
            AudioPlayManager.next()
        }

        dataBinding.ivPlaylist.setOnClickListener {
            showPlaylistDialog()
        }

        dataBinding.ivLike.setOnClickListener {
        }

        dataBinding.sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    dataBinding.tvCurrentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isDraggingProgress = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isDraggingProgress = false
                val state = AudioPlayManager.playState.value
                if (state.isPlaying || state.isPausing) {
                    AudioPlayManager.seekTo(seekBar?.progress ?: 0)
                } else {
                    seekBar?.progress = 0
                }
            }
        })
    }

    private fun showPlaylistDialog() {
        val songs = AudioPlayManager.playlist.value
        if (songs.isEmpty()) return
        val currentSong = AudioPlayManager.currentSong.value
        val items = songs.mapIndexed { index, song ->
            val isPlaying = song.uniqueKey == currentSong?.uniqueKey
            val prefix = if (isPlaying) "▶ " else "  "
            "$prefix${song.title}"
        }.toTypedArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("播放列表")
            .setItems(items) { _, which ->
                val song = songs.getOrNull(which) ?: return@setItems
                AudioPlayManager.play(song)
            }
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AudioPlayManager.currentSong.collectLatest { song ->
                    if (song != null) {
                        dataBinding.tvTitle.text = song.title
                        dataBinding.tvArtist.text = song.artist.ifEmpty { "未知艺术家" }
                        dataBinding.sbProgress.secondaryProgress = 0

                        dataBinding.albumCoverView.reset()
                        loadCover(song)
                        loadLrc(song)
                        updatePlayState(AudioPlayManager.playState.value)
                    }
                }
            }
        }

        lifecycleScope.launch {
            AudioPlayManager.songDuration.collectLatest { duration ->
                if (duration > 0) {
                    dataBinding.sbProgress.max = duration.toInt()
                    dataBinding.tvTotalTime.text = formatTime(duration)
                }
            }
        }

        lifecycleScope.launch {
            AudioPlayManager.playState.collectLatest { state ->
                updatePlayState(state)
            }
        }

        lifecycleScope.launch {
            AudioPlayManager.playProgress.collectLatest { progress ->
                if (!isDraggingProgress) {
                    dataBinding.sbProgress.progress = progress.toInt()
                }
                dataBinding.tvCurrentTime.text = formatTime(progress)
                if (dataBinding.lrcView.hasLrc()) {
                    dataBinding.lrcView.updateTime(progress)
                }
            }
        }

        lifecycleScope.launch {
            AudioPlayManager.playMode.collectLatest { mode ->
                dataBinding.ivMode.setImageLevel(mode.value)
            }
        }
    }

    private fun loadLrc(song: AudioSong) {
        if (song.lrcFilePath != null) {
            val lrcRef = song.lrcFilePath
            if (lrcRef.startsWith("http://") || lrcRef.startsWith("https://")) {
                downloadLrc(lrcRef)
                return
            }
            if (File(lrcRef).exists()) {
                dataBinding.lrcView.loadLrc(File(lrcRef))
                return
            }
        }

        val lrcPath = LrcManager.getLrcFilePath(song)
        if (lrcPath != null) {
            dataBinding.lrcView.loadLrc(File(lrcPath))
        } else {
            dataBinding.lrcView.loadLrc("")
            dataBinding.lrcView.setLabel("暂无歌词")
        }
    }

    private fun downloadLrc(url: String) {
        dataBinding.lrcView.loadLrc("")
        dataBinding.lrcView.setLabel("歌词加载中…")
        lifecycleScope.launch {
            val content = withContext(Dispatchers.IO) {
                try {
                    withTimeout(5000) {
                        java.net.URL(url).readText()
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (content != null && content.isNotEmpty()) {
                val lrcDir = File(cacheDir, "lrc_cache")
                if (!lrcDir.exists()) lrcDir.mkdirs()
                val tempFile = File(lrcDir, "lrc_${System.currentTimeMillis()}.lrc")
                tempFile.writeText(content)
                dataBinding.lrcView.loadLrc(tempFile)
            } else {
                dataBinding.lrcView.loadLrc("")
                dataBinding.lrcView.setLabel("暂无歌词")
            }
        }
    }

    private fun loadCover(song: AudioSong) {
        setDefaultCover()
        val coverPath = song.coverPath

        if (coverPath != null) {
            Glide.with(this)
                .asBitmap()
                .load(coverPath)
                .addListener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                        return false
                    }
                    override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                        resource?.let {
                            dataBinding.albumCoverView.setCoverBitmap(it)
                            setBlurBackground(it)
                        }
                        return true
                    }
                })
                .submit()
        }
    }

    private fun setDefaultCover() {
        dataBinding.albumCoverView.setCoverBitmap(defaultCoverBitmap)
        if (defaultBgBitmap != null) {
            dataBinding.ivPlayingBg.setImageBitmap(defaultBgBitmap)
        } else {
            dataBinding.ivPlayingBg.setImageDrawable(ColorDrawable(android.graphics.Color.BLACK))
        }
    }

    private fun setBlurBackground(bitmap: Bitmap) {
        lifecycleScope.launch {
            val blurred = withContext(Dispatchers.IO) {
                val scale = 8
                val smallW = (bitmap.width / scale).coerceAtLeast(1)
                val smallH = (bitmap.height / scale).coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)
                Bitmap.createScaledBitmap(small, bitmap.width, bitmap.height, true)
            }
            dataBinding.ivPlayingBg.setImageBitmap(blurred)
        }
    }

    private fun updatePlayState(state: AudioPlayState) {
        dataBinding.ivPlay.isSelected = state.isPlaying
        dataBinding.playLoading.isVisible = state is AudioPlayState.Preparing
        dataBinding.sbProgress.isEnabled = state.isPlaying || state.isPausing

        if (state.isPlaying) {
            dataBinding.albumCoverView.start()
        } else {
            dataBinding.albumCoverView.pause()
        }
    }

    private fun toggleCoverAndLrc() {
        isShowCover = !isShowCover
        dataBinding.albumCoverView.visibility = if (isShowCover) View.VISIBLE else View.GONE
        dataBinding.volumeLayout.visibility = if (isShowCover) View.GONE else View.VISIBLE
        dataBinding.lrcView.visibility = if (isShowCover) View.GONE else View.VISIBLE
        dataBinding.ivLrcMaskTop.visibility = if (isShowCover) View.GONE else View.VISIBLE
        dataBinding.ivLrcMaskBottom.visibility = if (isShowCover) View.GONE else View.VISIBLE
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
