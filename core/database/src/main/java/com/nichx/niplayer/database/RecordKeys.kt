package com.nichx.niplayer.database

import com.nichx.niplayer.database.enums.MediaType

/**
 * 播放历史云同步记录的业务键。
 *
 * ## 本地键（storageId + uniqueKey）
 * 仅用于本地 DB 去重与续播，不参与云同步匹配。uniqueKey 可能含普通分隔符，
 * 用 SOH 控制符（\u0001）绝对分隔。
 *
 * ## 同步键（storageSignature + 相对路径）
 * 用于云端跨设备合并与删除传播。旧方案用「storageId + uniqueKey」作同步键，而
 * storageId / uniqueKey 中的 library.id 都是设备本地的自增主键，两台设备指向同一
 * 存储/文件时算出不同的键，导致同步永远匹配不到同一条记录（重复插入、删除失配）。
 * BUG-B 修复：同步键改为**设备无关**的「归一化存储地址 + 存储内相对路径」，两端只要
 * 用相同地址配置同一服务器即可对齐。用 STX 控制符（\u0002）与本地键隔离开，防止串扰。
 */
internal const val RECORD_KEY_SEPARATOR = "\u0001"
internal const val SYNC_KEY_SEPARATOR = "\u0002"

/** 构造本地业务键：`storageId + uniqueKey`。 */
fun recordKey(storageId: Int, uniqueKey: String): String =
    "$storageId$RECORD_KEY_SEPARATOR$uniqueKey"

/**
 * 构造设备无关的云同步键：`归一化存储地址 + 存储内相对路径`。
 *
 * 两端用相同地址添加同一服务器时，[normalizeBaseUrl] 得到一致值，进而 [storagePath]
 * 一致 → 同步键一致 → 进度可合并、删除可传播。
 */
fun syncKey(baseUrl: String, storagePath: String): String =
    "${normalizeBaseUrl(baseUrl)}$SYNC_KEY_SEPARATOR$storagePath"

/**
 * 轻量归一化存储地址：去首尾空白、去尾部 `/`。
 *
 * 保持大小写原样：两端「复制粘贴」相同地址即可对齐，避免过度小写造成真正不同的路径被合并。
 */
fun normalizeBaseUrl(url: String): String = url.trim().trimEnd('/')

/** 从 [syncKey] 中解析出存储相对路径（`\u0002` 之后的部分），无则返回 null。 */
fun syncKeyPathOf(syncKey: String): String? {
    val idx = syncKey.indexOf(SYNC_KEY_SEPARATOR)
    return if (idx < 0) null else syncKey.substring(idx + SYNC_KEY_SEPARATOR.length)
}

/**
 * 该媒体类型是否可参与云同步。
 *
 * 仅网络存储（SMB / WebDAV / 其他远端）适合跨设备合并；LOCAL / EXTERNAL / QUICK_ACCESS
 * 都是设备本地内容，同路径不代表同一文件，不应同步（否则两端误合并不同内容）。
 */
fun MediaType.isSyncableBase(): Boolean = this in setOf(
    MediaType.SMB_SERVER,
    MediaType.WEBDAV_SERVER,
    MediaType.OTHER_STORAGE,
)