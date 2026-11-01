package com.rainnxd.rainnsland.ui.components

import android.net.Uri
import android.annotation.SuppressLint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.sharp.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rainnxd.rainnsland.model.DynamicIslandEvent
import com.rainnxd.rainnsland.model.SharedIslandRepository
import kotlinx.coroutines.delay

@Composable
fun DynamicIsland(
    event: DynamicIslandEvent,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onTogglePlayPause: (() -> Unit)? = null
) {
    val scale by SharedIslandRepository.scale.collectAsState()
    val customWidthOffset by SharedIslandRepository.customWidthOffset.collectAsState()
    val customHeightOffset by SharedIslandRepository.customHeightOffset.collectAsState()
    val expandedOffsetY by SharedIslandRepository.expandedOffsetY.collectAsState()
    val isGlowEnabled by SharedIslandRepository.isGlowEnabled.collectAsState()
    val glowColor by SharedIslandRepository.glowColor.collectAsState()
    val isAlwaysShowIdle by SharedIslandRepository.isAlwaysShowIdle.collectAsState()
    val isHideOnQuickSettings by SharedIslandRepository.isHideOnQuickSettings.collectAsState()
    val isQuickSettingsOpen by SharedIslandRepository.isQuickSettingsOpen.collectAsState()
    val isShowOnLockscreen by SharedIslandRepository.isShowOnLockscreen.collectAsState()
    val isServiceShuttingDown by SharedIslandRepository.isServiceShuttingDown.collectAsState()
    val eventChangeTrigger by SharedIslandRepository.eventChangeTrigger.collectAsState()

    var orbitProgress by remember { mutableStateOf(0f) }
    var glowAlpha by remember { mutableStateOf(0f) }

    // Trigger glowing pill particle ONLY when event changes (never on expand/collapse)
    LaunchedEffect(eventChangeTrigger) {
        if (isGlowEnabled && eventChangeTrigger > 0L) {
            glowAlpha = 1f
            val startTime = System.currentTimeMillis()
            val duration = 1200L
            while (glowAlpha > 0f) {
                val elapsed = System.currentTimeMillis() - startTime
                val fraction = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                orbitProgress = fraction * 1f // Exactly 1 full lap around perimeter
                glowAlpha = (1f - fraction)
                delay(16)
            }
        }
    }

    var isMuted by remember { mutableStateOf(false) }

    val compactWidthDp = (when (event) {
        is DynamicIslandEvent.Music -> 120.dp
        is DynamicIslandEvent.Call -> 130.dp
        is DynamicIslandEvent.Timer -> 110.dp
        is DynamicIslandEvent.Battery -> 110.dp
        is DynamicIslandEvent.AirPods -> 115.dp
        is DynamicIslandEvent.RingMode -> 120.dp
        is DynamicIslandEvent.Notification -> 120.dp
        is DynamicIslandEvent.None -> 90.dp
    }.value * scale + customWidthOffset).dp

    val compactHeightDp = (36.dp.value * scale + customHeightOffset).dp

    val expandedWidthDp = (340.dp.value * scale + customWidthOffset).dp
    val expandedHeightDp = ((when (event) {
        is DynamicIslandEvent.Music -> 145.dp
        is DynamicIslandEvent.Call -> 130.dp
        is DynamicIslandEvent.Timer -> 110.dp
        is DynamicIslandEvent.Battery -> 110.dp
        is DynamicIslandEvent.AirPods -> 110.dp
        is DynamicIslandEvent.RingMode -> 110.dp
        is DynamicIslandEvent.Notification -> 135.dp
        is DynamicIslandEvent.None -> 80.dp
    }).value * scale + customHeightOffset).dp

    // Should this island be visible or shrink into camera?
    val localContext = LocalContext.current
    val keyguardManager = localContext.getSystemService(android.content.Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
    val isLocked = keyguardManager?.isKeyguardLocked == true
    val shouldBeVisible = !(event is DynamicIslandEvent.None && !isAlwaysShowIdle && !isExpanded) &&
            !isServiceShuttingDown && !(isHideOnQuickSettings && isQuickSettingsOpen) &&
            !(isLocked && !isShowOnLockscreen)

    val visibilityProgress by animateFloatAsState(
        targetValue = if (shouldBeVisible && !(isLocked && !isShowOnLockscreen)) 1f else 0f,
        animationSpec = tween(
            durationMillis = 240,
            easing = FastOutSlowInEasing
        ),
        label = "island_visibility_progress"
    )

    // 100x butter-smooth spring physics animation for expansion progress
    val progress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 280f
        ),
        label = "island_expand_progress"
    )

    val targetWidth = lerpDp(compactWidthDp, expandedWidthDp, progress)
    val targetHeight = lerpDp(compactHeightDp, expandedHeightDp, progress)

    // Ultra-fluid spring animations for width and height
    val rawWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "current_width"
    )
    val currentHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "current_height"
    )

    // Shrink width into camera punch hole (10dp) when hiding, bloom out when appearing
    val currentWidth = lerpDp(10.dp, rawWidth, visibilityProgress)
    val currentAlpha = visibilityProgress.coerceIn(0f, 1f)

    val currentCorner = lerpDp((20 * scale).dp, (32 * scale).dp, progress)

    // Dynamic touch area: compact when closed, large when expanded
    val touchMinWidth = if (isExpanded) (360 * scale).dp else (150 * scale).dp
    val touchMinHeight = if (isExpanded) (190 * scale).dp else (52 * scale).dp

    // Secondary event (satellite bubble) state
    val secondaryEvent by SharedIslandRepository.secondaryEvent.collectAsState()
    val hasSecondary = secondaryEvent !is DynamicIslandEvent.None

    if (currentAlpha <= 0f) {
        return
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = progress * expandedOffsetY.dp.toPx()
                    transformOrigin = TransformOrigin(0.5f, 0.0f)
                },
            contentAlignment = Alignment.TopCenter
        ) {
            // Solid black capsule with swipe-up gesture detection & no X button!
            Box(
                modifier = Modifier
                    .padding(top = (4 * scale).dp)
                    .width(currentWidth)
                    .height(currentHeight)
                    .alpha(currentAlpha)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isExpanded) {
                            onToggleExpand()
                        }
                    }
                    .pointerInput(isExpanded, event) {
                        if (isExpanded) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount < -15f) { // Swiped UP!
                                    if (event is DynamicIslandEvent.Notification) {
                                        // Dismiss notification on swipe up
                                        SharedIslandRepository.setEvent(DynamicIslandEvent.None)
                                    } else {
                                        // Collapse card on swipe up
                                        onToggleExpand()
                                    }
                                }
                            }
                        }
                    }
                    .drawBehind {
                        if (glowAlpha > 0f && currentAlpha > 0.1f) {
                            val pt = getPointAndAngleOnRoundRect(
                                progress = orbitProgress,
                                width = size.width,
                                height = size.height,
                                radius = currentCorner.toPx()
                            )
                            rotate(degrees = pt.angleDegrees, pivot = pt.offset) {
                                drawOval(
                                    color = glowColor.copy(alpha = glowAlpha * 0.85f),
                                    topLeft = Offset(pt.offset.x - (16 * scale).dp.toPx(), pt.offset.y - (7 * scale).dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size((32 * scale).dp.toPx(), (14 * scale).dp.toPx())
                                )
                                drawOval(
                                    color = Color.White.copy(alpha = glowAlpha),
                                    topLeft = Offset(pt.offset.x - (9 * scale).dp.toPx(), pt.offset.y - (3.5 * scale).dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size((18 * scale).dp.toPx(), (7 * scale).dp.toPx())
                                )
                            }
                        }
                    }
                    .clip(RoundedCornerShape(currentCorner))
                    .background(Color(0xFF000000))
                    .padding(horizontal = lerpDp((10 * scale).dp, (16 * scale).dp, progress), vertical = lerpDp((6 * scale).dp, (12 * scale).dp, progress)),
                contentAlignment = Alignment.TopCenter
            ) {
                if (progress < 0.5f) {
                    // Compact View (fully clean under camera punch hole)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha((1f - progress * 2f).coerceIn(0f, 1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((24 * scale).dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            when (event) {
                                is DynamicIslandEvent.Music -> {
                                    Box(
                                        modifier = Modifier
                                            .size((24 * scale).dp)
                                            .clip(CircleShape)
                                            .background(Color(event.albumArtColor)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (event.albumArtBitmap != null) {
                                            Image(
                                                painter = BitmapPainter(event.albumArtBitmap.asImageBitmap()),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Sharp.Headphones,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size((14 * scale).dp)
                                            )
                                        }
                                    }
                                }
                                is DynamicIslandEvent.Call -> {
                                    Box(
                                        modifier = Modifier
                                            .size((24 * scale).dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size((14 * scale).dp)
                                        )
                                    }
                                }
                                is DynamicIslandEvent.Timer -> {
                                    Box(
                                        modifier = Modifier
                                            .size((24 * scale).dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00838F)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size((14 * scale).dp)
                                        )
                                    }
                                }
                                is DynamicIslandEvent.Battery -> {
                                    Box(
                                        modifier = Modifier
                                            .size((24 * scale).dp)
                                            .clip(CircleShape)
                                            .background(if (event.isCharging) Color(0xFFFFD600) else Color(0xFF00E5FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${event.level}%",
                                            color = Color.Black,
                                            fontSize = (9 * scale).sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                                is DynamicIslandEvent.AirPods -> {
                                    Box(
                                        modifier = Modifier
                                            .size((24 * scale).dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Sharp.Headphones,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size((14 * scale).dp)
                                        )
                                    }
                                }
                                is DynamicIslandEvent.RingMode -> {
                                    val iconVector = when (event.mode) {
                                        android.media.AudioManager.RINGER_MODE_SILENT -> Icons.Default.NotificationsOff
                                        android.media.AudioManager.RINGER_MODE_VIBRATE -> Icons.Default.Vibration
                                        else -> Icons.Default.Notifications
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size((24 * scale).dp)
                                            .clip(CircleShape)
                                            .background(Color.DarkGray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size((14 * scale).dp)
                                        )
                                    }
                                }
                                is DynamicIslandEvent.Notification -> {
                                    val appIconPainter = rememberAppIconPainter(event.packageName)
                                    Box(
                                        modifier = Modifier
                                            .size((24 * scale).dp)
                                            .clip(CircleShape)
                                            .background(Color.DarkGray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (appIconPainter != null) {
                                            Image(
                                                painter = appIconPainter,
                                                contentDescription = null,
                                                modifier = Modifier.size((18 * scale).dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size((14 * scale).dp)
                                            )
                                        }
                                    }
                                }
                                is DynamicIslandEvent.None -> {
                                    Box(
                                        modifier = Modifier
                                            .size((10 * scale).dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF111111))
                                    )
                                }
                            }

                            when (event) {
                                is DynamicIslandEvent.Music -> {
                                    MiniAudioWaveform(isPlaying = event.isPlaying, scale = scale)
                                }
                                is DynamicIslandEvent.Call -> {
                                    Box(
                                        modifier = Modifier
                                            .size((10 * scale).dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E676))
                                    )
                                }
                                is DynamicIslandEvent.Timer -> {
                                    Text(
                                        text = event.timeRemaining,
                                        color = Color(0xFF00E5FF),
                                        fontSize = (12 * scale).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                is DynamicIslandEvent.Battery -> {
                                    Text(
                                        text = if (event.isCharging) "⚡" else "${event.level}%",
                                        color = Color.White,
                                        fontSize = (11 * scale).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                is DynamicIslandEvent.AirPods -> {
                                    Text(
                                        text = "${event.batteryLevel}%",
                                        color = Color.White,
                                        fontSize = (11 * scale).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                is DynamicIslandEvent.RingMode -> {
                                    val icon = when (event.mode) {
                                        android.media.AudioManager.RINGER_MODE_SILENT -> "🔇"
                                        android.media.AudioManager.RINGER_MODE_VIBRATE -> "📳"
                                        else -> "🔔"
                                    }
                                    Text(
                                        text = icon,
                                        color = Color.White,
                                        fontSize = (12 * scale).sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                is DynamicIslandEvent.Notification -> {
                                    Text(
                                        text = event.appName,
                                        color = Color.White,
                                        fontSize = (11 * scale).sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                is DynamicIslandEvent.None -> {
                                    Box(
                                        modifier = Modifier
                                            .size((10 * scale).dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF111111))
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Expanded View (Full details)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(((progress - 0.5f) * 2f).coerceIn(0f, 1f)),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            when (event) {
                                is DynamicIslandEvent.Music -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size((46 * scale).dp)
                                                .clip(RoundedCornerShape((14 * scale).dp))
                                                .background(Color(event.albumArtColor)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (event.albumArtBitmap != null) {
                                                Image(
                                                    painter = BitmapPainter(event.albumArtBitmap.asImageBitmap()),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape((14 * scale).dp)),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Sharp.Headphones,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size((24 * scale).dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width((12 * scale).dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = event.songTitle,
                                                color = Color.White,
                                                fontSize = (15 * scale).sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = event.artist,
                                                color = Color(0xFFAAAAAA),
                                                fontSize = (12 * scale).sp,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    LinearProgressIndicator(
                                        progress = { event.progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((4 * scale).dp)
                                            .clip(CircleShape),
                                        color = Color(event.albumArtColor),
                                        trackColor = Color(0xFF222222)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = {
                                            com.rainnxd.rainnsland.service.RainnNotificationListenerService.skipPrevious()
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.SkipPrevious,
                                                contentDescription = "Previous",
                                                tint = Color.White,
                                                modifier = Modifier.size((24 * scale).dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { 
                                                com.rainnxd.rainnsland.service.RainnNotificationListenerService.playPause()
                                                onTogglePlayPause?.invoke() 
                                            },
                                            modifier = Modifier
                                                .size((40 * scale).dp)
                                                .clip(CircleShape)
                                                .background(Color(event.albumArtColor))
                                        ) {
                                            Icon(
                                                imageVector = if (event.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "PlayPause",
                                                tint = Color.Black,
                                                modifier = Modifier.size((22 * scale).dp)
                                            )
                                        }
                                        IconButton(onClick = {
                                            com.rainnxd.rainnsland.service.RainnNotificationListenerService.skipNext()
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.SkipNext,
                                                contentDescription = "Next",
                                                tint = Color.White,
                                                modifier = Modifier.size((24 * scale).dp)
                                            )
                                        }
                                    }
                                }

                                is DynamicIslandEvent.Call -> {
                                    val contactPhotoPainter = rememberContactPhotoPainter(event.photoUri)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size((46 * scale).dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E676)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (contactPhotoPainter != null) {
                                                Image(
                                                    painter = contactPhotoPainter,
                                                    contentDescription = null,
                                                    modifier = Modifier.size((46 * scale).dp)
                                                )
                                            } else {
                                                Text(
                                                    text = event.callerName.take(1).uppercase(),
                                                    color = Color.Black,
                                                    fontSize = (20 * scale).sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width((12 * scale).dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = event.callerName,
                                                color = Color.White,
                                                fontSize = (16 * scale).sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = event.callDuration,
                                                color = Color(0xFF00E676),
                                                fontSize = (13 * scale).sp,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Active Call • Ongoing",
                                            color = Color(0xFF00E676),
                                            fontSize = (13 * scale).sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                is DynamicIslandEvent.Timer -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = event.label,
                                                color = Color.White,
                                                fontSize = (15 * scale).sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = event.timeRemaining,
                                                color = Color(0xFF00E5FF),
                                                fontSize = (24 * scale).sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }

                                is DynamicIslandEvent.Battery -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "Battery Info",
                                                color = Color.White,
                                                fontSize = (15 * scale).sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (event.isCharging) event.chargingSpeed else "Discharging",
                                                color = Color.Gray,
                                                fontSize = (13 * scale).sp
                                            )
                                        }
                                        Text(
                                            text = "${event.level}%",
                                            color = if (event.isCharging) Color(0xFFFFD600) else Color(0xFF00E5FF),
                                            fontSize = (24 * scale).sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                is DynamicIslandEvent.AirPods -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = event.deviceName,
                                                color = Color.White,
                                                fontSize = (15 * scale).sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (event.batteryLevel >= 0) "Connected • ${event.batteryLevel}%" else "Connected",
                                                color = Color(0xFF00E5FF),
                                                fontSize = (12 * scale).sp
                                            )
                                        }
                                        if (event.batteryLevel >= 0) {
                                            Text(
                                                text = "${event.batteryLevel}%",
                                                color = Color.White,
                                                fontSize = (22 * scale).sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                is DynamicIslandEvent.RingMode -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = event.modeName,
                                                color = Color.White,
                                                fontSize = (15 * scale).sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Sound profile updated",
                                                color = Color(0xFF00E5FF),
                                                fontSize = (12 * scale).sp
                                            )
                                        }
                                        val icon = when (event.mode) {
                                            android.media.AudioManager.RINGER_MODE_SILENT -> "🔇"
                                            android.media.AudioManager.RINGER_MODE_VIBRATE -> "📳"
                                            else -> "🔔"
                                        }
                                        Text(
                                            text = icon,
                                            fontSize = (26 * scale).sp
                                        )
                                    }
                                }

                                is DynamicIslandEvent.Notification -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${event.appName} • ${event.title}",
                                                color = Color(0xFFFF9100),
                                                fontSize = (14 * scale).sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = event.text,
                                                color = Color.White,
                                                fontSize = (13 * scale).sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                is DynamicIslandEvent.None -> {
                                    // Clean minimalist idle view when expanded with no active event
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Rainnsland Idle",
                                            color = Color.Gray,
                                            fontSize = (13 * scale).sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Secondary event satellite bubble floating relative to the center island (collapses into island when expanded)
        val showSecondaryBubble = hasSecondary && !isExpanded
        val secondaryScaleAnim by animateFloatAsState(
            targetValue = if (showSecondaryBubble) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 280f),
            label = "secondary_scale"
        )

        if (secondaryScaleAnim > 0.01f) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = lerpDp(
                            ((compactWidthDp / 2) + (24 * scale).dp),
                            (expandedWidthDp / 2) - (20 * scale).dp,
                            progress
                        ).toPx()
                        translationY = (5 * scale).dp.toPx()
                        scaleX = secondaryScaleAnim * (1f - progress * 0.5f)
                        scaleY = secondaryScaleAnim * (1f - progress * 0.5f)
                        alpha = secondaryScaleAnim * (1f - progress)
                    }
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF000000)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable {
                            SharedIslandRepository.cycleEvents()
                        },
                    contentAlignment = Alignment.Center
                ) {
                // Render secondary event mini icon inside the satellite bubble
                when (val sec = secondaryEvent) {
                    is DynamicIslandEvent.Music -> {
                        if (sec.albumArtBitmap != null) {
                            Image(
                                painter = BitmapPainter(sec.albumArtBitmap.asImageBitmap()),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(imageVector = Icons.Sharp.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    is DynamicIslandEvent.Timer -> {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                    }
                    is DynamicIslandEvent.Battery -> {
                        Text(text = "${sec.level}%", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    is DynamicIslandEvent.Notification -> {
                        val iconPainter = rememberAppIconPainter(sec.packageName)
                        if (iconPainter != null) {
                            Image(painter = iconPainter, contentDescription = null, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    else -> {
                        Box(modifier = Modifier.size(10.dp).background(Color.White, CircleShape))
                    }
                }
              }
            }
        }
    }
}

@Composable
fun rememberAppIconPainter(packageName: String): Painter? {
    val context = LocalContext.current
    val imageBitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = android.graphics.Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    return imageBitmap?.let { BitmapPainter(it) }
}

@Composable
fun rememberContactPhotoPainter(photoUriStr: String?): Painter? {
    val context = LocalContext.current
    val imageBitmap = remember(photoUriStr) {
        if (photoUriStr.isNullOrBlank()) null
        else {
            try {
                val uri = Uri.parse(photoUriStr)
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                bitmap?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    return imageBitmap?.let { BitmapPainter(it) }
}

data class RoundRectPoint(val offset: Offset, val angleDegrees: Float)

fun getPointAndAngleOnRoundRect(progress: Float, width: Float, height: Float, radius: Float): RoundRectPoint {
    val cornerPerimeter = 2f * Math.PI.toFloat() * radius
    val topWidth = (width - 2 * radius).coerceAtLeast(0f)
    val rightHeight = (height - 2 * radius).coerceAtLeast(0f)
    val totalPerimeter = 2 * (topWidth + rightHeight) + cornerPerimeter

    val distance = (progress % 1f) * totalPerimeter
    var current = 0f

    // 1. Top straight edge (left to right)
    if (distance <= current + topWidth) {
        val d = distance - current
        return RoundRectPoint(Offset(radius + d, 0f), 0f)
    }
    current += topWidth

    // 2. Top-right corner arc
    val arcLen = cornerPerimeter / 4f
    if (distance <= current + arcLen) {
        val t = (distance - current) / arcLen
        val angleRad = t * (Math.PI.toFloat() / 2f)
        val x = width - radius + radius * kotlin.math.sin(angleRad)
        val y = radius - radius * kotlin.math.cos(angleRad)
        return RoundRectPoint(Offset(x, y), Math.toDegrees(angleRad.toDouble()).toFloat())
    }
    current += arcLen

    // 3. Right straight edge (top to bottom)
    if (distance <= current + rightHeight) {
        val d = distance - current
        return RoundRectPoint(Offset(width, radius + d), 90f)
    }
    current += rightHeight

    // 4. Bottom-right corner arc
    if (distance <= current + arcLen) {
        val t = (distance - current) / arcLen
        val angleRad = t * (Math.PI.toFloat() / 2f)
        val x = width - radius + radius * kotlin.math.cos(angleRad)
        val y = height - radius + radius * kotlin.math.sin(angleRad)
        return RoundRectPoint(Offset(x, y), 90f + Math.toDegrees(angleRad.toDouble()).toFloat())
    }
    current += arcLen

    // 5. Bottom straight edge (right to left)
    if (distance <= current + topWidth) {
        val d = distance - current
        return RoundRectPoint(Offset(width - radius - d, height), 180f)
    }
    current += topWidth

    // 6. Bottom-left corner arc
    if (distance <= current + arcLen) {
        val t = (distance - current) / arcLen
        val angleRad = t * (Math.PI.toFloat() / 2f)
        val x = radius - radius * kotlin.math.sin(angleRad)
        val y = height - radius + radius * kotlin.math.cos(angleRad)
        return RoundRectPoint(Offset(x, y), 180f + Math.toDegrees(angleRad.toDouble()).toFloat())
    }
    current += arcLen

    // 7. Left straight edge (bottom to top)
    if (distance <= current + rightHeight) {
        val d = distance - current
        return RoundRectPoint(Offset(0f, height - radius - d), 270f)
    }
    current += rightHeight

    // 8. Top-left corner arc
    val t = (distance - current) / arcLen
    val angleRad = t * (Math.PI.toFloat() / 2f)
    val x = radius - radius * kotlin.math.cos(angleRad)
    val y = radius - radius * kotlin.math.sin(angleRad)
    return RoundRectPoint(Offset(x, y), 270f + Math.toDegrees(angleRad.toDouble()).toFloat())
}

fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp {
    return Dp(start.value + (stop.value - start.value) * fraction)
}

@Composable
fun MiniAudioWaveform(isPlaying: Boolean, scale: Float = 1.0f) {
    val alpha by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.3f,
        label = "waveform_alpha"
    )
    Canvas(
        modifier = Modifier
            .width((16 * scale).dp)
            .height((14 * scale).dp)
    ) {
        val barWidth = (3 * scale).dp.toPx()
        val spacing = (2 * scale).dp.toPx()
        val heights = listOf(0.6f, 1f, 0.4f)

        heights.forEachIndexed { index, h ->
            val x = index * (barWidth + spacing)
            val barHeight = size.height * h * alpha
            drawRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(x, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
            )
        }
    }
}
