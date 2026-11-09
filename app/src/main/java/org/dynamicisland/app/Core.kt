package org.dynamicisland.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IslandSettings(
    val enabled: Boolean = false,
    val apps: Set<String> = emptySet(),
    val x: Int = 0,
    val y: Int = 4,
    val width: Int = 280,
    val duration: Int = 5,
    val speed: Float = 1f,
    val haptics: Int = 1,
    val hideLocked: Boolean = true,
    val hideContent: Boolean = false,
)

enum class IslandMode {
    COMPACT,
    PREVIEW,
    EXPANDED,
}

data class IslandState(val mode: IslandMode = IslandMode.COMPACT, val key: String? = null)

class IslandStateMachine {
    private val mutable = MutableStateFlow(IslandState())
    val state = mutable.asStateFlow()

    fun show(key: String) {
        mutable.value = IslandState(IslandMode.PREVIEW, key)
    }

    fun expand() {
        if (mutable.value.key != null)
            mutable.value = mutable.value.copy(mode = IslandMode.EXPANDED)
    }

    fun collapse() {
        mutable.value =
            mutable.value.copy(
                mode = if (mutable.value.key == null) IslandMode.COMPACT else IslandMode.PREVIEW
            )
    }

    fun clear() {
        mutable.value = IslandState()
    }
}

data class NotificationIdentity(val key: String, val fingerprint: String)

class NotificationQueueManager(private val capacity: Int = 20) {
    private val entries = LinkedHashMap<String, NotificationIdentity>()
    private val seen = LinkedHashMap<String, String>()
    val keys: List<String>
        get() = entries.keys.toList()

    fun offer(item: NotificationIdentity, pinned: String? = null): Boolean {
        if (seen[item.key] == item.fingerprint) return false
        seen[item.key] = item.fingerprint
        while (seen.size > 200) seen.remove(seen.keys.first())
        entries[item.key] = item
        while (entries.size > capacity) entries.remove(entries.keys.first { it != pinned })
        return true
    }

    fun remove(key: String) {
        entries.remove(key)
        seen.remove(key)
    }

    fun dismiss(key: String) {
        entries.remove(key)
    }

    fun next(current: String?, direction: Int = 1): String? {
        val list = keys
        if (list.isEmpty()) return null
        val i = list.indexOf(current)
        return list[Math.floorMod(if (i < 0) 0 else i + direction, list.size)]
    }

    fun clear() {
        entries.clear()
        seen.clear()
    }
}

object NotificationFilter {
    fun accepts(
        settings: IslandSettings,
        pkg: String,
        own: String,
        ongoing: Boolean,
        summary: Boolean,
        category: String?,
    ): Boolean =
        settings.enabled &&
            pkg != own &&
            pkg in settings.apps &&
            !ongoing &&
            !summary &&
            category !in setOf("transport", "alarm", "event", "reminder")
}

data class PermissionState(val overlay: Boolean, val listener: Boolean) {
    fun canPresent(enabled: Boolean) = enabled && overlay && listener
}

enum class Gesture {
    TAP,
    LONG_PRESS,
    DISMISS,
    NEXT,
    PREVIOUS,
    NONE,
}

class GestureController(private val threshold: Float) {
    fun classify(dx: Float, dy: Float, millis: Long): Gesture =
        when {
            dy < -threshold && -dy > kotlin.math.abs(dx) -> Gesture.DISMISS
            kotlin.math.abs(dx) > threshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) ->
                if (dx < 0) Gesture.NEXT else Gesture.PREVIOUS
            kotlin.math.abs(dx) > threshold || kotlin.math.abs(dy) > threshold -> Gesture.NONE
            millis >= 500 -> Gesture.LONG_PRESS
            else -> Gesture.TAP
        }
}

/** Idempotent ownership shared by lifecycle tests and the real window host. */
class OverlayLifecycle {
    var attached = false
        private set

    fun attach(action: () -> Unit) {
        if (!attached) {
            action()
            attached = true
        }
    }

    fun detach(action: () -> Unit) {
        if (attached) {
            try {
                action()
            } finally {
                attached = false
            }
        }
    }
}
