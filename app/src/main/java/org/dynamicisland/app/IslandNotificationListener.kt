package org.dynamicisland.app

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class IslandNotificationListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private val app
        get() = application as IslandApplication

    private var active = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        main.post {
            active = true
            app.connected.value = true
            app.refresh()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        main.post {
            if (active) {
                app.refresh()
                if (!app.canProcessNotifications()) {
                    app.overlay.clear()
                    return@post
                }
                runCatching { app.notifications.parse(sbn, app.settingsSnapshot()) }
                    .getOrNull()
                    ?.let(app.overlay::arrival) ?: app.overlay.remove(sbn.key)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        main.post { app.overlay.remove(sbn.key) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        main.post {
            active = false
            app.connected.value = false
            app.overlay.clear()
            runCatching {
                requestRebind(ComponentName(this, IslandNotificationListener::class.java))
            }
        }
    }

    override fun onDestroy() {
        active = false
        main.removeCallbacksAndMessages(null)
        app.connected.value = false
        app.overlay.clear()
        super.onDestroy()
    }
}
