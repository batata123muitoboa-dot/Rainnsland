package com.rainnxd.rainnsland.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rainnxd.rainnsland.model.DynamicIslandEvent
import com.rainnxd.rainnsland.model.SharedIslandRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DynamicIslandViewModel(application: Application) : AndroidViewModel(application) {

    val currentEvent: StateFlow<DynamicIslandEvent> = SharedIslandRepository.currentEvent
    val isExpanded: StateFlow<Boolean> = SharedIslandRepository.isExpanded
    val offsetY: StateFlow<Int> = SharedIslandRepository.offsetY
    val expandedOffsetY: StateFlow<Int> = SharedIslandRepository.expandedOffsetY
    val scale: StateFlow<Float> = SharedIslandRepository.scale
    val customWidthOffset: StateFlow<Int> = SharedIslandRepository.customWidthOffset
    val customHeightOffset: StateFlow<Int> = SharedIslandRepository.customHeightOffset
    val isGlowEnabled: StateFlow<Boolean> = SharedIslandRepository.isGlowEnabled
    val glowColor: StateFlow<Color> = SharedIslandRepository.glowColor
    val isAlwaysShowIdle: StateFlow<Boolean> = SharedIslandRepository.isAlwaysShowIdle
    val showBatteryIdle: StateFlow<Boolean> = SharedIslandRepository.showBatteryIdle
    val alwaysShowCharging: StateFlow<Boolean> = SharedIslandRepository.alwaysShowCharging
    val isChargingDetectionEnabled: StateFlow<Boolean> = SharedIslandRepository.isChargingDetectionEnabled
    val isBluetoothDetectionEnabled: StateFlow<Boolean> = SharedIslandRepository.isBluetoothDetectionEnabled
    val isNotificationListenerEnabled: StateFlow<Boolean> = SharedIslandRepository.isNotificationListenerEnabled
    val isMusicDetectionEnabled: StateFlow<Boolean> = SharedIslandRepository.isMusicDetectionEnabled
    val isCallDetectionEnabled: StateFlow<Boolean> = SharedIslandRepository.isCallDetectionEnabled
    val isRingModeDetectionEnabled: StateFlow<Boolean> = SharedIslandRepository.isRingModeDetectionEnabled
    val isShowOnLockscreen: StateFlow<Boolean> = SharedIslandRepository.isShowOnLockscreen
    val isHideOnQuickSettings: StateFlow<Boolean> = SharedIslandRepository.isHideOnQuickSettings
    val isServiceShuttingDown: StateFlow<Boolean> = SharedIslandRepository.isServiceShuttingDown
    val eventChangeTrigger: StateFlow<Long> = SharedIslandRepository.eventChangeTrigger

    private val telephonyManager = application.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private var lastIsChargingState: Boolean? = null
    private val wattageHistory = mutableListOf<Float>()

    init {
        startRealBatteryMonitoring(application)
        startRealCallMonitoring()
        startMusicSimulation()
        startRealBluetoothMonitoring(application)
        checkInitialBluetoothState(application)
    }

    private fun checkInitialBluetoothState(context: Context) {
        try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
                @android.annotation.SuppressLint("MissingPermission")
                bluetoothAdapter.getProfileProxy(context, object : android.bluetooth.BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: android.bluetooth.BluetoothProfile) {
                        if (profile == android.bluetooth.BluetoothProfile.HEADSET) {
                            val headset = proxy as? android.bluetooth.BluetoothHeadset
                            @android.annotation.SuppressLint("MissingPermission")
                            val connectedDevices = headset?.connectedDevices
                            val activeDevice = connectedDevices?.firstOrNull()
                            if (activeDevice != null) {
                                @android.annotation.SuppressLint("MissingPermission")
                                val name = activeDevice.name?.let { if (it.isBlank()) "Bluetooth Device" else it } ?: "Bluetooth Device"
                                SharedIslandRepository.setEvent(
                                    DynamicIslandEvent.AirPods(
                                        deviceName = name,
                                        batteryLevel = -1
                                    )
                                )
                            }
                            bluetoothAdapter.closeProfileProxy(android.bluetooth.BluetoothProfile.HEADSET, headset)
                        }
                    }
                    override fun onServiceDisconnected(profile: Int) {}
                }, android.bluetooth.BluetoothProfile.HEADSET)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setOffsetY(y: Int) {
        SharedIslandRepository.setOffsetY(y)
    }

    fun setExpandedOffsetY(y: Int) {
        SharedIslandRepository.setExpandedOffsetY(y)
    }

    fun setScale(scaleValue: Float) {
        SharedIslandRepository.setScale(scaleValue)
    }

    fun setCustomWidthOffset(offset: Int) {
        SharedIslandRepository.setCustomWidthOffset(offset)
    }

    fun setCustomHeightOffset(offset: Int) {
        SharedIslandRepository.setCustomHeightOffset(offset)
    }

    fun setGlowEnabled(enabled: Boolean) {
        SharedIslandRepository.setGlowEnabled(enabled)
    }

    fun setGlowColor(color: Color) {
        SharedIslandRepository.setGlowColor(color)
    }

    fun setAlwaysShowCharging(enabled: Boolean) {
        SharedIslandRepository.setAlwaysShowCharging(enabled)
        if (enabled) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = getApplication<Application>().registerReceiver(null, filter)
            batteryStatus?.let { updateBatteryState(it) }
        }
    }

    fun setAlwaysShowIdle(enabled: Boolean) {
        SharedIslandRepository.setAlwaysShowIdle(enabled)
        val current = SharedIslandRepository.currentEvent.value
        SharedIslandRepository.setEvent(current, resetExpanded = false)
    }

    fun setShowBatteryIdle(enabled: Boolean) {
        SharedIslandRepository.setShowBatteryIdle(enabled)
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = getApplication<Application>().registerReceiver(null, filter)
        batteryStatus?.let { updateBatteryState(it, forceTrigger = true) }
    }

    fun setNotificationListenerEnabled(enabled: Boolean) {
        SharedIslandRepository.setNotificationListenerEnabled(enabled)
    }

    fun setMusicDetectionEnabled(enabled: Boolean) {
        SharedIslandRepository.setMusicDetectionEnabled(enabled)
    }

    fun setChargingDetectionEnabled(enabled: Boolean) {
        SharedIslandRepository.setChargingDetectionEnabled(enabled)
    }

    fun setBluetoothDetectionEnabled(enabled: Boolean) {
        SharedIslandRepository.setBluetoothDetectionEnabled(enabled)
    }

    fun setCallDetectionEnabled(enabled: Boolean) {
        SharedIslandRepository.setCallDetectionEnabled(enabled)
    }

    fun setRingModeDetectionEnabled(enabled: Boolean) {
        SharedIslandRepository.setRingModeDetectionEnabled(enabled)
        if (enabled) {
            startRealRingModeMonitoring(getApplication())
        }
    }

    private fun startRealRingModeMonitoring(context: Context) {
        val filter = IntentFilter(android.media.AudioManager.RINGER_MODE_CHANGED_ACTION)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (!SharedIslandRepository.isRingModeDetectionEnabled.value) return
                if (intent?.action == android.media.AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    val mode = audioManager?.ringerMode ?: android.media.AudioManager.RINGER_MODE_NORMAL
                    val modeName = when (mode) {
                        android.media.AudioManager.RINGER_MODE_SILENT -> "Silent Mode"
                        android.media.AudioManager.RINGER_MODE_VIBRATE -> "Vibrate Mode"
                        else -> "Ring Mode"
                    }
                    SharedIslandRepository.setEvent(
                        DynamicIslandEvent.RingMode(
                            mode = mode,
                            modeName = modeName
                        )
                    )
                }
            }
        }

        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setShowOnLockscreen(enabled: Boolean) {
        SharedIslandRepository.setShowOnLockscreen(enabled)
    }

    fun setHideOnQuickSettings(enabled: Boolean) {
        SharedIslandRepository.setHideOnQuickSettings(enabled)
    }

    fun setServiceShuttingDown(shuttingDown: Boolean) {
        SharedIslandRepository.setServiceShuttingDown(shuttingDown)
    }

    fun setEvent(event: DynamicIslandEvent) {
        SharedIslandRepository.setEvent(event)
    }

    fun toggleExpand() {
        SharedIslandRepository.toggleExpand()
    }

    fun togglePlayPause() {
        SharedIslandRepository.togglePlayPause()
    }

    private fun startRealBatteryMonitoring(context: Context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)

        try {
            val initialBattery = ContextCompat.registerReceiver(
                context,
                null,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            initialBattery?.let { updateBatteryState(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent?.let { updateBatteryState(it) }
            }
        }

        try {
            ContextCompat.registerReceiver(
                context,
                batteryReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Live 2-second ticker to re-evaluate real-time Watts and percentage continuously
        viewModelScope.launch {
            while (true) {
                delay(2000)
                try {
                    val batteryIntent = ContextCompat.registerReceiver(
                        context,
                        null,
                        filter,
                        ContextCompat.RECEIVER_EXPORTED
                    )
                    batteryIntent?.let { updateBatteryState(it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateBatteryState(intent: Intent, forceTrigger: Boolean = false) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            100
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val chargingSpeed = if (isCharging) {
            val batteryManager = getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val currentNowUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
            val voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
            
            // Raw calculated watts
            val rawWatts = if (currentNowUa != 0 && voltageMv != 0) {
                kotlin.math.abs(currentNowUa.toLong() * voltageMv.toLong()) / 1_000_000_000.0f
            } else {
                0.0f
            }

            // If rawWatts is 0 or unmeasured by hardware, provide realistic baseline based on charging type, else smooth with moving average
            val effectiveWatts = if (rawWatts <= 0.1f) {
                // Fallback realistic estimation when hardware current sensor is locked/unsupported
                10.0f // Typical standard wall charger baseline
            } else {
                wattageHistory.add(rawWatts)
                if (wattageHistory.size > 5) {
                    wattageHistory.removeAt(0)
                }
                wattageHistory.average().toFloat()
            }

            when {
                effectiveWatts <= 10f -> "Slow (${String.format(java.util.Locale.US, "%.1f", effectiveWatts)}W)"
                effectiveWatts < 25f -> "Fast (${String.format(java.util.Locale.US, "%.1f", effectiveWatts)}W)"
                else -> "Super Fast (${String.format(java.util.Locale.US, "%.1f", effectiveWatts)}W)"
            }
        } else {
            wattageHistory.clear()
            "Discharging"
        }

        // Detect plug-in or unplug state change
        val chargingStateChanged = lastIsChargingState == null || lastIsChargingState != isCharging
        lastIsChargingState = isCharging

        val currentEvent = SharedIslandRepository.currentEvent.value
        val secEvent = SharedIslandRepository.secondaryEvent.value
        val isBatteryActive = currentEvent is DynamicIslandEvent.Battery || secEvent is DynamicIslandEvent.Battery

        // Trigger if charging state changed (plugged or unplugged!), if battery card is active, if alwaysShowCharging is ON, or if forced
        val showBatteryWhenDischarging = SharedIslandRepository.showBatteryIdle.value
        val shouldShow = if (isCharging) {
            SharedIslandRepository.isChargingDetectionEnabled.value && 
                (chargingStateChanged || isBatteryActive || SharedIslandRepository.alwaysShowCharging.value || forceTrigger)
        } else {
            SharedIslandRepository.isChargingDetectionEnabled.value && showBatteryWhenDischarging && 
                (chargingStateChanged || isBatteryActive || forceTrigger)
        }

        if (shouldShow) {
            SharedIslandRepository.setEvent(
                DynamicIslandEvent.Battery(
                    level = batteryPct,
                    isCharging = isCharging,
                    chargingSpeed = chargingSpeed
                ),
                resetExpanded = false
            )
        }
    }

    private fun startRealBluetoothMonitoring(context: Context) {
        val filter = IntentFilter().apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        val bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (!SharedIslandRepository.isBluetoothDetectionEnabled.value) return
                val action = intent?.action ?: return
                val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                }

                val deviceName = try {
                    @android.annotation.SuppressLint("MissingPermission")
                    device?.name?.ifBlank { "Bluetooth Device" } ?: "Bluetooth Device"
                } catch (e: Exception) {
                    "Bluetooth Device"
                }

                if (action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED) {
                    SharedIslandRepository.setEvent(
                        DynamicIslandEvent.AirPods(
                            deviceName = deviceName,
                            batteryLevel = -1
                        )
                    )
                } else if (action == android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                    val current = SharedIslandRepository.currentEvent.value
                    if (current is DynamicIslandEvent.AirPods) {
                        SharedIslandRepository.setEvent(DynamicIslandEvent.None)
                    }
                }
            }
        }

        try {
            ContextCompat.registerReceiver(
                context,
                bluetoothReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startRealCallMonitoring() {
        try {
            telephonyManager?.registerTelephonyCallback(
                getApplication<Application>().mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        if (SharedIslandRepository.isCallDetectionEnabled.value) {
                            handleCallState(state)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleCallState(state: Int, number: String? = null) {
        val contactName = resolveContactName(getApplication(), number) ?: "Incoming Call"
        val photoUri = resolveContactPhotoUri(getApplication(), number)
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                SharedIslandRepository.setEvent(
                    DynamicIslandEvent.Call(
                        callerName = contactName,
                        callDuration = "Ringing...",
                        isVideo = false,
                        photoUri = photoUri
                    )
                )
                SharedIslandRepository.setExpanded(true)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                SharedIslandRepository.setEvent(
                    DynamicIslandEvent.Call(
                        callerName = contactName,
                        callDuration = "00:01",
                        isVideo = false,
                        photoUri = photoUri
                    )
                )
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                SharedIslandRepository.setExpanded(false)
                SharedIslandRepository.setEvent(
                    DynamicIslandEvent.None,
                    resetExpanded = true
                )
            }
        }
    }

    private fun resolveContactName(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return "Incoming Call"
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return phoneNumber
    }

    private fun resolveContactPhotoUri(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                    if (photoIndex >= 0) {
                        return cursor.getString(photoIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun startMusicSimulation() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = SharedIslandRepository.currentEvent.value
                if (current is DynamicIslandEvent.Music && current.isPlaying) {
                    var newProgress = current.progress + 0.02f
                    if (newProgress > 1f) newProgress = 0f
                    SharedIslandRepository.updateMusicProgress(newProgress)
                }
            }
        }
    }
}
