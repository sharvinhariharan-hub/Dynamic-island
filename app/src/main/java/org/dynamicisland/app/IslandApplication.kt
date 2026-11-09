package org.dynamicisland.app

import android.app.Application
import android.app.NotificationManager
import android.content.*
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class IslandApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var settings: SettingsRepository
        private set

    lateinit var notifications: NotificationRepository
        private set

    lateinit var overlay: IslandOverlayController
        private set

    val connected = MutableStateFlow(false)
    private var current = IslandSettings()
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_SCREEN_OFF) overlay.clear() else refresh()
            }
        }
    private val overlayPermissionListener =
        android.app.AppOpsManager.OnOpChangedListener { _, _ ->
            Handler(Looper.getMainLooper()).post { refresh() }
        }
    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refresh()
            }
        }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        notifications = NotificationRepository(this)
        overlay = IslandOverlayController(this, notifications, scope)
        scope.launch {
            combine(settings.settings, connected) { s, c -> s to c }
                .collect { (s, _) ->
                    current = s
                    refresh()
                }
        }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(receiver, filter)
        getSystemService(android.app.AppOpsManager::class.java)
            .startWatchingMode(
                android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                packageName,
                overlayPermissionListener,
            )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("enabled_notification_listeners"),
            false,
            observer,
        )
    }

    fun refresh() {
        if (!getSystemService(android.os.PowerManager::class.java).isInteractive) {
            overlay.clear()
            return
        }
        overlay.configure(
            current,
            connected.value &&
                getSystemService(NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(
                        ComponentName(this, IslandNotificationListener::class.java)
                    ),
        )
    }

    fun settingsSnapshot() = current

    fun preview() {
        refresh()
        overlay.arrival(
            IslandNotification(
                "local-preview",
                packageName,
                "Dynamic Island",
                "Long press to expand. Swipe up to dismiss.",
                getDrawable(org.dynamicisland.app.R.drawable.ic_island),
                null,
                emptyList(),
                android.app.Notification.VISIBILITY_PUBLIC,
                System.nanoTime().toString(),
            )
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay.clear()
        refresh()
    }
}
