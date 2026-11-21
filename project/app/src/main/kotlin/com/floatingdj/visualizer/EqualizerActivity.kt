package com.floatingdj.visualizer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.util.TypedValue
import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Typeface
import com.floatingdj.visualizer.eq.core.EqEngineController
import com.floatingdj.visualizer.eq.data.EqPresetRepository
import com.floatingdj.visualizer.eq.model.EqProfile
import com.floatingdj.visualizer.eq.route.EqAudioRouteMonitor
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

class EqualizerActivity : Activity() {
    private lateinit var repo: EqPresetRepository
    private lateinit var presetSpinner: LinearLayout
    private lateinit var presetDropdownLabel: TextView
    private var presetPopup: PopupWindow? = null
    private var selectedPresetIndex = 0
    private var detectedPresetBandCount = 0
    private lateinit var headerTitle: TextView
    private lateinit var bandsRow: LinearLayout
    private lateinit var status: TextView
    private lateinit var masterSwitch: Switch
    private lateinit var preampSwitch: Switch
    private lateinit var preampSlider: SeekBar
    private lateinit var preampValue: TextView
    private lateinit var bassValue: TextView
    private lateinit var virtualValue: TextView
    private var pendingExportProfile: EqProfile? = null
    private var bandEditDialogOpen = false
    private var bandEditSessionId = 0
    private var faderWriteFailureShown = false
    private lateinit var bassSwitch: Switch
    private lateinit var virtualSwitch: Switch
    private lateinit var bassSlider: SeekBar
    private lateinit var virtualSlider: SeekBar
    private lateinit var limiterSwitch: Switch
    private lateinit var limiterValueLabel: TextView
    private lateinit var reverbSwitch: Switch
    private lateinit var reverbInfo: TextView
    private lateinit var bandHint: TextView
    private lateinit var bandCanvas: FrameLayout
    private lateinit var emptyBandMessage: TextView
    private lateinit var presetStateStatus: TextView
    private lateinit var masterStateBadge: TextView
    private var bandRailLeft: View? = null
    private var bandRailRight: View? = null
    private var bandScrollIndicator: EqHorizontalIndicatorView? = null
    private var pageScrollIndicator: EqVerticalIndicatorView? = null
    private val bandUiControls = mutableMapOf<Int, BandUiControl>()
    private var bandScrollable = false
    private val eqMasterDependentControls = mutableListOf<View>()
    private var profiles = mutableListOf<EqProfile>()
    private var spinnerEntries = mutableListOf<EqProfile>()
    private val currentStateEntryId = "__working_current_state__"
    private var active = EqProfile("flat", "Flat")
    private var suppress = false
    private var profileMappingWarning: String? = null
    private var themeMode = "GLASS"
    private var themeAccent = Color.parseColor("#9B7BFF")
    private var themeSurface = Color.parseColor("#1C2B42")
    private var themeEdge = Color.parseColor("#A9B8D8")
    private var themeText = Color.parseColor("#F5F7FC")
    private var isLightUi = false
    private var language = "en"
    private var textScale = 1f
    private var outerRoot: LinearLayout? = null
    private var contentRoot: LinearLayout? = null
    private var bandViewport: HorizontalScrollView? = null
    private var headerRoot: LinearLayout? = null
    private val navy = Color.rgb(24, 35, 57)
    private val blue = Color.rgb(91, 133, 218)
    private val white = Color.rgb(235, 241, 252)
    private val muted = Color.rgb(157, 174, 203)
    private val frequencies = intArrayOf(25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600, 2500, 4000, 6300, 10000, 16000)

    private inner class BandUiControl(
        val band: EqEngineController.Band,
        val fader: MixerFaderView,
        val valueView: TextView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = EqPresetRepository(this)
        loadThemeStudioSettings()
        active = repo.loadCurrentState() ?: EqProfile(currentStateEntryId, uiText("Current State", "Current State"))
        buildUi()
        loadPresets()
        restoreEngineIfEnabled()
        refreshCapability()
        // First launch also receives a dedicated Current State record, so the
        // first preset switch cannot accidentally treat a built-in Flat row as
        // the only working state and lose the initial configuration.
        if (repo.loadCurrentState() == null || active.id == currentStateEntryId) persistCurrentState()
        applyThemeStudio()
    }

    private fun loadThemeStudioSettings() {
        val p = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        themeMode = p.getString("theme_mode", "GLASS") ?: "GLASS"
        val storedCustomAccent = p.getInt("theme_accent", Color.parseColor("#9B7BFF"))
        val storedCustomSurface = p.getInt("theme_surface_color", Color.parseColor("#1C2B42"))
        val storedCustomEdge = p.getInt("theme_edge_color", Color.parseColor("#A9B8D8"))
        language = p.getString("app_language", "en") ?: "en"
        textScale = p.getInt("ui_scale_percent", 100).coerceIn(85, 125) / 100f
        val systemLight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        isLightUi = when (themeMode) {
            "LIGHT" -> true
            "SYSTEM" -> systemLight
            "CUSTOM" -> (Color.red(storedCustomSurface) * 0.2126 + Color.green(storedCustomSurface) * 0.7152 + Color.blue(storedCustomSurface) * 0.0722) > 145.0
            else -> false
        }
        // Built-in themes own their palettes. Only CUSTOM may consume the user's
        // stored panel/edge colors; this prevents Custom colors leaking into GLASS,
        // DARK, LIGHT, or SYSTEM when switching between Theme Studio presets.
        themeSurface = when (themeMode) {
            "CUSTOM" -> storedCustomSurface
            "LIGHT" -> Color.parseColor("#F6F8FC")
            "SYSTEM" -> if (systemLight) Color.parseColor("#F2F5FA") else Color.parseColor("#17243A")
            "DARK" -> Color.parseColor("#101827")
            else -> Color.parseColor("#1C2B42") // GLASS
        }
        themeEdge = when (themeMode) {
            "CUSTOM" -> storedCustomEdge
            "LIGHT" -> Color.parseColor("#A7B3C5")
            "SYSTEM" -> if (systemLight) Color.parseColor("#AAB6C8") else Color.parseColor("#536987")
            "DARK" -> Color.parseColor("#43536D")
            else -> Color.parseColor("#A9B8D8") // GLASS
        }
        themeAccent = storedCustomAccent
        val textKey = when (themeMode) {
            "GLASS" -> "theme_text_color_glass"
            "CUSTOM" -> "theme_text_color_custom"
            "SYSTEM" -> if (systemLight) "theme_text_color_system_light" else "theme_text_color_system_dark"
            "DARK" -> "theme_text_color_dark"
            "LIGHT" -> "theme_text_color_light"
            else -> "theme_text_color_custom"
        }
        val defaultText = if (isLightUi) Color.parseColor("#24324A") else Color.parseColor("#F5F7FC")
        themeText = p.getInt(textKey, defaultText)
        if (themeMode == "CUSTOM") themeText = customSurfaceTextColor()
        themeAccent = when (themeMode) {
            "GLASS" -> Color.parseColor("#9B7BFF")
            "SYSTEM" -> if (systemLight) Color.parseColor("#26344B") else Color.parseColor("#F1F4FA")
            "DARK" -> Color.parseColor("#F1F4FA")
            "LIGHT" -> Color.parseColor("#26344B")
            "CUSTOM" -> themeAccent
            else -> Color.parseColor("#9B7BFF")
        }
    }

    private fun uiText(id: String, en: String): String = if (language == "id") id else en

