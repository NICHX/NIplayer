package com.xyoye.common_component.application.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * Created by xyoye on 2022/12/27
 */

class Permission {
    /**
     * 存储权限
     * Android 13及以上使用新的媒体权限
     * Android 12及以下使用旧的存储权限
     */
    val storage = PermissionRequest(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        }
    )

    /**
     * 相机权限
     */
    val camera = PermissionRequest(
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.VIBRATE
        )
    )

    /**
     * 通知权限（Android 13+）
     */
    val notification = PermissionRequest(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }
    )

    /**
     * 检查是否有所有文件访问权限
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * 请求所有文件访问权限
     * 仅适用于Android 11及以上版本
     */
    fun requestAllFilesAccess(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    activity.startActivity(intent)
                }
            }
        }
    }

    /**
     * 请求所有文件访问权限
     * 仅适用于Android 11及以上版本
     */
    fun requestAllFilesAccess(fragment: Fragment, requestCode: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${fragment.requireActivity().packageName}")
                    fragment.startActivityForResult(intent, requestCode)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    fragment.startActivityForResult(intent, requestCode)
                }
            }
        }
    }

    class PermissionRequest(
        private val permissions: Array<String>
    ) {

        fun request(fragment: Fragment, result: PermissionResult.() -> Unit) {
            requestPermission(fragment.childFragmentManager, result)
        }

        fun request(activity: AppCompatActivity, result: PermissionResult.() -> Unit) {
            requestPermission(activity.supportFragmentManager, result)
        }

        private fun requestPermission(
            fragmentManager: FragmentManager,
            result: PermissionResult.() -> Unit
        ) {
            PermissionManager.requestPermissions(
                fragmentManager,
                permissions,
                PermissionResult().apply(result)
            )
        }
    }
}