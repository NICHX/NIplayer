package com.nichx.niplayer.common.message

import com.nichx.niplayer.common.error.NiMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AppMessageController] 的回归测试 —— 缺陷审计 #17。
 *
 * 原实现用 `MutableSharedFlow(replay = 0, ...)`：`replay = 0` 意味着**没有订阅者期间
 * 发射的消息会被直接丢弃**。而该类的契约是「跨导航/页面切换不丢消息 —— 后台扫描、下载、
 * 同步等在用户离开页面后才完成的结果也能送达」。Activity 被销毁重建（配置变更、从后台恢复）
 * 期间恰好没有订阅者，那段时间内完成的后台任务结果会静默消失。
 *
 * 现改为 `Channel` + `receiveAsFlow()`：消息缓冲到有人消费为止，且恰好投递一次。
 */
class AppMessageControllerTest {

    @Test
    fun `无订阅者期间发送的消息在订阅后仍能收到`() = runBlocking {
        val controller = AppMessageController()
        val message = NiMessage.info("后台任务已完成")

        // 关键：此时没有任何订阅者（模拟 Activity 已销毁 / 宿主尚未挂载）
        controller.post(message)

        // 之后才订阅
        val received = withTimeoutOrNull(2_000) { controller.messages.first() }

        assertEquals(
            "无订阅者期间发出的消息必须被缓冲并在订阅后送达；" +
                "replay = 0 的 SharedFlow 会让此处收到 null",
            message,
            received,
        )
    }

    @Test
    fun `消息恰好投递一次 重新订阅不会重复收到`() = runBlocking {
        val controller = AppMessageController()
        val message = NiMessage.warning("同步冲突已解决")
        controller.post(message)

        val first = withTimeoutOrNull(2_000) { controller.messages.first() }
        assertEquals(message, first)

        // 再订阅一次不应重复收到同一条（排除 replay = 1 式方案）
        val second = withTimeoutOrNull(300) { controller.messages.first() }
        assertNull("同一条消息不应被重复投递", second)
    }

    @Test
    fun `挂起发送与消息顺序保持先进先出`() = runBlocking {
        val controller = AppMessageController()
        val a = NiMessage.info("A")
        val b = NiMessage.info("B")

        controller.show(a)
        controller.show(b)

        assertEquals(a, withTimeoutOrNull(2_000) { controller.messages.first() })
        assertEquals(b, withTimeoutOrNull(2_000) { controller.messages.first() })
    }

    @Test
    fun `无订阅者时发送超过缓冲容量不阻塞调用方`() = runBlocking {
        val controller = AppMessageController()

        // 远超缓冲容量（64）；DROP_OLDEST 下 trySend 不应失败，也不会挂住调用方
        repeat(200) { index ->
            controller.postInfo("message-$index")
        }

        // 订阅后仍能取到消息。缓冲容量 64，发 200 条后缓冲区里留下的是最后 64 条
        // （message-136 .. message-199），即最旧的 136 条被丢弃 —— 这是预期的降级行为。
        val received = withTimeoutOrNull(2_000) { controller.messages.first() }
        assertEquals(
            "溢出时丢弃最旧、保留最新：应收到缓冲区中最早的一条 message-136",
            NiMessage.info("message-136"),
            received,
        )
    }
}
