package com.xyoye.common_component.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class NetworkType {
    WIFI,
    MOBILE,
    NONE
}

object NetworkTypeUtil {

    fun getNetworkType(context: Context): NetworkType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return NetworkType.NONE
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
            else -> NetworkType.NONE
        }
    }

    fun isWifi(context: Context): Boolean {
        return getNetworkType(context) == NetworkType.WIFI
    }

    fun isMobile(context: Context): Boolean {
        return getNetworkType(context) == NetworkType.MOBILE
    }
}