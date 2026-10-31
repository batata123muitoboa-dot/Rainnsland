package com.rainnxd.rainnsland.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
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
import com.rainnxd.rainnsland.model.SharedIslandRepository
import com.rainnxd.rainnsland.ui.components.DynamicIsland

class RainnAccessibilityService : AccessibilityService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        var instance: RainnAccessibilityService? = null
            private set
    }

    init {
        instance = this
    }

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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // If feature is disabled by user, reset panel state to false so island remains visible
        if (!SharedIslandRepository.isHideOnQuickSettings.value) {
            if (SharedIslandRepository.isQuickSettingsOpen.value) {
                SharedIslandRepository.setQuickSettingsOpen(false)
            }
            return
        }

        val packageName = event.packageName?.toString() ?: ""
        val className = event.className?.toString() ?: ""

        val isSystemUiPackage = packageName.contains("systemui", ignoreCase = true) ||
                packageName.contains("controlcenter", ignoreCase = true) ||
                packageName.contains("statusbar", ignoreCase = true) ||
                packageName.contains("keyguard", ignoreCase = true) ||
                packageName == "android"

        if (isSystemUiPackage) {
            val isShade = className.contains("Notification", ignoreCase = true) ||
                    className.contains("Shade", ignoreCase = true) ||
                    className.contains("QS", ignoreCase = true) ||
                    className.contains("Quick", ignoreCase = true) ||
                    className.contains("Panel", ignoreCase = true) ||
                    className.contains("Expanded", ignoreCase = true) ||
                    className.contains("Header", ignoreCase = true) ||
                    className.contains("Control", ignoreCase = true) ||
                    className.contains("Center", ignoreCase = true) ||
                    className.contains("StatusBar", ignoreCase = true) ||
                    className.endsWith("FrameLayout")

            if (isShade) {
                SharedIslandRepository.setQuickSettingsOpen(true)
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && packageName.isNotEmpty()) {
            // ONLY reset when a new app/launcher activity window actually becomes active foreground
            SharedIslandRepository.setQuickSettingsOpen(false)
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        setupAccessibilityOverlayWindow()
    }

    private fun setupAccessibilityOverlayWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density

        // TYPE_ACCESSIBILITY_OVERLAY renders on the absolute highest layer in Android 14!
        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            (380 * density).toInt(),
            (240 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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
            setViewTreeLifecycleOwner(this@RainnAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@RainnAccessibilityService)

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
                        params.width = (380 * density).toInt()
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

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
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
