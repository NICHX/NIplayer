package com.xyoye.common_component.storage.impl

import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import com.xyoye.common_component.extension.open
import com.xyoye.common_component.extension.openDirectory
import com.xyoye.common_component.extension.openFile
import com.xyoye.common_component.extension.standardFileInfo
import com.xyoye.common_component.storage.AbstractStorage
import com.xyoye.common_component.storage.file.StorageFile
import com.xyoye.common_component.storage.file.helper.SmbPlayServer
import com.xyoye.common_component.storage.file.impl.SmbStorageFile
import com.xyoye.common_component.utils.IOUtils
import com.xyoye.common_component.weight.ToastCenter
import com.xyoye.data_component.bean.StorageFileInfo
import com.xyoye.data_component.entity.MediaLibraryEntity
import com.xyoye.data_component.entity.PlayHistoryEntity
import java.io.InputStream
import java.util.EnumSet

/**
 * Created by xyoye on 2023/1/14.
 */

class SmbStorage(library: MediaLibraryEntity) : AbstractStorage(library) {

    private var mSmbClient = SMBClient()
    private var mSmbSession: Session? = null
    private var mDiskShare: DiskShare? = null

    override var rootUri: Uri = Uri.parse("smb://${library.url}")

    override suspend fun listFiles(file: StorageFile): List<StorageFile> {
        //检测SMB通讯是否正常
        if (checkConnection().not()) {
            return emptyList()
        }
        //要打开的是否为根目录
        if (file.isRootFile()) {
            return getRootShares()
        }
        //只处理SMB文件
        if (file !is SmbStorageFile) {
            return emptyList()
        }
        //切换共享目录
        val switchSuccess = switchShareDisk(file.getShareName()!!, showToast = true)
        if (switchSuccess.not()) {
            return emptyList()
        }
        //展开文件夹
        return listDirectory(file.filePath())
    }

    override suspend fun getRootFile(): StorageFile? {
        //媒体库未预设共享文件夹，则根目录为SMB共享库
        if (library.smbSharePath.isNullOrEmpty()) {
            return SmbStorageFile(this, null, "")
        }
        //检测SMB通讯是否正常
        if (checkConnection().not()) {
            return null
        }
        //切换共享目录
        val shareName = Uri.parse(library.smbSharePath).pathSegments.first()
        val switchSuccess = switchShareDisk(shareName)
        if (switchSuccess.not()) {
            return null
        }
        return SmbStorageFile(this, shareName, "")
    }

