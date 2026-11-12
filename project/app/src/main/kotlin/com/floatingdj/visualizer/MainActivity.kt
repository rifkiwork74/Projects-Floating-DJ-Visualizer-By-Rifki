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
    private var barEffect = EFFECT_GLOSSY

    private var singleModeCard: LinearLayout? = null
    private var loadedProfileName: String? = null
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
    private val realtimeUpdate = Runnable {
        saveSettings()
        updateFloatingService()
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
        positionXdp = prefs.getInt("position_x", positionXdp)
        positionYdp = prefs.getInt("position_y", positionYdp)
        clampPosition()
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
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme(animated = false)
        scanDisplayBounds()
        clampPosition()
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
        barCount = prefs.getInt("bar_count", 15).coerceIn(5, MAX_BAR_COUNT)
        barWidthDp = prefs.getInt("bar_width", 20)
        barGapDp = prefs.getInt("bar_gap", 3)
        scalePercent = prefs.getInt("scale", 100)
        opacityPercent = prefs.getInt("opacity", 100)
        glowPercent = prefs.getInt("glow", 65)
        responsePercent = prefs.getInt("response_speed", 82).coerceIn(0, 100)
        cornerRadiusDp = prefs.getInt("corner_radius", 14)
        isVerticalMode = prefs.getBoolean("vertical", false)
        singleAudioMode = prefs.getString("single_audio_mode", "MUSIC") ?: "MUSIC"
        bassMinHz = prefs.getInt("bass_min_hz", 60)
        bassMaxHz = prefs.getInt("bass_max_hz", 160)
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
        barEffect = prefs.getString("bar_effect", EFFECT_GLOSSY) ?: EFFECT_GLOSSY

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
        .putString("bar_effect", barEffect)
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
        overlayWidthDp = overlayWidthDp.coerceIn(120, (screenWidthDp * 0.94f).toInt().coerceAtLeast(120))
        overlayHeightDp = overlayHeightDp.coerceIn(80, (screenHeightDp * 0.90f).toInt().coerceAtLeast(80))
        barWidthDp = barWidthDp.coerceIn(4, (screenWidthDp * 0.35f).toInt().coerceAtLeast(24))
        barGapDp = barGapDp.coerceIn(0, 32)
        scalePercent = scalePercent.coerceIn(50, 180)
        opacityPercent = opacityPercent.coerceIn(10, 100)
        glowPercent = glowPercent.coerceIn(0, 100)
        cornerRadiusDp = cornerRadiusDp.coerceIn(0, 40)
        bassMinHz = bassMinHz.coerceIn(30, 200)
        bassMaxHz = bassMaxHz.coerceIn(bassMinHz + 20, 400)
        val (bw, bh) = rotatedBoundsDp()
        if (!positionInitialized) {
            positionXdp = ((screenWidthDp - bw) / 2).coerceAtLeast(0)
            positionYdp = ((screenHeightDp - bh) / 2).coerceAtLeast(0)
            positionInitialized = true
        } else clampPosition()
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
        val colors = if (active) intArrayOf(lighten(accent, 0.15f), darken(accent, 0.18f))
        else if (light) intArrayOf(Color.WHITE, Color.parseColor("#E7ECF5"))
        else intArrayOf(Color.parseColor("#273248"), Color.parseColor("#172235"))
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = 16.dp().toFloat()
            setStroke(1.dp(), if (active) 0x88FFFFFF.toInt() else if (light) 0x557F8DA5 else 0x557D91B8)
        }
    }

    private fun cardDrawable(light: Boolean, accent: Int): GradientDrawable {
        val top = if (light) Color.WHITE else blend(Color.parseColor("#1C2B42"), accent, 0.10f)
        val bottom = if (light) Color.parseColor("#F7F9FD") else Color.parseColor("#101A2B")
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = 25.dp().toFloat()
            setStroke(1.dp(), if (light) 0x668C9BB1 else blend(0x667D91B8, accent, 0.28f))
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
            val neonStrength = if (barEffect == EFFECT_NEON) glowPercent.coerceIn(0, 100) / 100f else 0f
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
        addControlSlider(bass, "BASS START", bassMinHz, 30, 180, " Hz") { bassMinHz = minOf(it, bassMaxHz - 20); saveSettings(); updateFloatingService() }
        addControlSlider(bass, "BASS END", bassMaxHz, 70, 400, " Hz") { bassMaxHz = maxOf(it, bassMinHz + 20); saveSettings(); updateFloatingService() }
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
        addControlSlider(controlsContainer, "HORIZONTAL WIDTH", overlayWidthDp, 120, (screenWidthDp * 0.94f).toInt().coerceAtLeast(120), " dp") { overlayWidthDp = it; clampPosition() }
        addControlSlider(controlsContainer, "BAR HEIGHT / LENGTH", overlayHeightDp, 80, (screenHeightDp * 0.90f).toInt().coerceAtLeast(80), " dp") { overlayHeightDp = it; clampPosition() }
        addControlSlider(controlsContainer, "HORIZONTAL BAR COUNT", barCount, 5, MAX_BAR_COUNT, " bars") {
            barCount = it
            if (!isVerticalMode) buildSpectrumBars()
        }
        addControlSlider(controlsContainer, "BAR THICKNESS", barWidthDp, 4, (screenWidthDp * 0.35f).toInt().coerceAtLeast(24), " dp") { barWidthDp = it; clampPosition() }
        addControlSlider(controlsContainer, "BAR GAP", barGapDp, 0, 32, " dp") { barGapDp = it }
        addControlSlider(controlsContainer, "SCALE", scalePercent, 50, 180, "%") { scalePercent = it; clampPosition() }
        addControlSlider(controlsContainer, "OPACITY", opacityPercent, 10, 100, "%") { opacityPercent = it }
        addControlSlider(controlsContainer, "GLOW INTENSITY", glowPercent, 0, 100, "%") { glowPercent = it }
        addControlSlider(controlsContainer, "RESPONSE SPEED", responsePercent, 0, 100, "%") { responsePercent = it }
        addControlSlider(controlsContainer, "CORNER ROUNDNESS", cornerRadiusDp, 0, 40, " dp") { cornerRadiusDp = it }
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
            updateHandler.removeCallbacks(realtimeUpdate)
            updateHandler.postDelayed(realtimeUpdate, 70L)
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
                    updateHandler.removeCallbacks(realtimeUpdate)
                    updateHandler.postDelayed(realtimeUpdate, 70L)
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
        getDrawable(R.drawable.seekbar_thumb_glass)?.mutate()?.let { drawable ->
            seek.thumb = drawable
        }
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
        binding.seek.progress = safeValue - binding.minimum
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
                            widthDp = item.optInt("width", 320).coerceIn(120, 1800),
                            heightDp = item.optInt("height", 360).coerceIn(80, 1800),
                            barCount = item.optInt("bar_count", 15).coerceIn(5, MAX_BAR_COUNT),
                            barWidthDp = item.optInt("bar_width", 20).coerceIn(2, 140),
                            gapDp = item.optInt("bar_gap", 3).coerceIn(0, 32),
                            scalePercent = item.optInt("scale", 100).coerceIn(50, 180),
                            opacityPercent = item.optInt("opacity", 100).coerceIn(10, 100),
                            glowPercent = item.optInt("glow", 65).coerceIn(0, 100),
                            responsePercent = item.optInt("response_speed", 82).coerceIn(0, 100),
                            cornerRadiusDp = item.optInt("corner_radius", 14).coerceIn(0, 50)
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
        return true
    }

    private fun showProfileNameDialog(
        title: String,
        initialName: String = "",
        onSave: (String) -> Unit
    ) {
        val input = EditText(this).apply {
            hint = "Profile name"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(initialName)
            setSelection(text.length)
            setTextColor(if (isLightTheme()) Color.DKGRAY else Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            background = glassEditDrawable()
        }
        val wrap = glassPanel().apply {
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            addView(input, matchWrap())
        }
        val dialog = AlertDialog.Builder(this)
        .setTitle(title)
        .setView(wrap)
        .setNegativeButton("CANCEL", null)
        .setPositiveButton("SAVE", null)
        .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                if (value.isBlank()) {
                    input.error = "Name cannot be empty"
                    return@setOnClickListener
                }
                onSave(value)
                dialog.dismiss()
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showColorProfileManager(onUse: (ColorProfileEntry) -> Unit) {
        val profiles = readColorProfiles()
        if (profiles.isEmpty()) {
            Toast.makeText(this, "Belum ada profile tersimpan. Gunakan SAVE AS PROFILE.", Toast.LENGTH_LONG).show()
            return
        }

        var selectedIndex = 0
        val labels = profiles.map { it.name }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
        .setTitle("SAVED COLOR PROFILES")
        .setSingleChoiceItems(labels, 0) { _, which ->
            selectedIndex = which
        }
        .setPositiveButton("LOAD", null)
        .setNeutralButton("RENAME", null)
        .setNegativeButton("DELETE", null)
        .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                onUse(profiles[selectedIndex])
                dialog.dismiss()
            }

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val chosen = profiles[selectedIndex]
                dialog.dismiss()
                showProfileNameDialog("RENAME PROFILE", chosen.name) { newName ->
                    val latest = readColorProfiles()
                    val index = latest.indexOfFirst {
                        it.name.equals(chosen.name, ignoreCase = true)
                    }
                    if (index >= 0) {
                        val collision = latest.indexOfFirst {
                            it.name.equals(newName, ignoreCase = true)
                        }
                        if (collision >= 0 && collision != index) {
                            Toast.makeText(this, "Nama profile sudah digunakan", Toast.LENGTH_SHORT).show()
                        } else {
                            latest[index] = chosen.copy(name = newName.take(28))
                            writeColorProfiles(latest)
                            if (loadedProfileName.equals(chosen.name, ignoreCase = true)) {
                                loadedProfileName = newName.take(28)
                            }
                            Toast.makeText(this, "Profile renamed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                val chosen = profiles[selectedIndex]
                AlertDialog.Builder(this)
                .setTitle("DELETE PROFILE?")
                .setMessage("Hapus profile \"${chosen.name}\"?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE") { _, _ ->
                    val latest = readColorProfiles()
                    .filterNot { it.name.equals(chosen.name, ignoreCase = true) }
                    writeColorProfiles(latest)
                    if (loadedProfileName.equals(chosen.name, ignoreCase = true)) {
                        loadedProfileName = null
                    }
                    Toast.makeText(this, "Profile deleted", Toast.LENGTH_SHORT).show()
                }
                .show()
                dialog.dismiss()
            }
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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
            setPadding(17.dp(), 17.dp(), 17.dp(), 17.dp())
        }
        val dialogDragHandle = sectionTitle("SPECTRUM PROFILE EDITOR  ·  DRAG TO MOVE").apply {
            contentDescription = "Drag to move this color editor"
            setPadding(0, 0, 0, 4.dp())
        }
        panel.addView(dialogDragHandle)
        panel.addView(subtitle("Edit each of the five color stops independently"))

        var selectedIndex = 0
        var usingPreset = false
        var selectedProfile: ColorProfileEntry? = null
        val draftColors = spectrumColors.toMutableList()
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

        val selectedLabel = sectionTitle("COLOR STOP 1")
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
        val satLabel = subtitle("SATURATION  ${(hsv[1] * 100).toInt()}%")
        val valueLabel = subtitle("BRIGHTNESS  ${(hsv[2] * 100).toInt()}%")
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
            satLabel.text = "SATURATION  ${(hsv[1] * 100).toInt()}%"
            valueLabel.text = "BRIGHTNESS  ${(hsv[2] * 100).toInt()}%"
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
            selectedLabel.text = "COLOR STOP ${selectedIndex + 1}"
        }

        fun applyCurrentColor() {
            val color = Color.HSVToColor(hsv)
            draftColors[selectedIndex] = color
            colorWheel.setColor(color, notify = false)
            preview.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, draftColors.toIntArray()).apply { cornerRadius = 16.dp().toFloat() }
            hexInput.setText(String.format("#%06X", 0xFFFFFF and color))
            redInput.setText(Color.red(color).toString())
            greenInput.setText(Color.green(color).toString())
            blueInput.setText(Color.blue(color).toString())
            usingPreset = false
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
            satLabel.text = "SATURATION  ${(hsv[1] * 100).toInt()}%"
            valueLabel.text = "BRIGHTNESS  ${(hsv[2] * 100).toInt()}%"
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
                    usingPreset = true
                    updateEditorFromColor(draftColors[0])
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
                    selectedProfile = readColorProfiles().firstOrNull {
                        it.name.equals(profileName, ignoreCase = true)
                    }
                    Toast.makeText(this, "Profile saved: $profileName", Toast.LENGTH_SHORT).show()
                }
            }
        }
        manageButton.setOnClickListener {
            showColorProfileManager { profile ->
                loadedProfileName = profile.name
                selectedProfile = profile
                draftColors.clear()
                draftColors.addAll(profile.colors)
                selectedIndex = 0
                updateEditorFromColor(draftColors[0])
                Toast.makeText(this, "Loaded ${profile.name}. APPLY updates its palette and bar style.", Toast.LENGTH_SHORT).show()
            }
        }
        panel.addView(profileActions)
        scroll.addView(panel)

        val dialog = AlertDialog.Builder(this)
        .setView(scroll)
        .setNegativeButton("CANCEL", null)
        .setPositiveButton("APPLY PALETTE", null)
        .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val hex = hexInput.text.toString().trim()
                if (!usingPreset && hex.isNotEmpty()) {
                    val parsed = try {
                        Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
                    } catch (_: Exception) {
                        Toast.makeText(this, "HEX tidak valid. Gunakan #RRGGBB", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    draftColors[selectedIndex] = parsed
                }
                spectrumColors.clear()
                spectrumColors.addAll(draftColors.take(5))

                selectedProfile?.let { profile ->
                    scanDisplayBounds()
                    barEffect = profile.effect
                    overlayWidthDp = profile.widthDp.coerceIn(
                        120,
                        (screenWidthDp * 0.94f).toInt().coerceAtLeast(120)
                    )
                    overlayHeightDp = profile.heightDp.coerceIn(
                        80,
                        (screenHeightDp * 0.90f).toInt().coerceAtLeast(80)
                    )
                    barCount = profile.barCount.coerceIn(5, MAX_BAR_COUNT)
                    barWidthDp = profile.barWidthDp.coerceIn(
                        4,
                        (screenWidthDp * 0.35f).toInt().coerceAtLeast(24)
                    )
                    barGapDp = profile.gapDp.coerceIn(0, 32)
                    scalePercent = profile.scalePercent.coerceIn(50, 180)
                    opacityPercent = profile.opacityPercent.coerceIn(10, 100)
                    glowPercent = profile.glowPercent.coerceIn(0, 100)
                    responsePercent = profile.responsePercent.coerceIn(0, 100)
                    cornerRadiusDp = profile.cornerRadiusDp.coerceIn(0, 40)
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
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.93f).toInt(),
            (resources.displayMetrics.heightPixels * 0.82f).toInt()
        )
        attachDialogDragHandle(dialog, dialogDragHandle)
    }

    private fun simpleSeekListener(onValue: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onValue(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
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
        val dragHandle = sectionTitle("INTERFACE COLOR STUDIO  ·  DRAG TO MOVE").apply {
            contentDescription = "Drag to move this appearance editor"
        }
        panel.addView(dragHandle)
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

        refreshColorFields(themeAccent)
        scroll.addView(panel)

        val dialog = AlertDialog.Builder(this)
        .setView(scroll)
        .setNegativeButton("CANCEL", null)
        .setPositiveButton("APPLY THEME", null)
        .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (resources.displayMetrics.heightPixels * 0.78f).toInt()
        )
        attachDialogDragHandle(dialog, dragHandle)
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
            val colors = intArrayOf(
                Color.RED,
                Color.MAGENTA,
                Color.BLUE,
                Color.CYAN,
                Color.GREEN,
                Color.YELLOW,
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

    private fun attachDialogDragHandle(dialog: AlertDialog, handle: View) {
        val dialogWindow = dialog.window ?: return
        dialogWindow.setGravity(Gravity.CENTER)
        var startRawX = 0f
        var startRawY = 0f
        var startWindowX = 0
        var startWindowY = 0
        handle.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    val attributes = dialogWindow.attributes
                    startWindowX = attributes.x
                    startWindowY = attributes.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val screenWidth = resources.displayMetrics.widthPixels
                    val screenHeight = resources.displayMetrics.heightPixels
                    val dialogWidth = dialogWindow.decorView.width.coerceAtLeast(1)
                    val dialogHeight = dialogWindow.decorView.height.coerceAtLeast(1)
                    val maxOffsetX = ((screenWidth - dialogWidth) / 2).coerceAtLeast(0)
                    val maxOffsetY = ((screenHeight - dialogHeight) / 2).coerceAtLeast(0)
                    val attributes = dialogWindow.attributes
                    attributes.x = (startWindowX + (event.rawX - startRawX).toInt()).coerceIn(-maxOffsetX, maxOffsetX)
                    attributes.y = (startWindowY + (event.rawY - startRawY).toInt()).coerceIn(-maxOffsetY, maxOffsetY)
                    dialogWindow.attributes = attributes
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> true
            }
        }
    }

    // -----------------------------------------------------------------
    // POSITION STUDIO
    // -----------------------------------------------------------------

    private fun showPositionSettings() {
        scanDisplayBounds()
        positionXdp = prefs.getInt("position_x", positionXdp)
        positionYdp = prefs.getInt("position_y", positionYdp)
        clampPosition()

        val panel = glassPanel().apply {
            setPadding(17.dp(), 17.dp(), 17.dp(), 17.dp())
        }
        val dialogDragHandle = sectionTitle("FLOATING POSITION STUDIO  ·  DRAG TO MOVE").apply {
            contentDescription = "Drag to move this position editor"
        }
        panel.addView(dialogDragHandle)
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
                    clampPosition()
                    updateHandler.removeCallbacks(realtimeUpdate)
                    updateHandler.postDelayed(realtimeUpdate, 65L)
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
            clampPosition()
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

        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = false
            addView(panel)
        }
        val dialog = AlertDialog.Builder(this)
        .setView(scroll)
        .setPositiveButton("DONE", null)
        .create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (resources.displayMetrics.heightPixels * 0.76f).toInt()
        )
        attachDialogDragHandle(dialog, dialogDragHandle)
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
        text = label
        gravity = Gravity.CENTER
        textSize = 11f
        setTextColor(if (isLightTheme()) Color.DKGRAY else Color.WHITE)
        setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
        background = pillDrawable(false, isLightTheme(), currentAccent())
        isClickable = true
        isFocusable = true
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(if (isLightTheme()) Color.parseColor("#1B2940") else Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun subtitle(value: String): TextView = TextView(this).apply {
        text = value
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
                button.text = "▶  RESUME VISUALIZER"
                button.background = accentDrawable(currentAccent())
                button.contentDescription = "Resume audio visualizer"
            }
            floatingServiceStarted -> {
                button.text = "■  STOP VISUALIZER"
                button.background = dangerDrawable()
                button.contentDescription = "Stop audio visualizer"
            }
            else -> {
                button.text = "▶  START VISUALIZER"
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
            engineStatus.text = "● STARTING"
            audioSourceStatus.text = "VISUALIZER · STARTING"
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
                        audioSourceStatus.text = "BACKUP · STARTING"
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
        engineStatus.text = "● READY"
        audioSourceStatus.text = "VISUALIZER · READY"
        updateStartStopButton()
    }

    private fun updateEngineStatus(status: String) {
        when {
            status.startsWith("PRIMARY_ACTIVE") -> {
                floatingServiceStarted = true
                backupCaptureEnabled = false
                engineStatus.text = "● AUDIO LIVE"
                audioSourceStatus.text = "VISUALIZER · ACTIVE"
                setBackupSwitch(false)
                servicePaused = false
                prefs.edit().putBoolean("service_active", true).putBoolean("backup_capture", false).putBoolean("service_paused", false).apply()
            }
            status.startsWith("BACKUP_ACTIVE") -> {
                floatingServiceStarted = true
                backupCaptureEnabled = true
                engineStatus.text = "● BACKUP LIVE"
                audioSourceStatus.text = "MEDIA PROJECTION · ACTIVE"
                setBackupSwitch(true)
                servicePaused = false
                prefs.edit().putBoolean("service_active", true).putBoolean("backup_capture", true).putBoolean("service_paused", false).apply()
            }
            status.startsWith("PRIMARY_ERROR") -> {
                // The foreground host remains available so the user can enable the optional backup.
                floatingServiceStarted = true
                servicePaused = false
                prefs.edit().putBoolean("service_active", true).putBoolean("service_paused", false).apply()
                engineStatus.text = "● AUDIO UNAVAILABLE"
                audioSourceStatus.text = "VISUALIZER · UNAVAILABLE"
                Toast.makeText(this, status.substringAfter(':', "Visualizer tidak tersedia"), Toast.LENGTH_LONG).show()
            }
            status.startsWith("BACKUP_ERROR") -> {
                backupCaptureEnabled = false
                prefs.edit().putBoolean("backup_capture", false).apply()
                setBackupSwitch(false)
                engineStatus.text = "● BACKUP ERROR"
                audioSourceStatus.text = "PRIMARY VISUALIZER"
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
                engineStatus.text = "● AUDIO PAUSED"
                audioSourceStatus.text = "ENGINE · PAUSED"
                setBackupSwitch(false)
            }
            status.startsWith("STOPPED") -> {
                floatingServiceStarted = false
                servicePaused = false
                backupCaptureEnabled = false
                prefs.edit().putBoolean("service_active", false).putBoolean("service_paused", false).apply()
                engineStatus.text = "● READY"
                audioSourceStatus.text = "VISUALIZER · READY"
                setBackupSwitch(false)
            }
        }
        updateStartStopButton()
    }
}