    private fun relativeLuminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(Color.red(color)) +
            0.7152 * linear(Color.green(color)) +
            0.0722 * linear(Color.blue(color))
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun customSurfaceTextColor(): Int {
        val top = lighten(themeSurface, 0.08f)
        val bottom = darken(themeSurface, 0.08f)
        val customMinimum = minOf(contrastRatio(themeText, top), contrastRatio(themeText, bottom))
        if (customMinimum >= 4.5) return themeText
        val whiteMinimum = minOf(contrastRatio(Color.WHITE, top), contrastRatio(Color.WHITE, bottom))
        val darkText = Color.parseColor("#17243A")
        val darkMinimum = minOf(contrastRatio(darkText, top), contrastRatio(darkText, bottom))
        return if (whiteMinimum >= darkMinimum) Color.WHITE else darkText
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private fun blend(base: Int, over: Int, ratio: Float): Int {
        val r = (Color.red(base) * (1f-ratio) + Color.red(over)*ratio).toInt()
        val g = (Color.green(base) * (1f-ratio) + Color.green(over)*ratio).toInt()
        val b = (Color.blue(base) * (1f-ratio) + Color.blue(over)*ratio).toInt()
        return Color.rgb(r,g,b)
    }
    private fun lighten(c: Int, amount: Float) = blend(c, Color.WHITE, amount)
    private fun darken(c: Int, amount: Float) = blend(c, Color.BLACK, amount)
    private fun roundedGradient(top: Int, bottom: Int, stroke: Int, radius: Int = 20): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = dp(radius).toFloat(); setStroke(dp(1), stroke)
        }
    private fun panelDrawable(radius: Int = 22): GradientDrawable {
        val custom = themeMode == "CUSTOM"
        val top = when {
            custom -> lighten(blend(themeSurface, themeAccent, 0.06f), 0.08f)
            isLightUi -> Color.WHITE
            else -> blend(Color.parseColor("#26364E"), themeAccent, 0.08f)
        }
        val bottom = when {
            custom -> darken(themeSurface, 0.18f)
            isLightUi -> Color.parseColor("#EDF2F8")
            else -> Color.parseColor("#111C2C")
        }
        val edge = if (custom) themeEdge else if (isLightUi) Color.parseColor("#A7B3C5") else 0x667D91B8
        return roundedGradient(top, bottom, edge, radius)
    }
    private fun trackColor(): Int = if (isLightUi) Color.parseColor("#B5C0D0") else Color.parseColor("#526078")
    private fun applyThumb(view: SeekBar) {
        val thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(lighten(themeAccent, 0.15f))
            setStroke(dp(2), if (isLightUi) Color.WHITE else Color.parseColor("#DCE4F4"))
            setSize(dp(22), dp(22))
        }
        view.thumb = thumb
        view.thumbOffset = dp(11)
        view.progressTintList = ColorStateList.valueOf(themeAccent)
        view.progressBackgroundTintList = ColorStateList.valueOf(trackColor())
        view.thumbTintList = null
        view.splitTrack = false
    }
    /** Horizontal mixer-style capsule used by the Bass Boost and Virtualizer faders. */
    private fun applyEffectMixerThumb(view: SeekBar) {
        view.thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(lighten(themeAccent, 0.15f))
            setStroke(dp(1), if (isLightUi) Color.parseColor("#8291A7") else 0xB8D5E0F3.toInt())
            setSize(dp(34), dp(18))
        }
        view.thumbOffset = dp(17)
        view.progressTintList = ColorStateList.valueOf(themeAccent)
        view.progressBackgroundTintList = ColorStateList.valueOf(trackColor())
        view.thumbTintList = null
        view.splitTrack = false
    }
    private fun applyMixerFaderThumb(view: SeekBar) {
        // SeekBar is rotated -90 degrees. A narrow/tall drawable becomes a wide,
        // low-profile mixer cap after rotation.
        view.thumb = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(5).toFloat()
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            colors = intArrayOf(
                if (isLightUi) Color.parseColor("#FFFFFF") else lighten(themeSurface, 0.30f),
                if (isLightUi) Color.parseColor("#D7DFEA") else darken(themeSurface, 0.02f)
            )
            setStroke(dp(1), if (isLightUi) Color.parseColor("#8291A7") else 0xB8D5E0F3.toInt())
            setSize(dp(14), dp(30))
        }
        view.thumbOffset = dp(15)
        view.progressTintList = ColorStateList.valueOf(themeAccent)
        view.progressBackgroundTintList = ColorStateList.valueOf(trackColor())
        view.thumbTintList = null
        view.splitTrack = false
    }

    private fun glassRailDrawable(left: Boolean): GradientDrawable {
        val base = if (isLightUi) Color.parseColor("#D7DFEA") else Color.parseColor("#9FB2D0")
        val strong = Color.argb(if (isLightUi) 180 else 145, Color.red(base), Color.green(base), Color.blue(base))
        val clear = Color.TRANSPARENT
        return GradientDrawable(
            if (left) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.RIGHT_LEFT,
            intArrayOf(strong, clear)
        )
    }

    private fun createGlassRail(left: Boolean): FrameLayout = FrameLayout(this).apply {
        background = glassRailDrawable(left)
        val line = View(this@EqualizerActivity).apply {
            setBackgroundColor(if (isLightUi) 0x88909EAF.toInt() else 0x88D6E1F2.toInt())
            alpha = 0.72f
        }
        addView(line, FrameLayout.LayoutParams(dp(1), ViewGroup.LayoutParams.MATCH_PARENT,
            if (left) Gravity.RIGHT else Gravity.LEFT))
    }

    private fun updateBandRails() {
        val scroller = bandViewport ?: return
        val left = bandRailLeft ?: return
        val right = bandRailRight ?: return
        if (!bandScrollable || scroller.visibility != View.VISIBLE) {
            left.visibility = View.GONE
            right.visibility = View.GONE
            bandScrollIndicator?.refreshMetrics()
            return
        }
        val maxScroll = (scroller.getChildAt(0)?.width ?: 0) - scroller.width
        left.visibility = if (scroller.scrollX > dp(2)) View.VISIBLE else View.GONE
        right.visibility = if (maxScroll > dp(2) && scroller.scrollX < maxScroll - dp(2)) View.VISIBLE else View.GONE
        bandScrollIndicator?.refreshMetrics()
    }

    private fun addSectionDivider(parent: LinearLayout, top: Int = 17, bottom: Int = 13) {
        parent.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (isLightUi) 0x557E8CA1 else 0x446F83A3)
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        })
    }

    private fun sectionTitle(parent: LinearLayout, text: String) {
        parent.addView(label(text, 13f, themeText, true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            letterSpacing = 0.075f
            setPadding(dp(2), dp(8), dp(2), dp(11))
        })
    }

    private fun effectCard(parent: LinearLayout, title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            tag = "eq_effect_card"
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = panelDrawable(16)
        }
        card.addView(label(title, 12f, themeAccent, true).apply {
            letterSpacing = 0.08f
            setPadding(dp(2), dp(1), dp(2), dp(7))
        })
        card.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                Color.argb(110, Color.red(themeAccent), Color.green(themeAccent), Color.blue(themeAccent)),
                if (isLightUi) 0x557E8CA1 else 0x446F83A3,
                Color.TRANSPARENT
            ))
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(9) })
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(11)
        })
        return card
    }

    private fun effectValueToggleRow(value: TextView, toggle: Switch): LinearLayout {
        value.gravity = Gravity.CENTER_VERTICAL
        value.setPadding(dp(13), dp(8), dp(13), dp(8))
        value.minHeight = dp(42)
        value.maxLines = 1
        value.ellipsize = android.text.TextUtils.TruncateAt.END
        value.background = panelDrawable(12)
        value.isClickable = true
        value.isFocusable = true
        toggle.text = ""
        styleModernSwitch(toggle)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(value, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(12) })
            addView(toggle, LinearLayout.LayoutParams(dp(68), dp(46)))
        }
    }

    private fun updateStrengthValue(target: TextView, strength: Int, enabled: Boolean, supported: Boolean) {
        val safe = strength.coerceIn(0, 1000)
        val percent = (safe / 10f).roundToInt()
        target.text = when {
            !EqEngineController.isActive() -> "$percent% · EQ ${uiText("OFF", "OFF")}"
            !supported -> uiText("Tidak didukung pada engine ini", "Not supported by this engine")
            enabled -> "$percent% · ${uiText("AKTIF", "ACTIVE")}"
            else -> "$percent% · ${uiText("OFF / tersimpan", "OFF / saved")}"
        }
    }

    private fun updateMasterStateBadge(enabled: Boolean) {
        if (!::masterStateBadge.isInitialized) return
        val color = if (enabled) Color.rgb(34, 197, 94) else Color.rgb(239, 91, 102)
        masterStateBadge.text = if (enabled) uiText("▶  EQ AKTIF", "▶  EQ ACTIVE") else uiText("■  EQ OFF", "■  EQ OFF")
        masterStateBadge.setTextColor(color)
        masterStateBadge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(Color.argb(24, Color.red(color), Color.green(color), Color.blue(color)))
            setStroke(dp(1), Color.argb(95, Color.red(color), Color.green(color), Color.blue(color)))
        }
        masterStateBadge.setPadding(dp(10), dp(5), dp(10), dp(5))
    }

    private fun updatePresetStateStatus() {
        if (!::presetStateStatus.isInitialized) return
        val currentStateSelected = active.id == currentStateEntryId || selectedPresetIndex == 0
        val dirty = isWorkingStateDirty()
        val profileName = profiles.firstOrNull { it.id == active.id }?.name ?: active.name
        presetStateStatus.text = when {
            currentStateSelected && dirty -> uiText("STATUS  ·  CURRENT STATE / PERUBAHAN TERBARU", "STATUS  ·  CURRENT STATE / LATEST EDITS")
            currentStateSelected -> uiText("STATUS  ·  CURRENT STATE / TERSIMPAN", "STATUS  ·  CURRENT STATE / SAVED")
            dirty -> uiText("STATUS  ·  CURRENT STATE / BELUM DISIMPAN", "STATUS  ·  CURRENT STATE / NOT SAVED")
            else -> uiText("STATUS  ·  PRESET: $profileName", "STATUS  ·  PRESET: $profileName")
        }
        val color = if (dirty) Color.rgb(239, 83, 96) else Color.rgb(34, 197, 94)
        presetStateStatus.setTextColor(color)
        presetStateStatus.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(9).toFloat()
            setColor(Color.argb(22, Color.red(color), Color.green(color), Color.blue(color)))
            setStroke(dp(1), Color.argb(85, Color.red(color), Color.green(color), Color.blue(color)))
        }
    }

    private fun installWindowInsets(root: View) {
        if (Build.VERSION.SDK_INT < 30) return
        window.setDecorFitsSystemWindows(false)
        val baseLeft = root.paddingLeft
        val baseTop = root.paddingTop
        val baseRight = root.paddingRight
        val baseBottom = root.paddingBottom
        root.setOnApplyWindowInsetsListener { view, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(
                baseLeft + safe.left,
                baseTop + safe.top,
                baseRight + safe.right,
                baseBottom + safe.bottom
            )
            insets
        }
        root.requestApplyInsets()
    }

    private fun applyHeaderTitleStyle() {
        if (!::headerTitle.isInitialized) return
        val start = if (isLightUi) themeAccent else lighten(themeAccent, 0.28f)
        val end = if (isLightUi) Color.parseColor("#26344B") else Color.parseColor("#F4F7FF")
        headerTitle.setTextColor(end)
        headerTitle.paint.shader = android.graphics.LinearGradient(
            0f, 0f, dp(250).toFloat(), 0f,
            intArrayOf(start, end, if (isLightUi) themeAccent else Color.WHITE),
            null, android.graphics.Shader.TileMode.CLAMP
        )
        headerTitle.setShadowLayer(dp(7).toFloat(), 0f, 0f, Color.argb(72,
            Color.red(themeAccent), Color.green(themeAccent), Color.blue(themeAccent)))
        headerTitle.letterSpacing = 0.045f
        headerTitle.invalidate()
    }

    /** Notification-style pill switch, recolored from the active Theme Studio accent. */
    private fun styleModernSwitch(toggle: Switch) {
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val disabledTrack = if (isLightUi) Color.parseColor("#D6DDE7") else Color.parseColor("#354257")
        val offTrack = if (isLightUi) Color.parseColor("#AEB9C8") else Color.parseColor("#59677D")
        val activeTrack = Color.argb(225, Color.red(themeAccent), Color.green(themeAccent), Color.blue(themeAccent))
        val trackColors = intArrayOf(disabledTrack, activeTrack, offTrack)
        val track = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setSize(dp(54), dp(30))
            cornerRadius = dp(18).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(1), if (isLightUi) 0x55718196 else 0x668FA3C1)
        }
        toggle.text = ""
        toggle.showText = false
        toggle.splitTrack = false
        toggle.switchMinWidth = dp(62)
        toggle.thumbTextPadding = dp(4)
        toggle.setTrackDrawable(track)
        toggle.setThumbDrawable(ThemePlayThumbDrawable(this).apply {
            setThemeColors(
                Color.WHITE,
                if (isLightUi) Color.parseColor("#E2E7EF") else Color.parseColor("#8D98A9"),
                if (isLightUi) 0x6696A4B7 else 0x778FA3C1,
                darken(themeAccent, if (isLightUi) 0.56f else 0.48f)
            )
        })
        // The intrinsic-sized thumb paints its own Play/Pause glyph; keep tint clear.
        toggle.thumbTintList = null
        toggle.trackTintList = ColorStateList(states, trackColors)
        toggle.minimumHeight = dp(42)
        toggle.setPadding(dp(2), dp(3), dp(2), dp(3))
        toggle.contentDescription = if (toggle.isChecked) uiText("Aktif", "On") else uiText("Nonaktif", "Off")
    }

    private fun applyThemeStudio() {
        val custom = themeMode == "CUSTOM"
        val rootTop = when {
            custom -> lighten(themeSurface, 0.07f)
            isLightUi -> Color.parseColor("#F6F8FC")
            else -> Color.parseColor("#142036")
        }
        val rootBottom = when {
            custom -> darken(themeSurface, 0.22f)
            isLightUi -> Color.parseColor("#E7EDF5")
            else -> Color.parseColor("#080F1E")
        }
        outerRoot?.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(rootTop, rootBottom))
        contentRoot?.background = panelDrawable(24)
        contentRoot?.clipToOutline = true
        headerRoot?.background = panelDrawable(20)
        status.background = panelDrawable(16)
        bandViewport?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(15).toFloat()
            setColor(when {
                custom -> darken(themeSurface, 0.08f)
                isLightUi -> Color.parseColor("#DDE4ED")
                else -> Color.parseColor("#263448")
            })
            setStroke(dp(1), if (custom) themeEdge else if (isLightUi) 0x66818FA2 else 0x667D91B8)
        }
        bandHint.background = panelDrawable(14)
        val normalText = themeText
        val secondaryText = if (isLightUi) Color.parseColor("#68768D") else Color.parseColor("#AAB7CB")
        fun visit(group: ViewGroup) {
            for (i in 0 until group.childCount) {
                val v = group.getChildAt(i)
                when (v) {
                    is MixerFaderView -> v.invalidate()
                    is SeekBar -> if (v.tag == "eq_mixer_fader") applyMixerFaderThumb(v) else applyThumb(v)
                    is LinearLayout -> {
                        if (v.tag == "eq_effect_card" || v.tag == "eq_reverb_card" || v.tag == "eq_effect_card_preamp") v.background = panelDrawable(16)
                        if (v.tag == "eq_reverb_card") { v.alpha = 0.46f; v.isEnabled = false }
                        if (v.tag == "eq_effect_card_preamp" && !EqEngineController.preampAvailable()) { v.alpha = 0.58f }
                    }
                    is Switch -> {
                        v.setTextColor(normalText)
                        v.textSize = 13f * textScale
                        styleModernSwitch(v)
                        if (v === reverbSwitch) v.isEnabled = false
                    }
                    is TextView -> {
                        if (v.isClickable) {
                            v.background = panelDrawable(14)
                            v.setTextColor(normalText)
                        } else if (v !== status) {
                            v.setTextColor(if (v.textSize / resources.displayMetrics.scaledDensity <= 11.5f) secondaryText else normalText)
                        }
                    }
                }
                if (v is ViewGroup) visit(v)
            }
        }
        outerRoot?.let { visit(it) }
        if (::presetSpinner.isInitialized) presetSpinner.background = panelDrawable(16)
        if (::presetDropdownLabel.isInitialized) presetDropdownLabel.setTextColor(normalText)
        if (::headerTitle.isInitialized) applyHeaderTitleStyle()
        presetStateStatus.setTextColor(if (isWorkingStateDirty()) Color.rgb(239,83,96) else Color.rgb(34,197,94))
        bandRailLeft?.background = glassRailDrawable(true)
        bandRailRight?.background = glassRailDrawable(false)
        updateMasterStateBadge(EqEngineController.isActive())
        updatePresetStateStatus()
        updateBandRails()
        pageScrollIndicator?.refreshMetrics()
        status.setTextColor(if (EqEngineController.isActive()) Color.rgb(34,197,94) else secondaryText)
        if (Build.VERSION.SDK_INT >= 30) {
            window.statusBarColor = when {
                custom -> rootTop
                isLightUi -> Color.parseColor("#F6F8FC")
                else -> Color.parseColor("#142036")
            }
            window.navigationBarColor = when {
                custom -> rootBottom
                isLightUi -> Color.parseColor("#E7EDF5")
                else -> Color.parseColor("#080F1E")
            }
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (isLightUi) mask else 0, mask)
        } else {
            @Suppress("DEPRECATION")
            run { window.statusBarColor = rootTop; window.navigationBarColor = rootBottom }
        }
    }

    private fun restoreEngineIfEnabled() {
        val prefs = getSharedPreferences("eq_presets_v1", MODE_PRIVATE)
        if (!prefs.getBoolean("engine_enabled", false)) return
        try {
            val serviceIntent = Intent(this, com.floatingdj.visualizer.eq.service.EqEngineService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            if (EqEngineController.activate()) {
                EqEngineController.setEnabled(true)
                applyProfile(active)
            }
        } catch (t: Throwable) {
            status.post { status.text = uiText("EQ tersimpan, tetapi engine belum dapat dipulihkan: ${t.message ?: "service tidak tersedia"}", "EQ settings were saved, but the engine could not be restored: ${t.message ?: "service unavailable"}") }
        }
    }

    private fun label(text: String, size: Float = 13f, color: Int = white, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size * textScale; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(text: String, action: () -> Unit): TextView = label(text, 12f, white, true).apply {
        gravity = Gravity.CENTER; setPadding(dp(12), dp(10), dp(12), dp(10)); background = panelDrawable(14)
        isClickable = true; isFocusable = true; setOnClickListener { action() }
    }
    private fun animateEqualizerEntrance(root: View) {
        root.alpha = 0f
        root.translationY = dp(12).toFloat()
        root.scaleX = 0.992f
        root.scaleY = 0.992f
        root.post {
            root.animate().cancel()
            root.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.65f))
                .start()
        }
    }

    private fun buildUi() {
        // Full-window shell owns the page edge rail. Keeping the rail outside the
        // padded content tree prevents it from being painted on top of Liquid Glass
        // cards and gives it the same safe, screen-edge gutter as MainActivity.
        val rootShell = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(if (isLightUi) Color.parseColor("#F6F8FC") else Color.parseColor("#080D18"))
        }
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        outerRoot = outer
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(12), dp(12))
            background = panelDrawable(20)
        }
        headerRoot = header
        val titleStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerTitle = label("EQUALIZER MODERN ENGINE", 16f, white, true).apply {
            gravity = Gravity.START
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
        }
        titleStack.addView(headerTitle, LinearLayout.LayoutParams(-1, -2))
        titleStack.addView(label("PRECISION AUDIO CONTROL  ·  DSP", 9f, muted, true).apply {
            letterSpacing = 0.11f
            setPadding(0, dp(4), 0, 0)
        })
        header.addView(titleStack, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(10) })
        header.addView(button("×") { finish() }, LinearLayout.LayoutParams(dp(46), dp(46)))
        outer.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        // Keep a dedicated right gutter inside the page content. The scroll rail
        // itself is drawn by a viewport-sized sibling, matching the main page's
        // glass indicator placement instead of living in a narrow side column.
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(28), dp(30)) }
        contentRoot = content
        val scroller = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scroller.addView(content)
        val pageScrollFrame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        pageScrollFrame.addView(scroller, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        // The page rail is added to rootShell below, not to this content frame.
        // This keeps the complete glass thumb outside every card's rounded edge.
        outer.addView(pageScrollFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        val masterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(3), dp(2), dp(3))
        }
        masterRow.addView(label(uiText("EQ OUTPUT MIX · EKSPERIMENTAL · TERGANTUNG PERANGKAT", "EQ OUTPUT MIX · EXPERIMENTAL · DEVICE-DEPENDENT"), 13f, themeText, true),
            LinearLayout.LayoutParams(0, dp(46), 1f))
        masterSwitch = ThemePlaySwitch(this).apply { text = ""; contentDescription = uiText("Aktifkan Equalizer", "Enable Equalizer") }
        styleModernSwitch(masterSwitch)
        masterRow.addView(masterSwitch, LinearLayout.LayoutParams(dp(68), dp(46)))
        content.addView(masterRow)
        masterStateBadge = label(uiText("■  EQ OFF", "■  EQ OFF"), 10f, muted, true)
        content.addView(masterStateBadge, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3); bottomMargin = dp(10) })
        status = label(uiText("Memeriksa dukungan AudioEffect…", "Checking AudioEffect support…"), 11f, muted).apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }
        status.background = panelDrawable(16)
        content.addView(status)
        addSectionDivider(content, 16, 12)
        presetStateStatus = label("STATUS", 10f, themeAccent, true).apply { setPadding(dp(10), dp(7), dp(10), dp(7)) }
        content.addView(presetStateStatus, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8) })
        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        presetSpinner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(14), dp(8))
            background = panelDrawable(16)
            minimumHeight = dp(54)
            isClickable = true
            isFocusable = true
            contentDescription = uiText("Buka daftar preset equalizer", "Open equalizer preset list")
        }
        presetDropdownLabel = label(uiText("Current State", "Current State"), 14f, themeText, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        presetSpinner.addView(presetDropdownLabel, LinearLayout.LayoutParams(0, -1, 1f))
        presetSpinner.addView(label("⌄", 23f, themeAccent, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(dp(30), -1))
        presetSpinner.setOnClickListener { showPresetDropdown() }
        presetRow.addView(presetSpinner, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(10) })
        val plus = button("＋") { promptName(uiText("Simpan Sebagai", "Save As")) { name -> saveAs(name) } }.apply {
            textSize = 22f * textScale
            background = roundedGradient(lighten(themeSurface, 0.12f), darken(themeSurface, 0.12f), themeEdge, 18)
        }
        presetRow.addView(plus, LinearLayout.LayoutParams(dp(52), dp(50)))
        content.addView(presetRow)
        registerEqMasterDependentControl(presetRow)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(12)) }
        listOf(uiText("SIMPAN KE", "SAVE TO"), uiText("GANTI NAMA", "RENAME"), uiText("HAPUS", "DELETE"), "FLAT").forEachIndexed { i, t ->
            val actionButton = button(t) {
                when (i) {
                    0 -> saveToPreset()
                    1 -> if (isBuiltInPreset(active)) {
                        toast(uiText("Preset bawaan tidak dapat diganti nama. Gunakan Simpan Sebagai untuk membuat preset sendiri.", "Built-in presets cannot be renamed. Use Save As to create your own preset."))
                    } else {
                        promptName(uiText("Ganti Nama", "Rename")) { rename(it) }
                    }
                    2 -> confirmDeleteCurrent()
                    3 -> resetFlat()
                }
            }.apply {
                maxLines = 1
                minLines = 1
                includeFontPadding = false
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(2), dp(9), dp(2), dp(9))
                setAutoSizeTextTypeUniformWithConfiguration(7, 10, 1, TypedValue.COMPLEX_UNIT_SP)
            }
            actions.addView(actionButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
        }
        content.addView(actions)
        registerEqMasterDependentControl(actions)
        addSectionDivider(content, 14, 12)
        sectionTitle(content, uiText("BAND EQUALIZER", "EQUALIZER BANDS"))
        bandCanvas = FrameLayout(this).apply {
            background = panelDrawable(18)
            clipToOutline = true
        }
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(dp(5), dp(10), dp(5), dp(10))
        }
        bandViewport = horizontal
        bandsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        horizontal.addView(bandsRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        bandCanvas.addView(horizontal, FrameLayout.LayoutParams(-1, -1))
        emptyBandMessage = label(uiText("EQ belum aktif\nAktifkan EQ untuk memindai band yang didukung perangkat ini.", "EQ is off\nEnable EQ to scan the bands supported by this device."), 14f, muted, true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(dp(26), dp(24), dp(26), dp(24))
        }
        bandCanvas.addView(emptyBandMessage, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        bandRailLeft = createGlassRail(true).also { bandCanvas.addView(it, FrameLayout.LayoutParams(dp(10), -1, Gravity.LEFT or Gravity.CENTER_VERTICAL)) }
        bandRailRight = createGlassRail(false).also { bandCanvas.addView(it, FrameLayout.LayoutParams(dp(10), -1, Gravity.RIGHT or Gravity.CENTER_VERTICAL)) }
        horizontal.setOnScrollChangeListener { _, _, _, _, _ ->
            updateBandRails()
            bandScrollIndicator?.invalidate()
        }
        content.addView(bandCanvas, LinearLayout.LayoutParams(-1, dp(480)))
        registerEqMasterDependentControl(bandCanvas)
        bandScrollIndicator = EqHorizontalIndicatorView(horizontal).also { indicator ->
            content.addView(indicator, LinearLayout.LayoutParams(-1, dp(14)).apply {
                topMargin = dp(5)
                bottomMargin = dp(7)
            })
            registerEqMasterDependentControl(indicator)
        }
        bandHint = label(uiText("Jumlah band mengikuti engine audio aktif. Ketuk nilai dB untuk memasukkan angka secara langsung; setiap band dapat di-reset ke 0 dB.", "Band count follows the active audio engine. Tap a dB value to enter it directly; each band can be reset to 0 dB."), 10f, muted).apply {
            setPadding(dp(10), dp(12), dp(10), dp(15)); gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        content.addView(bandHint, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(9)
            bottomMargin = dp(3)
        })
        // EFFECTS SUPPORTED BY THE ACTIVE BACKEND
        addSectionDivider(content, 11, 7)
        val bassCard = effectCard(content, uiText("PENGUAT BASS", "BASS BOOST"))
        registerEqMasterDependentControl(bassCard)
        bassSwitch = ThemePlaySwitch(this).apply { text = ""; contentDescription = uiText("Aktifkan Bass Boost", "Enable Bass Boost") }
        bassValue = label("0% · OFF", 11f, themeText, true).apply {
            contentDescription = uiText("Ketuk untuk memasukkan nilai Bass Boost", "Tap to enter Bass Boost value")
        }
        bassCard.addView(effectValueToggleRow(bassValue, bassSwitch))
        bassSlider = SeekBar(this).apply { max = 1000; applyEffectMixerThumb(this) }
        bassCard.addView(bassSlider, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) })

        addSectionDivider(content, 13, 5)
        val virtualCard = effectCard(content, "VIRTUALIZER")
        registerEqMasterDependentControl(virtualCard)
        virtualSwitch = ThemePlaySwitch(this).apply { text = ""; contentDescription = uiText("Aktifkan Virtualizer", "Enable Virtualizer") }
        virtualValue = label("0% · OFF", 11f, themeText, true).apply {
            contentDescription = uiText("Ketuk untuk memasukkan nilai Virtualizer", "Tap to enter Virtualizer value")
        }
        virtualCard.addView(effectValueToggleRow(virtualValue, virtualSwitch))
        virtualSlider = SeekBar(this).apply { max = 1000; applyEffectMixerThumb(this) }
        virtualCard.addView(virtualSlider, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) })

        addSectionDivider(content, 13, 5)
        val limiterCard = effectCard(content, "LIMITER")
        registerEqMasterDependentControl(limiterCard)
        limiterSwitch = ThemePlaySwitch(this).apply { text = ""; contentDescription = uiText("Aktifkan Limiter", "Enable Limiter") }
        limiterValueLabel = label("−1 dBFS  ·  20:1", 11f, themeText, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(8), dp(13), dp(8))
            minHeight = dp(42)
            background = panelDrawable(12)
        }
        limiterCard.addView(effectValueToggleRow(limiterValueLabel, limiterSwitch))
        limiterCard.addView(label(uiText(
            "Limiter bekerja otomatis saat sinyal melewati threshold. Kontrol ON/OFF; threshold −1 dBFS dan ratio 20:1. Cakupan output tetap eksperimental.",
            "The limiter acts automatically when signal crosses its threshold. ON/OFF control; threshold −1 dBFS and ratio 20:1. Output coverage remains experimental."
        ), 10f, muted).apply { setPadding(0, dp(7), 0, 0) })

        // Visually separate effects that are detected/stored but cannot be attached
        // to the current experimental output-mix route.
        addSectionDivider(content, 24, 13)
        content.addView(label(
            uiText("FITUR SEMENTARA NONAKTIF · KOMPATIBILITAS ROUTE", "TEMPORARILY DISABLED · ROUTE COMPATIBILITY"),
            10f, muted, true
        ).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            letterSpacing = 0.055f
            setPadding(dp(8), dp(4), dp(8), dp(10))
        })

        val preampCard = effectCard(content, "PREAMP").apply { tag = "eq_effect_card_preamp" }
        registerEqMasterDependentControl(preampCard)
        preampSwitch = ThemePlaySwitch(this).apply {
            text = ""
            isChecked = false
            isEnabled = false
            contentDescription = uiText("Preamp tidak tersedia pada jalur output mix", "Preamp unavailable on output-mix route")
        }
        preampValue = label("0.0 dB · OFF · DISABLED", 11f, muted, true).apply {
            contentDescription = uiText("Preamp dinonaktifkan demi keamanan output mix", "Preamp disabled for output-mix safety")
            isEnabled = false
        }
        preampCard.addView(effectValueToggleRow(preampValue, preampSwitch))
        preampSlider = SeekBar(this).apply { max = 180; progress = 120; applyThumb(this); isEnabled = false }
        preampCard.addView(preampSlider, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(7) })
        preampCard.addView(label(uiText(
            "Preamp tidak diterapkan pada Output Mix eksperimental demi keamanan level audio. Nilai lama tetap tersimpan, tetapi tidak ditulis ke effect.",
            "Preamp is not applied on the experimental Output Mix for audio-level safety. Previously saved values are retained but are not written to the effect."
        ), 10f, muted))

        addSectionDivider(content, 13, 5)
        val reverbCard = effectCard(content, "REVERB").apply { tag = "eq_reverb_card"; alpha = 0.46f; isEnabled = false }
        reverbSwitch = ThemePlaySwitch(this).apply {
            text = ""
            isChecked = false
            isEnabled = false
            contentDescription = uiText("Reverb nonaktif, memerlukan player attach dan send level", "Reverb disabled; requires player attachment and send level")
        }
        val reverbValue = label(uiText("NOT AVAILABLE · PLAYER ATTACH REQUIRED", "NOT AVAILABLE · PLAYER ATTACH REQUIRED"), 10f, muted, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            minHeight = dp(42)
            background = panelDrawable(12)
            isEnabled = false
        }
        reverbCard.addView(effectValueToggleRow(reverbValue, reverbSwitch))
        reverbInfo = label(uiText("Memeriksa dukungan Reverb…", "Checking Reverb support…"), 10f, muted).apply { setPadding(0, dp(7), 0, 0) }
        reverbCard.addView(reverbInfo)
        reverbCard.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        addSectionDivider(content, 17, 12)
        val fileActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(4), 0, dp(10)) }
        fileActions.addView(button(uiText("IMPOR .dj-eq.json", "IMPORT .dj-eq.json")) { importPreset() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        fileActions.addView(button(uiText("EKSPOR PRESET", "EXPORT PRESET")) { exportPreset() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(8) })
        content.addView(fileActions)
        rootShell.addView(outer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val verticalIndicator = EqVerticalIndicatorView(scroller, showWhenNoOverflow = true)
        pageScrollIndicator = verticalIndicator
        // Full-window overlay, exactly like the main Visualizer page: the rail
        // hugs the device's right edge while remaining safely inside the screen.
        rootShell.addView(verticalIndicator, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        scroller.setOnScrollChangeListener { _, _, _, _, _ -> verticalIndicator.invalidate() }
        val refreshPageIndicator = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> verticalIndicator.refreshMetrics() }
        scroller.addOnLayoutChangeListener(refreshPageIndicator)
        content.addOnLayoutChangeListener(refreshPageIndicator)
        pageScrollFrame.addOnLayoutChangeListener(refreshPageIndicator)
        verticalIndicator.addOnLayoutChangeListener(refreshPageIndicator)
        setContentView(rootShell)
        installWindowInsets(outer)
        verticalIndicator.post { verticalIndicator.refreshMetrics() }
        pageScrollFrame.post { verticalIndicator.refreshMetrics() }
        applyThemeStudio()
        animateEqualizerEntrance(outer)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        masterSwitch.setOnCheckedChangeListener { _, checked ->
            if (suppress) return@setOnCheckedChangeListener
            if (checked) {
                val ok = EqEngineController.activate()
                if (!ok) { suppress = true; masterSwitch.isChecked = false; suppress = false; toast(EqEngineController.lastError ?: uiText("EQ tidak tersedia", "EQ unavailable")) }
                else {
                    getSharedPreferences("eq_presets_v1", MODE_PRIVATE).edit().putBoolean("engine_enabled", true).apply()
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, com.floatingdj.visualizer.eq.service.EqEngineService::class.java))
                        else startService(Intent(this, com.floatingdj.visualizer.eq.service.EqEngineService::class.java))
                        EqEngineController.setEnabled(true); refreshCapability(); applyProfile(active)
                    } catch (t: Throwable) {
                        getSharedPreferences("eq_presets_v1", MODE_PRIVATE).edit().putBoolean("engine_enabled", false).apply()
                        EqEngineController.disableAll(); EqEngineController.release()
                        suppress = true; masterSwitch.isChecked = false; suppress = false
                        toast(uiText("Engine service tidak dapat dijalankan: ${t.message ?: "tidak diizinkan"}", "The EQ service could not start: ${t.message ?: "permission denied"}"))
                        refreshCapability()
                    }
                }
            } else {
                persistCurrentState(captureFromEngine = true)
                getSharedPreferences("eq_presets_v1", MODE_PRIVATE).edit().putBoolean("engine_enabled", false).apply()
                EqEngineController.disableAll(); EqEngineController.release()
                stopService(Intent(this, com.floatingdj.visualizer.eq.service.EqEngineService::class.java)); refreshCapability()
            }
        }
        preampSwitch.setOnCheckedChangeListener { _, checked ->
            if (!suppress) {
                val requested = if (checked) active.preampDb else 0f
                val applied = EqEngineController.setPreampDb(requested)
                if (applied != null) {
                    active.preampEnabled = checked
                    if (checked) active.preampDb = applied
                    preampSlider.progress = ((active.preampDb + 12f) * 10f).roundToInt().coerceIn(0, 180)
                    updatePreampValue(active.preampDb, checked)
                    persistCurrentState(force = true)
                    markWorkingStateSelected()
                } else {
                    handlePreampApplyFailure()
                }
            }
        }
        preampSlider.setOnSeekBarChangeListener(simpleListener { progress ->
            val requested = (-12f + progress / 10f).coerceIn(-12f, 6f)
            if (active.preampEnabled) {
                val applied = EqEngineController.setPreampDb(requested)
                if (applied != null) {
                    active.preampDb = applied
                    updatePreampValue(applied, true)
                    persistCurrentState(force = true)
                    markWorkingStateSelected()
                } else {
                    handlePreampApplyFailure()
                }
            } else {
                active.preampDb = requested
                updatePreampValue(requested, false)
                persistCurrentState(force = true)
                markWorkingStateSelected()
            }
        })
        bassSwitch.setOnCheckedChangeListener { _, checked ->
            if (!suppress) {
                val applied = EqEngineController.setBassEnabled(checked)
                if (applied) {
                    active.bassEnabled = checked
                    updateStrengthValue(bassValue, active.bassStrength, checked, EqEngineController.bassStrengthSupported())
                    persistCurrentState(force = true)
                    markWorkingStateSelected()
                } else {
                    suppress = true; bassSwitch.isChecked = EqEngineController.bassEnabled(); suppress = false
                    toast(uiText("Bass Boost tidak dapat mengambil kontrol audio.", "Bass Boost could not obtain audio control."))
                }
            }
        }
        virtualSwitch.setOnCheckedChangeListener { _, checked ->
            if (!suppress) {
                val applied = EqEngineController.setVirtualizerEnabled(checked)
                if (applied) {
                    active.virtualizerEnabled = checked
                    updateStrengthValue(virtualValue, active.virtualizerStrength, checked, EqEngineController.virtualizerStrengthSupported())
                    persistCurrentState(force = true)
                    markWorkingStateSelected()
                } else {
                    suppress = true; virtualSwitch.isChecked = EqEngineController.virtualizerEnabled(); suppress = false
                    toast(uiText("Virtualizer tidak dapat mengambil kontrol audio.", "Virtualizer could not obtain audio control."))
                }
            }
        }
        bassSlider.setOnSeekBarChangeListener(simpleListener {
            val v = EqEngineController.setBassStrength(it)
            if (v >= 0) { active.bassStrength = v; updateStrengthValue(bassValue, v, active.bassEnabled, EqEngineController.bassStrengthSupported()); persistCurrentState(force = true); markWorkingStateSelected() }
        })
        virtualSlider.setOnSeekBarChangeListener(simpleListener {
            val v = EqEngineController.setVirtualizerStrength(it)
            if (v >= 0) { active.virtualizerStrength = v; updateStrengthValue(virtualValue, v, active.virtualizerEnabled, EqEngineController.virtualizerStrengthSupported()); persistCurrentState(force = true); markWorkingStateSelected() }
        })
        preampValue.setOnClickListener {
            if (preampSwitch.isEnabled) showEffectValueDialog(
                uiText("NILAI PREAMP", "PREAMP VALUE"),
                uiText("Input gain sebelum EQ · -12 sampai +6 dB", "Input gain before EQ · -12 to +6 dB"),
                active.preampDb, -12f, 6f, "dB", 1
            ) { requested ->
                if (active.preampEnabled) {
                    val applied = EqEngineController.setPreampDb(requested)
                    if (applied == null) { handlePreampApplyFailure(); return@showEffectValueDialog false }
                    active.preampDb = applied
                } else active.preampDb = requested
                preampSlider.progress = ((active.preampDb + 12f) * 10f).roundToInt().coerceIn(0, 180)
                updatePreampValue(active.preampDb, active.preampEnabled)
                persistCurrentState(force = true); markWorkingStateSelected(); true
            }
        }
        bassValue.setOnClickListener {
            if (bassSlider.isEnabled) {
                val baseline = active.bassStrength / 10f
                showEffectValueDialog(
                    uiText("NILAI BASS BOOST", "BASS BOOST VALUE"),
                    uiText("Kekuatan effect · 0 sampai 100%", "Effect strength · 0 to 100%"),
                    baseline, 0f, 100f, "%", 0,
                    onPreview = { requested ->
                        val applied = EqEngineController.setBassStrength((requested * 10f).roundToInt().coerceIn(0, 1000))
                        if (applied < 0) null else {
                            bassSlider.progress = applied
                            updateStrengthValue(bassValue, applied, active.bassEnabled, EqEngineController.bassStrengthSupported())
                            applied / 10f
                        }
                    },
                    onRestore = { original ->
                        val applied = EqEngineController.setBassStrength((original * 10f).roundToInt().coerceIn(0, 1000))
                        if (applied >= 0) {
                            bassSlider.progress = applied
                            updateStrengthValue(bassValue, applied, active.bassEnabled, EqEngineController.bassStrengthSupported())
                        }
                    },
                    onSave = { requested ->
                        val applied = EqEngineController.setBassStrength((requested * 10f).roundToInt().coerceIn(0, 1000))
                        if (applied < 0) return@showEffectValueDialog false
                        active.bassStrength = applied
                        bassSlider.progress = applied
                        updateStrengthValue(bassValue, applied, active.bassEnabled, EqEngineController.bassStrengthSupported())
                        persistCurrentState(force = true); markWorkingStateSelected(); true
                    }
                )
            }
        }
        virtualValue.setOnClickListener {
            if (virtualSlider.isEnabled) {
                val baseline = active.virtualizerStrength / 10f
                showEffectValueDialog(
                    uiText("NILAI VIRTUALIZER", "VIRTUALIZER VALUE"),
                    uiText("Kekuatan effect · 0 sampai 100%", "Effect strength · 0 to 100%"),
                    baseline, 0f, 100f, "%", 0,
                    onPreview = { requested ->
                        val applied = EqEngineController.setVirtualizerStrength((requested * 10f).roundToInt().coerceIn(0, 1000))
                        if (applied < 0) null else {
                            virtualSlider.progress = applied
                            updateStrengthValue(virtualValue, applied, active.virtualizerEnabled, EqEngineController.virtualizerStrengthSupported())
                            applied / 10f
                        }
                    },
                    onRestore = { original ->
                        val applied = EqEngineController.setVirtualizerStrength((original * 10f).roundToInt().coerceIn(0, 1000))
                        if (applied >= 0) {
                            virtualSlider.progress = applied
                            updateStrengthValue(virtualValue, applied, active.virtualizerEnabled, EqEngineController.virtualizerStrengthSupported())
                        }
                    },
                    onSave = { requested ->
                        val applied = EqEngineController.setVirtualizerStrength((requested * 10f).roundToInt().coerceIn(0, 1000))
                        if (applied < 0) return@showEffectValueDialog false
                        active.virtualizerStrength = applied
                        virtualSlider.progress = applied
                        updateStrengthValue(virtualValue, applied, active.virtualizerEnabled, EqEngineController.virtualizerStrengthSupported())
                        persistCurrentState(force = true); markWorkingStateSelected(); true
                    }
                )
            }
        }
        limiterSwitch.setOnCheckedChangeListener { _, checked ->
            if (!suppress) {
                val applied = EqEngineController.setLimiterEnabled(checked)
                if (applied) {
                    active.limiterEnabled = checked
                    persistCurrentState(force = true)
                    markWorkingStateSelected()
                } else {
                    suppress = true
                    limiterSwitch.isChecked = EqEngineController.limiterEnabled()
                    suppress = false
                    toast(uiText("Limiter tidak tersedia pada backend EQ aktif.", "Limiter is not available on the active EQ backend."))
                }
                refreshCapability()
            }
        }
    }

    /** A correctly measured vertical fader; no rotated SeekBar drawing/touch overflow. */
    private inner class MixerFaderView(
        private val band: EqEngineController.Band
    ) : View(this@EqualizerActivity) {
        private val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val activePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val capPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val capStrokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val glossPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val viewConfig = android.view.ViewConfiguration.get(context)
        private val touchSlop = viewConfig.scaledTouchSlop
        private val minimumFlingVelocity = viewConfig.scaledMinimumFlingVelocity
        private var velocityTracker: android.view.VelocityTracker? = null
        private var gainMb = band.levelMb.coerceIn(band.minMb, band.maxMb)
        // Keep the last value confirmed by the active native effect. A failed write
        // must snap back to this value, not to the initial value captured at layout time.
        private var confirmedGainMb = gainMb
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var verticalDrag = false
        private var horizontalGesture = false
        private var gainChangedInGesture = false
        var onGainChanged: ((Int) -> Unit)? = null
        var onGainCommitted: ((Int) -> Unit)? = null

        init {
            isFocusable = true
            isClickable = true
            setWillNotDraw(false)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        fun setGainMb(value: Int) {
            val next = value.coerceIn(band.minMb, band.maxMb)
            confirmedGainMb = next
            if (next != gainMb) { gainMb = next; invalidate() }
        }

        fun lastConfirmedGainMb(): Int = confirmedGainMb

        private fun trackTop() = dp(20).toFloat()
        private fun trackBottom() = (height - dp(20)).toFloat().coerceAtLeast(trackTop() + 1f)

        private fun applyTouchY(y: Float) {
            val top = trackTop()
            val bottom = trackBottom()
            val fraction = (1f - ((y - top) / (bottom - top))).coerceIn(0f, 1f)
            val next = (band.minMb + fraction * (band.maxMb - band.minMb)).roundToInt().coerceIn(band.minMb, band.maxMb)
            if (next != gainMb) {
                gainMb = next
                gainChangedInGesture = true
                invalidate()
                onGainChanged?.invoke(next)
            }
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val top = trackTop()
            val bottom = trackBottom()
            val fraction = if (band.maxMb == band.minMb) 0f else ((gainMb - band.minMb).toFloat() / (band.maxMb - band.minMb)).coerceIn(0f, 1f)
            val thumbY = bottom - fraction * (bottom - top)

            trackPaint.shader = null
            trackPaint.color = if (isLightUi) Color.parseColor("#AEB9C9") else Color.parseColor("#53647C")
            canvas.drawRoundRect(cx - dp(2).toFloat(), top, cx + dp(2).toFloat(), bottom, dp(2).toFloat(), dp(2).toFloat(), trackPaint)

            activePaint.shader = android.graphics.LinearGradient(
                cx, thumbY, cx, bottom,
                lighten(themeAccent, if (isLightUi) 0.08f else 0.12f),
                darken(themeAccent, if (isLightUi) 0.04f else 0.12f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(cx - dp(2).toFloat(), thumbY, cx + dp(2).toFloat(), bottom, dp(2).toFloat(), dp(2).toFloat(), activePaint)
            activePaint.shader = null

            val cap = android.graphics.RectF(cx - dp(17).toFloat(), thumbY - dp(9).toFloat(), cx + dp(17).toFloat(), thumbY + dp(9).toFloat())
            capPaint.shader = android.graphics.LinearGradient(
                cap.left, cap.top, cap.left, cap.bottom,
                if (isLightUi) Color.WHITE else lighten(themeSurface, 0.32f),
                if (isLightUi) Color.parseColor("#D6DFEA") else darken(themeSurface, 0.05f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(cap, dp(6).toFloat(), dp(6).toFloat(), capPaint)
            capPaint.shader = null

            capStrokePaint.style = android.graphics.Paint.Style.STROKE
            capStrokePaint.strokeWidth = dp(1).toFloat()
            capStrokePaint.color = if (isLightUi) Color.parseColor("#7E8CA1") else 0xCCB9C8DD.toInt()
            canvas.drawRoundRect(cap, dp(6).toFloat(), dp(6).toFloat(), capStrokePaint)
            capStrokePaint.style = android.graphics.Paint.Style.FILL
            glossPaint.color = if (isLightUi) 0xBFFFFFFF.toInt() else 0x72FFFFFF
            canvas.drawRoundRect(cap.left + dp(5), cap.top + dp(3), cap.right - dp(5), cap.top + dp(5), dp(1).toFloat(), dp(1).toFloat(), glossPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y; lastX = event.x
                    verticalDrag = false; horizontalGesture = false
                    gainChangedInGesture = false
                    velocityTracker?.recycle()
                    velocityTracker = android.view.VelocityTracker.obtain().also { it.addMovement(event) }
                    // Keep both parent scroll containers from stealing this fader gesture;
                    // horizontal drags are routed to the band viewport and flung on release.
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dx = kotlin.math.abs(event.x - downX)
                    val dy = kotlin.math.abs(event.y - downY)
                    if (!verticalDrag && !horizontalGesture && maxOf(dx, dy) > touchSlop) {
                        if (dy >= dx) {
                            verticalDrag = true
                        } else {
                            horizontalGesture = true
                        }
                    }
                    if (verticalDrag) applyTouchY(event.y)
                    if (horizontalGesture) {
                        bandViewport?.scrollBy((lastX - event.x).roundToInt(), 0)
                    }
                    lastX = event.x
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    velocityTracker?.addMovement(event)
                    if (verticalDrag) {
                        applyTouchY(event.y)
                    } else if (horizontalGesture) {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val fingerVelocity = velocityTracker?.xVelocity?.roundToInt() ?: 0
                        if (kotlin.math.abs(fingerVelocity) >= minimumFlingVelocity) {
                            bandViewport?.fling(-fingerVelocity)
                        }
                    } else {
                        applyTouchY(event.y)
                        performClick()
                    }
                    if (gainChangedInGesture) onGainCommitted?.invoke(gainMb)
                    velocityTracker?.recycle(); velocityTracker = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    verticalDrag = false; horizontalGesture = false
                    gainChangedInGesture = false
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // ACTION_CANCEL can occur when the window loses focus; persist the
                    // last engine-confirmed value so the next Activity open stays in sync.
                    if (gainChangedInGesture) onGainCommitted?.invoke(gainMb)
                    velocityTracker?.recycle(); velocityTracker = null
                    parent?.requestDisallowInterceptTouchEvent(false)
                    verticalDrag = false; horizontalGesture = false
                    gainChangedInGesture = false
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean { super.performClick(); return true }
    }

    /** Theme-aware frosted vertical scroll thumb for the full EQ settings page. */
    private inner class EqVerticalIndicatorView(
        private val host: ScrollView,
        private val showWhenNoOverflow: Boolean = false
    ) : View(this@EqualizerActivity) {
        private val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val haloPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val glossPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var cachedAccent = Int.MIN_VALUE
        private var cachedLight = false
        private var bodyShader: android.graphics.Shader? = null
        private val density = resources.displayMetrics.density.coerceAtLeast(0.1f)

        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
            // Keep the rail in the view tree from its first frame. The previous
            // host.windowVisibility/isShown gate could evaluate during Activity
            // launch while Android was still transitioning the window, set GONE,
            // and never receive a reliable visibility callback until EQ/theme changed.
            visibility = View.VISIBLE
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshMetrics() }
            host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshMetrics() }
            host.getChildAt(0)?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshMetrics() }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            visibility = View.VISIBLE
            postOnAnimation { refreshMetrics() }
        }

        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            if (visibility == View.VISIBLE) postOnAnimation { refreshMetrics() }
        }

        private fun metrics(): Triple<Float, Float, Float> {
            val child = host.getChildAt(0) ?: return Triple(0f, 0f, 0f)
            val contentHeight = maxOf(child.height, child.measuredHeight).toFloat()
            val viewportHeight = (host.height - host.paddingTop - host.paddingBottom).toFloat().coerceAtLeast(1f)
            return Triple(contentHeight, viewportHeight, (contentHeight - viewportHeight).coerceAtLeast(0f))
        }

        fun refreshMetrics() {
            // Activity/window visibility is intentionally not used as a gate:
            // it may still be INVISIBLE during the opening transition. Android
            // will draw this sibling when the window is presented; the measured
            // viewport decides only the thumb size, never whether the rail exists.
            if (visibility != View.VISIBLE) visibility = View.VISIBLE
            invalidate()
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val (contentHeight, viewportHeight, range) = metrics()
            if (width <= 0 || height <= 0 || visibility != View.VISIBLE) return
            if (range <= 1f && !showWhenNoOverflow) return
            // Draw in the full-window shell rather than the padded scroll frame.
            // The cards end before the shell's outer gutter, so the glass rail is
            // visually detached from Liquid Glass and mirrors MainActivity's edge rail.
            val top = (6f * density).coerceAtMost(height.toFloat() - 28f * density)
            val bottom = (height.toFloat() - 6f * density).coerceAtLeast(top + 28f * density)
            val trackHeight = (bottom - top).coerceAtLeast(1f)
            val x = width.toFloat() - 6.5f * density
            val accent = themeAccent
            val light = isLightUi

            trackPaint.shader = null
            trackPaint.color = if (light) Color.argb(70, 56, 72, 96) else Color.argb(86, 220, 231, 248)
            canvas.drawRoundRect(x - 1.5f * density, top, x + 1.5f * density, bottom, 3f * density, 3f * density, trackPaint)

            val thumbHeight = if (range <= 1f) trackHeight else
                (trackHeight * viewportHeight / contentHeight.coerceAtLeast(1f)).coerceIn(
                    minOf(34f * density, trackHeight), trackHeight
                )
            val fraction = if (range <= 1f) 0f else (host.scrollY.toFloat() / range).coerceIn(0f, 1f)
            val thumbTop = top + (trackHeight - thumbHeight) * fraction
            val thumbBottom = thumbTop + thumbHeight
            haloPaint.shader = null
            haloPaint.color = Color.argb(72, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(x - 4.1f * density, thumbTop - density, x + 4.1f * density, thumbBottom + density,
                4.2f * density, 4.2f * density, haloPaint)
            if (cachedAccent != accent || cachedLight != light || bodyShader == null) {
                cachedAccent = accent
                cachedLight = light
                bodyShader = android.graphics.LinearGradient(
                    x - 2.3f * density, 0f, x + 2.3f * density, 0f,
                    Color.rgb((Color.red(accent) + 255) / 2, (Color.green(accent) + 255) / 2, (Color.blue(accent) + 255) / 2),
                    accent, android.graphics.Shader.TileMode.CLAMP
                )
            }
            bodyPaint.shader = bodyShader
            canvas.drawRoundRect(x - 2.2f * density, thumbTop, x + 2.2f * density, thumbBottom,
                4f * density, 4f * density, bodyPaint)
            bodyPaint.shader = null
            glossPaint.shader = null
            glossPaint.color = Color.argb(if (light) 180 else 144, 255, 255, 255)
            canvas.drawRoundRect(x - 1.2f * density, thumbTop + 4f * density, x - 0.3f * density,
                (thumbBottom - 4f * density).coerceAtLeast(thumbTop + 4f * density), density, density, glossPaint)
        }
    }

    private inner class EqHorizontalIndicatorView(
        private val host: HorizontalScrollView
    ) : View(this@EqualizerActivity) {
        private val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val haloPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val glossPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var cachedAccent = Int.MIN_VALUE
        private var cachedLight = false
        private var bodyShader: android.graphics.Shader? = null
        private val hostLocation = IntArray(2)
        private val indicatorLocation = IntArray(2)

        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshMetrics() }
            host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshMetrics() }
            host.getChildAt(0)?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshMetrics() }
        }

        private fun metrics(): Triple<Float, Float, Float> {
            val child = host.getChildAt(0) ?: return Triple(0f, 0f, 0f)
            val margins = child.layoutParams as? ViewGroup.MarginLayoutParams
            val content = (maxOf(child.width, child.measuredWidth) + (margins?.leftMargin ?: 0) + (margins?.rightMargin ?: 0)).toFloat()
            val viewport = (host.width - host.paddingLeft - host.paddingRight).toFloat().coerceAtLeast(1f)
            return Triple(content, viewport, (content - viewport).coerceAtLeast(0f))
        }

        fun refreshMetrics() {
            val overflow = metrics().third > dp(1).toFloat()
            visibility = if (bandScrollable && host.isShown && host.width > 0 && overflow) View.VISIBLE else View.GONE
            invalidate()
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val (contentWidth, viewportWidth, range) = metrics()
            if (range <= 1f || width <= 0 || height <= 0 || visibility != View.VISIBLE) return
            host.getLocationOnScreen(hostLocation)
            getLocationOnScreen(indicatorLocation)
            val hostLeft = (hostLocation[0] - indicatorLocation[0]).toFloat()
            val inset = dp(9).toFloat()
            val rawLeft = hostLeft + host.paddingLeft + inset
            val rawRight = hostLeft + host.width - host.paddingRight - inset
            if (rawRight <= 0f || rawLeft >= width.toFloat()) return
            val trackLeft = rawLeft.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
            val trackRight = rawRight.coerceIn(trackLeft + 1f, width.toFloat())
            val trackWidth = (trackRight - trackLeft).coerceAtLeast(1f)
            val centerY = height / 2f
            val light = isLightUi
            val accent = themeAccent
            val highlight = Color.rgb((Color.red(accent) + 255) / 2, (Color.green(accent) + 255) / 2, (Color.blue(accent) + 255) / 2)

            trackPaint.shader = null
            trackPaint.color = if (light) Color.argb(74, 59, 76, 101) else Color.argb(92, 220, 231, 248)
            canvas.drawRoundRect(trackLeft, centerY - dp(1).toFloat(), trackRight, centerY + dp(1).toFloat(), dp(3).toFloat(), dp(3).toFloat(), trackPaint)

            val proportionalWidth = trackWidth * (viewportWidth / contentWidth.coerceAtLeast(1f))
            val thumbMaxWidth = (trackWidth * 0.56f).coerceAtLeast(1f)
            val thumbMinWidth = minOf(dp(24).toFloat(), thumbMaxWidth)
            val thumbWidth = proportionalWidth.coerceIn(thumbMinWidth, thumbMaxWidth)
            val fraction = (host.scrollX.toFloat() / range).coerceIn(0f, 1f)
            val thumbLeft = trackLeft + (trackWidth - thumbWidth) * fraction
            val thumbRight = thumbLeft + thumbWidth

            haloPaint.shader = null
            haloPaint.color = Color.argb(if (light) 62 else 76, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(thumbLeft - dp(1), centerY - dp(6).toFloat(), thumbRight + dp(1), centerY + dp(6).toFloat(), dp(7).toFloat(), dp(7).toFloat(), haloPaint)
            if (cachedAccent != accent || cachedLight != light || bodyShader == null) {
                cachedAccent = accent
                cachedLight = light
                bodyShader = android.graphics.LinearGradient(
                    0f, centerY - dp(3).toFloat(), 0f, centerY + dp(3).toFloat(),
                    highlight, if (light) accent else darken(accent, 0.08f), android.graphics.Shader.TileMode.CLAMP
                )
            }
            bodyPaint.shader = bodyShader
            canvas.drawRoundRect(thumbLeft, centerY - dp(3).toFloat(), thumbRight, centerY + dp(3).toFloat(), dp(6).toFloat(), dp(6).toFloat(), bodyPaint)
            bodyPaint.shader = null
            glossPaint.shader = null
            glossPaint.color = Color.argb(if (light) 178 else 140, 255, 255, 255)
            canvas.drawRoundRect(thumbLeft + dp(4), centerY - dp(2).toFloat(), (thumbRight - dp(4)).coerceAtLeast(thumbLeft + dp(4)), centerY - dp(1).toFloat(), dp(1).toFloat(), dp(1).toFloat(), glossPaint)
        }
    }

    private fun handlePreampApplyFailure() {
        val reason = EqEngineController.lastError ?: uiText("Preamp gagal diterapkan.", "Preamp could not be applied.")
        val mustStopEngine = EqEngineController.wasOutputBypassedByPreampFault()
        active.preampEnabled = false
        active.preampDb = if (EqEngineController.preampGainDb().isFinite()) EqEngineController.preampGainDb() else 0f
        suppress = true
        preampSwitch.isChecked = false
        preampSlider.progress = ((active.preampDb + 12f) * 10f).roundToInt().coerceIn(0, 180)
        suppress = false
        updatePreampValue(active.preampDb, false)
        persistCurrentState(force = true)
        markWorkingStateSelected()
        if (mustStopEngine) {
            getSharedPreferences("eq_presets_v1", MODE_PRIVATE).edit().putBoolean("engine_enabled", false).apply()
            suppress = true; masterSwitch.isChecked = false; suppress = false
            EqEngineController.disableAll()
            stopService(Intent(this, com.floatingdj.visualizer.eq.service.EqEngineService::class.java))
            EqEngineController.release()
        }
        refreshCapability()
        toast(reason)
    }

    private fun showEffectValueDialog(
        title: String,
        description: String,
        initialValue: Float,
        minValue: Float,
        maxValue: Float,
        suffix: String,
        decimals: Int,
        onPreview: ((Float) -> Float?)? = null,
        onRestore: ((Float) -> Unit)? = null,
        onSave: (Float) -> Boolean
    ) {
        val baseline = initialValue.coerceIn(minValue, maxValue)
        var latestPreview = baseline
        var committed = false
        var restored = false
        var updating = false

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = panelDrawable(20)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleStack.addView(label(title, 11f, muted, true))
        val mainValue = label("", 23f, themeText, true)
        titleStack.addView(mainValue)
        header.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f))
        val close = label("×", 25f, themeText, true).apply {
            gravity = Gravity.CENTER
            background = panelDrawable(12)
            isClickable = true
            isFocusable = true
            contentDescription = uiText("Batalkan dan tutup", "Discard and close")
            setPadding(dp(12), dp(3), dp(12), dp(3))
        }
        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(42)))
        panel.addView(header)

        val descriptionLabel = label(description, 10.5f, muted).apply {
            setPadding(0, dp(8), 0, dp(3))
        }
        panel.addView(descriptionLabel)
        val rangeLabel = label("", 10.5f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        panel.addView(rangeLabel)

        val valueInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setSelectAllOnFocus(true)
            showSoftInputOnFocus = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            textSize = 23f * textScale
            setTypeface(null, Typeface.BOLD)
            setTextColor(themeText)
            setHintTextColor(muted)
            gravity = Gravity.CENTER
            background = roundedGradient(
                if (isLightUi) Color.parseColor("#F7F9FC") else darken(themeSurface, 0.12f),
                if (isLightUi) Color.parseColor("#E8EEF6") else Color.parseColor("#111C2C"),
                themeEdge, 13
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        panel.addView(valueInput, LinearLayout.LayoutParams(-1, dp(58)))

        val previewSlider = SeekBar(this).apply {
            max = ((maxValue - minValue) * if (decimals == 0) 1f else 10f).roundToInt().coerceAtLeast(1)
            progress = ((baseline - minValue) * if (decimals == 0) 1f else 10f).roundToInt().coerceIn(0, max)
        }
        applyThumb(previewSlider)
        panel.addView(previewSlider, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(10)
            bottomMargin = dp(2)
        })
        val liveInfo = label("", 10f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        }
        panel.addView(liveInfo)

        fun formatValue(value: Float): String = String.format(
            java.util.Locale.US,
            if (decimals == 0) "%.0f %s" else "%.1f %s",
            value, suffix
        )
        fun updatePreview(value: Float, fromEngine: Boolean): Boolean {
            if (!value.isFinite() || value !in minValue..maxValue) return false
            val applied = if (onPreview == null) value else onPreview.invoke(value) ?: return false
            latestPreview = applied.coerceIn(minValue, maxValue)
            mainValue.text = formatValue(latestPreview)
            val scale = if (decimals == 0) 1f else 10f
            updating = true
            previewSlider.progress = ((latestPreview - minValue) * scale).roundToInt().coerceIn(0, previewSlider.max)
            updating = false
            liveInfo.text = if (fromEngine) {
                uiText("Pratinjau engine · ${formatValue(latestPreview)} · belum disimpan", "Engine preview · ${formatValue(latestPreview)} · not saved yet")
            } else {
                uiText("Pratinjau nilai · belum disimpan", "Value preview · not saved yet")
            }
            return true
        }
        fun restoreBaseline() {
            if (restored || committed) return
            restored = true
            try { onRestore?.invoke(baseline) } catch (_: Throwable) { }
            latestPreview = baseline
        }

        updating = true
        valueInput.setText(String.format(java.util.Locale.US, if (decimals == 0) "%.0f" else "%.1f", baseline))
        valueInput.setSelection(valueInput.text.length)
        updating = false
        mainValue.text = formatValue(baseline)
        rangeLabel.text = uiText("MIN ${formatValue(minValue)}  ·  MAX ${formatValue(maxValue)}", "MIN ${formatValue(minValue)}  ·  MAX ${formatValue(maxValue)}")
        liveInfo.text = uiText("Pratinjau langsung · pilih Simpan untuk menerapkan permanen.", "Live preview · choose Save to commit permanently.")

        valueInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (updating) return
                val raw = s?.toString()?.trim()?.replace(',', '.') ?: return
                val parsed = raw.toFloatOrNull() ?: return
                if (!parsed.isFinite() || parsed !in minValue..maxValue) {
                    valueInput.error = uiText("Masukkan ${formatValue(minValue)} sampai ${formatValue(maxValue)}", "Enter ${formatValue(minValue)} to ${formatValue(maxValue)}")
                    return
                }
                valueInput.error = null
                if (updatePreview(parsed, onPreview != null)) {
                    val scale = if (decimals == 0) 1f else 10f
                    updating = true
                    previewSlider.progress = ((latestPreview - minValue) * scale).roundToInt().coerceIn(0, previewSlider.max)
                    updating = false
                } else {
                    valueInput.error = EqEngineController.lastError ?: uiText("Effect tidak dapat menerima nilai ini.", "The effect rejected this value.")
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        previewSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || updating) return
                val scale = if (decimals == 0) 1f else 10f
                val requested = minValue + progress / scale
                if (updatePreview(requested, onPreview != null)) {
                    updating = true
                    valueInput.setText(String.format(java.util.Locale.US, if (decimals == 0) "%.0f" else "%.1f", latestPreview))
                    valueInput.setSelection(valueInput.text.length)
                    valueInput.error = null
                    updating = false
                } else {
                    valueInput.error = EqEngineController.lastError ?: uiText("Effect tidak dapat menerima nilai ini.", "The effect rejected this value.")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        valueInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(valueInput.windowToken, 0)
                true
            } else false
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun actionButton(symbol: String, caption: String, onClick: () -> Unit): TextView =
            label("$symbol\n$caption", 10f, themeText, true).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                background = panelDrawable(12)
                setPadding(dp(5), dp(8), dp(5), dp(8))
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        val back = actionButton("↩", uiText("KEMBALI", "BACK")) { dialog.dismiss() }
        val save = actionButton("✓", uiText("SIMPAN", "SAVE")) {
            val raw = valueInput.text.toString().trim().replace(',', '.')
            val requested = raw.toFloatOrNull()?.takeIf { it.isFinite() && it in minValue..maxValue }
            if (requested == null) {
                valueInput.error = uiText("Masukkan nilai ${formatValue(minValue)} sampai ${formatValue(maxValue)}", "Enter ${formatValue(minValue)} to ${formatValue(maxValue)}")
                return@actionButton
            }
            if (onSave(requested)) {
                committed = true
                dialog.dismiss()
            } else {
                valueInput.error = EqEngineController.lastError ?: uiText("Nilai tidak dapat diterapkan oleh audio engine.", "The audio engine could not apply this value.")
            }
        }
        actions.addView(back, LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(5) })
        actions.addView(save, LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginStart = dp(5) })
        panel.addView(actions, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(8) })
        close.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { restoreBaseline() }
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            valueInput.requestFocus()
            valueInput.post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(valueInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
    }

    private fun simpleListener(update: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) update(progress) }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }
    private fun registerEqMasterDependentControl(view: View) {
        if (eqMasterDependentControls.none { it === view }) eqMasterDependentControls.add(view)
    }

    private fun setViewTreeEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) setViewTreeEnabled(view.getChildAt(index), enabled)
        }
    }

    /** Gate all user-editable EQ controls from the master engine state. */
    private fun updateEqMasterControlGate(enabled: Boolean) {
        if (!enabled) presetPopup?.dismiss()
        eqMasterDependentControls.forEach { control ->
            setViewTreeEnabled(control, enabled)
            control.alpha = if (enabled) 1f else 0.38f
        }
    }

    private fun refreshCapability() {
        val descriptors = EqEngineController.inventory()
        val eqNames = descriptors.filter { it.name.contains("equal", true) }
        val actual = EqEngineController.bands()
        if (actual.isNotEmpty()) ensureCurrentProfileHasBands(actual)
        val cachedCount = getSharedPreferences("eq_presets_v1", MODE_PRIVATE).getInt("detected_band_count", 0)
        val resolvedPresetBandCount = actual.size.takeIf { it in 1..32 } ?: cachedCount
        if (resolvedPresetBandCount != detectedPresetBandCount) loadPresets()
        val activeNow = EqEngineController.isActive()
        updateEqMasterControlGate(activeNow)
        // Migrate old working profiles away from the unsafe global Preamp flag.
        // Keep the requested dB as dormant data, but never reapply it to session 0.
        if (!EqEngineController.preampAvailable() && active.preampEnabled) {
            active.preampEnabled = false
            if (active.id == currentStateEntryId) persistCurrentState()
        }
        suppress = true; masterSwitch.isChecked = activeNow; suppress = false
        val baseStatus = if (actual.isNotEmpty()) {
            "${EqEngineController.backendName()} · ${actual.size} ${uiText("band aktif", "active bands")} · control=${EqEngineController.hasControl()} · ${if (activeNow) "ACTIVE" else "DISABLED"}\n${if (EqEngineController.backendName().contains("NATIVE")) EqEngineController.lastError.orEmpty() + "\n" else ""}${uiText("Rute", "Route")}: ${EqAudioRouteMonitor.currentOutputs(this).joinToString().ifBlank { "Unknown" }}\n${uiText("Output mix bersifat eksperimental; bukan jaminan semua aplikasi atau rute audio diproses.", "Output mix is experimental; not a guarantee that every app or audio route is processed.")}"
        } else {
            uiText("Engine EQ belum aktif. Descriptor Equalizer: ${eqNames.size}.\n${EqEngineController.lastError ?: "Output session belum terverifikasi."}", "No active EQ engine. Equalizer descriptors: ${eqNames.size}.\n${EqEngineController.lastError ?: "Output session is not verified."}")
        }
        status.text = if (profileMappingWarning.isNullOrBlank()) baseStatus else "$baseStatus\n$profileMappingWarning"
        bassSwitch.isEnabled = activeNow && EqEngineController.bassAvailable()
        bassSlider.isEnabled = activeNow && EqEngineController.bassStrengthSupported()
        bassValue.isEnabled = activeNow && EqEngineController.bassStrengthSupported()
        bassValue.alpha = if (bassValue.isEnabled) 1f else 0.56f
        bassSwitch.alpha = if (bassSwitch.isEnabled) 1f else 0.54f
        virtualSwitch.isEnabled = activeNow && EqEngineController.virtualizerAvailable()
        virtualSlider.isEnabled = activeNow && EqEngineController.virtualizerStrengthSupported()
        virtualValue.isEnabled = activeNow && EqEngineController.virtualizerStrengthSupported()
        virtualValue.alpha = if (virtualValue.isEnabled) 1f else 0.56f
        virtualSwitch.alpha = if (virtualSwitch.isEnabled) 1f else 0.54f
        updateStrengthValue(bassValue, EqEngineController.bassStrength().takeIf { it >= 0 } ?: active.bassStrength, EqEngineController.bassEnabled(), EqEngineController.bassStrengthSupported())
        updateStrengthValue(virtualValue, EqEngineController.virtualizerStrength().takeIf { it >= 0 } ?: active.virtualizerStrength, EqEngineController.virtualizerEnabled(), EqEngineController.virtualizerStrengthSupported())
        preampSwitch.isEnabled = activeNow && EqEngineController.preampAvailable()
        preampSlider.isEnabled = activeNow && EqEngineController.preampAvailable()
        preampValue.isEnabled = activeNow && EqEngineController.preampAvailable()
        preampValue.alpha = if (preampSwitch.isEnabled) 1f else 0.54f
        preampSwitch.alpha = if (preampSwitch.isEnabled) 1f else 0.54f
        preampSwitch.contentDescription = when {
            !activeNow -> uiText("Preamp terkunci karena EQ OFF", "Preamp unavailable while EQ is off")
            EqEngineController.preampAvailable() -> uiText("Aktifkan Preamp", "Enable Preamp")
            else -> uiText("Preamp dinonaktifkan demi keamanan output mix", "Preamp disabled for output-mix safety")
        }
        (preampSwitch.parent as? View)?.alpha = if (activeNow && !EqEngineController.preampAvailable()) 0.58f else if (!activeNow) 0.62f else 1f

        suppress = true
        preampSwitch.isChecked = active.preampEnabled && EqEngineController.preampAvailable()
        val appliedPreamp = if (EqEngineController.preampAvailable() && active.preampEnabled) EqEngineController.preampGainDb() else active.preampDb
        preampSlider.progress = ((active.preampDb + 12f) * 10f).roundToInt().coerceIn(0, 180)
        updatePreampValue(appliedPreamp, active.preampEnabled && EqEngineController.preampAvailable())
        if (!activeNow) {
            preampValue.text = uiText("${String.format(java.util.Locale.US, "%.1f dB", active.preampDb)} · EQ OFF", "${String.format(java.util.Locale.US, "%.1f dB", active.preampDb)} · EQ OFF")
        } else if (!EqEngineController.preampAvailable()) {
            preampValue.text = uiText(
                "${String.format(java.util.Locale.US, "%.1f dB", active.preampDb)} · TIDAK DITERAPKAN",
                "${String.format(java.util.Locale.US, "%.1f dB", active.preampDb)} · NOT APPLIED"
            )
        }
        limiterSwitch.isEnabled = activeNow && EqEngineController.limiterAvailable()
        limiterSwitch.alpha = if (limiterSwitch.isEnabled) 1f else 0.54f
        limiterValueLabel.alpha = if (limiterSwitch.isEnabled) 1f else 0.56f
        limiterValueLabel.text = if (EqEngineController.limiterAvailable()) "−1 dBFS  ·  20:1" else uiText("Tidak tersedia", "Unavailable")
        limiterSwitch.isChecked = EqEngineController.limiterEnabled()
        suppress = false
        limiterSwitch.contentDescription = when {
            !activeNow -> uiText("Limiter terkunci karena EQ OFF", "Limiter unavailable while EQ is off")
            EqEngineController.limiterAvailable() -> uiText("Aktifkan Dynamic Limiter, threshold -1 dBFS", "Enable Dynamic Limiter, threshold -1 dBFS")
            else -> uiText("Limiter tidak tersedia pada engine ini", "Limiter is unavailable on this engine")
        }
        reverbInfo.text = buildReverbCapabilityMessage()
        val reverbPost = com.floatingdj.visualizer.eq.core.EffectCapabilityProbe.reverbPostProcessingDescriptor()
        val reverbAux = com.floatingdj.visualizer.eq.core.EffectCapabilityProbe.reverbAuxiliaryDescriptor()
        reverbSwitch.isChecked = false
        reverbSwitch.isEnabled = false
        reverbSwitch.alpha = 0.72f
        reverbInfo.alpha = 0.78f
        reverbSwitch.contentDescription = uiText("Reverb dinonaktifkan: jalur output eksternal tidak menyediakan attachment player.", "Reverb disabled: this external output path does not expose player attachment.")
        // Keep the switch itself visually clean; the adjacent disabled value chip
        // and multi-line description carry the capability information.
        reverbSwitch.contentDescription = when {
            reverbPost != null -> uiText("Reverb terdeteksi, memerlukan akses sistem/OEM", "Reverb detected, requires system/OEM access")
            reverbAux != null -> uiText("Reverb terdeteksi, memerlukan player attach/send", "Reverb detected, requires player attachment/send")
            else -> uiText("Reverb tidak didukung atau tidak terdeteksi", "Reverb unsupported or not detected")
        }
        bandHint.visibility = View.VISIBLE
        bandHint.text = when {
            actual.size > 5 -> uiText("BAND ANALYZER · ${actual.size} BAND AKTIF · Geser kiri/kanan untuk melihat band lainnya. Tepi kaca menandai batas canvas.", "BAND ANALYZER · ${actual.size} ACTIVE BANDS · Swipe left/right to view the remaining bands. Glass edge rails mark the canvas limits.")
            actual.isNotEmpty() -> uiText("BAND ANALYZER · ${actual.size} BAND AKTIF · Setiap band dapat di-reset sendiri ke 0 dB.", "BAND ANALYZER · ${actual.size} ACTIVE BANDS · Each band can be reset individually to 0 dB.")
            detectedPresetBandCount > 0 -> uiText("BAND ANALYZER · ${detectedPresetBandCount} BAND TERAKHIR TERDETEKSI · Aktifkan EQ untuk scan ulang engine saat ini.", "BAND ANALYZER · ${detectedPresetBandCount} BANDS LAST DETECTED · Enable EQ to scan the current engine again.")
            else -> uiText("BAND ANALYZER · BELUM TERDETEKSI · Aktifkan EQ untuk membaca jumlah band yang benar-benar didukung perangkat.", "BAND ANALYZER · NOT DETECTED · Enable EQ to read the band's actual device-supported count.")
        }
        if (actual.isNotEmpty()) {
            bandViewport?.visibility = View.VISIBLE
            emptyBandMessage.visibility = View.GONE
            buildBands(actual)
        } else {
            bandScrollable = false
            bandsRow.removeAllViews()
            bandViewport?.visibility = View.GONE
            emptyBandMessage.visibility = View.VISIBLE
            updateBandRails()
        }
        updateMasterStateBadge(activeNow)
        updatePresetStateStatus()
        applyThemeStudio()
    }
    private fun updatePreampValue(db: Float, enabled: Boolean) {
        if (!::preampValue.isInitialized) return
        val safeDb = db.coerceIn(-12f, 6f)
        val formatted = String.format(java.util.Locale.US, "%.1f dB", safeDb)
        val rangePercent = (((safeDb + 12f) / 18f) * 100f).roundToInt().coerceIn(0, 100)
        preampValue.text = if (enabled) "$formatted · $rangePercent% ${uiText("RENTANG", "RANGE")} · ${uiText("AKTIF", "ACTIVE")}" else "$formatted · $rangePercent% ${uiText("RENTANG", "RANGE")} · ${uiText("OFF / tersimpan", "OFF / saved")}"
    }

    private fun buildReverbCapabilityMessage(): String {
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        val post = com.floatingdj.visualizer.eq.core.EffectCapabilityProbe.reverbPostProcessingDescriptor()
        val auxiliary = com.floatingdj.visualizer.eq.core.EffectCapabilityProbe.reverbAuxiliaryDescriptor()
        return when {
            post != null -> if (language == "id") "${device}\nDescriptor Reverb post-processing vendor terdeteksi (${post.implementor}), tetapi kontrol device-route memerlukan integrasi sistem/OEM. Aplikasi biasa tidak dapat mengaktifkannya secara global." else "${device}\nVendor post-processing Reverb descriptor detected (${post.implementor}), but device-route control requires system/OEM integration. A regular app cannot enable it globally."
            auxiliary != null -> if (language == "id") "${device}\nAuxiliary Reverb terdeteksi (${auxiliary.implementor}). Player AudioTrack/MediaPlayer harus attach ke effect dan mengirim send level; tidak otomatis memproses audio aplikasi lain." else "${device}\nAuxiliary Reverb detected (${auxiliary.implementor}). An AudioTrack/MediaPlayer must attach the effect and send a level; it does not automatically process other apps."
            else -> if (language == "id") "${device}\nDescriptor Reverb tidak ditemukan. Kontrol global dinonaktifkan; dukungan player-owned memerlukan jalur playback milik aplikasi." else "${device}\nNo Reverb descriptor found. Global control is disabled; player-owned support requires an app-owned playback path."
        }
    }

    private fun markWorkingStateSelected() {
        if (::presetSpinner.isInitialized && spinnerEntries.isNotEmpty()) {
            selectedPresetIndex = 0
            updatePresetDropdownLabel()
        }
        if (::presetStateStatus.isInitialized) updatePresetStateStatus()
    }

    private fun isWorkingStateDirty(): Boolean {
        if (active.id == currentStateEntryId) return false
        val baseline = profiles.firstOrNull { it.id == active.id }
            ?: profiles.firstOrNull { it.id == "flat" }
            ?: EqProfile("flat", "Flat")
        val currentMap = active.centersHz.zip(active.gainDb).toMap()
        val baselineMap = baseline.centersHz.zip(baseline.gainDb).toMap()
        val frequencies = currentMap.keys + baselineMap.keys
        val bandsDiffer = frequencies.any { hz -> kotlin.math.abs((currentMap[hz] ?: 0f) - (baselineMap[hz] ?: 0f)) > 0.01f }
        // Unsupported Preamp is deliberately migrated to OFF; that capability
        // migration is not a user edit and must not dirty/replace Current State.
        val preampDiff = EqEngineController.preampAvailable() && (
            active.preampEnabled != baseline.preampEnabled ||
                kotlin.math.abs(active.preampDb - baseline.preampDb) > 0.01f
            )
        return bandsDiffer || active.bassEnabled != baseline.bassEnabled || active.bassStrength != baseline.bassStrength ||
            active.virtualizerEnabled != baseline.virtualizerEnabled || active.virtualizerStrength != baseline.virtualizerStrength ||
            active.limiterEnabled != baseline.limiterEnabled || preampDiff
    }

    private fun ensureCurrentProfileHasBands(bands: List<EqEngineController.Band>) {
        bands.forEach { band ->
            if (active.centersHz.indexOf(band.centerHz) < 0) {
                active.centersHz.add(band.centerHz)
                active.gainDb.add(band.levelMb / 100f)
            }
        }
    }

    private fun persistCurrentState(captureFromEngine: Boolean = false, force: Boolean = false) {
        try {
            // Activity pause/destroy is a safety net for real edits, not a reason
            // to replace Current State merely because a different saved preset
            // happens to be active. Explicit Flat reset uses force=true.
            if (!force && active.id != currentStateEntryId && !isWorkingStateDirty()) return
            if (captureFromEngine) captureCurrent()
            // Current State is an independent working snapshot. Editing a loaded
            // preset must never overwrite that saved preset in the library.
            repo.saveCurrentState(active.copy(
                id = currentStateEntryId,
                name = uiText("Current State", "Current State"),
                centersHz = active.centersHz.toMutableList(),
                gainDb = active.gainDb.toMutableList()
            ))
            if (::presetStateStatus.isInitialized) updatePresetStateStatus()
        } catch (_: Throwable) {
            // Persistence is best-effort; never interrupt real-time audio slider interaction.
        }
    }

    private fun buildBands(bands: List<EqEngineController.Band>) {
        bandsRow.removeAllViews()
        bandUiControls.clear()
        bandScrollable = bands.size > 5
        val screenWidthDp = resources.configuration.screenWidthDp.toFloat().coerceAtLeast(300f)
        // Five columns remain visible at once. Ten-band engines use two smooth pages.
        val slotWidth = ((screenWidthDp - 66f) / 5f).coerceIn(48f, 72f)
        val columnWidth = if (bandScrollable) slotWidth
            else ((screenWidthDp - 66f) / bands.size.coerceAtLeast(1)).coerceIn(48f, 82f)
        val viewport = bandViewport
        viewport?.isFillViewport = !bandScrollable
        viewport?.scrollTo(0, 0)
        bandsRow.gravity = if (bandScrollable) Gravity.CENTER_VERTICAL or Gravity.LEFT else Gravity.CENTER
        bandsRow.layoutParams = FrameLayout.LayoutParams(
            if (bandScrollable) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (bandScrollable) Gravity.LEFT else Gravity.CENTER
        )
        bandCanvas.layoutParams = bandCanvas.layoutParams.apply { height = dp(if (bandScrollable) 492 else 472) }

        bands.forEachIndexed { bandPosition, band ->
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(3), dp(7), dp(3), dp(7))
                clipChildren = false
                clipToPadding = false
            }
            val value = label(String.format(java.util.Locale.US, "%.1f dB", band.levelMb / 100f), 10f, themeText, true).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                maxLines = 1
                minHeight = dp(31)
                setPadding(dp(3), dp(3), dp(3), dp(3))
                background = roundedGradient(
                    if (isLightUi) Color.parseColor("#F7F9FC") else lighten(themeSurface, 0.10f),
                    if (isLightUi) Color.parseColor("#E7EDF5") else darken(themeSurface, 0.11f),
                    if (isLightUi) Color.parseColor("#AAB6C7") else 0x667D91B8, 12
                )
                isClickable = true
                isFocusable = true
                contentDescription = uiText("Ketuk untuk mengatur gain ${band.centerHz} Hz", "Tap to edit ${band.centerHz} Hz gain")
                setOnClickListener { showBandGainDialog(bandPosition) }
            }
            column.addView(value, LinearLayout.LayoutParams(-1, dp(32)))

            val fader = MixerFaderView(band)
            fader.apply {
                contentDescription = uiText("Fader ${band.centerHz} Hz. Geser vertikal untuk mengubah gain.", "${band.centerHz} Hz fader. Drag vertically to change gain.")
                setGainMb(band.levelMb)
                onGainChanged = { requestedMb ->
                    // IMPORTANT: the fader must write to the native effect handle,
                    // not only update the label/profile. Input-value editing already
                    // used this controller path; this fixes the missing fader route.
                    val appliedMb = EqEngineController.setBand(band.index, requestedMb)
                    if (appliedMb == null) {
                        val actualMb = EqEngineController.bands()
                            .firstOrNull { it.index == band.index }?.levelMb ?: fader.lastConfirmedGainMb()
                        fader.setGainMb(actualMb)
                        value.text = String.format(java.util.Locale.US, "%.1f dB", actualMb / 100f)
                        updateActiveBandValue(band.centerHz, actualMb)
                        if (!faderWriteFailureShown) {
                            faderWriteFailureShown = true
                            val reason = EqEngineController.lastError
                            toast(uiText(
                                "Fader tidak dapat mengirim nilai ke audio engine${reason?.let { ": $it" } ?: ". Periksa status kontrol EQ."}",
                                "Fader could not write to the audio engine${reason?.let { ": $it" } ?: ". Check EQ control status."}"
                            ))
                        }
                    } else {
                        faderWriteFailureShown = false
                        // Use read-back from DynamicsProcessing/Equalizer so the
                        // displayed value always reflects what the effect accepted.
                        fader.setGainMb(appliedMb)
                        value.text = String.format(java.util.Locale.US, "%.1f dB", appliedMb / 100f)
                        updateActiveBandValue(band.centerHz, appliedMb)
                        markWorkingStateSelected()
                    }
                }
                onGainCommitted = {
                    // Persist once at the end of a gesture, not for every MotionEvent.
                    // onPause also captures the engine read-back as a safety net.
                    persistCurrentState(captureFromEngine = true, force = true)
                }
            }
            column.addView(fader, LinearLayout.LayoutParams(-1, dp(312)).apply {
                topMargin = dp(5)
                bottomMargin = dp(3)
            })

            val frequencyText = if (band.centerHz >= 1000) {
                val valueK = band.centerHz / 1000f
                if (valueK == valueK.toInt().toFloat()) "${valueK.toInt()}.0 kHz" else "${valueK} kHz"
            } else "${band.centerHz} Hz"
            column.addView(label(frequencyText, 9.5f, if (isLightUi) Color.parseColor("#59677D") else muted, true).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                maxLines = 1
                includeFontPadding = false
            }, LinearLayout.LayoutParams(-1, dp(25)).apply { topMargin = dp(2) })

            val reset = label("↺", 16f, themeAccent, true).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                background = roundedGradient(
                    if (isLightUi) Color.parseColor("#F7F9FC") else lighten(themeSurface, 0.07f),
                    if (isLightUi) Color.parseColor("#E7EDF5") else darken(themeSurface, 0.12f),
                    if (isLightUi) Color.parseColor("#AAB6C7") else 0x667D91B8, 11
                )
                isClickable = true
                isFocusable = true
                contentDescription = uiText("Reset band ${band.centerHz} Hz ke 0 dB", "Reset ${band.centerHz} Hz band to 0 dB")
                setOnClickListener {
                    val current = EqEngineController.bands().firstOrNull { it.index == band.index }?.levelMb ?: band.levelMb
                    val target = 0.coerceIn(band.minMb, band.maxMb)
                    showEqConfirmDialog(
                        uiText("RESET BAND ${band.centerHz} Hz?", "RESET ${band.centerHz} Hz BAND?"),
                        uiText(
                            "Nilai band akan diubah dari ${String.format(java.util.Locale.US, "%+.1f", current / 100f)} dB menjadi ${String.format(java.util.Locale.US, "%+.1f", target / 100f)} dB. Hanya band ${band.centerHz} Hz yang berubah. Pengaturan band lain tetap aman.",
                            "This band will change from ${String.format(java.util.Locale.US, "%+.1f", current / 100f)} dB to ${String.format(java.util.Locale.US, "%+.1f", target / 100f)} dB. Only the ${band.centerHz} Hz band changes; every other band stays untouched."
                        ),
                        uiText("BATAL", "CANCEL"), uiText("YA, RESET", "YES, RESET")
                    ) {
                        val applied = EqEngineController.setBand(band.index, target)
                        if (applied == null) {
                            toast(uiText("Band tidak dapat diubah pada engine saat ini", "This band cannot be changed on the current engine"))
                        } else {
                            fader.setGainMb(applied)
                            value.text = String.format(java.util.Locale.US, "%.1f dB", applied / 100f)
                            updateActiveBandValue(band.centerHz, applied)
                            persistCurrentState(force = true)
                            markWorkingStateSelected()
                        }
                    }
                }
            }
            column.addView(reset, LinearLayout.LayoutParams(dp(36), dp(34)).apply { topMargin = dp(5) })
            bandsRow.addView(column, LinearLayout.LayoutParams(dp(columnWidth.toInt()), -1))
            bandUiControls[band.index] = BandUiControl(band, fader, value)
        }
        bandRailLeft?.visibility = View.GONE
        bandRailRight?.visibility = if (bandScrollable) View.VISIBLE else View.GONE
        bandsRow.post { updateBandRails(); bandScrollIndicator?.refreshMetrics() }
        applyThemeStudio()
    }

    private fun updateActiveBandValue(centerHz: Int, appliedMb: Int) {
        val idx = active.centersHz.indexOf(centerHz)
        if (idx >= 0 && idx < active.gainDb.size) active.gainDb[idx] = appliedMb / 100f
        else {
            active.centersHz.add(centerHz)
            active.gainDb.add(appliedMb / 100f)
        }
    }

    private fun showBandGainDialog(startIndex: Int) {
        val editSessionId = ++bandEditSessionId
        bandEditDialogOpen = true
        val detectedBands = EqEngineController.bands()
        if (detectedBands.isEmpty() || startIndex !in detectedBands.indices) {
            bandEditDialogOpen = false
            return
        }
        var bandPosition = startIndex
        val initialBand = detectedBands[bandPosition]
        val initialMb = initialBand.levelMb
        var latestPreviewMb = initialMb
        var committed = false
        var restored = false
        var updatingInput = false

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = panelDrawable(20)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = label(uiText("ATUR NILAI BAND", "BAND GAIN CONTROL"), 11f, muted, true)
        val frequency = label("${initialBand.centerHz} Hz", 23f, themeText, true)
        titleStack.addView(title)
        titleStack.addView(frequency)
        header.addView(titleStack, LinearLayout.LayoutParams(0, -2, 1f))
        val close = label("×", 25f, themeText, true).apply {
            gravity = Gravity.CENTER
            background = panelDrawable(12)
            isClickable = true
            isFocusable = true
            contentDescription = uiText("Batalkan dan tutup", "Discard and close")
            setPadding(dp(12), dp(3), dp(12), dp(3))
        }
        header.addView(close, LinearLayout.LayoutParams(dp(44), dp(42)))
        panel.addView(header)

        val rangeText = label("", 11f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(12))
        }
        panel.addView(rangeText)

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setSelectAllOnFocus(true)
            showSoftInputOnFocus = true
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            textSize = 23f * textScale
            gravity = Gravity.CENTER
            setTextColor(themeText)
            setHintTextColor(muted)
            hint = "0.0 dB"
            background = roundedGradient(
                if (isLightUi) Color.parseColor("#F7F9FC") else darken(themeSurface, 0.12f),
                if (isLightUi) Color.parseColor("#E8EEF6") else Color.parseColor("#111C2C"),
                themeEdge, 13
            )
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(58)))

        val previewSlider = SeekBar(this).apply {
            max = (initialBand.maxMb - initialBand.minMb).coerceAtLeast(1)
            progress = (initialMb - initialBand.minMb).coerceIn(0, max)
        }
        applyThumb(previewSlider)
        panel.addView(previewSlider, LinearLayout.LayoutParams(-1, dp(48)).apply {
            topMargin = dp(10)
            bottomMargin = dp(2)
        })
        val liveInfo = label("", 10f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(12))
        }
        panel.addView(liveInfo)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        fun actionButton(symbol: String, caption: String, enabled: Boolean = true, onClick: () -> Unit): TextView {
            return label("$symbol\n$caption", 10f, if (enabled) themeText else muted, true).apply {
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                background = panelDrawable(12)
                alpha = if (enabled) 1f else 0.42f
                isEnabled = enabled
                isClickable = enabled
                isFocusable = enabled
                setPadding(dp(3), dp(8), dp(3), dp(8))
                setOnClickListener { if (enabled) onClick() }
            }
        }
        panel.addView(actionRow, LinearLayout.LayoutParams(-1, dp(58)).apply { topMargin = dp(10) })

        fun formatDb(mb: Int) = String.format(java.util.Locale.US, "%.2f dB", mb / 100f)
        fun updateRangeAndHeader() {
            val b = detectedBands[bandPosition]
            frequency.text = "${b.centerHz} Hz"
            rangeText.text = uiText(
                "MIN ${formatDb(b.minMb)}   ·   MAX ${formatDb(b.maxMb)}",
                "MIN ${formatDb(b.minMb)}   ·   MAX ${formatDb(b.maxMb)}"
            )
            previewSlider.max = (b.maxMb - b.minMb).coerceAtLeast(1)
            previewSlider.progress = (latestPreviewMb - b.minMb).coerceIn(0, previewSlider.max)
            updatingInput = true
            input.setText(String.format(java.util.Locale.US, "%.2f", latestPreviewMb / 100f))
            input.setSelection(input.text.length)
            updatingInput = false
            liveInfo.text = uiText(
                "Pratinjau langsung · belum disimpan sampai kamu memilih aksi.",
                "Live preview · not committed until you choose an action."
            )
        }
        fun updateBandUi(b: EqEngineController.Band, appliedMb: Int) {
            bandUiControls[b.index]?.let { control ->
                control.fader.setGainMb(appliedMb)
                control.valueView.text = String.format(java.util.Locale.US, "%.1f dB", appliedMb / 100f)
            }
        }
        fun previewValue(requestedDb: Float): Boolean {
            val b = detectedBands[bandPosition]
            val minDb = b.minMb / 100f
            val maxDb = b.maxMb / 100f
            if (!requestedDb.isFinite() || requestedDb < minDb || requestedDb > maxDb) return false
            val applied = EqEngineController.setBand(b.index, (requestedDb * 100f).roundToInt()) ?: return false
            latestPreviewMb = applied
            updateBandUi(b, applied)
            previewSlider.progress = (applied - b.minMb).coerceIn(0, previewSlider.max)
            liveInfo.text = uiText("Preview engine: ${formatDb(applied)} · HOLD", "Engine preview: ${formatDb(applied)} · HOLD")
            return true
        }
        fun commitPreview() {
            val b = detectedBands[bandPosition]
            val idx = active.centersHz.indexOf(b.centerHz)
            if (idx >= 0 && idx < active.gainDb.size) active.gainDb[idx] = latestPreviewMb / 100f
            else { active.centersHz.add(b.centerHz); active.gainDb.add(latestPreviewMb / 100f) }
            persistCurrentState(force = true)
            markWorkingStateSelected()
            committed = true
        }
        fun restorePreview() {
            if (restored || committed) return
            restored = true
            val b = detectedBands[bandPosition]
            val applied = EqEngineController.setBand(b.index, initialMb) ?: initialMb
            updateBandUi(b, applied)
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                // IME Done only closes the keyboard. The dialog's Save/Prev/Next actions
                // commit; Back/X must still be able to roll the live preview back.
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(input.windowToken, 0)
                true
            } else false
        }

        updateRangeAndHeader()
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (updatingInput) return
                val raw = s?.toString()?.trim()?.replace(',', '.') ?: return
                val parsed = raw.toFloatOrNull() ?: return
                val b = detectedBands[bandPosition]
                val minDb = b.minMb / 100f
                val maxDb = b.maxMb / 100f
                input.error = if (parsed < minDb || parsed > maxDb) uiText(
                    "Nilai harus ${formatDb(b.minMb)} sampai ${formatDb(b.maxMb)}",
                    "Value must be between ${formatDb(b.minMb)} and ${formatDb(b.maxMb)}"
                ) else null
                if (parsed in minDb..maxDb && previewValue(parsed)) {
                    updatingInput = true
                    previewSlider.progress = (latestPreviewMb - b.minMb).coerceIn(0, previewSlider.max)
                    updatingInput = false
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        previewSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val b = detectedBands[bandPosition]
                val requestedDb = (b.minMb + progress) / 100f
                if (previewValue(requestedDb)) {
                    updatingInput = true
                    input.setText(String.format(java.util.Locale.US, "%.2f", latestPreviewMb / 100f))
                    input.setSelection(input.text.length)
                    input.error = null
                    updatingInput = false
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val dialog = AlertDialog.Builder(this).setView(panel).create()
        fun navigateTo(position: Int) {
            commitPreview()
            dialog.dismiss()
            focusBandColumn(position)
            showBandGainDialog(position)
        }
        actionRow.addView(actionButton("↩", uiText("BACK", "BACK")) {
            restorePreview()
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(5) })
        actionRow.addView(actionButton("‹", uiText("PREV", "PREV"), bandPosition > 0) {
            if (bandPosition > 0) navigateTo(bandPosition - 1)
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(5) })
        actionRow.addView(actionButton("›", uiText("NEXT", "NEXT"), bandPosition < detectedBands.lastIndex) {
            if (bandPosition < detectedBands.lastIndex) navigateTo(bandPosition + 1)
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(5) })
        actionRow.addView(actionButton("✓", uiText("SAVE", "SAVE")) {
            commitPreview()
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, -1, 1f))
        close.setOnClickListener { restorePreview(); dialog.dismiss() }
        dialog.setOnCancelListener { restorePreview() }
        dialog.setOnDismissListener {
            if (!committed) restorePreview()
            if (bandEditSessionId == editSessionId) bandEditDialogOpen = false
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
            input.requestFocus()
            input.post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                input.selectAll()
            }
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun focusBandColumn(index: Int) {
        val viewport = bandViewport ?: return
        val child = bandsRow.getChildAt(index) ?: return
        val left = child.left - dp(8)
        val right = child.right + dp(8)
        val visibleLeft = viewport.scrollX
        val visibleRight = visibleLeft + viewport.width
        if (left < visibleLeft) viewport.smoothScrollTo(left.coerceAtLeast(0), 0)
        else if (right > visibleRight) viewport.smoothScrollTo((right - viewport.width).coerceAtLeast(0), 0)
    }

    private fun isBuiltInPreset(profile: EqProfile): Boolean = profile.id.startsWith("builtin_") || profile.id == "flat"

    /**
     * Keep the library bound to the real backend band count. The last successful
     * scan is stored locally so a disabled EQ can still show the device's own
     * matching library without guessing from an imported preset.
     */
    private fun loadPresets() {
        repo.ensureStarterPresets()
        profiles = repo.all()
        if (profiles.none { it.id == "flat" }) profiles.add(0, EqProfile("flat", "Flat"))
        val eqPrefs = getSharedPreferences("eq_presets_v1", MODE_PRIVATE)
        val liveCount = EqEngineController.bands().size.takeIf { it in 1..32 } ?: 0
        if (liveCount > 0) eqPrefs.edit().putInt("detected_band_count", liveCount).apply()
        detectedPresetBandCount = liveCount.takeIf { it > 0 } ?: eqPrefs.getInt("detected_band_count", 0).coerceIn(0, 32)

        spinnerEntries = mutableListOf(active.copy(id = currentStateEntryId, name = uiText("Kondisi Saat Ini", "Current State")))
        if (detectedPresetBandCount > 0) {
            val compatible = profiles.filter {
                it.centersHz.size == detectedPresetBandCount && it.gainDb.size == detectedPresetBandCount
            }
            // Repository.save appends, so reverse user profiles to put the newest
            // custom preset at the top while built-ins remain a stable library.
            val userPresets = compatible.filterNot(::isBuiltInPreset).asReversed()
            val builtIns = compatible.filter(::isBuiltInPreset)
            spinnerEntries.addAll(userPresets)
            spinnerEntries.addAll(builtIns)
        }
        val selectedPreset = spinnerEntries.indexOfFirst { it.id == active.id }
        selectedPresetIndex = if (active.id == currentStateEntryId || isWorkingStateDirty() || selectedPreset < 0) 0 else selectedPreset
        updatePresetDropdownLabel()
        updatePresetStateStatus()
    }

    private fun updatePresetDropdownLabel() {
        if (!::presetDropdownLabel.isInitialized) return
        val display = if (selectedPresetIndex <= 0 || selectedPresetIndex !in spinnerEntries.indices) {
            uiText("Current State", "Current State")
        } else {
            val entry = spinnerEntries[selectedPresetIndex]
            if (entry.id == "flat") uiText("Datar", "Flat") else entry.name
        }
        presetDropdownLabel.text = display
        presetSpinner.contentDescription = uiText("Preset terpilih: $display. Ketuk untuk membuka daftar.", "Selected preset: $display. Tap to open the list.")
    }

    private fun showPresetDropdown() {
        if (spinnerEntries.isEmpty()) loadPresets()
        presetPopup?.dismiss()
        val surface = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = panelDrawable(18)
        }
        surface.addView(label(uiText("PRESET LIBRARY", "PRESET LIBRARY"), 10f, muted, true).apply {
            letterSpacing = 0.09f
            setPadding(dp(10), dp(8), dp(10), dp(10))
        })
        if (detectedPresetBandCount <= 0) {
            surface.addView(label(
                uiText("Band belum terdeteksi. Aktifkan EQ untuk scan engine perangkat; preset 5/10-band akan muncul sesuai hasil scan.", "Bands have not been detected. Enable EQ to scan the device engine; 5/10-band presets will appear after the scan."),
                10f, muted
            ).apply {
                setPadding(dp(10), dp(8), dp(10), dp(10))
                background = roundedGradient(if (isLightUi) Color.parseColor("#F1F4F8") else darken(themeSurface, 0.08f), themeSurface, if (isLightUi) 0x447D8DA3 else 0x446D819F, 12)
            })
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(1), dp(2), dp(3), dp(4)) }
        val popupRows = mutableListOf<View>()
        var lastSection: String? = null
        spinnerEntries.forEachIndexed { index, profile ->
            val section = when {
                index == 0 -> uiText("WORKING STATE", "WORKING STATE")
                !isBuiltInPreset(profile) -> uiText("MY PRESETS · ${detectedPresetBandCount}-BAND", "MY PRESETS · ${detectedPresetBandCount}-BAND")
                else -> uiText("${detectedPresetBandCount}-BAND BUILT-IN", "${detectedPresetBandCount}-BAND BUILT-IN")
            }
            if (section != lastSection) {
                val heading = label(section, 9f, muted, true).apply {
                    letterSpacing = 0.08f
                    setPadding(dp(10), dp(10), dp(10), dp(6))
                }
                list.addView(heading, LinearLayout.LayoutParams(-1, -2))
                lastSection = section
            }
            val selected = index == selectedPresetIndex
            val displayName = when {
                index == 0 -> uiText("Current State", "Current State")
                profile.id == "flat" || profile.id.startsWith("builtin_flat_") -> uiText("Datar", "Flat")
                else -> profile.name
            }
            val subtitle = when {
                index == 0 && isWorkingStateDirty() -> uiText("Perubahan saat ini · belum disimpan", "Current edits · not saved")
                index == 0 -> uiText("Konfigurasi yang sedang berjalan", "Currently applied configuration")
                isBuiltInPreset(profile) -> uiText("Preset bawaan · ${profile.centersHz.size} band", "Built-in curve · ${profile.centersHz.size} bands")
                else -> uiText("Preset buatan pengguna · ${profile.centersHz.size} band", "User preset · ${profile.centersHz.size} bands")
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(11), dp(13), dp(11))
                minimumHeight = dp(60)
                background = if (selected) roundedGradient(
                    if (isLightUi) lighten(themeAccent, 0.82f) else blend(themeSurface, themeAccent, 0.18f),
                    if (isLightUi) Color.parseColor("#EDF1F8") else darken(themeSurface, 0.06f),
                    Color.argb(105, Color.red(themeAccent), Color.green(themeAccent), Color.blue(themeAccent)), 13
                ) else roundedGradient(
                    if (isLightUi) Color.parseColor("#F6F8FC") else lighten(themeSurface, 0.05f),
                    if (isLightUi) Color.parseColor("#E9EEF6") else darken(themeSurface, 0.14f),
                    if (isLightUi) 0x557F8DA1 else 0x446D819F, 13
                )
                isClickable = true
                isFocusable = true
            }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            labels.addView(label(displayName, 14f, themeText, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
                setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            })
            labels.addView(label(subtitle, 10f, muted).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                includeFontPadding = false
            })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label(if (selected) "✓" else "›", if (selected) 16f else 21f, themeAccent, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(28), -1))
            row.setOnClickListener {
                presetPopup?.dismiss()
                val chosen = spinnerEntries.getOrNull(index) ?: return@setOnClickListener
                if (index == 0) {
                    val savedCurrent = repo.loadCurrentState() ?: captureCurrent().copy(
                        id = currentStateEntryId,
                        name = uiText("Current State", "Current State")
                    )
                    active = savedCurrent.copy(
                        id = currentStateEntryId,
                        name = uiText("Current State", "Current State"),
                        centersHz = savedCurrent.centersHz.toMutableList(),
                        gainDb = savedCurrent.gainDb.toMutableList()
                    )
                    applyProfile(active)
                    selectedPresetIndex = 0
                    updatePresetDropdownLabel()
                    updatePresetStateStatus()
                    return@setOnClickListener
                }

                fun applyChosenPreset() {
                    active = chosen.copy(
                        centersHz = chosen.centersHz.toMutableList(),
                        gainDb = chosen.gainDb.toMutableList()
                    )
                    applyProfile(active)
                    // Selecting a library preset does not replace Current State.
                    // The next actual band/effect edit writes a fresh Current snapshot.
                    loadPresets()
                }

                if (selectedPresetIndex == 0 || isWorkingStateDirty()) {
                    showEqConfirmDialog(
                        uiText("SIMPAN CURRENT STATE?", "SAVE CURRENT STATE?"),
                        uiText(
                            "Nilai band dan effect terakhir akan disimpan sebagai Current State sebelum membuka '${chosen.name}'. Current State tetap bisa dipilih kembali kapan saja. Jika memilih TIDAK, kamu akan kembali ke Current State tanpa membuka preset tersebut.",
                            "Your latest band and effect values will be saved as Current State before opening '${chosen.name}'. You can return to Current State at any time. Choose NO to stay on Current State and not open this preset."
                        ),
                        uiText("TIDAK · KEMBALI", "NO · STAY CURRENT"),
                        uiText("YA · SIMPAN & LANJUT", "YES · SAVE & CONTINUE"),
                        onConfirm = {
                            val snapshot = captureCurrent().copy(
                                id = currentStateEntryId,
                                name = uiText("Current State", "Current State"),
                                centersHz = active.centersHz.toMutableList(),
                                gainDb = active.gainDb.toMutableList()
                            )
                            repo.saveCurrentState(snapshot)
                            applyChosenPreset()
                        },
                        onCancel = {
                            // NO means return to the actual saved Current State, not
                            // merely relabel the dropdown while leaving another preset active.
                            val savedCurrent = repo.loadCurrentState() ?: captureCurrent().copy(
                                id = currentStateEntryId,
                                name = uiText("Current State", "Current State")
                            )
                            active = savedCurrent.copy(
                                id = currentStateEntryId,
                                name = uiText("Current State", "Current State"),
                                centersHz = savedCurrent.centersHz.toMutableList(),
                                gainDb = savedCurrent.gainDb.toMutableList()
                            )
                            applyProfile(active)
                            selectedPresetIndex = 0
                            updatePresetDropdownLabel()
                            updatePresetStateStatus()
                        }
                    )
                } else {
                    applyChosenPreset()
                }
            }
            row.alpha = 0f
            row.translationY = -dp(12).toFloat()
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
            popupRows.add(row)
        }
        if (detectedPresetBandCount > 0 && spinnerEntries.size <= 1) {
            list.addView(label(
                uiText("Belum ada preset untuk ${detectedPresetBandCount} band. Preset custom baru akan muncul di bagian teratas setelah disimpan.", "No preset exists for ${detectedPresetBandCount} bands yet. New custom presets will appear at the top after saving."),
                11f, muted
            ).apply { setPadding(dp(12), dp(12), dp(12), dp(16)) })
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(7), 0, dp(7))
            addView(list)
        }
        val groupCount = if (spinnerEntries.size > 1 && spinnerEntries.drop(1).any { !isBuiltInPreset(it) } && spinnerEntries.drop(1).any(::isBuiltInPreset)) 2 else 1
        val estimatedListHeight = dp((spinnerEntries.size * 68 + groupCount * 30 + 16).coerceAtLeast(132))
        val maxScrollHeight = minOf(dp(340), (resources.displayMetrics.heightPixels - dp(210)).coerceAtLeast(dp(150)))
        val scrollHeight = minOf(maxScrollHeight, estimatedListHeight)
        val scrollRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        scrollRow.addView(scroll, LinearLayout.LayoutParams(0, scrollHeight, 1f))
        val indicator = EqVerticalIndicatorView(scroll)
        scrollRow.addView(indicator, LinearLayout.LayoutParams(dp(21), scrollHeight).apply {
            marginStart = dp(12)
            marginEnd = dp(3)
        })
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> indicator.refreshMetrics() }
        val refreshPopupIndicator = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> indicator.refreshMetrics() }
        scroll.addOnLayoutChangeListener(refreshPopupIndicator)
        list.addOnLayoutChangeListener(refreshPopupIndicator)
        indicator.post { indicator.refreshMetrics() }
        surface.addView(scrollRow, LinearLayout.LayoutParams(-1, scrollHeight))
        val popupMaxWidth = (resources.displayMetrics.widthPixels - dp(28)).coerceAtLeast(dp(240))
        val popupWidth = minOf(maxOf(presetSpinner.width, dp(270)), popupMaxWidth)
        // Measure the complete surface (section headings + optional analyzer note +
        // scroll viewport) instead of assuming a fixed 56dp header. This prevents
        // the popup from clipping its last rows on compact screens / large fonts.
        val maxPopupHeight = (resources.displayMetrics.heightPixels - dp(112)).coerceAtLeast(dp(190))
        surface.measure(
            View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxPopupHeight, View.MeasureSpec.AT_MOST)
        )
        val popupHeight = surface.measuredHeight.coerceIn(dp(150), maxPopupHeight)
        val popup = PopupWindow(surface, popupWidth, popupHeight, true).apply {
            setBackgroundDrawable(panelDrawable(18))
            setElevation(dp(18).toFloat())
            isOutsideTouchable = true
            isClippingEnabled = true
            exitTransition = android.transition.Fade().apply { duration = 125L }
        }
        surface.pivotY = 0f
        surface.pivotX = popupWidth / 2f
        surface.alpha = 0f
        surface.translationY = -dp(26).toFloat()
        surface.scaleX = 0.985f
        surface.scaleY = 0.88f
        presetPopup = popup
        popup.setOnDismissListener { if (presetPopup === popup) presetPopup = null }
        popup.showAsDropDown(presetSpinner, 0, dp(5), Gravity.START)
        surface.post {
            surface.animate().cancel()
            surface.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(245L).setInterpolator(android.view.animation.DecelerateInterpolator(1.15f)).start()
            popupRows.forEachIndexed { index, row ->
                row.animate().cancel()
                row.animate().alpha(1f).translationY(0f).setStartDelay((index * 14L).coerceAtMost(140L))
                    .setDuration(175L).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }
        }
    }

    private fun captureCurrent(): EqProfile {
        val b = EqEngineController.bands()
        // Preserve portable EQ points that this device cannot currently expose; never silently collapse 15 points to 5.
        b.forEach { band ->
            val index = active.centersHz.indexOf(band.centerHz)
            if (index >= 0 && index < active.gainDb.size) active.gainDb[index] = band.levelMb / 100f
            else { active.centersHz.add(band.centerHz); active.gainDb.add(band.levelMb / 100f) }
        }
        val pairs = active.centersHz.zip(active.gainDb).sortedBy { it.first }
        active.centersHz.clear(); active.gainDb.clear()
        pairs.forEach { active.centersHz.add(it.first); active.gainDb.add(it.second) }
        val bassValue = EqEngineController.bassStrength()
        if (bassValue >= 0) { active.bassStrength = bassValue; active.bassEnabled = EqEngineController.bassEnabled() }
        val virtualValue = EqEngineController.virtualizerStrength()
        if (virtualValue >= 0) { active.virtualizerStrength = virtualValue; active.virtualizerEnabled = EqEngineController.virtualizerEnabled() }
        if (EqEngineController.limiterAvailable()) active.limiterEnabled = EqEngineController.limiterEnabled()
        if (active.preampEnabled && EqEngineController.preampAvailable()) active.preampDb = EqEngineController.preampGainDb()
        return active
    }
    private fun applyProfile(p: EqProfile) {
        val bands = EqEngineController.bands()
        if (bands.isEmpty()) { profileMappingWarning = uiText("Preset dipilih; menunggu engine audio tersedia.", "Preset selected; waiting for the audio engine."); refreshCapability(); return }
        val mapped = bands.count { p.centersHz.contains(it.centerHz) }
        val exactMapping = bands.isNotEmpty() && mapped == bands.size
        val canInterpolateProfile = p.centersHz.size >= 2 && p.centersHz.size == p.gainDb.size
        profileMappingWarning = when {
            p.centersHz.isEmpty() -> null
            exactMapping -> null
            canInterpolateProfile -> uiText(
                "Preset ${p.centersHz.size}-band dipetakan ke ${bands.size}-band menggunakan interpolasi frekuensi logaritmik. Hasilnya perkiraan.",
                "${p.centersHz.size}-band preset mapped to ${bands.size} bands using logarithmic frequency interpolation. Result is approximate."
            )
            else -> uiText("Preset tidak memiliki titik frekuensi yang cukup untuk dipetakan ke engine ini.", "Preset does not contain enough frequency points to map to this engine.")
        }
        bands.forEach { b ->
            val exact = p.centersHz.indexOf(b.centerHz)
            val gain = when {
                exact >= 0 && exact < p.gainDb.size -> p.gainDb[exact]
                canInterpolateProfile -> interpolateGain(p, b.centerHz)
                p.id == "flat" -> 0f
                else -> null
            }
            if (gain != null) EqEngineController.setBand(b.index, (gain * 100).roundToInt())
        }
        // Keep the source preset untouched. The working copy stores the mapped values
        // at the active engine's real centre frequencies, for both 5 -> 10 and 10 -> 5.
        if (canInterpolateProfile && p.centersHz.size != bands.size) {
            val appliedBands = EqEngineController.bands()
            if (appliedBands.size == bands.size) {
                active.centersHz.clear()
                active.gainDb.clear()
                appliedBands.forEach { applied ->
                    active.centersHz.add(applied.centerHz)
                    active.gainDb.add(applied.levelMb / 100f)
                }
            }
        }
        if (p.preampEnabled && !EqEngineController.preampAvailable()) {
            // Keep the stored value for portability/history, but never apply it to session 0.
            p.preampEnabled = false
            active.preampEnabled = false
            active.preampDb = p.preampDb
            profileMappingWarning = EqEngineController.preampUnavailableReason()
        } else if (p.preampEnabled) {
            val preampApplied = EqEngineController.setPreampDb(p.preampDb)
            if (preampApplied == null) {
                p.preampEnabled = false
                active.preampEnabled = false
                active.preampDb = p.preampDb
                profileMappingWarning = EqEngineController.lastError
            } else {
                p.preampDb = preampApplied
                active.preampDb = preampApplied
            }
        } else if (EqEngineController.preampAvailable() && kotlin.math.abs(EqEngineController.preampGainDb()) > 0.05f) {
            val neutral = EqEngineController.setPreampDb(0f)
            if (neutral == null) {
                p.preampEnabled = false
                if (EqEngineController.isPreampFaulted()) p.preampDb = 0f
                profileMappingWarning = EqEngineController.lastError
            }
        }
        EqEngineController.setBassEnabled(p.bassEnabled); EqEngineController.setBassStrength(p.bassStrength)
        EqEngineController.setVirtualizerEnabled(p.virtualizerEnabled); EqEngineController.setVirtualizerStrength(p.virtualizerStrength)
        EqEngineController.setLimiterEnabled(p.limiterEnabled)
        suppress = true
        preampSwitch.isChecked = p.preampEnabled && EqEngineController.preampAvailable()
        preampSlider.progress = ((p.preampDb + 12f) * 10f).roundToInt().coerceIn(0, 180)
        updatePreampValue(p.preampDb, p.preampEnabled && EqEngineController.preampAvailable())
        bassSwitch.isChecked = p.bassEnabled; bassSlider.progress = p.bassStrength
        updateStrengthValue(bassValue, p.bassStrength, p.bassEnabled, EqEngineController.bassStrengthSupported())
        virtualSwitch.isChecked = p.virtualizerEnabled; virtualSlider.progress = p.virtualizerStrength
        updateStrengthValue(virtualValue, p.virtualizerStrength, p.virtualizerEnabled, EqEngineController.virtualizerStrengthSupported())
        limiterSwitch.isChecked = EqEngineController.limiterEnabled()
        suppress = false
        if (EqEngineController.wasOutputBypassedByPreampFault()) {
            getSharedPreferences("eq_presets_v1", MODE_PRIVATE).edit().putBoolean("engine_enabled", false).apply()
            EqEngineController.disableAll()
            stopService(Intent(this, com.floatingdj.visualizer.eq.service.EqEngineService::class.java))
            EqEngineController.release()
            suppress = true; masterSwitch.isChecked = false; suppress = false
        }
        refreshCapability()
        // Applying a preset only changes the active EQ curve. The separately
        // saved Current State snapshot is updated only by actual user edits.
        if (isWorkingStateDirty()) markWorkingStateSelected()
    }
    private fun interpolateGain(profile: EqProfile, targetHz: Int): Float? {
        val points = profile.centersHz.zip(profile.gainDb).filter { it.first > 0 }.sortedBy { it.first }
        if (points.isEmpty()) return null
        if (targetHz <= points.first().first) return points.first().second
        if (targetHz >= points.last().first) return points.last().second
        val upperIndex = points.indexOfFirst { it.first >= targetHz }
        if (upperIndex <= 0) return points.first().second
        val lower = points[upperIndex - 1]
        val upper = points[upperIndex]
        val x = kotlin.math.ln(targetHz.toDouble())
        val x0 = kotlin.math.ln(lower.first.toDouble())
        val x1 = kotlin.math.ln(upper.first.toDouble())
        val ratio = if (x1 == x0) 0.0 else (x - x0) / (x1 - x0)
        return (lower.second + (upper.second - lower.second) * ratio.toFloat()).coerceIn(-15f, 15f)
    }

    private fun showEqConfirmDialog(
        titleText: String,
        messageText: String,
        cancelText: String,
        confirmText: String,
        onCancel: () -> Unit = {},
        onConfirm: () -> Unit
    ): AlertDialog {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = panelDrawable(20)
        }
        panel.addView(label(titleText, 15f, themeText, true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(9))
        })
        panel.addView(label(messageText, 12f, if (isLightUi) Color.parseColor("#526078") else muted).apply {
            setLineSpacing(dp(2).toFloat(), 1f)
            includeFontPadding = false
        }, LinearLayout.LayoutParams(-1, -2))
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(15), 0, 0)
        }
        fun dialogButton(text: String): TextView = button(text) {}.apply {
            setTextColor(if (isLightUi) Color.parseColor("#26344B") else themeText)
            textSize = 10f * textScale
            maxLines = 1
            includeFontPadding = false
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), dp(10), dp(4), dp(10))
            setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        val cancel = dialogButton(cancelText)
        // Select text color by measured contrast against both gradient stops.
        // This keeps YES legible on light gold/custom accents as well as dark accents.
        val confirmTop = lighten(themeAccent, 0.12f)
        val confirmBottom = darken(themeAccent, 0.12f)
        val darkConfirmText = Color.parseColor("#17243A")
        val darkContrast = minOf(contrastRatio(darkConfirmText, confirmTop), contrastRatio(darkConfirmText, confirmBottom))
        val whiteContrast = minOf(contrastRatio(Color.WHITE, confirmTop), contrastRatio(Color.WHITE, confirmBottom))
        val confirmTextColor = if (darkContrast >= whiteContrast) darkConfirmText else Color.WHITE
        val confirm = dialogButton(confirmText).apply {
            background = roundedGradient(lighten(themeAccent, 0.12f), darken(themeAccent, 0.12f), themeAccent, 13)
            setTextColor(confirmTextColor)
        }
        footer.addView(cancel, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
        footer.addView(confirm, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) })
        panel.addView(footer)
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        cancel.setOnClickListener { dialog.dismiss(); onCancel() }
        confirm.setOnClickListener { dialog.dismiss(); onConfirm() }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val maxWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(250))
            dialog.window?.setLayout(minOf(dp(460), maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val maxWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(250))
        dialog.window?.setLayout(minOf(dp(460), maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    private fun showSaveToPresetDialog(targets: List<EqProfile>) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(10))
            background = panelDrawable(20)
        }
        panel.addView(label(uiText("SIMPAN KE PRESET", "SAVE TO PRESET"), 14f, themeText, true).apply {
            setPadding(dp(5), dp(4), dp(5), dp(4))
        })
        panel.addView(label(
            uiText("PILIH TUJUAN SIMPAN · ${detectedPresetBandCount.coerceAtLeast(0)}-BAND", "CHOOSE SAVE TARGET · ${detectedPresetBandCount.coerceAtLeast(0)}-BAND"),
            9f, muted, true
        ).apply { letterSpacing = 0.08f; setPadding(dp(10), dp(9), dp(10), dp(7)) })
        panel.addView(label(
            uiText("Hanya preset buatan kamu yang kompatibel dengan jumlah band aktif. Preset bawaan 5/10-band tetap terlindungi.", "Only your saved presets matching the active band count are listed. Built-in 5/10-band presets remain protected."),
            10f, muted
        ).apply { setPadding(dp(10), dp(2), dp(10), dp(9)) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(1), dp(2), dp(3), dp(4)) }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            isNestedScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding(0, dp(7), 0, dp(7))
            addView(list)
        }
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        if (targets.isEmpty()) {
            panel.addView(label(
                uiText("Belum ada preset buatan kamu yang kompatibel dengan ${detectedPresetBandCount.coerceAtLeast(0)} band. Gunakan BUAT PRESET untuk membuat tujuan simpan terlebih dahulu.", "You have no saved custom preset compatible with ${detectedPresetBandCount.coerceAtLeast(0)} bands. Choose CREATE PRESET to create a destination first."),
                11f, muted
            ).apply { setPadding(dp(6), dp(12), dp(6), dp(12)) })
        } else {
            targets.forEach { target ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(13), dp(11), dp(13), dp(11))
                    minimumHeight = dp(60)
                    background = roundedGradient(
                        if (isLightUi) Color.parseColor("#F6F8FC") else lighten(themeSurface, 0.05f),
                        if (isLightUi) Color.parseColor("#E9EEF6") else darken(themeSurface, 0.14f),
                        if (isLightUi) 0x557F8DA1 else 0x446D819F, 13
                    )
                    isClickable = true
                    isFocusable = true
                }
                val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
                textColumn.addView(label(target.name, 14f, themeText, true).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                    setAutoSizeTextTypeUniformWithConfiguration(11, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                })
                textColumn.addView(label(uiText("Preset buatan kamu · ${target.centersHz.size} band", "Your preset · ${target.centersHz.size} bands"), 10f, muted).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; includeFontPadding = false
                })
                row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(label("›", 21f, themeAccent, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(28), -1))
                row.setOnClickListener {
                    dialog.dismiss()
                    val captured = captureCurrent()
                    showEqConfirmDialog(
                        uiText("TIMPA PRESET ${target.name}?", "OVERWRITE ${target.name}?"),
                        uiText(
                            "Konfigurasi EQ yang sedang aktif akan menggantikan preset tersimpan '${target.name}'. Preset bawaan tidak terpengaruh.",
                            "Your current EQ configuration will replace the saved preset '${target.name}'. Built-in presets will not be changed."
                        ),
                        uiText("BATAL", "CANCEL"), uiText("YA, SIMPAN", "YES, SAVE")
                    ) {
                        active = captured.copy(
                            id = target.id,
                            name = target.name,
                            centersHz = captured.centersHz.toMutableList(),
                            gainDb = captured.gainDb.toMutableList()
                        )
                        repo.save(active)
                        // Saving a named preset must not replace the independent Current State snapshot.
                        loadPresets()
                        toast(uiText("Konfigurasi disimpan ke ${active.name}", "Configuration saved to ${active.name}"))
                    }
                }
                list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
            val maxScrollHeight = minOf(dp(330), (resources.displayMetrics.heightPixels - dp(300)).coerceAtLeast(dp(120)))
            val estimatedListHeight = dp((targets.size * 68).coerceAtLeast(100))
            val scrollHeight = minOf(maxScrollHeight, estimatedListHeight)
            val scrollRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                clipChildren = false
            }
            scrollRow.addView(scroll, LinearLayout.LayoutParams(0, scrollHeight, 1f))
            val indicator = EqVerticalIndicatorView(scroll)
            scrollRow.addView(indicator, LinearLayout.LayoutParams(dp(21), scrollHeight).apply {
                marginStart = dp(12)
                marginEnd = dp(3)
            })
            scroll.setOnScrollChangeListener { _, _, _, _, _ -> indicator.refreshMetrics() }
            val refreshSaveToIndicator = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> indicator.refreshMetrics() }
            scroll.addOnLayoutChangeListener(refreshSaveToIndicator)
            list.addOnLayoutChangeListener(refreshSaveToIndicator)
            indicator.post { indicator.refreshMetrics() }
            panel.addView(scrollRow, LinearLayout.LayoutParams(-1, scrollHeight))
        }
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
        val close = button(uiText("TUTUP", "CLOSE"), {}).apply {
            setTextColor(if (isLightUi) Color.parseColor("#26344B") else themeText)
            textSize = 10f * textScale; maxLines = 1; includeFontPadding = false
        }
        footer.addView(close, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) })
        val create = button(uiText("BUAT PRESET +", "CREATE PRESET +"), {}).apply {
            setTextColor(if (isLightUi) Color.parseColor("#26344B") else themeText)
            textSize = 10f * textScale; maxLines = 1; includeFontPadding = false
            background = roundedGradient(lighten(themeAccent, 0.12f), darken(themeAccent, 0.12f), themeAccent, 13)
        }
        footer.addView(create, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(4) })
        panel.addView(footer)
        close.setOnClickListener { dialog.dismiss() }
        create.setOnClickListener {
            dialog.dismiss()
            promptName(uiText("Simpan Sebagai", "Save As")) { name -> saveAs(name) }
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val maxWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(250))
            dialog.window?.setLayout(minOf(dp(460), maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val maxWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(250))
        dialog.window?.setLayout(minOf(dp(460), maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun saveToPreset() {
        val compatibleCount = detectedPresetBandCount.takeIf { it > 0 } ?: EqEngineController.bands().size
        val targets = profiles
            .filter { !isBuiltInPreset(it) && it.centersHz.size == compatibleCount && it.gainDb.size == compatibleCount }
            .asReversed()
        showSaveToPresetDialog(targets)
    }

    private fun confirmDeleteCurrent() {
        if (active.id == "flat" || isBuiltInPreset(active)) {
            toast(uiText("Preset bawaan tidak dapat dihapus", "Built-in presets cannot be deleted"))
            return
        }
        val name = active.name
        showEqConfirmDialog(
            uiText("HAPUS PRESET?", "DELETE PRESET?"),
            uiText("Preset '$name' akan dihapus dari daftar preset buatan kamu. Tindakan ini tidak bisa dibatalkan.", "The saved preset '$name' will be removed from your custom preset list. This cannot be undone."),
            uiText("BATAL", "CANCEL"), uiText("YA, HAPUS", "YES, DELETE")
        ) { deleteCurrent() }
    }

    private fun saveCurrent() {
        captureCurrent()
        if (active.id == currentStateEntryId || isBuiltInPreset(active)) {
            saveAs(uiText("Preset Saya", "My Preset"))
            return
        }
        repo.save(active)
        loadPresets()
        toast(uiText("Preset disimpan", "Preset saved"))
    }
    private fun saveAs(name: String) {
        val copy = captureCurrent().copy(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            centersHz = active.centersHz.toMutableList(),
            gainDb = active.gainDb.toMutableList()
        )
        repo.save(copy)
        active = copy
        loadPresets()
        toast(uiText("Preset baru disimpan", "New preset saved"))
    }
    private fun rename(name: String) {
        if (isBuiltInPreset(active)) {
            toast(uiText("Preset bawaan tidak dapat diganti nama. Gunakan Simpan Sebagai untuk membuat preset sendiri.", "Built-in presets cannot be renamed. Use Save As to create your own preset."))
            return
        }
        active.name = name
        repo.save(active)
        loadPresets()
    }
    private fun deleteCurrent() {
        if (isBuiltInPreset(active)) {
            toast(uiText("Preset bawaan tidak dapat dihapus", "Built-in presets cannot be deleted"))
            return
        }
        repo.delete(active.id)
        active = profiles.firstOrNull { it.id == "flat" } ?: EqProfile("flat", "Flat")
        loadPresets()
        applyProfile(active)
    }
    private fun resetFlat() {
        val currentBands = EqEngineController.bands()
        val nonZeroBands = currentBands.filter { kotlin.math.abs(it.levelMb) >= 1 }
        val summary = if (nonZeroBands.isEmpty()) {
            uiText("Semua band sudah berada di 0 dB. Tetap pilih preset Flat?", "All bands are already at 0 dB. Select the Flat preset anyway?")
        } else {
            val preview = nonZeroBands.take(8).joinToString("\n") { band ->
                val before = band.levelMb / 100f
                "${band.centerHz} Hz: ${if (before > 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", before)} dB  →  0.0 dB"
            }
            val remainder = if (nonZeroBands.size > 8) "\n… +${nonZeroBands.size - 8} ${uiText("band lainnya", "more bands")}" else ""
            uiText(
                "Preset Flat akan mengatur semua band ke 0 dB. Perubahan saat ini:\n$preview$remainder\n\nLanjutkan?",
                "The Flat preset will set every band to 0 dB. Current changes:\n$preview$remainder\n\nContinue?"
            )
        }
        showEqConfirmDialog(
            uiText("RESET KE FLAT?", "RESET TO FLAT?"),
            summary,
            uiText("BATAL", "NO, CANCEL"),
            uiText("YA, FLAT", "YES, FLAT")
        ) {
            active = EqProfile("flat", "Flat", currentBands.map { it.centerHz }.toMutableList(), currentBands.map { 0f }.toMutableList())
            applyProfile(active)
            loadPresets()
            persistCurrentState(force = true)
            markWorkingStateSelected()
        }
    }
    private fun promptName(title: String, done: (String) -> Unit) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = panelDrawable(20)
        }
        panel.addView(label(title, 15f, themeText, true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(10))
        })
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            setText(active.name)
            textSize = 14f * textScale
            setTextColor(if (isLightUi) Color.parseColor("#26344B") else themeText)
            setHintTextColor(muted)
            hint = uiText("Nama preset", "Preset name")
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = roundedGradient(
                if (isLightUi) Color.WHITE else darken(themeSurface, 0.12f),
                if (isLightUi) Color.parseColor("#F0F3F8") else themeSurface,
                if (isLightUi) 0x557F8DA1 else 0x667D91B8, 12
            )
            maxLines = 1
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        panel.addView(input, LinearLayout.LayoutParams(-1, dp(50)))
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
        }
        fun promptButton(text: String, primary: Boolean = false) = button(text, {}).apply {
            maxLines = 1
            includeFontPadding = false
            ellipsize = android.text.TextUtils.TruncateAt.END
            textSize = 10f * textScale
            setPadding(dp(4), dp(10), dp(4), dp(10))
            setAutoSizeTextTypeUniformWithConfiguration(8, 11, 1, TypedValue.COMPLEX_UNIT_SP)
            if (primary) {
                val confirmTop = lighten(themeAccent, 0.12f)
                val confirmBottom = darken(themeAccent, 0.12f)
                background = roundedGradient(confirmTop, confirmBottom, themeAccent, 13)
                val darkText = Color.parseColor("#17243A")
                val darkContrast = minOf(contrastRatio(darkText, confirmTop), contrastRatio(darkText, confirmBottom))
                val whiteContrast = minOf(contrastRatio(Color.WHITE, confirmTop), contrastRatio(Color.WHITE, confirmBottom))
                setTextColor(if (darkContrast >= whiteContrast) darkText else Color.WHITE)
            } else {
                setTextColor(if (isLightUi) Color.parseColor("#26344B") else themeText)
            }
        }
        val cancel = promptButton(uiText("BATAL", "CANCEL"))
        val save = promptButton(uiText("SIMPAN", "SAVE"), primary = true)
        footer.addView(cancel, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
        footer.addView(save, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) })
        panel.addView(footer)
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        cancel.setOnClickListener { dialog.dismiss() }
        save.setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                dialog.dismiss()
                done(name)
            } else {
                input.error = uiText("Nama tidak boleh kosong", "Name cannot be empty")
            }
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val maxWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(250))
            dialog.window?.setLayout(minOf(dp(460), maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
            dialog.window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
            input.requestFocus()
            input.post {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                input.selectAll()
            }
        }
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val maxWidth = (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(250))
        dialog.window?.setLayout(minOf(dp(460), maxWidth), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    private fun exportPreset() {
        val options = mutableListOf<EqProfile>()
        val currentExport = captureCurrent()
        options.add(currentExport.copy(
            id = currentStateEntryId,
            name = uiText("Current State", "Current State"),
            centersHz = currentExport.centersHz.toMutableList(),
            gainDb = currentExport.gainDb.toMutableList()
        ))
        profiles.filter { it.id != "flat" }.forEach {
            options.add(it.copy(centersHz = it.centersHz.toMutableList(), gainDb = it.gainDb.toMutableList()))
        }
        val dialogContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(8))
            background = panelDrawable(18)
        }
        dialogContent.addView(label(
            uiText("Pilih Current State atau preset tersimpan yang ingin diekspor.", "Choose Current State or a saved preset to export."),
            11f, muted
        ).apply { setPadding(dp(8), dp(7), dp(8), dp(10)) })
        val list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            setBackgroundColor(Color.TRANSPARENT)
            clipToPadding = false
            adapter = object : ArrayAdapter<EqProfile>(this@EqualizerActivity, android.R.layout.simple_list_item_1, options) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val profile = options[position]
                    val isCurrent = profile.id == currentStateEntryId
                    return LinearLayout(this@EqualizerActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(13), dp(10), dp(13), dp(10))
                        minimumHeight = dp(58)
                        background = roundedGradient(
                            if (isLightUi) Color.parseColor("#F5F7FB") else lighten(themeSurface, 0.035f),
                            if (isLightUi) Color.parseColor("#E8EDF5") else darken(themeSurface, 0.11f),
                            if (isLightUi) 0x557F8DA1 else 0x446D819F, 12
                        )
                        val labels = LinearLayout(this@EqualizerActivity).apply { orientation = LinearLayout.VERTICAL }
                        labels.addView(label(
                            if (isCurrent) uiText("Current State", "Current State") else profile.name,
                            13f, themeText, true
                        ))
                        labels.addView(label(
                            if (isCurrent) uiText("Nilai kerja terbaru", "Latest working values") else uiText("Preset tersimpan", "Saved preset"),
                            10f, muted
                        ))
                        addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
                        addView(label("›", 22f, themeAccent, true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(26), -1))
                    }
                }
            }
        }
        dialogContent.addView(list, LinearLayout.LayoutParams(-1, dp((options.size.coerceAtMost(5) * 64).coerceAtLeast(64))))
        val chooser = AlertDialog.Builder(this)
            .setTitle(uiText("PILIH PRESET UNTUK EKSPOR", "CHOOSE PRESET TO EXPORT"))
            .setView(dialogContent)
            .setNegativeButton(uiText("BATAL", "CANCEL"), null)
            .create()
        list.setOnItemClickListener { _, _, which, _ ->
            val selected = options.getOrNull(which) ?: return@setOnItemClickListener
            pendingExportProfile = selected
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "${selected.name.replace(" ", "_")}.dj-eq.json")
            }
            chooser.dismiss()
            startActivityForResult(intent, 4101)
        }
        chooser.setOnShowListener {
            chooser.window?.setBackgroundDrawable(panelDrawable(18))
            chooser.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(if (isLightUi) Color.parseColor("#526078") else Color.parseColor("#C6D1E2"))
        }
        chooser.show()
    }
    private fun importPreset() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }
        startActivityForResult(intent, 4102)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        try {
            val uri = data.data!!
            if (requestCode == 4101) {
                val selected = pendingExportProfile ?: captureCurrent()
                contentResolver.openOutputStream(uri)?.use { it.write(repo.encode(selected).toByteArray(Charsets.UTF_8)) }
                pendingExportProfile = null
                toast(uiText("Preset berhasil diekspor", "Preset exported"))
            }
            if (requestCode == 4102) {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("File kosong")
                val imported = repo.decode(raw)
                val importChoice = AlertDialog.Builder(this)
                    .setTitle(uiText("IMPOR PRESET EQ", "IMPORT EQ PRESET"))
                    .setMessage(uiText("Bagaimana preset ini akan digunakan?", "How would you like to use this preset?"))
                    .setPositiveButton(uiText("TERAPKAN KE CURRENT STATE", "APPLY TO CURRENT STATE")) { _, _ ->
                        active = imported.copy(
                            id = currentStateEntryId,
                            name = uiText("Current State", "Current State"),
                            centersHz = imported.centersHz.toMutableList(),
                            gainDb = imported.gainDb.toMutableList()
                        )
                        persistCurrentState()
                        loadPresets()
                        applyProfile(active)
                        selectedPresetIndex = 0
                        updatePresetDropdownLabel()
                    }
                    .setNeutralButton(uiText("SIMPAN SEBAGAI PRESET BARU", "SAVE AS NEW PRESET")) { _, _ ->
                        promptName(uiText("Nama preset baru", "New preset name")) { name ->
                            active = imported.copy(
                                id = java.util.UUID.randomUUID().toString(),
                                name = name,
                                centersHz = imported.centersHz.toMutableList(),
                                gainDb = imported.gainDb.toMutableList()
                            )
                            repo.save(active)
                            loadPresets()
                            applyProfile(active)
                        }
                    }
                    .setNegativeButton(uiText("BATAL", "CANCEL"), null)
                    .create()
                importChoice.setOnShowListener {
                    importChoice.window?.setBackgroundDrawable(panelDrawable(18))
                    importChoice.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(themeAccent)
                    importChoice.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(themeAccent)
                    importChoice.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(if (isLightUi) Color.parseColor("#526078") else Color.parseColor("#C6D1E2"))
                }
                importChoice.show()
            }
        } catch (t: Throwable) { toast(uiText("Gagal: ${t.message ?: "file tidak valid"}", "Failed: ${t.message ?: "invalid file"}")) }
    }
    private fun toast(s: String) = ThemedToast.makeText(this, s, Toast.LENGTH_SHORT).show()
    override fun onResume() {
        super.onResume()
        loadThemeStudioSettings()
        applyThemeStudio()
        pageScrollIndicator?.postOnAnimation { pageScrollIndicator?.refreshMetrics() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) pageScrollIndicator?.postOnAnimation { pageScrollIndicator?.refreshMetrics() }
    }
    override fun onPause() {
        presetPopup?.dismiss()
        presetPopup = null
        persistCurrentState(captureFromEngine = !bandEditDialogOpen)
        super.onPause()
    }
    override fun finish() { super.finish(); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) }
    override fun onDestroy() {
        presetPopup?.dismiss()
        presetPopup = null
        persistCurrentState(captureFromEngine = !bandEditDialogOpen)
        super.onDestroy() // The foreground service owns the native engine lifecycle.
    }
}
