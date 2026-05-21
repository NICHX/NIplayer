package com.xyoye.player_component.audio.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.TransitionDrawable
import android.media.AudioManager
import android.widget.ImageView
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.core.view.doOnLayout
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
import com.xyoye.common_component.extension.toCoverFile
import com.xyoye.common_component.utils.ThumbnailMemoryCache
import com.xyoye.player_component.R
import com.xyoye.player_component.audio.lrc.LrcManager
import com.xyoye.player_component.audio.manager.AudioPlayManager
import jp.wasabeef.blurry.Blurry
import java.io.File
import java.io.ByteArrayOutputStream
import com.xyoye.player_component.audio.model.AudioPlayMode
import com.xyoye.player_component.audio.model.AudioPlayState
import com.xyoye.player_component.audio.model.AudioSong
import com.xyoye.player_component.audio.ui.AudioPlaylistDialog
import com.xyoye.player_component.databinding.ActivityAudioPlayerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
private const val LRC_RETRY_COUNT = 3
private const val LRC_RETRY_DELAY_MS = 1000L

@Route(path = RouteTable.Player.AudioPlayer)
class AudioPlayerActivity : BaseActivity<AudioPlayerViewModel, ActivityAudioPlayerBinding>() {

    private var isDraggingProgress = false
    private var isShowCover = true
    private var lrcLoadJob: kotlinx.coroutines.Job? = null
    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val defaultCoverBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.bg_playing_default_cover)
    }
    private val defaultBgBitmap by lazy {
        BitmapFactory.decodeResource(resources, R.drawable.bg_playing_default)
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == VOLUME_CHANGED_ACTION) {
                dataBinding.sbVolume.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
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
        AudioPlayManager.init(this)
        LrcManager.setApplicationContext(this)
        adjustNavBarPadding()
        initTitle()
        initCoverAndLrc()
        initVolume()
        initControls()
        observeData()
        switchCoverLrc(true)
    }

    override fun onDestroy() {
        lrcLoadJob?.cancel()
        try { unregisterReceiver(volumeReceiver) } catch (_: Exception) {}
        super.onDestroy()
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
            switchCoverLrc(false)
        }
        dataBinding.lrcView.setOnTapListener { _, _, _ ->
            switchCoverLrc(true)
        }
        dataBinding.lrcView.setDraggable(true) { _, time ->
            val state = AudioPlayManager.playState.value
            if (state.isPlaying || state.isPausing) {
                AudioPlayManager.seekTo(time.toLong())
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
        val filter = IntentFilter(VOLUME_CHANGED_ACTION)
        registerReceiver(volumeReceiver, filter)
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
                    val progress = seekBar?.progress?.toLong() ?: 0L
                    AudioPlayManager.seekTo(progress)
                    if (dataBinding.lrcView.hasLrc()) {
                        dataBinding.lrcView.updateTime(progress.toLong())
                    }
                } else {
                    seekBar?.progress = 0
                }
            }
        })
    }

    private val playlistDialog by lazy { AudioPlaylistDialog(this, this) }

    private fun showPlaylistDialog() {
        playlistDialog.show()
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AudioPlayManager.currentSong.collect { song ->
                    if (song != null) {
                        val playState = AudioPlayManager.playState.value
                        if (playState !is AudioPlayState.Preparing && playState !is AudioPlayState.Playing && playState !is AudioPlayState.Pause) {
                            return@collect
                        }

                        dataBinding.tvTitle.text = song.title
                        dataBinding.tvArtist.text = song.artist.ifEmpty { "未知艺术家" }
                        dataBinding.sbProgress.secondaryProgress = 0

                        val direction = AudioPlayManager.lastNavigationDirection
                        AudioPlayManager.lastNavigationDirection = 0

                        val enrichedSong = if (song.title.isEmpty() || song.artist.isEmpty() || song.duration == 0L || song.coverPath == null) {
                            AudioPlayManager.playWithMetadata(song, forceReload = false)
                        } else {
                            song
                        }

                        if (enrichedSong.title != song.title || enrichedSong.artist != song.artist ||
                            enrichedSong.duration != song.duration || enrichedSong.coverPath != song.coverPath) {
                            AudioPlayManager.updateCurrentSong(enrichedSong)
                        }

                        val initialCover = when {
                            song.coverBytes != null -> BitmapFactory.decodeByteArray(song.coverBytes, 0, song.coverBytes.size)
                            song.coverPath != null -> {
                                val coverFile = File(song.coverPath)
                                if (coverFile.exists()) BitmapFactory.decodeFile(song.coverPath) else defaultCoverBitmap
                            }
                            else -> defaultCoverBitmap
                        }

                        if (direction != 0) {
                            dataBinding.albumCoverView.switchSong(initialCover, direction) {
                                loadLrc(enrichedSong)
                                updatePlayState(AudioPlayManager.playState.value)
                            }
                            loadCover(enrichedSong)
                        } else {
                            dataBinding.albumCoverView.reset()
                            loadCover(enrichedSong)
                            loadLrc(enrichedSong)
                            updatePlayState(AudioPlayManager.playState.value)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AudioPlayManager.songDuration.collectLatest { duration ->
                    if (duration > 0) {
                        dataBinding.sbProgress.max = duration.toInt()
                        dataBinding.tvTotalTime.text = formatTime(duration)
                    }
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
        lrcLoadJob?.cancel()
        lrcLoadJob = lifecycleScope.launch {
            loadLrcInternal(song)
        }
    }

    private suspend fun loadLrcInternal(song: AudioSong) {
        // 优先级1: song.lrcFilePath (预设路径，可能是本地文件或已缓存的网络URL)
        if (song.lrcFilePath != null) {
            val lrcRef = song.lrcFilePath
            if (lrcRef.startsWith("http://") || lrcRef.startsWith("https://")) {
                val loaded = downloadLrcWithRetry(lrcRef)
                if (loaded) return
            } else if (File(lrcRef).exists()) {
                dataBinding.lrcView.loadLrc(File(lrcRef))
                return
            }
        }

        // 优先级2: song.lrcUrl (网络存储的歌词URL)
        if (song.lrcUrl != null) {
            val loaded = downloadLrcWithRetry(song.lrcUrl)
            if (loaded) return
        }
        
        // 优先级3: 本地歌词查找
        val localLrcPath = LrcManager.findLocalLrcFile(song)
        if (localLrcPath != null) {
            dataBinding.lrcView.loadLrc(File(localLrcPath))
            updateSongLrcPath(localLrcPath)
            return
        }

        // 优先级4: 缓存歌词
        val cachedLrcPath = LrcManager.findCachedLrcFile(song)
        if (cachedLrcPath != null) {
            dataBinding.lrcView.loadLrc(File(cachedLrcPath))
            return
        }

        // 无歌词
        dataBinding.lrcView.loadLrc("")
        dataBinding.lrcView.setLabel("暂无歌词")
    }

    private suspend fun downloadLrcWithRetry(url: String): Boolean {
        dataBinding.lrcView.loadLrc("")
        dataBinding.lrcView.setLabel("歌词加载中…")

        repeat(LRC_RETRY_COUNT) { attempt ->
            try {
                val content = withContext(Dispatchers.IO) {
                    withTimeout(8000) {
                        java.net.URL(url).readText()
                    }
                }
                if (content.isNotEmpty()) {
                    val lrcFile = withContext(Dispatchers.IO) {
                        saveLrcCache(content)
                    }
                    dataBinding.lrcView.loadLrc(lrcFile)
                    updateSongLrcPath(lrcFile.absolutePath)
                    return true
                }
            } catch (e: Exception) {
                if (attempt < LRC_RETRY_COUNT - 1) {
                    delay(LRC_RETRY_DELAY_MS)
                }
            }
        }

        dataBinding.lrcView.loadLrc("")
        dataBinding.lrcView.setLabel("暂无歌词")
        return false
    }

    private fun saveLrcCache(content: String): File {
        val song = AudioPlayManager.currentSong.value
        val lrcDir = File(cacheDir, "lrc_cache")
        if (!lrcDir.exists()) lrcDir.mkdirs()
        val fileName = if (song != null) {
            "${song.uniqueKey}.lrc"
        } else {
            "lrc_${System.currentTimeMillis()}.lrc"
        }
        val lrcFile = File(lrcDir, fileName)
        lrcFile.writeText(content)
        return lrcFile
    }

    private fun updateSongLrcPath(lrcPath: String) {
        val current = AudioPlayManager.currentSong.value ?: return
        if (current.lrcFilePath != lrcPath) {
            val updated = current.copy(lrcFilePath = lrcPath)
            AudioPlayManager.updateCurrentSong(updated)
        }
    }

    private fun loadCover(song: AudioSong) {
        dataBinding.ivLrcMaskTop.setImageDrawable(null)
        dataBinding.ivLrcMaskBottom.setImageDrawable(null)

        if (song.coverBytes != null) {
            val coverBitmap = BitmapFactory.decodeByteArray(song.coverBytes, 0, song.coverBytes.size)
            if (coverBitmap != null) {
                if (dataBinding.albumCoverView.isSwitchInProgress()) {
                    dataBinding.albumCoverView.setPendingCover(coverBitmap)
                } else {
                    dataBinding.albumCoverView.setCoverBitmap(coverBitmap)
                }
                setBlurBackground(coverBitmap)
                return
            }
        }

        var coverPath = song.coverPath

        if (coverPath == null) {
            coverPath = ThumbnailMemoryCache.getCoverPath(song.uniqueKey)
        }

        if (coverPath == null) {
            val cachedCoverFile = song.uniqueKey.toCoverFile()
            if (cachedCoverFile != null && cachedCoverFile.exists() && cachedCoverFile.length() > 0) {
                coverPath = cachedCoverFile.absolutePath
                ThumbnailMemoryCache.putCoverPath(song.uniqueKey, coverPath)
            }
        }

        if (coverPath != null) {
            Glide.with(this)
                .asBitmap()
                .load(coverPath)
                .addListener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>?, isFirstResource: Boolean): Boolean {
                        fadeToDefaultBackground()
                        if (dataBinding.albumCoverView.isSwitchInProgress()) {
                            dataBinding.albumCoverView.setPendingCover(defaultCoverBitmap)
                        } else {
                            dataBinding.albumCoverView.setCoverBitmap(defaultCoverBitmap)
                        }
                        return false
                    }
                    override fun onResourceReady(resource: Bitmap?, model: Any?, target: Target<Bitmap>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                        if (isFinishing || isDestroyed) return true

                        resource?.let { bitmap ->
                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                if (dataBinding.albumCoverView.isSwitchInProgress()) {
                                    dataBinding.albumCoverView.setPendingCover(bitmap)
                                } else {
                                    dataBinding.albumCoverView.setCoverBitmap(bitmap)
                                }
                                setBlurBackground(bitmap)
                                val stream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                val bytes = stream.toByteArray()
                                val cachedSong = song.copy(coverBytes = bytes)
                                AudioPlayManager.updateCurrentSong(cachedSong)
                            }
                        }
                        return true
                    }
                })
                .submit()
        } else {
            fadeToDefaultBackground()
            if (dataBinding.albumCoverView.isSwitchInProgress()) {
                dataBinding.albumCoverView.setPendingCover(defaultCoverBitmap)
            } else {
                dataBinding.albumCoverView.setCoverBitmap(defaultCoverBitmap)
            }
        }
    }

    private fun setDefaultCover() {
        dataBinding.albumCoverView.setCoverBitmap(defaultCoverBitmap)
        if (defaultBgBitmap != null) {
            dataBinding.ivPlayingBg.setImageBitmap(defaultBgBitmap)
        } else {
            dataBinding.ivPlayingBg.setImageDrawable(ColorDrawable(android.graphics.Color.BLACK))
        }
        dataBinding.ivLrcMaskTop.setImageDrawable(null)
        dataBinding.ivLrcMaskBottom.setImageDrawable(null)
    }

    private fun fadeToDefaultBackground() {
        if (isFinishing || isDestroyed) return
        val imageView = dataBinding.ivPlayingBg
        val oldDrawable = imageView.drawable
        val newDrawable = if (defaultBgBitmap != null) {
            BitmapDrawable(resources, defaultBgBitmap)
        } else {
            ColorDrawable(android.graphics.Color.BLACK)
        }
        val layers = arrayOf(
            oldDrawable ?: ColorDrawable(android.graphics.Color.TRANSPARENT),
            newDrawable
        )
        val transitionDrawable = TransitionDrawable(layers)
        imageView.setImageDrawable(transitionDrawable)
        transitionDrawable.startTransition(350)
        imageView.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            updateLrcMask()
        }, 400)
    }

    private fun setBlurBackground(bitmap: Bitmap) {
        if (isFinishing || isDestroyed) return
        val imageView = dataBinding.ivPlayingBg
        val oldDrawable = imageView.drawable
        imageView.post {
            if (isFinishing || isDestroyed) return@post
            try {
                val tempView = ImageView(this@AudioPlayerActivity)
                Blurry.with(this@AudioPlayerActivity)
                    .sampling(10)
                    .from(bitmap)
                    .into(tempView)
                val newDrawable = tempView.drawable
                val layers = arrayOf(
                    oldDrawable ?: ColorDrawable(android.graphics.Color.TRANSPARENT),
                    newDrawable ?: BitmapDrawable(resources, bitmap)
                )
                val transitionDrawable = TransitionDrawable(layers)
                imageView.setImageDrawable(transitionDrawable)
                transitionDrawable.startTransition(350)
                imageView.postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    updateLrcMask()
                }, 400)
            } catch (_: Exception) {
                imageView.setImageBitmap(defaultBgBitmap)
                updateLrcMask()
            }
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

    private fun switchCoverLrc(showCover: Boolean) {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            dataBinding.albumCoverView.isVisible = showCover
            dataBinding.lrcLayout.isVisible = showCover.not()
        }
        if (showCover) {
            return
        }
        dataBinding.lrcView.doOnLayout {
            val song = AudioPlayManager.currentSong.value
            if (song != null) {
                loadLrc(song)
            }
        }
        updateLrcMask()
    }

    private fun updateLrcMask() {
        if (!dataBinding.lrcLayout.isVisible) return
        dataBinding.ivLrcMaskTop.post {
            updateLrcMaskTop()
        }
        dataBinding.ivLrcMaskBottom.post {
            updateLrcMaskBottom()
        }
    }

    private fun updateLrcMaskTop() {
        try {
            updateLrcMaskInternal(dataBinding.ivLrcMaskTop, true)
        } catch (_: Exception) {
        }
    }

    private fun updateLrcMaskBottom() {
        try {
            updateLrcMaskInternal(dataBinding.ivLrcMaskBottom, false)
        } catch (_: Exception) {
        }
    }

    private fun updateLrcMaskInternal(maskView: ImageView, topToBottom: Boolean) {
        if (maskView.width <= 0 || maskView.height <= 0) return
        
        val bg = dataBinding.flBackground
        if (bg.width <= 0 || bg.height <= 0) return
        
        val bitmap = viewToBitmap(bg) ?: return

        val location = IntArray(2)
        maskView.getLocationInWindow(location)
        val bgLocation = IntArray(2)
        bg.getLocationInWindow(bgLocation)

        val relativeX = location[0] - bgLocation[0]
        val relativeY = location[1] - bgLocation[1]
        val clipW = maskView.width.coerceAtMost(bitmap.width - relativeX.coerceAtLeast(0))
        val clipH = maskView.height.coerceAtMost(bitmap.height - relativeY.coerceAtLeast(0))
        if (clipW <= 0 || clipH <= 0) {
            bitmap.recycle()
            return
        }

        try {
            val clipped = Bitmap.createBitmap(bitmap, relativeX.coerceAtLeast(0), relativeY.coerceAtLeast(0), clipW, clipH)
            bitmap.recycle()
            val alphaBitmap = clipped.transAlpha(topToBottom)
            clipped.recycle()
            maskView.setImageBitmap(alphaBitmap)
        } catch (_: Exception) {
            bitmap.recycle()
        }
    }

    private fun viewToBitmap(view: android.view.View): Bitmap? {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun Bitmap.transAlpha(topToBottom: Boolean): Bitmap {
        val result = this.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        for (y in 0 until result.height) {
            val alpha = if (topToBottom) {
                ((result.height - y).toFloat() / result.height * 255).toInt()
            } else {
                (y.toFloat() / result.height * 255).toInt()
            }
            val rowStart = y * result.width
            for (x in 0 until result.width) {
                val idx = rowStart + x
                pixels[idx] = (pixels[idx] and 0x00FFFFFF) or (alpha shl 24)
            }
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_out_bottom)
    }
}
