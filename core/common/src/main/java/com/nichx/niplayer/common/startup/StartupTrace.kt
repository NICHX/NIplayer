package com.nichx.niplayer.common.startup

import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * 冷启动耗时埋点。
 *
 * 以「进程启动时刻」为基准（[Process.getStartElapsedRealtime]，API 24+），每次 [mark] 打印
 * 相对进程启动的累计耗时与相对上一标记的增量耗时，用于定位冷启动卡在主线程的哪一段。
 *
 * 用法：在主线程关键阶段调用 [mark]，例如
 * ```
 * StartupTrace.mark("app.onCreate")
 * ```
 * 首帧绘制完成后由宿主调用 `reportFullyDrawn()`，与日志中的 "first frame" 标记对照得到 TTFD。
 *
 * 注意：本对象**不做**首次初始化，`Process.getStartElapsedRealtime()` 在类加载时读取；
 * 只要首次 [mark] 发生在冷启动早期，基准即准确。
 */
object StartupTrace {

    /** 统一日志 tag，便于 `adb logcat -s NIplayerStartup` 过滤。 */
    const val TAG = "NIplayerStartup"

    /** 进程启动时刻（elapsedRealtime 基准）。 */
    private val processStartElapsed = Process.getStartElapsedRealtime()

    /** 上一次 [mark] 的时刻，用于计算增量耗时。 */
    private var lastMarkElapsed = processStartElapsed

    /**
     * 打一个阶段标记。
     *
     * @param stage 阶段名（建议英文短标识，便于日志检索）
     */
    fun mark(stage: String) {
        val now = SystemClock.elapsedRealtime()
        val sinceProcess = now - processStartElapsed
        val sinceLast = now - lastMarkElapsed
        lastMarkElapsed = now
        Log.d(TAG, "[$stage] 进程启动后 +${sinceProcess}ms（距上一标记 +${sinceLast}ms）")
    }

    /** 距进程启动的累计耗时（毫秒）。 */
    val sinceProcessStartMs: Long
        get() = SystemClock.elapsedRealtime() - processStartElapsed
}
