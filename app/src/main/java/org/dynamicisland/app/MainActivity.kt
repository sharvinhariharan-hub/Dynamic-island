package org.dynamicisland.app

import android.app.Application
import android.app.NotificationManager
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        // Do not accept permission/settings taps obscured by another overlay.
        if (
            event.flags and
                (android.view.MotionEvent.FLAG_WINDOW_IS_OBSCURED or
                    android.view.MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0
        )
            return false
        return super.dispatchTouchEvent(event)
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
        var grantOverlay by remember { mutableStateOf(false) }
        var grantNotifications by remember { mutableStateOf(false) }
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
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.padding(vertical = 20.dp)
                        .size(132.dp, 44.dp)
                        .background(Color.Black, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        Modifier.padding(end = 18.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    )
                }
            }
            Text("Dynamic Island", style = MaterialTheme.typography.headlineLarge)
            Text(
                "A quieter place for your notifications.",
                color = MaterialTheme.colorScheme.primary,
            )
            Toggle("Enable Dynamic Island", s.enabled) {
                vm.update { old -> old.copy(enabled = it) }
            }
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            !permission.overlay || !permission.listener -> "Finish setup"
                            !s.enabled -> "Ready when you are"
                            !connected -> "Connecting to Android"
                            s.apps.isEmpty() -> "Choose your applications"
                            else -> "Your island is ready"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        when {
                            !permission.overlay ->
                                "1. Allow the floating island to appear over other apps."
                            !permission.listener ->
                                "2. Allow notification access so selected apps can appear here."
                            !s.enabled ->
                                "Enable Dynamic Island above, then select the apps you want to see."
                            !connected ->
                                "Waiting for the notification listener. Reopen notification access if this takes too long."
                            s.apps.isEmpty() ->
                                "No apps are selected. The test preview works, but real notifications need an app selection."
                            else ->
                                "${s.apps.size} apps selected · Everything stays on this device."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Button(onClick = { grantOverlay = true }, Modifier.fillMaxWidth()) {
                Text(
                    if (permission.overlay) "Overlay permission granted"
                    else "Grant overlay permission"
                )
            }
            Button(onClick = { grantNotifications = true }, Modifier.fillMaxWidth()) {
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
                val matches =
                    apps.filter {
                        it.label.contains(appSearch, true) || it.pkg.contains(appSearch, true)
                    }
                if (matches.isEmpty())
                    Text(
                        "No matching apps. Try a different name.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                matches.forEach { app ->
                    Toggle(app.label, app.pkg in s.apps) { checked ->
                        vm.update {
                            it.copy(apps = if (checked) it.apps + app.pkg else it.apps - app.pkg)
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
            Toggle("Metadata-only previews", s.hideContent) { v ->
                vm.update { it.copy(hideContent = v) }
            }
            Text(
                "Metadata-only mode skips extracting titles, messages and actions; Android still grants broad notification access. Turning it on clears existing previews. Payloads and reply drafts are held only temporarily in RAM and expire after one minute, including while expanded. Lock-screen content stays hidden. No notification history is saved to disk.",
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
        if (grantOverlay || grantNotifications) {
            val overlayGrant = grantOverlay
            AlertDialog(
                onDismissRequest = {
                    grantOverlay = false
                    grantNotifications = false
                },
                title = {
                    Text(
                        if (overlayGrant) "Display over other apps"
                        else "Sensitive notification access"
                    )
                },
                text = {
                    Text(
                        if (overlayGrant)
                            "The island draws a small interactive window over other apps. Android can hide it on protected screens. Only continue if you trust this app. You can revoke this permission in system settings."
                        else
                            "Android grants broad notification access, which can expose private messages and verification codes. Dynamic Island filters to your selected apps, keeps content only in memory, and has no internet access. Select only apps you trust; avoid banking and authentication apps. You can revoke access at any time. Do not disable Play Protect or bypass a security block to install this app."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            grantOverlay = false
                            grantNotifications = false
                            openSettings(
                                if (overlayGrant)
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:$packageName"),
                                    )
                                else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            )
                        }
                    ) {
                        Text("Continue to Android settings")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            grantOverlay = false
                            grantNotifications = false
                        }
                    ) {
                        Text("Cancel")
                    }
                },
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
        Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
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
