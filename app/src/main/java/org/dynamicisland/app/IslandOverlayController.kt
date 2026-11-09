package org.dynamicisland.app

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import kotlin.math.*
import kotlinx.coroutines.*

class IslandOverlayController(
    private val context: Context,
    private val repo: NotificationRepository,
    private val scope: CoroutineScope,
) {
    val stateMachine = IslandStateMachine()
    private val window = IslandWindowManager(context)
    private val haptic = HapticEngine(context)
    private val queue = NotificationQueueManager()
    private var settings = IslandSettings()
    private var panel: LinearLayout? = null
    private var expiry: Job? = null
    private var animator: ValueAnimator? = null
    private var animationTarget: Pair<Int, Int>? = null
    private var width = 120
    private var height = 48
    private var connected = false
    private var locked = false
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    init {
        window.onOutside = {
            // Keyboard touches can be reported as outside touches by some OEMs.
            // Keep an active reply intact; the explicit Collapse button stays available.
            if (
                stateMachine.state.value.mode == IslandMode.EXPANDED &&
                    panel?.findFocus() !is EditText
            )
                collapse()
        }
    }

    fun configure(s: IslandSettings, listenerConnected: Boolean) {
        val changed = settings != s || locked != keyguard.isKeyguardLocked
        locked = keyguard.isKeyguardLocked
        settings = s
        connected = listenerConnected && android.provider.Settings.canDrawOverlays(context)
        if (
            !PermissionState(android.provider.Settings.canDrawOverlays(context), connected)
                .canPresent(s.enabled) || (s.hideLocked && keyguard.isKeyguardLocked)
        ) {
            clear()
            return
        }
        queue.keys
            .filter { repo.get(it)?.pkg !in s.apps && repo.get(it)?.pkg != context.packageName }
            .forEach {
                queue.remove(it)
                repo.remove(it)
            }
        if (stateMachine.state.value.key !in queue.keys) {
            val next = queue.keys.firstOrNull()
            if (next == null) stateMachine.clear()
            else {
                stateMachine.show(next)
                schedule()
            }
        }
        if (!window.isAttached) panel = null
        val newlyAttached = panel == null
        if (newlyAttached) createPanel()
        window.configure(s)
        if (changed || newlyAttached) render()
    }

    private fun createPanel() {
        val view =
            object : LinearLayout(context) {
                    override fun performClick(): Boolean {
                        super.performClick()
                        return true
                    }
                }
                .apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(window.dp(14), window.dp(2), window.dp(14), window.dp(2))
                    background =
                        GradientDrawable().apply {
                            setColor(Color.BLACK)
                            cornerRadius = window.dp(28).toFloat()
                        }
                    clipToOutline = true
                }
        view.setOnClickListener { open() }
        view.setOnLongClickListener {
            expand()
            true
        }
        val gesture = GestureController(window.dp(40).toFloat())
        var x = 0f
        var y = 0f
        var time = 0L
        var long = false
        val longAction = Runnable {
            long = true
            haptic.play(HapticEngine.Event.LONG_PRESS, settings.haptics)
            expand()
        }
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x = e.rawX
                    y = e.rawY
                    time = e.eventTime
                    long = false
                    v.postDelayed(longAction, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - x) > window.dp(12) || abs(e.rawY - y) > window.dp(12))
                        v.removeCallbacks(longAction)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longAction)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.removeCallbacks(longAction)
                    if (!long)
                        when (gesture.classify(e.rawX - x, e.rawY - y, e.eventTime - time)) {
                            Gesture.TAP -> {
                                v.performClick()
                            }
                            Gesture.LONG_PRESS -> expand()
                            Gesture.DISMISS -> dismiss()
                            Gesture.NEXT -> switch(1)
                            Gesture.PREVIOUS -> switch(-1)
                            else -> Unit
                        }
                    true
                }
                else -> false
            }
        }
        view.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    v.removeCallbacks(longAction)
                }
            }
        )
        if (window.attach(view, settings)) panel = view
    }

    fun arrival(item: IslandNotification) {
        if (
            !context.getSystemService(android.os.PowerManager::class.java).isInteractive ||
                !settings.enabled ||
                !connected ||
                (settings.hideLocked && keyguard.isKeyguardLocked)
        )
            return
        if (
            !queue.offer(
                NotificationIdentity(item.key, item.fingerprint),
                stateMachine.state.value.key,
            )
        )
            return
        repo.put(item)
        repo.retain(queue.keys)
        if (panel == null) createPanel()
        val previousKey = stateMachine.state.value.key
        if (previousKey == null) stateMachine.show(item.key)
        haptic.play(HapticEngine.Event.ARRIVAL, settings.haptics)
        if (stateMachine.state.value.mode != IslandMode.EXPANDED) {
            render()
            // Waiting arrivals must not continually postpone the visible item's expiry.
            if (previousKey == null || previousKey == item.key) schedule()
        }
    }

    fun remove(key: String) {
        val current = stateMachine.state.value.key
        queue.remove(key)
        repo.remove(key)
        if (current == key) advance()
    }

    private fun schedule() {
        expiry?.cancel()
        expiry =
            scope.launch {
                delay(settings.duration * 1000L)
                dismiss(false)
            }
    }

    private fun advance() {
        val next = queue.keys.firstOrNull()
        if (next == null) {
            stateMachine.clear()
            expiry?.cancel()
        } else {
            stateMachine.show(next)
            schedule()
        }
        render()
    }

    private fun dismiss(feedback: Boolean = true) {
        stateMachine.state.value.key?.let {
            queue.dismiss(it)
            repo.remove(it)
        }
        if (feedback) haptic.play(HapticEngine.Event.DISMISS, settings.haptics)
        advance()
    }

    private fun switch(direction: Int) {
        queue.next(stateMachine.state.value.key, direction)?.let {
            stateMachine.show(it)
            render()
            schedule()
        }
    }

    private fun expand() {
        if (stateMachine.state.value.key == null) return
        expiry?.cancel()
        stateMachine.expand()
        haptic.play(HapticEngine.Event.EXPAND, settings.haptics)
        render()
    }

    private fun collapse() {
        stateMachine.collapse()
        haptic.play(HapticEngine.Event.CONTRACT, settings.haptics)
        render()
        if (stateMachine.state.value.key != null) schedule()
    }

    private fun open() {
        val item = repo.get(stateMachine.state.value.key) ?: return
        if (keyguard.isKeyguardLocked) return
        if (repo.open(item)) {
            haptic.play(HapticEngine.Event.SUCCESS, settings.haptics)
            dismiss(false)
        } else
            Toast.makeText(context, "Notification is no longer available", Toast.LENGTH_SHORT)
                .show()
    }

    private fun text(value: String, size: Float = 14f) =
        TextView(context).apply {
            text = value
            setTextColor(Color.WHITE)
            textSize = size
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(context).apply {
            text = label
            textSize = 12f
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            setTextColor(0xff82e0c0.toInt())
            val shape =
                GradientDrawable().apply {
                    setColor(0xff1c2225.toInt())
                    cornerRadius = window.dp(14).toFloat()
                }
            background =
                android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0xff35413f.toInt()),
                    shape,
                    null,
                )
            setOnClickListener { action() }
        }

    private fun render() {
        val view = panel ?: return
        if (keyguard.isKeyguardLocked && settings.hideLocked) {
            clear()
            return
        }
        view.removeAllViews()
        view.contentDescription = "Dynamic Island ready"
        val state = stateMachine.state.value
        val item = repo.get(state.key)
        val cameraInset = if (item != null) window.contentTopInsetDp() else 0
        view.setPadding(window.dp(14), window.dp(2 + cameraInset), window.dp(14), window.dp(2))
        val expanded = state.mode == IslandMode.EXPANDED && item != null
        if (item == null)
            view.addView(
                text("●", 12f).apply {
                    gravity = Gravity.CENTER
                    setTextColor(0xff82e0c0.toInt())
                    contentDescription = "Dynamic Island ready"
                }
            )
        else {
            val redacted = settings.hideContent || keyguard.isKeyguardLocked
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(
                ImageView(context).apply {
                    setImageDrawable(item.icon)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(window.dp(26), window.dp(26)),
            )
            row.addView(
                text(if (redacted) "New notification" else item.title).apply {
                    setTypeface(null, Typeface.BOLD)
                    setPadding(window.dp(8), 0, 0, 0)
                },
                LinearLayout.LayoutParams(0, -2, 1f),
            )
            if (queue.keys.size > 1) {
                row.addView(
                    text("${queue.keys.indexOf(item.key) + 1}/${queue.keys.size}", 11f).apply {
                        setTextColor(0xff82e0c0.toInt())
                        setPadding(window.dp(8), 0, 0, 0)
                        contentDescription =
                            "Notification ${queue.keys.indexOf(item.key) + 1} of ${queue.keys.size}. Swipe sideways to switch"
                    }
                )
            }
            view.addView(row)
            view.contentDescription =
                if (redacted) "New notification. Long press to expand"
                else "${item.title}. ${item.text}. Long press to expand"
            if (!redacted)
                view.addView(
                    text(item.text, 13f).apply {
                        setTextColor(0xffc4cacf.toInt())
                        maxLines = if (expanded) 4 else 1
                    },
                    LinearLayout.LayoutParams(-1, if (expanded) window.dp(80) else window.dp(20)),
                )
            if (expanded && !keyguard.isKeyguardLocked) {
                val buttons = LinearLayout(context)
                fun button(label: String, action: () -> Unit) {
                    buttons.addView(
                        actionButton(label, action),
                        LinearLayout.LayoutParams(0, window.dp(48), 1f).apply {
                            setMargins(window.dp(2), 0, window.dp(2), 0)
                        },
                    )
                }
                button("Open") { open() }
                button("Collapse") { collapse() }
                view.addView(buttons)
                if (!redacted)
                    item.actions.take(2).forEach { action ->
                        if (action.inputs.isEmpty())
                            view.addView(
                                actionButton(action.title) { interact(repo.send(action)) },
                                LinearLayout.LayoutParams(-1, window.dp(48)),
                            )
                        else {
                            val replyRow = LinearLayout(context)
                            val input =
                                EditText(context).apply {
                                    hint = "Reply"
                                    setHintTextColor(Color.LTGRAY)
                                    setTextColor(Color.WHITE)
                                    maxLines = 2
                                    inputType =
                                        android.text.InputType.TYPE_CLASS_TEXT or
                                            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                }
                            replyRow.addView(input, LinearLayout.LayoutParams(0, window.dp(48), 1f))
                            replyRow.addView(
                                actionButton("Send") {
                                        val reply = input.text.toString().trim()
                                        if (reply.isNotEmpty()) interact(repo.send(action, reply))
                                    }
                                    .apply { contentDescription = action.title },
                                LinearLayout.LayoutParams(window.dp(80), window.dp(48)),
                            )
                            view.addView(replyRow)
                        }
                    }
            }
        }
        val targetWidth = if (item == null) 120 else settings.width
        val targetHeight =
            if (!expanded) if (item == null) 48 else 64
            else
                (if (settings.hideContent || keyguard.isKeyguardLocked) 100
                else 170 + min(item!!.actions.size, 2) * 48)
        animate(targetWidth, targetHeight + cameraInset, expanded)
    }

    private fun interact(success: Boolean) {
        if (success) {
            haptic.play(HapticEngine.Event.SUCCESS, settings.haptics)
            dismiss(false)
        } else Toast.makeText(context, "Action unavailable", Toast.LENGTH_SHORT).show()
    }

    private fun animate(w: Int, h: Int, expanded: Boolean) {
        val target = w to h
        if (animationTarget == target && animator?.isRunning == true) return
        animator?.cancel()
        animationTarget = target
        if (width == w && height == h) {
            window.resize(w, h, expanded)
            return
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            width = w
            height = h
            window.resize(w, h, expanded)
            return
        }
        val fromW = width
        val fromH = height
        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = (360 / settings.speed).toLong()
                interpolator =
                    android.animation.TimeInterpolator { t ->
                        if (t == 1f) 1f else (1 - exp(-7 * t) * cos(7 * t))
                    }
                addUpdateListener { a ->
                    val f = a.animatedValue as Float
                    width = (fromW + (w - fromW) * f).toInt()
                    height = (fromH + (h - fromH) * f).toInt()
                    window.resize(width, height, expanded)
                }
                start()
            }
    }

    fun clear() {
        expiry?.cancel()
        animator?.cancel()
        queue.clear()
        repo.clear()
        stateMachine.clear()
        window.close()
        panel = null
        width = 120
        height = 48
        animationTarget = null
    }

    fun destroy() {
        clear()
        window.onOutside = null
    }
}
