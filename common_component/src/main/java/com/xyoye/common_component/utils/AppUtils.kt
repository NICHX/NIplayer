package com.xyoye.common_component.utils

import androidx.core.content.pm.PackageInfoCompat
import com.xyoye.common_component.base.app.BaseApplication

/**
 * Created by xyoye on 2020/8/19.
 */

object AppUtils {
    fun getVersionCode(): Long {
        if (SecurityHelper.getInstance().isOfficialApplication) {
            val packageName = BaseApplication.getAppContext().applicationInfo.packageName
            val packageInfo =
                BaseApplication.getAppContext().packageManager.getPackageInfo(packageName, 0)
            return PackageInfoCompat.getLongVersionCode(packageInfo)
        }
        return 0L
    }

    fun getVersionName(): String {
        return try {
            val packageName = BaseApplication.getAppContext().applicationInfo.packageName
            val packageInfo =
                BaseApplication.getAppContext().packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}