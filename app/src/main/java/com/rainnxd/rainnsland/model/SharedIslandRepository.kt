package com.rainnxd.rainnsland.model

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

object SharedIslandRepository {

    private const val PREFS_NAME = "rainnsland_island_prefs"
    private var prefs: SharedPreferences? = null

    private val _currentEvent = MutableStateFlow<DynamicIslandEvent>(
        DynamicIslandEvent.None
    )
    val currentEvent: StateFlow<DynamicIslandEvent> = _currentEvent.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    private val _offsetY = MutableStateFlow(16) // Y-Offset in dp for Punch Hole calibration
    val offsetY: StateFlow<Int> = _offsetY.asStateFlow()

    private val _expandedOffsetY = MutableStateFlow(20) // Y-Offset in dp for Expanded Menu Drop
    val expandedOffsetY: StateFlow<Int> = _expandedOffsetY.asStateFlow()

    private val _scale = MutableStateFlow(1.0f) // Scale factor (0.75, 1.0, 1.25, 1.5, 2.0)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _customWidthOffset = MutableStateFlow(0) // dp offset for width
    val customWidthOffset: StateFlow<Int> = _customWidthOffset.asStateFlow()

    private val _customHeightOffset = MutableStateFlow(0) // dp offset for height
    val customHeightOffset: StateFlow<Int> = _customHeightOffset.asStateFlow()

    private val _isGlowEnabled = MutableStateFlow(true) // Toggle for orbiting glow animation
    val isGlowEnabled: StateFlow<Boolean> = _isGlowEnabled.asStateFlow()

    private val _glowColor = MutableStateFlow(Color(0xFF00E5FF)) // Customizable glow color
    val glowColor: StateFlow<Color> = _glowColor.asStateFlow()

    private val _isAlwaysShowIdle = MutableStateFlow(false) // Disabled by default
    val isAlwaysShowIdle: StateFlow<Boolean> = _isAlwaysShowIdle.asStateFlow()

    private val _showBatteryIdle = MutableStateFlow(false) // Disabled by default
    val showBatteryIdle: StateFlow<Boolean> = _showBatteryIdle.asStateFlow()

    private val _alwaysShowCharging = MutableStateFlow(false) // Toggle to always show battery event while charging
    val alwaysShowCharging: StateFlow<Boolean> = _alwaysShowCharging.asStateFlow()

    private val _isChargingDetectionEnabled = MutableStateFlow(false) // Disabled by default
    val isChargingDetectionEnabled: StateFlow<Boolean> = _isChargingDetectionEnabled.asStateFlow()

    private val _isBluetoothDetectionEnabled = MutableStateFlow(false) // Disabled by default
    val isBluetoothDetectionEnabled: StateFlow<Boolean> = _isBluetoothDetectionEnabled.asStateFlow()

    private val _isNotificationListenerEnabled = MutableStateFlow(false) // Disabled by default
    val isNotificationListenerEnabled: StateFlow<Boolean> = _isNotificationListenerEnabled.asStateFlow()

    private val _isMusicDetectionEnabled = MutableStateFlow(false) // Disabled by default
    val isMusicDetectionEnabled: StateFlow<Boolean> = _isMusicDetectionEnabled.asStateFlow()

    private val _isCallDetectionEnabled = MutableStateFlow(false) // Disabled by default
    val isCallDetectionEnabled: StateFlow<Boolean> = _isCallDetectionEnabled.asStateFlow()

    private val _isRingModeDetectionEnabled = MutableStateFlow(false) // Disabled by default
    val isRingModeDetectionEnabled: StateFlow<Boolean> = _isRingModeDetectionEnabled.asStateFlow()

    private val _isShowOnLockscreen = MutableStateFlow(true) // Show on lockscreen enabled by default
    val isShowOnLockscreen: StateFlow<Boolean> = _isShowOnLockscreen.asStateFlow()

    private val _isHideOnQuickSettings = MutableStateFlow(false) // Hide on quick settings panel
    val isHideOnQuickSettings: StateFlow<Boolean> = _isHideOnQuickSettings.asStateFlow()

    private val _isQuickSettingsOpen = MutableStateFlow(false) // Quick settings panel active state
    val isQuickSettingsOpen: StateFlow<Boolean> = _isQuickSettingsOpen.asStateFlow()

    private val _isServiceShuttingDown = MutableStateFlow(false) // Graceful shutdown animation flag
    val isServiceShuttingDown: StateFlow<Boolean> = _isServiceShuttingDown.asStateFlow()

