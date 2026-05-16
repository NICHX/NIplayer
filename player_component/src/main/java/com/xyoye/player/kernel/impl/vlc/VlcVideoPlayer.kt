package com.xyoye.player.kernel.impl.vlc

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Point
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Surface
import com.xyoye.common_component.utils.IOUtils
import com.xyoye.data_component.bean.VideoTrackBean
import com.xyoye.data_component.enums.SurfaceType
import com.xyoye.data_component.enums.TrackType
import com.xyoye.data_component.enums.VLCHWDecode
import com.xyoye.player.info.PlayerInitializer
import com.xyoye.player.kernel.inter.AbstractVideoPlayer
import com.xyoye.player.utils.PlayerConstant
import com.xyoye.player.utils.VideoLog
import com.xyoye.player.utils.VlcProxyServer
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMedia.VideoTrack
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

/**
 * Created by xyoye on 2021/4/12.
 */

class VlcVideoPlayer(private val mContext: Context) : AbstractVideoPlayer() {

    companion object {
        private val TAG = VlcVideoPlayer::class.java.simpleName

        @Volatile
        var playbackState = PlaybackStateCompat.STATE_NONE
            private set
    }

    private lateinit var libVlc: LibVLC
    private lateinit var mMediaPlayer: MediaPlayer
    private lateinit var mMedia: Media
    private var videoSourceFd: AssetFileDescriptor? = null

    private var mCurrentDuration = 0L
    private var seekable = true
    private var isBufferEnd = false
    private val mVideoSize = Point(0, 0)
    private var consecutiveErrorCount = 0
    private val maxConsecutiveErrors = 5

    override fun initPlayer() {
        setOptions()
        mMediaPlayer = MediaPlayer(libVlc)
        mMediaPlayer.setAudioOutput(PlayerInitializer.Player.vlcAudioOutput.value)
        initVLCEventListener()
    }

    override fun setDataSource(path: String, headers: Map<String, String>?) {
        val vlcMedia = createVlcMedia(path, headers)
        if (vlcMedia == null) {
            mPlayerEventListener.onInfo(PlayerConstant.MEDIA_INFO_URL_EMPTY, 0)
            return
        }

        mMedia = vlcMedia

        //是否开启硬件加速
        if (PlayerInitializer.Player.vlcHWDecode == VLCHWDecode.HW_ACCELERATION_DISABLE) {
            mMedia.setHWDecoderEnabled(false, false)
        } else if (PlayerInitializer.Player.vlcHWDecode == VLCHWDecode.HW_ACCELERATION_DECODING ||
            PlayerInitializer.Player.vlcHWDecode == VLCHWDecode.HW_ACCELERATION_FULL
        ) {
            mMedia.setHWDecoderEnabled(true, true)
            if (PlayerInitializer.Player.vlcHWDecode == VLCHWDecode.HW_ACCELERATION_DECODING) {
                mMedia.addOption(":no-mediacodec-dr")
                mMedia.addOption(":no-omxil-dr")
            }
        } /* else automatic: use default options */

        mCurrentDuration = mMedia.duration
        mMediaPlayer.media = mMedia
        mMedia.release()
    }

    override fun setOptions() {
        val options = arrayListOf<String>()
        options.add("-v")
        options.add("--android-display-chroma")
        options.add(PlayerInitializer.Player.vlcPixelFormat.value)
        options.add(":network-caching=3000")
        options.add(":file-caching=10000")
        libVlc = LibVLC(mContext, options)
    }

    override fun setSurface(surface: Surface?) {

    }

    override fun prepareAsync() {
        mMediaPlayer.play()
    }

    override fun start() {
        mMediaPlayer.play()
    }

    override fun pause() {
        mMediaPlayer.pause()
    }

    override fun stop() {
        playbackState = PlaybackStateCompat.STATE_STOPPED

        if (mMediaPlayer.hasMedia() && !mMediaPlayer.isReleased) {
            mMediaPlayer.stop()
        }
    }

    override fun reset() {

    }

