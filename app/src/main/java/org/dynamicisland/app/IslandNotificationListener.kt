package org.dynamicisland.app

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** API 29+ listener callbacks run on main. Do not post raw notifications into another queue. */
class IslandNotificationListener : NotificationListenerService() {
    private val app
        get() = application as IslandApplication

    private var active = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        active = true
        app.connected.value = true
        app.refresh()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!active) return
        app.refresh()
        if (!app.canProcessNotifications()) {
            app.overlay.clear()
            return
        }
        runCatching { app.notifications.parse(sbn, app.settingsSnapshot()) }
            .getOrNull()
            ?.let(app.overlay::arrival) ?: app.overlay.remove(sbn.key)
        // No raw StatusBarNotification/Notification/Bundle is retained by this service.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        app.overlay.remove(sbn.key)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        active = false
        app.connected.value = false
        app.overlay.clear()
        runCatching { requestRebind(ComponentName(this, IslandNotificationListener::class.java)) }
    }

    override fun onDestroy() {
        active = false
        app.connected.value = false
        app.overlay.clear()
        super.onDestroy()
    }
}
