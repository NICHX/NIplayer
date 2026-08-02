package com.nichx.niplayer.common.error

/**
 * 错误分类枚举（O-25）。
 *
 * 用于统一错误提示的文案与图标映射。每类有默认中文文案，可在展示时被 [AppError] 自带的
 * `message` 覆盖。映射到 [NiMessageSeverity] 时：[AUTH]/[NETWORK]/[STORAGE] 默认 Error，
 * [DECODE]/[UNKNOWN] 默认 Warning（不影响继续浏览）。
 */
enum class ErrorType {
    /** 网络异常：超时、无法连接、DNS 失败等。 */
    NETWORK,

    /** 认证/权限异常：401 账号密码错误、403 无权限。 */
    AUTH,

    /** 文件/资源异常：404 文件不存在、路径无效、SAF URI 失效。 */
    FILE,

    /** 存储源异常：连接测试失败、协议不支持、Storage 创建失败。 */
    STORAGE,

    /** 解码/播放异常：不支持的编码、Media3 初始化失败。 */
    DECODE,

    /** 数据库异常：Room 读写失败、迁移错误。 */
    DATABASE,

    /** 未知/其他异常。 */
    UNKNOWN;

    /** 该错误类型的默认中文文案。 */
    val defaultMessage: String
        get() = when (this) {
            NETWORK -> "网络异常"
            AUTH -> "账号密码错误"
            FILE -> "文件不存在"
            STORAGE -> "存储源连接失败"
            DECODE -> "解码失败"
            DATABASE -> "数据读写失败"
            UNKNOWN -> "发生错误"
        }
}

/**
 * 统一错误模型（O-25）。
 *
 * 采用 sealed class 按错误来源分子类，符合项目约定
 * "Error handling uses AppError sealed class with source-specific subclasses"。
 * 每个 [AppError] 携带可选的 [message]（覆盖默认文案）与 [cause] 原始异常，
 * 供 ViewModel → UI 单向传递，UI 层据 [type] 选择文案/图标/严重级别展示。
 *
 * 使用方式：
 * ```
 * catch (e: IOException) -> emit(AppError.Network(message = "SMB 连接超时", cause = e))
 * catch (e: HttpException) {
 *     val err = when (e.code()) {
 *         401 -> AppError.Auth()
 *         403 -> AppError.Auth(message = "无权限访问")
 *         404 -> AppError.File()
 *         else -> AppError.Network()
 *     }
 * }
 * ```
 */
sealed class AppError {
    /** 错误分类，用于默认文案与图标映射。 */
    abstract val type: ErrorType

    /** 覆盖默认文案的自定义消息，null 时使用 [ErrorType.defaultMessage]。 */
    abstract val message: String?

    /** 原始异常，供日志记录，不展示给用户。 */
    abstract val cause: Throwable?

    /** 展示用文案：优先 [message]，否则 [type] 默认文案。 */
    val displayMessage: String
        get() = message ?: type.defaultMessage

    /** 网络异常：超时、连接失败、DNS 错误。 */
    data class Network(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val type: ErrorType = ErrorType.NETWORK
    }

    /** 认证/权限异常：401/403。 */
    data class Auth(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val type: ErrorType = ErrorType.AUTH
    }

    /** 文件/资源异常：404、路径无效、URI 失效。 */
    data class File(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val type: ErrorType = ErrorType.FILE
    }

    /** 存储源异常：连接测试失败、协议不支持。 */
    data class Storage(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val type: ErrorType = ErrorType.STORAGE
    }

    /** 解码/播放异常：不支持的编码、初始化失败。 */
    data class Decode(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val type: ErrorType = ErrorType.DECODE
    }

    /** 数据库异常：Room 读写失败。 */
    data class Database(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val type: ErrorType = ErrorType.DATABASE
    }

    /** 未知/其他异常。 */
    data class Unknown(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val type: ErrorType = ErrorType.UNKNOWN
    }

    companion object {
        /**
         * 由 [Throwable] 推断 [AppError] 子类。
         *
         * 默认按异常类型粗分（IOException → Network，其他 → Unknown），
         * 复杂场景（HTTP 状态码区分）应由调用方显式构造对应子类。
         */
        fun from(throwable: Throwable, message: String? = null): AppError = when (throwable) {
            is java.io.IOException -> Network(message = message ?: throwable.message, cause = throwable)
            is kotlinx.coroutines.CancellationException -> throw throwable
            else -> Unknown(message = message ?: throwable.message, cause = throwable)
        }
    }
}
