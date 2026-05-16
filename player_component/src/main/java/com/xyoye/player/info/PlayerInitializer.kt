package com.xyoye.player.info

import android.graphics.Color
import com.xyoye.data_component.enums.PixelFormat
import com.xyoye.data_component.enums.PlayerType
import com.xyoye.data_component.enums.SurfaceType
import com.xyoye.data_component.enums.VLCAudioOutput
import com.xyoye.data_component.enums.VLCHWDecode
import com.xyoye.data_component.enums.VLCPixelFormat
import com.xyoye.data_component.enums.VideoScreenScale

/**
 * Created by xyoye on 2020/10/29.
 */

object PlayerInitializer {

    var isPrintLog: Boolean = true
    var isOrientationEnabled = true
    var isEnableAudioFocus = true
    var isLooping = false
    var playerType = PlayerType.TYPE_VLC_PLAYER
    var surfaceType = SurfaceType.VIEW_TEXTURE
    var screenScale = VideoScreenScale.SCREEN_SCALE_DEFAULT

    var selectSourceDirectory: String? = null

    object Player {
        const val DEFAULT_SPEED = 1f
        const val DEFAULT_PRESS_SPEED = 2f

        var isMediaCodeCEnabled = false
        var isMediaCodeCH265Enabled = false
        var isOpenSLESEnabled = false
        var pixelFormat = PixelFormat.PIXEL_AUTO
        var vlcPixelFormat = VLCPixelFormat.PIXEL_RGB_32
        var vlcHWDecode = VLCHWDecode.HW_ACCELERATION_AUTO
        var videoSpeed = DEFAULT_SPEED
        var pressVideoSpeed = DEFAULT_PRESS_SPEED
        var vlcAudioOutput = VLCAudioOutput.AUTO
        var isAutoPlayNext = true
        var wifiCacheSize = 1024L
        var mobileCacheSize = 100L
    }

    object Subtitle {
        const val DEFAULT_POSITION = 0L
        const val DEFAULT_SIZE = 50
        const val DEFAULT_STROKE = 50
        const val DEFAULT_TEXT_COLOR = Color.WHITE
        const val DEFAULT_STROKE_COLOR = Color.BLACK

        var offsetPosition = DEFAULT_POSITION

        var textSize = DEFAULT_SIZE
        var strokeWidth = DEFAULT_STROKE
        var textColor = DEFAULT_TEXT_COLOR
        var strokeColor = DEFAULT_STROKE_COLOR
    }
}