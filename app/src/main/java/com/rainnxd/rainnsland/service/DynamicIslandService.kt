package com.rainnxd.rainnsland.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.rainnxd.rainnsland.R
import com.rainnxd.rainnsland.model.SharedIslandRepository
import com.rainnxd.rainnsland.ui.components.DynamicIsland

class DynamicIslandService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView

    private val viewModelStoreValue = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = viewModelStoreValue

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        startForeground(1001, createNotification())
        setupFloatingWindow()
    }

    private fun createNotification(): Notification {
        val channelId = "dynamic_island_channel"
        val channel = NotificationChannel(
            channelId,
            "Rainnsland Floating Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Rainnsland Active")
            .setContentText("Dynamic Island floating on screen.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density

        val layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        // WindowManager layout params with full cutout rendering support for all apps
        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            (380 * density).toInt(),
            (240 * density).toInt(),
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (SharedIslandRepository.offsetY.value * density).toInt()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DynamicIslandService)
            setViewTreeSavedStateRegistryOwner(this@DynamicIslandService)

            setContent {
                val event by SharedIslandRepository.currentEvent.collectAsState()
                val isExpanded by SharedIslandRepository.isExpanded.collectAsState()
                val offsetY by SharedIslandRepository.offsetY.collectAsState()
                val isShowOnLockscreen by SharedIslandRepository.isShowOnLockscreen.collectAsState()

                @Suppress("DEPRECATION")
                LaunchedEffect(isExpanded, offsetY, isShowOnLockscreen) {
                    params.y = (offsetY * density).toInt()
                    visibility = View.VISIBLE

                    if (isShowOnLockscreen) {
                        params.flags = params.flags or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    } else {
                        params.flags = params.flags and (WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON).inv()
                    }

                    if (isExpanded) {
                        params.width = (380 * density).toInt()
                        params.height = (240 * density).toInt()
                    } else {
                        params.width = (380 * density).toInt() // Keeps wide window buffer for satellite bubble!
                        params.height = (60 * density).toInt()
                    }

                    try {
                        if (composeView.isAttachedToWindow) {
                            windowManager.updateViewLayout(composeView, params)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                DynamicIsland(
                    event = event,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        SharedIslandRepository.toggleExpand()
                    },
                    onTogglePlayPause = {
                        SharedIslandRepository.togglePlayPause()
                    }
                )
            }
        }

        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Ensure service stays alive and running even when MainActivity is swiped away from Recents
        val restartServiceIntent = Intent(applicationContext, DynamicIslandService::class.java)
        restartServiceIntent.setPackage(packageName)
        try {
            startForegroundService(restartServiceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        SharedIslandRepository.clearEvent()
        if (::windowManager.isInitialized && ::composeView.isInitialized) {
            try {
                windowManager.removeView(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        viewModelStore.clear()
    }
}