    private val _eventChangeTrigger = MutableStateFlow(0L) // Timestamp to trigger glow ONLY on event change
    val eventChangeTrigger: StateFlow<Long> = _eventChangeTrigger.asStateFlow()

    private var autoDismissJob: Job? = null
    private var timerTickerJob: Job? = null

    private val eventQueue = mutableListOf<DynamicIslandEvent>()
    
    private val _secondaryEvent = MutableStateFlow<DynamicIslandEvent>(DynamicIslandEvent.None)
    val secondaryEvent: StateFlow<DynamicIslandEvent> = _secondaryEvent.asStateFlow()

    fun initPrefs(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p

        // Load persisted values
        _offsetY.value = p.getInt("offsetY", 16)
        _expandedOffsetY.value = p.getInt("expandedOffsetY", 20)
        _scale.value = p.getFloat("scale", 1.0f)
        _customWidthOffset.value = p.getInt("customWidthOffset", 0)
        _customHeightOffset.value = p.getInt("customHeightOffset", 0)
        _isGlowEnabled.value = p.getBoolean("isGlowEnabled", true)
        _glowColor.value = Color(p.getInt("glowColor", Color(0xFF00E5FF).toArgb()))
        _isAlwaysShowIdle.value = p.getBoolean("isAlwaysShowIdle", false)
        _showBatteryIdle.value = p.getBoolean("showBatteryIdle", false)
        _alwaysShowCharging.value = p.getBoolean("alwaysShowCharging", false)
        _isChargingDetectionEnabled.value = p.getBoolean("isChargingDetectionEnabled", false)
        _isBluetoothDetectionEnabled.value = p.getBoolean("isBluetoothDetectionEnabled", false)
        _isNotificationListenerEnabled.value = p.getBoolean("isNotificationListenerEnabled", false)
        _isMusicDetectionEnabled.value = p.getBoolean("isMusicDetectionEnabled", false)
        _isCallDetectionEnabled.value = p.getBoolean("isCallDetectionEnabled", false)
        _isRingModeDetectionEnabled.value = p.getBoolean("isRingModeDetectionEnabled", false)
        _isShowOnLockscreen.value = p.getBoolean("isShowOnLockscreen", true)
        _isHideOnQuickSettings.value = p.getBoolean("isHideOnQuickSettings", false)
    }

    fun setEvent(event: DynamicIslandEvent, resetExpanded: Boolean = true) {
        val current = _currentEvent.value

        if (current is DynamicIslandEvent.Battery && event is DynamicIslandEvent.Battery) {
            _currentEvent.value = event
            val sec = _secondaryEvent.value
            if (sec is DynamicIslandEvent.Battery) {
                _secondaryEvent.value = event
            }
            return
        }

        if (event is DynamicIslandEvent.None) {
            if (eventQueue.isNotEmpty()) {
                _currentEvent.value = eventQueue.removeAt(0)
                _secondaryEvent.value = eventQueue.firstOrNull() ?: DynamicIslandEvent.None
            } else {
                _currentEvent.value = DynamicIslandEvent.None
                _secondaryEvent.value = DynamicIslandEvent.None
            }
            return
        }

        if (current is DynamicIslandEvent.Music && event is DynamicIslandEvent.Battery) {
            _secondaryEvent.value = event
            scheduleAutoDismissForSecondaryBattery()
            return
        }

        if (current is DynamicIslandEvent.Call && (event is DynamicIslandEvent.Music || event is DynamicIslandEvent.Battery)) {
            _secondaryEvent.value = event
            if (event is DynamicIslandEvent.Battery) {
                scheduleAutoDismissForSecondaryBattery()
            }
            return
        }

        autoDismissJob?.cancel()
        timerTickerJob?.cancel()

        _currentEvent.value = event
        _eventChangeTrigger.value = System.currentTimeMillis()
        if (resetExpanded) {
            _isExpanded.value = false
        }

        when (event) {
            is DynamicIslandEvent.Notification -> {
                scheduleAutoDismissForNotification()
            }
            is DynamicIslandEvent.Battery -> {
                if (!event.isCharging || !_alwaysShowCharging.value) {
                    scheduleAutoDismissForBattery()
                }
            }
            is DynamicIslandEvent.RingMode -> {
                scheduleAutoDismissForRingMode()
            }
            is DynamicIslandEvent.Timer -> {
                startTimerCountdown(event)
            }
            else -> {}
        }
    }