    override fun release() {
        try {
            if (this::mMediaPlayer.isInitialized && !mMediaPlayer.isReleased
                && mMediaPlayer.vlcVout.areViewsAttached()) {
                mMediaPlayer.vlcVout.detachViews()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mMediaPlayer.setEventListener(null)
        stop()
        IOUtils.closeIO(videoSourceFd)
        mMediaPlayer.media?.apply {
            setEventListener(null)
            release()
        }
        if (!mMediaPlayer.isReleased) {
            mMediaPlayer.release()
        }
        if (this::libVlc.isInitialized) {
            libVlc.release()
        }
    }

    override fun seekTo(timeMs: Long) {
        if (seekable && isPlayerAvailable()) {
            mMediaPlayer.time = timeMs
        }
    }

    override fun setSpeed(speed: Float) {
        mMediaPlayer.rate = speed
    }

    override fun setVolume(leftVolume: Float, rightVolume: Float) {
        val volume = ((leftVolume + rightVolume) / 2 * 100).toInt()
        mMediaPlayer.volume = volume
    }

    override fun setLooping(isLooping: Boolean) {

    }

    override fun setSubtitleOffset(offsetMs: Long) {
        mMediaPlayer.spuDelay = offsetMs * 1000
    }

    override fun isPlaying(): Boolean {
        return mMediaPlayer.isPlaying && isBufferEnd
    }

    override fun getCurrentPosition(): Long {
        return mMediaPlayer.time
    }

    override fun getDuration(): Long {
        return mCurrentDuration
    }

    override fun getSpeed(): Float {
        return mMediaPlayer.rate
    }

    override fun getVideoSize(): Point {
        return mVideoSize
    }

    override fun getBufferedPercentage(): Int {
        return 0
    }

    override fun getTcpSpeed(): Long {
        return 0
    }

    override fun getTracks(type: TrackType): List<VideoTrackBean> {
        return getVlcTrackType(type)
            ?.let {
                mMediaPlayer.getTracks(it)
            }?.map {
                VideoTrackBean.internal(it.id, it.name, type, selected = it.selected)
            } ?: emptyList()
    }

    override fun selectTrack(track: VideoTrackBean) {
        if (isPlayerAvailable() && track.id.isNullOrEmpty().not()) {
            mMediaPlayer.selectTrack(track.id)
            seekTo(mMediaPlayer.time)
        }
    }

    override fun deselectTrack(type: TrackType) {
        getVlcTrackType(type)?.let {
            mMediaPlayer.unselectTrackType(it)
            seekTo(mMediaPlayer.time)
        }
    }

    override fun supportAddTrack(type: TrackType): Boolean {
        return type == TrackType.SUBTITLE || type == TrackType.AUDIO
    }

    override fun addTrack(track: VideoTrackBean): Boolean {
        return when (track.type) {
            TrackType.AUDIO -> {
                val audioPath = track.type.getAudio(track.trackResource) ?: return false
                mMediaPlayer.addSlave(IMedia.Slave.Type.Audio, audioPath, true)
            }

            TrackType.SUBTITLE -> {
                val subtitlePath = track.type.getSubtitle(track.trackResource) ?: return false
                mMediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, subtitlePath, true)
            }

            else -> return false
        }
    }

    private fun getVlcTrackType(type: TrackType): Int? {
        return when (type) {
            TrackType.AUDIO -> IMedia.Track.Type.Audio
            TrackType.SUBTITLE -> IMedia.Track.Type.Text
            else -> null
        }
    }

    fun attachRenderView(vlcVideoLayout: VLCVideoLayout) {
        if (mMediaPlayer.vlcVout.areViewsAttached()) {
            mMediaPlayer.detachViews()
        }
        val isTextureView = PlayerInitializer.surfaceType == SurfaceType.VIEW_TEXTURE
        mMediaPlayer.attachViews(vlcVideoLayout, null, true, isTextureView)
    }

    fun setScale(scale: MediaPlayer.ScaleType) {
        mMediaPlayer.videoScale = scale
    }

    private fun initVLCEventListener() {
        mMediaPlayer.setEventListener {
            //VlcEventLog.log(it)
            when (it.type) {
                //缓冲
                MediaPlayer.Event.Buffering -> {
                    isBufferEnd = it.buffering == 100f
                    if (it.buffering == 100f) {
                        mPlayerEventListener.onInfo(PlayerConstant.MEDIA_INFO_BUFFERING_END, 0)
                        VideoLog.d("$TAG--listener--onInfo--> MEDIA_INFO_BUFFERING_END")
                    } else {
                        mPlayerEventListener.onInfo(PlayerConstant.MEDIA_INFO_BUFFERING_START, 0)
                        VideoLog.d("$TAG--listener--onInfo--> MEDIA_INFO_BUFFERING_START")
                    }
                }
                //打开中
                MediaPlayer.Event.Opening -> {
                }
                //播放中
                MediaPlayer.Event.Playing -> {
                    consecutiveErrorCount = 0
                    playbackState = PlaybackStateCompat.STATE_PLAYING
                }
                //已暂停
                MediaPlayer.Event.Paused -> playbackState = PlaybackStateCompat.STATE_PAUSED
                //是否可跳转
                MediaPlayer.Event.SeekableChanged -> seekable = it.seekable
                //播放错误
                MediaPlayer.Event.EncounteredError -> {
                    consecutiveErrorCount++
                    mPlayerEventListener.onError()
                    VideoLog.d("$TAG--listener--onInfo--> onError, consecutive: $consecutiveErrorCount")
                    if (consecutiveErrorCount >= maxConsecutiveErrors) {
                        stop()
                    }
                }
                //时长输出
                MediaPlayer.Event.LengthChanged -> {
                    mCurrentDuration = it.lengthChanged
                }

                MediaPlayer.Event.ESSelected -> {
                    if (it.esChangedType == IMedia.Track.Type.Video) {
                        val track = mMediaPlayer.getSelectedTrack(IMedia.Track.Type.Video)
                        (track as? VideoTrack)?.let { videoTrack ->
                            if (videoTrack.width > 0 && videoTrack.height > 0
                                && videoTrack.width < 10000 && videoTrack.height < 10000) {
                                mVideoSize.x = videoTrack.width
                                mVideoSize.y = videoTrack.height
                            }
                        }
                    }
                }
                //视频输出
                MediaPlayer.Event.Vout -> {
                    if (it.voutCount > 0) {
                        mMediaPlayer.updateVideoSurfaces()

                        mPlayerEventListener.onInfo(
                            PlayerConstant.MEDIA_INFO_VIDEO_RENDERING_START,
                            0
                        )
                        VideoLog.d("$TAG--listener--onInfo--> MEDIA_INFO_VIDEO_RENDERING_START")
                    }
                }
                //播放完成
                MediaPlayer.Event.EndReached -> {
                    mPlayerEventListener.onCompletion()
                    VideoLog.d("$TAG--listener--onInfo--> onCompletion")
                }
            }
        }
    }

    private fun createVlcMedia(path: String, headers: Map<String, String>?): Media? {
        if (path.isEmpty()) {
            return null
        }

        //VLC播放器通过代理服务实现请求头设置
        if (headers?.isNotEmpty() == true) {
            val proxyServer = VlcProxyServer.getInstance()
            if (!proxyServer.isAlive) {
                proxyServer.safeStart()
            }
            val proxyUrl = proxyServer.getInputStreamUrl(path, headers)
            return Media(libVlc, Uri.parse(proxyUrl))
        }

        val videoUri = if (path.startsWith("/")) {
            Uri.fromFile(File(path))
        } else {
            Uri.parse(path)
        }

        // content://
        return if (videoUri.scheme == ContentResolver.SCHEME_CONTENT) {
            IOUtils.closeIO(videoSourceFd)
            videoSourceFd = try {
                mContext.contentResolver.openAssetFileDescriptor(videoUri, "r")
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            videoSourceFd?.run { Media(libVlc, this) }
        } else {
            Media(libVlc, videoUri)
        }
    }

    private fun isPlayerAvailable() = mMediaPlayer.hasMedia() && !mMediaPlayer.isReleased
}