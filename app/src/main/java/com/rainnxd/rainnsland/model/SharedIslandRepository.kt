package com.rainnxd.rainnsland.model

import androidx.compose.ui.graphics.Color
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

    fun setEvent(event: DynamicIslandEvent, resetExpanded: Boolean = true) {
        val current = _currentEvent.value

        // If it's just a battery update (% or Watts changing) and type is already Battery, update silently WITHOUT glow
        if (current is DynamicIslandEvent.Battery && event is DynamicIslandEvent.Battery) {
            if (current.isCharging == event.isCharging) {
                _currentEvent.value = event
                return
            }
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

        // Smart secondary bubble handling: if user is listening to music and plugs in charger (Battery event), show Battery as a secondary floating bubble!
        if (current is DynamicIslandEvent.Music && event is DynamicIslandEvent.Battery) {
            _secondaryEvent.value = event
            if (!eventQueue.contains(event)) eventQueue.add(event)
            _eventChangeTrigger.value = System.currentTimeMillis()
            return
        }

        _currentEvent.value = event
        if (resetExpanded) {
            _isExpanded.value = false
        }
        _secondaryEvent.value = DynamicIslandEvent.None
        eventQueue.clear()
        _eventChangeTrigger.value = System.currentTimeMillis()

        autoDismissJob?.cancel()
        if (event is DynamicIslandEvent.Notification) {
            scheduleAutoDismissForNotification()
        } else if (event is DynamicIslandEvent.Battery && (!event.isCharging || !_alwaysShowCharging.value)) {
            scheduleAutoDismissForBattery()
        }

        if (event is DynamicIslandEvent.Timer) {
            startTimerTicker(event.timeRemaining)
        } else {
            timerTickerJob?.cancel()
        }
    }

    private fun scheduleAutoDismissForNotification() {
        autoDismissJob?.cancel()
        autoDismissJob = CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            if (!_isExpanded.value) {
                _currentEvent.value = eventQueue.removeOrNone()
                _secondaryEvent.value = DynamicIslandEvent.None
            }
        }
    }

    private fun scheduleAutoDismissForBattery() {
        autoDismissJob?.cancel()
        autoDismissJob = CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            if (!_isExpanded.value) {
                _currentEvent.value = eventQueue.removeOrNone()
                _secondaryEvent.value = DynamicIslandEvent.None
            }
        }
    }

    private fun MutableList<DynamicIslandEvent>.removeOrNone(): DynamicIslandEvent {
        return if (this.isNotEmpty()) this.removeAt(0) else DynamicIslandEvent.None
    }

    fun cycleEvents() {
        val curr = _currentEvent.value
        val sec = _secondaryEvent.value
        if (sec !is DynamicIslandEvent.None) {
            _currentEvent.value = sec
            _secondaryEvent.value = curr
        }
    }

    private fun startTimerTicker(initialTimeStr: String) {
        timerTickerJob?.cancel()
        val totalSeconds = parseTimeToSeconds(initialTimeStr) ?: return
        if (totalSeconds <= 0) return

        timerTickerJob = CoroutineScope(Dispatchers.Main).launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                val formatted = formatSecondsToTime(remaining)
                val current = _currentEvent.value
                if (current is DynamicIslandEvent.Timer) {
                    _currentEvent.value = current.copy(
                        timeRemaining = formatted,
                        progress = remaining.toFloat() / totalSeconds.toFloat()
                    )
                } else {
                    break
                }
            }
        }
    }

    fun togglePlayPause() {
        val current = _currentEvent.value
        if (current is DynamicIslandEvent.Music) {
            _currentEvent.value = current.copy(isPlaying = !current.isPlaying)
        }
    }

    private fun parseTimeToSeconds(timeStr: String): Int? {
        val parts = timeStr.trim().split(":")
        return when (parts.size) {
            2 -> {
                val mins = parts[0].toIntOrNull() ?: return null
                val secs = parts[1].toIntOrNull() ?: return null
                mins * 60 + secs
            }
            3 -> {
                val hrs = parts[0].toIntOrNull() ?: return null
                val mins = parts[1].toIntOrNull() ?: return null
                val secs = parts[2].toIntOrNull() ?: return null // Fixed index 2!
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
            // Restart auto dismiss from scratch when collapsed if event is notification or temporary battery
            val current = _currentEvent.value
            if (current is DynamicIslandEvent.Notification) {
                scheduleAutoDismissForNotification()
            } else if (current is DynamicIslandEvent.Battery && (!current.isCharging || !_alwaysShowCharging.value)) {
                scheduleAutoDismissForBattery()
            }
        }
    }

    fun setExpanded(expanded: Boolean) {
        _isExpanded.value = expanded
        if (expanded) {
            autoDismissJob?.cancel()
        } else {
            // Restart auto dismiss from scratch when collapsed if event is notification or temporary battery
            val current = _currentEvent.value
            if (current is DynamicIslandEvent.Notification) {
                scheduleAutoDismissForNotification()
            } else if (current is DynamicIslandEvent.Battery && (!current.isCharging || !_alwaysShowCharging.value)) {
                scheduleAutoDismissForBattery()
            }
        }
    }

    fun setOffsetY(y: Int) {
        _offsetY.value = y
    }

    fun setChargingDetectionEnabled(enabled: Boolean) {
        _isChargingDetectionEnabled.value = enabled
    }

    fun setBluetoothDetectionEnabled(enabled: Boolean) {
        _isBluetoothDetectionEnabled.value = enabled
    }

    fun setExpandedOffsetY(y: Int) {
        _expandedOffsetY.value = y
    }

    fun setScale(scaleValue: Float) {
        _scale.value = scaleValue
    }

    fun setCustomWidthOffset(offset: Int) {
        _customWidthOffset.value = offset
    }

    fun setCustomHeightOffset(offset: Int) {
        _customHeightOffset.value = offset
    }

    fun setGlowEnabled(enabled: Boolean) {
        _isGlowEnabled.value = enabled
    }

    fun setGlowColor(color: Color) {
        _glowColor.value = color
    }

    fun setAlwaysShowIdle(enabled: Boolean) {
        _isAlwaysShowIdle.value = enabled
    }

    fun setShowBatteryIdle(enabled: Boolean) {
        _showBatteryIdle.value = enabled
    }

    fun setNotificationListenerEnabled(enabled: Boolean) {
        _isNotificationListenerEnabled.value = enabled
    }

    fun setCallDetectionEnabled(enabled: Boolean) {
        _isCallDetectionEnabled.value = enabled
    }

    fun setRingModeDetectionEnabled(enabled: Boolean) {
        _isRingModeDetectionEnabled.value = enabled
    }

    fun setShowOnLockscreen(enabled: Boolean) {
        _isShowOnLockscreen.value = enabled
    }

    fun setHideOnQuickSettings(enabled: Boolean) {
        _isHideOnQuickSettings.value = enabled
    }

    fun setQuickSettingsOpen(open: Boolean) {
        _isQuickSettingsOpen.value = open
    }

    fun setServiceShuttingDown(shuttingDown: Boolean) {
        _isServiceShuttingDown.value = shuttingDown
    }

    fun setAlwaysShowCharging(enabled: Boolean) {
        _alwaysShowCharging.value = enabled
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
