package com.xyoye.common_component.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

object SafPathResolver {

    fun isExternalStorageManager(): Boolean {
        return try {
            Environment.isExternalStorageManager()
        } catch (_: Exception) {
            false
        }
    }

    fun resolveTreeUri(context: Context, treeUri: Uri): String? {
        if (treeUri.authority != "com.android.externalstorage.documents") return null
        val treeId = treeUri.lastPathSegment ?: return null
        val parts = Uri.decode(treeId).split("/", limit = 2)
        if (parts.isEmpty()) return null
        val volume = parts[0]
        val subPath = parts.getOrElse(1) { "" }

        val volumeRoot = when {
            volume == "primary" -> "/storage/emulated/0"
            else -> {
                val candidate = File("/storage/$volume")
                if (candidate.isDirectory) candidate.absolutePath
                else null
            }
        } ?: return null

        return if (subPath.isEmpty()) volumeRoot else "$volumeRoot/$subPath"
    }

    fun resolveTargetFile(context: Context, targetStorageUrl: String, fileName: String): File? {
        if (!isExternalStorageManager()) return null
        val treeUri = Uri.parse(targetStorageUrl)
        val dirPath = resolveTreeUri(context, treeUri) ?: return null
        val file = File(dirPath, fileName)
        return if (file.exists() || file.parentFile?.exists() == true) file
        else if (file.parentFile?.mkdirs() == true) file
        else null
    }
}