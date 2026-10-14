package com.netpulse.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.netpulse.app.core.Constants
import com.netpulse.app.service.NetPulseService
import com.netpulse.app.ui.guide.BatteryGuideRoute
import com.netpulse.app.ui.home.HomeRoute
import com.netpulse.app.ui.settings.SettingsRoute
import com.netpulse.app.ui.theme.NetPulseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NetPulseTheme {
                NotificationPermissionHandler()
                MainAppScaffold()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val prefs = NetPulseApp.instance.preferencesRepository.getPreferences()
            if (prefs.isServiceEnabled) {
                val intent = Intent(this@MainActivity, NetPulseService::class.java).apply {
                    action = Constants.ACTION_START_SERVICE
                }
                try {
                    startForegroundService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @Composable
    private fun NotificationPermissionHandler() {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

private enum class NavTab(val title: String, val icon: ImageVector) {
    HOME("Telemetry", Icons.Rounded.Speed),
    SETTINGS("Settings", Icons.Rounded.Settings),
    GUIDE("Battery", Icons.Rounded.BatteryChargingFull)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScaffold() {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(NavTab.HOME.ordinal) }
    val currentTab = NavTab.entries[selectedTabIndex]

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentTab == NavTab.HOME) "NetPulse" else currentTab.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (currentTab) {
            NavTab.HOME -> HomeRoute(modifier = contentModifier)
            NavTab.SETTINGS -> SettingsRoute(modifier = contentModifier)
            NavTab.GUIDE -> BatteryGuideRoute(modifier = contentModifier)
        }
    }
}
