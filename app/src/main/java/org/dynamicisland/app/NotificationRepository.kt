package org.dynamicisland.app

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.security.MessageDigest

data class IslandAction(
    val title: String,
    val pending: PendingIntent,
    val inputs: Array<RemoteInput> = emptyArray(),
)

data class IslandNotification(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val icon: Drawable?,
    val pending: PendingIntent?,
    val actions: List<IslandAction>,
    val visibility: Int,
    val fingerprint: String,
)

class NotificationRepository(private val context: Context) {
    private val items = mutableMapOf<String, IslandNotification>()

    fun get(key: String?) = items[key]

    fun remove(key: String) {
        items.remove(key)
    }

    fun clear() = items.clear()

    fun retain(keys: List<String>) {
        items.keys.retainAll(keys.toSet())
    }

    fun put(item: IslandNotification) {
        items[item.key] = item
    }

    fun parse(sbn: StatusBarNotification, settings: IslandSettings): IslandNotification? {
        val n = sbn.notification
        if (
            !NotificationFilter.accepts(
                settings,
                sbn.packageName,
                context.packageName,
                n.flags and Notification.FLAG_ONGOING_EVENT != 0,
                n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                n.category,
            )
        )
            return null
        if (n.visibility == Notification.VISIBILITY_SECRET) return null
        val title =
            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(200)
        val body =
            (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()
                .orEmpty()
                .take(2000)
        if (title.isBlank() && body.isBlank()) return null
        val actions =
            n.actions
                .orEmpty()
                .filter { it.actionIntent != null }
                .take(4)
                .map {
                    IslandAction(
                        it.title.toString(),
                        it.actionIntent,
                        it.remoteInputs
                            .orEmpty()
                            .filter { input -> input.allowFreeFormInput }
                            .toTypedArray(),
                    )
                }
        val raw =
            n.visibility.toString() + title + "\u0000" + body + actions.joinToString { it.title }
        val fingerprint =
            MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
        return IslandNotification(
            sbn.key,
            sbn.packageName,
            title,
            body,
            runCatching { context.packageManager.getApplicationIcon(sbn.packageName) }.getOrNull(),
            n.contentIntent,
            actions,
            n.visibility,
            fingerprint,
        )
    }

    fun send(action: IslandAction, reply: String? = null): Boolean =
        runCatching {
                val intent = Intent()
                if (reply != null && action.inputs.isNotEmpty()) {
                    val results = Bundle()
                    action.inputs.forEach { results.putCharSequence(it.resultKey, reply) }
                    RemoteInput.addResultsToIntent(action.inputs, intent, results)
                    RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
                }
                sendFromInteraction(action.pending, intent)
            }
            .isSuccess

    private fun sendFromInteraction(pending: PendingIntent, intent: Intent) {
        // Android 14+ requires an explicit sender opt-in for activity starts. Called only
        // from a direct user gesture; platform policy and creator restrictions still apply.
        val options =
            if (android.os.Build.VERSION.SDK_INT >= 34)
                android.app.ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                    .toBundle()
            else null
        pending.send(context, 0, intent, null, null, null, options)
    }

    fun open(item: IslandNotification): Boolean =
        runCatching {
                if (item.pending != null) sendFromInteraction(item.pending, Intent())
                else {
                    val intent =
                        context.packageManager.getLaunchIntentForPackage(item.pkg) ?: return false
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            .isSuccess
}
