package com.nichx.niplayer.common.crash

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局未捕获异常处理器（O-12）。
 *
 * 通过 [Thread.setDefaultUncaughtExceptionHandler] 捕获主线程与子线程未处理异常，
 * 将崩溃堆栈写入 `filesDir/crash_logs/` 下的日志文件，再委托原 handler 让进程按默认行为终止。
 *
 * 下次启动时由 [consumePreviousCrash] 读取并删除日志，UI 层据非 null 结果提示用户上报，
 * 避免崩溃静默丢失。
 *
 * 线程安全：崩溃回调可能在任意线程触发，文件写入仅依赖 [File] API（单次 write），
 * 不与其他业务并发；[consumePreviousCrash] 在主线程启动时调用，无竞争。
 */
@Singleton
class CrashHandler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val crashDir: File by lazy { File(context.filesDir, "crash_logs").apply { mkdirs() } }

    /** 崩溃日志文件名（仅保留最近一次，覆盖式写入）。 */
    private val latestCrashFile: File get() = File(crashDir, "crash_latest.log")

    /** 安装全局未捕获异常处理器。应在 Application.onCreate 最早处调用一次。 */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(thread, throwable)
            } catch (_: Throwable) {
                // 写日志失败不应阻断默认崩溃流程
            }
            // 委托原 handler（通常是 RuntimeInit.UncaughtHandler，会终止进程）
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 读取并删除上次崩溃日志。
     *
     * @return 上次崩溃的完整堆栈文本，或 null 表示无未读崩溃。
     * 消费即删除，确保下次启动不重复提示。
     */
    fun consumePreviousCrash(): String? {
        val file = latestCrashFile
        if (!file.exists()) return null
        return runCatching {
            val text = file.readText()
            file.delete()
            text
        }.getOrNull()
    }

    /** 检查是否存在未读崩溃日志（不删除），用于决定是否显示提示。 */
    fun hasPreviousCrash(): Boolean = latestCrashFile.exists()

    private fun writeCrashLog(thread: Thread, throwable: Throwable) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("========== NIplayer Crash Report ==========")
            pw.println("Time: $timestamp")
            pw.println("Thread: ${thread.name} (id=${thread.id})")
            pw.println("Process: ${android.os.Process.myPid()}")
            pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            pw.println("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            pw.println("--------------------------------------------")
            throwable.printStackTrace(pw)
        }
        latestCrashFile.writeText(sw.toString())
    }
}
