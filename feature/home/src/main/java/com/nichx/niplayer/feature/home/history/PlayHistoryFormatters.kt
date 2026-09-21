package com.nichx.niplayer.feature.home.history

import com.nichx.niplayer.feature.home.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


internal fun formatDateGroup(dateKey: String, context: Context): String {
    // dateKey is yyyy-MM-dd
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(
        Date(System.currentTimeMillis() - 86400000)
    )
    return when (dateKey) {
        today -> context.getString(R.string.play_history_today)
        yesterday -> context.getString(R.string.play_history_yesterday)
        else -> dateKey
    }
}

internal fun formatPositionMs(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
