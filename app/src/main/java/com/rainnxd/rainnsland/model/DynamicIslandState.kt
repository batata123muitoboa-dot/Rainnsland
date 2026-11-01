package com.rainnxd.rainnsland.model

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

sealed interface DynamicIslandMode {
    data object Compact : DynamicIslandMode
    data object MinimalLeading : DynamicIslandMode
    data object MinimalTrailing : DynamicIslandMode
    data object Expanded : DynamicIslandMode
}

sealed class DynamicIslandEvent(
    val compactWidth: Dp,
    val expandedWidth: Dp,
    val expandedHeight: Dp
) {
    data class Music(
        val songTitle: String,
        val artist: String,
        val albumArtColor: Long,
        val isPlaying: Boolean,
        val progress: Float,
        val albumArtBitmap: android.graphics.Bitmap? = null
    ) : DynamicIslandEvent(120.dp, 340.dp, 145.dp)

    data class Call(
        val callerName: String,
        val callDuration: String,
        val isVideo: Boolean,
        val photoUri: String? = null
    ) : DynamicIslandEvent(130.dp, 340.dp, 130.dp)

    data class Timer(
        val timeRemaining: String,
        val progress: Float,
        val label: String
    ) : DynamicIslandEvent(110.dp, 340.dp, 110.dp)

    data class Battery(
        val level: Int,
        val isCharging: Boolean,
        val chargingSpeed: String = ""
    ) : DynamicIslandEvent(110.dp, 340.dp, 110.dp)

    data class AirPods(
        val deviceName: String = "Bluetooth Device",
        val batteryLevel: Int = -1
    ) : DynamicIslandEvent(115.dp, 340.dp, 110.dp)

    data class Notification(
        val appName: String,
        val title: String,
        val text: String,
        val packageName: String,
        val pendingIntent: android.app.PendingIntent? = null,
        val remoteInput: android.app.RemoteInput? = null,
        val sbnKey: String? = null
    ) : DynamicIslandEvent(130.dp, 340.dp, 160.dp)

    data class RingMode(
        val mode: Int, // AudioManager.RINGER_MODE_NORMAL, VIBRATE, SILENT
        val modeName: String
    ) : DynamicIslandEvent(120.dp, 340.dp, 110.dp)

    data object None : DynamicIslandEvent(90.dp, 340.dp, 100.dp)
}