    override suspend fun openFile(file: StorageFile): InputStream? {
        //检测SMB通讯是否正常
        if (checkConnection().not()) {
            return null
        }

        val shareName = (file as SmbStorageFile).getShareName()
            ?: return null
        if (switchShareDisk(shareName).not()) {
            return null
        }

        return try {
            mDiskShare?.openFile(file.filePath())?.inputStream
        } catch (e: Exception) {
            e.printStackTrace()

            // 仅关闭 DiskShare 而不是整个连接
            closeDiskShare()

            // 触发重连
            if (switchShareDisk(shareName).not()) {
                return null
            }
            try {
                mDiskShare?.openFile(file.filePath())?.inputStream
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    override suspend fun openFile(file: StorageFile, offset: Long): InputStream? {
        if (offset <= 0) return openFile(file)

        if (checkConnection().not()) return null

        val shareName = (file as SmbStorageFile).getShareName() ?: return null
        if (switchShareDisk(shareName).not()) return null

        return try {
            val smbFile = mDiskShare?.openFile(file.filePath()) ?: return null
            OffsetSmbInputStream(smbFile, offset)
        } catch (e: Exception) {
            e.printStackTrace()
            closeDiskShare()
            if (switchShareDisk(shareName).not()) return null
            try {
                val smbFile = mDiskShare?.openFile(file.filePath()) ?: return null
                OffsetSmbInputStream(smbFile, offset)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private class OffsetSmbInputStream(
        private val smbFile: File,
        private val startOffset: Long
    ) : InputStream() {
        private var position = startOffset

        override fun read(): Int {
            val buf = ByteArray(1)
            val bytesRead = smbFile.read(buf, position, 0, 1)
            return if (bytesRead > 0) { position += bytesRead; buf[0].toInt() and 0xFF } else -1
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytesRead = smbFile.read(b, position, off, len)
            if (bytesRead > 0) position += bytesRead
            return bytesRead
        }

        override fun close() {
            smbFile.close()
        }
    }

    override suspend fun pathFile(path: String, isDirectory: Boolean): StorageFile? {
        if (checkConnection().not()) {
            return null
        }
        val pathSegments = Uri.parse(path).pathSegments
        val targetShare = pathSegments.firstOrNull()
            ?: return null
        val switchShare = switchShareDisk(targetShare)
        if (switchShare.not()) {
            return null
        }
        val diskShare = mDiskShare ?: return null
        if (diskShare.isConnected.not()) {
            return null
        }
        val shareName = diskShare.smbPath.shareName
        val filePath = pathSegments.takeLast(pathSegments.size - 1).joinToString(separator = "/")
        return try {
            diskShare.open(filePath).use { entry ->
                val fileInfo = entry.standardFileInfo()
                val directory = fileInfo.isDirectory
                val fileLength = if (directory) 0L else fileInfo.endOfFile
                SmbStorageFile(this, shareName, filePath, fileLength, directory)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("获取文件信息失败", e)
            null
        }
    }

    override suspend fun fileExists(path: String): Boolean {
        if (checkConnection().not()) {
            return false
        }
        val pathSegments = Uri.parse("/$path").pathSegments
        val targetShare = pathSegments.firstOrNull()
            ?: return false
        if (switchShareDisk(targetShare).not()) {
            return false
        }
        val diskShare = mDiskShare ?: return false
        if (diskShare.isConnected.not()) {
            return false
        }
        val filePath = pathSegments.takeLast(pathSegments.size - 1).joinToString(separator = "/")
        return try {
            diskShare.open(filePath).use { true }
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun createDirectory(path: String): Boolean {
        if (checkConnection().not()) {
            return false
        }
        val pathSegments = Uri.parse("/$path").pathSegments
        val targetShare = pathSegments.firstOrNull() ?: return false
        if (switchShareDisk(targetShare).not()) {
            return false
        }
        val diskShare = mDiskShare ?: return false
        if (diskShare.isConnected.not()) {
            return false
        }
        val dirPath = pathSegments.takeLast(pathSegments.size - 1).joinToString(separator = "/")
        return try {
            diskShare.mkdir(dirPath)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun historyFile(history: PlayHistoryEntity): StorageFile? {
        val storagePath = history.storagePath ?: return null
        return pathFile(storagePath, false)?.also {
            it.playHistory = history
        }
    }

    override suspend fun createPlayUrl(file: StorageFile): String? {
        if (file !is SmbStorageFile) {
            return null
        }
        val playServer = SmbPlayServer.getInstance()
        if (!playServer.startSync()) {
            return null
        }

        // 预热 SMB 连接，使播放器发起 HTTP 请求时连接已就绪，避免播放延迟
        val shareName = file.getShareName()
        if (shareName != null) {
            checkConnection()
            switchShareDisk(shareName)
        }

        return playServer.generatePlayUrl(this, file)
    }

    override suspend fun saveFile(path: String, data: ByteArray): Boolean {
        if (checkConnection().not()) {
            return false
        }
        val pathSegments = Uri.parse("/$path").pathSegments
        val targetShare = pathSegments.firstOrNull() ?: return false
        if (switchShareDisk(targetShare).not()) {
            return false
        }
        val filePath = pathSegments.takeLast(pathSegments.size - 1).joinToString(separator = "/")
        return try {
            val smbFile = mDiskShare?.openFile(
                filePath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                setOf(SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            if (smbFile != null) {
                smbFile.write(data, 0L)
                smbFile.close()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("保存文件失败", e)
            false
        }
    }

    override suspend fun fileInfo(file: StorageFile): StorageFileInfo? {
        if (file !is SmbStorageFile) {
            return null
        }
        if (checkConnection().not()) {
            return null
        }
        val shareName = file.getShareName() ?: return null
        if (switchShareDisk(shareName).not()) {
            return null
        }
        val diskShare = mDiskShare ?: return null

        return try {
            if (file.isShareDirectory()) {
                StorageFileInfo(
                    name = file.fileName(),
                    path = file.storagePath(),
                    isDirectory = true,
                    fileSize = 0,
                    lastModified = 0,
                    childCount = diskShare.openDirectory(file.filePath()).list().size
                )
            } else {
                val fileSize = try {
                    val entry = diskShare.open(file.filePath())
                    entry.use {
                        it.standardFileInfo().endOfFile
                    }
                } catch (_: Exception) { 0L }

                val baseInfo = StorageFileInfo(
                    name = file.fileName(),
                    path = file.storagePath(),
                    isDirectory = false,
                    fileSize = fileSize,
                    lastModified = 0L,
                    isVideo = file.isVideoFile(),
                    isAudio = file.isAudioFile(),
                    isImage = file.isImageFile()
                )

                if (file.isVideoFile() || file.isAudioFile()) {
                    extractMediaMetadata(file, baseInfo)
                } else if (file.isImageFile()) {
                    extractImageMetadata(file, baseInfo)
                } else {
                    baseInfo
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("获取文件信息失败", e)
            null
        }
    }

    private suspend fun extractMediaMetadata(file: SmbStorageFile, base: StorageFileInfo): StorageFileInfo {
        val diskShare = mDiskShare ?: return base
        return try {
            val smbFile = diskShare.openFile(file.filePath())

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(SmbMediaDataSource(smbFile, base.fileSize))

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            val sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)

            retriever.release()

            base.copy(
                videoWidth = widthStr?.toIntOrNull() ?: 0,
                videoHeight = heightStr?.toIntOrNull() ?: 0,
                durationMs = durationStr?.toLongOrNull() ?: 0,
                bitrate = bitrateStr?.toLongOrNull() ?: 0,
                videoCodec = mimeType?.ifEmpty { null },
                audioCodec = null,
                frameRate = frameRateStr?.ifEmpty { null },
                sampleRate = sampleRateStr?.toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            e.printStackTrace()
            base
        }
    }

    private suspend fun extractImageMetadata(file: SmbStorageFile, base: StorageFileInfo): StorageFileInfo {
        val diskShare = mDiskShare ?: return base
        return try {
            val smbFile = diskShare.openFile(file.filePath())
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(SmbFileInputStream(smbFile), null, options)

            base.copy(
                videoWidth = options.outWidth,
                videoHeight = options.outHeight
            )
        } catch (e: Exception) {
            e.printStackTrace()
            base
        }
    }

    private class SmbMediaDataSource(
        private val smbFile: File,
        private val fileSize: Long
    ) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            val readBuffer = ByteArray(size)
            val bytesRead = smbFile.read(readBuffer, position)
            if (bytesRead > 0) {
                System.arraycopy(readBuffer, 0, buffer, offset, bytesRead)
            }
            return bytesRead
        }

        override fun getSize(): Long = fileSize

        override fun close() {
            try {
                smbFile.close()
            } catch (_: Exception) {}
        }
    }

    private class SmbFileInputStream(
        private val smbFile: File
    ) : java.io.InputStream() {
        private var position = 0L

        override fun read(): Int {
            val buf = ByteArray(1)
            val n = smbFile.read(buf, position)
            if (n <= 0) return -1
            position++
            return buf[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val readBuffer = ByteArray(len)
            val bytesRead = smbFile.read(readBuffer, position)
            if (bytesRead <= 0) return -1
            System.arraycopy(readBuffer, 0, b, off, bytesRead)
            position += bytesRead
            return bytesRead
        }

        override fun close() {
            try {
                smbFile.close()
            } catch (_: Exception) {}
        }
    }

    override suspend fun delete(file: StorageFile): Boolean {
        if (file !is SmbStorageFile) {
            return false
        }
        if (checkConnection().not()) {
            return false
        }
        val shareName = file.getShareName() ?: return false
        if (switchShareDisk(shareName).not()) {
            return false
        }
        val diskShare = mDiskShare ?: return false

        return try {
            val filePath = file.filePath()
            if (file.isDirectory()) {
                deleteDirectoryRecursively(diskShare, filePath)
            } else {
                diskShare.rm(filePath)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("删除失败", e)
            false
        }
    }

    private fun deleteDirectoryRecursively(diskShare: DiskShare, dirPath: String): Boolean {
        val entries = diskShare.openDirectory(dirPath).list()
        for (entry in entries) {
            val name = entry.fileName
            if (name == "." || name == "..") continue
            val childPath = generateChildPath(dirPath, name)
            val isDir = (entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
            if (isDir) {
                deleteDirectoryRecursively(diskShare, childPath)
            } else {
                diskShare.rm(childPath)
            }
        }
        diskShare.rmdir(dirPath, true)
        return true
    }

    override suspend fun test(): Boolean {
        if (checkConnection().not()) {
            return false
        }
        val rootFile = getRootFile() ?: return false
        return listFiles(rootFile).isNotEmpty()
    }

    override fun close() {
        closeDiskShare()
        SmbPlayServer.getInstance().releaseStorage(this)
        IOUtils.closeIO(mSmbClient)
    }

    /**
     * 检查SMB连接是否正常，异常则执行重连
     */
    private fun checkConnection(): Boolean {
        if (mSmbSession?.connection?.isConnected == true) {
            return true
        }

        try {
            mSmbSession?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mSmbSession = null

        try {
            val port = if (library.port == 0) SMBClient.DEFAULT_PORT else library.port
            val connection = mSmbClient.connect(library.url, port)
            val session = connection.authenticate(getAuthenticationContext())

            if (session?.connection?.isConnected == true) {
                mSmbSession = session
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    /**
     * 获取SMB身份信息
     */
    private fun getAuthenticationContext(): AuthenticationContext {
        val password = library.password ?: ""

        return if (library.isAnonymous)
            AuthenticationContext.anonymous()
        else
            AuthenticationContext(library.account, password.toCharArray(), null)
    }

    /**
     * 获取SMB共享目录列表
     */
    private fun getRootShares(): List<SmbStorageFile> {
        return try {
            val transport = SMBTransportFactories.SRVSVC.getTransport(mSmbSession)
            ServerService(transport).shares0.map {
                SmbStorageFile(this, it.netName, "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("获取共享目录列表失败", e)
            emptyList()
        }
    }

    /**
     * 展示文件夹
     */
    private fun listDirectory(filePath: String): List<SmbStorageFile> {
        val diskShare = mDiskShare ?: return emptyList()
        val shareName = diskShare.smbPath.shareName

        return try {
            diskShare.openDirectory(filePath)
                .list()
                .filter { it.fileName != "." && it.fileName != ".." }
                .map { info ->
                    val childPath = generateChildPath(filePath, info.fileName)
                
                    val fileAttributes = info.fileAttributes
                    val isDirectory = (fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
                    val fileLength = if (isDirectory) 0L else info.endOfFile

                    SmbStorageFile(this, shareName, childPath, fileLength, isDirectory)
                }
                .filter { it.filePath().isNotEmpty() }
        } catch (e: Exception) {
            e.printStackTrace()
            showErrorToast("获取文件列表失败", e)
            emptyList()
        }
    }

    /**
     * 切换共享目录
     */
    private fun switchShareDisk(shareName: String, showToast: Boolean = false): Boolean {
        val currentShare = mDiskShare
        if (currentShare != null && currentShare.isConnected && currentShare.smbPath.shareName == shareName) {
            return true
        }
        //关闭现有共享目录
        closeDiskShare()

        if (connectShare(shareName)) {
            return true
        }

        try {
            mSmbSession?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mSmbSession = null

        if (checkConnection()) {
            if (connectShare(shareName)) {
                return true
            }
        }

        if (showToast) {
            ToastCenter.showError("切换共享目录失败")
        }
        return false
    }

    private fun connectShare(shareName: String): Boolean {
        return try {
            val diskShare = mSmbSession?.connectShare(shareName) as? DiskShare?
            if (diskShare?.isConnected == true) {
                mDiskShare = diskShare
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 关闭共享目录
     */
    private fun closeDiskShare() {
        if (mDiskShare == null) {
            return
        }
        try {
            mDiskShare!!.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateChildPath(parent: String, child: String): String {
        return Uri.parse(parent)
            .buildUpon()
            .appendPath(child)
            .build()
            .path
            ?.removePrefix("/")
            ?: ""
    }

    private fun showErrorToast(message: String, e: Exception) {
        ToastCenter.showError("$message: ${e.message}")
    }
}