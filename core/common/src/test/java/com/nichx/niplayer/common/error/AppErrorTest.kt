package com.nichx.niplayer.common.error

import com.nichx.niplayer.common.R
import com.nichx.niplayer.common.ui.UiState
import com.nichx.niplayer.common.ui.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AppErrorTest {

    @Test
    fun `无自定义消息时使用类型默认文案资源`() {
        assertEquals(R.string.error_type_network, AppError.Network().displayMessageRes)
        assertEquals(R.string.error_type_auth, AppError.Auth().displayMessageRes)
        assertEquals(R.string.error_type_file, AppError.File().displayMessageRes)
        assertEquals(R.string.error_type_storage, AppError.Storage().displayMessageRes)
        assertEquals(R.string.error_type_decode, AppError.Decode().displayMessageRes)
        assertEquals(R.string.error_type_database, AppError.Database().displayMessageRes)
        assertEquals(R.string.error_type_unknown, AppError.Unknown().displayMessageRes)
    }

    @Test
    fun `自定义消息保留在 message 属性`() {
        val error = AppError.Network(message = "SMB 连接超时")
        assertEquals("SMB 连接超时", error.message)
        assertEquals(R.string.error_type_network, error.displayMessageRes)
    }

    @Test
    fun `各子类 type 映射正确`() {
        assertSame(ErrorType.NETWORK, AppError.Network().type)
        assertSame(ErrorType.AUTH, AppError.Auth().type)
        assertSame(ErrorType.FILE, AppError.File().type)
        assertSame(ErrorType.STORAGE, AppError.Storage().type)
        assertSame(ErrorType.DECODE, AppError.Decode().type)
        assertSame(ErrorType.DATABASE, AppError.Database().type)
        assertSame(ErrorType.UNKNOWN, AppError.Unknown().type)
    }

    @Test
    fun `from 将 IOException 映射为网络错误`() {
        val cause = IOException("connection refused")
        val error = AppError.from(cause)
        assertTrue(error is AppError.Network)
        assertEquals("connection refused", error.message)
        assertEquals(cause, error.cause)
    }

    @Test
    fun `from 保留自定义消息`() {
        val error = AppError.from(IOException("x"), message = "自定义")
        assertEquals("自定义", error.message)
    }

    @Test
    fun `from 将普通异常映射为未知错误`() {
        val error = AppError.from(IllegalStateException("boom"))
        assertTrue(error is AppError.Unknown)
        assertEquals("boom", error.message)
    }

    @Test
    fun `from 不吞掉协程取消异常`() {
        val cancellation = kotlinx.coroutines.CancellationException("cancelled")
        try {
            AppError.from(cancellation)
            assertTrue("应抛出 CancellationException", false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun `cause 保留原始异常`() {
        val cause = RuntimeException("original")
        val error = AppError.Unknown(cause = cause)
        assertEquals(cause, error.cause)
    }
}

class UiStateTest {

    @Test
    fun `map 保持 Loading 状态`() {
        val mapped = UiState.Loading.map { it.toString() }
        assertTrue(mapped is UiState.Loading)
    }

    @Test
    fun `map 保持 Empty 状态`() {
        val mapped = UiState.Empty.map { it.toString() }
        assertTrue(mapped is UiState.Empty)
    }

    @Test
    fun `map 保持 Error 状态`() {
        val error = UiState.Error(AppError.Network())
        val mapped = error.map { it.toString() }
        assertTrue(mapped is UiState.Error)
        assertEquals(error.error, (mapped as UiState.Error).error)
    }

    @Test
    fun `map 转换 Success 数据`() {
        val success: UiState<List<String>> = UiState.Success(listOf("a", "b"))
        val mapped = success.map { it.size }
        assertTrue(mapped is UiState.Success<*>)
        assertEquals(2, (mapped as UiState.Success<Int>).data)
    }

    @Test
    fun `Error 可携带具体错误`() {
        val error = UiState.Error(AppError.File(message = "404"))
        assertEquals("404", error.error.message)
        assertEquals(R.string.error_type_file, error.error.displayMessageRes)
    }
}
