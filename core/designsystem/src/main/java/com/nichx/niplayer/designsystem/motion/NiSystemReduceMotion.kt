package com.nichx.niplayer.designsystem.motion

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * 读取**系统**「动画时长倍率」是否为 0。
 *
 * 用户在开发者选项或无障碍里把动画关掉（倍率 0）时返回 true；
 * 用 [ContentObserver] 监听，设置变化后实时生效（而非只在创建时读一次）。
 */
@Composable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(isAnimatorScaleZero(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = isAnimatorScaleZero(context)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

/**
 * 是否开启「减少动态效果」：应用内设置 **或** 系统设置，取并集。
 *
 * 由 App 根部计算并下发到 [LocalNiReduceMotion]。
 */
@Composable
fun rememberNiReduceMotion(userEnabled: Boolean): Boolean =
    userEnabled || rememberSystemReduceMotion()

private fun isAnimatorScaleZero(context: Context): Boolean =
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
