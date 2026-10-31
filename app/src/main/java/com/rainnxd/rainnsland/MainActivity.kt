package com.rainnxd.rainnsland

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.net.toUri
import com.rainnxd.rainnsland.ui.screens.DynamicIslandDemoScreen
import com.rainnxd.rainnsland.ui.theme.RainnslandTheme
import com.rainnxd.rainnsland.viewmodel.DynamicIslandViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: DynamicIslandViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Transparent status bar and navigation bar to eliminate white bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // Automatically request overlay permission on launch if not already granted
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivity(intent)
        }

        setContent {
            RainnslandTheme {
                DynamicIslandDemoScreen(viewModel = viewModel)
            }
        }
    }
}
