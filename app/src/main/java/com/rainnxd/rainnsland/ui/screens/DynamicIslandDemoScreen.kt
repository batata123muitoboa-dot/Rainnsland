package com.rainnxd.rainnsland.ui.screens

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.rainnxd.rainnsland.model.DynamicIslandEvent
import com.rainnxd.rainnsland.service.DynamicIslandService
import com.rainnxd.rainnsland.viewmodel.DynamicIslandViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicIslandDemoScreen(
    viewModel: DynamicIslandViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val offsetY by viewModel.offsetY.collectAsState()
    val expandedOffsetY by viewModel.expandedOffsetY.collectAsState()
    val scale by viewModel.scale.collectAsState()
    val customWidthOffset by viewModel.customWidthOffset.collectAsState()
    val customHeightOffset by viewModel.customHeightOffset.collectAsState()
    val isGlowEnabled by viewModel.isGlowEnabled.collectAsState()
    val glowColor by viewModel.glowColor.collectAsState()
    val isAlwaysShowIdle by viewModel.isAlwaysShowIdle.collectAsState()
    val showBatteryIdle by viewModel.showBatteryIdle.collectAsState()
    val alwaysShowCharging by viewModel.alwaysShowCharging.collectAsState()
    val isNotificationListenerEnabled by viewModel.isNotificationListenerEnabled.collectAsState()
    val isCallDetectionEnabled by viewModel.isCallDetectionEnabled.collectAsState()
    val isRingModeDetectionEnabled by viewModel.isRingModeDetectionEnabled.collectAsState()
    val isChargingDetectionEnabled by viewModel.isChargingDetectionEnabled.collectAsState()
    val isBluetoothDetectionEnabled by viewModel.isBluetoothDetectionEnabled.collectAsState()
    val isShowOnLockscreen by viewModel.isShowOnLockscreen.collectAsState()
    val isHideOnQuickSettings by viewModel.isHideOnQuickSettings.collectAsState()

    val hasOverlayPermission = Settings.canDrawOverlays(context)
    val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    var isServiceRunning by remember { mutableStateOf(isServiceRunningCheck(context, DynamicIslandService::class.java)) }

    // Robust Accessibility check using AccessibilityManager for Android 14+
    var isAccessibilityActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            val enabledServices = accessibilityManager?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK) ?: emptyList()
            isAccessibilityActive = enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
            delay(1000)
        }
    }

    // Live slider states
    var punchHoleYOffset by remember { mutableFloatStateOf(offsetY.toFloat()) }
    var expandedYOffset by remember { mutableFloatStateOf(expandedOffsetY.toFloat()) }
    var widthSliderValue by remember { mutableFloatStateOf(customWidthOffset.toFloat()) }
    var heightSliderValue by remember { mutableFloatStateOf(customHeightOffset.toFloat()) }

    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Auto-start floating service if overlay permission is granted and service is not running
    LaunchedEffect(hasOverlayPermission) {
        if (hasOverlayPermission && !isServiceRunningCheck(context, DynamicIslandService::class.java)) {
            val serviceIntent = Intent(context, DynamicIslandService::class.java)
            context.startForegroundService(serviceIntent)
            isServiceRunning = true
        }
    }

    val scaleOptions = listOf(
        "75%" to 0.75f,
        "100%" to 1.0f,
        "125%" to 1.25f,
        "150%" to 1.5f,
        "200%" to 2.0f
    )

    val glowColorOptions = listOf(
        "Cyan" to Color(0xFF00E5FF),
        "Neon Pink" to Color(0xFFFF007F),
        "Electric Blue" to Color(0xFF0088FF),
        "Neon Green" to Color(0xFF00FF66),
        "Gold" to Color(0xFFFFD700)
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Rainnsland Configurations",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Service Controls Card
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Service Enabled",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Overlay punch hole island on system",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    isServiceRunning = true
                                    viewModel.setServiceShuttingDown(false)
                                    val serviceIntent = Intent(context, DynamicIslandService::class.java)
                                    context.startForegroundService(serviceIntent)
                                } else {
                                    scope.launch {
                                        viewModel.setServiceShuttingDown(true)
                                        delay(260)
                                        val serviceIntent = Intent(context, DynamicIslandService::class.java)
                                        context.stopService(serviceIntent)
                                        isServiceRunning = false
                                        viewModel.setServiceShuttingDown(false)
                                    }
                                }
                            }
                        )
                    }

                    if (!hasOverlayPermission) {
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    "package:${context.packageName}".toUri()
                                )
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Grant Overlay Permission")
                        }
                    }

                    if (!hasPhoneStatePermission) {
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Grant Phone Calls Permission")
                        }
                    }

                    // Ultra-Priority Accessibility Overlay Button for Android 14 (Hidden if already active)
                    if (!isAccessibilityActive) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Grant Accessibility Service (Android 14 Priority)")
                        }
                    }

                    // Request Ignore Battery Optimizations Button (Hidden if already ignored)
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
                    
                    if (!isIgnoringBattery) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = "package:${context.packageName}".toUri()
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Disable Battery Restrictions (Keep Alive)")
                        }
                    }
                }
            }

            // Event Presets Card
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trigger Event Presets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                com.rainnxd.rainnsland.model.SharedIslandRepository.clearEvent()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        ) {
                            Text(text = "Clear Preset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.setEvent(
                                    DynamicIslandEvent.Music(
                                        songTitle = "Starboy",
                                        artist = "The Weeknd",
                                        albumArtColor = 0xFF8A2BE2,
                                        isPlaying = true,
                                        progress = 0.45f
                                    )
                                )
                            }
                        ) {
                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Music")
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.setEvent(
                                    DynamicIslandEvent.Call(
                                        callerName = "Neymar Jr",
                                        callDuration = "00:24",
                                        isVideo = false
                                    )
                                )
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Call")
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.setEvent(
                                    DynamicIslandEvent.Timer(
                                        timeRemaining = "04:59",
                                        progress = 0.7f,
                                        label = "Timer"
                                    )
                                )
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Timer")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.setEvent(
                                    DynamicIslandEvent.Battery(
                                        level = 88,
                                        isCharging = true
                                    )
                                )
                            }
                        ) {
                            Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Battery")
                        }

                        FilledTonalButton(
                            onClick = {
                                viewModel.setEvent(
                                    DynamicIslandEvent.AirPods(
                                        deviceName = "AirPods Pro",
                                        batteryLevel = 94
                                    )
                                )
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "AirPods")
                        }
                    }
                }
            }

            // Real-time System Detection Toggles Card
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Real-Time System Interception",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Detect System Notifications Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Detect System Notifications",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Intercept incoming WhatsApp, SMS, and apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isNotificationListenerEnabled,
                            onCheckedChange = { checked ->
                                viewModel.setNotificationListenerEnabled(checked)
                                if (checked) {
                                    try {
                                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        )
                    }

                    // Detect Incoming Calls Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Detect Incoming Calls",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Show incoming phone call cards",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isCallDetectionEnabled,
                            onCheckedChange = { viewModel.setCallDetectionEnabled(it) }
                        )
                    }

                    // Detect Charging Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Detect Charging",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Show charging cards and live wattage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isChargingDetectionEnabled,
                            onCheckedChange = { viewModel.setChargingDetectionEnabled(it) }
                        )
                    }

                    // Detect Bluetooth Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Detect Bluetooth",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Show connected Bluetooth audio devices",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBluetoothDetectionEnabled,
                            onCheckedChange = { checked ->
                                viewModel.setBluetoothDetectionEnabled(checked)
                                if (checked && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                        permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                    }
                                }
                            }
                        )
                    }

                    // Detect Ring Mode Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Detect Ring Mode",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Show alert when ringer mode changes (Silent, Vibrate, Ring)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRingModeDetectionEnabled,
                            onCheckedChange = { viewModel.setRingModeDetectionEnabled(it) }
                        )
                    }
                }
            }

            // Direct Calibration & Customization Settings Card (Live Updates)
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Calibration & Customization",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Hide On Quick Settings Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide On Quick Settings",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Smoothly shrink into camera when pulling down status bar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isHideOnQuickSettings,
                            onCheckedChange = { viewModel.setHideOnQuickSettings(it) }
                        )
                    }

                    // Show On Lockscreen Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show On Lockscreen",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Display dynamic island over lock screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isShowOnLockscreen,
                            onCheckedChange = { viewModel.setShowOnLockscreen(it) }
                        )
                    }

                    // Always Show When Idle Toggle (Default false)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Always Show When Idle",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Keep island visible even when empty",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAlwaysShowIdle,
                            onCheckedChange = { viewModel.setAlwaysShowIdle(it) }
                        )
                    }

                    // Show Battery When Discharging Toggle (Default false)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Show Battery When Discharging",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Display battery percentage in idle state",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showBatteryIdle,
                            onCheckedChange = { viewModel.setShowBatteryIdle(it) }
                        )
                    }

                    // Always Show While Charging Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Always Show While Charging",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Keep charging event visible without auto-dismiss",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alwaysShowCharging,
                            onCheckedChange = { viewModel.setAlwaysShowCharging(it) }
                        )
                    }

                    // Orbiting Glow Animation Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Orbiting Glow Animation",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Neon pill traveling on event changes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isGlowEnabled,
                            onCheckedChange = { viewModel.setGlowEnabled(it) }
                        )
                    }

                    // Glow Color Selection
                    Text(
                        text = "Glow Color Customization",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        glowColorOptions.forEach { (label, color) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setGlowColor(color) }
                                    .padding(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(
                                            if (glowColor == color) Modifier.padding(2.dp) else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (glowColor == color) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black)
                                        )
                                    }
                                }
                                Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Scale Selection (75%, 100%, 125%, 150%, 200%)
                    Text(
                        text = "Scale Preset: ${(scale * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        scaleOptions.forEach { (label, value) ->
                            FilterChip(
                                selected = (scale == value),
                                onClick = { viewModel.setScale(value) },
                                label = { Text(text = label, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Punch Hole Y-Offset Calibration (Live Real-Time Updates)
                    Text(
                        text = "Compact Position (Punch Hole Y-Offset): ${punchHoleYOffset.toInt()} dp",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = punchHoleYOffset,
                        onValueChange = { value ->
                            punchHoleYOffset = value
                            viewModel.setOffsetY(value.toInt())
                        },
                        valueRange = -30f..100f
                    )

                    // Expanded Menu Vertical Drop (Live Real-Time Updates)
                    Text(
                        text = "Expanded Menu Vertical Drop: ${expandedYOffset.toInt()} dp",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = expandedYOffset,
                        onValueChange = { value ->
                            expandedYOffset = value
                            viewModel.setExpandedOffsetY(value.toInt())
                        },
                        valueRange = -20f..80f
                    )

                    // Custom Width Slider (Live Real-Time Updates)
                    Text(
                        text = "Width Offset: ${widthSliderValue.toInt()} dp",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = widthSliderValue,
                        onValueChange = { value ->
                            widthSliderValue = value
                            viewModel.setCustomWidthOffset(value.toInt())
                        },
                        valueRange = -50f..100f
                    )

                    // Custom Height Slider (Live Real-Time Updates)
                    Text(
                        text = "Height Offset: ${heightSliderValue.toInt()} dp",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = heightSliderValue,
                        onValueChange = { value ->
                            heightSliderValue = value
                            viewModel.setCustomHeightOffset(value.toInt())
                        },
                        valueRange = -30f..50f
                    )
                }
            }
        }
    }
}

fun isServiceRunningCheck(context: Context, serviceClass: Class<*>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    @Suppress("DEPRECATION")
    for (service in manager?.getRunningServices(Int.MAX_VALUE) ?: emptyList()) {
        if (serviceClass.name == service.service.className) {
            return true
        }
    }
    return false
}
