package com.floatingdj.visualizer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.animation.LayoutTransition
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.ScrollView
import android.widget.FrameLayout
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.util.TypedValue
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : Activity() {

    private lateinit var mainRoot: ViewGroup
    private lateinit var contentRoot: LinearLayout
    private lateinit var barsContainer: LinearLayout
    private lateinit var singleModeContainer: LinearLayout
    private lateinit var controlsContainer: LinearLayout
    private lateinit var colorSwatches: LinearLayout
    private lateinit var engineStatus: TextView
    private lateinit var audioSourceStatus: TextView
    private lateinit var backupSwitch: Switch
    private lateinit var floatingOverlaySwitch: Switch
    private var floatingOverlayEnabled = false
    private var pendingFloatingOverlayEnable = false

    private val prefs by lazy {
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
    }

    private val spectrumColors = mutableListOf(
        Color.parseColor("#22D3EE"),
        Color.parseColor("#A855F7"),
        Color.parseColor("#22C55E"),
        Color.parseColor("#FACC15"),
        Color.parseColor("#EF4444")
    )

    private var isVerticalMode = false
    private var floatingServiceStarted = false
    private var servicePaused = false
    private var receiverRegistered = false
    private var backupRequestPending = false
    private var backupCaptureEnabled = false

    private var overlayWidthDp = 320
    private var overlayHeightDp = 360
    private var barCount = 15
    private var barWidthDp = 20
    private var barGapDp = 3
    private var scalePercent = 100
    private var opacityPercent = 100
    private var glowPercent = 65
    private var responsePercent = 82
    private var cornerRadiusDp = 14

    private var singleAudioMode = "MUSIC"
    private var bassMinHz = 60
    private var bassMaxHz = 160

    private var screenWidthDp = 360
    private var screenHeightDp = 740
    private var positionXdp = 0
    private var positionYdp = 0
    private var positionInitialized = false
    private var positionMode = "STATIC"
    private var positionUnit = "DP"
    private var positionStepDp = 12f
    private var rotationDegrees = 0

    private var themeMode = THEME_GLASS
    private var themeAccent = Color.parseColor("#9B7BFF")
    private var themeSurfaceColor = Color.parseColor("#1C2B42")
    private var themeEdgeColor = Color.parseColor("#A9B8D8")
    private var barEffect = EFFECT_GLOSSY
    private var appLanguage = "en"
    private var uiScalePercent = 100

    private var singleModeCard: LinearLayout? = null
    private var loadedProfileName: String? = null
    private var pendingProfileExport: ColorProfileEntry? = null
    private var bassControls: LinearLayout? = null
    private val previewBars = mutableListOf<View>()
    private data class SliderBinding(
        val seek: SeekBar,
        val input: EditText,
        val minimum: Int,
        val maximum: Int
    )
    private val sliderBindings = mutableMapOf<String, SliderBinding>()

    private val updateHandler = Handler(Looper.getMainLooper())
    private var realtimeUpdateScheduled = false
    private val realtimeUpdate = Runnable {
        realtimeUpdateScheduled = false
        saveSettings()
        updateFloatingService()
    }

    private fun scheduleRealtimeUpdate(delayMs: Long = 70L) {
        if (realtimeUpdateScheduled) return
        realtimeUpdateScheduled = true
        updateHandler.postDelayed(realtimeUpdate, delayMs)
    }

    companion object {
        private const val REQUEST_OVERLAY = 1001
        private const val REQUEST_AUDIO = 2001
        private const val REQUEST_BACKUP_PROJECTION = 2002
        const val EXTRA_NOTIFICATION_TOGGLE_BACKUP = "extra_notification_toggle_backup"

        private const val THEME_GLASS = "GLASS"
        private const val THEME_CUSTOM = "CUSTOM"
        private const val THEME_SYSTEM = "SYSTEM"
        private const val THEME_LIGHT = "LIGHT"

        private const val EFFECT_CLEAN = "CLEAN"
        private const val EFFECT_GLOSSY = "GLOSSY"
        private const val EFFECT_NEON = "NEON"
        private const val EFFECT_GLASS = "GLASS"
        private const val MAX_BAR_COUNT = 64
        private const val REQUEST_EXPORT_CONFIG = 3101
        private const val REQUEST_IMPORT_CONFIG = 3102
        private const val CONFIG_SCHEMA_VERSION = 1
        private const val REQUEST_EXPORT_PROFILE = 3103
        private const val REQUEST_IMPORT_PROFILE = 3104
        private const val PROFILE_SCHEMA_VERSION = 1
    }

    private val levelsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                FloatingVisualizerService.ACTION_POSITION -> {
                    positionXdp = intent.getIntExtra(FloatingVisualizerService.EXTRA_POSITION_X, positionXdp)
                    positionYdp = intent.getIntExtra(FloatingVisualizerService.EXTRA_POSITION_Y, positionYdp)
                }
                FloatingVisualizerService.ACTION_ENGINE_STATUS -> {
                    val status = intent.getStringExtra(FloatingVisualizerService.EXTRA_ENGINE_STATUS) ?: "READY"
                    updateEngineStatus(status)
                }
                FloatingVisualizerService.ACTION_LEVELS -> {
                    val levels = intent.getFloatArrayExtra(FloatingVisualizerService.EXTRA_LEVELS)
                    if (levels != null) updatePreviewLevels(levels)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainRoot = findViewById(R.id.mainRoot)
        contentRoot = findViewById(R.id.contentRoot)
        barsContainer = findViewById(R.id.equalizerBars)
        singleModeContainer = findViewById(R.id.singleModeContainer)
        controlsContainer = findViewById(R.id.floatingControlsContainer)
        colorSwatches = findViewById(R.id.colorSwatches)
        engineStatus = findViewById(R.id.txtEngineStatus)
        audioSourceStatus = findViewById(R.id.txtAudioSource)
        backupSwitch = findViewById(R.id.switchBackupCapture)
        floatingOverlaySwitch = findViewById(R.id.switchFloatingOverlay)
        installEdgeToEdgeInsets()

        loadSettings()
        if (!floatingServiceStarted) backupCaptureEnabled = false
        // A revoked overlay permission must never be silently treated as active.
        if (floatingOverlayEnabled && !Settings.canDrawOverlays(this)) {
            floatingOverlayEnabled = false
            prefs.edit().putBoolean("floating_overlay_enabled", false).apply()
        }
        scanDisplayBounds()
        initializeOrClampPosition()
        setupButtons()
        backupSwitch.isChecked = backupCaptureEnabled
        backupSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) enableBackupCapture() else disableBackupCapture()
        }
        setFloatingOverlaySwitch(floatingOverlayEnabled)
        floatingOverlaySwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) enableFloatingOverlay() else disableFloatingOverlay()
        }
        applyTheme(animated = false)
        buildSpectrumBars()
        buildSingleModePanel()
        buildFloatingControls()
        refreshColorSwatches()
        updateModeButtons()
        updateEffectButtons()
        updateSingleModeVisibility()
        updateSpectrumBandLabels(animated = false)
        localizeViewTree(contentRoot)
        applyUiTextScale(contentRoot)

        backupSwitch.isChecked = backupCaptureEnabled
        updateEngineStatus(when {
                floatingServiceStarted -> "STARTING"
                servicePaused -> "AUDIO_PAUSED: Service kept alive"
                else -> "READY"
        })

        if (floatingServiceStarted || servicePaused) {
            barsContainer.postDelayed({ updateFloatingService() }, 300L)
        }
        handleNotificationBackupToggle(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleNotificationBackupToggle(intent)
        }
    }

    private fun handleNotificationBackupToggle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_NOTIFICATION_TOGGLE_BACKUP, false) == true) {
            intent.removeExtra(EXTRA_NOTIFICATION_TOGGLE_BACKUP)
            backupSwitch.post {
                backupSwitch.isChecked = !backupSwitch.isChecked
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(FloatingVisualizerService.ACTION_LEVELS)
            addAction(FloatingVisualizerService.ACTION_POSITION)
            addAction(FloatingVisualizerService.ACTION_ENGINE_STATUS)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(levelsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(levelsReceiver, filter)
        }
        receiverRegistered = true
        // Refresh persisted service switches when returning from Settings or a notification action.
        floatingServiceStarted = prefs.getBoolean("service_active", floatingServiceStarted)
        servicePaused = prefs.getBoolean("service_paused", servicePaused)
        floatingOverlayEnabled = prefs.getBoolean("floating_overlay_enabled", floatingOverlayEnabled)
        backupCaptureEnabled = prefs.getBoolean("backup_capture", backupCaptureEnabled)
        if (floatingOverlayEnabled && !Settings.canDrawOverlays(this)) {
            floatingOverlayEnabled = false
            prefs.edit().putBoolean("floating_overlay_enabled", false).apply()
        }
        setFloatingOverlaySwitch(floatingOverlayEnabled)
        setBackupSwitch(backupCaptureEnabled && floatingServiceStarted)
        positionXdp = prefs.getInt("position_x", positionXdp).coerceAtLeast(0)
        positionYdp = prefs.getInt("position_y", positionYdp).coerceAtLeast(0)
        // Keep the saved top-left anchor unchanged during lifecycle/orientation refresh.
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(levelsReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        updateHandler.removeCallbacks(realtimeUpdate)
        realtimeUpdateScheduled = false
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        localizeViewTree(contentRoot)
        applyUiTextScale(contentRoot)
        applyTheme(animated = false)
        scanDisplayBounds()
        // Do not clamp the saved overlay anchor on display rotation; the service keeps its center.
        updateHandler.removeCallbacks(realtimeUpdate)
        updateHandler.postDelayed({
                saveSettings()
                updateFloatingService()
            }, 120L)
    }

    // -----------------------------------------------------------------
    // ANDROID 15/16 EDGE-TO-EDGE INSETS
    // -----------------------------------------------------------------

    private fun installEdgeToEdgeInsets() {
        // API 30-34 needs the explicit platform switch. On API 35+ edge-to-edge
        // is enforced for this target SDK, so the insets listener below is the
        // important part and no deprecated opt-out/switch is needed.
        if (Build.VERSION.SDK_INT in 30..34) {
            setDecorFitsSystemWindowsForLegacyApi()
        }

        val baseLeft = contentRoot.paddingLeft
        val baseTop = contentRoot.paddingTop
        val baseRight = contentRoot.paddingRight
        val baseBottom = contentRoot.paddingBottom

        mainRoot.setOnApplyWindowInsetsListener { _, insets ->
            val safeInsets = if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(
                    WindowInsets.Type.systemBars() or
                    WindowInsets.Type.displayCutout()
                )
                intArrayOf(safe.left, safe.top, safe.right, safe.bottom)
            } else {
                readLegacyWindowInsets(insets)
            }

            // Apply all four sides: a cutout can be on a side edge in landscape.
            // Always add to the XML padding rather than the previously applied
            // padding, preventing insets from accumulating after theme/rotation changes.
            contentRoot.setPadding(
                baseLeft + safeInsets[0],
                baseTop + safeInsets[1],
                baseRight + safeInsets[2],
                baseBottom + safeInsets[3]
            )
            insets
        }
        mainRoot.requestApplyInsets()
    }

    @Suppress("DEPRECATION")
    private fun setDecorFitsSystemWindowsForLegacyApi() {
        window.setDecorFitsSystemWindows(false)
    }

    @Suppress("DEPRECATION")
    private fun readLegacyWindowInsets(insets: WindowInsets): IntArray {
        return intArrayOf(
            insets.systemWindowInsetLeft,
            insets.systemWindowInsetTop,
            insets.systemWindowInsetRight,
            insets.systemWindowInsetBottom
        )
    }

    // -----------------------------------------------------------------
    // PERSISTENCE
    // -----------------------------------------------------------------

    private fun loadSettings() {
        overlayWidthDp = prefs.getInt("width", 320)
        overlayHeightDp = prefs.getInt("height", 360)
        barCount = prefs.getInt("bar_count", 15).coerceIn(0, MAX_BAR_COUNT)
        barWidthDp = prefs.getInt("bar_width", 20)
        barGapDp = prefs.getInt("bar_gap", 3)
        scalePercent = prefs.getInt("scale", 100)
        opacityPercent = prefs.getInt("opacity", 100).coerceIn(0, 100)
        glowPercent = prefs.getInt("glow", 65)
        responsePercent = prefs.getInt("response_speed", 82).coerceIn(0, 100)
        cornerRadiusDp = prefs.getInt("corner_radius", 14)
        isVerticalMode = prefs.getBoolean("vertical", false)
        singleAudioMode = prefs.getString("single_audio_mode", "MUSIC") ?: "MUSIC"
        bassMinHz = prefs.getInt("bass_min_hz", 60).coerceIn(0, 400)
        bassMaxHz = prefs.getInt("bass_max_hz", 160).coerceIn(0, 400)
        floatingServiceStarted = prefs.getBoolean("service_active", false)
        servicePaused = prefs.getBoolean("service_paused", false)
        backupCaptureEnabled = prefs.getBoolean("backup_capture", false)
        floatingOverlayEnabled = prefs.getBoolean("floating_overlay_enabled", false)
        positionXdp = prefs.getInt("position_x", 0)
        positionYdp = prefs.getInt("position_y", 0)
        positionInitialized = prefs.getBoolean("position_initialized", false)
        positionMode = prefs.getString("position_mode", "STATIC") ?: "STATIC"
        positionUnit = prefs.getString("position_unit", "DP") ?: "DP"
        positionStepDp = prefs.getFloat("position_step", 12f)
        rotationDegrees = prefs.getInt("rotation_degrees", 0).coerceIn(0, 359)
        themeMode = prefs.getString("theme_mode", THEME_GLASS) ?: THEME_GLASS
        themeAccent = prefs.getInt("theme_accent", Color.parseColor("#9B7BFF"))
        themeSurfaceColor = prefs.getInt("theme_surface_color", Color.parseColor("#1C2B42"))
        themeEdgeColor = prefs.getInt("theme_edge_color", Color.parseColor("#A9B8D8"))
        barEffect = prefs.getString("bar_effect", EFFECT_GLOSSY) ?: EFFECT_GLOSSY
        appLanguage = prefs.getString("app_language", "en") ?: "en"
        uiScalePercent = prefs.getInt("ui_scale_percent", 100).coerceIn(85, 125)
        loadedProfileName = prefs.getString("active_color_profile", null)

        val savedColors = prefs.getString("colors", null)
        if (!savedColors.isNullOrBlank()) {
            val parsed = savedColors.split(",").mapNotNull { value ->
                try { Color.parseColor(value.trim()) } catch (_: Exception) { null }
            }
            if (parsed.size >= 2) {
                spectrumColors.clear()
                spectrumColors.addAll(parsed.take(5))
            }
        }
        while (spectrumColors.size < 5) spectrumColors.add(Color.WHITE)
    }

    private fun saveSettings() {
        prefs.edit()
        .putInt("width", overlayWidthDp)
        .putInt("height", overlayHeightDp)
        .putInt("bar_count", barCount)
        .putInt("bar_width", barWidthDp)
        .putInt("bar_gap", barGapDp)
        .putInt("scale", scalePercent)
        .putInt("opacity", opacityPercent)
        .putInt("glow", glowPercent)
        .putInt("response_speed", responsePercent)
        .putInt("corner_radius", cornerRadiusDp)
        .putBoolean("vertical", isVerticalMode)
        .putString("single_audio_mode", singleAudioMode)
        .putInt("bass_min_hz", bassMinHz)
        .putInt("bass_max_hz", bassMaxHz)
        .putInt("position_x", positionXdp)
        .putInt("position_y", positionYdp)
        .putBoolean("position_initialized", positionInitialized)
        .putString("position_mode", positionMode)
        .putString("position_unit", positionUnit)
        .putFloat("position_step", positionStepDp)
        .putInt("rotation_degrees", rotationDegrees)
        .putString("theme_mode", themeMode)
        .putInt("theme_accent", themeAccent)
        .putInt("theme_surface_color", themeSurfaceColor)
        .putInt("theme_edge_color", themeEdgeColor)
        .putString("bar_effect", barEffect)
        .putString("app_language", appLanguage)
        .putInt("ui_scale_percent", uiScalePercent)
        .putBoolean("backup_capture", backupCaptureEnabled)
        .putBoolean("floating_overlay_enabled", floatingOverlayEnabled)
        .putString("colors", spectrumColors.joinToString(",") {
                String.format("#%06X", 0xFFFFFF and it)
        })
        .apply()
    }

    // -----------------------------------------------------------------
    // SCREEN GEOMETRY
    // -----------------------------------------------------------------

    private fun scanDisplayBounds() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val widthPx = metrics.widthPixels.coerceAtLeast(1)
        val heightPx = metrics.heightPixels.coerceAtLeast(1)
        screenWidthDp = (widthPx / density).toInt().coerceAtLeast(160)
        screenHeightDp = (heightPx / density).toInt().coerceAtLeast(240)
    }

    private fun localContentSizeDp(): Pair<Int, Int> {
        val rawWidth = if (isVerticalMode) barWidthDp else overlayWidthDp
        val width = (rawWidth * scalePercent / 100f).toInt().coerceAtLeast(2)
        val height = (overlayHeightDp * scalePercent / 100f).toInt().coerceAtLeast(2)
        return Pair(width, height)
    }

    private fun rotatedBoundsDp(): Pair<Int, Int> {
        val (w, h) = localContentSizeDp()
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        var bw = ceil(w * c + h * s).toInt().coerceAtLeast(1)
        var bh = ceil(w * s + h * c).toInt().coerceAtLeast(1)
        val fit = minOf(1f, screenWidthDp.toFloat() / bw, screenHeightDp.toFloat() / bh)
        bw = (bw * fit).toInt().coerceAtLeast(1)
        bh = (bh * fit).toInt().coerceAtLeast(1)
        return Pair(bw, bh)
    }

    private fun initializeOrClampPosition() {
        overlayWidthDp = overlayWidthDp.coerceIn(0, 4096)
        overlayHeightDp = overlayHeightDp.coerceIn(0, 4096)
        barWidthDp = barWidthDp.coerceIn(0, 2048)
        barCount = barCount.coerceIn(0, MAX_BAR_COUNT)
        barGapDp = barGapDp.coerceIn(0, 512)
        scalePercent = scalePercent.coerceIn(0, 500)
        opacityPercent = opacityPercent.coerceIn(0, 100)
        glowPercent = glowPercent.coerceIn(0, 100)
        cornerRadiusDp = cornerRadiusDp.coerceIn(0, 512)
        bassMinHz = bassMinHz.coerceIn(0, 400)
        bassMaxHz = bassMaxHz.coerceIn(0, 400)
        val (bw, bh) = rotatedBoundsDp()
        if (!positionInitialized) {
            positionXdp = ((screenWidthDp - bw) / 2).coerceAtLeast(0)
            positionYdp = ((screenHeightDp - bh) / 2).coerceAtLeast(0)
            positionInitialized = true
        } else {
            // Keep the user's persisted anchor stable across orientation changes.
            // The overlay service re-centres its new rotated bounds around its retained center.
            positionXdp = positionXdp.coerceAtLeast(0)
            positionYdp = positionYdp.coerceAtLeast(0)
        }
        saveSettings()
    }

    private fun clampPosition() {
        val (bw, bh) = rotatedBoundsDp()
        positionXdp = positionXdp.coerceIn(0, (screenWidthDp - bw).coerceAtLeast(0))
        positionYdp = positionYdp.coerceIn(0, (screenHeightDp - bh).coerceAtLeast(0))
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    // -----------------------------------------------------------------
    // BUTTONS / THEME / EFFECTS
    // -----------------------------------------------------------------

    private fun setupButtons() {
        findViewById<View>(R.id.btnModeVertical).setOnClickListener { selectMode(true) }
        findViewById<View>(R.id.btnModeHorizontal).setOnClickListener { selectMode(false) }
        findViewById<View>(R.id.btnColorStudio).setOnClickListener { showColorStudio() }
        findViewById<View>(R.id.btnStart).setOnClickListener { toggleStartStop() }
        findViewById<View>(R.id.btnPositionSettings).setOnClickListener { showPositionSettings() }
        findViewById<View>(R.id.btnGeneralSettings).setOnClickListener {
            it.animate().cancel()
            it.rotation = 0f
            it.animate().rotationBy(360f).setDuration(360L).withEndAction { it.rotation = 0f }.start()
            showGeneralSettings()
        }
        findViewById<View>(R.id.btnResetBarAppearance).setOnClickListener { confirmResetBarAppearance(it) }

        findViewById<View>(R.id.btnThemeGlass).setOnClickListener { setThemeMode(THEME_GLASS) }
        findViewById<View>(R.id.btnThemeCustom).setOnClickListener { setThemeMode(THEME_CUSTOM) }
        findViewById<View>(R.id.btnThemeSystem).setOnClickListener { setThemeMode(THEME_SYSTEM) }
        findViewById<View>(R.id.btnThemeLight).setOnClickListener { setThemeMode(THEME_LIGHT) }
        findViewById<View>(R.id.btnThemeAccent).setOnClickListener { showAccentPicker() }

        findViewById<View>(R.id.btnEffectClean).setOnClickListener { setBarEffect(EFFECT_CLEAN) }
        findViewById<View>(R.id.btnEffectGlossy).setOnClickListener { setBarEffect(EFFECT_GLOSSY) }
        findViewById<View>(R.id.btnEffectNeon).setOnClickListener { setBarEffect(EFFECT_NEON) }
        findViewById<View>(R.id.btnEffectGlass).setOnClickListener { setBarEffect(EFFECT_GLASS) }

    }

    private fun setThemeMode(mode: String) {
        themeMode = mode
        saveSettings()
        applyTheme(animated = true)
        updateModeButtons()
        updateEffectButtons()
        updateSingleModeButtons()
        updateSpectrumBandLabels(animated = true)
    }

    private fun isLightTheme(): Boolean {
        return when (themeMode) {
            THEME_LIGHT -> true
            THEME_SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
            else -> false
        }
    }

    private fun currentAccent(): Int {
        return when (themeMode) {
            THEME_GLASS -> Color.parseColor("#9B7BFF")
            else -> themeAccent
        }
    }

    private fun applyTheme(animated: Boolean) {
        val light = isLightTheme()
        val accent = currentAccent()
        val rootColor = if (light) Color.parseColor("#EEF2F9") else Color.parseColor("#080D18")
        mainRoot.setBackgroundColor(rootColor)

        val cards = intArrayOf(
            R.id.cardBackup, R.id.cardTheme, R.id.cardPreview, R.id.cardMode,
            R.id.cardColors, R.id.cardEffects, R.id.cardFloatingOverlay
        )
        for (id in cards) {
            findViewById<View>(id)?.background = cardDrawable(light, accent)
        }

        styleTextTree(contentRoot, light)
        singleModeCard?.let {
            it.background = cardDrawable(light, accent)
            styleDynamicTree(it, light, accent)
        }
        styleDynamicTree(controlsContainer, light, accent)
        engineStatus.setTextColor(if (light) Color.parseColor("#087F62") else Color.parseColor("#75E6BD"))
        audioSourceStatus.setTextColor(if (light) Color.parseColor("#087F62") else Color.parseColor("#82DCC3"))
        styleThemeButton(R.id.btnThemeGlass, themeMode == THEME_GLASS, light, accent)
        styleThemeButton(R.id.btnThemeCustom, themeMode == THEME_CUSTOM, light, accent)
        styleThemeButton(R.id.btnThemeSystem, themeMode == THEME_SYSTEM, light, accent)
        styleThemeButton(R.id.btnThemeLight, themeMode == THEME_LIGHT, light, accent)

        val accentButton = findViewById<TextView>(R.id.btnThemeAccent)
        accentButton.background = pillDrawable(false, light, accent)
        accentButton.setTextColor(if (light) Color.parseColor("#25314A") else Color.WHITE)

        findViewById<View>(R.id.btnColorStudio).background = accentDrawable(accent)
        findViewById<View>(R.id.btnStart).background = accentDrawable(accent)
        findViewById<View>(R.id.btnPositionSettings).background = pillDrawable(false, light, accent)
        findViewById<TextView>(R.id.btnPositionSettings).setTextColor(if (light) Color.DKGRAY else Color.WHITE)
        findViewById<View>(R.id.btnGeneralSettings).background = pillDrawable(false, light, accent)
        findViewById<TextView>(R.id.btnGeneralSettings).setTextColor(if (light) Color.DKGRAY else Color.WHITE)

        val tint = ColorStateList.valueOf(accent)
        backupSwitch.thumbTintList = tint
        backupSwitch.trackTintList = ColorStateList.valueOf(if (light) 0x55717F99 else 0x667D91B8)
        floatingOverlaySwitch.thumbTintList = tint
        floatingOverlaySwitch.trackTintList = ColorStateList.valueOf(if (light) 0x55717F99 else 0x667D91B8)

        if (animated) {
            contentRoot.animate().cancel()
            contentRoot.alpha = 0.88f
            contentRoot.animate().alpha(1f).setDuration(190L).start()
        }

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= 30) {
            val appearanceMask =
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            val appearance = if (light) appearanceMask else 0
            window.insetsController?.setSystemBarsAppearance(appearance, appearanceMask)
        } else {
            @Suppress("DEPRECATION")
            val edgeFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            @Suppress("DEPRECATION")
            val lightFlags = if (light) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else 0
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = edgeFlags or lightFlags
        }
        mainRoot.post { mainRoot.requestApplyInsets() }
        updateStartStopButton()

    }

    private fun styleTextTree(group: ViewGroup, light: Boolean) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is TextView) {
                if (!child.isClickable && !child.isFocusable) {
                    child.setTextColor(
                        if (light) {
                            if (child.textSize >= 13f * resources.displayMetrics.scaledDensity) Color.parseColor("#1B2940") else Color.parseColor("#66758C")
                        } else {
                            if (child.textSize >= 13f * resources.displayMetrics.scaledDensity) Color.parseColor("#F5F7FC") else Color.parseColor("#9AA8BE")
                        }
                    )
                }
            }
            if (child is ViewGroup) styleTextTree(child, light)
        }
    }

    private fun styleDynamicTree(group: ViewGroup, light: Boolean, accent: Int) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            when (child) {
                is SeekBar -> {
                    child.progressTintList = ColorStateList.valueOf(accent)
                    child.thumbTintList = ColorStateList.valueOf(accent)
                }
                is EditText -> {
                    child.setTextColor(if (light) Color.parseColor("#26344B") else Color.WHITE)
                    child.setHintTextColor(if (light) Color.parseColor("#8693A8") else Color.parseColor("#8593A8"))
                    child.background = glassEditDrawable(light)
                }
                is TextView -> {
                    if (!child.isClickable && !child.isFocusable) {
                        child.setTextColor(if (light) {
                                if (child.textSize >= 13f * resources.displayMetrics.scaledDensity) Color.parseColor("#1B2940") else Color.parseColor("#66758C")
                            } else {
                                if (child.textSize >= 13f * resources.displayMetrics.scaledDensity) Color.parseColor("#F5F7FC") else Color.parseColor("#9AA8BE")
                        })
                    }
                }
            }
            if (child is ViewGroup) styleDynamicTree(child, light, accent)
        }
    }

    private fun styleThemeButton(id: Int, active: Boolean, light: Boolean, accent: Int) {
        val view = findViewById<TextView>(id)
        view.background = pillDrawable(active, light, accent)
        view.setTextColor(if (active || !light) Color.WHITE else Color.parseColor("#31405A"))
    }

    private fun pillDrawable(active: Boolean, light: Boolean, accent: Int): GradientDrawable {
        val custom = themeMode == THEME_CUSTOM
        val colors = if (active) intArrayOf(lighten(accent, 0.15f), darken(accent, 0.18f))
        else if (light) intArrayOf(Color.WHITE, Color.parseColor("#E7ECF5"))
        else if (custom) intArrayOf(lighten(themeSurfaceColor, 0.08f), darken(themeSurfaceColor, 0.18f))
        else intArrayOf(Color.parseColor("#273248"), Color.parseColor("#172235"))
        val stroke = when {
            active -> 0x99FFFFFF.toInt()
            custom && !light -> Color.argb(165, Color.red(themeEdgeColor), Color.green(themeEdgeColor), Color.blue(themeEdgeColor))
            light -> 0x557F8DA5
            else -> 0x557D91B8
        }
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = 16.dp().toFloat()
            setStroke(1.dp(), stroke)
        }
    }

    private fun cardDrawable(light: Boolean, accent: Int): GradientDrawable {
        val custom = themeMode == THEME_CUSTOM
        val top = when {
            light -> Color.WHITE
            custom -> lighten(blend(themeSurfaceColor, accent, 0.07f), 0.035f)
            else -> blend(Color.parseColor("#1C2B42"), accent, 0.10f)
        }
        val bottom = when {
            light -> Color.parseColor("#F7F9FD")
            custom -> darken(themeSurfaceColor, 0.24f)
            else -> Color.parseColor("#101A2B")
        }
        val stroke = when {
            light -> 0x668C9BB1
            custom -> Color.argb(190, Color.red(themeEdgeColor), Color.green(themeEdgeColor), Color.blue(themeEdgeColor))
            else -> blend(0x667D91B8, accent, 0.28f)
        }
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = 25.dp().toFloat()
            setStroke(1.dp(), stroke)
        }
    }

    private fun accentDrawable(accent: Int): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(lighten(accent, 0.16f), darken(accent, 0.18f))
    ).apply {
        cornerRadius = 19.dp().toFloat()
        setStroke(1.dp(), 0x99FFFFFF.toInt())
    }

    private fun dangerDrawable(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.parseColor("#FB7185"), Color.parseColor("#BE3455"))
    ).apply { cornerRadius = 19.dp().toFloat(); setStroke(1.dp(), 0x99FFFFFF.toInt()) }

    private fun blend(base: Int, over: Int, ratio: Float): Int {
        val r = (Color.red(base) * (1f - ratio) + Color.red(over) * ratio).toInt()
        val g = (Color.green(base) * (1f - ratio) + Color.green(over) * ratio).toInt()
        val b = (Color.blue(base) * (1f - ratio) + Color.blue(over) * ratio).toInt()
        return Color.rgb(r, g, b)
    }

    private fun lighten(color: Int, amount: Float): Int = blend(color, Color.WHITE, amount.coerceIn(0f, 1f))
    private fun darken(color: Int, amount: Float): Int = blend(color, Color.BLACK, amount.coerceIn(0f, 1f))

    private fun setBarEffect(effect: String) {
        barEffect = effect
        saveSettings()
        updateEffectButtons()
        updateFloatingService()
    }

    private fun updateEffectButtons() {
        val light = isLightTheme()
        val ids = listOf(R.id.btnEffectClean, R.id.btnEffectGlossy, R.id.btnEffectNeon, R.id.btnEffectGlass)
        val styles = listOf(EFFECT_CLEAN, EFFECT_GLOSSY, EFFECT_NEON, EFFECT_GLASS)
        ids.forEachIndexed { index, id ->
            val view = findViewById<TextView>(id)
            val active = barEffect == styles[index]
            view.background = pillDrawable(active, light, currentAccent())
            view.setTextColor(if (active || !light) Color.WHITE else Color.parseColor("#31405A"))
        }
    }

    // -----------------------------------------------------------------
    // MODE / LIVE PREVIEW
    // -----------------------------------------------------------------

    private fun selectMode(vertical: Boolean) {
        isVerticalMode = vertical
        scanDisplayBounds()
        clampPosition()
        updateModeButtons()
        updateSingleModeVisibility()
        updateSpectrumBandLabels(animated = true)
        buildSpectrumBars()
        saveSettings()
        updateFloatingService()
    }

    private fun updateModeButtons() {
        val light = isLightTheme()
        val verticalActive = isVerticalMode
        findViewById<TextView>(R.id.btnModeVertical).apply {
            background = pillDrawable(verticalActive, light, currentAccent())
            setTextColor(if (verticalActive || !light) Color.WHITE else Color.parseColor("#31405A"))
        }
        findViewById<TextView>(R.id.btnModeHorizontal).apply {
            background = pillDrawable(!verticalActive, light, currentAccent())
            setTextColor(if (!verticalActive || !light) Color.WHITE else Color.parseColor("#31405A"))
        }
    }

    private fun buildSpectrumBars() {
        barsContainer.removeAllViews()
        previewBars.clear()
        barsContainer.orientation = LinearLayout.HORIZONTAL
        barsContainer.gravity = Gravity.CENTER or Gravity.BOTTOM
        val count = if (isVerticalMode) 1 else barCount
        repeat(count) { index ->
            val bar = PreviewBarView(this, index)
            val width = if (isVerticalMode) 22.dp() else 6.dp()
            val lp = LinearLayout.LayoutParams(width, 136.dp())
            barsContainer.addView(bar, lp)
            previewBars.add(bar)
        }
        barsContainer.post { adjustPreviewBarWidths() }
    }

    private fun adjustPreviewBarWidths() {
        if (isVerticalMode || previewBars.isEmpty()) return

        val availableWidth = barsContainer.width.takeIf { it > 0 }
        ?: (screenWidthDp * resources.displayMetrics.density).toInt()
        val slotWidth = (availableWidth / previewBars.size).coerceAtLeast(1)
        val barWidthPx = (slotWidth * 0.62f).toInt().coerceAtLeast(1)
        val sideMargin = ((slotWidth - barWidthPx) / 2).coerceAtLeast(0)

        previewBars.forEach { bar ->
            bar.layoutParams = LinearLayout.LayoutParams(
                barWidthPx,
                136.dp()
            ).apply {
                setMargins(sideMargin, 0, sideMargin, 0)
            }
        }
    }

    private fun updatePreviewLevels(levels: FloatArray) {
        previewBars.forEachIndexed { index, view ->
            val level = when {
                isVerticalMode && singleAudioMode == "DJ" -> levels.getOrElse(MAX_BAR_COUNT) { 0f }
                isVerticalMode -> levels.getOrElse(MAX_BAR_COUNT + 1) { 0f }
                else -> levels.getOrElse(index) { 0f }
            }
            (view as? PreviewBarView)?.setLevel(level)
        }
    }

    private inner class PreviewBarView(context: Context, private val index: Int) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var level = 0.08f

        fun setLevel(value: Float) {
            val target = value.coerceIn(0.02f, 1f)
            val response = responsePercent.coerceIn(0, 100) / 100f
            val factor = if (target >= level) 0.56f + response * 0.40f else 0.20f + response * 0.62f
            level += (target - level) * factor
            invalidate()
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0 || spectrumColors.isEmpty()) return

            val barHeight = height * level
            val colorBottom = spectrumColors[index % spectrumColors.size]
            val colorTop = spectrumColors[(index + 1) % spectrumColors.size]
            val rect = android.graphics.RectF(
                0f,
                height.toFloat() - barHeight,
                width.toFloat(),
                height.toFloat()
            )
            val radius = minOf(
                cornerRadiusDp.dp().toFloat(),
                width / 2f,
                height / 2f
            )
            val opacity = opacityPercent.coerceIn(0, 100) * 255 / 100
            val neonStrength = glowPercent.coerceIn(0, 100) / 100f
            val glowInset = minOf(10.dp().toFloat() * neonStrength, width * 0.32f)
            val bodyRect = android.graphics.RectF(rect).apply {
                if (glowInset > 0f) inset(glowInset, 0f)
            }

            if (neonStrength > 0f && bodyRect.width() > 1f) {
                for (layer in 3 downTo 1) {
                    val spread = glowInset * layer / 3f
                    val halo = android.graphics.RectF(
                        bodyRect.left - spread,
                        bodyRect.top - spread * 0.18f,
                        bodyRect.right + spread,
                        bodyRect.bottom + spread * 0.10f
                    )
                    val haloAlpha = (opacity * neonStrength * (0.18f + (3 - layer) * 0.11f)).toInt().coerceIn(0, 95)
                    paint.shader = null
                    paint.color = Color.argb(
                        haloAlpha,
                        Color.red(colorTop),
                        Color.green(colorTop),
                        Color.blue(colorTop)
                    )
                    canvas.drawRoundRect(halo, radius + spread, radius + spread, paint)
                }
            }
            paint.alpha = opacity

            paint.shader = if (isVerticalMode) {
                android.graphics.LinearGradient(
                    0f,
                    height.toFloat(),
                    0f,
                    rect.top,
                    spectrumColors.toIntArray(),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
            } else {
                android.graphics.LinearGradient(
                    0f,
                    height.toFloat(),
                    0f,
                    rect.top,
                    colorBottom,
                    colorTop,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(bodyRect, radius, radius, paint)
            paint.clearShadowLayer()
            paint.shader = null
            paint.alpha = opacity

            if (barEffect == EFFECT_GLOSSY || barEffect == EFFECT_GLASS) {
                paint.color = if (barEffect == EFFECT_GLASS) {
                    0x88FFFFFF.toInt()
                } else {
                    0x55FFFFFF
                }
                canvas.drawRoundRect(
                    bodyRect.left + bodyRect.width() * 0.16f,
                    bodyRect.top + 2.dp().toFloat(),
                    bodyRect.left + bodyRect.width() * 0.34f,
                    bodyRect.bottom - 2.dp().toFloat(),
                    radius,
                    radius,
                    paint
                )
            }
            paint.alpha = 255
        }
    }

    private fun updateSpectrumBandLabels(animated: Boolean) {
        val left = findViewById<TextView>(R.id.txtBandLeft) ?: return
        val center = findViewById<TextView>(R.id.txtBandCenter) ?: return
        val right = findViewById<TextView>(R.id.txtBandRight) ?: return
        val labels = if (isVerticalMode) {
            if (singleAudioMode == "DJ") {
                Triple("SINGLE", "DJ BASS", "LIVE LEVEL")
            } else {
                Triple("SINGLE", "MUSIC FULL", "LIVE LEVEL")
            }
        } else {
            Triple("BASS", "MID", "TREBLE")
        }
        val targets = listOf(left to labels.first, center to labels.second, right to labels.third)
        targets.forEach { (view, value) ->
            if (animated) {
                view.animate().cancel()
                view.animate().alpha(0.35f).setDuration(75L).withEndAction {
                    view.text = value
                    view.animate().alpha(1f).setDuration(125L).start()
                }.start()
            } else {
                view.text = value
                view.alpha = 1f
            }
        }
    }

    // -----------------------------------------------------------------
    // SINGLE BAR MODE
    // -----------------------------------------------------------------

    private fun buildSingleModePanel() {
        singleModeContainer.removeAllViews()
        singleModeContainer.layoutTransition = LayoutTransition().apply {
            setDuration(LayoutTransition.CHANGE_APPEARING, 210L)
            setDuration(LayoutTransition.CHANGE_DISAPPEARING, 160L)
            setDuration(LayoutTransition.APPEARING, 210L)
            setDuration(LayoutTransition.DISAPPEARING, 160L)
        }

        val card = glassPanel().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(17.dp(), 19.dp(), 17.dp(), 17.dp())
        }
        card.addView(sectionTitle("SINGLE BAR ENGINE"))
        card.addView(subtitle("Choose how the single meter reacts to sound").apply {
                setPadding(0, 5.dp(), 0, 0)
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 18.dp(), 0, 12.dp())
        }
        val dj = glassButton("DJ BASS")
        val music = glassButton("MUSIC FULL")
        val lp = LinearLayout.LayoutParams(0, 48.dp(), 1f)
        lp.setMargins(4.dp(), 0, 4.dp(), 0)
        row.addView(dj, lp)
        row.addView(music, LinearLayout.LayoutParams(0, 48.dp(), 1f).apply { setMargins(4.dp(), 0, 4.dp(), 0) })
        card.addView(row)

        val bass = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bassControls = bass
        val bassHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp(), 0, 2.dp())
        }
        bassHeader.addView(sectionTitle(if (appLanguage == "id") "RENTANG FREKUENSI BASS" else "BASS FREQUENCY RANGE"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val resetBass = glassButton("↺ RESET").apply { minWidth = 82.dp(); textSize = 10f }
        bassHeader.addView(resetBass, LinearLayout.LayoutParams(86.dp(), 36.dp()))
        bass.addView(bassHeader)
        addControlSlider(bass, "BASS START", bassMinHz, 0, 400, " Hz") {
            bassMinHz = it.coerceIn(0, 400); saveSettings(); updateFloatingService()
        }
        addControlSlider(bass, "BASS END", bassMaxHz, 0, 400, " Hz") {
            bassMaxHz = it.coerceIn(0, 400); saveSettings(); updateFloatingService()
        }
        resetBass.setOnClickListener {
            bassMinHz = 60
            bassMaxHz = 160
            syncSliderValue("BASS START", bassMinHz)
            syncSliderValue("BASS END", bassMaxHz)
            saveSettings()
            updateFloatingService()
            resetBass.animate().rotationBy(360f).setDuration(420L).start()
        }
        card.addView(bass)

        dj.setOnClickListener {
            singleAudioMode = "DJ"
            updateSingleModeButtons()
            updateSpectrumBandLabels(animated = true)
            saveSettings()
            updateFloatingService()
        }
        music.setOnClickListener {
            singleAudioMode = "MUSIC"
            updateSingleModeButtons()
            updateSpectrumBandLabels(animated = true)
            saveSettings()
            updateFloatingService()
        }
        singleModeCard = card
        singleModeContainer.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 13.dp() }
        )
        updateSingleModeButtons()
        updateSingleModeVisibility()
    }

    private fun updateSingleModeButtons() {
        val card = singleModeCard ?: return
        val row = card.getChildAt(2) as? LinearLayout ?: return
        val light = isLightTheme()
        val djActive = singleAudioMode == "DJ"
        row.getChildAt(0).background = pillDrawable(djActive, light, currentAccent())
        row.getChildAt(1).background = pillDrawable(!djActive, light, currentAccent())
        (row.getChildAt(0) as? TextView)?.setTextColor(if (djActive || !light) Color.WHITE else Color.DKGRAY)
        (row.getChildAt(1) as? TextView)?.setTextColor(if (!djActive || !light) Color.WHITE else Color.DKGRAY)
        bassControls?.visibility = if (djActive) View.VISIBLE else View.GONE
    }

    private fun updateSingleModeVisibility() {
        val card = singleModeCard ?: return
        card.animate().cancel()
        if (isVerticalMode) {
            card.visibility = View.VISIBLE
            card.alpha = 0f
            card.animate().alpha(1f).setDuration(190L).start()
        } else {
            card.animate().alpha(0f).setDuration(150L).withEndAction { card.visibility = View.GONE }.start()
        }
    }

    // -----------------------------------------------------------------
    // FLOATING CONTROLS
    // -----------------------------------------------------------------

    private fun buildFloatingControls() {
        controlsContainer.removeAllViews()
        // Manual values and sliders share broad, bounded ranges. The overlay renderer
        // safely fits oversized dimensions to the current display instead of overflowing it.
        addControlSlider(controlsContainer, "HORIZONTAL WIDTH", overlayWidthDp, 0, 4096, " dp") { overlayWidthDp = it; clampPosition() }
        addControlSlider(controlsContainer, "BAR HEIGHT / LENGTH", overlayHeightDp, 0, 4096, " dp") { overlayHeightDp = it; clampPosition() }
        addControlSlider(controlsContainer, "HORIZONTAL BAR COUNT", barCount, 0, MAX_BAR_COUNT, " bars") {
            barCount = it
            if (!isVerticalMode) buildSpectrumBars()
        }
        addControlSlider(controlsContainer, "BAR THICKNESS", barWidthDp, 0, 2048, " dp") { barWidthDp = it; clampPosition() }
        addControlSlider(controlsContainer, "BAR GAP", barGapDp, 0, 512, " dp") { barGapDp = it }
        addControlSlider(controlsContainer, "SCALE", scalePercent, 0, 500, "%") { scalePercent = it; clampPosition() }
        addControlSlider(controlsContainer, "OPACITY", opacityPercent, 0, 100, "%") { opacityPercent = it }
        addControlSlider(controlsContainer, "GLOW INTENSITY", glowPercent, 0, 100, "%") { glowPercent = it }
        addControlSlider(controlsContainer, "RESPONSE SPEED", responsePercent, 0, 100, "%") { responsePercent = it }
        addControlSlider(controlsContainer, "CORNER ROUNDNESS", cornerRadiusDp, 0, 512, " dp") { cornerRadiusDp = it }
    }

    private fun addControlSlider(
        panel: LinearLayout,
        name: String,
        current: Int,
        minimum: Int,
        maximum: Int,
        unit: String,
        onValue: (Int) -> Unit
    ) {
        val safeMax = maximum.coerceAtLeast(minimum + 1)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = 40.dp()
            setPadding(0, 8.dp(), 0, 1.dp())
        }

        val label = subtitle(name).apply {
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }

        val valueInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setText(current.toString())
            textSize = 12f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = 72.dp()
            setTextColor(if (isLightTheme()) Color.parseColor("#26344B") else Color.WHITE)
            setHintTextColor(if (isLightTheme()) Color.parseColor("#8290A6") else Color.parseColor("#98A6BD"))
            setPadding(5.dp(), 0, 5.dp(), 0)
            background = glassEditDrawable()
        }

        val unitLabel = subtitle(unit).apply {
            gravity = Gravity.CENTER_VERTICAL
            minWidth = 42.dp()
            maxLines = 1
            setPadding(7.dp(), 0, 0, 0)
        }

        header.addView(
            label,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            valueInput,
            LinearLayout.LayoutParams(76.dp(), 36.dp())
        )
        header.addView(
            unitLabel,
            LinearLayout.LayoutParams(48.dp(), 36.dp())
        )

        val seek = SeekBar(this)
        seek.max = safeMax - minimum
        seek.progress = (current - minimum).coerceIn(0, seek.max)
        styleAppearanceSeekBar(seek)

        var updatingInput = false

        fun commitValue(rawValue: Int?) {
            val value = (rawValue ?: return).coerceIn(minimum, safeMax)
            updatingInput = true
            valueInput.setText(value.toString())
            valueInput.setSelection(valueInput.text.length)
            updatingInput = false

            seek.progress = value - minimum
            onValue(value)
            scheduleRealtimeUpdate()
        }

        valueInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitValue(valueInput.text.toString().toIntOrNull())
                valueInput.clearFocus()
                true
            } else {
                false
            }
        }
        valueInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val typed = valueInput.text.toString().toIntOrNull()
                if (typed != null) commitValue(typed)
                else {
                    valueInput.setText(seek.progress.plus(minimum).toString())
                }
            }
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (!fromUser) return
                    val value = minimum + progress
                    if (!valueInput.hasFocus()) {
                        updatingInput = true
                        valueInput.setText(value.toString())
                        updatingInput = false
                    }
                    onValue(value)
                    scheduleRealtimeUpdate()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    if (valueInput.hasFocus()) {
                        valueInput.clearFocus()
                        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE)
                        as android.view.inputmethod.InputMethodManager
                        inputManager.hideSoftInputFromWindow(valueInput.windowToken, 0)
                    }
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    saveSettings()
                    updateFloatingService()
                }
        })

        panel.addView(header)
        panel.addView(seek)
        sliderBindings[name] = SliderBinding(seek, valueInput, minimum, safeMax)
    }

    private fun styleAppearanceSeekBar(seek: SeekBar) {
        seek.progressTintList = ColorStateList.valueOf(currentAccent())
        seek.thumbTintList = ColorStateList.valueOf(currentAccent())

        getDrawable(R.drawable.seekbar_thumb_glass)
        ?.mutate()
        ?.let { seek.thumb = it }

        seek.thumbTintList = ColorStateList.valueOf(currentAccent())
        seek.thumbOffset = 12.dp()
        seek.minHeight = 34.dp()
        seek.setPadding(12.dp(), 0, 12.dp(), 0)
        seek.splitTrack = false
    }

    private fun syncSliderValue(name: String, value: Int) {
        val binding = sliderBindings[name] ?: return
        val safeValue = value.coerceIn(binding.minimum, binding.maximum)
        binding.input.setText(safeValue.toString())
        android.animation.ObjectAnimator.ofInt(
            binding.seek, "progress", binding.seek.progress, safeValue - binding.minimum
        ).apply {
            duration = 260L
            interpolator = android.view.animation.DecelerateInterpolator()
            start()
        }
    }

    // -----------------------------------------------------------------
    // COLOR PROFILE STUDIO: FIVE INDEPENDENT STOPS
    // -----------------------------------------------------------------

    // -----------------------------------------------------------------
    // PERSISTENT COLOR PROFILE LIBRARY
    // -----------------------------------------------------------------

    private data class ColorProfileEntry(
        val name: String,
        val colors: List<Int>,
        val effect: String,
        val widthDp: Int,
        val heightDp: Int,
        val barCount: Int,
        val barWidthDp: Int,
        val gapDp: Int,
        val scalePercent: Int,
        val opacityPercent: Int,
        val glowPercent: Int,
        val responsePercent: Int,
        val cornerRadiusDp: Int
    )

    private fun readColorProfiles(): MutableList<ColorProfileEntry> {
        val raw = prefs.getString("color_profiles", null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<ColorProfileEntry>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                val colorArray = item.optJSONArray("colors") ?: continue
                val colors = mutableListOf<Int>()
                for (j in 0 until colorArray.length()) {
                    val colorText = colorArray.optString(j)
                    try {
                        colors.add(Color.parseColor(colorText))
                    } catch (_: Exception) {
                        // Ignore a damaged color stop and keep valid entries.
                    }
                }
                if (name.isNotBlank() && colors.size >= 2) {
                    val effect = item.optString("effect", EFFECT_GLOSSY)
                    .takeIf { it in listOf(EFFECT_CLEAN, EFFECT_GLOSSY, EFFECT_NEON, EFFECT_GLASS) }
                    ?: EFFECT_GLOSSY
                    result.add(
                        ColorProfileEntry(
                            name = name,
                            colors = colors.take(5),
                            effect = effect,
                            widthDp = item.optInt("width", 320).coerceIn(0, 4096),
                            heightDp = item.optInt("height", 360).coerceIn(0, 4096),
                            barCount = item.optInt("bar_count", 15).coerceIn(0, MAX_BAR_COUNT),
                            barWidthDp = item.optInt("bar_width", 20).coerceIn(0, 2048),
                            gapDp = item.optInt("bar_gap", 3).coerceIn(0, 512),
                            scalePercent = item.optInt("scale", 100).coerceIn(0, 500),
                            opacityPercent = item.optInt("opacity", 100).coerceIn(0, 100),
                            glowPercent = item.optInt("glow", 65).coerceIn(0, 100),
                            responsePercent = item.optInt("response_speed", 82).coerceIn(0, 100),
                            cornerRadiusDp = item.optInt("corner_radius", 14).coerceIn(0, 512)
                        )
                    )
                }
            }
            result
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun writeColorProfiles(profiles: List<ColorProfileEntry>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            val item = JSONObject()
            item.put("name", profile.name)
            val colors = JSONArray()
            profile.colors.take(5).forEach { color ->
                colors.put(String.format("#%06X", 0xFFFFFF and color))
            }
            item.put("colors", colors)
            item.put("effect", profile.effect)
            item.put("width", profile.widthDp)
            item.put("height", profile.heightDp)
            item.put("bar_count", profile.barCount)
            item.put("bar_width", profile.barWidthDp)
            item.put("bar_gap", profile.gapDp)
            item.put("scale", profile.scalePercent)
            item.put("opacity", profile.opacityPercent)
            item.put("glow", profile.glowPercent)
            item.put("response_speed", profile.responsePercent)
            item.put("corner_radius", profile.cornerRadiusDp)
            array.put(item)
        }
        prefs.edit().putString("color_profiles", array.toString()).apply()
    }

    private fun saveColorProfile(name: String, colors: List<Int>): Boolean {
        val safeName = name.trim().take(28)
        if (safeName.isBlank() || colors.size < 2) return false

        val profiles = readColorProfiles()
        val existing = profiles.indexOfFirst {
            it.name.equals(safeName, ignoreCase = true)
        }
        val entry = ColorProfileEntry(
            name = safeName,
            colors = colors.take(5).toList(),
            effect = barEffect,
            widthDp = overlayWidthDp,
            heightDp = overlayHeightDp,
            barCount = barCount,
            barWidthDp = barWidthDp,
            gapDp = barGapDp,
            scalePercent = scalePercent,
            opacityPercent = opacityPercent,
            glowPercent = glowPercent,
            responsePercent = responsePercent,
            cornerRadiusDp = cornerRadiusDp
        )
        if (existing >= 0) profiles[existing] = entry
        else profiles.add(entry)

        writeColorProfiles(profiles)
        prefs.edit()
            .putString("active_color_profile", safeName)
            .putString("last_managed_color_profile", safeName)
            .apply()
        loadedProfileName = safeName
        return true
    }

    private fun showProfileNameDialog(
        title: String,
        initialName: String = "",
        onSave: (String) -> Unit
    ) {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 12.dp(), 8.dp(), 8.dp())
            addView(subtitle(if (appLanguage == "id") "Kasih nama yang gampang kamu kenali." else "Choose a name you will recognize later."))
        }
        val input = EditText(this).apply {
            hint = if (appLanguage == "id") "Nama profil" else "Profile name"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(initialName)
            setSelection(text.length)
            setTextColor(if (isLightTheme()) Color.DKGRAY else Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            background = glassEditDrawable()
        }
        body.addView(input, matchWrap().apply { topMargin = 10.dp() })
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        val save = glassButton(if (appLanguage == "id") "SIMPAN" else "SAVE")
        footer.addView(cancel, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(save, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(title, body, footer, 0.90f, 0.48f)
        fun commitName() {
            val value = input.text.toString().trim()
            if (value.isBlank()) {
                input.error = if (appLanguage == "id") "Nama profil jangan kosong" else "Name cannot be empty"
                return
            }
            onSave(value)
            dialog.dismiss()
        }
        cancel.setOnClickListener { dialog.dismiss() }
        save.setOnClickListener { commitName() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitName()
                true
            } else false
        }
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        input.requestFocus()
    }

    private fun showColorProfileManager(
        onUse: (ColorProfileEntry) -> Unit,
        onDelete: (ColorProfileEntry) -> Unit,
        onRename: (String, String) -> Unit
    ) {
        val profiles = readColorProfiles()
        if (profiles.isEmpty()) {
            Toast.makeText(this, if (appLanguage == "id") "Belum ada profil. Simpan dulu lewat SIMPAN SEBAGAI." else "No saved profiles yet. Use SAVE AS PROFILE first.", Toast.LENGTH_LONG).show()
            return
        }

        val preferredProfileName = prefs.getString("last_managed_color_profile", null)
            ?: loadedProfileName
        val initialIndex = profiles.indexOfFirst {
            it.name.equals(preferredProfileName, ignoreCase = true)
        }.let { if (it >= 0) it else profiles.lastIndex }
        var selectedIndex = initialIndex.coerceIn(0, profiles.lastIndex)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val listPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(5.dp(), 5.dp(), 5.dp(), 8.dp())
        }
        val rowViews = mutableListOf<TextView>()
        fun refreshSelection() {
            rowViews.forEachIndexed { index, row ->
                val selected = index == selectedIndex
                row.text = (if (selected) "◉  " else "○  ") + profiles[index].name
                row.background = pillDrawable(selected, isLightTheme(), currentAccent())
                row.setTextColor(if (selected || !isLightTheme()) Color.WHITE else Color.DKGRAY)
            }
        }
        profiles.forEachIndexed { index, profile ->
            val row = glassButton(profile.name).apply {
                gravity = Gravity.CENTER_VERTICAL
                textSize = 13f
                setPadding(14.dp(), 11.dp(), 14.dp(), 11.dp())
                setOnClickListener {
                    selectedIndex = index
                    prefs.edit().putString("last_managed_color_profile", profile.name).apply()
                    refreshSelection()
                }
            }
            listPanel.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()).apply {
                bottomMargin = 7.dp()
            })
            rowViews.add(row)
        }
        refreshSelection()
        scroll.addView(listPanel)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 7.dp(), 0, 0)
        }
        val renameButton = glassButton(if (appLanguage == "id") "GANTI NAMA" else "RENAME")
        val deleteButton = glassButton(if (appLanguage == "id") "HAPUS" else "DELETE")
        val loadButton = glassButton(if (appLanguage == "id") "MUAT" else "LOAD")
        listOf(renameButton, deleteButton, loadButton).forEach { button ->
            footer.addView(button, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply {
                setMargins(3.dp(), 0, 3.dp(), 0)
            })
        }
        val dialog = showGlassStudioDialog(
            if (appLanguage == "id") "PROFIL WARNA TERSIMPAN" else "SAVED COLOR PROFILES",
            scroll, footer, 0.96f, 0.72f
        )
        loadButton.setOnClickListener {
            val chosen = profiles[selectedIndex]
            prefs.edit()
                .putString("last_managed_color_profile", chosen.name)
                .putString("active_color_profile", chosen.name)
                .apply()
            onUse(chosen)
            dialog.dismiss()
        }
        renameButton.setOnClickListener {
            val chosen = profiles[selectedIndex]
            dialog.dismiss()
            showProfileNameDialog(if (appLanguage == "id") "GANTI NAMA PROFIL" else "RENAME PROFILE", chosen.name) { newName ->
                val safeName = newName.trim().take(28)
                val latest = readColorProfiles()
                val index = latest.indexOfFirst { it.name.equals(chosen.name, ignoreCase = true) }
                val collision = latest.indexOfFirst { it.name.equals(safeName, ignoreCase = true) }
                if (safeName.isBlank()) {
                    Toast.makeText(this, if (appLanguage == "id") "Nama profil tidak boleh kosong" else "Profile name cannot be empty", Toast.LENGTH_SHORT).show()
                } else if (index >= 0 && collision >= 0 && collision != index) {
                    Toast.makeText(this, if (appLanguage == "id") "Nama itu sudah dipakai" else "That profile name is already used", Toast.LENGTH_SHORT).show()
                } else if (index >= 0) {
                    latest[index] = latest[index].copy(name = safeName)
                    writeColorProfiles(latest)
                    prefs.edit()
                        .putString("last_managed_color_profile", safeName)
                        .apply()
                    if (prefs.getString("active_color_profile", null).equals(chosen.name, ignoreCase = true)) {
                        prefs.edit().putString("active_color_profile", safeName).apply()
                    }
                    onRename(chosen.name, safeName)
                    Toast.makeText(this, if (appLanguage == "id") "Nama profil diperbarui" else "Profile renamed", Toast.LENGTH_SHORT).show()
                }
            }
        }
        deleteButton.setOnClickListener {
            val chosen = profiles[selectedIndex]
            showGlassConfirmDialog(
                if (appLanguage == "id") "HAPUS PROFIL?" else "DELETE PROFILE?",
                if (appLanguage == "id") "Profil ${chosen.name} akan dihapus dari perangkat ini." else "${chosen.name} will be removed from this device.",
                if (appLanguage == "id") "BATAL" else "CANCEL",
                if (appLanguage == "id") "HAPUS" else "DELETE",
                0.90f, 0.44f
            ) {
                val latest = readColorProfiles().filterNot { it.name.equals(chosen.name, ignoreCase = true) }
                writeColorProfiles(latest)
                val fallbackProfile = latest.lastOrNull()?.name
                prefs.edit().putString("last_managed_color_profile", fallbackProfile).apply()
                if (prefs.getString("active_color_profile", null).equals(chosen.name, ignoreCase = true)) {
                    prefs.edit().remove("active_color_profile").apply()
                }
                onDelete(chosen)
                Toast.makeText(this, if (appLanguage == "id") "Profil dihapus" else "Profile deleted", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
    }

    private fun refreshColorSwatches() {
        colorSwatches.removeAllViews()
        spectrumColors.take(5).forEachIndexed { index, color ->
            val swatch = View(this).apply {
                background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 50.dp().toFloat()
                    setStroke(1.dp(), 0xAAFFFFFF.toInt())
                }
            }
            val lp = LinearLayout.LayoutParams(0, 25.dp(), 1f)
            lp.setMargins(4.dp(), 0, 4.dp(), 0)
            colorSwatches.addView(swatch, lp)
        }
        previewBars.forEach { it.invalidate() }
    }

    private fun showColorStudio() {
        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, 0, 0, 14.dp())
        }
        val panel = glassPanel().apply {
            orientation = LinearLayout.VERTICAL
            background = null
            setPadding(17.dp(), 17.dp(), 17.dp(), 17.dp())
        }
        scroll.background = null
        scroll.clipToOutline = false
        panel.background = null
        panel.addView(subtitle(if (appLanguage == "id") "Edit lima titik warna secara terpisah." else "Edit each of the five color stops independently"))
        panel.addView(subtitle("Edit each of the five color stops independently"))

        var selectedIndex = 0
        var usingPreset = false
        var selectedProfile: ColorProfileEntry? = loadedProfileName?.let { activeName ->
            readColorProfiles().firstOrNull { it.name.equals(activeName, ignoreCase = true) }
        }
        var saveChangesButtonRef: TextView? = null
        val originalSpectrumColors = spectrumColors.toList()
        val draftColors = spectrumColors.toMutableList()
        var acceptedPalette = false
        val hsv = FloatArray(3)
        Color.colorToHSV(draftColors[selectedIndex], hsv)

        val preview = View(this)
        panel.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 54.dp()).apply { topMargin = 12.dp() })

        val slotRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val slotButtons = mutableListOf<TextView>()
        repeat(5) { index ->
            val button = glassButton("${index + 1}")
            val lp = LinearLayout.LayoutParams(0, 42.dp(), 1f)
            lp.setMargins(3.dp(), 10.dp(), 3.dp(), 0)
            slotRow.addView(button, lp)
            slotButtons.add(button)
        }
        panel.addView(slotRow)

        val selectedLabel = sectionTitle(if (appLanguage == "id") "TITIK WARNA 1" else "COLOR STOP 1")
        selectedLabel.setPadding(0, 14.dp(), 0, 3.dp())
        panel.addView(selectedLabel)

        val colorWheel = ColorWheelView(this)
        panel.addView(
            colorWheel,
            LinearLayout.LayoutParams(216.dp(), 216.dp()).apply {
                gravity = Gravity.CENTER
                topMargin = 7.dp()
                bottomMargin = 4.dp()
            }
        )

        val hexInput = EditText(this).apply {
            hint = "HEX · #8B5CF6"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            setTextColor(if (isLightTheme()) Color.parseColor("#26344B") else Color.WHITE)
            setHintTextColor(if (isLightTheme()) Color.parseColor("#8693A8") else Color.parseColor("#8593A8"))
            setPadding(12.dp(), 9.dp(), 12.dp(), 9.dp())
            background = glassEditDrawable()
        }
        panel.addView(hexInput, matchWrap().apply { topMargin = 8.dp() })
        val applyHexButton = glassButton("APPLY HEX VALUE")
        panel.addView(applyHexButton, matchWrap().apply { topMargin = 6.dp() })

        val rgbRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp(), 0, 0)
        }
        fun profileRgbField(label: String): EditText = EditText(this).apply {
            hint = label
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(if (isLightTheme()) Color.parseColor("#26344B") else Color.WHITE)
            setHintTextColor(if (isLightTheme()) Color.parseColor("#8693A8") else Color.parseColor("#8593A8"))
            setPadding(4.dp(), 7.dp(), 4.dp(), 7.dp())
            background = glassEditDrawable()
        }
        val redInput = profileRgbField("R")
        val greenInput = profileRgbField("G")
        val blueInput = profileRgbField("B")
        listOf(redInput, greenInput, blueInput).forEachIndexed { index, field ->
            val channelLabel = listOf("R", "G", "B")[index]
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(subtitle(channelLabel))
                addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40.dp()))
            }
            rgbRow.addView(
                column,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(3.dp(), 0, 3.dp(), 0)
                }
            )
        }
        panel.addView(rgbRow)
        val applyRgbButton = glassButton("APPLY RGB VALUES")
        panel.addView(applyRgbButton, matchWrap().apply { topMargin = 6.dp() })

        val hueLabel = subtitle("HUE  ${hsv[0].toInt()}°")
        val satLabel = subtitle("${if (appLanguage == "id") "SATURASI" else "SATURATION"}  ${(hsv[1] * 100).toInt()}%")
        val valueLabel = subtitle("${if (appLanguage == "id") "KECERAHAN" else "BRIGHTNESS"}  ${(hsv[2] * 100).toInt()}%")
        var internalHexUpdate = false

        fun buildColorSeek(label: TextView, max: Int, initial: Int): SeekBar {
            val seek = SeekBar(this).apply {
                this.max = max
                progress = initial.coerceIn(0, max)
                styleAppearanceSeekBar(this)
            }
            panel.addView(label)
            panel.addView(seek)
            return seek
        }

        val hueSeek = buildColorSeek(hueLabel, 360, hsv[0].toInt())
        val satSeek = buildColorSeek(satLabel, 100, (hsv[1] * 100).toInt())
        val valueSeek = buildColorSeek(valueLabel, 100, (hsv[2] * 100).toInt())

        fun updateEditorFromColor(color: Int) {
            Color.colorToHSV(color, hsv)
            colorWheel.setColor(color, notify = false)
            hueSeek.progress = hsv[0].toInt().coerceIn(0, 360)
            satSeek.progress = (hsv[1] * 100).toInt().coerceIn(0, 100)
            valueSeek.progress = (hsv[2] * 100).toInt().coerceIn(0, 100)
            hueLabel.text = "HUE  ${hsv[0].toInt()}°"
            satLabel.text = "${if (appLanguage == "id") "SATURASI" else "SATURATION"}  ${(hsv[1] * 100).toInt()}%"
            valueLabel.text = "${if (appLanguage == "id") "KECERAHAN" else "BRIGHTNESS"}  ${(hsv[2] * 100).toInt()}%"
            internalHexUpdate = true
            hexInput.setText(String.format("#%06X", 0xFFFFFF and color))
            redInput.setText(Color.red(color).toString())
            greenInput.setText(Color.green(color).toString())
            blueInput.setText(Color.blue(color).toString())
            internalHexUpdate = false
            preview.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, draftColors.toIntArray()).apply { cornerRadius = 16.dp().toFloat() }
            slotButtons.forEachIndexed { index, button ->
                button.background = pillDrawable(index == selectedIndex, isLightTheme(), currentAccent())
                button.setTextColor(if (index == selectedIndex || !isLightTheme()) Color.WHITE else Color.DKGRAY)
            }
            selectedLabel.text = "${if (appLanguage == "id") "TITIK WARNA" else "COLOR STOP"} ${selectedIndex + 1}"
        }

        fun publishDraftPaletteLive() {
            spectrumColors.clear()
            spectrumColors.addAll(draftColors.take(5))
            refreshColorSwatches()
            scheduleRealtimeUpdate()
        }

        fun applyCurrentColor() {
            val color = Color.HSVToColor(hsv)
            draftColors[selectedIndex] = color
            colorWheel.setColor(color, notify = false)
            preview.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, draftColors.toIntArray()).apply { cornerRadius = 16.dp().toFloat() }
            hexInput.setText(String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and color))
            redInput.setText(Color.red(color).toString())
            greenInput.setText(Color.green(color).toString())
            blueInput.setText(Color.blue(color).toString())
            usingPreset = false
            publishDraftPaletteLive()
        }

        fun applyHexValue(): Boolean {
            val raw = hexInput.text.toString().trim()
            if (raw.isBlank()) {
                hexInput.error = if (appLanguage == "id") "Masukkan kode HEX" else "Enter a HEX value"
                return false
            }
            val normalized = if (raw.startsWith("#")) raw else "#$raw"
            if (!normalized.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                hexInput.error = if (appLanguage == "id") "Formatnya #RRGGBB ya" else "Use the #RRGGBB format"
                return false
            }
            val color = try {
                Color.parseColor(normalized)
            } catch (_: IllegalArgumentException) {
                hexInput.error = if (appLanguage == "id") "Kode warna belum valid" else "That color value is invalid"
                return false
            }
            updateEditorFromColor(color)
            applyCurrentColor()
            hexInput.error = null
            return true
        }

        applyHexButton.setOnClickListener { applyHexValue() }
        hexInput.imeOptions = EditorInfo.IME_ACTION_DONE
        hexInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val applied = applyHexValue()
                if (applied) hexInput.clearFocus()
                applied
            } else false
        }

        applyRgbButton.setOnClickListener {
            val red = redInput.text.toString().toIntOrNull()
            val green = greenInput.text.toString().toIntOrNull()
            val blue = blueInput.text.toString().toIntOrNull()
            if (red == null || green == null || blue == null) {
                Toast.makeText(this, "Isi nilai R, G, dan B dari 0 sampai 255", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            updateEditorFromColor(
                Color.rgb(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
            )
            applyCurrentColor()
        }

        colorWheel.onColorChanged = { color ->
            Color.colorToHSV(color, hsv)
            hueSeek.progress = hsv[0].toInt().coerceIn(0, 360)
            satSeek.progress = (hsv[1] * 100f).toInt().coerceIn(0, 100)
            valueSeek.progress = (hsv[2] * 100f).toInt().coerceIn(0, 100)
            hueLabel.text = "HUE  ${hsv[0].toInt()}°"
            satLabel.text = "${if (appLanguage == "id") "SATURASI" else "SATURATION"}  ${(hsv[1] * 100).toInt()}%"
            valueLabel.text = "${if (appLanguage == "id") "KECERAHAN" else "BRIGHTNESS"}  ${(hsv[2] * 100).toInt()}%"
            applyCurrentColor()
        }

        hueSeek.setOnSeekBarChangeListener(simpleSeekListener { hsv[0] = it.toFloat(); hueLabel.text = "HUE  ${it}°"; applyCurrentColor() })
        satSeek.setOnSeekBarChangeListener(simpleSeekListener { hsv[1] = it / 100f; satLabel.text = "SATURATION  $it%"; applyCurrentColor() })
        valueSeek.setOnSeekBarChangeListener(simpleSeekListener { hsv[2] = it / 100f; valueLabel.text = "BRIGHTNESS  $it%"; applyCurrentColor() })

        hexInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!internalHexUpdate) usingPreset = false
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
        })

        slotButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectedIndex = index
                updateEditorFromColor(draftColors[selectedIndex])
            }
        }

        panel.addView(sectionTitle("COLOR PRESETS").apply { setPadding(0, 14.dp(), 0, 7.dp()) })
        val presetScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presetScroll.addView(presetRow)
        val presets = listOf(
            Pair("NEON", intArrayOf(Color.parseColor("#00F5FF"), Color.parseColor("#A855F7"), Color.parseColor("#FF00AA"), Color.parseColor("#FFFFFF"), Color.parseColor("#22D3EE"))),
            Pair("SUNSET", intArrayOf(Color.parseColor("#FF1744"), Color.parseColor("#FF9100"), Color.parseColor("#FFEA00"), Color.parseColor("#9C27B0"), Color.parseColor("#FF6B35"))),
            Pair("OCEAN", intArrayOf(Color.parseColor("#001F54"), Color.parseColor("#0077B6"), Color.parseColor("#00B4D8"), Color.parseColor("#90E0EF"), Color.WHITE)),
            Pair("FOREST", intArrayOf(Color.parseColor("#14532D"), Color.parseColor("#22C55E"), Color.parseColor("#A3E635"), Color.parseColor("#FACC15"), Color.parseColor("#0F766E"))),
            Pair("ICE", intArrayOf(Color.WHITE, Color.parseColor("#BAE6FD"), Color.parseColor("#38BDF8"), Color.parseColor("#4F46E5"), Color.parseColor("#A5F3FC")))
        )
        presets.forEach { preset ->
            val chip = TextView(this).apply {
                text = preset.first
                gravity = Gravity.CENTER
                textSize = 10f
                setTextColor(Color.WHITE)
                setPadding(15.dp(), 13.dp(), 15.dp(), 13.dp())
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, preset.second).apply {
                    cornerRadius = 15.dp().toFloat()
                    setStroke(1.dp(), 0x88FFFFFF.toInt())
                }
                setOnClickListener {
                    draftColors.clear()
                    draftColors.addAll(preset.second.toList())
                    selectedIndex = 0
                    loadedProfileName = null
                    selectedProfile = null
                    prefs.edit().remove("active_color_profile").apply()
                    saveChangesButtonRef?.let { button ->
                        button.isEnabled = false
                        button.alpha = 0.48f
                    }
                    usingPreset = true
                    updateEditorFromColor(draftColors[0])
                    spectrumColors.clear()
                    spectrumColors.addAll(draftColors.take(5))
                    refreshColorSwatches()
                    scheduleRealtimeUpdate()
                }
            }
            presetRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 43.dp()).apply { setMargins(0, 0, 7.dp(), 0) })
        }
        panel.addView(presetScroll)
        updateEditorFromColor(draftColors[0])

        val profileActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 15.dp(), 0, 0)
        }
        val saveAsButton = glassButton("SAVE AS PROFILE")
        val manageButton = glassButton("MANAGE")
        val saveChangesButton = glassButton("SAVE CHANGES").apply {
            isEnabled = !loadedProfileName.isNullOrBlank()
            alpha = if (isEnabled) 1f else 0.48f
        }
        saveChangesButtonRef = saveChangesButton
        profileActions.addView(
            saveAsButton,
            LinearLayout.LayoutParams(0, 44.dp(), 1f).apply {
                setMargins(3.dp(), 0, 5.dp(), 0)
            }
        )
        profileActions.addView(
            manageButton,
            LinearLayout.LayoutParams(0, 44.dp(), 1f).apply {
                setMargins(5.dp(), 0, 3.dp(), 0)
            }
        )
        saveAsButton.setOnClickListener {
            showProfileNameDialog("SAVE COLOR PROFILE") { profileName ->
                if (saveColorProfile(profileName, draftColors)) {
                    loadedProfileName = profileName
                    prefs.edit().putString("active_color_profile", profileName).putString("last_managed_color_profile", profileName).apply()
                    saveChangesButton.isEnabled = true
                    saveChangesButton.alpha = 1f
                    selectedProfile = readColorProfiles().firstOrNull {
                        it.name.equals(profileName, ignoreCase = true)
                    }
                    Toast.makeText(this, "Profile saved: $profileName", Toast.LENGTH_SHORT).show()
                }
            }
        }
        manageButton.setOnClickListener {
            showColorProfileManager(
                onUse = { profile ->
                    loadedProfileName = profile.name
                    prefs.edit().putString("active_color_profile", profile.name).putString("last_managed_color_profile", profile.name).apply()
                    saveChangesButton.isEnabled = true
                    saveChangesButton.alpha = 1f
                    selectedProfile = profile
                    draftColors.clear()
                    draftColors.addAll(profile.colors)
                    selectedIndex = 0
                    updateEditorFromColor(draftColors[0])
                    publishDraftPaletteLive()
                    Toast.makeText(this, if (appLanguage == "id") "Profil ${profile.name} dimuat. Tekan Terapkan untuk menyimpan gaya bar." else "Loaded ${profile.name}. Apply to keep its palette and bar style.", Toast.LENGTH_SHORT).show()
                },
                onDelete = { deletedProfile ->
                    if (loadedProfileName.equals(deletedProfile.name, ignoreCase = true)) {
                        loadedProfileName = null
                        selectedProfile = null
                        prefs.edit().remove("active_color_profile").apply()
                        saveChangesButton.isEnabled = false
                        saveChangesButton.alpha = 0.48f
                    }
                },
                onRename = { oldName, newName ->
                    if (loadedProfileName.equals(oldName, ignoreCase = true)) {
                        loadedProfileName = newName
                        selectedProfile = readColorProfiles().firstOrNull { it.name.equals(newName, ignoreCase = true) }
                        saveChangesButton.isEnabled = true
                        saveChangesButton.alpha = 1f
                    }
                }
            )
        }
        panel.addView(profileActions)
        saveChangesButton.setOnClickListener {
            val name = loadedProfileName
            if (name.isNullOrBlank()) {
                Toast.makeText(this, if (appLanguage == "id") "Muat profil dulu atau simpan profil baru" else "Load or save a profile before saving changes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (saveColorProfile(name, draftColors)) {
                prefs.edit().putString("active_color_profile", name).putString("last_managed_color_profile", name).apply()
                selectedProfile = readColorProfiles().firstOrNull { it.name.equals(name, ignoreCase = true) }
                Toast.makeText(this, if (appLanguage == "id") "Perubahan profil disimpan" else "Profile changes saved", Toast.LENGTH_SHORT).show()
            }
        }
        panel.addView(saveChangesButton, matchWrap().apply { topMargin = 7.dp() })
        val profileFileRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val exportProfileButton = glassButton("EXPORT PROFILE")
        val importProfileButton = glassButton("IMPORT PROFILE")
        profileFileRow.addView(exportProfileButton, LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { setMargins(3.dp(), 7.dp(), 5.dp(), 0) })
        profileFileRow.addView(importProfileButton, LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { setMargins(5.dp(), 7.dp(), 3.dp(), 0) })
        exportProfileButton.setOnClickListener {
            val name = loadedProfileName
            val savedProfile = if (name.isNullOrBlank()) null else readColorProfiles().firstOrNull { it.name.equals(name, true) }
            if (savedProfile == null) {
                Toast.makeText(this, if (appLanguage == "id") "Muat atau simpan profil dulu sebelum ekspor" else "Load or save a profile before exporting", Toast.LENGTH_SHORT).show()
            } else {
                val currentProfile = savedProfile.copy(
                    colors = draftColors.take(5).toList(),
                    effect = barEffect,
                    widthDp = overlayWidthDp,
                    heightDp = overlayHeightDp,
                    barCount = barCount,
                    barWidthDp = barWidthDp,
                    gapDp = barGapDp,
                    scalePercent = scalePercent,
                    opacityPercent = opacityPercent,
                    glowPercent = glowPercent,
                    responsePercent = responsePercent,
                    cornerRadiusDp = cornerRadiusDp
                )
                exportColorProfile(currentProfile)
            }
        }
        importProfileButton.setOnClickListener { launchProfileImport() }
        panel.addView(profileFileRow)
        scroll.addView(panel)

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancelPalette = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        val applyPalette = glassButton(if (appLanguage == "id") "TERAPKAN PALET" else "APPLY PALETTE")
        footer.addView(cancelPalette, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(applyPalette, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(
            if (appLanguage == "id") "EDITOR PROFIL WARNA" else "SPECTRUM PROFILE EDITOR",
            scroll, footer, 0.98f, 0.94f
        )
        cancelPalette.setOnClickListener { dialog.dismiss() }
        applyPalette.setOnClickListener {
                val hex = hexInput.text.toString().trim()
                if (!usingPreset && hex.isNotEmpty()) {
                    val parsed = try {
                        Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
                    } catch (_: Exception) {
                        hexInput.error = if (appLanguage == "id") "Gunakan format #RRGGBB" else "Use #RRGGBB"
                        return@setOnClickListener
                    }
                    draftColors[selectedIndex] = parsed
                }
                spectrumColors.clear()
                spectrumColors.addAll(draftColors.take(5))
                acceptedPalette = true

                selectedProfile?.let { profile ->
                    scanDisplayBounds()
                    barEffect = profile.effect
                    overlayWidthDp = profile.widthDp.coerceIn(0, 4096)
                    overlayHeightDp = profile.heightDp.coerceIn(0, 4096)
                    barCount = profile.barCount.coerceIn(0, MAX_BAR_COUNT)
                    barWidthDp = profile.barWidthDp.coerceIn(0, 2048)
                    barGapDp = profile.gapDp.coerceIn(0, 512)
                    scalePercent = profile.scalePercent.coerceIn(0, 500)
                    opacityPercent = profile.opacityPercent.coerceIn(0, 100)
                    glowPercent = profile.glowPercent.coerceIn(0, 100)
                    responsePercent = profile.responsePercent.coerceIn(0, 100)
                    cornerRadiusDp = profile.cornerRadiusDp.coerceIn(0, 512)
                    clampPosition()
                }

                loadedProfileName?.let { profileName ->
                    saveColorProfile(profileName, spectrumColors)
                }

                saveSettings()
                refreshColorSwatches()
                updateEffectButtons()
                syncSliderValue("HORIZONTAL WIDTH", overlayWidthDp)
                syncSliderValue("BAR HEIGHT / LENGTH", overlayHeightDp)
                syncSliderValue("HORIZONTAL BAR COUNT", barCount)
                syncSliderValue("BAR THICKNESS", barWidthDp)
                syncSliderValue("BAR GAP", barGapDp)
                syncSliderValue("SCALE", scalePercent)
                syncSliderValue("OPACITY", opacityPercent)
                syncSliderValue("GLOW INTENSITY", glowPercent)
                syncSliderValue("RESPONSE SPEED", responsePercent)
                syncSliderValue("CORNER ROUNDNESS", cornerRadiusDp)
                buildSpectrumBars()
                updateFloatingService()
                dialog.dismiss()
        }
        dialog.setOnDismissListener {
            if (!acceptedPalette) {
                spectrumColors.clear()
                spectrumColors.addAll(originalSpectrumColors)
                refreshColorSwatches()
                saveSettings()
                updateFloatingService()
            }
        }
    }

    /** Central UI copy map for the first English/Indonesian release pair. */
    private fun translateUiText(value: String): String {
        val translations = mapOf(
            "BACKUP AUDIO CAPTURE" to "CADANGAN AUDIO",
            "Optional · MediaProjection, only when needed" to "Opsional · MediaProjection kalau dibutuhkan",
            "FLOATING BAR OVERLAY" to "FLOATING BAR DI ATAS APLIKASI",
            "Optional · show bar over other apps" to "Opsional · munculkan bar di atas aplikasi lain",
            "Optional · tampilkan bar di atas aplikasi lain" to "Opsional · munculkan bar di atas aplikasi lain",
            "APPEARANCE STUDIO" to "STUDIO TAMPILAN",
            "CUSTOM" to "KUSTOM",
            "SYSTEM" to "SISTEM",
            "LIGHT" to "TERANG",
            "Choose the interface atmosphere" to "Pilih nuansa tampilan aplikasi",
            "LIVE SPECTRUM" to "SPEKTRUM LANGSUNG",
            "VISUALIZER MODE" to "MODE VISUALIZER",
            "VERTICAL · SINGLE" to "VERTIKAL · TUNGGAL",
            "HORIZONTAL · DJ" to "HORIZONTAL · DJ",
            "SPECTRUM COLOR PROFILE" to "PROFIL WARNA SPEKTRUM",
            "Five independent color stops · save your look" to "Lima titik warna yang bisa diatur sendiri · simpan gayamu",
            "OPEN COLOR & PROFILE STUDIO" to "BUKA STUDIO WARNA & PROFIL",
            "✦  OPEN COLOR & PROFILE STUDIO" to "✦  BUKA STUDIO WARNA & PROFIL",
            "Reset bar appearance values" to "Atur ulang nilai tampilan bar",
            "FLOATING DJ · V2 • By Rifki" to "FLOATING DJ · V2 • Oleh Rifki",
            "BAR APPEARANCE" to "TAMPILAN BAR",
            "CLEAN" to "BERSIH",
            "FROSTED" to "KACA BURAM",
            "HORIZONTAL WIDTH" to "LEBAR HORIZONTAL",
            "BAR HEIGHT / LENGTH" to "TINGGI / PANJANG BAR",
            "HORIZONTAL BAR COUNT" to "JUMLAH BAR HORIZONTAL",
            "BAR THICKNESS" to "KETEBALAN BAR",
            "BAR GAP" to "JARAK ANTAR BAR",
            "SCALE" to "SKALA",
            "OPACITY" to "OPASITAS",
            "GLOW INTENSITY" to "INTENSITAS CAHAYA",
            "RESPONSE SPEED" to "KECEPATAN RESPONS",
            "CORNER ROUNDNESS" to "KELENGKUNGAN SUDUT",
            "SINGLE BAR ENGINE" to "MESIN BAR TUNGGAL",
            "Choose how the single meter reacts to sound" to "Pilih cara meter tunggal merespons suara",
            "DJ BASS" to "DJ BASS",
            "MUSIC FULL" to "MUSIK PENUH",
            "BASS START" to "MULAI BASS",
            "BASS END" to "AKHIR BASS",
            "SPECTRUM PROFILE EDITOR  ·  DRAG TO MOVE" to "EDITOR PROFIL SPEKTRUM  ·  GESER UNTUK PINDAH",
            "Edit each of the five color stops independently" to "Atur masing-masing dari lima titik warna",
            "COLOR PRESETS" to "PRESET WARNA",
            "SAVE AS PROFILE" to "SIMPAN SEBAGAI PROFIL",
            "SAVE CHANGES" to "SIMPAN PERUBAHAN",
            "MANAGE" to "KELOLA",
            "EXPORT PROFILE" to "EKSPOR PROFIL",
            "IMPORT PROFILE" to "IMPOR PROFIL",
            "APPLY PALETTE" to "TERAPKAN PALET",
            "APPLY RGB VALUES" to "TERAPKAN NILAI RGB",
            "APPLY HEX VALUE" to "TERAPKAN NILAI HEX",
            "SAVED COLOR PROFILES" to "PROFIL WARNA TERSIMPAN",
            "GENERAL SETTINGS" to "PENGATURAN UMUM",
            "Configure language, interface scale, and app information." to "Atur bahasa, ukuran tampilan, dan info aplikasi.",
            "LANGUAGE" to "BAHASA",
            "UI / TEXT SCALE" to "UKURAN UI / TEKS",
            "Safe range: 85–125%. Changes apply after reopening the app." to "Batas 85–125% biar tampilan tetap nyaman dibaca. Perubahan diterapkan setelah disimpan.",
            "ABOUT THIS APP" to "TENTANG APLIKASI",
            "CONFIGURATION BACKUP" to "CADANGAN PENGATURAN",
            "Export stores settings and all saved profiles. Import shows a review summary before applying changes." to "Ekspor menyimpan pengaturan dan semua profil. Impor akan menampilkan ringkasan sebelum diterapkan.",
            "COPY" to "SALIN",
            "EMAIL" to "EMAIL",
            "OPEN" to "BUKA",
            "EXPORT" to "EKSPOR",
            "IMPORT" to "IMPOR",
            "CLOSE" to "TUTUP",
            "SAVE" to "SIMPAN",
            "CANCEL" to "BATAL",
            "LOAD" to "MUAT",
            "RENAME" to "GANTI NAMA",
            "DELETE" to "HAPUS",
            "DONE" to "SELESAI",
            "● AUDIO LIVE" to "● AUDIO LANGSUNG",
            "● BACKUP LIVE" to "● CADANGAN LANGSUNG",
            "● AUDIO UNAVAILABLE" to "● AUDIO TIDAK TERSEDIA",
            "● BACKUP ERROR" to "● ERROR CADANGAN",
            "● AUDIO PAUSED" to "● AUDIO DIJEDA",
            "VISUALIZER · ACTIVE" to "VISUALIZER · AKTIF",
            "VISUALIZER · READY" to "VISUALIZER · SIAP",
            "VISUALIZER · UNAVAILABLE" to "VISUALIZER · TIDAK TERSEDIA",
            "MEDIA PROJECTION · ACTIVE" to "MEDIA PROJECTION · AKTIF",
            "ENGINE · PAUSED" to "MESIN · DIJEDA",
            "REAL-TIME AUDIO EXPERIENCE" to "PENGALAMAN AUDIO REAL-TIME",
            "＋ CUSTOM INTERFACE ACCENT" to "＋ AKSEN TAMPILAN KUSTOM",
            "✧  GLOSSY" to "✧  GLOSSY",
            "✧ GLOSSY" to "✧ GLOSSY",
            "BASS" to "BASS",
            "MID" to "MID",
            "TREBLE" to "TREBLE",
            "▶  START VISUALIZER" to "▶  MULAI VISUALIZER",
            "▶  RESUME VISUALIZER" to "▶  LANJUTKAN VISUALIZER",
            "■  STOP VISUALIZER" to "■  HENTIKAN VISUALIZER",
            "● READY" to "● SIAP",
            "● STARTING" to "● MEMULAI",
            "VISUALIZER · STARTING" to "VISUALIZER · MEMULAI",
            "BACKUP · STARTING" to "CADANGAN · MEMULAI",
            "Resume audio visualizer" to "Lanjutkan visualizer audio",
            "Stop audio visualizer" to "Hentikan visualizer audio",
            "Start audio visualizer" to "Mulai visualizer audio",
            "OPEN APP INFORMATION" to "BUKA INFO APLIKASI",
            "General settings" to "Pengaturan umum",
            "Floating position studio" to "Studio posisi floating",
            "Drag to move this color editor" to "Geser untuk memindahkan editor warna",
            "Drag to move this position editor" to "Geser untuk memindahkan editor posisi",
            "Developer information, email, and GitHub." to "Info pembuat, email, dan GitHub.",
            "BUILT BY RIFKI" to "DIBUAT OLEH RIFKI",
            "DRAG TO MOVE" to "GESER UNTUK PINDAH",
            "FLOATING POSITION STUDIO  ·  DRAG TO MOVE" to "STUDIO POSISI FLOATING  ·  GESER UNTUK PINDAH",
            "Position, drag mode and rotation" to "Posisi, mode geser, dan rotasi",
            "STEP DISTANCE (DP)" to "JARAK LANGKAH (DP)",
            "STEP DISTANCE (CM)" to "JARAK LANGKAH (CM)",
            "ROTATION · 0°–359°" to "ROTASI · 0°–359°",
            "POSITION MODE · STATIC" to "MODE POSISI · TETAP",
            "POSITION MODE · DYNAMIC" to "MODE POSISI · DINAMIS",
            "Enter 0–359" to "Masukkan 0–359",
            "Name cannot be empty" to "Nama tidak boleh kosong",
            "Profile name" to "Nama profil",
            "Enter a HEX value" to "Masukkan kode HEX",
            "Use the #RRGGBB format" to "Gunakan format #RRGGBB",
            "That color value is invalid" to "Kode warna itu belum valid",
            "Enter R, G, and B values from 0 to 255." to "Masukkan nilai R, G, dan B dari 0 sampai 255.",
            "Bounded to 85–125% for comfortable reading. Changes apply after saving." to "Dibatasi 85–125% biar tetap nyaman dibaca. Perubahan langsung diterapkan.",
            "Developer information, email, and GitHub." to "Info pembuat, email, dan GitHub.",
            "INTERFACE COLOR STUDIO  ·  DRAG TO MOVE" to "STUDIO AKSEN TAMPILAN  ·  GESER UNTUK PINDAH",
            "Pick an accent from the color wheel or enter exact color values." to "Pilih aksen dari roda warna atau masukkan kode warnanya."
        )
        if (appLanguage == "id") return translations[value] ?: value
        return translations.entries.firstOrNull { it.value == value }?.key ?: value
    }

    private fun localizeViewTree(root: View) {
        fun visit(view: View) {
            if (view is TextView) {
                view.text?.toString()?.takeIf { it.isNotEmpty() }?.let { view.text = translateUiText(it) }
                view.hint?.toString()?.takeIf { it.isNotEmpty() }?.let { view.hint = translateUiText(it) }
                view.contentDescription?.toString()?.takeIf { it.isNotEmpty() }?.let { view.contentDescription = translateUiText(it) }
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
    }

    /**
     * Builds one consistent modal: fixed glass header + close action, one scrolling body,
     * and a fixed in-panel footer. The whole surface is centered before optional dragging.
     */
    private fun showGlassStudioDialog(
        title: String,
        body: View,
        footer: View,
        widthFraction: Float = 0.96f,
        heightFraction: Float = 0.92f
    ): AlertDialog {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable(isLightTheme(), currentAccent())
            setPadding(11.dp(), 9.dp(), 11.dp(), 10.dp())
            clipToOutline = true
            clipChildren = true
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.dp(), 0, 1.dp(), 7.dp())
        }
        val titleView = sectionTitle(title).apply {
            textSize = 14f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            contentDescription = if (appLanguage == "id") "Judul jendela" else "Dialog title"
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, 44.dp(), 1f))
        val closeButton = glassButton("×").apply {
            textSize = 23f
            setPadding(0, 0, 0, 2.dp())
            contentDescription = if (appLanguage == "id") "Tutup" else "Close"
        }
        header.addView(closeButton, LinearLayout.LayoutParams(43.dp(), 40.dp()))
        shell.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val bodyFrame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        bodyFrame.addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        shell.addView(bodyFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        shell.addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 7.dp()
        })

        val dialog = AlertDialog.Builder(this).setView(shell).create()
        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            sizeStudioDialog(dialog, widthFraction, heightFraction)
            applyDialogTextScale(dialog)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    private fun showGlassConfirmDialog(
        title: String,
        message: String,
        cancelLabel: String,
        confirmLabel: String,
        widthFraction: Float = 0.90f,
        heightFraction: Float = 0.48f,
        onConfirm: () -> Unit
    ): AlertDialog {
        val scroll = ScrollView(this).apply { isFillViewport = true; background = null }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(9.dp(), 12.dp(), 9.dp(), 12.dp())
            addView(subtitle(message))
        }
        scroll.addView(body)
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = glassButton(cancelLabel)
        val confirm = glassButton(confirmLabel)
        footer.addView(cancel, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(confirm, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(title, scroll, footer, widthFraction, heightFraction)
        cancel.setOnClickListener { dialog.dismiss() }
        confirm.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        return dialog
    }

    /** Size the entire glossy dialog surface to the usable app window. */
    private fun sizeStudioDialog(dialog: AlertDialog, widthFraction: Float, heightFraction: Float) {
        val window = dialog.window ?: return
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val bounds: android.graphics.Rect
        val safeInsets: android.graphics.Insets?
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = windowManager.currentWindowMetrics
            bounds = android.graphics.Rect(metrics.bounds)
            safeInsets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
        } else {
            bounds = android.graphics.Rect()
            window.decorView.getWindowVisibleDisplayFrame(bounds)
            safeInsets = null
        }
        val safeWidth = (bounds.width() - (safeInsets?.left ?: 0) - (safeInsets?.right ?: 0) - 20.dp())
            .coerceAtLeast(1.dp())
        val safeHeight = (bounds.height() - (safeInsets?.top ?: 0) - (safeInsets?.bottom ?: 0) - 20.dp())
            .coerceAtLeast(1.dp())
        val minDialogWidth = 220.dp().coerceAtMost(safeWidth)
        val minDialogHeight = 190.dp().coerceAtMost(safeHeight)
        val width = (safeWidth * widthFraction.coerceIn(0.82f, 1f)).toInt().coerceIn(minDialogWidth, safeWidth)
        val height = (safeHeight * heightFraction.coerceIn(0.42f, 0.96f)).toInt().coerceIn(minDialogHeight, safeHeight)
        window.setLayout(width, height)
        window.setGravity(Gravity.CENTER)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val centeredAttributes = window.attributes
        centeredAttributes.dimAmount = 0.60f
        window.attributes = centeredAttributes
        window.decorView.post {
            localizeViewTree(window.decorView)
            applyUiTextScale(window.decorView)
        }
    }

    private fun applyUiTextScale(root: View) {
        val factor = uiScalePercent.coerceIn(85, 125) / 100f
        fun visit(view: View) {
            if (view is TextView) {
                val original = (view.getTag(R.id.tag_original_text_size) as? Float)
                    ?: view.textSize.also { view.setTag(R.id.tag_original_text_size, it) }
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, original * factor)
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
    }

    private fun applyDialogTextScale(dialog: AlertDialog) {
        dialog.window?.decorView?.let { decor ->
            decor.post {
                localizeViewTree(decor)
                applyUiTextScale(decor)
            }
        }
    }

    private fun showGeneralSettings() {
        val light = isLightTheme()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val panel = glassPanel().apply {
            orientation = LinearLayout.VERTICAL
            background = null
            setPadding(8.dp(), 7.dp(), 8.dp(), 8.dp())
        }
        val title = sectionTitle(if (appLanguage == "id") "PENGATURAN UMUM" else "GENERAL SETTINGS")
        panel.addView(title)
        panel.addView(subtitle(if (appLanguage == "id") "Atur bahasa, skala tampilan, dan info aplikasi." else "Configure language, interface scale, and app information."))

        panel.addView(sectionTitle(if (appLanguage == "id") "BAHASA / LANGUAGE" else "LANGUAGE"))
        val languageLabels = listOf("English", "Bahasa Indonesia")
        var selectedLanguageIndex = if (appLanguage == "id") 1 else 0
        val languageDropdown = glassButton(languageLabels[selectedLanguageIndex] + "    ⌄").apply {
            gravity = Gravity.CENTER_VERTICAL
            textSize = 14f
            setPadding(14.dp(), 0, 14.dp(), 0)
            background = glassEditDrawable(light)
            setTextColor(if (light) Color.parseColor("#23314A") else Color.WHITE)
            contentDescription = if (appLanguage == "id") "Pilih bahasa" else "Choose language"
        }
        languageDropdown.setOnClickListener {
            val choices = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(7.dp(), 7.dp(), 7.dp(), 7.dp())
                background = cardDrawable(light, currentAccent())
            }
            var languagePopup: android.widget.PopupWindow? = null
            languageLabels.forEachIndexed { index, label ->
                val item = TextView(this).apply {
                    text = (if (index == selectedLanguageIndex) "✓  " else "     ") + label
                    gravity = Gravity.CENTER_VERTICAL
                    textSize = 14f
                    setTextColor(if (light) Color.parseColor("#23314A") else Color.WHITE)
                    setPadding(13.dp(), 12.dp(), 13.dp(), 12.dp())
                    background = pillDrawable(index == selectedLanguageIndex, light, currentAccent())
                    setOnClickListener {
                        selectedLanguageIndex = index
                        languageDropdown.text = "$label    ⌄"
                        languagePopup?.dismiss()
                    }
                }
                choices.addView(item, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 46.dp()).apply {
                    bottomMargin = if (index == languageLabels.lastIndex) 0 else 5.dp()
                })
            }
            languagePopup = android.widget.PopupWindow(
                choices,
                languageDropdown.width.coerceAtLeast(180.dp()),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                setBackgroundDrawable(cardDrawable(light, currentAccent()))
                elevation = 18.dp().toFloat()
                isOutsideTouchable = true
                animationStyle = android.R.style.Animation_Dialog
            }
            languagePopup?.showAsDropDown(languageDropdown, 0, 5.dp())
        }
        panel.addView(languageDropdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 46.dp()))

        panel.addView(sectionTitle(if (appLanguage == "id") "UKURAN UI / TEKS" else "UI / TEXT SCALE"))
        val scaleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val scaleValue = TextView(this).apply {
            text = "$uiScalePercent%"
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setTextColor(if (light) Color.DKGRAY else Color.WHITE)
        }
        val scaleInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setText(uiScalePercent.toString())
            gravity = Gravity.CENTER
            setTextColor(if (light) Color.DKGRAY else Color.WHITE)
            setPadding(4.dp(), 3.dp(), 4.dp(), 3.dp())
            background = glassEditDrawable(light)
        }
        scaleRow.addView(scaleValue, LinearLayout.LayoutParams(0, 40.dp(), 1f))
        scaleRow.addView(scaleInput, LinearLayout.LayoutParams(72.dp(), 40.dp()).apply { marginStart = 10.dp() })
        panel.addView(scaleRow)
        val scaleSeek = SeekBar(this).apply {
            max = 40
            progress = uiScalePercent - 85
            progressTintList = ColorStateList.valueOf(currentAccent())
            thumbTintList = ColorStateList.valueOf(currentAccent())
        }
        scaleSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 85
                    scaleValue.text = "$value%"
                    if (fromUser && scaleInput.text.toString() != value.toString()) scaleInput.setText(value.toString())
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        scaleInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val value = scaleInput.text.toString().toIntOrNull()
                if (value == null) {
                    scaleInput.error = if (appLanguage == "id") "Isi angka 85–125" else "Enter 85–125"
                    true
                } else {
                    scaleSeek.progress = value.coerceIn(85, 125) - 85
                    scaleInput.clearFocus()
                    true
                }
            } else false
        }
        scaleInput.setOnFocusChangeListener { _, focused ->
            if (!focused) {
                val value = scaleInput.text.toString().toIntOrNull()
                if (value != null) scaleSeek.progress = value.coerceIn(85, 125) - 85
            }
        }
        panel.addView(scaleSeek, matchWrap())
        panel.addView(subtitle(if (appLanguage == "id") "Batas 85–125% biar tampilan tetap nyaman dibaca. Perubahan diterapkan setelah disimpan." else "Bounded to 85–125% for comfortable reading. Changes apply after saving."))

        panel.addView(sectionTitle(if (appLanguage == "id") "INFO APLIKASI" else "ABOUT THIS APP"))
        panel.addView(subtitle(if (appLanguage == "id") "Profil pembuat, email, dan GitHub." else "Developer information, email, and GitHub."))
        val aboutButton = glassButton(if (appLanguage == "id") "BUKA INFO APLIKASI" else "OPEN APP INFORMATION")
        aboutButton.setOnClickListener { showAboutDialog() }
        panel.addView(aboutButton, matchWrap())

        panel.addView(sectionTitle(if (appLanguage == "id") "CADANGAN KONFIGURASI" else "CONFIGURATION BACKUP"))
        panel.addView(subtitle(if (appLanguage == "id") "Ekspor menyimpan pengaturan dan seluruh profil. Impor akan menampilkan ringkasan sebelum diterapkan." else "Export stores settings and all saved profiles. Import shows a review summary before applying changes."))
        val backupRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val export = glassButton(if (appLanguage == "id") "EKSPOR" else "EXPORT")
        val import = glassButton(if (appLanguage == "id") "IMPOR" else "IMPORT")
        backupRow.addView(export, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        backupRow.addView(import, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        export.setOnClickListener { launchConfigExport() }
        import.setOnClickListener { launchConfigImport() }
        panel.addView(backupRow)
        scroll.addView(panel)

        scroll.background = null
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cancelSettings = glassButton(if (appLanguage == "id") "TUTUP" else "CLOSE")
        val saveSettingsButton = glassButton(if (appLanguage == "id") "SIMPAN" else "SAVE")
        footer.addView(cancelSettings, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(saveSettingsButton, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(
            if (appLanguage == "id") "PENGATURAN UMUM" else "GENERAL SETTINGS",
            scroll, footer, 0.96f, 0.92f
        )
        cancelSettings.setOnClickListener { dialog.dismiss() }
        saveSettingsButton.setOnClickListener {
            val newLanguage = if (selectedLanguageIndex == 1) "id" else "en"
            val changedLanguage = newLanguage != appLanguage
            val requestedScale = scaleInput.text.toString().toIntOrNull()
            if (requestedScale == null) {
                scaleInput.error = if (appLanguage == "id") "Isi angka 85–125" else "Enter 85–125"
                return@setOnClickListener
            }
            appLanguage = newLanguage
            uiScalePercent = requestedScale.coerceIn(85, 125)
            scaleSeek.progress = uiScalePercent - 85
            scaleInput.setText(uiScalePercent.toString())
            applyUiTextScale(contentRoot)
            saveSettings()
            dialog.dismiss()
            if (changedLanguage) recreate()
        }
    }

    private fun showAboutDialog() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val panel = glassPanel().apply {
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
        }
        panel.background = null
        panel.addView(sectionTitle(if (appLanguage == "id") "TENTANG APLIKASI" else "ABOUT THIS APP"))
        panel.addView(subtitle("Floating DJ Visualizer"))
        panel.addView(sectionTitle(if (appLanguage == "id") "DIBUAT OLEH RIFKI" else "BUILT BY RIFKI"))
        panel.addView(subtitle(if (appLanguage == "id") {
            "IT Networking Engineer. Aplikasi ini dibuat sebagai proyek eksplorasi audio visual dan eksperimen UI. Semoga seru dipakai!"
        } else {
            "IT Networking Engineer. Built as a personal audio-visual and interface experiment. Hope you enjoy using it."
        }))

        val emailRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        emailRow.addView(subtitle("✉  rifki.work74@outlook.com"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val copyEmail = glassButton(if (appLanguage == "id") "SALIN" else "COPY")
        copyEmail.setOnClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                ClipData.newPlainText("Email", "rifki.work74@outlook.com")
            )
            Toast.makeText(this, if (appLanguage == "id") "Email disalin" else "Email copied", Toast.LENGTH_SHORT).show()
        }
        val sendEmail = glassButton(if (appLanguage == "id") "EMAIL" else "EMAIL")
        sendEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:rifki.work74@outlook.com"))
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, if (appLanguage == "id") "Belum ada aplikasi email" else "No email app found", Toast.LENGTH_SHORT).show()
            }
        }
        emailRow.addView(copyEmail, LinearLayout.LayoutParams(70.dp(), 42.dp()).apply { marginStart = 5.dp() })
        emailRow.addView(sendEmail, LinearLayout.LayoutParams(70.dp(), 42.dp()).apply { marginStart = 5.dp() })
        panel.addView(emailRow, matchWrap().apply { topMargin = 15.dp() })

        val githubRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        githubRow.addView(subtitle("⌘  github.com/rifkiwork74"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val copyGithub = glassButton(if (appLanguage == "id") "SALIN" else "COPY")
        copyGithub.setOnClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                ClipData.newPlainText("GitHub", "https://github.com/rifkiwork74")
            )
            Toast.makeText(this, if (appLanguage == "id") "Link GitHub disalin" else "GitHub link copied", Toast.LENGTH_SHORT).show()
        }
        val openGithub = glassButton(if (appLanguage == "id") "BUKA" else "OPEN")
        openGithub.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rifkiwork74")))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, if (appLanguage == "id") "Browser tidak tersedia" else "No browser available", Toast.LENGTH_SHORT).show()
            }
        }
        githubRow.addView(copyGithub, LinearLayout.LayoutParams(70.dp(), 42.dp()).apply { marginStart = 5.dp() })
        githubRow.addView(openGithub, LinearLayout.LayoutParams(70.dp(), 42.dp()).apply { marginStart = 5.dp() })
        panel.addView(githubRow, matchWrap().apply { topMargin = 8.dp() })
        scroll.addView(panel)
        scroll.background = null
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val closeAbout = glassButton(if (appLanguage == "id") "TUTUP" else "CLOSE")
        footer.addView(closeAbout, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()))
        val dialog = showGlassStudioDialog(
            if (appLanguage == "id") "TENTANG APLIKASI" else "ABOUT THIS APP",
            scroll, footer, 0.94f, 0.78f
        )
        closeAbout.setOnClickListener { dialog.dismiss() }
    }

    private fun exportColorProfile(profile: ColorProfileEntry) {
        pendingProfileExport = profile
        val safeName = profile.name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(24).ifBlank { "profile" }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "FloatingDJ-$safeName.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT_PROFILE)
    }

    private fun launchProfileImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT_PROFILE)
    }

    private fun profileToJson(profile: ColorProfileEntry): JSONObject {
        val root = JSONObject().put("app", "FloatingDJVisualizerProfile")
            .put("schema", PROFILE_SCHEMA_VERSION).put("name", profile.name)
        val colors = JSONArray()
        profile.colors.take(5).forEach { colors.put(String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and it)) }
        root.put("colors", colors)
            .put("effect", profile.effect)
            .put("width", profile.widthDp).put("height", profile.heightDp)
            .put("bar_count", profile.barCount).put("bar_width", profile.barWidthDp)
            .put("bar_gap", profile.gapDp).put("scale", profile.scalePercent)
            .put("opacity", profile.opacityPercent).put("glow", profile.glowPercent)
            .put("response_speed", profile.responsePercent).put("corner_radius", profile.cornerRadiusDp)
        return root
    }

    private fun profileFromJson(raw: String): ColorProfileEntry {
        require(raw.length <= 128_000) { "Profile file is too large" }
        val root = JSONObject(raw)
        require(root.optString("app") == "FloatingDJVisualizerProfile") { "This is not a Floating DJ profile" }
        require(root.optInt("schema", -1) == PROFILE_SCHEMA_VERSION) { "Unsupported profile version" }
        val name = root.optString("name").trim().take(28)
        require(name.isNotBlank()) { "Profile name is missing" }
        val colorsJson = root.optJSONArray("colors") ?: throw IllegalArgumentException("Profile colors are missing")
        val colors = mutableListOf<Int>()
        for (i in 0 until colorsJson.length()) {
            val rawColor = colorsJson.optString(i)
            val color = try { Color.parseColor(rawColor) } catch (_: Exception) { null }
            if (color != null) colors.add(color)
        }
        require(colors.size == 5) { "A profile must contain five valid color stops" }
        val effect = root.optString("effect", EFFECT_GLOSSY)
            .takeIf { it in listOf(EFFECT_CLEAN, EFFECT_GLOSSY, EFFECT_NEON, EFFECT_GLASS) } ?: EFFECT_GLOSSY
        return ColorProfileEntry(
            name, colors, effect,
            root.optInt("width", 320).coerceIn(0, 4096),
            root.optInt("height", 360).coerceIn(0, 4096),
            root.optInt("bar_count", 15).coerceIn(0, MAX_BAR_COUNT),
            root.optInt("bar_width", 20).coerceIn(0, 2048),
            root.optInt("bar_gap", 3).coerceIn(0, 512),
            root.optInt("scale", 100).coerceIn(0, 500),
            root.optInt("opacity", 100).coerceIn(0, 100),
            root.optInt("glow", 65).coerceIn(0, 100),
            root.optInt("response_speed", 82).coerceIn(0, 100),
            root.optInt("corner_radius", 14).coerceIn(0, 512)
        )
    }

    private fun saveImportedColorProfile(profile: ColorProfileEntry) {
        val profiles = readColorProfiles()
        val index = profiles.indexOfFirst { it.name.equals(profile.name, ignoreCase = true) }
        if (index >= 0) profiles[index] = profile else profiles.add(profile)
        writeColorProfiles(profiles)
        prefs.edit().putString("last_managed_color_profile", profile.name).apply()
    }

    private fun reviewImportedColorProfile(uri: Uri) {
        try {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalArgumentException("Unable to read selected file")
            val profile = profileFromJson(raw)
            val existing = readColorProfiles().any { it.name.equals(profile.name, true) }
            val summary = buildString {
                if (appLanguage == "id") {
                    append("Profil: ").append(profile.name)
                    append("\nTitik warna: 5")
                    append("\nEfek: ").append(profile.effect)
                    append("\nUkuran: ").append(profile.widthDp).append(" × ").append(profile.heightDp).append(" dp")
                    append("\nJumlah bar: ").append(profile.barCount)
                    append("\nKetebalan / jarak: ").append(profile.barWidthDp).append(" / ").append(profile.gapDp).append(" dp")
                    append("\nSkala / opasitas: ").append(profile.scalePercent).append("% / ").append(profile.opacityPercent).append("%")
                    append("\nGlow / respons: ").append(profile.glowPercent).append("% / ").append(profile.responsePercent).append("%")
                    append("\nRadius sudut: ").append(profile.cornerRadiusDp).append(" dp")
                    if (existing) append("\n\nNama profil ini sudah ada. Versi lama akan diganti.")
                } else {
                    append("Profile: ").append(profile.name)
                    append("\nColor stops: 5")
                    append("\nEffect: ").append(profile.effect)
                    append("\nSize: ").append(profile.widthDp).append(" × ").append(profile.heightDp).append(" dp")
                    append("\nBars: ").append(profile.barCount)
                    append("\nThickness / gap: ").append(profile.barWidthDp).append(" / ").append(profile.gapDp).append(" dp")
                    append("\nScale / opacity: ").append(profile.scalePercent).append("% / ").append(profile.opacityPercent).append("%")
                    append("\nGlow / response: ").append(profile.glowPercent).append("% / ").append(profile.responsePercent).append("%")
                    append("\nCorner radius: ").append(profile.cornerRadiusDp).append(" dp")
                    if (existing) append("\n\nA profile with this name already exists and will be replaced.")
                }
            }
            val reviewScroll = ScrollView(this).apply { isFillViewport = true; background = null }
            val reviewBody = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8.dp(), 10.dp(), 8.dp(), 10.dp())
                addView(subtitle(summary))
            }
            reviewScroll.addView(reviewBody)
            val reviewFooter = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val cancelImport = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
            val confirmImport = glassButton(if (appLanguage == "id") "IMPOR PROFIL" else "IMPORT PROFILE")
            reviewFooter.addView(cancelImport, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
            reviewFooter.addView(confirmImport, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
            val reviewDialog = showGlassStudioDialog(
                if (appLanguage == "id") "TINJAU PROFIL" else "REVIEW PROFILE",
                reviewScroll, reviewFooter, 0.94f, 0.78f
            )
            cancelImport.setOnClickListener { reviewDialog.dismiss() }
            confirmImport.setOnClickListener {
                saveImportedColorProfile(profile)
                Toast.makeText(this, if (appLanguage == "id") "Profil berhasil diimpor" else "Profile imported", Toast.LENGTH_LONG).show()
                reviewDialog.dismiss()
            }
        } catch (error: Exception) {
            Toast.makeText(this, if (appLanguage == "id") "Impor profil gagal: ${error.message ?: "file tidak valid"}" else "Profile import failed: ${error.message ?: "invalid file"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchConfigExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "FloatingDJ-Backup.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT_CONFIG)
    }

    private fun launchConfigImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_IMPORT_CONFIG)
    }

    private fun createConfigBackup(): JSONObject {
        val root = JSONObject()
        root.put("app", "FloatingDJVisualizer")
        root.put("schema", CONFIG_SCHEMA_VERSION)
        root.put("exported_at", System.currentTimeMillis())
        val values = JSONObject()
        prefs.all.forEach { (key, value) ->
            when (value) {
                is Boolean, is Int, is Long, is Float, is String -> values.put(key, value)
            }
        }
        // Device grants and running-service state are deliberately informational, never replayed as grants.
        values.put("floating_overlay_enabled", floatingOverlayEnabled)
        values.put("backup_capture", backupCaptureEnabled)
        values.put("service_active", floatingServiceStarted)
        root.put("settings", values)
        return root
    }

    private fun applyConfigBackup(uri: Uri) {
        try {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Unable to read selected file")
            require(raw.length <= 1_500_000) { "Backup file is too large" }
            val root = JSONObject(raw)
            require(root.optString("app") == "FloatingDJVisualizer") { "This file is not a Floating DJ backup" }
            require(root.optInt("schema", -1) in 1..CONFIG_SCHEMA_VERSION) { "Unsupported backup version" }
            val values = root.getJSONObject("settings")
            val keys = values.keys()
            val imported = JSONObject()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key in setOf("floating_overlay_enabled", "backup_capture", "service_active", "service_paused")) continue
                imported.put(key, values.get(key))
            }
            val summary = buildString {
                val profilesIncluded = values.has("color_profiles")
                val floatingRequested = values.optBoolean("floating_overlay_enabled")
                val backupRequested = values.optBoolean("backup_capture")
                if (appLanguage == "id") {
                    append("Pengaturan dan profil warna: ").append(if (profilesIncluded) "ikut" else "tidak ada")
                    append("\nFloating Bar diminta aktif: ").append(if (floatingRequested) "Ya" else "Tidak")
                    append("\nBackup audio diminta aktif: ").append(if (backupRequested) "Ya" else "Tidak")
                    append("\nTema: ").append(values.optString("theme_mode", "default"))
                    append("\nEfek bar: ").append(values.optString("bar_effect", "default"))
                    append("\nUkuran: ").append(values.optInt("width", 320)).append(" × ").append(values.optInt("height", 360)).append(" dp")
                    append("\nJumlah / ketebalan / jarak bar: ").append(values.optInt("bar_count", 15)).append(" / ").append(values.optInt("bar_width", 20)).append(" / ").append(values.optInt("bar_gap", 3)).append(" dp")
                    append("\nSkala / opasitas / glow: ").append(values.optInt("scale", 100)).append("% / ").append(values.optInt("opacity", 100)).append("% / ").append(values.optInt("glow", 65)).append("%")
                    append("\nRespons: ").append(values.optInt("response_speed", 82)).append("%")
                    append("\nPosisi X/Y: ").append(values.optInt("position_x", 0)).append(" / ").append(values.optInt("position_y", 0)).append(" dp")
                    append("\nRotasi: ").append(values.optInt("rotation_degrees", 0)).append("°")
                    append("\n\nIzin Android tidak bisa ikut pindah dari file backup. Kamu tetap perlu menyetujui izin Floating Bar dan MediaProjection di HP ini. Status aktif dari perangkat asal hanya jadi informasi, bukan langsung dijalankan.")
                } else {
                    append("Settings and saved color profiles: ").append(if (profilesIncluded) "included" else "not included")
                    append("\nFloating Bar requested: ").append(if (floatingRequested) "On" else "Off")
                    append("\nBackup capture requested: ").append(if (backupRequested) "On" else "Off")
                    append("\nTheme: ").append(values.optString("theme_mode", "default"))
                    append("\nBar effect: ").append(values.optString("bar_effect", "default"))
                    append("\nSize: ").append(values.optInt("width", 320)).append(" × ").append(values.optInt("height", 360)).append(" dp")
                    append("\nBar count / thickness / gap: ").append(values.optInt("bar_count", 15)).append(" / ").append(values.optInt("bar_width", 20)).append(" / ").append(values.optInt("bar_gap", 3)).append(" dp")
                    append("\nScale / opacity / glow: ").append(values.optInt("scale", 100)).append("% / ").append(values.optInt("opacity", 100)).append("% / ").append(values.optInt("glow", 65)).append("%")
                    append("\nResponse: ").append(values.optInt("response_speed", 82)).append("%")
                    append("\nPosition X/Y: ").append(values.optInt("position_x", 0)).append(" / ").append(values.optInt("position_y", 0)).append(" dp")
                    append("\nRotation: ").append(values.optInt("rotation_degrees", 0)).append("°")
                    append("\n\nAndroid permissions are not transferred by a backup. You must approve overlay and MediaProjection on this device. The source device's active state is informational and will not be started automatically.")
                }
            }
            val reviewDialog = AlertDialog.Builder(this)
            .setTitle(if (appLanguage == "id") "Tinjau backup" else "Review backup")
            .setMessage(summary)
            .setNegativeButton(if (appLanguage == "id") "BATAL" else "CANCEL", null)
            .setPositiveButton(if (appLanguage == "id") "IMPOR" else "IMPORT") { _, _ ->
                val editor = prefs.edit()
                val importedKeys = imported.keys()
                while (importedKeys.hasNext()) {
                    val key = importedKeys.next()
                    when (val value = imported.get(key)) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                        is String -> editor.putString(key, value)
                    }
                }
                editor.putBoolean("service_active", false)
                .putBoolean("service_paused", false)
                .putBoolean("backup_capture", false)
                .putBoolean("floating_overlay_enabled", false)
                .apply()
                loadSettings()
                scanDisplayBounds()
                initializeOrClampPosition()
                applyTheme(animated = true)
                refreshColorSwatches()
                updateModeButtons()
                updateEffectButtons()
                buildSpectrumBars()
                updateFloatingService()
                val wantsOverlay = values.optBoolean("floating_overlay_enabled", false)
                if (wantsOverlay && !Settings.canDrawOverlays(this)) {
                    val overlayPermissionDialog = AlertDialog.Builder(this)
                        .setTitle(if (appLanguage == "id") "Izin floating diperlukan" else "Overlay permission required")
                        .setMessage(if (appLanguage == "id") "Backup meminta Floating Bar aktif. Android perlu persetujuan kamu sebelum aplikasi boleh tampil di atas aplikasi lain." else "The backup requests Floating Bar. Android requires your approval before this app can draw over other apps.")
                        .setNegativeButton(if (appLanguage == "id") "NANTI" else "LATER", null)
                        .setPositiveButton(if (appLanguage == "id") "BUKA PENGATURAN" else "OPEN SETTINGS") { _, _ -> enableFloatingOverlay() }
                        .create()
                    overlayPermissionDialog.show()
                    applyDialogTextScale(overlayPermissionDialog)
                } else if (wantsOverlay) {
                    floatingOverlaySwitch.isChecked = true
                }
                Toast.makeText(this, if (appLanguage == "id") "Backup berhasil diterapkan" else "Backup imported", Toast.LENGTH_LONG).show()
            }.create()
            reviewDialog.show()
            applyDialogTextScale(reviewDialog)
        } catch (error: Exception) {
            Toast.makeText(this, if (appLanguage == "id") "Impor backup gagal: ${error.message ?: "file tidak valid"}" else "Import failed: ${error.message ?: "invalid file"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun simpleSeekListener(onValue: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onValue(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun showThemeColorPicker(title: String, initialColor: Int, onApply: (Int) -> Unit) {
        val scroll = ScrollView(this).apply { isFillViewport = true; background = null }
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(7.dp(), 7.dp(), 7.dp(), 7.dp()) }
        val preview = View(this).apply {
            background = GradientDrawable().apply { setColor(initialColor); cornerRadius = 14.dp().toFloat() }
        }
        panel.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()))
        val wheel = ColorWheelView(this)
        panel.addView(wheel, LinearLayout.LayoutParams(214.dp(), 214.dp()).apply {
            gravity = Gravity.CENTER
            topMargin = 10.dp()
            bottomMargin = 8.dp()
        })
        val hex = EditText(this).apply {
            hint = "HEX · #RRGGBB"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setTextColor(if (isLightTheme()) Color.DKGRAY else Color.WHITE)
            setPadding(12.dp(), 9.dp(), 12.dp(), 9.dp())
            background = glassEditDrawable()
        }
        panel.addView(hex, matchWrap())
        var selected = initialColor
        fun showColor(color: Int, syncWheel: Boolean = true) {
            selected = color
            preview.background = GradientDrawable().apply { setColor(color); cornerRadius = 14.dp().toFloat() }
            hex.setText(String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and color))
            if (syncWheel) wheel.setColor(color, false)
        }
        wheel.onColorChanged = { showColor(it, false) }
        showColor(initialColor)
        scroll.addView(panel)
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        val apply = glassButton(if (appLanguage == "id") "TERAPKAN" else "APPLY")
        footer.addView(cancel, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(apply, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(title, scroll, footer, 0.92f, 0.80f)
        cancel.setOnClickListener { dialog.dismiss() }
        apply.setOnClickListener {
            val raw = hex.text.toString().trim()
            val parsed = try { Color.parseColor(if (raw.startsWith("#")) raw else "#$raw") }
            catch (_: Exception) {
                hex.error = if (appLanguage == "id") "Format HEX belum benar" else "Enter a valid HEX value"
                return@setOnClickListener
            }
            onApply(parsed)
            dialog.dismiss()
        }
    }

    private fun showAccentPicker() {
        val hsv = FloatArray(3)
        Color.colorToHSV(themeAccent, hsv)
        var selectedAccent = themeAccent

        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val panel = glassPanel().apply {
            setPadding(17.dp(), 16.dp(), 17.dp(), 15.dp())
        }
        panel.background = null
        panel.addView(subtitle(if (appLanguage == "id") "Atur warna aksen, latar panel, dan garis glossy." else "Customize the accent, panel surface, and glossy edge."))
        panel.addView(subtitle("Pick an accent from the color wheel or enter exact color values."))

        val wheel = ColorWheelView(this)
        panel.addView(
            wheel,
            LinearLayout.LayoutParams(210.dp(), 210.dp()).apply {
                gravity = Gravity.CENTER
                topMargin = 10.dp()
                bottomMargin = 8.dp()
            }
        )

        val preview = View(this)
        panel.addView(
            preview,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()).apply {
                topMargin = 4.dp()
            }
        )

        val hexInput = EditText(this).apply {
            hint = "HEX · #9B7BFF"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(if (isLightTheme()) Color.parseColor("#26344B") else Color.WHITE)
            setHintTextColor(if (isLightTheme()) Color.parseColor("#8693A8") else Color.parseColor("#8593A8"))
            setPadding(12.dp(), 9.dp(), 12.dp(), 9.dp())
            background = glassEditDrawable()
        }
        panel.addView(hexInput, matchWrap().apply { topMargin = 10.dp() })

        val rgbRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp(), 0, 0)
        }
        fun rgbField(label: String): EditText {
            return EditText(this).apply {
                hint = label
                inputType = InputType.TYPE_CLASS_NUMBER
                imeOptions = EditorInfo.IME_ACTION_DONE
                setSingleLine(true)
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (isLightTheme()) Color.parseColor("#26344B") else Color.WHITE)
                setHintTextColor(if (isLightTheme()) Color.parseColor("#8693A8") else Color.parseColor("#8593A8"))
                setPadding(4.dp(), 7.dp(), 4.dp(), 7.dp())
                background = glassEditDrawable()
            }
        }
        val redInput = rgbField("R")
        val greenInput = rgbField("G")
        val blueInput = rgbField("B")
        listOf(redInput, greenInput, blueInput).forEachIndexed { index, field ->
            val label = listOf("R", "G", "B")[index]
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(subtitle(label))
                addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40.dp()))
            }
            rgbRow.addView(
                column,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(3.dp(), 0, 3.dp(), 0)
                }
            )
        }
        panel.addView(rgbRow)

        val hueLabel = subtitle("HUE  ${hsv[0].toInt()}°")
        val saturationLabel = subtitle("SATURATION  ${(hsv[1] * 100).toInt()}%")
        val brightnessLabel = subtitle("BRIGHTNESS  ${(hsv[2] * 100).toInt()}%")
        fun colorSeek(label: TextView, maximum: Int, progress: Int): SeekBar {
            panel.addView(label.apply { setPadding(0, 9.dp(), 0, 0) })
            return SeekBar(this).apply {
                max = maximum
                this.progress = progress.coerceIn(0, maximum)
                progressTintList = ColorStateList.valueOf(currentAccent())
                thumbTintList = ColorStateList.valueOf(currentAccent())
                panel.addView(this)
            }
        }
        val hueSeek = colorSeek(hueLabel, 360, hsv[0].toInt())
        val saturationSeek = colorSeek(saturationLabel, 100, (hsv[1] * 100).toInt())
        val brightnessSeek = colorSeek(brightnessLabel, 100, (hsv[2] * 100).toInt())

        fun refreshColorFields(color: Int, syncWheel: Boolean = true) {
            selectedAccent = color
            Color.colorToHSV(color, hsv)
            if (syncWheel) wheel.setColor(color, notify = false)
            hueSeek.progress = hsv[0].toInt().coerceIn(0, 360)
            saturationSeek.progress = (hsv[1] * 100f).toInt().coerceIn(0, 100)
            brightnessSeek.progress = (hsv[2] * 100f).toInt().coerceIn(0, 100)
            hueLabel.text = "HUE  ${hsv[0].toInt()}°"
            saturationLabel.text = "SATURATION  ${(hsv[1] * 100).toInt()}%"
            brightnessLabel.text = "BRIGHTNESS  ${(hsv[2] * 100).toInt()}%"
            preview.background = accentDrawable(color)
            hexInput.setText(String.format(java.util.Locale.US, "#%06X", 0xFFFFFF and color))
            redInput.setText(Color.red(color).toString())
            greenInput.setText(Color.green(color).toString())
            blueInput.setText(Color.blue(color).toString())
        }

        wheel.onColorChanged = { color -> refreshColorFields(color, syncWheel = false) }
        hueSeek.setOnSeekBarChangeListener(simpleSeekListener {
                hsv[0] = it.toFloat()
                refreshColorFields(Color.HSVToColor(hsv))
        })
        saturationSeek.setOnSeekBarChangeListener(simpleSeekListener {
                hsv[1] = it / 100f
                refreshColorFields(Color.HSVToColor(hsv))
        })
        brightnessSeek.setOnSeekBarChangeListener(simpleSeekListener {
                hsv[2] = it / 100f
                refreshColorFields(Color.HSVToColor(hsv))
        })

        val hexApply = glassButton("USE HEX VALUE")
        hexApply.setOnClickListener {
            val text = hexInput.text.toString().trim()
            val parsed = try {
                Color.parseColor(if (text.startsWith("#")) text else "#$text")
            } catch (_: Exception) {
                hexInput.error = "Use #RRGGBB"
                return@setOnClickListener
            }
            refreshColorFields(parsed)
        }
        panel.addView(hexApply, matchWrap().apply { topMargin = 7.dp() })

        val rgbApply = glassButton("APPLY RGB VALUES")
        rgbApply.setOnClickListener {
            val red = redInput.text.toString().toIntOrNull()
            val green = greenInput.text.toString().toIntOrNull()
            val blue = blueInput.text.toString().toIntOrNull()
            if (red == null || green == null || blue == null) {
                Toast.makeText(this, "Isi nilai R, G, dan B dari 0 sampai 255", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            refreshColorFields(
                Color.rgb(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
            )
        }
        panel.addView(rgbApply, matchWrap().apply { topMargin = 6.dp() })

        panel.addView(sectionTitle(if (appLanguage == "id") "WARNA PANEL & GARIS GLOSSY" else "PANEL & GLOSSY EDGE COLORS").apply {
            setPadding(0, 16.dp(), 0, 6.dp())
        })
        val customSurfaceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val surfaceButton = glassButton(if (appLanguage == "id") "PILIH LATAR PANEL" else "PICK PANEL BACKGROUND")
        val edgeButton = glassButton(if (appLanguage == "id") "PILIH GARIS GLOSSY" else "PICK GLOSSY EDGE")
        customSurfaceRow.addView(surfaceButton, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        customSurfaceRow.addView(edgeButton, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        panel.addView(customSurfaceRow)
        var surfacePreview: View? = null
        var edgePreview: View? = null
        surfaceButton.setOnClickListener {
            showThemeColorPicker(if (appLanguage == "id") "WARNA LATAR PANEL" else "PANEL BACKGROUND", themeSurfaceColor) { color ->
                themeSurfaceColor = color
                surfacePreview?.background = GradientDrawable().apply { setColor(color); cornerRadius = 12.dp().toFloat() }
                saveSettings()
                applyTheme(animated = true)
            }
        }
        edgeButton.setOnClickListener {
            showThemeColorPicker(if (appLanguage == "id") "WARNA GARIS GLOSSY" else "GLOSSY EDGE COLOR", themeEdgeColor) { color ->
                themeEdgeColor = color
                edgePreview?.background = GradientDrawable().apply { setColor(color); cornerRadius = 12.dp().toFloat() }
                saveSettings()
                applyTheme(animated = true)
            }
        }
        val colorPreviewRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val surfacePreviewView = View(this).apply { background = GradientDrawable().apply { setColor(themeSurfaceColor); cornerRadius = 12.dp().toFloat() } }
        val edgePreviewView = View(this).apply { background = GradientDrawable().apply { setColor(themeEdgeColor); cornerRadius = 12.dp().toFloat() } }
        surfacePreview = surfacePreviewView
        edgePreview = edgePreviewView
        colorPreviewRow.addView(surfacePreviewView, LinearLayout.LayoutParams(0, 24.dp(), 1f).apply { marginEnd = 5.dp() })
        colorPreviewRow.addView(edgePreviewView, LinearLayout.LayoutParams(0, 24.dp(), 1f).apply { marginStart = 5.dp() })
        panel.addView(colorPreviewRow, matchWrap().apply { topMargin = 7.dp() })

        refreshColorFields(themeAccent)
        scroll.background = null
        scroll.addView(panel)

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancelTheme = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        val applyThemeButton = glassButton(if (appLanguage == "id") "TERAPKAN TEMA" else "APPLY THEME")
        footer.addView(cancelTheme, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(applyThemeButton, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(
            if (appLanguage == "id") "STUDIO WARNA TAMPILAN" else "INTERFACE COLOR STUDIO",
            scroll, footer, 0.96f, 0.92f
        )
        cancelTheme.setOnClickListener { dialog.dismiss() }
        applyThemeButton.setOnClickListener {
            themeAccent = selectedAccent
            themeMode = THEME_CUSTOM
            saveSettings()
            applyTheme(animated = true)
            updateModeButtons()
            updateEffectButtons()
            updateSingleModeButtons()
            dialog.dismiss()
        }
    }

    /**
     * Single-knob HSV disk.
     * Angle controls hue, radius controls saturation, brightness remains on its own slider.
     * This avoids the old dual-target ring + inner SV globe that was difficult to track.
     */
    private inner class ColorWheelView(context: Context) : View(context) {
        private val wheelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var hue = 275f
        private var saturation = 0.72f
        private var brightness = 0.92f
        var onColorChanged: ((Int) -> Unit)? = null

        init {
            isClickable = true
            isFocusable = true
        }

        fun setColor(color: Int, notify: Boolean = false) {
            val values = FloatArray(3)
            Color.colorToHSV(color, values)
            hue = values[0]
            saturation = values[1]
            brightness = values[2]
            invalidate()
            if (notify) onColorChanged?.invoke(Color.HSVToColor(values))
        }

        private fun wheelRadius(): Float {
            return (minOf(width, height) / 2f - 16.dp()).coerceAtLeast(18.dp().toFloat())
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return
            val cx = width / 2f
            val cy = height / 2f
            val radius = wheelRadius()
            // Standard HSV hue order: red → yellow → green → cyan → blue → magenta → red.
            val colors = intArrayOf(
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED
            )

            // Full hue disk.
            wheelPaint.style = android.graphics.Paint.Style.FILL
            wheelPaint.shader = android.graphics.SweepGradient(cx, cy, colors, null)
            canvas.save()
            canvas.rotate(-90f, cx, cy)
            canvas.drawCircle(cx, cy, radius, wheelPaint)
            canvas.restore()
            wheelPaint.shader = null

            // White center fading outward maps radial distance to saturation.
            wheelPaint.shader = android.graphics.RadialGradient(
                cx,
                cy,
                radius,
                intArrayOf(Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, radius, wheelPaint)
            wheelPaint.shader = null

            // Brightness is adjusted by the separate BRIGHTNESS slider.
            val dimAlpha = ((1f - brightness.coerceIn(0f, 1f)) * 190f).toInt()
            if (dimAlpha > 0) {
                wheelPaint.color = Color.argb(dimAlpha, 0, 0, 0)
                canvas.drawCircle(cx, cy, radius, wheelPaint)
            }

            wheelPaint.style = android.graphics.Paint.Style.STROKE
            wheelPaint.strokeWidth = 1.dp().toFloat()
            wheelPaint.color = if (isLightTheme()) 0x668090A8 else 0x99FFFFFF.toInt()
            canvas.drawCircle(cx, cy, radius, wheelPaint)

            // One visible, draggable knob only.
            val angle = Math.toRadians((hue - 90f).toDouble())
            val knobRadius = saturation.coerceIn(0f, 1f) * radius
            val markerX = cx + cos(angle).toFloat() * knobRadius
            val markerY = cy + sin(angle).toFloat() * knobRadius
            wheelPaint.style = android.graphics.Paint.Style.FILL
            wheelPaint.color = Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
            canvas.drawCircle(markerX, markerY, 10.dp().toFloat(), wheelPaint)
            wheelPaint.style = android.graphics.Paint.Style.STROKE
            wheelPaint.strokeWidth = 2.dp().toFloat()
            wheelPaint.color = Color.WHITE
            canvas.drawCircle(markerX, markerY, 10.dp().toFloat(), wheelPaint)
            wheelPaint.style = android.graphics.Paint.Style.FILL
        }

        private fun updateFromTouch(x: Float, y: Float) {
            val cx = width / 2f
            val cy = height / 2f
            val dx = x - cx
            val dy = y - cy
            val radius = wheelRadius()
            val distance = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
            hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0 + 360.0) % 360.0).toFloat()
            saturation = (distance / radius).coerceIn(0f, 1f)
            invalidate()
            onColorChanged?.invoke(Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateFromTouch(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    updateFromTouch(event.x, event.y)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }
    }


    // -----------------------------------------------------------------
    // POSITION STUDIO
    // -----------------------------------------------------------------

    private fun confirmResetBarAppearance(trigger: View) {
        showGlassConfirmDialog(
            if (appLanguage == "id") "ATUR ULANG TAMPILAN BAR?" else "RESET BAR APPEARANCE?",
            if (appLanguage == "id") {
                "Hanya nilai tampilan bar yang dikembalikan ke awal. Tema, warna, profil, posisi, dan pengaturan audio tetap aman."
            } else {
                "Only bar appearance values will return to their defaults. Your theme, colors, profiles, position, and audio settings will stay as they are."
            },
            if (appLanguage == "id") "BATAL" else "CANCEL",
            if (appLanguage == "id") "ATUR ULANG" else "RESET",
            0.92f, 0.50f
        ) {
            resetBarAppearanceValues()
            trigger.animate().rotationBy(360f).setDuration(420L).withEndAction { trigger.rotation = 0f }.start()
        }
    }

    private fun resetBarAppearanceValues() {
        scanDisplayBounds()
        overlayWidthDp = minOf(320, (screenWidthDp * 0.94f).toInt().coerceAtLeast(120))
        overlayHeightDp = minOf(360, (screenHeightDp * 0.90f).toInt().coerceAtLeast(80))
        barCount = 15
        barWidthDp = 20
        barGapDp = 3
        scalePercent = 100
        opacityPercent = 100
        glowPercent = 65
        responsePercent = 82
        cornerRadiusDp = 14
        barEffect = EFFECT_GLOSSY
        clampPosition()
        updateEffectButtons()
        syncSliderValue("HORIZONTAL WIDTH", overlayWidthDp)
        syncSliderValue("BAR HEIGHT / LENGTH", overlayHeightDp)
        syncSliderValue("HORIZONTAL BAR COUNT", barCount)
        syncSliderValue("BAR THICKNESS", barWidthDp)
        syncSliderValue("BAR GAP", barGapDp)
        syncSliderValue("SCALE", scalePercent)
        syncSliderValue("OPACITY", opacityPercent)
        syncSliderValue("GLOW INTENSITY", glowPercent)
        syncSliderValue("RESPONSE SPEED", responsePercent)
        syncSliderValue("CORNER ROUNDNESS", cornerRadiusDp)
        buildSpectrumBars()
        saveSettings()
        updateFloatingService()
    }

    private fun showPositionSettings() {
        scanDisplayBounds()
        positionXdp = prefs.getInt("position_x", positionXdp).coerceAtLeast(0)
        positionYdp = prefs.getInt("position_y", positionYdp).coerceAtLeast(0)

        val panel = glassPanel().apply {
            setPadding(17.dp(), 17.dp(), 17.dp(), 17.dp())
        }
        panel.background = null
        panel.addView(subtitle(if (appLanguage == "id") "Atur posisi dan sudut floating bar." else "Adjust the floating bar position and angle."))
        panel.addView(subtitle("Position, drag mode and rotation"))

        val mode = glassButton("POSITION MODE · $positionMode")
        mode.setOnClickListener {
            positionMode = if (positionMode == "STATIC") "DYNAMIC" else "STATIC"
            mode.text = "POSITION MODE · $positionMode"
            saveSettings()
            updateFloatingService()
        }
        panel.addView(mode, matchWrap().apply { topMargin = 11.dp() })

        val positionLabel = subtitle(positionText()).apply {
            gravity = Gravity.CENTER
            setPadding(0, 15.dp(), 0, 10.dp())
            textSize = 12f
        }
        panel.addView(positionLabel)

        val unitRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val dpButton = glassButton("DP")
        val cmButton = glassButton("CM")
        unitRow.addView(
            dpButton,
            LinearLayout.LayoutParams(0, 42.dp(), 1f).apply {
                setMargins(3.dp(), 0, 3.dp(), 0)
            }
        )
        unitRow.addView(
            cmButton,
            LinearLayout.LayoutParams(0, 42.dp(), 1f).apply {
                setMargins(3.dp(), 0, 3.dp(), 0)
            }
        )
        panel.addView(unitRow)

        val stepTitle = subtitle("STEP DISTANCE ($positionUnit)").apply {
            setPadding(0, 10.dp(), 0, 3.dp())
        }
        val stepInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setText(
                if (positionUnit == "DP") {
                    positionStepDp.toInt().toString()
                } else {
                    String.format(java.util.Locale.US, "%.2f", dpToCm(positionStepDp))
                }
            )
            hint = "Movement step"
            setTextColor(if (isLightTheme()) Color.parseColor("#26344B") else Color.WHITE)
            setHintTextColor(if (isLightTheme()) Color.parseColor("#8693A8") else Color.parseColor("#8593A8"))
            setPadding(11.dp(), 9.dp(), 11.dp(), 9.dp())
            background = glassEditDrawable()
        }
        panel.addView(stepTitle)
        panel.addView(stepInput, matchWrap())

        fun refreshUnitButtons() {
            dpButton.background = pillDrawable(positionUnit == "DP", isLightTheme(), currentAccent())
            cmButton.background = pillDrawable(positionUnit == "CM", isLightTheme(), currentAccent())
            dpButton.setTextColor(if (positionUnit == "DP" || !isLightTheme()) Color.WHITE else Color.DKGRAY)
            cmButton.setTextColor(if (positionUnit == "CM" || !isLightTheme()) Color.WHITE else Color.DKGRAY)
            stepTitle.text = "STEP DISTANCE ($positionUnit)"
        }

        fun refreshStepInput() {
            stepInput.setText(
                if (positionUnit == "DP") {
                    positionStepDp.toInt().toString()
                } else {
                    String.format(java.util.Locale.US, "%.2f", dpToCm(positionStepDp))
                }
            )
        }

        dpButton.setOnClickListener {
            positionUnit = "DP"
            refreshStepInput()
            refreshUnitButtons()
            saveSettings()
        }
        cmButton.setOnClickListener {
            positionUnit = "CM"
            refreshStepInput()
            refreshUnitButtons()
            saveSettings()
        }
        refreshUnitButtons()

        fun readStep() {
            val raw = stepInput.text.toString().trim().replace(',', '.')
            val value = raw.toFloatOrNull() ?: return
            if (value > 0f) {
                positionStepDp = if (positionUnit == "DP") {
                    value.coerceIn(1f, 1000f)
                } else {
                    cmToDp(value).coerceIn(1f, 1000f)
                }
                prefs.edit().putFloat("position_step", positionStepDp).apply()
            }
        }

        fun move(dx: Int, dy: Int) {
            readStep()
            val step = positionStepDp.toInt().coerceAtLeast(1)
            positionXdp += dx * step
            positionYdp += dy * step
            clampPosition()
            saveSettings()
            updateFloatingService()
            positionLabel.text = positionText()
        }

        // D-pad supports both taps and press-and-hold for continuous movement.
        fun holdButton(symbol: String, dx: Int, dy: Int): TextView {
            val button = positionButton(symbol) {}
            val repeatTask = object : Runnable {
                override fun run() {
                    move(dx, dy)
                    if (button.isPressed) {
                        updateHandler.postDelayed(this, 90L)
                    }
                }
            }
            button.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        move(dx, dy)
                        updateHandler.postDelayed(repeatTask, 360L)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        updateHandler.removeCallbacks(repeatTask)
                        view.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        updateHandler.removeCallbacks(repeatTask)
                        true
                    }
                    else -> true
                }
            }
            return button
        }

        val up = LinearLayout(this).apply { gravity = Gravity.CENTER }
        up.addView(
            holdButton("↑", 0, -1),
            LinearLayout.LayoutParams(62.dp(), 46.dp())
        )
        panel.addView(up)

        val middle = LinearLayout(this).apply { gravity = Gravity.CENTER }
        middle.addView(
            holdButton("←", -1, 0),
            LinearLayout.LayoutParams(62.dp(), 46.dp())
        )
        middle.addView(
            positionButton("CENTER") {
                val (boundsWidth, boundsHeight) = rotatedBoundsDp()
                positionXdp = ((screenWidthDp - boundsWidth) / 2).coerceAtLeast(0)
                positionYdp = ((screenHeightDp - boundsHeight) / 2).coerceAtLeast(0)
                saveSettings()
                updateFloatingService()
                positionLabel.text = positionText()
            },
            LinearLayout.LayoutParams(88.dp(), 46.dp()).apply {
                setMargins(5.dp(), 0, 5.dp(), 0)
            }
        )
        middle.addView(
            holdButton("→", 1, 0),
            LinearLayout.LayoutParams(62.dp(), 46.dp())
        )
        panel.addView(middle)

        val down = LinearLayout(this).apply { gravity = Gravity.CENTER }
        down.addView(
            holdButton("↓", 0, 1),
            LinearLayout.LayoutParams(62.dp(), 46.dp())
        )
        panel.addView(down)

        panel.addView(subtitle("ROTATION · 0°–359°").apply {
                setPadding(0, 16.dp(), 0, 3.dp())
        })
        val rotationLabel = subtitle("$rotationDegrees°")
        val rotationSeek = SeekBar(this).apply {
            max = 359
            progress = rotationDegrees
            progressTintList = ColorStateList.valueOf(currentAccent())
            thumbTintList = ColorStateList.valueOf(currentAccent())
        }
        rotationSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (!fromUser) return
                    rotationDegrees = progress.coerceIn(0, 359)
                    rotationLabel.text = "$rotationDegrees°"
                    // The service preserves the overlay center while only its bounds rotate.
                    scheduleRealtimeUpdate(65L)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    saveSettings()
                    updateFloatingService()
                    positionLabel.text = positionText()
                }
        })
        panel.addView(rotationLabel)
        panel.addView(rotationSeek)

        val rotationRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp(), 0, 0)
        }
        val rotationInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setText(rotationDegrees.toString())
            hint = "Angle 0–359"
            setTextColor(if (isLightTheme()) Color.parseColor("#26344B") else Color.WHITE)
            setHintTextColor(if (isLightTheme()) Color.parseColor("#8693A8") else Color.parseColor("#8593A8"))
            setPadding(10.dp(), 8.dp(), 10.dp(), 8.dp())
            background = glassEditDrawable()
        }
        val applyRotation = glassButton("APPLY ANGLE")
        rotationRow.addView(
            rotationInput,
            LinearLayout.LayoutParams(0, 42.dp(), 1f)
        )
        rotationRow.addView(
            applyRotation,
            LinearLayout.LayoutParams(112.dp(), 42.dp()).apply {
                setMargins(8.dp(), 0, 0, 0)
            }
        )
        panel.addView(rotationRow)

        fun applyRotationValue() {
            val value = rotationInput.text.toString().trim().toIntOrNull()
            if (value == null) {
                rotationInput.error = "Enter 0–359"
                return
            }
            rotationDegrees = value.coerceIn(0, 359)
            rotationInput.setText(rotationDegrees.toString())
            rotationSeek.progress = rotationDegrees
            rotationLabel.text = "$rotationDegrees°"
            saveSettings()
            updateFloatingService()
            positionLabel.text = positionText()
        }
        applyRotation.setOnClickListener { applyRotationValue() }
        rotationInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyRotationValue()
                rotationInput.clearFocus()
                true
            } else {
                false
            }
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            background = null
            addView(panel)
        }
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val doneButton = glassButton(if (appLanguage == "id") "SELESAI" else "DONE")
        footer.addView(doneButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()))
        val dialog = showGlassStudioDialog(
            if (appLanguage == "id") "STUDIO POSISI FLOATING" else "FLOATING POSITION STUDIO",
            scroll, footer, 0.96f, 0.92f
        )
        doneButton.setOnClickListener { dialog.dismiss() }
    }

    private fun positionButton(label: String, action: () -> Unit): TextView {
        return glassButton(label).apply { textSize = 16f; setOnClickListener { action() } }
    }

    private fun positionText(): String {
        return if (positionUnit == "DP") "X $positionXdp dp    Y $positionYdp dp"
        else "X ${"%.1f".format(dpToCm(positionXdp.toFloat()))} cm    Y ${"%.1f".format(dpToCm(positionYdp.toFloat()))} cm"
    }

    private fun dpToCm(value: Float): Float {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val xdpi = resources.displayMetrics.xdpi.takeIf { it > 0f } ?: 160f
        return value * density / xdpi * 2.54f
    }

    private fun cmToDp(value: Float): Float {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val xdpi = resources.displayMetrics.xdpi.takeIf { it > 0f } ?: 160f
        return value * xdpi / 2.54f / density
    }

    // -----------------------------------------------------------------
    // GLASS VIEW HELPERS
    // -----------------------------------------------------------------

    private fun glassDrawable(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#E629344A"), Color.parseColor("#E6141B2A"))
    ).apply { cornerRadius = 18.dp().toFloat(); setStroke(1.dp(), 0x667D91B8) }

    private fun glassEditDrawable(light: Boolean = isLightTheme()): GradientDrawable {
        val colors = if (light) {
            intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#EAF0F8"))
        } else {
            intArrayOf(Color.parseColor("#26374F"), Color.parseColor("#152237"))
        }
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = 14.dp().toFloat()
            setStroke(1.dp(), if (light) 0x667D8DA8 else 0x777D91B8)
        }
    }

    private fun glassPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cardDrawable(isLightTheme(), currentAccent())
    }

    private fun glassButton(label: String): TextView = TextView(this).apply {
        text = translateUiText(label)
        gravity = Gravity.CENTER
        textSize = 11f
        setTextColor(if (isLightTheme()) Color.DKGRAY else Color.WHITE)
        setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
        background = pillDrawable(false, isLightTheme(), currentAccent())
        isClickable = true
        isFocusable = true
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = translateUiText(value)
        textSize = 13f
        setTextColor(if (isLightTheme()) Color.parseColor("#1B2940") else Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun subtitle(value: String): TextView = TextView(this).apply {
        text = translateUiText(value)
        textSize = 10f
        setTextColor(if (isLightTheme()) Color.parseColor("#66758C") else Color.parseColor("#9AA8BE"))
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    // -----------------------------------------------------------------
    // PERMISSIONS + DUAL AUDIO ENGINE
    // -----------------------------------------------------------------

    private fun toggleStartStop() {
        when {
            servicePaused -> startPrimaryVisualizer()
            floatingServiceStarted -> stopFloating()
            else -> startFloatingFromButton()
        }
    }

    private fun updateStartStopButton() {
        val button = findViewById<TextView>(R.id.btnStart) ?: return
        when {
            servicePaused -> {
                button.text = translateUiText("▶  RESUME VISUALIZER")
                button.background = accentDrawable(currentAccent())
                button.contentDescription = "Resume audio visualizer"
            }
            floatingServiceStarted -> {
                button.text = translateUiText("■  STOP VISUALIZER")
                button.background = dangerDrawable()
                button.contentDescription = "Stop audio visualizer"
            }
            else -> {
                button.text = translateUiText("▶  START VISUALIZER")
                button.background = accentDrawable(currentAccent())
                button.contentDescription = "Start audio visualizer"
            }
        }
    }

    private fun startFloatingFromButton() {
        // Audio engine and live in-app spectrum start independently from overlay permission.
        requestPrimaryAudioPermission()
    }

    private fun requestPrimaryAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else startPrimaryVisualizer()
    }

    private fun startPrimaryVisualizer() {
        val serviceIntent = Intent(this, FloatingVisualizerService::class.java).apply {
            action = FloatingVisualizerService.ACTION_START_PRIMARY
            addFloatingSettings(this)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            floatingServiceStarted = true
            backupCaptureEnabled = false
            backupSwitch.setOnCheckedChangeListener(null)
            backupSwitch.isChecked = false
            backupSwitch.setOnCheckedChangeListener { _, checked -> if (checked) enableBackupCapture() else disableBackupCapture() }
            prefs.edit().putBoolean("service_active", true).putBoolean("backup_capture", false).apply()
            servicePaused = false
            prefs.edit().putBoolean("service_paused", false).apply()
            engineStatus.text = translateUiText("● STARTING")
            audioSourceStatus.text = translateUiText("VISUALIZER · STARTING")
            updateStartStopButton()
        } catch (e: Exception) {
            floatingServiceStarted = false
            prefs.edit().putBoolean("service_active", false).apply()
            Toast.makeText(this, "Tidak bisa memulai audio: ${e.message}", Toast.LENGTH_LONG).show()
            updateStartStopButton()
        }
    }

    private fun enableFloatingOverlay() {
        if (Settings.canDrawOverlays(this)) {
            floatingOverlayEnabled = true
            prefs.edit().putBoolean("floating_overlay_enabled", true).apply()
            updateFloatingService()
            Toast.makeText(
                this,
                if (floatingServiceStarted) "Floating bar aktif" else "Floating bar siap muncul setelah audio dimulai",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        pendingFloatingOverlayEnable = true
        setFloatingOverlaySwitch(false)
        val permissionIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(permissionIntent, REQUEST_OVERLAY)
    }

    private fun disableFloatingOverlay() {
        pendingFloatingOverlayEnable = false
        floatingOverlayEnabled = false
        prefs.edit().putBoolean("floating_overlay_enabled", false).apply()
        updateFloatingService()
        Toast.makeText(this, "Floating bar disembunyikan · audio tetap berjalan", Toast.LENGTH_SHORT).show()
    }

    private fun setFloatingOverlaySwitch(checked: Boolean) {
        floatingOverlaySwitch.setOnCheckedChangeListener(null)
        floatingOverlaySwitch.isChecked = checked
        floatingOverlaySwitch.setOnCheckedChangeListener { _, value ->
            if (value) enableFloatingOverlay() else disableFloatingOverlay()
        }
    }

    private fun enableBackupCapture() {
        if (!floatingServiceStarted && !servicePaused) {
            setBackupSwitch(false)
            Toast.makeText(this, "Mulai visualizer terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < 29) {
            setBackupSwitch(false)
            Toast.makeText(this, "Backup playback capture membutuhkan Android 10+", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setBackupSwitch(false)
            requestPrimaryAudioPermission()
            return
        }
        backupRequestPending = true
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_BACKUP_PROJECTION)
    }

    private fun disableBackupCapture() {
        backupRequestPending = false
        backupCaptureEnabled = false
        prefs.edit().putBoolean("backup_capture", false).apply()
        if (floatingServiceStarted) {
            val intent = Intent(this, FloatingVisualizerService::class.java).apply {
                action = FloatingVisualizerService.ACTION_DISABLE_BACKUP
                addFloatingSettings(this)
            }
            try { startService(intent) } catch (_: Exception) {}
        }
    }

    private fun setBackupSwitch(checked: Boolean) {
        backupSwitch.setOnCheckedChangeListener(null)
        backupSwitch.isChecked = checked
        backupSwitch.setOnCheckedChangeListener { _, value -> if (value) enableBackupCapture() else disableBackupCapture() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startPrimaryVisualizer()
            } else {
                Toast.makeText(this, "Izin audio diperlukan untuk visualizer", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_EXPORT_CONFIG -> if (resultCode == RESULT_OK && data?.data != null) {
                try {
                    contentResolver.openOutputStream(data.data!!, "wt")?.bufferedWriter()?.use {
                        it.write(createConfigBackup().toString(2))
                    } ?: throw IllegalStateException("Unable to create selected file")
                    Toast.makeText(this, if (appLanguage == "id") "Backup berhasil diekspor" else "Backup exported", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Toast.makeText(this, if (appLanguage == "id") "Ekspor backup gagal: ${error.message}" else "Backup export failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_IMPORT_CONFIG -> if (resultCode == RESULT_OK && data?.data != null) applyConfigBackup(data.data!!)
            REQUEST_EXPORT_PROFILE -> if (resultCode == RESULT_OK && data?.data != null) {
                try {
                    val profile = pendingProfileExport ?: throw IllegalStateException("No profile selected")
                    contentResolver.openOutputStream(data.data!!, "wt")?.bufferedWriter()?.use {
                        it.write(profileToJson(profile).toString(2))
                    } ?: throw IllegalStateException("Unable to create selected file")
                    Toast.makeText(this, if (appLanguage == "id") "Profil berhasil diekspor" else "Profile exported", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    Toast.makeText(this, if (appLanguage == "id") "Ekspor profil gagal: ${error.message}" else "Profile export failed: ${error.message}", Toast.LENGTH_LONG).show()
                } finally { pendingProfileExport = null }
            } else if (requestCode == REQUEST_EXPORT_PROFILE) {
                pendingProfileExport = null
            }
            REQUEST_IMPORT_PROFILE -> if (resultCode == RESULT_OK && data?.data != null) reviewImportedColorProfile(data.data!!)
            REQUEST_OVERLAY -> {
                pendingFloatingOverlayEnable = false
                if (Settings.canDrawOverlays(this)) {
                    floatingOverlayEnabled = true
                    prefs.edit().putBoolean("floating_overlay_enabled", true).apply()
                    setFloatingOverlaySwitch(true)
                    updateFloatingService()
                    Toast.makeText(
                        this,
                        if (floatingServiceStarted) "Floating bar ditampilkan" else "Floating siap muncul saat audio dimulai",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    floatingOverlayEnabled = false
                    prefs.edit().putBoolean("floating_overlay_enabled", false).apply()
                    setFloatingOverlaySwitch(false)
                    Toast.makeText(this, "Izin floating belum diberikan", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_BACKUP_PROJECTION -> {
                backupRequestPending = false
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val intent = Intent(this, FloatingVisualizerService::class.java).apply {
                        action = FloatingVisualizerService.ACTION_ENABLE_BACKUP
                        putExtra(FloatingVisualizerService.EXTRA_RESULT_CODE, resultCode)
                        putExtra(FloatingVisualizerService.EXTRA_PROJECTION_DATA, data)
                        addFloatingSettings(this)
                    }
                    try {
                        startService(intent)
                        backupCaptureEnabled = true
                        prefs.edit().putBoolean("backup_capture", true).apply()
                        audioSourceStatus.text = translateUiText("BACKUP · STARTING")
                    } catch (e: Exception) {
                        setBackupSwitch(false)
                        Toast.makeText(this, "Backup gagal: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    backupCaptureEnabled = false
                    prefs.edit().putBoolean("backup_capture", false).apply()
                    Toast.makeText(this, "Backup capture dibatalkan; Visualizer utama tetap aktif", Toast.LENGTH_SHORT).show()
                }
                setBackupSwitch(backupCaptureEnabled)
            }
        }
    }

    private fun startServiceIntent(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    private fun addFloatingSettings(intent: Intent) {
        intent.putExtra(FloatingVisualizerService.EXTRA_VERTICAL, isVerticalMode)
        intent.putExtra(FloatingVisualizerService.EXTRA_COLORS, spectrumColors.toIntArray())
        intent.putExtra(FloatingVisualizerService.EXTRA_WIDTH, overlayWidthDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_HEIGHT, overlayHeightDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_BAR_COUNT, barCount)
        intent.putExtra(FloatingVisualizerService.EXTRA_BAR_WIDTH, barWidthDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_GAP, barGapDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_SCALE, scalePercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_OPACITY, opacityPercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_GLOW, glowPercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_RESPONSE, responsePercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_CORNER_RADIUS, cornerRadiusDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_EFFECT, barEffect)
        intent.putExtra(FloatingVisualizerService.EXTRA_SINGLE_MODE, singleAudioMode)
        intent.putExtra(FloatingVisualizerService.EXTRA_BASS_MIN_HZ, bassMinHz)
        intent.putExtra(FloatingVisualizerService.EXTRA_BASS_MAX_HZ, bassMaxHz)
        intent.putExtra(FloatingVisualizerService.EXTRA_POSITION_X, positionXdp)
        intent.putExtra(FloatingVisualizerService.EXTRA_POSITION_Y, positionYdp)
        intent.putExtra(FloatingVisualizerService.EXTRA_POSITION_MODE, positionMode)
        intent.putExtra(FloatingVisualizerService.EXTRA_ROTATION, rotationDegrees)
        intent.putExtra(FloatingVisualizerService.EXTRA_FLOATING_ENABLED, floatingOverlayEnabled)
    }

    private fun updateFloatingService() {
        // The service can remain in the foreground while paused from its notification.
        // Keep settings synchronized in that state without creating a new audio session.
        if (!floatingServiceStarted && !servicePaused) return
        val intent = Intent(this, FloatingVisualizerService::class.java).apply {
            action = FloatingVisualizerService.ACTION_UPDATE
            addFloatingSettings(this)
        }
        try { startService(intent) } catch (e: Exception) {
            Toast.makeText(this, "Gagal memperbarui floating: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopFloating() {
        floatingServiceStarted = false
        backupCaptureEnabled = false
        servicePaused = false
        updateHandler.removeCallbacks(realtimeUpdate)
        try { stopService(Intent(this, FloatingVisualizerService::class.java)) } catch (_: Exception) {}
        prefs.edit().putBoolean("service_active", false).putBoolean("backup_capture", false).putBoolean("service_paused", false).apply()
        setBackupSwitch(false)
        previewBars.forEach { (it as? PreviewBarView)?.setLevel(0.03f) }
        engineStatus.text = translateUiText("● READY")
        audioSourceStatus.text = translateUiText("VISUALIZER · READY")
        updateStartStopButton()
    }

    private fun updateEngineStatus(status: String) {
        when {
            status.startsWith("PRIMARY_ACTIVE") -> {
                floatingServiceStarted = true
                backupCaptureEnabled = false
                engineStatus.text = translateUiText("● AUDIO LIVE")
                audioSourceStatus.text = translateUiText("VISUALIZER · ACTIVE")
                setBackupSwitch(false)
                servicePaused = false
                prefs.edit().putBoolean("service_active", true).putBoolean("backup_capture", false).putBoolean("service_paused", false).apply()
            }
            status.startsWith("BACKUP_ACTIVE") -> {
                floatingServiceStarted = true
                backupCaptureEnabled = true
                engineStatus.text = translateUiText("● BACKUP LIVE")
                audioSourceStatus.text = translateUiText("MEDIA PROJECTION · ACTIVE")
                setBackupSwitch(true)
                servicePaused = false
                prefs.edit().putBoolean("service_active", true).putBoolean("backup_capture", true).putBoolean("service_paused", false).apply()
            }
            status.startsWith("PRIMARY_ERROR") -> {
                // The foreground host remains available so the user can enable the optional backup.
                floatingServiceStarted = true
                servicePaused = false
                prefs.edit().putBoolean("service_active", true).putBoolean("service_paused", false).apply()
                engineStatus.text = translateUiText("● AUDIO UNAVAILABLE")
                audioSourceStatus.text = translateUiText("VISUALIZER · UNAVAILABLE")
                Toast.makeText(this, status.substringAfter(':', "Visualizer tidak tersedia"), Toast.LENGTH_LONG).show()
            }
            status.startsWith("BACKUP_ERROR") -> {
                backupCaptureEnabled = false
                prefs.edit().putBoolean("backup_capture", false).apply()
                setBackupSwitch(false)
                engineStatus.text = translateUiText("● BACKUP ERROR")
                audioSourceStatus.text = translateUiText("PRIMARY VISUALIZER")
                Toast.makeText(this, status.substringAfter(':', "Backup tidak tersedia"), Toast.LENGTH_LONG).show()
            }
            status.startsWith("OVERLAY_ACTIVE") -> {
                floatingOverlayEnabled = true
                setFloatingOverlaySwitch(true)
                prefs.edit().putBoolean("floating_overlay_enabled", true).apply()
            }
            status.startsWith("OVERLAY_HIDDEN") -> {
                floatingOverlayEnabled = false
                setFloatingOverlaySwitch(false)
                prefs.edit().putBoolean("floating_overlay_enabled", false).apply()
            }
            status.startsWith("OVERLAY_PERMISSION_REQUIRED") -> {
                floatingOverlayEnabled = false
                setFloatingOverlaySwitch(false)
                prefs.edit().putBoolean("floating_overlay_enabled", false).apply()
                Toast.makeText(this, "Izin tampil di atas aplikasi perlu diaktifkan dari aplikasi", Toast.LENGTH_LONG).show()
            }
            status.startsWith("OVERLAY_ERROR") -> {
                Toast.makeText(
                    this,
                    status.substringAfter(':', "Floating window gagal dibuat; audio tetap berjalan"),
                    Toast.LENGTH_LONG
                ).show()
            }
            status.startsWith("AUDIO_PAUSED") -> {
                floatingServiceStarted = false
                servicePaused = true
                backupCaptureEnabled = false
                prefs.edit().putBoolean("service_active", false).putBoolean("backup_capture", false).putBoolean("service_paused", true).apply()
                engineStatus.text = translateUiText("● AUDIO PAUSED")
                audioSourceStatus.text = translateUiText("ENGINE · PAUSED")
                setBackupSwitch(false)
            }
            status.startsWith("STOPPED") -> {
                floatingServiceStarted = false
                servicePaused = false
                backupCaptureEnabled = false
                prefs.edit().putBoolean("service_active", false).putBoolean("service_paused", false).apply()
                engineStatus.text = translateUiText("● READY")
                audioSourceStatus.text = translateUiText("VISUALIZER · READY")
                setBackupSwitch(false)
            }
        }
        updateStartStopButton()
    }
}
