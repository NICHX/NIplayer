package com.xyoye.player_component.ui.activities.overlay_permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.xyoye.common_component.base.app.BaseApplication
import com.xyoye.common_component.weight.ToastCenter

/**
 * Created by xyoye on 2022/11/4
 */

class OverlayPermissionActivity : AppCompatActivity() {
    companion object {

        private var permissionCallback: ((Boolean) -> Unit)? = null

        fun requestOverlayPermission(context: Context, callback: ((Boolean) -> Unit)? = null) {
            permissionCallback = callback

            context.startActivity(
                Intent(context, OverlayPermissionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }

        fun hasOverlayPermission(context: Context = BaseApplication.getAppContext()): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onPermissionResult(hasOverlayPermission(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || hasOverlayPermission(this)) {
            onPermissionResult(true)
            return
        }

        requestOverlayPermission()
    }

    private fun requestOverlayPermission() {
        val intent = Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION")
        intent.data = Uri.parse("package:$packageName")
        if (intent.resolveActivity(packageManager) != null) {
            overlayPermissionLauncher.launch(intent)
        } else {
            ToastCenter.showError("无法启动悬浮窗权限授权页")
            onPermissionResult(false)
        }
    }

    private fun onPermissionResult(granted: Boolean) {
        permissionCallback?.invoke(granted)
        finish()
    }
}