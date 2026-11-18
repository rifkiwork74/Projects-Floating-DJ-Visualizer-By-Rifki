package com.floatingdj.visualizer

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Small in-app toast surface that follows Theme Studio instead of Android's
 * OEM/system toast palette. It deliberately avoids WindowManager overlays and
 * is attached only to the current Activity content root.
 */
internal object ThemedToast {
    class Request internal constructor(
        private val context: Context,
        private val message: CharSequence,
        private val duration: Int
    ) {
        fun show() = ThemedToast.showToast(context, message, duration)
    }

    private data class ActiveToast(
        val view: WeakReference<View>,
        val dismiss: Runnable
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = WeakHashMap<Activity, ActiveToast>()

    fun makeText(context: Context, message: CharSequence, duration: Int): Request =
        Request(context, message, duration)

    private fun showToast(context: Context, message: CharSequence, duration: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            render(context, message, duration)
        } else {
            mainHandler.post { render(context, message, duration) }
        }
    }

    private fun render(context: Context, message: CharSequence, duration: Int) {
        val activity = context as? Activity
        if (activity == null || activity.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) {
            Toast.makeText(context, message, duration).show()
            return
        }
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        if (root !is FrameLayout) {
            Toast.makeText(context, message, duration).show()
            return
        }

        active.remove(activity)?.let { old ->
            mainHandler.removeCallbacks(old.dismiss)
            old.view.get()?.let { view -> if (view.parent === root) root.removeView(view) }
        }

        val prefs = activity.getSharedPreferences("floating_dj_settings", Context.MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "GLASS")?.uppercase() ?: "GLASS"
        val systemNight = (activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val light = when (mode) {
            "LIGHT" -> true
            "DARK", "GLASS" -> false
            "SYSTEM" -> !systemNight
            "CUSTOM" -> {
                val c = prefs.getInt("theme_surface_color", Color.parseColor("#1C2B42"))
                (Color.red(c) * 0.2126 + Color.green(c) * 0.7152 + Color.blue(c) * 0.0722) > 145.0
            }
            else -> false
        }
        val accent = when (mode) {
            "LIGHT" -> Color.parseColor("#26344B")
            "DARK" -> Color.parseColor("#F1F4FA")
            "SYSTEM" -> if (systemNight) Color.parseColor("#F1F4FA") else Color.parseColor("#26344B")
            "CUSTOM" -> prefs.getInt("theme_accent", Color.parseColor("#9B7BFF"))
            else -> Color.parseColor("#9B7BFF")
        }
        val surface = when (mode) {
            "LIGHT" -> Color.parseColor("#F6F8FC")
            "DARK" -> Color.parseColor("#101827")
            "SYSTEM" -> if (systemNight) Color.parseColor("#17243A") else Color.parseColor("#F2F5FA")
            "CUSTOM" -> prefs.getInt("theme_surface_color", Color.parseColor("#1C2B42"))
            else -> Color.parseColor("#1C2B42") // GLASS owns a fixed built-in surface.
        }
        val edge = when (mode) {
            "LIGHT" -> Color.parseColor("#A7B3C5")
            "DARK" -> Color.parseColor("#43536D")
            "SYSTEM" -> if (systemNight) Color.parseColor("#536987") else Color.parseColor("#AAB6C8")
            "CUSTOM" -> prefs.getInt("theme_edge_color", accent)
            else -> Color.parseColor("#A9B8D8")
        }
        val textKey = when (mode) {
            "GLASS" -> "theme_text_color_glass"
            "LIGHT" -> "theme_text_color_light"
            "DARK" -> "theme_text_color_dark"
            "SYSTEM" -> if (systemNight) "theme_text_color_system_dark" else "theme_text_color_system_light"
            else -> "theme_text_color_custom"
        }
        val textFallback = if (light) Color.parseColor("#24324A") else Color.parseColor("#F5F7FC")
        val textColor = prefs.getInt(textKey, textFallback)
        val density = activity.resources.displayMetrics.density.coerceAtLeast(0.1f)
        fun dp(value: Int) = (value * density).toInt()

        val fill = Color.argb(238, Color.red(surface), Color.green(surface), Color.blue(surface))
        val outline = Color.argb(150, Color.red(edge), Color.green(edge), Color.blue(edge))
        val toastView = TextView(activity).apply {
            text = message
            textSize = 12f * (prefs.getInt("ui_scale_percent", 100).coerceIn(85, 125) / 100f)
            setAutoSizeTextTypeUniformWithConfiguration(10, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            maxLines = 3
            includeFontPadding = false
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setTextColor(textColor)
            setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(fill)
                setStroke(dp(1), outline)
            }
            elevation = dp(14).toFloat()
            alpha = 0f
            translationY = dp(12).toFloat()
            scaleX = 0.97f
            scaleY = 0.97f
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            contentDescription = message.toString()
        }
        toastView.maxWidth = (activity.resources.displayMetrics.widthPixels - dp(36)).coerceAtLeast(dp(180))
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply {
            leftMargin = dp(18)
            rightMargin = dp(18)
            bottomMargin = dp(30)
        }
        root.addView(toastView, params)
        toastView.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(205L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
            .start()

        val dismiss = Runnable {
            if (toastView.parent === root) {
                toastView.animate().alpha(0f).translationY(dp(7).toFloat()).scaleX(0.985f).scaleY(0.985f)
                    .setDuration(180L)
                    .withEndAction {
                        if (toastView.parent === root) root.removeView(toastView)
                        active.remove(activity)
                    }.start()
            }
        }
        active[activity] = ActiveToast(WeakReference(toastView), dismiss)
        mainHandler.postDelayed(dismiss, if (duration == Toast.LENGTH_LONG) 4_000L else 2_600L)
    }
}
