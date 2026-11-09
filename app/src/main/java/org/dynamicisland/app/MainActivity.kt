package org.dynamicisland.app

import android.app.Application
import android.app.NotificationManager
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppChoice(val pkg: String, val label: String)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as IslandApplication
    val settings =
        app.settings.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            IslandSettings(),
        )
    val apps = MutableStateFlow<List<AppChoice>>(emptyList())
    val permission = MutableStateFlow(PermissionState(false, false))
    val connected = app.connected

    init {
        refresh()
        viewModelScope.launch {
            apps.value =
                withContext(Dispatchers.IO) {
                    val pm = app.packageManager
                    val launcher =
                        pm.queryIntentActivities(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                                0,
                            )
                            .map { it.activityInfo.packageName }
                    val saved = app.settings.settings.first()
                    (launcher + saved.apps)
                        .distinct()
                        .filter { it != app.packageName }
                        .mapNotNull { pkg ->
                            runCatching {
                                    AppChoice(
                                        pkg,
                                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
                                            .toString(),
                                    )
                                }
                                .getOrNull()
                        }
                        .sortedBy { it.label.lowercase() }
                }
        }
    }

    fun refresh() {
        permission.value =
            PermissionState(
                Settings.canDrawOverlays(app),
                app.getSystemService(NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(
                        ComponentName(app, IslandNotificationListener::class.java)
                    ),
            )
        app.refresh()
    }

    fun update(f: (IslandSettings) -> IslandSettings) {
        viewModelScope.launch { app.settings.update(f) }
    }

    fun reset() {
        viewModelScope.launch { app.settings.reset() }
    }

    fun preview() = app.preview()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme =
                    darkColorScheme(
                        primary = Color(0xff82e0c0),
                        background = Color(0xff101214),
                        surface = Color(0xff1b1e21),
                    )
            ) {
                Surface(Modifier.fillMaxSize()) { SettingsScreen() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as IslandApplication).refresh()
    }

    @Composable
    private fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
        val s by vm.settings.collectAsStateWithLifecycle()
        val apps by vm.apps.collectAsStateWithLifecycle()
        val permission by vm.permission.collectAsStateWithLifecycle()
        val connected by vm.connected.collectAsStateWithLifecycle()
        var appSearch by remember { mutableStateOf("") }
        var showApps by remember { mutableStateOf(false) }
        var reset by remember { mutableStateOf(false) }
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycle) {
            val obs =
                androidx.lifecycle.LifecycleEventObserver { _, e ->
                    if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refresh()
                }
            lifecycle.lifecycle.addObserver(obs)
            onDispose { lifecycle.lifecycle.removeObserver(obs) }
        }
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Dynamic Island", style = MaterialTheme.typography.headlineLarge)
            Text(
                "A quieter place for your notifications.",
                color = MaterialTheme.colorScheme.primary,
            )
            Toggle("Enable Dynamic Island", s.enabled) {
                vm.update { old -> old.copy(enabled = it) }
            }
            Text(
                if (!permission.overlay) "Overlay permission is required."
                else if (!permission.listener) "Notification access is required."
                else if (!connected)
                    "Waiting for Android to connect the listener. Try toggling notification access."
                else "Ready • notifications stay on this device"
            )
            Button(
                onClick = {
                    openSettings(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        )
                    )
                },
                Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (permission.overlay) "Overlay permission granted"
                    else "Grant overlay permission"
                )
            }
            Button(
                onClick = { openSettings(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (permission.listener) "Manage notification access"
                    else "Grant notification access"
                )
            }
            Text(
                "Android grants access to all notifications. Only apps you select below are eligible; ongoing, media, alarm, event and reminder notifications are excluded.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Text("Applications", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { showApps = !showApps }) {
                Text("${s.apps.size} selected • ${if(showApps) "Hide" else "Choose apps"}")
            }
            if (showApps) {
                OutlinedTextField(
                    value = appSearch,
                    onValueChange = { appSearch = it },
                    label = { Text("Find an app") },
                    modifier = Modifier.fillMaxWidth(),
                )
                apps
                    .filter {
                        it.label.contains(appSearch, true) || it.pkg.contains(appSearch, true)
                    }
                    .forEach { app ->
                        Toggle(app.label, app.pkg in s.apps) { checked ->
                            vm.update {
                                it.copy(
                                    apps = if (checked) it.apps + app.pkg else it.apps - app.pkg
                                )
                            }
                        }
                    }
            }
            HorizontalDivider()
            Text("Appearance & motion", style = MaterialTheme.typography.titleLarge)
            Text(
                "Cutout alignment varies by device. Adjust these values in each orientation if needed.",
                style = MaterialTheme.typography.bodySmall,
            )
            SettingSlider("Horizontal offset: ${s.x} dp", s.x.toFloat(), -160f..160f) { v ->
                vm.update { it.copy(x = v.toInt()) }
            }
            SettingSlider("Vertical offset: ${s.y} dp", s.y.toFloat(), 0f..160f) { v ->
                vm.update { it.copy(y = v.toInt()) }
            }
            SettingSlider("Preview width: ${s.width} dp", s.width.toFloat(), 180f..360f) { v ->
                vm.update { it.copy(width = v.toInt()) }
            }
            SettingSlider("Animation speed: ${"%.1f".format(s.speed)}×", s.speed, 0.5f..2f) { v ->
                vm.update { it.copy(speed = v) }
            }
            SettingSlider(
                "Preview duration: ${s.duration} seconds",
                s.duration.toFloat(),
                2f..15f,
            ) { v ->
                vm.update { it.copy(duration = v.toInt()) }
            }
            Text("Haptics", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Off", "Subtle", "Standard").forEachIndexed { i, label ->
                    FilterChip(
                        selected = s.haptics == i,
                        onClick = { vm.update { it.copy(haptics = i) } },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "Respects the system touch feedback setting. Strength depends on device hardware.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Text("Privacy", style = MaterialTheme.typography.titleLarge)
            Toggle("Hide island while device is locked", s.hideLocked) { v ->
                vm.update { it.copy(hideLocked = v) }
            }
            Toggle("Hide notification text & actions", s.hideContent) { v ->
                vm.update { it.copy(hideContent = v) }
            }
            Text(
                "On the lock screen, content and actions are always hidden. Secret notifications are never shown. Screen-off clears the presentation when lock protection applies. No notification content is saved to disk.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { vm.preview() },
                enabled = s.enabled && permission.canPresent(true) && connected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test island preview")
            }
            Text(
                "Tap to open • hold to expand • swipe up to dismiss • swipe sideways to browse. Dismissal removes the island presentation, not the system notification.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = {
                    openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Battery optimization settings")
            }
            Text(
                "If your manufacturer stops the listener, allow background operation in system battery settings. This app does not hold wake locks or run a foreground service.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { reset = true }) { Text("Reset all settings") }
            Text(
                "Open source • MIT • No internet permission",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (reset)
            AlertDialog(
                onDismissRequest = { reset = false },
                title = { Text("Reset settings?") },
                text = {
                    Text("This disables the island and clears your app selections and preferences.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            vm.reset()
                            reset = false
                        }
                    ) {
                        Text("Reset")
                    }
                },
                dismissButton = { TextButton(onClick = { reset = false }) { Text("Cancel") } },
            )
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure {
                android.widget.Toast.makeText(
                        this,
                        "This settings page is unavailable on your device",
                        android.widget.Toast.LENGTH_LONG,
                    )
                    .show()
            }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = change)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    change: (Float) -> Unit,
) {
    Column {
        Text(label)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = change,
            valueRange = range,
        )
    }
}
