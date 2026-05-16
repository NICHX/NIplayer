package com.xyoye.common_component.utils.subtitle

import com.xyoye.common_component.extension.toHexString
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Created by xyoye on 2020/11/30.
 */

object SubtitleHashUtils {

    fun getShooterHash(videoPath: String): String? {
        try {
            val stringBuilder = StringBuilder()
            val file = RandomAccessFile(videoPath, "r")
            val fileLength: Long = file.length()
            val positions =
                longArrayOf(4096, fileLength / 3 * 2, fileLength / 3, fileLength - 8192)
            for (position in positions) {
                var buffer = ByteArray(4096)
                if (fileLength < position) {
                    file.close()
                    return stringBuilder.toString()
                }
                file.seek(position)
                val realBufferSize: Int = file.read(buffer)
                buffer = buffer.copyOfRange(0, realBufferSize)
                val messageDigest = MessageDigest.getInstance("MD5")
                val byteArray = messageDigest.digest(buffer)
                stringBuilder.append(byteArray.toHexString())
                stringBuilder.append(";")
            }
            file.close()
            stringBuilder.deleteCharAt(stringBuilder.length - 1)
            return stringBuilder.toString()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return null
    }
}