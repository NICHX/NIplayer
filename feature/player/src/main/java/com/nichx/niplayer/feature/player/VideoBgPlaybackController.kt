package com.nichx.niplayer.feature.player

import android.graphics.Bitmap
import androidx.media3.common.Player
import android.content.Context
import android.content.Intent

/**
 * 视频后台播放（后台仅音频）控制器（进程级单例）。
 *
 * 桥接 [PlayerActivity]/[PlayerViewModel]（拥有视频 NxPlayer、切歌命令、标题）与
 * 前台服务 [VideoBgPlaybackService]（建 MediaSession + 媒体通知转发命令）。
 * 采用全局对象通信先例（PlaybackRequestHolder 同思路），服务与 Activity 写同一来源。
 *
 * 进入后台模式：[enter] 填入当前播放内核与命令回调并置 [active] = true；
 * 退出后台（回前台 / ViewModel 销毁）：[clear] 清引用并置 [active] = false，
 * 配合 [stopService] 停掉前台服务，避免服务持有已 release 的播放器。
 *
 * 所有权始终归 ViewModel：本控制器只读引用 [sessionPlayer]，不负责 [Player.release]。
 */
object VideoBgPlaybackController {

    /** 当前纳入媒体会话的视频 media3 Player（null = 未进入后台播放）。 */
    var sessionPlayer: Player? = null
        private set

    /** 当前后台播放视频的标题（通知展示用）。 */
    var title: String = ""
        private set

    /** 当前后台播放视频的缩略图（通知 large icon），进入后异步回填。 */
    @Volatile
    var cover: Bitmap? = null
        private set

    /** 是否处于"视频后台播放"会话中。供 [PlayerScreen] 的 ON_PAUSE 暂停闸门判读。 */
    @Volatile
    var active: Boolean = false
        private set

    /** 通知「下一首」转发到播放器列表切换（由 ViewModel 注入，销毁时清空）。 */
    var onNext: (() -> Unit)? = null

    /** 通知「上一首」转发到播放器列表切换（由 ViewModel 注入，销毁时清空）。 */
    var onPrevious: (() -> Unit)? = null

    /** 进入后台播放：填入当前播放器与标题/切歌命令。 */
    fun enter(player: Player?, titleText: String, next: (() -> Unit)?, previous: (() -> Unit)?) {
        sessionPlayer = player
        title = titleText
        onNext = next
        onPrevious = previous
        active = true
    }

    /** 退出后台播放：清引用（释放所有权仍由 ViewModel 负责）。 */
    fun clear() {
        sessionPlayer = null
        title = ""
        cover = null
        onNext = null
        onPrevious = null
        active = false
    }

    /** 设置通知封面缩略图（由 ViewModel 在异步 IO 线程解码缓存缩略图后回填）。 */
    fun setCover(bitmap: Bitmap) {
        cover = bitmap
    }

    /** 退出后台播放并停掉前台服务。 */
    fun stopService(context: Context) {
        clear()
        context.stopService(Intent(context, VideoBgPlaybackService::class.java))
    }
}
