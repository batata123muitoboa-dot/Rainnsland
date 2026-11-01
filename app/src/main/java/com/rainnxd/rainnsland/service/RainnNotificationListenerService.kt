
package com.rainnxd.rainnsland.service

import android.app.Notification
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.RemoteViews
import com.rainnxd.rainnsland.model.DynamicIslandEvent
import com.rainnxd.rainnsland.model.SharedIslandRepository
import java.util.Locale

class RainnNotificationListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var clockKey: Pair<String, Int>? = null

    private val clockUpdater = object : Runnable {
        override fun run() {
            val key = clockKey ?: return

            try {
                val current = activeNotifications?.firstOrNull {
                    it.packageName == key.first && it.id == key.second
                }

                if (current == null) {
                    stopClockUpdater()
                    return
                }

                processClockNotification(current)
            } catch (e: Exception) {
                Log.e("RainnsTimer", "Clock update failed", e)
            }

            if (clockKey == key) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName == "com.rainnxd.rainnsland") return

        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        val isWhatsApp =
            packageName == "com.whatsapp" ||
                    packageName == "com.whatsapp.w4b"

        val allText = listOf(title, text, bigText, subText)
            .joinToString(" ")

        // Calls
        val isCall = notification.category == Notification.CATEGORY_CALL ||
                (isWhatsApp &&
                        (allText.contains("Call", true) ||
                                allText.contains("Chamada", true)))

        if (isCall) {
            SharedIslandRepository.setEvent(
                DynamicIslandEvent.Call(
                    callerName = title.ifBlank { "Incoming call" },
                    callDuration = "Incoming call",
                    isVideo = allText.contains("video", true)
                ),
                resetExpanded = false
            )
            SharedIslandRepository.setExpanded(true)
            return
        }

        // Clock notification detection
        val isClockPackage =
            packageName.contains("clock", true) ||
                    packageName.contains("alarm", true) ||
                    packageName.contains("timer", true)

        if (isClockPackage) {
            clockKey = packageName to sbn.id
            handler.removeCallbacks(clockUpdater)
            processClockNotification(sbn)
            handler.postDelayed(clockUpdater, 1000L)
            return
        }

        if (allText.isBlank()) return

        val appName = try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }

        SharedIslandRepository.setEvent(
            DynamicIslandEvent.Notification(
                appName = appName,
                title = title.ifBlank { appName },
                text = text.ifBlank { allText },
                packageName = packageName
            ),
            resetExpanded = false
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        startMediaSessionMonitoring()
    }

    private var activeMediaController: android.media.session.MediaController? = null

    private fun startMediaSessionMonitoring() {
        if (!SharedIslandRepository.isMusicDetectionEnabled.value) return
        try {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
            val componentName = android.content.ComponentName(this, RainnNotificationListenerService::class.java)
            
            mediaSessionManager?.addOnActiveSessionsChangedListener({ controllers ->
                if (SharedIslandRepository.isMusicDetectionEnabled.value) {
                    updateActiveController(controllers)
                } else {
                    updateActiveController(emptyList())
                }
            }, componentName)

            if (SharedIslandRepository.isMusicDetectionEnabled.value) {
                val controllers = mediaSessionManager?.getActiveSessions(componentName)
                updateActiveController(controllers)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateActiveController(controllers: List<android.media.session.MediaController>?) {
        val musicController = controllers?.firstOrNull { controller ->
            val playbackState = controller.playbackState
            val metadata = controller.metadata
            val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            val state = playbackState?.state
            // A controller is only valid if it's actually playing or paused with valid title/artist (not empty system sessions)
            playbackState != null && 
                !title.isNullOrBlank() && 
                title != "Unknown" && 
                (state == android.media.session.PlaybackState.STATE_PLAYING || state == android.media.session.PlaybackState.STATE_PAUSED)
        }

        activeMediaController = musicController
        setActiveController(musicController)

        if (musicController != null) {
            val metadata = musicController.metadata
            val title = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Song"
            val artist = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val bitmap = metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART) 
                ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)

            val artColor = bitmap?.let { extractDominantColor(it) } ?: 0xFF1DB954
            val playbackState = musicController.playbackState
            val isPlaying = playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING

            // Calculate real progress
            val position = playbackState?.position ?: 0L
            val duration = metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 1L
            val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

            SharedIslandRepository.setEvent(
                DynamicIslandEvent.Music(
                    songTitle = title,
                    artist = artist,
                    albumArtColor = artColor,
                    isPlaying = isPlaying,
                    progress = progress,
                    albumArtBitmap = bitmap
                ),
                resetExpanded = false
            )

            musicController.registerCallback(object : android.media.session.MediaController.Callback() {
                override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
                    super.onMetadataChanged(metadata)
                    val t = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: title
                    val a = metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: artist
                    val bmp = metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART) 
                        ?: metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART)
                    val color = bmp?.let { extractDominantColor(it) } ?: artColor
                    val playing = musicController.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
                    val pos = musicController.playbackState?.position ?: 0L
                    val dur = metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 1L
                    val prog = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

                    SharedIslandRepository.setEvent(
                        DynamicIslandEvent.Music(
                            songTitle = t,
                            artist = a,
                            albumArtColor = color,
                            isPlaying = playing,
                            progress = prog,
                            albumArtBitmap = bmp
                        ),
                        resetExpanded = false
                    )
                }

                override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
                    super.onPlaybackStateChanged(state)
                    val playing = state?.state == android.media.session.PlaybackState.STATE_PLAYING
                    val pos = state?.position ?: 0L
                    val dur = musicController.metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 1L
                    val prog = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

                    val current = SharedIslandRepository.currentEvent.value
                    if (current is DynamicIslandEvent.Music) {
                        SharedIslandRepository.setEvent(
                            current.copy(isPlaying = playing, progress = prog),
                            resetExpanded = false
                        )
                    } else if (playing) {
                        // If it wasn't showing because it was paused, bring it back when played!
                        val t = musicController.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
                        val a = musicController.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown"
                        val bmp = musicController.metadata?.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART)
                        val color = bmp?.let { extractDominantColor(it) } ?: 0xFF1DB954
                        SharedIslandRepository.setEvent(
                            DynamicIslandEvent.Music(
                                songTitle = t,
                                artist = a,
                                albumArtColor = color,
                                isPlaying = true,
                                progress = prog,
                                albumArtBitmap = bmp
                            ),
                            resetExpanded = false
                        )
                    }
                }
            })
        }
    }

    private fun extractDominantColor(bitmap: android.graphics.Bitmap): Long {
        try {
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 24, 24, true)
            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var count = 0L
            for (x in 0 until scaled.width) {
                for (y in 0 until scaled.height) {
                    val pixel = scaled.getPixel(x, y)
                    rSum += android.graphics.Color.red(pixel)
                    gSum += android.graphics.Color.green(pixel)
                    bSum += android.graphics.Color.blue(pixel)
                    count++
                }
            }
            if (count > 0) {
                val r = (rSum / count).toInt()
                val g = (gSum / count).toInt()
                val b = (bSum / count).toInt()
                return android.graphics.Color.argb(255, r, g, b).toLong() and 0xFFFFFFFFL
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0xFF1DB954
    }

    companion object {
        private var activeController: android.media.session.MediaController? = null

        fun setActiveController(controller: android.media.session.MediaController?) {
            activeController = controller
        }

        fun playPause() {
            try {
                val state = activeController?.playbackState?.state
                if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                    activeController?.transportControls?.pause()
                } else {
                    activeController?.transportControls?.play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun skipNext() {
            try {
                activeController?.transportControls?.skipToNext()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun skipPrevious() {
            try {
                activeController?.transportControls?.skipToPrevious()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun processClockNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val packageName = sbn.packageName
        val extras = notification.extras

        val remoteTexts = mutableListOf<String>()

        remoteTexts += extractRemoteViewText(notification.contentView, packageName)
        remoteTexts += extractRemoteViewText(notification.bigContentView, packageName)
        remoteTexts += extractRemoteViewText(notification.headsUpContentView, packageName)

        val sources = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        ) + remoteTexts

        val combinedText = sources.joinToString(" ").lowercase(Locale.ROOT)

        val label = when {
            combinedText.contains("stopwatch") ||
                    combinedText.contains("cronômetro") ||
                    combinedText.contains("cronometro") -> "Stopwatch"

            combinedText.contains("timer") ||
                    combinedText.contains("temporizador") -> "Timer"

            else -> "Clock"
        }

        val durationRegex =
            Regex("""(?<!\d)(\d{1,2}):([0-5]\d)(?::([0-5]\d))?(?!\d)""")

        for (source in sources) {
            val match = durationRegex.find(source) ?: continue

            val first = match.groupValues[1].toLongOrNull() ?: continue
            val second = match.groupValues[2].toLongOrNull() ?: continue
            val third = match.groupValues[3]
                .takeIf { it.isNotEmpty() }
                ?.toLongOrNull()

            val totalSeconds = if (third != null) {
                first * 3600L + second * 60L + third
            } else {
                first * 60L + second
            }

            SharedIslandRepository.setEvent(
                DynamicIslandEvent.Timer(
                    timeRemaining = formatTime(totalSeconds),
                    progress = 0.5f,
                    label = label
                ),
                resetExpanded = false
            )

            return
        }

        Log.w("RainnsTimer", "No time found: $remoteTexts")
    }

    private fun extractRemoteViewText(
        remoteViews: RemoteViews?,
        packageName: String
    ): List<String> {
        if (remoteViews == null) return emptyList()

        return try {
            val remoteContext = createPackageContext(
                packageName,
                Context.CONTEXT_RESTRICTED
            )

            val container = FrameLayout(this)
            val root = remoteViews.apply(remoteContext, container)
            val results = mutableListOf<String>()

            fun collect(view: View) {
                if (view is TextView) {
                    view.text?.toString()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(results::add)
                }

                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        collect(view.getChildAt(i))
                    }
                }
            }

            collect(root)
            results.distinct()
        } catch (e: Exception) {
            Log.e("RainnsTimer", "RemoteViews extraction failed", e)
            emptyList()
        }
    }

    private fun formatTime(totalSeconds: Long): String {
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun stopClockUpdater() {
        handler.removeCallbacks(clockUpdater)
        clockKey = null

        if (SharedIslandRepository.currentEvent.value is DynamicIslandEvent.Timer) {
            SharedIslandRepository.setEvent(
                DynamicIslandEvent.None,
                resetExpanded = true
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        if (clockKey == (sbn.packageName to sbn.id)) {
            stopClockUpdater()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(clockUpdater)
        super.onDestroy()
    }
}
