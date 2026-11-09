package org.dynamicisland.app

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout

class IslandWindowManager(private val context: Context) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val lifecycle = OverlayLifecycle()
    internal val isAttached: Boolean
        get() = lifecycle.attached

    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var settings = IslandSettings()
    var onOutside: (() -> Unit)? = null

    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun attach(content: View, s: IslandSettings): Boolean {
        settings = s
        if (!Settings.canDrawOverlays(context)) {
            close()
            return false
        }
        if (lifecycle.attached) return true
        val host =
            object : FrameLayout(context) {
                override fun performClick(): Boolean = super.performClick()

                override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
                    if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                        onOutside?.invoke()
                        return true
                    }
                    return super.onTouchEvent(event)
                }
            }
        host.addView(content, FrameLayout.LayoutParams(-1, -1))
        val p =
            WindowManager.LayoutParams(
                dp(120),
                dp(48),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        root = host
        params = p
        host.setOnApplyWindowInsetsListener { _, insets ->
            position()
            insets
        }
        host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> position() }
        return runCatching {
                lifecycle.attach { manager.addView(host, p) }
                position()
                true
            }
            .getOrElse {
                root = null
                params = null
                false
            }
    }

    private fun position() {
        val p = params ?: return
        val metrics = context.resources.displayMetrics
        val cutout =
            manager.defaultDisplay.cutout?.boundingRects?.firstOrNull {
                it.top <= dp(48) &&
                    kotlin.math.abs(it.centerX() - metrics.widthPixels / 2) <
                        metrics.widthPixels / 4
            }
        val center = cutout?.centerX() ?: metrics.widthPixels / 2
        val desired = center - metrics.widthPixels / 2 + dp(settings.x)
        p.x =
            desired.coerceIn(
                -((metrics.widthPixels - p.width) / 2).coerceAtLeast(0),
                ((metrics.widthPixels - p.width) / 2).coerceAtLeast(0),
            )
        // Window-relative insets change as this overlay moves. Use display-relative
        // cutouts and stable status-bar insets to avoid a positioning feedback loop.
        val statusTop =
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                manager.maximumWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                    .top
            } else {
                val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
                if (id != 0) context.resources.getDimensionPixelSize(id) else dp(24)
            }
        p.y =
            (if (cutout != null) cutout.top + (cutout.height() - dp(40)) / 2 else statusTop) +
                dp(settings.y)
        p.y = p.y.coerceIn(0, (metrics.heightPixels - p.height).coerceAtLeast(0))
        update()
    }

    /** Keep notification content below a physical camera intersecting this window. */
    fun contentTopInsetDp(): Int {
        val p = params ?: return 0
        val left = context.resources.displayMetrics.widthPixels / 2 + p.x - dp(settings.width) / 2
        val bottom =
            manager.defaultDisplay.cutout
                ?.boundingRects
                ?.filter {
                    it.top <= dp(48) && it.right > left && it.left < left + dp(settings.width)
                }
                ?.maxOfOrNull { it.bottom } ?: return 0
        return kotlin.math
            .ceil(
                ((bottom - p.y).coerceAtLeast(0) / context.resources.displayMetrics.density)
                    .toDouble()
            )
            .toInt()
            .coerceAtMost(128)
    }

    fun resize(width: Int, height: Int, expanded: Boolean) {
        val p = params ?: return
        p.width = dp(width).coerceAtMost(context.resources.displayMetrics.widthPixels - dp(16))
        p.height = dp(height).coerceAtMost(context.resources.displayMetrics.heightPixels - dp(16))
        p.flags =
            if (expanded) p.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            else p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        position()
    }

    fun configure(s: IslandSettings) {
        settings = s
        position()
    }

    private fun update() {
        val view = root ?: return
        val p = params ?: return
        if (lifecycle.attached)
            runCatching { manager.updateViewLayout(view, p) }.onFailure { close() }
    }

    fun close() {
        lifecycle.detach { root?.let { runCatching { manager.removeViewImmediate(it) } } }
        root = null
        params = null
    }
}