    private fun scheduleAutoDismissForSecondaryBattery() {
        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            if (_secondaryEvent.value is DynamicIslandEvent.Battery) {
                _secondaryEvent.value = DynamicIslandEvent.None
            }
        }
    }

    private fun scheduleAutoDismissForNotification() {
        autoDismissJob?.cancel()
        autoDismissJob = CoroutineScope(Dispatchers.Main).launch {
            delay(4000)
            if (!_isExpanded.value) {
                setEvent(DynamicIslandEvent.None)
            }
        }
    }

    private fun scheduleAutoDismissForBattery() {
        autoDismissJob?.cancel()
        autoDismissJob = CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            if (!_isExpanded.value) {
                setEvent(DynamicIslandEvent.None)
            }
        }
    }

    private fun scheduleAutoDismissForRingMode() {
        autoDismissJob?.cancel()
        autoDismissJob = CoroutineScope(Dispatchers.Main).launch {
            delay(2500)
            if (!_isExpanded.value) {
                setEvent(DynamicIslandEvent.None)
            }
        }
    }

    private fun startTimerCountdown(initialEvent: DynamicIslandEvent.Timer) {
        timerTickerJob?.cancel()
        var currentSeconds = parseTimeToSeconds(initialEvent.timeRemaining) ?: return

        timerTickerJob = CoroutineScope(Dispatchers.Main).launch {
            while (currentSeconds > 0) {
                delay(1000)
                currentSeconds--
                val formattedTime = formatSecondsToTime(currentSeconds)
                val current = _currentEvent.value
                if (current is DynamicIslandEvent.Timer) {
                    _currentEvent.value = current.copy(
                        timeRemaining = formattedTime,
                        progress = currentSeconds.toFloat() / 300f
                    )
                } else {
                    break
                }
            }
            if (currentSeconds <= 0) {
                setEvent(DynamicIslandEvent.None)
            }
        }
    }

    private fun parseTimeToSeconds(timeStr: String): Int? {
        val parts = timeStr.split(":")
        return when (parts.size) {
            2 -> {
                val mins = parts[0].toIntOrNull() ?: return null
                val secs = parts[1].toIntOrNull() ?: return null
                mins * 60 + secs
            }
            3 -> {
                val hrs = parts[0].toIntOrNull() ?: return null
                val mins = parts[1].toIntOrNull() ?: return null
                val secs = parts[2].toIntOrNull() ?: return null
                hrs * 3600 + mins * 60 + secs
            }
            else -> null
        }
    }

    private fun formatSecondsToTime(totalSecs: Int): String {
        val hrs = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hrs > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
        }
    }

    fun togglePlayPause() {
        val current = _currentEvent.value
        if (current is DynamicIslandEvent.Music) {
            _currentEvent.value = current.copy(isPlaying = !current.isPlaying)
        }
    }

    fun cycleEvents() {
        if (eventQueue.isNotEmpty()) {
            val next = eventQueue.removeAt(0)
            val oldCurrent = _currentEvent.value
            if (oldCurrent !is DynamicIslandEvent.None) {
                eventQueue.add(oldCurrent)
            }
            _currentEvent.value = next
            _secondaryEvent.value = eventQueue.firstOrNull() ?: DynamicIslandEvent.None
        }
    }

    fun updateMusicProgress(progress: Float) {
        val current = _currentEvent.value
        if (current is DynamicIslandEvent.Music) {
            _currentEvent.value = current.copy(progress = progress)
        }
    }

    fun toggleExpand() {
        _isExpanded.value = !_isExpanded.value
        if (_isExpanded.value) {
            autoDismissJob?.cancel()
        } else {
            val current = _currentEvent.value
            if (current is DynamicIslandEvent.Notification) {
                scheduleAutoDismissForNotification()
            } else if (current is DynamicIslandEvent.Battery && (!current.isCharging || !_alwaysShowCharging.value)) {
                scheduleAutoDismissForBattery()
            } else if (current is DynamicIslandEvent.RingMode) {
                scheduleAutoDismissForRingMode()
            }
        }
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
        if (expanded) {
            autoDismissJob?.cancel()
        } else {
            val current = _currentEvent.value
            if (current is DynamicIslandEvent.Notification) {
                scheduleAutoDismissForNotification()
            } else if (current is DynamicIslandEvent.Battery && (!current.isCharging || !_alwaysShowCharging.value)) {
                scheduleAutoDismissForBattery()
            } else if (current is DynamicIslandEvent.RingMode) {
                scheduleAutoDismissForRingMode()
            }
        }
    }

    fun setOffsetY(y: Int) {
        _offsetY.value = y
        prefs?.edit()?.putInt("offsetY", y)?.apply()
    }

    fun setChargingDetectionEnabled(enabled: Boolean) {
        _isChargingDetectionEnabled.value = enabled
        prefs?.edit()?.putBoolean("isChargingDetectionEnabled", enabled)?.apply()
    }

    fun setBluetoothDetectionEnabled(enabled: Boolean) {
        _isBluetoothDetectionEnabled.value = enabled
        prefs?.edit()?.putBoolean("isBluetoothDetectionEnabled", enabled)?.apply()
    }

    fun setExpandedOffsetY(y: Int) {
        _expandedOffsetY.value = y
        prefs?.edit()?.putInt("expandedOffsetY", y)?.apply()
    }

    fun setScale(scaleValue: Float) {
        _scale.value = scaleValue
        prefs?.edit()?.putFloat("scale", scaleValue)?.apply()
    }

    fun setCustomWidthOffset(offset: Int) {
        _customWidthOffset.value = offset
        prefs?.edit()?.putInt("customWidthOffset", offset)?.apply()
    }

    fun setCustomHeightOffset(offset: Int) {
        _customHeightOffset.value = offset
        prefs?.edit()?.putInt("customHeightOffset", offset)?.apply()
    }

    fun setGlowEnabled(enabled: Boolean) {
        _isGlowEnabled.value = enabled
        prefs?.edit()?.putBoolean("isGlowEnabled", enabled)?.apply()
    }

    fun setGlowColor(color: Color) {
        _glowColor.value = color
        prefs?.edit()?.putInt("glowColor", color.toArgb())?.apply()
    }

    fun setAlwaysShowIdle(enabled: Boolean) {
        _isAlwaysShowIdle.value = enabled
        prefs?.edit()?.putBoolean("isAlwaysShowIdle", enabled)?.apply()
    }

    fun setShowBatteryIdle(enabled: Boolean) {
        _showBatteryIdle.value = enabled
        prefs?.edit()?.putBoolean("showBatteryIdle", enabled)?.apply()
    }

    fun setNotificationListenerEnabled(enabled: Boolean) {
        _isNotificationListenerEnabled.value = enabled
        prefs?.edit()?.putBoolean("isNotificationListenerEnabled", enabled)?.apply()
    }

    fun setMusicDetectionEnabled(enabled: Boolean) {
        _isMusicDetectionEnabled.value = enabled
        prefs?.edit()?.putBoolean("isMusicDetectionEnabled", enabled)?.apply()
    }

    fun setCallDetectionEnabled(enabled: Boolean) {
        _isCallDetectionEnabled.value = enabled
        prefs?.edit()?.putBoolean("isCallDetectionEnabled", enabled)?.apply()
    }

    fun setRingModeDetectionEnabled(enabled: Boolean) {
        _isRingModeDetectionEnabled.value = enabled
        prefs?.edit()?.putBoolean("isRingModeDetectionEnabled", enabled)?.apply()
    }

    fun setShowOnLockscreen(enabled: Boolean) {
        _isShowOnLockscreen.value = enabled
        prefs?.edit()?.putBoolean("isShowOnLockscreen", enabled)?.apply()
    }

    fun setHideOnQuickSettings(enabled: Boolean) {
        _isHideOnQuickSettings.value = enabled
        prefs?.edit()?.putBoolean("isHideOnQuickSettings", enabled)?.apply()
    }

    fun setQuickSettingsOpen(open: Boolean) {
        _isQuickSettingsOpen.value = open
    }

    fun setServiceShuttingDown(shuttingDown: Boolean) {
        _isServiceShuttingDown.value = shuttingDown
    }

    fun setAlwaysShowCharging(enabled: Boolean) {
        _alwaysShowCharging.value = enabled
        prefs?.edit()?.putBoolean("alwaysShowCharging", enabled)?.apply()
        val current = _currentEvent.value
        if (enabled && current is DynamicIslandEvent.Battery && current.isCharging) {
            autoDismissJob?.cancel()
        } else if (!enabled && current is DynamicIslandEvent.Battery && current.isCharging) {
            autoDismissJob = CoroutineScope(Dispatchers.Main).launch {
                delay(3000)
                if (!_isExpanded.value) {
                    _currentEvent.value = DynamicIslandEvent.None
                }
            }
        }
    }

    fun clearEvent() {
        autoDismissJob?.cancel()
        timerTickerJob?.cancel()
        _currentEvent.value = DynamicIslandEvent.None
        _isExpanded.value = false
    }
}
