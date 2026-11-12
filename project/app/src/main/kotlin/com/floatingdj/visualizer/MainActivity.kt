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
import android.os.SystemClock
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
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.Choreographer
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : Activity() {

    private lateinit var mainRoot: ScrollView
    private lateinit var mainShell: FrameLayout
    private lateinit var contentRoot: LinearLayout
    private lateinit var barsContainer: LinearLayout
    private lateinit var singleModeContainer: LinearLayout
    private lateinit var horizontalModeContainer: LinearLayout
    private lateinit var controlsContainer: LinearLayout
    private lateinit var barAppearancePresetContainer: LinearLayout
    private lateinit var barAppearancePresetStatus: TextView
    private val barAppearancePresetViews = mutableMapOf<String, MutableList<Pair<TextView, String>>>()
    private val barAppearancePresetGalleryViews = mutableListOf<Triple<String, TextView, String>>()
    private lateinit var colorSwatches: LinearLayout
    private lateinit var floatingProfilesList: LinearLayout
    private lateinit var floatingProfileCount: TextView
    private lateinit var colorProfileFloatingControls: LinearLayout
    private lateinit var liveFrequencyLabels: LinearLayout
    private lateinit var liveFrequencyScroll: HorizontalScrollView
    private lateinit var liveFrequencyControlsRow: LinearLayout
    private lateinit var livePreviewProfileButton: TextView
    private lateinit var visualizerModeScroll: HorizontalScrollView
    private lateinit var modeScrollLeft: TextView
    private lateinit var modeScrollRight: TextView
    private lateinit var frequencyScrollLeft: TextView
    private lateinit var frequencyScrollRight: TextView
    private lateinit var modeComingSoon: TextView
    private var activePositionDialogProfileId: String? = null
    private var activePositionCoordinateView: TextView? = null
    private var activePositionDialogUnit: String = "DP"
    private var activePositionDraftUpdater: ((Int, Int) -> Unit)? = null
    private var themeTextColor = Color.parseColor("#F5F7FC")
    private var singleRangeResetButton: TextView? = null
    private var livePreviewProfileId = "root"
    private var latestSpectrumLevels = FloatArray(MAX_BAR_COUNT + 2)
    private var livePreviewProfileCache: FloatingLayerProfile? = null
    private var livePreviewProfileCacheRevision = -1L
    private var singleModeHideAnimationRunning = false
    private var livePreviewProfileRevision = 0L
    private val floatingProfileSectionPickerButtons = mutableListOf<TextView>()
    private val floatingProfileSectionCopyButtons = mutableListOf<TextView>()
    private var selectedFloatingProfileId = "root"
    private var lastAppliedFloatingProfileId: String? = null
    private var suppressFloatingProfileSync = false
    private var floatingProfilePickerButton: TextView? = null
    private lateinit var engineStatus: TextView
    private lateinit var audioSourceStatus: TextView
    private lateinit var backupSwitch: Switch
    private lateinit var floatingOverlaySwitch: Switch
    private var floatingOverlayEnabled = false
    private val scrollEdgeFadeRefs = mutableListOf<java.lang.ref.WeakReference<ScrollEdgeGlassFadeDrawable>>()
    private var pendingFloatingOverlayEnable = false

    private val prefs by lazy {
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
    }

    // Neutral white is the initial palette. Users can choose a color family
    // from Color Studio; existing saved palettes are never overwritten.
    private val spectrumColors = mutableListOf(
        Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE
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
    private var shadowOpacityPercent = 100
    private var glowPercent = 50
    private var responsePercent = 82
    private var cornerRadiusDp = 14

    private var singleAudioMode = "MUSIC"
    // Full-range single meter analysis. Defaults intentionally focus on a useful bass range.
    private var bassMinHz = 20
    private var bassMaxHz = 125
    private var horizontalAlignment = "LEFT"
    private var mirrorMode = "OFF" // OFF, VERTICAL (top/bottom), HORIZONTAL (left/right)

    private var screenWidthDp = 360
    private var screenHeightDp = 740
    // Physical display bounds are shared with the overlay Service so X/Y edge limits
    // resolve to the same pixel coordinate space on every density and Android version.
    private var screenWidthPx = 0
    private var screenHeightPx = 0
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
    private var singleModeButtonsRow: LinearLayout? = null
    private var loadedProfileName: String? = null
    private var profileDirty = false
    private var suppressProfileDirty = false
    private var profilePreviewActive = false
    private var profileSaveChangesButton: TextView? = null
    private var pendingProfileExport: ColorProfileEntry? = null // Legacy palette format; not exposed in the Color Studio UI.
    private var pendingFloatingSceneExportJson: String? = null
    private var pendingFloatingSceneExportName: String? = null
    private var horizontalFlowPanel: LinearLayout? = null
    private var horizontalFlowValueButton: TextView? = null
    private var horizontalMirrorValueButton: TextView? = null
    private var horizontalBandInfoLabel: TextView? = null
    private var lastBandLabelUpdateMs = 0L
    private var bassControls: LinearLayout? = null
    private val previewBars = mutableListOf<View>()

    // One display-synchronised animation loop updates all preview bars together.
    // FFT callbacks may arrive around 20 Hz; the preview is interpolated at the
    // device's actual display cadence so bars and peak markers do not step.
    private var previewFrameScheduled = false
    private var previewLastFrameNanos = 0L
    private var activityStartedForPreview = false
    private val previewFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            previewFrameScheduled = false
            if (!activityStartedForPreview || isFinishing || isDestroyed) {
                previewLastFrameNanos = 0L
                return
            }
            val dtSeconds = if (previewLastFrameNanos == 0L) (1f / 60f) else
                ((frameTimeNanos - previewLastFrameNanos).coerceIn(1_000_000L, 50_000_000L) / 1_000_000_000f)
            previewLastFrameNanos = frameTimeNanos
            val nowMs = SystemClock.uptimeMillis()
            val responseFactors = previewFrameSmoothingFactors(livePreviewProfile().responsePercent, dtSeconds)
            var continueAnimating = false
            previewBars.forEach { view ->
                val bar = view as? PreviewBarView
                if (bar != null && bar.advanceAnimation(dtSeconds, nowMs, responseFactors.first, responseFactors.second)) continueAnimating = true
            }
            if (continueAnimating) {
                schedulePreviewAnimationFrame()
            } else {
                previewLastFrameNanos = 0L
            }
        }
    }

    private fun schedulePreviewAnimationFrame() {
        if (!activityStartedForPreview || isFinishing || previewFrameScheduled) return
        if (previewBars.none { it is PreviewBarView }) return
        previewFrameScheduled = true
        Choreographer.getInstance().postFrameCallback(previewFrameCallback)
    }

    private fun cancelPreviewAnimationFrame() {
        if (previewFrameScheduled) {
            Choreographer.getInstance().removeFrameCallback(previewFrameCallback)
            previewFrameScheduled = false
        }
        previewLastFrameNanos = 0L
    }

    private fun previewFrameSmoothingFactors(responsePercent: Int, dtSeconds: Float): Pair<Float, Float> {
        val response = responsePercent.coerceIn(0, 100) / 100f
        // Preserve the former response feel at a 20 Hz audio update interval,
        // but evaluate it for every display frame using an exponential time curve.
        val dt = dtSeconds.coerceIn(0.001f, 0.05f)
        fun factor(sampleFactor: Float): Float {
            val ratePerSecond = -kotlin.math.ln((1f - sampleFactor).coerceAtLeast(0.001f).toDouble()) / 0.05
            return (1.0 - exp(-ratePerSecond * dt)).toFloat().coerceIn(0f, 1f)
        }
        return Pair(factor(0.56f + response * 0.40f), factor(0.20f + response * 0.62f))
    }
    private data class SliderBinding(
        val seek: SeekBar,
        val input: EditText,
        val minimum: Int,
        val maximum: Int
    )

    private data class BarAppearancePreset(
        val name: String,
        val description: String,
        val effect: String,
        val glow: Int,
        val opacity: Int,
        val response: Int,
        val corner: Int,
        val gap: Int
    )
    private val sliderBindings = mutableMapOf<String, SliderBinding>()

    private val updateHandler = Handler(Looper.getMainLooper())
    private var realtimeUpdateScheduled = false
    private var livePreviewBarLayoutSignature = ""
    private var livePreviewFrequencySignature = ""
    private val realtimeUpdate = Runnable {
        realtimeUpdateScheduled = false
        storeSelectedEffectGlow(barEffect, glowPercent)
        saveSettings()
        updateFloatingService()
        if (::barsContainer.isInitialized) previewBars.forEach { it.invalidate() }
    }
    private val barCountPreviewRebuild = Runnable {
        if (!isVerticalMode) buildSpectrumBars()
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
        private const val THEME_DARK = "DARK"
        private const val THEME_LIGHT = "LIGHT"

        private const val EFFECT_CLEAN = "CLEAN"
        private const val EFFECT_GLOSSY = "GLOSSY"
        private const val EFFECT_NEON = "NEON"
        private const val EFFECT_GLASS = "GLASS"
        private const val MAX_BAR_COUNT = 64
        private const val MIN_SPECTRUM_HZ = 20.0
        private const val MAX_SPECTRUM_HZ = 20000.0
        private const val PREVIEW_FLOATING_SCALE = 0.30f
        private const val REQUEST_EXPORT_CONFIG = 3101
        private const val REQUEST_IMPORT_CONFIG = 3102
        private const val CONFIG_SCHEMA_VERSION = 4
        private const val REQUEST_EXPORT_PROFILE = 3103
        private const val REQUEST_IMPORT_PROFILE = 3104
        private const val REQUEST_EXPORT_FLOATING_SCENE = 3105
        private const val REQUEST_IMPORT_FLOATING_SCENE = 3106
        private const val PROFILE_SCHEMA_VERSION = 4
        private const val FLOATING_SCENE_SCHEMA_VERSION = 1
        private const val MAX_FLOATING_SCENES = 32
    }

    private val levelsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                FloatingVisualizerService.ACTION_POSITION -> {
                    val newX = intent.getIntExtra(FloatingVisualizerService.EXTRA_POSITION_X, positionXdp)
                    val newY = intent.getIntExtra(FloatingVisualizerService.EXTRA_POSITION_Y, positionYdp)
                    val layerId = intent.getStringExtra(FloatingVisualizerService.EXTRA_POSITION_PROFILE_ID)
                    val isFinalPosition = intent.getBooleanExtra(FloatingVisualizerService.EXTRA_POSITION_FINAL, false)
                    if (!layerId.isNullOrBlank()) {
                        if (layerId == selectedFloatingProfileId) {
                            positionXdp = newX
                            positionYdp = newY
                            positionInitialized = true
                            if (isFinalPosition) markProfileDirtyFromCurrentState()
                        }
                        if (layerId == livePreviewProfileId) invalidateLivePreviewProfileCache()
                        if (activePositionDialogProfileId == layerId) {
                            activePositionCoordinateView?.text = formatLayerCoordinateStatus(newX, newY, activePositionDialogUnit)
                            activePositionDraftUpdater?.invoke(newX, newY)
                        }
                        // Do not rebuild the whole profile list during a drag. The service
                        // broadcasts transient coordinates at a capped rate and persists
                        // them once on release; only the final event refreshes the rows.
                        if (isFinalPosition) {
                            buildFloatingProfileList()
                            refreshFloatingProfileSectionPickers()
                        }
                    } else if (newX != positionXdp || newY != positionYdp) {
                        positionXdp = newX
                        positionYdp = newY
                        markProfileDirtyFromCurrentState()
                    }
                }
                FloatingVisualizerService.ACTION_LAYER_FOCUSED -> {
                    val focusedId = intent.getStringExtra(FloatingVisualizerService.EXTRA_LAYER_ID)
                    if (!focusedId.isNullOrBlank()) selectFloatingProfileFromOverlay(focusedId)
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

        mainShell = findViewById(R.id.mainShell)
        mainRoot = findViewById(R.id.mainRoot)
        contentRoot = findViewById(R.id.contentRoot)
        barsContainer = findViewById(R.id.equalizerBars)
        singleModeContainer = findViewById(R.id.singleModeContainer)
        horizontalModeContainer = findViewById(R.id.horizontalModeContainer)
        controlsContainer = findViewById(R.id.floatingControlsContainer)
        barAppearancePresetContainer = findViewById(R.id.barAppearancePresetContainer)
        colorSwatches = findViewById(R.id.colorSwatches)
        floatingProfilesList = findViewById(R.id.floatingProfilesList)
        floatingProfileCount = findViewById(R.id.txtFloatingProfileCount)
        colorProfileFloatingControls = findViewById(R.id.colorProfileFloatingControls)
        liveFrequencyLabels = findViewById(R.id.liveFrequencyLabels)
        liveFrequencyScroll = findViewById(R.id.liveFrequencyScroll)
        liveFrequencyControlsRow = findViewById(R.id.liveFrequencyControlsRow)
        livePreviewProfileButton = findViewById(R.id.btnLivePreviewProfile)
        visualizerModeScroll = findViewById(R.id.visualizerModeScroll)
        modeScrollLeft = findViewById(R.id.btnModeScrollLeft)
        modeScrollRight = findViewById(R.id.btnModeScrollRight)
        frequencyScrollLeft = findViewById(R.id.btnFrequencyScrollLeft)
        frequencyScrollRight = findViewById(R.id.btnFrequencyScrollRight)
        modeComingSoon = findViewById(R.id.btnModeComingSoon)
        engineStatus = findViewById(R.id.txtEngineStatus)
        audioSourceStatus = findViewById(R.id.txtAudioSource)
        backupSwitch = findViewById(R.id.switchBackupCapture)
        floatingOverlaySwitch = findViewById(R.id.switchFloatingOverlay)
        installEdgeToEdgeInsets()

        loadSettings()
        ensureFloatingProfileLibrary()
        livePreviewProfileId = prefs.getString("live_preview_profile_id", selectedFloatingProfileId) ?: selectedFloatingProfileId
        if (readFloatingProfileLibrary().none { it.id == livePreviewProfileId }) livePreviewProfileId = selectedFloatingProfileId
        if (!floatingServiceStarted) backupCaptureEnabled = false
        // A revoked overlay permission must never be silently treated as active.
        if (floatingOverlayEnabled && !Settings.canDrawOverlays(this)) {
            floatingOverlayEnabled = false
            prefs.edit().putBoolean("floating_overlay_enabled", false).apply()
        }
        scanDisplayBounds()
        initializeOrClampPosition()
        syncSelectedFloatingProfileFromCurrent()
        setupButtons()
        setupScrollNavigation()
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
        styleResetControls(isLightTheme(), currentAccent())
        buildHorizontalFlowControls()
        buildColorProfileFloatingControls()
        buildFloatingControls()
        buildBarAppearancePresetControls()
        findViewById<View>(R.id.btnAddFloatingProfile).setOnClickListener { addFloatingProfile() }
        buildFloatingProfileList()
        refreshColorSwatches()
        updateLivePreviewProfileButton()
        buildLiveFrequencyLabels()
        updateModeButtons()
        updateEffectButtons()
        updateSingleModeVisibility()
        localizeViewTree(contentRoot)
        updateSpectrumBandLabels(animated = false)
        applyUiTextScale(contentRoot)
        // Re-apply theme and subtle scroll affordances after programmatic panels are attached.
        applyTheme(animated = false)
        installScrollAffordances(mainRoot, mainPageAffordances = true)
        installMainVerticalBubbleIndicator()

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
            val requestedState = intent.getBooleanExtra(
                RemoteViews.EXTRA_CHECKED,
                !backupSwitch.isChecked
            )
            backupSwitch.post {
                backupSwitch.isChecked = requestedState
            }
        }
    }

    override fun onStart() {
        super.onStart()
        activityStartedForPreview = true
        if (previewBars.isNotEmpty()) schedulePreviewAnimationFrame()
        val filter = IntentFilter().apply {
            addAction(FloatingVisualizerService.ACTION_LEVELS)
            addAction(FloatingVisualizerService.ACTION_POSITION)
            addAction(FloatingVisualizerService.ACTION_LAYER_FOCUSED)
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
        val lastInteractedLayerId = prefs.getString(
            "last_interacted_floating_profile_id",
            prefs.getString("selected_floating_profile_id", selectedFloatingProfileId)
        ) ?: selectedFloatingProfileId
        val profileLibrary = readFloatingProfileLibrary()
        val lastInteractedProfile = profileLibrary.firstOrNull { it.id == lastInteractedLayerId }
        if (lastInteractedProfile != null) {
            if (lastInteractedLayerId != selectedFloatingProfileId) {
                selectFloatingProfileFromOverlay(lastInteractedLayerId)
            } else if (lastAppliedFloatingProfileId != lastInteractedLayerId) {
                // A new Activity can load the new ID from SharedPreferences before its
                // controls have been populated from that profile. Apply it once here.
                applyFloatingProfileToControls(lastInteractedProfile, updateService = false)
            }
        }
        val selectedLayerState = readFloatingProfileLibrary().firstOrNull { it.id == selectedFloatingProfileId }
        if (selectedLayerState != null) {
            positionXdp = selectedLayerState.positionXdp.coerceAtLeast(0)
            positionYdp = selectedLayerState.positionYdp.coerceAtLeast(0)
        } else {
            positionXdp = prefs.getInt("position_x", positionXdp).coerceAtLeast(0)
            positionYdp = prefs.getInt("position_y", positionYdp).coerceAtLeast(0)
        }
        // A position drag may have happened while this Activity was stopped.
        // Reconcile the loaded profile state after refreshing persisted coordinates.
        markProfileDirtyFromCurrentState()
        // Keep the saved top-left anchor unchanged during lifecycle/orientation refresh.
    }

    override fun onStop() {
        activityStartedForPreview = false
        cancelPreviewAnimationFrame()
        if (receiverRegistered) {
            unregisterReceiver(levelsReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        activityStartedForPreview = false
        cancelPreviewAnimationFrame()
        updateHandler.removeCallbacks(realtimeUpdate)
        realtimeUpdateScheduled = false
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (themeMode == THEME_SYSTEM) {
            // SYSTEM keeps separate readable text colors for day and night mode.
            themeTextColor = readThemeTextColor(THEME_SYSTEM)
        }
        localizeViewTree(contentRoot)
        applyUiTextScale(contentRoot)
        applyTheme(animated = false)
        refreshFloatingNotificationTheme()
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

    private fun isSystemNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun themeTextPreferenceKey(mode: String): String = when (mode) {
        THEME_GLASS -> "theme_text_color_glass"
        THEME_CUSTOM -> "theme_text_color_custom"
        THEME_SYSTEM -> if (isSystemNightMode()) "theme_text_color_system_dark" else "theme_text_color_system_light"
        THEME_DARK -> "theme_text_color_dark"
        THEME_LIGHT -> "theme_text_color_light"
        else -> "theme_text_color_custom"
    }

    private fun defaultThemeTextColor(mode: String): Int = when (mode) {
        THEME_LIGHT -> Color.parseColor("#24324A")
        THEME_DARK -> Color.parseColor("#F2F5FC")
        THEME_SYSTEM -> if (isSystemNightMode()) Color.parseColor("#F2F5FC") else Color.parseColor("#24324A")
        else -> Color.parseColor("#F5F7FC")
    }

    private fun readThemeTextColor(mode: String): Int {
        val key = themeTextPreferenceKey(mode)
        if (prefs.contains(key)) return prefs.getInt(key, defaultThemeTextColor(mode))

        // Older builds stored one shared SYSTEM color. Migrate it only to the
        // currently active day/night variant; the other variant gets its own
        // contrast-safe default rather than inheriting a possibly unreadable color.
        if (mode == THEME_SYSTEM && prefs.contains("theme_text_color_system")) {
            val legacy = prefs.getInt("theme_text_color_system", defaultThemeTextColor(mode))
            prefs.edit().putInt(key, legacy).apply()
            return legacy
        }

        // Preserve legacy custom colors for the two non-adaptive themes only.
        if ((mode == THEME_GLASS || mode == THEME_CUSTOM) && mode == themeMode && prefs.contains("theme_text_color")) {
            val legacy = prefs.getInt("theme_text_color", defaultThemeTextColor(mode))
            prefs.edit().putInt(key, legacy).apply()
            return legacy
        }
        return defaultThemeTextColor(mode)
    }

    private fun loadSettings() {
        overlayWidthDp = prefs.getInt("width", 320)
        overlayHeightDp = prefs.getInt("height", 360)
        barCount = prefs.getInt("bar_count", 15).coerceIn(1, MAX_BAR_COUNT)
        barWidthDp = prefs.getInt("bar_width", 20)
        barGapDp = prefs.getInt("bar_gap", 3)
        scalePercent = prefs.getInt("scale", 100)
        opacityPercent = prefs.getInt("opacity", 100).coerceIn(0, 100)
        shadowOpacityPercent = prefs.getInt("shadow_opacity", 100).coerceIn(0, 100)
        responsePercent = prefs.getInt("response_speed", 82).coerceIn(0, 100)
        cornerRadiusDp = prefs.getInt("corner_radius", 14)
        isVerticalMode = prefs.getBoolean("vertical", false)
        singleAudioMode = prefs.getString("single_audio_mode", "MUSIC") ?: "MUSIC"
        bassMinHz = prefs.getInt("bass_min_hz", 20).coerceIn(0, 20000)
        bassMaxHz = prefs.getInt("bass_max_hz", 125).coerceIn(0, 20000)
        horizontalAlignment = prefs.getString("horizontal_alignment", "LEFT")
            ?.takeIf { it in setOf("LEFT", "CENTER", "RIGHT") } ?: "LEFT"
        mirrorMode = prefs.getString(
            "horizontal_mirror_mode",
            if (prefs.getBoolean("horizontal_mirror", false)) "HORIZONTAL" else "OFF"
        )?.takeIf { it in setOf("OFF", "VERTICAL", "HORIZONTAL") } ?: "OFF"
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
        themeTextColor = readThemeTextColor(themeMode)
        selectedFloatingProfileId = prefs.getString("selected_floating_profile_id", "root") ?: "root"
        barEffect = prefs.getString("bar_effect", EFFECT_GLOSSY) ?: EFFECT_GLOSSY
        // New-install defaults: Neon is intentionally strong; other looks stay softer.
        glowPercent = if (barEffect == EFFECT_CLEAN) 0 else
            prefs.getInt("glow", defaultGlowForEffect(barEffect)).coerceIn(0, 100)
        appLanguage = prefs.getString("app_language", "en") ?: "en"
        uiScalePercent = prefs.getInt("ui_scale_percent", 100).coerceIn(85, 125)
        loadedProfileName = prefs.getString("profile_base_name", prefs.getString("active_color_profile", null))
        profileDirty = prefs.getBoolean("profile_dirty", false)

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
        .putInt("shadow_opacity", shadowOpacityPercent)
        .putInt("glow", glowPercent)
        .putInt("response_speed", responsePercent)
        .putInt("corner_radius", cornerRadiusDp)
        .putBoolean("vertical", isVerticalMode)
        .putString("single_audio_mode", singleAudioMode)
        .putInt("bass_min_hz", bassMinHz)
        .putInt("bass_max_hz", bassMaxHz)
        .putString("horizontal_alignment", horizontalAlignment)
        .putBoolean("horizontal_mirror", mirrorMode != "OFF") // Legacy key for 2.4.0 profiles.
        .putString("horizontal_mirror_mode", mirrorMode)
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
        .putInt("theme_text_color", themeTextColor)
        .putInt(themeTextPreferenceKey(themeMode), themeTextColor)
        .putString("bar_effect", barEffect)
        .putString("app_language", appLanguage)
        .putInt("ui_scale_percent", uiScalePercent)
        .putBoolean("backup_capture", backupCaptureEnabled)
        .putBoolean("floating_overlay_enabled", floatingOverlayEnabled)
        .putString("colors", spectrumColors.joinToString(",") {
                String.format("#%06X", 0xFFFFFF and it)
        })
        .apply()
        syncSelectedFloatingProfileFromCurrent()
        // Keep the profile manager's Current State pointer synchronized with
        // every setting commit (sliders, modes, colors, direction and position).
        markProfileDirtyFromCurrentState()
        refreshFloatingSceneManagerStatus()
    }

    // -----------------------------------------------------------------
    // SCREEN GEOMETRY
    // -----------------------------------------------------------------

    private fun scanDisplayBounds() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val metrics = android.util.DisplayMetrics()
        // TYPE_APPLICATION_OVERLAY is positioned in display coordinates, not the
        // Activity content area. Use real display metrics on every supported API
        // so the editor and Service calculate identical physical left/right limits.
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidthPx = metrics.widthPixels.coerceAtLeast(1)
        screenHeightPx = metrics.heightPixels.coerceAtLeast(1)
        screenWidthDp = (screenWidthPx / density).toInt().coerceAtLeast(160)
        screenHeightDp = (screenHeightPx / density).toInt().coerceAtLeast(240)
    }

    /** Same scaled and rotated window bounds used by FloatingVisualizerService.updateLayeredRuntime. */
    private fun floatingLayerWindowBoundsPx(profile: FloatingLayerProfile): Pair<Int, Int> {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val scale = profile.scalePercent.coerceIn(1, 500) / 100f
        val requestedWidthDp = (if (profile.verticalMode) profile.barWidthDp else profile.widthDp).coerceAtLeast(1) * scale
        val requestedHeightDp = profile.heightDp.coerceAtLeast(1) * scale
        val rawW = (requestedWidthDp * density).toInt().coerceAtLeast(1)
        val rawH = (requestedHeightDp * density).toInt().coerceAtLeast(1)
        val radians = Math.toRadians(profile.rotationDegrees.toDouble())
        val cosR = abs(kotlin.math.cos(radians))
        val sinR = abs(kotlin.math.sin(radians))
        val rawBoundW = ceil(rawW * cosR + rawH * sinR).toInt().coerceAtLeast(1)
        val rawBoundH = ceil(rawW * sinR + rawH * cosR).toInt().coerceAtLeast(1)
        val displayW = screenWidthPx.coerceAtLeast(1)
        val displayH = screenHeightPx.coerceAtLeast(1)
        val fit = minOf(1f, minOf(displayW.toFloat() / rawBoundW, displayH.toFloat() / rawBoundH))
            .coerceIn(0.0001f, 1f)
        val logicalW = (rawW * fit).toInt().coerceAtLeast(1)
        val logicalH = (rawH * fit).toInt().coerceAtLeast(1)
        val boundW = ceil(logicalW * cosR + logicalH * sinR).toInt().coerceIn(1, displayW)
        val boundH = ceil(logicalW * sinR + logicalH * cosR).toInt().coerceIn(1, displayH)
        return Pair(boundW, boundH)
    }

    /** Clamp coordinates to the exact top-left range accepted by the overlay window. */
    private fun clampFloatingLayerPosition(profile: FloatingLayerProfile): FloatingLayerProfile {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val (boundW, boundH) = floatingLayerWindowBoundsPx(profile)
        // Ceil lets the Service's final pixel clamp reach the exact last pixel even
        // when display density cannot represent that edge as an integer dp value.
        val maxXdp = ceil((screenWidthPx - boundW).coerceAtLeast(0) / density).toInt()
        val maxYdp = ceil((screenHeightPx - boundH).coerceAtLeast(0) / density).toInt()
        return profile.copy(
            positionXdp = profile.positionXdp.coerceIn(0, maxXdp),
            positionYdp = profile.positionYdp.coerceIn(0, maxYdp)
        )
    }

    private fun centerFloatingLayerPosition(profile: FloatingLayerProfile): FloatingLayerProfile {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val (boundW, boundH) = floatingLayerWindowBoundsPx(profile)
        val maxXdp = ceil((screenWidthPx - boundW).coerceAtLeast(0) / density).toInt()
        val maxYdp = ceil((screenHeightPx - boundH).coerceAtLeast(0) / density).toInt()
        return clampFloatingLayerPosition(profile.copy(positionXdp = maxXdp / 2, positionYdp = maxYdp / 2))
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
        barCount = barCount.coerceIn(1, MAX_BAR_COUNT)
        barGapDp = barGapDp.coerceIn(0, 512)
        scalePercent = scalePercent.coerceIn(0, 500)
        opacityPercent = opacityPercent.coerceIn(0, 100)
        shadowOpacityPercent = shadowOpacityPercent.coerceIn(0, 100)
        glowPercent = glowPercent.coerceIn(0, 100)
        cornerRadiusDp = cornerRadiusDp.coerceIn(0, 512)
        bassMinHz = bassMinHz.coerceIn(0, 20000)
        bassMaxHz = bassMaxHz.coerceIn(0, 20000)
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

    private fun visualGapPx(value: Int): Float {
        if (value <= 0) return 0f
        return (value.dp().toFloat() * 0.55f).coerceAtLeast(0.55f)
    }

    private fun glowStyleFactor(effect: String): Float = when (effect) {
        EFFECT_NEON -> 1f
        EFFECT_GLOSSY -> 0.82f
        EFFECT_GLASS -> 0.70f
        EFFECT_CLEAN -> 0.42f
        else -> 0f
    }

    private fun drawTopRoundedColumn(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        rect: android.graphics.RectF,
        requestedRadius: Float
    ) {
        val radius = requestedRadius.coerceAtLeast(0f).coerceAtMost(minOf(rect.width() / 2f, rect.height() / 2f))
        if (radius <= 0.5f) {
            val oldAa = paint.isAntiAlias
            paint.isAntiAlias = false
            canvas.drawRect(rect, paint)
            paint.isAntiAlias = oldAa
            return
        }
        // Round only the upper corners. The lower half is covered by a flush rect,
        // keeping zero-gap neighbouring bars joined without anti-aliased hairlines.
        canvas.drawRoundRect(
            android.graphics.RectF(rect.left, rect.top, rect.right, minOf(rect.bottom, rect.top + radius * 2f)),
            radius, radius, paint
        )
        canvas.drawRect(rect.left, rect.top + radius, rect.right, rect.bottom, paint)
    }

    private fun drawBottomRoundedColumn(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        rect: android.graphics.RectF,
        requestedRadius: Float
    ) {
        val radius = requestedRadius.coerceAtLeast(0f).coerceAtMost(minOf(rect.width() / 2f, rect.height() / 2f))
        if (radius <= 0.5f) {
            val oldAa = paint.isAntiAlias
            paint.isAntiAlias = false
            canvas.drawRect(rect, paint)
            paint.isAntiAlias = oldAa
            return
        }
        canvas.drawRoundRect(
            android.graphics.RectF(rect.left, maxOf(rect.top, rect.bottom - radius * 2f), rect.right, rect.bottom),
            radius, radius, paint
        )
        canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom - radius, paint)
    }

    /**
     * Lightweight glow used only by the in-app Display preview.
     * The Floating Service keeps its full multi-pass glow renderer unchanged.
     * One subdued halo pass here avoids drawing a second full-strength glow stack
     * while the real Floating overlay is rendering at the same time.
     */
    private fun drawPreviewGlow(
        canvas: android.graphics.Canvas,
        paint: android.graphics.Paint,
        rect: android.graphics.RectF,
        color: Int,
        effect: String,
        glowPercent: Int,
        opacityPercent: Int,
        cornerRadius: Float,
        intensityScale: Float = 1f
    ) {
        val strength = (
            glowPercent.coerceIn(0, 100) / 100f *
                glowStyleFactor(effect) *
                intensityScale.coerceIn(0f, 1f) *
                0.58f
            ).coerceIn(0f, 1f)
        if (strength <= 0f || rect.width() <= 0f || rect.height() <= 0f) return

        val maxSpread = minOf(
            8.dp().toFloat() * strength,
            maxOf(2.dp().toFloat(), rect.height() * 0.08f)
        ).coerceAtLeast(0.65f)
        val opacity = opacityPercent.coerceIn(0, 100) / 100f

        // Preserve the bar fill paint exactly. Draw one soft outline pass instead
        // of three nested passes; use scalar Canvas coordinates to avoid a new
        // RectF allocation for every bar on every frame.
        val oldShader = paint.shader
        val oldColor = paint.color
        val oldAlpha = paint.alpha
        val oldStyle = paint.style
        val oldStroke = paint.strokeWidth
        paint.shader = null
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = maxOf(1.dp().toFloat(), maxSpread * 0.62f)
        val alpha = (255f * strength * opacity * 0.34f).toInt().coerceIn(0, 90)
        paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        val radius = cornerRadius + maxSpread
        canvas.drawRoundRect(
            rect.left - maxSpread,
            rect.top - maxSpread * 0.38f,
            rect.right + maxSpread,
            rect.bottom + maxSpread * 0.12f,
            radius,
            radius,
            paint
        )
        paint.style = oldStyle
        paint.strokeWidth = oldStroke
        paint.shader = oldShader
        paint.color = oldColor
        paint.alpha = oldAlpha
    }

    // -----------------------------------------------------------------
    // EXPLICIT SCROLL NAVIGATION
    // -----------------------------------------------------------------

    private fun setupScrollNavigation() {
        modeScrollLeft.contentDescription = if (appLanguage == "id") "Mode sebelumnya" else "Previous visualizer modes"
        modeScrollRight.contentDescription = if (appLanguage == "id") "Mode berikutnya" else "More visualizer modes"
        frequencyScrollLeft.contentDescription = if (appLanguage == "id") "Rentang frekuensi sebelumnya" else "Previous frequency bands"
        frequencyScrollRight.contentDescription = if (appLanguage == "id") "Rentang frekuensi berikutnya" else "More frequency bands"
        modeScrollLeft.setOnClickListener {
            visualizerModeScroll.smoothScrollBy(-(visualizerModeScroll.width * 0.78f).toInt().coerceAtLeast(90.dp()), 0)
        }
        modeScrollRight.setOnClickListener {
            visualizerModeScroll.smoothScrollBy((visualizerModeScroll.width * 0.78f).toInt().coerceAtLeast(90.dp()), 0)
        }
        frequencyScrollLeft.setOnClickListener {
            liveFrequencyScroll.smoothScrollBy(-(liveFrequencyScroll.width * 0.78f).toInt().coerceAtLeast(100.dp()), 0)
        }
        frequencyScrollRight.setOnClickListener {
            liveFrequencyScroll.smoothScrollBy((liveFrequencyScroll.width * 0.78f).toInt().coerceAtLeast(100.dp()), 0)
        }
        visualizerModeScroll.setOnScrollChangeListener { _, _, _, _, _ -> refreshScrollNavigationState() }
        liveFrequencyScroll.setOnScrollChangeListener { _, _, _, _, _ -> refreshScrollNavigationState() }
        visualizerModeScroll.post { refreshScrollNavigationState() }
        liveFrequencyScroll.post { refreshScrollNavigationState() }
        styleScrollArrow(modeScrollLeft)
        styleScrollArrow(modeScrollRight)
        styleScrollArrow(frequencyScrollLeft)
        styleScrollArrow(frequencyScrollRight)
    }

    private fun styleScrollArrow(button: TextView) {
        applyPillStyle(button, false)
        button.gravity = Gravity.CENTER
        button.includeFontPadding = false
        button.textSize = 22f
        button.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        button.setPadding(0, 0, 0, 1.dp())
        button.minWidth = 34.dp()
        button.minHeight = 34.dp()
        button.background = pillDrawable(false, isLightTheme(), currentAccent(), 11)
        button.elevation = 1.dp().toFloat()
    }

    private fun refreshScrollNavigationState() {
        fun update(button: TextView, canScroll: Boolean) {
            button.isEnabled = canScroll
            button.alpha = if (canScroll) 0.96f else 0.48f
        }
        val modeRange = horizontalScrollRange(visualizerModeScroll)
        update(modeScrollLeft, visualizerModeScroll.scrollX > 2.dp())
        update(modeScrollRight, modeRange > 2.dp() && visualizerModeScroll.scrollX < modeRange - 2.dp())
        val frequencyRange = horizontalScrollRange(liveFrequencyScroll)
        val frequencyVisible = liveFrequencyControlsRow.visibility == View.VISIBLE
        update(frequencyScrollLeft, frequencyVisible && liveFrequencyScroll.scrollX > 2.dp())
        update(frequencyScrollRight, frequencyVisible && frequencyRange > 2.dp() && liveFrequencyScroll.scrollX < frequencyRange - 2.dp())
    }

    // -----------------------------------------------------------------
    // BUTTONS / THEME / EFFECTS
    // -----------------------------------------------------------------

    private fun setupButtons() {
        findViewById<View>(R.id.btnModeVertical).setOnClickListener { selectMode(true) }
        findViewById<View>(R.id.btnModeHorizontal).setOnClickListener { selectMode(false) }
        findViewById<View>(R.id.btnColorStudio).setOnClickListener { showColorStudio() }
        findViewById<View>(R.id.btnSaveFloatingScene).setOnClickListener { promptSaveFloatingSceneAs() }
        findViewById<View>(R.id.btnManageFloatingScenes).setOnClickListener { showFloatingSceneManager() }
        findViewById<View>(R.id.btnExportFloatingScene).setOnClickListener { showFloatingSceneExportPicker() }
        findViewById<View>(R.id.btnImportFloatingScene).setOnClickListener { launchFloatingSceneImport() }
        findViewById<View>(R.id.btnLivePreviewProfile).setOnClickListener { showLivePreviewProfilePicker() }
        findViewById<View>(R.id.btnStart).setOnClickListener { toggleStartStop() }
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
        findViewById<View>(R.id.btnThemeDark).setOnClickListener { setThemeMode(THEME_DARK) }
        findViewById<View>(R.id.btnThemeLight).setOnClickListener { setThemeMode(THEME_LIGHT) }
        findViewById<View>(R.id.btnThemeAccent).setOnClickListener { showAccentPicker() }

        findViewById<View>(R.id.btnEffectClean).setOnClickListener { setBarEffect(EFFECT_CLEAN) }
        findViewById<View>(R.id.btnEffectGlossy).setOnClickListener { setBarEffect(EFFECT_GLOSSY) }
        findViewById<View>(R.id.btnEffectNeon).setOnClickListener { setBarEffect(EFFECT_NEON) }
        findViewById<View>(R.id.btnEffectGlass).setOnClickListener { setBarEffect(EFFECT_GLASS) }

    }

    private fun setThemeMode(mode: String) {
        if (themeMode != mode) {
            prefs.edit().putInt(themeTextPreferenceKey(themeMode), themeTextColor).apply()
            themeMode = mode
            themeTextColor = readThemeTextColor(mode)
        }
        saveSettings()
        applyTheme(animated = true)
        updateModeButtons()
        updateEffectButtons()
        updateSingleModeButtons()
        updateHorizontalFlowButtons()
        updateSpectrumBandLabels(animated = true)
        scheduleBarAppearancePresetRefresh()
        refreshFloatingNotificationTheme()
    }

    private fun isLightTheme(): Boolean {
        return when (themeMode) {
            THEME_LIGHT -> true
            THEME_SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
            THEME_DARK, THEME_GLASS, THEME_CUSTOM -> false
            else -> false
        }
    }

    private fun currentAccent(): Int {
        return when (themeMode) {
            THEME_GLASS -> Color.parseColor("#9B7BFF")
            THEME_SYSTEM -> if (isSystemNightMode()) Color.parseColor("#A99BFF") else Color.parseColor("#6656C8")
            THEME_DARK -> Color.parseColor("#8EA5FF")
            THEME_LIGHT -> Color.parseColor("#7055C8")
            THEME_CUSTOM -> themeAccent
            else -> Color.parseColor("#9B7BFF")
        }
    }

    private fun floatingProfileRowDrawable(selected: Boolean, light: Boolean, accent: Int): GradientDrawable {
        // Profile rows are selectors too: unselected follows the active theme,
        // selected gets a deliberate inverse/brand surface with a clear border.
        return pillDrawable(selected, light, accent, cornerRadiusDp = 16)
    }

    private fun styleFloatingProfileRows(light: Boolean, accent: Int) {
        if (!::floatingProfilesList.isInitialized) return
        for (index in 0 until floatingProfilesList.childCount) {
            val row = floatingProfilesList.getChildAt(index) as? LinearLayout ?: continue
            val id = row.tag as? String ?: continue
            val selected = id == selectedFloatingProfileId
            row.background = floatingProfileRowDrawable(selected, light, accent)
            for (childIndex in 0 until row.childCount) {
                val child = row.getChildAt(childIndex)
                if (child is TextView) {
                    val text = child.text?.toString().orEmpty()
                    when {
                        text.startsWith("●") || text.startsWith("○") ->
                            child.setTextColor(pillForegroundColor(light, selected))
                        text == "⠿" -> applyPillStyle(child, false, light, accent)
                        else -> applyPillStyle(child, false, light, accent, enabled = child.isEnabled)
                    }
                }
            }
        }
    }

    private fun previewSurfaceDrawable(light: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (light) Color.parseColor("#AFBAC8") else Color.parseColor("#101827"))
        cornerRadius = 15.dp().toFloat()
        setStroke(1.dp(), if (light) Color.parseColor("#909EAF") else Color.parseColor("#40506A"))
    }

    private fun applyTheme(animated: Boolean) {
        val light = isLightTheme()
        val accent = currentAccent()
        val rootColor = if (light) Color.parseColor("#EEF2F9") else Color.parseColor("#080D18")
        mainShell.setBackgroundColor(rootColor)
        mainRoot.setBackgroundColor(rootColor)

        val cards = intArrayOf(
            R.id.cardBackup, R.id.cardTheme, R.id.cardPreview, R.id.cardMode,
            R.id.cardColors, R.id.cardEffects, R.id.cardFloatingSceneManager,
            R.id.cardFloatingOverlay, R.id.cardFloatingProfiles
        )
        for (id in cards) {
            findViewById<View>(id)?.background = cardDrawable(light, accent)
        }

        styleDynamicTree(contentRoot, light, accent)
        if (::barsContainer.isInitialized) {
            barsContainer.background = previewSurfaceDrawable(light)
            barsContainer.clipToOutline = true
        }
        singleModeCard?.let {
            it.background = cardDrawable(light, accent)
            styleDynamicTree(it, light, accent)
        }
        horizontalFlowPanel?.let {
            it.background = cardDrawable(light, accent)
            styleDynamicTree(it, light, accent)
        }
        styleDynamicTree(controlsContainer, light, accent)
        if (::floatingProfilesList.isInitialized) {
            styleDynamicTree(floatingProfilesList, light, accent)
            styleFloatingProfileRows(light, accent)
        }
        if (::colorProfileFloatingControls.isInitialized) styleDynamicTree(colorProfileFloatingControls, light, accent)
        if (::liveFrequencyLabels.isInitialized) {
            styleDynamicTree(liveFrequencyLabels, light, accent)
            styleLiveFrequencyLabels(light, accent)
        }
        styleResetControls(light, accent)
        // These profile selectors are created dynamically. styleDynamicTree updates
        // their text, but their old GradientDrawable would otherwise retain the
        // dark surface from the theme active at creation time.
        styleFloatingProfileSectionControls(light, accent)
        if (::livePreviewProfileButton.isInitialized) updateLivePreviewProfileButton()
        if (::modeScrollLeft.isInitialized) {
            listOf(modeScrollLeft, modeScrollRight, frequencyScrollLeft, frequencyScrollRight).forEach { styleScrollArrow(it) }
        }
        val badgeState = when {
            engineStatus.text.toString().contains("STARTING", ignoreCase = true) -> "STARTING"
            engineStatus.text.toString().contains("LIVE", ignoreCase = true) && engineStatus.text.toString().contains("ON", ignoreCase = true) -> "ON"
            else -> "OFF"
        }
        setEngineStatusBadge(badgeState)
        audioSourceStatus.setTextColor(if (light) Color.parseColor("#087F62") else Color.parseColor("#82DCC3"))
        styleThemeButton(R.id.btnThemeGlass, themeMode == THEME_GLASS, light, accent)
        styleThemeButton(R.id.btnThemeCustom, themeMode == THEME_CUSTOM, light, accent)
        styleThemeButton(R.id.btnThemeSystem, themeMode == THEME_SYSTEM, light, accent)
        styleThemeButton(R.id.btnThemeDark, themeMode == THEME_DARK, light, accent)
        styleThemeButton(R.id.btnThemeLight, themeMode == THEME_LIGHT, light, accent)

        // Built-in themes own their palettes. The accent editor is visible only
        // for Custom so old custom colors cannot leak into Glossy/System/Dark/Light.
        val accentButton = findViewById<TextView>(R.id.btnThemeAccent)
        accentButton.visibility = if (themeMode == THEME_CUSTOM) View.VISIBLE else View.GONE
        applyPillStyle(accentButton, false, light, accent)
        findViewById<View>(R.id.btnColorStudio).background = accentDrawable(accent)
        findViewById<View>(R.id.btnAddFloatingProfile).background = accentDrawable(accent)
        findViewById<TextView>(R.id.btnAddFloatingProfile).setTextColor(textOnAccent(accent))
        findViewById<View>(R.id.btnSaveFloatingScene).background = accentDrawable(accent)
        findViewById<TextView>(R.id.btnSaveFloatingScene).setTextColor(textOnAccent(accent))
        listOf(
            R.id.btnManageFloatingScenes,
            R.id.btnExportFloatingScene,
            R.id.btnImportFloatingScene
        ).forEach { id ->
            findViewById<TextView>(id)?.let { applyPillStyle(it, false, light, accent) }
        }
        refreshFloatingSceneManagerStatus()
        findViewById<View>(R.id.btnStart).background = accentDrawable(accent)
        findViewById<TextView>(R.id.btnColorStudio).setTextColor(textOnAccent(accent))
        findViewById<TextView>(R.id.btnStart).setTextColor(textOnAccent(accent))
        findViewById<TextView>(R.id.btnGeneralSettings).let { applyPillStyle(it, false, light, accent) }

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
        refreshScrollEdgeGlassFades()
        mainRoot.post { mainRoot.requestApplyInsets() }
        updateStartStopButton()

    }

    private fun colorLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92 else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    private fun contrastAgainst(color: Int, background: Int): Double {
        val first = colorLuminance(color)
        val second = colorLuminance(background)
        val light = maxOf(first, second)
        val dark = minOf(first, second)
        return (light + 0.05) / (dark + 0.05)
    }

    private fun bestContrastTextForSurfaces(firstSurface: Int, secondSurface: Int): Int {
        val white = Color.WHITE
        val dark = Color.parseColor("#17243A")
        val whiteMinimum = minOf(contrastAgainst(white, firstSurface), contrastAgainst(white, secondSurface))
        val darkMinimum = minOf(contrastAgainst(dark, firstSurface), contrastAgainst(dark, secondSurface))
        return if (whiteMinimum >= darkMinimum) white else dark
    }

    private fun textOnAccent(accent: Int): Int {
        return bestContrastTextForSurfaces(lighten(accent, 0.16f), darken(accent, 0.18f))
    }

    private fun bestContrastText(background: Int): Int = bestContrastTextForSurfaces(background, background)

    private fun customSurfaceTextColor(): Int {
        val top = lighten(themeSurfaceColor, 0.08f)
        val bottom = darken(themeSurfaceColor, 0.08f)
        val themeMinimum = minOf(contrastAgainst(themeTextColor, top), contrastAgainst(themeTextColor, bottom))
        return if (themeMinimum >= 4.5) themeTextColor else bestContrastTextForSurfaces(top, bottom)
    }

    /** Selected Custom control must remain visually distinct even when the user's
     * accent and surface colors are nearly identical. */
    private fun customSelectedPillColor(): Int {
        val accent = currentAccent()
        val candidates = listOf(
            accent,
            darken(accent, 0.34f),
            lighten(accent, 0.38f),
            darken(accent, 0.58f),
            lighten(accent, 0.62f)
        )
        fun minimumBestContrast(base: Int): Double {
            val top = lighten(base, 0.10f)
            val bottom = darken(base, 0.12f)
            val whiteMin = minOf(contrastAgainst(Color.WHITE, top), contrastAgainst(Color.WHITE, bottom))
            val darkMin = minOf(contrastAgainst(Color.parseColor("#17243A"), top), contrastAgainst(Color.parseColor("#17243A"), bottom))
            return maxOf(whiteMin, darkMin)
        }
        val best = candidates.maxByOrNull { minimumBestContrast(it) } ?: accent
        // Keep the configured accent when it already reads well against the
        // surface. Otherwise use the closest contrast-safe tint from its own hue.
        return if (contrastAgainst(accent, themeSurfaceColor) >= 1.45 && minimumBestContrast(accent) >= 4.5) accent else best
    }

    /** Foreground paired with the exact selector state/background. */
    private fun pillForegroundColor(light: Boolean, selected: Boolean = false): Int {
        return when (themeMode) {
            THEME_LIGHT -> if (selected) Color.WHITE else Color.parseColor("#26344B")
            THEME_DARK -> if (selected) Color.parseColor("#17243A") else Color.parseColor("#F2F5FC")
            THEME_SYSTEM -> if (light) {
                if (selected) Color.WHITE else Color.parseColor("#26344B")
            } else {
                if (selected) Color.parseColor("#17243A") else Color.parseColor("#F2F5FC")
            }
            THEME_CUSTOM -> if (selected) {
                val selectedSurface = customSelectedPillColor()
                bestContrastTextForSurfaces(lighten(selectedSurface, 0.10f), darken(selectedSurface, 0.12f))
            } else customSurfaceTextColor()
            THEME_GLASS -> if (selected) textOnAccent(currentAccent()) else Color.parseColor("#F5F7FC")
            else -> if (selected) Color.WHITE else Color.parseColor("#F2F5FC")
        }
    }

    private fun contentThemeTextColor(light: Boolean): Int {
        if (themeMode == THEME_CUSTOM) return customSurfaceTextColor()
        val backgroundReference = if (light) Color.WHITE else Color.parseColor("#101A2B")
        if (contrastAgainst(themeTextColor, backgroundReference) < 4.5) return bestContrastText(backgroundReference)
        return themeTextColor
    }

    private fun themedTextColor(view: TextView, light: Boolean): Int {
        if (view.tag == "floatingdj:content-surface") return contentThemeTextColor(light)
        if (view.isClickable || view.isFocusable) {
            val selected = view.getTag(R.id.tag_selector_selected) as? Boolean ?: false
            return pillForegroundColor(light, selected)
        }
        return contentThemeTextColor(light)
    }

    private fun applyPillStyle(
        view: TextView,
        selected: Boolean,
        light: Boolean = isLightTheme(),
        accent: Int = currentAccent(),
        enabled: Boolean = view.isEnabled
    ) {
        view.setTag(R.id.tag_selector_selected, selected)
        view.background = pillDrawable(selected, light, accent)
        view.setTextColor(if (enabled) pillForegroundColor(light, selected) else 0x668A95A8)
    }

    private fun styleTextTree(group: ViewGroup, light: Boolean) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is TextView && child !is EditText && child !== engineStatus && child !== audioSourceStatus) {
                child.setTextColor(themedTextColor(child, light))
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
                is TextView -> if (child !== engineStatus && child !== audioSourceStatus) {
                    child.setTextColor(themedTextColor(child, light))
                }
            }
            if (child is ViewGroup) styleDynamicTree(child, light, accent)
        }
    }

    private fun styleLiveFrequencyLabels(light: Boolean, accent: Int) {
        if (!::liveFrequencyLabels.isInitialized) return
        for (index in 0 until liveFrequencyLabels.childCount) {
            val label = liveFrequencyLabels.getChildAt(index) as? TextView ?: continue
            applyPillStyle(label, false, light, accent)
        }
    }

    private fun styleResetControls(light: Boolean, accent: Int) {
        findViewById<TextView>(R.id.btnResetBarAppearance)?.let { applyPillStyle(it, false, light, accent) }
        singleRangeResetButton?.let { applyPillStyle(it, false, light, accent) }
    }

    private fun styleThemeButton(id: Int, active: Boolean, light: Boolean, accent: Int) {
        applyPillStyle(findViewById(id), active, light, accent)
    }

    private fun pillDrawable(
        active: Boolean,
        light: Boolean,
        accent: Int,
        cornerRadiusDp: Int = 13
    ): GradientDrawable {
        val colors = when (themeMode) {
            THEME_LIGHT -> if (active) {
                intArrayOf(Color.parseColor("#46566D"), Color.parseColor("#273449"))
            } else {
                intArrayOf(Color.WHITE, Color.parseColor("#E8EEF6"))
            }
            THEME_DARK -> if (active) {
                intArrayOf(Color.parseColor("#F4F6FA"), Color.parseColor("#DCE4EF"))
            } else {
                intArrayOf(Color.parseColor("#263449"), Color.parseColor("#111B2A"))
            }
            THEME_SYSTEM -> if (light) {
                if (active) intArrayOf(Color.parseColor("#46566D"), Color.parseColor("#273449"))
                else intArrayOf(Color.WHITE, Color.parseColor("#E8EEF6"))
            } else {
                if (active) intArrayOf(Color.parseColor("#F4F6FA"), Color.parseColor("#DCE4EF"))
                else intArrayOf(Color.parseColor("#263449"), Color.parseColor("#111B2A"))
            }
            THEME_CUSTOM -> if (active) {
                val selectedSurface = customSelectedPillColor()
                intArrayOf(lighten(selectedSurface, 0.10f), darken(selectedSurface, 0.12f))
            } else {
                intArrayOf(lighten(themeSurfaceColor, 0.08f), darken(themeSurfaceColor, 0.08f))
            }
            THEME_GLASS -> if (active) {
                intArrayOf(lighten(accent, 0.14f), darken(accent, 0.20f))
            } else {
                intArrayOf(Color.parseColor("#29364A"), Color.parseColor("#111C2C"))
            }
            else -> if (active) intArrayOf(Color.parseColor("#46566D"), Color.parseColor("#273449"))
            else intArrayOf(Color.parseColor("#263449"), Color.parseColor("#111B2A"))
        }
        val stroke = when {
            active && themeMode == THEME_CUSTOM -> themeEdgeColor
            active -> Color.argb(230, Color.red(accent), Color.green(accent), Color.blue(accent))
            themeMode == THEME_CUSTOM -> Color.argb(210, Color.red(themeEdgeColor), Color.green(themeEdgeColor), Color.blue(themeEdgeColor))
            light -> 0x668596AB
            else -> 0x667D91B8
        }
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors).apply {
            cornerRadius = cornerRadiusDp.dp().toFloat()
            setStroke(if (active) 2.dp() else 1.dp(), stroke)
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

    private fun defaultGlowForEffect(effect: String): Int = when (effect) {
        EFFECT_CLEAN -> 0
        EFFECT_GLOSSY -> 50
        EFFECT_NEON -> 100
        EFFECT_GLASS -> 38
        else -> 50
    }

    private fun storeSelectedEffectGlow(effect: String, value: Int) {
        val profiles = readFloatingProfileLibrary()
        val index = profiles.indexOfFirst { it.id == selectedFloatingProfileId }
        if (index < 0) return
        val profile = profiles[index]
        val values = profile.effectGlowValues.toMutableMap()
        values[effect] = value.coerceIn(0, 100)
        profiles[index] = profile.copy(effectGlowValues = values)
        writeFloatingProfileLibrary(profiles)
    }

    private fun selectedEffectGlow(effect: String): Int {
        val selected = readFloatingProfileLibrary().firstOrNull { it.id == selectedFloatingProfileId }
        return selected?.effectGlowValues?.get(effect)?.coerceIn(0, 100) ?: defaultGlowForEffect(effect)
    }

    private fun updateBarAppearanceModeAvailability() {
        fun setEnabled(name: String, enabled: Boolean) {
            val binding = sliderBindings[name] ?: return
            binding.seek.isEnabled = enabled
            binding.input.isEnabled = enabled
            val alpha = if (enabled) 1f else 0.38f
            binding.seek.alpha = alpha
            binding.input.alpha = alpha
        }
        // Width and bar count are Horizontal-only; Single uses Bar Thickness + Height.
        setEnabled("HORIZONTAL WIDTH", !isVerticalMode)
        setEnabled("HORIZONTAL BAR COUNT", !isVerticalMode)
        // Clean is explicitly glow-free; keep the control visible but disabled at 0%.
        setEnabled("GLOW INTENSITY", barEffect != EFFECT_CLEAN)
    }

    private fun setBarEffect(effect: String) {
        if (effect !in setOf(EFFECT_CLEAN, EFFECT_GLOSSY, EFFECT_NEON, EFFECT_GLASS)) return
        if (effect == barEffect) return
        storeSelectedEffectGlow(barEffect, glowPercent)
        clearSelectedAppearancePreset()
        barEffect = effect
        glowPercent = if (effect == EFFECT_CLEAN) 0 else selectedEffectGlow(effect)
        saveSettings()
        updateEffectButtons()
        syncSliderValue("GLOW INTENSITY", glowPercent)
        updateBarAppearanceModeAvailability()
        updateFloatingService()
    }

    private fun updateEffectButtons() {
        val light = isLightTheme()
        val ids = listOf(R.id.btnEffectClean, R.id.btnEffectGlossy, R.id.btnEffectNeon, R.id.btnEffectGlass)
        val styles = listOf(EFFECT_CLEAN, EFFECT_GLOSSY, EFFECT_NEON, EFFECT_GLASS)
        ids.forEachIndexed { index, id ->
            val view = findViewById<TextView>(id)
            val active = barEffect == styles[index]
            applyPillStyle(view, active, light, currentAccent())
        }
    }

    // -----------------------------------------------------------------
    // MODE / LIVE PREVIEW
    // -----------------------------------------------------------------

    private fun selectMode(vertical: Boolean) {
        isVerticalMode = vertical
        invalidateLivePreviewProfileCache()
        scanDisplayBounds()
        clampPosition()
        updateModeButtons()
        updateSingleModeVisibility()
        updateHorizontalFlowButtons()
        updateBarAppearanceModeAvailability()
        updateSpectrumBandLabels(animated = true)
        buildSpectrumBars()
        buildLiveFrequencyLabels()
        refreshScrollNavigationState()
        saveSettings()
        updateFloatingService()
    }

    private fun updateModeButtons() {
        val light = isLightTheme()
        val verticalActive = isVerticalMode
        applyPillStyle(findViewById(R.id.btnModeVertical), verticalActive, light, currentAccent())
        applyPillStyle(findViewById(R.id.btnModeHorizontal), !verticalActive, light, currentAccent())
        modeComingSoon.apply {
            text = if (appLanguage == "id") "SEGERA HADIR" else "COMING SOON"
            isEnabled = false
            isClickable = false
            alpha = 0.38f
            applyPillStyle(this, false, light, currentAccent(), enabled = false)
            setTextColor(if (light) Color.parseColor("#8591A4") else Color.parseColor("#8391A7"))
        }
        visualizerModeScroll.post {
            val content = visualizerModeScroll.getChildAt(0) as? ViewGroup ?: return@post
            val selected = content.getChildAt(if (verticalActive) 0 else 1) ?: return@post
            val left = selected.left
            val right = selected.right
            when {
                left < visualizerModeScroll.scrollX -> visualizerModeScroll.smoothScrollTo(left, 0)
                right > visualizerModeScroll.scrollX + visualizerModeScroll.width ->
                    visualizerModeScroll.smoothScrollTo((right - visualizerModeScroll.width).coerceAtLeast(0), 0)
            }
            refreshScrollNavigationState()
        }
    }

    private fun invalidateLivePreviewProfileCache() {
        livePreviewProfileRevision++
        livePreviewProfileCache = null
        livePreviewProfileCacheRevision = -1L
    }

    private fun livePreviewProfile(): FloatingLayerProfile {
        if (livePreviewProfileCacheRevision == livePreviewProfileRevision) {
            livePreviewProfileCache?.let { return it }
        }
        val profiles = readFloatingProfileLibrary()
        val selected = profiles.firstOrNull { it.id == selectedFloatingProfileId }
        // The selected layer is represented by controls currently on screen. This
        // prevents stale persisted mode/count data from surviving a quick profile edit.
        val resolved = if (livePreviewProfileId == selectedFloatingProfileId && selected != null) {
            currentFloatingLayerSnapshot(selected.id, selected.name, selected.isDefault, selected)
        } else {
            profiles.firstOrNull { it.id == livePreviewProfileId }
                ?: selected
                ?: profiles.firstOrNull()
                ?: currentFloatingLayerSnapshot()
        }
        livePreviewProfileCacheRevision = livePreviewProfileRevision
        livePreviewProfileCache = resolved
        return resolved
    }

    private fun styleFloatingProfileSectionControls(light: Boolean, accent: Int) {
        floatingProfileSectionPickerButtons.removeAll { it.parent == null }
        // These three pickers show the currently targeted Floating Profile,
        // therefore they remain visually marked as the active selection.
        floatingProfileSectionPickerButtons.forEach { button ->
            applyPillStyle(button, true, light, accent)
        }
        floatingProfileSectionCopyButtons.removeAll { it.parent == null }
        floatingProfileSectionCopyButtons.forEach { button ->
            applyPillStyle(button, false, light, accent)
        }
    }

    private fun updateLivePreviewProfileButton() {
        if (!::livePreviewProfileButton.isInitialized) return
        val profile = livePreviewProfile()
        livePreviewProfileButton.text = "LIVE PREVIEW · ${profile.name.uppercase(java.util.Locale.US)}   ⌄"
        applyPillStyle(livePreviewProfileButton, true)
    }

    private fun showLivePreviewProfilePicker() {
        val profiles = readFloatingProfileLibrary()
        if (profiles.isEmpty()) return
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 9.dp(), 8.dp(), 8.dp())
            addView(subtitle(if (appLanguage == "id") "Pilih layer untuk dipantau. Ini hanya mengganti Live Preview, bukan profile yang sedang diedit." else "Choose a layer to monitor. This changes Live Preview only, not the profile currently being edited."))
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false; addView(list) }
        body.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = 9.dp() })
        val rows = profiles.map { profile ->
            val active = profile.id == livePreviewProfileId
            glassButton("${if (active) "●" else "○"}   ${profile.name}").apply {
                gravity = Gravity.CENTER_VERTICAL
                textSize = 11f
                applyPillStyle(this, active)
                list.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 46.dp()).apply { bottomMargin = 6.dp() })
            }
        }
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val close = glassButton(if (appLanguage == "id") "TUTUP" else "CLOSE")
        footer.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()))
        val dialog = showGlassStudioDialog(if (appLanguage == "id") "LIVE SPECTRUM PROFILE" else "LIVE SPECTRUM PROFILE", body, footer, 0.92f, 0.72f)
        rows.forEachIndexed { index, row ->
            row.setOnClickListener {
                livePreviewProfileId = profiles[index].id
                invalidateLivePreviewProfileCache()
                prefs.edit().putString("live_preview_profile_id", livePreviewProfileId).apply()
                updateLivePreviewProfileButton()
                buildSpectrumBars()
                buildLiveFrequencyLabels()
                updateSpectrumBandLabels(animated = true)
                if (latestSpectrumLevels.isNotEmpty()) updatePreviewLevels(latestSpectrumLevels)
                dialog.dismiss()
            }
        }
        close.setOnClickListener { dialog.dismiss() }
    }

    private fun liveBarLayoutKey(profile: FloatingLayerProfile): String =
        listOf(
            profile.id, profile.verticalMode, profile.barCount, profile.widthDp, profile.barWidthDp,
            profile.gapDp, profile.heightDp, profile.scalePercent, profile.rotationDegrees,
            screenWidthDp, screenHeightDp
        ).joinToString("|")

    /**
     * Resolve the exact logical content size used by the floating layer renderer,
     * including scale, rotation and display fit. Live Spectrum may present a compact
     * height, but it must never impose an arbitrary 64dp cap on bar thickness.
     */
    private fun previewFloatingContentSize(profile: FloatingLayerProfile): Pair<Int, Int> {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val scale = profile.scalePercent.coerceIn(1, 500) / 100f
        val requestedWidthDp = (if (profile.verticalMode) profile.barWidthDp else profile.widthDp).coerceAtLeast(1) * scale
        val requestedHeightDp = profile.heightDp.coerceAtLeast(1) * scale
        val rawW = (requestedWidthDp * density).toInt().coerceAtLeast(1)
        val rawH = (requestedHeightDp * density).toInt().coerceAtLeast(1)
        val radians = Math.toRadians(profile.rotationDegrees.toDouble())
        val cosR = abs(kotlin.math.cos(radians))
        val sinR = abs(kotlin.math.sin(radians))
        val rawBoundW = ceil(rawW * cosR + rawH * sinR).toInt().coerceAtLeast(1)
        val rawBoundH = ceil(rawW * sinR + rawH * cosR).toInt().coerceAtLeast(1)
        val fit = minOf(1f,
            screenWidthPx.coerceAtLeast(1).toFloat() / rawBoundW,
            screenHeightPx.coerceAtLeast(1).toFloat() / rawBoundH
        ).coerceIn(0.0001f, 1f)
        val contentWidthPx = (rawW * fit).toInt().coerceAtLeast(1)
        val contentHeightPx = (rawH * fit).toInt().coerceAtLeast(1)
        val heightLimitPx = (barsContainer.height.takeIf { it > 0 } ?: 158.dp()) - 20.dp()
        // Keep the Live Spectrum card compact while preserving the actual bar width.
        // Its height is only a monitor viewport; gap and body geometry remain in dp.
        val previewHeightPx = minOf(contentHeightPx, heightLimitPx.coerceAtLeast(92.dp()))
            .coerceAtLeast(1)
        return Pair(contentWidthPx, previewHeightPx)
    }

    /** Logical horizontal overlay width, matching updateLayeredRuntime's scaled/rotated fit. */
    private fun previewOverlayWidthPx(profile: FloatingLayerProfile): Int =
        previewFloatingContentSize(profile).first

    private fun liveFrequencyKey(profile: FloatingLayerProfile): String =
        listOf(profile.id, profile.verticalMode, profile.barCount, profile.horizontalAlignment, profile.mirrorMode).joinToString("|")

    /**
     * Refresh only the presentation pieces affected by the selected profile edit.
     * The audio bank and FFT data are never recreated; bar views are rebuilt only
     * when the count actually changes. This keeps HZ labels, display bars and the
     * floating renderer in sync without a high-frequency layout churn.
     */
    private fun refreshLivePreviewAfterProfileEdit() {
        if (livePreviewProfileId != selectedFloatingProfileId) return
        val profile = livePreviewProfile()
        val layoutKey = liveBarLayoutKey(profile)
        if (layoutKey != livePreviewBarLayoutSignature) {
            val expectedCount = if (profile.verticalMode) 1 else profile.barCount.coerceIn(1, MAX_BAR_COUNT)
            if (previewBars.size != expectedCount) {
                buildSpectrumBars()
            } else {
                adjustPreviewBarWidths()
            }
            livePreviewBarLayoutSignature = layoutKey
        }

        val frequencyKey = liveFrequencyKey(profile)
        if (frequencyKey != livePreviewFrequencySignature) {
            buildLiveFrequencyLabels()
            livePreviewFrequencySignature = frequencyKey
        }

        // The three live status labels are mode-dependent even when the bar count
        // and frequency key happen to stay unchanged. Always refresh their state
        // on every selected-profile edit so Single/Horizontal never waits for the
        // user to switch away and return to the layer.
        updateSpectrumBandLabels(animated = false)
        updateLivePreviewProfileButton()
        previewBars.forEach { it.invalidate() }
        if (latestSpectrumLevels.isNotEmpty()) {
            updateSpectrumBandLabelsFromLevels(latestSpectrumLevels, profile)
        }
    }

    private fun buildSpectrumBars() {
        invalidateLivePreviewProfileCache()
        val profile = livePreviewProfile()
        val vertical = profile.verticalMode
        val count = if (vertical) 1 else profile.barCount.coerceIn(1, MAX_BAR_COUNT)
        barsContainer.orientation = LinearLayout.HORIZONTAL
        // Keep the live monitor centered; FLOW affects band order, not group gravity.
        barsContainer.gravity = previewHorizontalGravity()

        // Reuse existing bar views and only grow/shrink the tail. Recreating 64 Views
        // for every small bar-count edit causes avoidable GC and frame drops on low-RAM phones.
        while (previewBars.size > count) {
            val last = previewBars.removeAt(previewBars.lastIndex)
            barsContainer.removeView(last)
        }
        while (previewBars.size < count) {
            val index = previewBars.size
            val bar = PreviewBarView(this, index)
            previewBars.add(bar)
            barsContainer.addView(bar, LinearLayout.LayoutParams(6.dp(), 136.dp()))
        }
        val (singlePreviewWidth, singlePreviewHeight) = previewFloatingContentSize(profile)
        previewBars.forEach { bar ->
            val params = bar.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(if (vertical) singlePreviewWidth else 6.dp(), 136.dp())
            params.height = if (vertical) singlePreviewHeight else 136.dp()
            if (vertical) params.width = singlePreviewWidth
            bar.layoutParams = params
            bar.invalidate()
        }
        livePreviewBarLayoutSignature = liveBarLayoutKey(profile)
        barsContainer.post { adjustPreviewBarWidths() }
    }

    private fun setLiveFrequencyStripVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        liveFrequencyControlsRow.visibility = visibility
        // The custom thumb is inserted as a sibling after liveFrequencyControlsRow,
        // so it must be hidden explicitly when Single mode hides the row itself.
        (liveFrequencyScroll.getTag(R.id.tag_scroll_indicator) as? View)?.visibility = visibility
        (liveFrequencyScroll.getTag(R.id.tag_scroll_edge_glass) as? ScrollEdgeGlassFadeDrawable)?.invalidateSelf()
        refreshScrollNavigationState()
    }

    private fun buildLiveFrequencyLabels() {
        if (!::liveFrequencyLabels.isInitialized) return
        invalidateLivePreviewProfileCache()
        val profile = livePreviewProfile()
        val showFrequencyStrip = !isVerticalMode && !profile.verticalMode
        if (!showFrequencyStrip) {
            // In Single Display mode, neither the frequency row nor its detached
            // scroll thumb/glass affordance should remain visible.
            setLiveFrequencyStripVisible(false)
            livePreviewFrequencySignature = liveFrequencyKey(profile)
            return
        }
        setLiveFrequencyStripVisible(true)
        val count = profile.barCount.coerceIn(1, MAX_BAR_COUNT)

        // Reuse existing TextViews when count changes. Only append or remove the
        // difference, then update each band's Hz range in place.
        while (liveFrequencyLabels.childCount > count) {
            liveFrequencyLabels.removeViewAt(liveFrequencyLabels.childCount - 1)
        }
        while (liveFrequencyLabels.childCount < count) {
            val label = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 8f
                maxLines = 2
                includeFontPadding = false
                setPadding(3.dp(), 4.dp(), 3.dp(), 4.dp())
                layoutParams = LinearLayout.LayoutParams(76.dp(), 42.dp()).apply {
                    marginStart = 2.dp()
                    marginEnd = 3.dp()
                }
            }
            liveFrequencyLabels.addView(label)
        }
        for (index in 0 until count) {
            val range = previewBandFrequencyRange(index, count, profile)
            val label = liveFrequencyLabels.getChildAt(index) as? TextView ?: continue
            label.text = String.format(java.util.Locale.US, "%02d\n%s", index + 1, formatHzRange(range.first, range.second))
            label.contentDescription = previewBandFrequencyDescription(index, count, profile)
            label.background = pillDrawable(false, isLightTheme(), currentAccent())
            label.setTextColor(pillForegroundColor(isLightTheme(), false))
        }
        livePreviewFrequencySignature = liveFrequencyKey(profile)
        liveFrequencyScroll.post { refreshScrollNavigationState() }
    }

    private fun formatHzRange(low: Double, high: Double): String {
        return when {
            high < 1000.0 -> String.format(java.util.Locale.US, "%.0f–%.0f Hz", low, high)
            low >= 1000.0 -> String.format(java.util.Locale.US, "%.2f–%.2f kHz", low / 1000.0, high / 1000.0)
            else -> String.format(java.util.Locale.US, "%.0f Hz–%.2f kHz", low, high / 1000.0)
        }
    }

    private fun previewBandFrequencyRange(index: Int, count: Int, profile: FloatingLayerProfile): Pair<Double, Double> {
        val (start, end) = horizontalBandWindow(index, count, profile)
        val ratio = MAX_SPECTRUM_HZ / MIN_SPECTRUM_HZ
        return Pair(MIN_SPECTRUM_HZ * ratio.pow(start.toDouble() / MAX_BAR_COUNT), MIN_SPECTRUM_HZ * ratio.pow(end.toDouble() / MAX_BAR_COUNT))
    }

    private fun previewHorizontalGravity(): Int = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM

    private fun adjustPreviewBarWidths() {
        if (previewBars.isEmpty()) return
        val profile = livePreviewProfile()
        if (profile.verticalMode) {
            barsContainer.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            val (singleWidth, singleHeight) = previewFloatingContentSize(profile)
            previewBars.firstOrNull()?.let { bar ->
                (bar as? PreviewBarView)?.setGlowPaddingPx(0)
                bar.layoutParams = LinearLayout.LayoutParams(singleWidth, singleHeight)
                bar.invalidate()
            }
            return
        }
        val count = previewBars.size.coerceAtLeast(1)
        val measuredWidth = barsContainer.width.takeIf { it > 0 }
            ?: (screenWidthDp * resources.displayMetrics.density).toInt()
        val availableWidth = minOf(measuredWidth, previewOverlayWidthPx(profile)).coerceAtLeast(count)
        // Layered Floating uses one FloatingSceneSpectrumView: it draws every body
        // directly on the configured logical overlay width, with no per-child glow
        // reservation. Mirror that same width/gap/thickness math in Display instead
        // of borrowing the legacy EffectBarView's glow-padding layout.
        val requestedGap = visualGapPx(profile.gapDp).toInt()
            .coerceAtLeast(if (profile.gapDp == 0) 0 else 1).toFloat()
        val minimumBarWidth = 1.dp().coerceAtLeast(1).toFloat()
        val maxGap = if (count <= 1) 0f else
            ((availableWidth.toFloat() - count.toFloat() * minimumBarWidth) / (count - 1).toFloat()).coerceAtLeast(0f)
        val gapPx = minOf(requestedGap, maxGap)
        val availableForBars = (availableWidth.toFloat() - gapPx * (count - 1).toFloat())
            .coerceAtLeast(count.toFloat() * minimumBarWidth)
        val requestedWidth = profile.barWidthDp.dp().coerceAtLeast(minimumBarWidth.toInt()).toFloat()
        val actualWidth = minOf(requestedWidth, availableForBars / count.toFloat()).coerceAtLeast(minimumBarWidth)
        val childWidth = actualWidth.toInt().coerceAtLeast(minimumBarWidth.toInt())
        val wholeGap = gapPx.toInt().coerceAtLeast(0)
        barsContainer.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM

        previewBars.forEachIndexed { index, bar ->
            val left = if (index == 0) 0 else wholeGap / 2
            val right = if (index == count - 1) 0 else wholeGap - wholeGap / 2
            (bar as? PreviewBarView)?.setGlowPaddingPx(0)
            bar.layoutParams = LinearLayout.LayoutParams(childWidth, 136.dp()).apply {
                setMargins(left, 0, right, 0)
            }
            bar.invalidate()
        }
    }

    /**
     * Resolve a physical bar to a position in the fixed 64-band logarithmic
     * spectrum. The analysis bank is independent of the requested bar count,
     * so changing 15 bars to 18/32/64 never changes the FFT band boundaries.
     */
    private fun horizontalBandWindow(
        index: Int,
        countValue: Int,
        profile: FloatingLayerProfile = livePreviewProfile()
    ): Pair<Float, Float> {
        val count = countValue.coerceAtLeast(1)
        val isMirrored = profile.mirrorMode == "HORIZONTAL"
        val isSymmetric = profile.horizontalAlignment == "CENTER" || isMirrored
        val uniqueCount = if (isSymmetric) ((count + 1) / 2).coerceAtLeast(1) else count
        val uniqueIndex = if (isSymmetric) minOf(index, count - 1 - index) else index
        var normalizedCenter = (uniqueIndex + 0.5f) / uniqueCount.toFloat()
        if (profile.horizontalAlignment == "RIGHT") normalizedCenter = 1f - normalizedCenter

        val windowWidth = MAX_BAR_COUNT.toFloat() / uniqueCount.toFloat()
        val center = normalizedCenter * MAX_BAR_COUNT.toFloat()
        val start = (center - windowWidth / 2f).coerceIn(0f, MAX_BAR_COUNT.toFloat())
        val end = (center + windowWidth / 2f).coerceIn(0f, MAX_BAR_COUNT.toFloat())
        return Pair(start, end)
    }

    private fun sampleSpectrumLevel(levels: FloatArray, index: Int, count: Int, profile: FloatingLayerProfile = livePreviewProfile()): Float {
        if (levels.isEmpty()) return 0f
        val (start, end) = horizontalBandWindow(index, count, profile)
        var weighted = 0f
        var totalWeight = 0f
        val firstBand = floor(start.toDouble()).toInt().coerceIn(0, MAX_BAR_COUNT - 1)
        val lastBand = floor((end - 0.0001f).coerceAtLeast(start).toDouble()).toInt().coerceIn(firstBand, MAX_BAR_COUNT - 1)
        for (band in firstBand..lastBand) {
            val overlap = (minOf(end, band + 1f) - maxOf(start, band.toFloat())).coerceAtLeast(0f)
            if (overlap > 0f) {
                weighted += levels.getOrElse(band) { 0f }.coerceIn(0f, 1f) * overlap
                totalWeight += overlap
            }
        }
        return (weighted / totalWeight.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    }

    private fun previewBandFrequencyDescription(index: Int, count: Int, profile: FloatingLayerProfile = livePreviewProfile()): String {
        val (start, end) = horizontalBandWindow(index, count, profile)
        val ratio = MAX_SPECTRUM_HZ / MIN_SPECTRUM_HZ
        val lowHz = MIN_SPECTRUM_HZ * ratio.pow(start.toDouble() / MAX_BAR_COUNT)
        val highHz = MIN_SPECTRUM_HZ * ratio.pow(end.toDouble() / MAX_BAR_COUNT)
        fun label(value: Double): String = if (value >= 1000.0) {
            String.format(java.util.Locale.US, "%.2f kHz", value / 1000.0)
        } else String.format(java.util.Locale.US, "%.0f Hz", value)
        return "Band ${index + 1}: ${label(lowHz)}–${label(highHz)}"
    }

    private fun sampleSinglePreviewRange(levels: FloatArray, profile: FloatingLayerProfile): Float {
        val low = minOf(profile.bassMinHz, profile.bassMaxHz)
        val high = maxOf(profile.bassMinHz, profile.bassMaxHz)
        val fullRange = low == 0 && high == 0
        val startHz = if (fullRange) MIN_SPECTRUM_HZ else low.coerceAtLeast(MIN_SPECTRUM_HZ.toInt()).toDouble()
        val endHz = if (fullRange) 20000.0 else maxOf(high, low).toDouble().coerceAtLeast(startHz)
        val start = (ln(startHz / MIN_SPECTRUM_HZ) / ln(MAX_SPECTRUM_HZ / MIN_SPECTRUM_HZ) * MAX_BAR_COUNT).toFloat().coerceIn(0f, MAX_BAR_COUNT - 0.001f)
        val end = (ln(endHz / MIN_SPECTRUM_HZ) / ln(MAX_SPECTRUM_HZ / MIN_SPECTRUM_HZ) * MAX_BAR_COUNT).toFloat().coerceIn(start + 0.001f, MAX_BAR_COUNT.toFloat())
        val first = floor(start.toDouble()).toInt().coerceIn(0, MAX_BAR_COUNT - 1)
        val last = floor((end - 0.0001f).coerceAtLeast(start).toDouble()).toInt().coerceIn(first, MAX_BAR_COUNT - 1)
        var sum = 0f
        var weight = 0f
        for (band in first..last) {
            val overlap = (minOf(end, band + 1f) - maxOf(start, band.toFloat())).coerceAtLeast(0f)
            if (overlap > 0f) { sum += levels.getOrElse(band) { 0f } * overlap; weight += overlap }
        }
        return (sum / weight.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    }

    private fun updatePreviewLevels(levels: FloatArray) {
        latestSpectrumLevels = levels.copyOf()
        val profile = livePreviewProfile()
        previewBars.forEachIndexed { index, view ->
            val level = when {
                profile.verticalMode && profile.singleAudioMode == "DJ" -> sampleSinglePreviewRange(levels, profile)
                profile.verticalMode -> levels.getOrElse(MAX_BAR_COUNT + 1) { 0f }
                else -> sampleSpectrumLevel(levels, index, previewBars.size, profile)
            }
            (view as? PreviewBarView)?.setLevel(level, profile)
        }
        schedulePreviewAnimationFrame()
        updateSpectrumBandLabelsFromLevels(levels, profile)
    }

    private fun formatFrequency(hz: Int): String {
        return when {
            hz >= 1000 -> String.format(java.util.Locale.US, "%.1f kHz", hz / 1000f)
            else -> "$hz Hz"
        }
    }

    private fun singleSpectrumLabel(): String {
        if (singleAudioMode != "DJ") {
            return "SINGLE HZ · FULL RANGE"
        }
        if (bassMinHz == 0 && bassMaxHz == 0) {
            return if (appLanguage == "id") "SINGLE HZ · FULL RANGE" else "SINGLE HZ · FULL RANGE"
        }
        val low = minOf(bassMinHz, bassMaxHz)
        val high = maxOf(bassMinHz, bassMaxHz)
        return "SINGLE HZ · ${formatFrequency(low)} – ${formatFrequency(high)}"
    }

    private fun updateSpectrumBandLabelsFromLevels(levels: FloatArray, profile: FloatingLayerProfile = livePreviewProfile()) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBandLabelUpdateMs < 95L) return
        lastBandLabelUpdateMs = now
        val left = findViewById<TextView>(R.id.txtBandLeft) ?: return
        val center = findViewById<TextView>(R.id.txtBandCenter) ?: return
        val right = findViewById<TextView>(R.id.txtBandRight) ?: return
        if (profile.verticalMode) {
            left.visibility = View.GONE
            right.visibility = View.GONE
            center.visibility = View.VISIBLE
            val level = if (profile.singleAudioMode == "DJ") sampleSinglePreviewRange(levels, profile) else levels.getOrElse(MAX_BAR_COUNT + 1) { 0f }
            center.text = "${singleSpectrumLabel(profile)} · ${(level.coerceIn(0f, 1f) * 100f).toInt()}%"
            center.alpha = 1f
            return
        }
        val segment = (MAX_BAR_COUNT / 3).coerceAtLeast(1)
        fun bandPercent(from: Int, to: Int): Int {
            var power = 0.0
            var count = 0
            for (i in from until to.coerceAtMost(MAX_BAR_COUNT)) {
                val value = levels.getOrElse(i) { 0f }.coerceIn(0f, 1f).toDouble()
                power += value * value
                count++
            }
            return (kotlin.math.sqrt(power / count.coerceAtLeast(1)) * 100.0).toInt().coerceIn(0, 100)
        }
        val low = bandPercent(0, segment)
        val mid = bandPercent(segment, segment * 2)
        val high = bandPercent(segment * 2, MAX_BAR_COUNT)
        val leftLabel: String
        val centerLabel: String
        val rightLabel: String
        when {
            profile.mirrorMode == "HORIZONTAL" && profile.horizontalAlignment == "RIGHT" -> { leftLabel = "HIGH"; centerLabel = "LOW"; rightLabel = "HIGH" }
            profile.mirrorMode == "HORIZONTAL" || profile.horizontalAlignment == "CENTER" -> { leftLabel = "LOW"; centerLabel = "HIGH"; rightLabel = "LOW" }
            profile.horizontalAlignment == "RIGHT" -> { leftLabel = "HIGH"; centerLabel = "MID"; rightLabel = "LOW" }
            else -> { leftLabel = "LOW"; centerLabel = "MID"; rightLabel = "HIGH" }
        }
        fun valueFor(label: String) = when (label) { "LOW" -> low; "HIGH" -> high; else -> mid }
        left.visibility = View.VISIBLE; center.visibility = View.VISIBLE; right.visibility = View.VISIBLE
        left.text = "${leftLabel} · ${valueFor(leftLabel)}%"
        center.text = "${centerLabel} · ${valueFor(centerLabel)}%"
        right.text = "${rightLabel} · ${valueFor(rightLabel)}%"
        left.alpha = 0.48f + valueFor(leftLabel) / 200f
        center.alpha = 0.48f + valueFor(centerLabel) / 200f
        right.alpha = 0.48f + valueFor(rightLabel) / 200f
    }

    private fun singleSpectrumLabel(profile: FloatingLayerProfile = livePreviewProfile()): String {
        if (profile.singleAudioMode != "DJ") return "SINGLE HZ · FULL RANGE"
        if (profile.bassMinHz == 0 && profile.bassMaxHz == 0) return "SINGLE HZ · FULL RANGE"
        val low = minOf(profile.bassMinHz, profile.bassMaxHz)
        val high = maxOf(profile.bassMinHz, profile.bassMaxHz)
        return "SINGLE HZ · ${formatFrequency(low)} – ${formatFrequency(high)}"
    }

    private fun updateSpectrumBandLabels(animated: Boolean) {
        invalidateLivePreviewProfileCache()
        val left = findViewById<TextView>(R.id.txtBandLeft) ?: return
        val center = findViewById<TextView>(R.id.txtBandCenter) ?: return
        val right = findViewById<TextView>(R.id.txtBandRight) ?: return
        val profile = livePreviewProfile()
        if (profile.verticalMode) {
            left.visibility = View.GONE; right.visibility = View.GONE; center.visibility = View.VISIBLE
            center.text = singleSpectrumLabel(profile)
            center.alpha = 1f
        } else {
            left.visibility = View.VISIBLE; center.visibility = View.VISIBLE; right.visibility = View.VISIBLE
            val labels = when {
                profile.mirrorMode == "HORIZONTAL" && profile.horizontalAlignment == "RIGHT" -> listOf("HIGH", "LOW", "HIGH")
                profile.mirrorMode == "HORIZONTAL" || profile.horizontalAlignment == "CENTER" -> listOf("LOW", "HIGH", "LOW")
                profile.horizontalAlignment == "RIGHT" -> listOf("HIGH", "MID", "LOW")
                else -> listOf("LOW", "MID", "HIGH")
            }
            left.text = "${labels[0]} · 0%"; center.text = "${labels[1]} · 0%"; right.text = "${labels[2]} · 0%"
            left.alpha = 0.72f; center.alpha = 0.72f; right.alpha = 0.72f
        }
        if (animated) listOf(left, center, right).forEach { label ->
            if (label.visibility == View.VISIBLE) {
                label.animate().cancel(); label.alpha = 0.45f
                label.animate().alpha(1f).setDuration(170L).start()
            }
        }
        if (latestSpectrumLevels.isNotEmpty()) updateSpectrumBandLabelsFromLevels(latestSpectrumLevels, profile)
    }

    private inner class PreviewBarView(context: Context, private val index: Int) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var level = 0f
        private var targetLevel = 0f
        private var peak = 0f
        private var peakHoldUntilMs = 0L

        private var lastProfileSignature: String? = null
        private var reservedGlowPaddingPx = 0

        fun setGlowPaddingPx(value: Int) {
            reservedGlowPaddingPx = value.coerceAtLeast(0)
            invalidate()
        }

        fun setLevel(value: Float, profile: FloatingLayerProfile = livePreviewProfile()) {
            val now = SystemClock.uptimeMillis()
            val signature = listOf(
                profile.id, profile.verticalMode, profile.barCount,
                profile.horizontalAlignment, profile.mirrorMode
            ).joinToString("|")
            if (lastProfileSignature != signature) {
                // Do not carry peak markers from an older flow/mirror profile.
                level = 0f
                peak = 0f
                peakHoldUntilMs = now
                lastProfileSignature = signature
            }
            targetLevel = value.coerceIn(0f, 1f)
            schedulePreviewAnimationFrame()
        }

        fun advanceAnimation(dtSeconds: Float, nowMs: Long, attackFactor: Float, releaseFactor: Float): Boolean {
            val oldLevel = level
            val oldPeak = peak
            val factor = if (targetLevel >= level) attackFactor else releaseFactor
            level += (targetLevel - level) * factor
            if (level > peak) {
                peak = level
                peakHoldUntilMs = nowMs + 125L
            } else if (nowMs > peakHoldUntilMs) {
                peak = (peak - 1.9f * dtSeconds).coerceAtLeast(level)
            }
            val changed = abs(level - oldLevel) > 0.00025f || abs(peak - oldPeak) > 0.00025f
            if (changed) invalidate()
            return abs(targetLevel - level) > 0.0015f || abs(peak - level) > 0.02f
        }

        private fun drawPreviewPeakLine(
            canvas: android.graphics.Canvas,
            profile: FloatingLayerProfile,
            insetX: Float = 0f,
            insetY: Float = 0f
        ) {
            if (width <= 0 || height <= 0) return
            val markerHeight = minOf((if (profile.verticalMode) 2 else 1).dp().toFloat(), height.toFloat())
            val mirroredVertical = !profile.verticalMode && profile.mirrorMode == "VERTICAL"
            val markerLeft = insetX.coerceAtLeast(0f)
            val markerRight = (width.toFloat() - insetX).coerceAtLeast(markerLeft + 1f)
            val innerHeight = (height.toFloat() - insetY * 2f).coerceAtLeast(1f)

            fun markerRects(value: Float): List<android.graphics.RectF> {
                val safeValue = value.coerceIn(0f, 1f)
                return if (mirroredVertical) {
                    val centerY = height / 2f
                    val extent = centerY * safeValue
                    val topY = (centerY - extent - markerHeight / 2f).coerceIn(0f, height - markerHeight)
                    val bottomY = (centerY + extent - markerHeight / 2f).coerceIn(0f, height - markerHeight)
                    listOf(
                        android.graphics.RectF(markerLeft, topY, markerRight, topY + markerHeight),
                        android.graphics.RectF(markerLeft, bottomY, markerRight, bottomY + markerHeight)
                    )
                } else {
                    val y = (insetY + innerHeight * (1f - safeValue) - markerHeight / 2f)
                        .coerceIn(insetY, (height.toFloat() - insetY - markerHeight).coerceAtLeast(insetY))
                    listOf(android.graphics.RectF(markerLeft, y, markerRight, y + markerHeight))
                }
            }

            fun drawMarker(value: Float, markerOpacity: Int) {
                if (markerOpacity <= 0 || value <= 0.04f) return
                markerRects(value).forEach { marker ->
                    // Peak caps stay crisp in Display preview. The bar body already
                    // carries the reduced preview halo, so a second halo here would
                    // duplicate glow work for both the live and held peak markers.
                    paint.shader = null
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.color = Color.WHITE
                    paint.alpha = (235 * markerOpacity.coerceIn(0, 100) / 100)
                    if (profile.verticalMode) {
                        canvas.drawRoundRect(marker, markerHeight / 2f, markerHeight / 2f, paint)
                    } else {
                        canvas.drawRect(marker, paint)
                    }
                    paint.alpha = 255
                }
            }

            // A held maximum is a separate shadow marker. It remains visible while
            // the live bar drops, and has its own independent opacity control.
            if (abs(peak - level) > 0.025f) drawMarker(peak, profile.shadowOpacityPercent)
            // The live endpoint belongs to the main bar, so opacity 0 hides it too.
            drawMarker(level, profile.opacityPercent)
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val profile = livePreviewProfile()
            if (width <= 0 || height <= 0 || profile.colors.isEmpty()) return
            val colors = profile.colors
            val opacity = profile.opacityPercent.coerceIn(0, 100) * 255 / 100
            val radius = minOf(profile.cornerRadiusDp.dp().toFloat(), width / 2f, height / 2f)
            if (profile.verticalMode) {
                val segmentCount = 24
                val glowStrength = profile.glowPercent.coerceIn(0, 100) / 100f * glowStyleFactor(profile.effect)
                val needsGlowRoom = glowStrength > 0f && profile.opacityPercent > 0
                val glowInsetX = if (needsGlowRoom) minOf(14.dp().toFloat() * glowStrength, width * 0.14f) else 0f
                val glowInsetY = 0f // Match Floating Single: use the full longitudinal preview surface.
                val contentWidth = (width.toFloat() - glowInsetX * 2f).coerceAtLeast(1f)
                val contentHeight = (height.toFloat() - glowInsetY * 2f).coerceAtLeast(1f)
                val left = glowInsetX
                val right = width.toFloat() - glowInsetX
                val bottomEdge = height.toFloat() - glowInsetY
                val safeRadius = minOf(radius, minOf(contentWidth, contentHeight) / 2f)

                // Match Floating's continuous single meter when Gap is zero.
                if (profile.gapDp == 0) {
                    val shaderColors = IntArray(colors.size) { index ->
                        val color = colors[index]
                        Color.argb(opacity, Color.red(color), Color.green(color), Color.blue(color))
                    }
                    val fillTop = glowInsetY + contentHeight * (1f - level.coerceIn(0f, 1f))
                    val continuousRect = android.graphics.RectF(left, fillTop, right, bottomEdge)
                    drawPreviewGlow(canvas, paint, continuousRect, colors.first(), profile.effect,
                        profile.glowPercent, profile.opacityPercent, safeRadius, 1f)
                    paint.shader = android.graphics.LinearGradient(
                        0f, bottomEdge, 0f, glowInsetY, shaderColors, null,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    paint.alpha = opacity
                    canvas.drawRoundRect(continuousRect, safeRadius, safeRadius, paint)
                    paint.shader = null
                    paint.alpha = 255
                    if (profile.effect == EFFECT_GLOSSY || profile.effect == EFFECT_GLASS) {
                        paint.color = Color.WHITE
                        paint.alpha = (opacity * if (profile.effect == EFFECT_GLASS) 0.42f else 0.32f).toInt()
                        canvas.drawRoundRect(
                            continuousRect.left + continuousRect.width() * 0.14f,
                            fillTop + 2.dp().toFloat(),
                            continuousRect.left + continuousRect.width() * 0.30f,
                            bottomEdge - 2.dp().toFloat(),
                            safeRadius, safeRadius, paint
                        )
                        paint.alpha = 255
                    }
                    drawPreviewPeakLine(canvas, profile, glowInsetX, glowInsetY)
                    return
                }

                val minimumSegmentHeight = 2.dp().toFloat()
                val availableForGap = (contentHeight - minimumSegmentHeight * segmentCount.toFloat()).coerceAtLeast(0f)
                val maxGapPx = availableForGap / (segmentCount - 1).toFloat()
                val gapPx = visualGapPx(profile.gapDp).coerceAtMost(maxGapPx)
                val segmentHeight = (contentHeight - gapPx * (segmentCount - 1).toFloat()) / segmentCount.toFloat()
                if (segmentHeight <= 0f) return
                val gapRatio = if (maxGapPx > 0f) (gapPx / maxGapPx).coerceIn(0f, 1f) else 0f
                val segmentRadius = minOf(safeRadius * (1f - gapRatio * 0.78f), segmentHeight / 2f)
                val activeCount = (level.coerceIn(0f, 1f) * segmentCount).toInt().coerceIn(0, segmentCount)
                for (segment in 0 until segmentCount) {
                    val top = glowInsetY + contentHeight - (segment + 1) * segmentHeight - segment * gapPx
                    val bottom = top + segmentHeight
                    val color = colors[(segment * colors.size / segmentCount).coerceIn(0, colors.lastIndex)]
                    val active = segment < activeCount
                    paint.shader = null
                    paint.color = color
                    paint.alpha = if (active) opacity else
                        (profile.shadowOpacityPercent.coerceIn(0, 100) * 255 / 100 * 0.10f).toInt()
                    val rect = android.graphics.RectF(left, top, right, bottom)
                    if (active) drawPreviewGlow(canvas, paint, rect, color, profile.effect,
                        profile.glowPercent, profile.opacityPercent, segmentRadius, 1f)
                    canvas.drawRoundRect(rect, segmentRadius, segmentRadius, paint)
                    paint.alpha = 255
                    if (active && (profile.effect == EFFECT_GLOSSY || profile.effect == EFFECT_GLASS) && segmentHeight > 2.dp()) {
                        paint.color = Color.WHITE
                        paint.alpha = (opacity * if (profile.effect == EFFECT_GLASS) 0.42f else 0.32f).toInt()
                        canvas.drawRoundRect(
                            rect.left + rect.width() * 0.14f, top + 1.dp().toFloat(),
                            rect.left + rect.width() * 0.30f, bottom - 1.dp().toFloat(),
                            segmentRadius, segmentRadius, paint
                        )
                        paint.alpha = 255
                    }
                }
                drawPreviewPeakLine(canvas, profile, glowInsetX, glowInsetY)
                return
            }
            val colorBottom = colors[index % colors.size]
            val colorTop = colors[(index + 1) % colors.size]
            val mirroredVertical = profile.mirrorMode == "VERTICAL"
            val glow = reservedGlowPaddingPx.toFloat().coerceAtMost(width * 0.42f).coerceAtMost(height * 0.18f)
            val availableHeight = (height.toFloat() - glow * 2f).coerceAtLeast(2f)
            val rects = if (mirroredVertical) {
                val centerY = height / 2f
                val halfHeight = (availableHeight / 2f).coerceAtLeast(1f)
                val barHeight = halfHeight * level.coerceIn(0f, 1f)
                listOf(
                    android.graphics.RectF(glow, centerY - barHeight, width.toFloat() - glow, centerY),
                    android.graphics.RectF(glow, centerY, width.toFloat() - glow, centerY + barHeight)
                )
            } else {
                val barHeight = availableHeight * level.coerceIn(0f, 1f)
                val top = height.toFloat() - glow - barHeight
                listOf(android.graphics.RectF(glow, top, width.toFloat() - glow, height.toFloat() - glow))
            }
            rects.forEachIndexed { rectIndex, rect ->
                if (rect.height() <= 0f) return@forEachIndexed
                val bodyRect = android.graphics.RectF(rect)
                val gapCurve = (profile.gapDp / 64f).coerceIn(0f, 1f)
                val progressiveRadius = radius * (1f - gapCurve * 0.78f)
                val renderedRadius = minOf(progressiveRadius, bodyRect.width() / 2f, bodyRect.height() / 2f)
                // Match the per-layer Floating renderer: its halo uses colorA (the
                // first stop for this bar) and the same gap-adjusted corner radius.
                drawPreviewGlow(canvas, paint, bodyRect, colorBottom, profile.effect, profile.glowPercent, profile.opacityPercent, renderedRadius)
                val gradientColors = if (profile.verticalMode) colors.toIntArray() else intArrayOf(colorTop, colorBottom)
                paint.shader = android.graphics.LinearGradient(0f, rect.bottom, 0f, rect.top, gradientColors, null, android.graphics.Shader.TileMode.CLAMP)
                paint.alpha = opacity
                if (!profile.verticalMode && profile.gapDp == 0) {
                    val outerRadius = minOf(profile.cornerRadiusDp.dp().toFloat(), bodyRect.width() / 2f, bodyRect.height() / 2f)
                    if (profile.mirrorMode == "VERTICAL" && rectIndex == 1) drawBottomRoundedColumn(canvas, paint, bodyRect, outerRadius)
                    else drawTopRoundedColumn(canvas, paint, bodyRect, outerRadius)
                } else {
                    canvas.drawRoundRect(bodyRect, renderedRadius, renderedRadius, paint)
                }
                paint.shader = null
                paint.alpha = 255
                if (profile.effect == EFFECT_GLOSSY || profile.effect == EFFECT_GLASS) {
                    paint.color = Color.WHITE
                    paint.alpha = (opacity * if (profile.effect == EFFECT_GLASS) 0.36f else 0.24f).toInt()
                    val highlightWidth = maxOf(1.dp().toFloat(), bodyRect.width() * 0.18f)
                    canvas.drawRoundRect(
                        bodyRect.left + bodyRect.width() * 0.16f,
                        bodyRect.top + 1.dp().toFloat(),
                        bodyRect.left + bodyRect.width() * 0.16f + highlightWidth,
                        bodyRect.bottom - 1.dp().toFloat(),
                        renderedRadius, renderedRadius, paint
                    )
                }
                paint.alpha = 255
            }
            drawPreviewPeakLine(canvas, profile, glow, glow)
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
        addFloatingProfileSectionControls(card, "MODE")

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 18.dp(), 0, 12.dp())
        }
        val dj = glassButton(if (appLanguage == "id") "ATUR HZ" else "SETTINGS HZ")
        val music = glassButton("MUSIC FULL")
        val lp = LinearLayout.LayoutParams(0, 48.dp(), 1f)
        lp.setMargins(4.dp(), 0, 4.dp(), 0)
        row.addView(dj, lp)
        row.addView(music, LinearLayout.LayoutParams(0, 48.dp(), 1f).apply { setMargins(4.dp(), 0, 4.dp(), 0) })
        card.addView(row)
        singleModeButtonsRow = row

        val bass = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bassControls = bass
        val bassHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp(), 0, 2.dp())
        }
        bassHeader.addView(sectionTitle(if (appLanguage == "id") "RENTANG FREKUENSI" else "FREQUENCY RANGE"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val resetBass = glassButton("↺ RESET").apply { minWidth = 82.dp(); textSize = 10f }
        singleRangeResetButton = resetBass
        applyPillStyle(resetBass, false)
        resetBass.setTextColor(themedTextColor(resetBass, isLightTheme()))
        bassHeader.addView(resetBass, LinearLayout.LayoutParams(86.dp(), 36.dp()))
        bass.addView(bassHeader)
        View(this).apply { setBackgroundColor(if (isLightTheme()) Color.argb(55, 75, 92, 118) else Color.argb(70, 190, 208, 236)) }.also {
            bass.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp()).apply { topMargin = 3.dp(); bottomMargin = 5.dp() })
        }
        addControlSlider(bass, if (appLanguage == "id") "FREKUENSI MULAI" else "START FREQUENCY", bassMinHz, 0, 20000, " Hz") {
            bassMinHz = it.coerceIn(0, 20000); updateSpectrumBandLabels(animated = false); saveSettings(); updateFloatingService()
        }
        addControlSlider(bass, if (appLanguage == "id") "FREKUENSI AKHIR" else "END FREQUENCY", bassMaxHz, 0, 20000, " Hz") {
            bassMaxHz = it.coerceIn(0, 20000); updateSpectrumBandLabels(animated = false); saveSettings(); updateFloatingService()
        }
        resetBass.setOnClickListener {
            bassMinHz = 20
            bassMaxHz = 125
            updateSpectrumBandLabels(animated = true)
            syncSliderValue(if (appLanguage == "id") "FREKUENSI MULAI" else "START FREQUENCY", bassMinHz)
            syncSliderValue(if (appLanguage == "id") "FREKUENSI AKHIR" else "END FREQUENCY", bassMaxHz)
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
        singleModeHideAnimationRunning = false
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
        val row = singleModeButtonsRow ?: return
        val light = isLightTheme()
        val djActive = singleAudioMode == "DJ"
        (row.getChildAt(0) as? TextView)?.let { applyPillStyle(it, djActive, light, currentAccent()) }
        (row.getChildAt(1) as? TextView)?.let { applyPillStyle(it, !djActive, light, currentAccent()) }
        bassControls?.visibility = if (djActive) View.VISIBLE else View.GONE
    }

    private fun updateSingleModeVisibility() {
        val card = singleModeCard ?: return
        if (isVerticalMode) {
            // Do not restart an animation while frequency controls are changing.
            if (card.visibility != View.VISIBLE) {
                card.animate().cancel()
                singleModeHideAnimationRunning = false
                card.visibility = View.VISIBLE
                card.alpha = 0f
                card.animate().alpha(1f).setDuration(160L).start()
            } else if (singleModeHideAnimationRunning) {
                card.animate().cancel()
                singleModeHideAnimationRunning = false
                card.alpha = 1f
            }
        } else if (card.visibility == View.VISIBLE && !singleModeHideAnimationRunning) {
            // A hide transition is started exactly once. Repeated service/slider
            // updates must not cancel and restart it frame after frame.
            singleModeHideAnimationRunning = true
            card.animate().cancel()
            card.animate().alpha(0f).setDuration(140L).withEndAction {
                singleModeHideAnimationRunning = false
                if (!isVerticalMode) card.visibility = View.GONE
                else {
                    card.visibility = View.VISIBLE
                    card.alpha = 1f
                }
            }.start()
        }
    }

    // -----------------------------------------------------------------
    // FLOATING CONTROLS
    // -----------------------------------------------------------------

    private fun buildFloatingControls() {
        controlsContainer.removeAllViews()
        sliderBindings.clear()
        addFloatingProfileAppearanceControls()
        addControlSectionDivider(controlsContainer, if (appLanguage == "id") "GEOMETRI BAR" else "BAR GEOMETRY")
        // Manual values and sliders share broad, bounded ranges. The overlay renderer
        // safely fits oversized dimensions to the current display instead of overflowing it.
        addControlSlider(controlsContainer, "HORIZONTAL WIDTH", overlayWidthDp, 0, 4096, " dp") { clearSelectedAppearancePreset(); overlayWidthDp = it; clampPosition() }
        addControlSlider(controlsContainer, "BAR HEIGHT / LENGTH", overlayHeightDp, 0, 4096, " dp") { clearSelectedAppearancePreset(); overlayHeightDp = it; clampPosition() }
        addControlSlider(controlsContainer, "HORIZONTAL BAR COUNT", barCount, 1, MAX_BAR_COUNT, " bars") {
            clearSelectedAppearancePreset()
            barCount = it
            updateHorizontalFlowButtons()
            if (!isVerticalMode) {
                updateHandler.removeCallbacks(barCountPreviewRebuild)
                updateHandler.postDelayed(barCountPreviewRebuild, 90L)
            }
            buildLiveFrequencyLabels()
        }
        addControlSlider(controlsContainer, "BAR THICKNESS", barWidthDp, 0, 2048, " dp") {
            clearSelectedAppearancePreset()
            barWidthDp = it; clampPosition(); adjustPreviewBarWidths()
        }
        addControlSlider(controlsContainer, "BAR GAP", barGapDp, 0, 512, " dp") {
            clearSelectedAppearancePreset()
            barGapDp = it; adjustPreviewBarWidths()
        }
        addControlSectionDivider(controlsContainer, if (appLanguage == "id") "TAMPILAN & RESPONS" else "APPEARANCE & RESPONSE")
        addControlSlider(controlsContainer, "SCALE", scalePercent, 0, 500, "%") { clearSelectedAppearancePreset(); scalePercent = it; clampPosition() }
        addControlSlider(controlsContainer, "BAR OPACITY", opacityPercent, 0, 100, "%") { clearSelectedAppearancePreset(); opacityPercent = it }
        addControlSlider(controlsContainer, "SHADOW BAR OPACITY", shadowOpacityPercent, 0, 100, "%") { clearSelectedAppearancePreset(); shadowOpacityPercent = it }
        addControlSlider(controlsContainer, "GLOW INTENSITY", glowPercent, 0, 100, "%") { clearSelectedAppearancePreset(); glowPercent = it }
        addControlSlider(controlsContainer, "RESPONSE SPEED", responsePercent, 0, 100, "%") { clearSelectedAppearancePreset(); responsePercent = it }
        addControlSlider(controlsContainer, "CORNER ROUNDNESS", cornerRadiusDp, 0, 512, " dp") { clearSelectedAppearancePreset(); cornerRadiusDp = it }
        updateBarAppearanceModeAvailability()
    }

    private fun storeSelectedAppearancePreset(name: String?) {
        try {
            val profiles = readFloatingProfileLibrary()
            val index = profiles.indexOfFirst { it.id == selectedFloatingProfileId }
            if (index < 0) return
            val normalized = name?.trim()?.takeIf { it.isNotEmpty() }
            if (profiles[index].appearancePresetName == normalized) return
            profiles[index] = profiles[index].copy(appearancePresetName = normalized)
            writeFloatingProfileLibrary(profiles)
        } catch (error: Exception) {
            // Preset metadata is supplementary UI state. A malformed legacy
            // preference must never take down the visualizer Activity.
            android.util.Log.e("FloatingDJ", "Unable to persist Bar Appearance preset metadata", error)
        }
    }

    /** Manual Bar Appearance edits detach the layer from its last applied preset. */
    private fun clearSelectedAppearancePreset() {
        storeSelectedAppearancePreset(null)
        scheduleBarAppearancePresetRefresh()
    }

    private fun selectedAppearancePresetName(): String? {
        return try {
            val profile = readFloatingProfileLibrary().firstOrNull { it.id == selectedFloatingProfileId }
                ?: return null
            val storedName = profile.appearancePresetName ?: return null
            val preset = barAppearancePresets().firstOrNull { it.name.equals(storedName, ignoreCase = true) }
                ?: return null
            val matches = profile.effect == preset.effect &&
                profile.glowPercent == (if (preset.effect == EFFECT_CLEAN) 0 else preset.glow) &&
                profile.opacityPercent == preset.opacity &&
                profile.responsePercent == preset.response &&
                profile.cornerRadiusDp == preset.corner &&
                profile.gapDp == preset.gap
            storedName.takeIf { matches }
        } catch (error: Exception) {
            android.util.Log.e("FloatingDJ", "Unable to resolve active Bar Appearance preset", error)
            null
        }
    }

    /** Defer selector repaint until the current view/layout transaction has finished. */
    private fun scheduleBarAppearancePresetRefresh() {
        if (!::barAppearancePresetStatus.isInitialized || !::mainRoot.isInitialized) return
        mainRoot.post {
            if (isFinishing || isDestroyed || !::barAppearancePresetStatus.isInitialized) return@post
            refreshBarAppearancePresetSelection()
        }
    }

    private fun registerBarAppearancePresetView(name: String, view: TextView, baseText: String) {
        barAppearancePresetViews.getOrPut(name) { mutableListOf() }.add(view to baseText)
    }

    private fun refreshBarAppearancePresetSelection() {
        if (!::barAppearancePresetStatus.isInitialized) return
        try {
            val activeName = selectedAppearancePresetName()
            val light = isLightTheme()
            if (activeName != null) {
                barAppearancePresetStatus.text = if (appLanguage == "id") "● PRESET DIPAKAI · $activeName" else "● PRESET IN USE · $activeName"
                barAppearancePresetStatus.contentDescription = if (appLanguage == "id") "Preset tampilan yang sedang dipakai: $activeName" else "Appearance preset currently in use: $activeName"
                barAppearancePresetStatus.isSelected = true
                applyPillStyle(barAppearancePresetStatus, true, light, currentAccent())
            } else {
                barAppearancePresetStatus.text = if (appLanguage == "id") "○ CUSTOM · TIDAK MEMAKAI PRESET" else "○ CUSTOM SETUP · NO PRESET IN USE"
                barAppearancePresetStatus.contentDescription = if (appLanguage == "id") "Pengaturan kustom, tidak memakai preset" else "Custom settings, no appearance preset in use"
                barAppearancePresetStatus.isSelected = false
                applyPillStyle(barAppearancePresetStatus, false, light, currentAccent())
            }
            barAppearancePresetViews.forEach { (name, views) ->
                val selected = activeName != null && name.equals(activeName, ignoreCase = true)
                views.toList().forEach viewLoop@{ (view, baseText) ->
                    if (view.parent == null) return@viewLoop
                    view.text = if (selected) "✓  $baseText" else baseText
                    view.isSelected = selected
                    view.contentDescription = if (selected) {
                        if (appLanguage == "id") "$baseText · sedang dipakai" else "$baseText · currently in use"
                    } else baseText
                    applyPillStyle(view, selected, light, currentAccent())
                    view.elevation = if (selected) 2.dp().toFloat() else 0f
                    view.alpha = if (selected) 1f else 0.92f
                }
            }
            barAppearancePresetGalleryViews.toList().forEach galleryLoop@{ (name, view, baseText) ->
                if (view.parent == null) return@galleryLoop
                val selected = activeName != null && name.equals(activeName, ignoreCase = true)
                view.text = if (selected) "✓  $baseText" else baseText
                view.isSelected = selected
                view.contentDescription = if (selected) {
                    if (appLanguage == "id") "$name · sedang dipakai" else "$name · currently in use"
                } else name
                applyPillStyle(view, selected, light, currentAccent())
                view.elevation = if (selected) 2.dp().toFloat() else 0f
                view.alpha = if (selected) 1f else 0.94f
            }
        } catch (error: Exception) {
            android.util.Log.e("FloatingDJ", "Bar Appearance preset UI refresh failed; using safe Custom state", error)
            try {
                barAppearancePresetStatus.text = if (appLanguage == "id") "○ KUSTOM · STATUS PRESET TIDAK TERSEDIA" else "○ CUSTOM · PRESET STATUS UNAVAILABLE"
                barAppearancePresetStatus.isSelected = false
                barAppearancePresetStatus.setTextColor(if (isLightTheme()) Color.DKGRAY else Color.WHITE)
                barAppearancePresetStatus.setBackgroundColor(if (isLightTheme()) 0xFFE8EEF6.toInt() else 0xFF263449.toInt())
            } catch (_: Exception) { }
        }
    }

    private fun barAppearancePresets(): List<BarAppearancePreset> = listOf(
        BarAppearancePreset("Clean Line", "Flat and precise", EFFECT_CLEAN, 0, 100, 82, 0, 0),
        BarAppearancePreset("Clean Studio", "Neutral meter", EFFECT_CLEAN, 0, 100, 88, 5, 2),
        BarAppearancePreset("Clean Soft", "Soft rounded bars", EFFECT_CLEAN, 0, 92, 78, 12, 5),
        BarAppearancePreset("Clean Compact", "Compact and quiet", EFFECT_CLEAN, 0, 88, 90, 4, 8),
        BarAppearancePreset("Glossy Classic", "Balanced glossy highlight", EFFECT_GLOSSY, 50, 100, 82, 14, 3),
        BarAppearancePreset("Glossy Soft", "Gentle glass reflection", EFFECT_GLOSSY, 35, 94, 76, 22, 5),
        BarAppearancePreset("Glossy Bright", "Bright reflective bars", EFFECT_GLOSSY, 65, 100, 90, 18, 2),
        BarAppearancePreset("Glossy Smooth", "Smooth rounded motion", EFFECT_GLOSSY, 45, 96, 72, 28, 7),
        BarAppearancePreset("Glass Frost", "Frosted transparent finish", EFFECT_GLASS, 38, 88, 80, 18, 4),
        BarAppearancePreset("Glass Crystal", "Crisp crystal finish", EFFECT_GLASS, 52, 96, 90, 10, 2),
        BarAppearancePreset("Glass Mist", "Soft translucent bars", EFFECT_GLASS, 24, 76, 72, 26, 8),
        BarAppearancePreset("Glass Edge", "Thin frosted edges", EFFECT_GLASS, 45, 92, 86, 6, 1),
        BarAppearancePreset("Neon Pulse", "Strong beat glow", EFFECT_NEON, 100, 100, 96, 16, 3),
        BarAppearancePreset("Neon Aurora", "Smooth colored glow", EFFECT_NEON, 84, 96, 86, 24, 5),
        BarAppearancePreset("Neon Cyber", "Sharp cyber meter", EFFECT_NEON, 100, 100, 100, 5, 1),
        BarAppearancePreset("Neon Velvet", "Softer neon edge", EFFECT_NEON, 68, 90, 75, 32, 7),
        BarAppearancePreset("Neon Ice", "Cool bright neon", EFFECT_NEON, 92, 94, 90, 20, 2),
        BarAppearancePreset("Neon Club", "High energy response", EFFECT_NEON, 100, 100, 100, 12, 4),
        BarAppearancePreset("Stage Glass", "Stage-ready reflections", EFFECT_GLASS, 62, 100, 92, 14, 3),
        BarAppearancePreset("Studio Gloss", "Balanced everyday setup", EFFECT_GLOSSY, 50, 100, 84, 20, 4)
    )

    private fun applyBarAppearancePreset(preset: BarAppearancePreset) {
        storeSelectedEffectGlow(barEffect, glowPercent)
        barEffect = preset.effect
        glowPercent = if (preset.effect == EFFECT_CLEAN) 0 else preset.glow.coerceIn(0, 100)
        storeSelectedEffectGlow(preset.effect, glowPercent)
        opacityPercent = preset.opacity.coerceIn(0, 100)
        responsePercent = preset.response.coerceIn(0, 100)
        cornerRadiusDp = preset.corner.coerceIn(0, 512)
        barGapDp = preset.gap.coerceIn(0, 512)
        storeSelectedAppearancePreset(preset.name)
        saveSettings()
        updateEffectButtons()
        syncSliderValue("GLOW INTENSITY", glowPercent)
        syncSliderValue("BAR OPACITY", opacityPercent)
        syncSliderValue("SHADOW BAR OPACITY", shadowOpacityPercent)
        syncSliderValue("RESPONSE SPEED", responsePercent)
        syncSliderValue("CORNER ROUNDNESS", cornerRadiusDp)
        syncSliderValue("BAR GAP", barGapDp)
        updateBarAppearanceModeAvailability()
        updateFloatingService()
    }

    private fun buildBarAppearancePresetControls() {
        if (!::barAppearancePresetContainer.isInitialized) return
        barAppearancePresetContainer.removeAllViews()
        barAppearancePresetViews.clear()
        val presets = barAppearancePresets()
        val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        section.addView(sectionTitle(if (appLanguage == "id") "PRESET TAMPILAN BAR" else "BAR APPEARANCE PRESETS").apply {
            textSize = 11f
            setPadding(1.dp(), 0, 1.dp(), 6.dp())
        })
        section.addView(View(this).apply { background = studioDividerDrawable() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2.dp()).apply {
                topMargin = 3.dp(); bottomMargin = 11.dp(); leftMargin = 2.dp(); rightMargin = 2.dp()
            })
        barAppearancePresetStatus = TextView(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            textSize = 9f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            includeFontPadding = false
            setPadding(11.dp(), 8.dp(), 11.dp(), 8.dp())
        }
        section.addView(barAppearancePresetStatus, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 34.dp()).apply {
            bottomMargin = 7.dp()
        })

        val scroll = HorizontalScrollView(this).apply {
            // Android 11 can crash in View.onDrawScrollBars when a themed
            // ScrollBarDrawable is absent. The app uses arrow/fading-edge affordances.
            isHorizontalScrollBarEnabled = false
            clipToPadding = true
            clipChildren = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Keep the first/last preset comfortably separated from the frosted glass rails.
            setPadding(12.dp(), 0, 12.dp(), 0)
        }
        scroll.addView(strip)
        // Keep the complete preset catalog in the horizontal strip. The viewport
        // shows only the chips that fit, while horizontal scrolling/arrows can
        // reach every one of the 20 built-in looks (the gallery remains available too).
        presets.forEach { preset ->
            val base = preset.name.uppercase(java.util.Locale.ROOT)
            val chip = glassButton(base)
            chip.textSize = 9f
            chip.maxLines = 1
            chip.setPadding(11.dp(), 8.dp(), 11.dp(), 8.dp())
            registerBarAppearancePresetView(preset.name, chip, base)
            chip.setOnClickListener { applyBarAppearancePreset(preset) }
            strip.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 38.dp()).apply {
                marginEnd = 6.dp()
            })
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = glassButton("‹").apply {
            textSize = 22f; includeFontPadding = false; gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 1.dp())
        }
        val right = glassButton("›").apply {
            textSize = 22f; includeFontPadding = false; gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 1.dp())
        }
        styleScrollArrow(left); styleScrollArrow(right)
        nav.addView(left, LinearLayout.LayoutParams(36.dp(), 38.dp()).apply { marginEnd = 5.dp() })
        nav.addView(scroll, LinearLayout.LayoutParams(0, 44.dp(), 1f))
        nav.addView(right, LinearLayout.LayoutParams(36.dp(), 38.dp()).apply { marginStart = 5.dp() })
        left.setOnClickListener { scroll.smoothScrollBy(-(scroll.width * 0.8f).toInt().coerceAtLeast(80.dp()), 0) }
        right.setOnClickListener { scroll.smoothScrollBy((scroll.width * 0.8f).toInt().coerceAtLeast(80.dp()), 0) }
        section.addView(nav)

        val allButton = glassButton(if (appLanguage == "id") "▦  BUKA SEMUA TAMPILAN (${presets.size})" else "▦  OPEN ALL LOOKS (${presets.size})")
        allButton.textSize = 10f
        section.addView(allButton, matchWrap().apply { topMargin = 6.dp() })
        allButton.setOnClickListener {
            val gallery = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
            }
            gallery.addView(subtitle(if (appLanguage == "id") "Pilih tampilan untuk menerapkan efek dan nilai ke layer yang sedang aktif." else "Choose a look to apply its effect and values to the active layer.").apply {
                setPadding(3.dp(), 2.dp(), 3.dp(), 8.dp())
            })
            val galleryScroll = ScrollView(this).apply {
                // Keep scrolling, but avoid framework scrollbar drawing (API 30 devices).
                isVerticalScrollBarEnabled = false
                clipToPadding = false
            }
            val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            galleryScroll.addView(grid, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            fun render(query: String = "") {
                grid.removeAllViews()
                barAppearancePresetGalleryViews.clear()
                presets.filter { it.name.contains(query, true) || it.description.contains(query, true) }
                    .chunked(2).forEach { pair ->
                        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                        pair.forEach { preset ->
                            val base = preset.name + "\n" + preset.description
                            val tile = glassButton(base)
                            tile.textSize = 9f
                            tile.gravity = Gravity.CENTER
                            tile.maxLines = 2
                            tile.setPadding(8.dp(), 6.dp(), 8.dp(), 6.dp())
                            barAppearancePresetGalleryViews.add(Triple(preset.name, tile, base))
                            tile.setOnClickListener { clickedView ->
                                applyBarAppearancePreset(preset)
                                clickedView.animate().cancel()
                                clickedView.animate().scaleX(0.985f).scaleY(0.985f).setDuration(75L)
                                    .withEndAction {
                                        clickedView.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                                    }.start()
                            }
                            row.addView(tile, LinearLayout.LayoutParams(0, 62.dp(), 1f).apply { setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp()) })
                        }
                        if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 62.dp(), 1f))
                        grid.addView(row, matchWrap())
                    }
                scheduleBarAppearancePresetRefresh()
            }
            val search = EditText(this).apply {
                hint = if (appLanguage == "id") "Cari tampilan…" else "Search looks…"
                setSingleLine(true)
                textSize = 12f
                setTextColor(if (isLightTheme()) Color.parseColor("#26344B") else Color.WHITE)
                setHintTextColor(if (isLightTheme()) Color.parseColor("#8290A6") else Color.parseColor("#98A6BD"))
                background = glassEditDrawable()
                setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            }
            gallery.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 42.dp()).apply { bottomMargin = 6.dp() })
            gallery.addView(galleryScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: Editable?) {}
            })
            render()
            val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val close = glassButton(if (appLanguage == "id") "TUTUP" else "CLOSE")
            footer.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()))
            val dialog = showGlassStudioDialog(if (appLanguage == "id") "SEMUA TAMPILAN BAR" else "ALL BAR LOOKS", gallery, footer, 0.96f, 0.84f)
            close.setOnClickListener { dialog.dismiss() }
        }
        barAppearancePresetContainer.addView(section, matchWrap())
        // Run the first preset-state resolution after the initial view tree has
        // completed layout. It must never sit on the Activity's startup path.
        scheduleBarAppearancePresetRefresh()
    }

    private fun buildHorizontalFlowControls() {
        horizontalModeContainer.removeAllViews()
        horizontalModeContainer.layoutTransition = LayoutTransition().apply {
            setDuration(LayoutTransition.CHANGE_APPEARING, 210L)
            setDuration(LayoutTransition.CHANGE_DISAPPEARING, 160L)
            setDuration(LayoutTransition.APPEARING, 210L)
            setDuration(LayoutTransition.DISAPPEARING, 160L)
        }
        val panel = glassPanel().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(13.dp(), 13.dp(), 13.dp(), 13.dp())
        }
        panel.addView(sectionTitle(if (appLanguage == "id") "ARAH & MIRROR SPEKTRUM" else "SPECTRUM FLOW & MIRROR"))
        panel.addView(subtitle(
            if (appLanguage == "id") "Atur urutan frekuensi dan arah pantulan bar." else "Set frequency order and the visual reflection of the bars."
        ).apply { setPadding(0, 4.dp(), 0, 10.dp()) })
        addFloatingProfileSectionControls(panel, "MODE")

        val flowBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        flowBlock.addView(subtitle(if (appLanguage == "id") "ARAH FREKUENSI" else "SPECTRUM FLOW"))
        val flowButton = glassButton("")
        flowButton.gravity = Gravity.CENTER_VERTICAL
        flowButton.textSize = 10f
        flowButton.maxLines = 1
        flowButton.ellipsize = android.text.TextUtils.TruncateAt.END
        flowButton.setPadding(14.dp(), 11.dp(), 14.dp(), 11.dp())
        flowButton.setOnClickListener { showHorizontalChoiceDialog(mirrorChoice = false) }
        flowBlock.addView(flowButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()).apply {
            topMargin = 5.dp()
        })
        panel.addView(flowBlock)
        horizontalFlowValueButton = flowButton

        val mirrorBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 11.dp(), 0, 0)
        }
        mirrorBlock.addView(subtitle(if (appLanguage == "id") "MODE MIRROR" else "MIRROR MODE"))
        val mirrorButton = glassButton("")
        mirrorButton.gravity = Gravity.CENTER_VERTICAL
        mirrorButton.textSize = 10f
        mirrorButton.maxLines = 1
        mirrorButton.ellipsize = android.text.TextUtils.TruncateAt.END
        mirrorButton.setPadding(14.dp(), 11.dp(), 14.dp(), 11.dp())
        mirrorButton.setOnClickListener { showHorizontalChoiceDialog(mirrorChoice = true) }
        mirrorBlock.addView(mirrorButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()).apply {
            topMargin = 5.dp()
        })
        panel.addView(mirrorBlock)
        horizontalMirrorValueButton = mirrorButton

        val bandInfo = subtitle("").apply {
            setPadding(2.dp(), 11.dp(), 2.dp(), 0)
            textSize = 10f
        }
        panel.addView(bandInfo)
        horizontalBandInfoLabel = bandInfo

        horizontalModeContainer.addView(panel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12.dp() })
        panel.visibility = if (isVerticalMode) View.GONE else View.VISIBLE
        horizontalFlowPanel = panel
        updateHorizontalFlowButtons()
    }

    private fun flowDisplayLabel(value: String): String = when (value) {
        "CENTER" -> if (appLanguage == "id") "TENGAH · RENDAH → TINGGI → RENDAH" else "CENTER · LOW → HIGH → LOW"
        "RIGHT" -> if (appLanguage == "id") "TINGGI → RENDAH" else "HIGH → LOW"
        else -> if (appLanguage == "id") "RENDAH → TINGGI" else "LOW → HIGH"
    }

    private fun mirrorDisplayLabel(value: String): String = when (value) {
        "VERTICAL" -> if (appLanguage == "id") "VERTIKAL · ATAS / BAWAH" else "VERTICAL · TOP / BOTTOM"
        "HORIZONTAL" -> if (appLanguage == "id") "HORIZONTAL · KIRI ↔ KANAN" else "HORIZONTAL · LEFT ↔ RIGHT"
        else -> if (appLanguage == "id") "OFF · TANPA PANTULAN" else "OFF · NO REFLECTION"
    }

    private fun showHorizontalChoiceDialog(mirrorChoice: Boolean) {
        val options = if (mirrorChoice) {
            listOf("OFF", "VERTICAL", "HORIZONTAL")
        } else {
            listOf("LEFT", "CENTER", "RIGHT")
        }
        val title = if (mirrorChoice) {
            if (appLanguage == "id") "PILIH MODE MIRROR" else "SELECT MIRROR MODE"
        } else {
            if (appLanguage == "id") "PILIH ARAH SPEKTRUM" else "SELECT SPECTRUM FLOW"
        }
        val bodyPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(7.dp(), 7.dp(), 7.dp(), 7.dp())
        }
        val body = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            background = null
            addView(bodyPanel)
        }
        var dialog: AlertDialog? = null
        options.forEach { option ->
            val isSelected = if (mirrorChoice) mirrorMode == option else horizontalAlignment == option
            val label = if (mirrorChoice) mirrorDisplayLabel(option) else flowDisplayLabel(option)
            val button = glassButton((if (isSelected) "✓  " else "   ") + label).apply {
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(13.dp(), 10.dp(), 13.dp(), 10.dp())
                applyPillStyle(this, isSelected)
            }
            bodyPanel.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()).apply {
                bottomMargin = 7.dp()
            })
            button.setOnClickListener {
                if (mirrorChoice) mirrorMode = option else horizontalAlignment = option
                updateHorizontalFlowButtons()
                buildSpectrumBars()
                buildLiveFrequencyLabels()
                updateSpectrumBandLabels(animated = true)
                saveSettings()
                updateFloatingService()
                dialog?.dismiss()
            }
        }
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val close = glassButton(if (appLanguage == "id") "TUTUP" else "CLOSE")
        footer.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()))
        dialog = showGlassStudioDialog(title, body, footer, 0.92f, 0.58f)
        close.setOnClickListener { dialog?.dismiss() }
    }

    private fun updateHorizontalFlowButtons() {
        val flowButton = horizontalFlowValueButton
        flowButton?.text = "⌄   ${flowDisplayLabel(horizontalAlignment)}"
        flowButton?.let { applyPillStyle(it, horizontalAlignment != "LEFT") }

        val mirrorButton = horizontalMirrorValueButton
        mirrorButton?.text = "⌄   ${mirrorDisplayLabel(mirrorMode)}"
        mirrorButton?.let { applyPillStyle(it, mirrorMode != "OFF") }
        horizontalBandInfoLabel?.text = if (appLanguage == "id") {
            "$barCount BAR · 20 Hz–20 kHz · TIAP BAR PUNYA RENTANG LOGARITMIK SENDIRI"
        } else {
            "$barCount BANDS · 20 Hz–20 kHz · EACH BAR HAS ITS OWN LOGARITHMIC RANGE"
        }
        horizontalFlowPanel?.let { panel ->
            val shouldShow = !isVerticalMode
            if (shouldShow && panel.visibility != View.VISIBLE) {
                panel.animate().cancel()
                panel.visibility = View.VISIBLE
                panel.alpha = 0f
                panel.animate().alpha(1f).setDuration(190L).start()
            } else if (!shouldShow && panel.visibility == View.VISIBLE) {
                panel.animate().cancel()
                panel.animate().alpha(0f).setDuration(140L).withEndAction {
                    if (isVerticalMode) panel.visibility = View.GONE
                }.start()
            }
        }
    }

    private fun studioDividerDrawable(): GradientDrawable {
        val line = if (isLightTheme()) Color.argb(112, 76, 92, 116) else Color.argb(132, 180, 199, 229)
        val transparent = Color.argb(0, Color.red(line), Color.green(line), Color.blue(line))
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(transparent, line, line, transparent)
        ).apply { cornerRadius = 2.dp().toFloat() }
    }

    private fun addControlSectionDivider(panel: LinearLayout, title: String) {
        val header = sectionTitle(title).apply {
            textSize = 11f
            includeFontPadding = false
            letterSpacing = 0.055f
            setPadding(1.dp(), 15.dp(), 1.dp(), 7.dp())
        }
        panel.addView(header)
        View(this).apply { background = studioDividerDrawable() }.also { line ->
            panel.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2.dp()).apply {
                leftMargin = 3.dp(); rightMargin = 3.dp(); topMargin = 2.dp(); bottomMargin = 9.dp()
            })
        }
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
            minimumHeight = 42.dp()
            setPadding(0, 10.dp(), 0, 4.dp())
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
            LinearLayout.LayoutParams(76.dp(), 36.dp()).apply { leftMargin = 12.dp() }
        )
        header.addView(
            unitLabel,
            LinearLayout.LayoutParams(48.dp(), 36.dp()).apply { leftMargin = 7.dp() }
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
            invalidateLivePreviewProfileCache()
            if (::barsContainer.isInitialized) previewBars.forEach { it.invalidate() }
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
                    invalidateLivePreviewProfileCache()
                    if (::barsContainer.isInitialized) previewBars.forEach { it.invalidate() }
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
                    updateHandler.removeCallbacks(barCountPreviewRebuild)
                    // Persist the edited selected layer before rebuilding labels. Otherwise
                    // Live Frequency labels can read the previous profile snapshot (e.g. 18
                    // bars) while the floating overlay already received the new count (20).
                    saveSettings()
                    refreshLivePreviewAfterProfileEdit()
                    updateFloatingService()
                }
        })

        panel.addView(header)
        panel.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 3.dp()
            bottomMargin = 3.dp()
        })
        // The separator closes the entire setting group, so it is spaced away from
        // both the numeric field and the seekbar rather than cutting through the row.
        View(this).apply { background = studioDividerDrawable() }.also { divider ->
            panel.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2.dp()).apply {
                leftMargin = 5.dp(); rightMargin = 5.dp(); topMargin = 4.dp(); bottomMargin = 11.dp()
            })
        }
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
    // FLOATING PROFILE LAYERS — independent appearance and placement
    // -----------------------------------------------------------------

    private data class FloatingLayerProfile(
        val id: String,
        val name: String,
        val isDefault: Boolean,
        val colors: List<Int>,
        val effect: String,
        val widthDp: Int,
        val heightDp: Int,
        val barCount: Int,
        val barWidthDp: Int,
        val gapDp: Int,
        val scalePercent: Int,
        val opacityPercent: Int,
        val shadowOpacityPercent: Int,
        val glowPercent: Int,
        val responsePercent: Int,
        val cornerRadiusDp: Int,
        val verticalMode: Boolean,
        val singleAudioMode: String,
        val bassMinHz: Int,
        val bassMaxHz: Int,
        val horizontalAlignment: String,
        val mirrorMode: String,
        val positionXdp: Int,
        val positionYdp: Int,
        val positionMode: String,
        val rotationDegrees: Int,
        val visible: Boolean = true,
        val effectGlowValues: Map<String, Int> = emptyMap(),
        val appearancePresetName: String? = null
    )

    private fun currentFloatingLayerSnapshot(
        id: String = selectedFloatingProfileId,
        name: String? = null,
        isDefault: Boolean? = null,
        metadata: FloatingLayerProfile? = null
    ): FloatingLayerProfile {
        val old = metadata ?: readFloatingProfileLibrary().firstOrNull { it.id == id }
        return FloatingLayerProfile(
            id = id,
            name = name ?: old?.name ?: "Default",
            isDefault = isDefault ?: old?.isDefault ?: (id == "root"),
            colors = spectrumColors.take(5).toList(),
            effect = barEffect,
            widthDp = overlayWidthDp,
            heightDp = overlayHeightDp,
            barCount = barCount,
            barWidthDp = barWidthDp,
            gapDp = barGapDp,
            scalePercent = scalePercent,
            opacityPercent = opacityPercent,
            shadowOpacityPercent = shadowOpacityPercent,
            glowPercent = if (barEffect == EFFECT_CLEAN) 0 else glowPercent,
            responsePercent = responsePercent,
            cornerRadiusDp = cornerRadiusDp,
            verticalMode = isVerticalMode,
            singleAudioMode = singleAudioMode,
            bassMinHz = bassMinHz,
            bassMaxHz = bassMaxHz,
            horizontalAlignment = horizontalAlignment,
            mirrorMode = mirrorMode,
            positionXdp = positionXdp,
            positionYdp = positionYdp,
            positionMode = positionMode,
            rotationDegrees = rotationDegrees,
            visible = old?.visible ?: true,
            effectGlowValues = old?.effectGlowValues ?: emptyMap(),
            appearancePresetName = old?.appearancePresetName
        )
    }

    private fun readFloatingProfileLibrary(): MutableList<FloatingLayerProfile> {
        val raw = prefs.getString("floating_profiles", null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<FloatingLayerProfile>()
            for (index in 0 until minOf(array.length(), 12)) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim().ifBlank { "layer_${index + 1}" }
                val name = item.optString("name").trim().ifBlank { if (index == 0) "Default" else "Profile ${index + 1}" }.take(28)
                val colorArray = item.optJSONArray("colors") ?: JSONArray()
                val colors = mutableListOf<Int>()
                for (colorIndex in 0 until colorArray.length()) {
                    try { colors.add(Color.parseColor(colorArray.optString(colorIndex))) } catch (_: Exception) {}
                }
                if (colors.size < 2) continue
                while (colors.size < 5) colors.add(colors.last())
                val profileEffect = item.optString("effect", EFFECT_GLOSSY)
                    .takeIf { it in setOf(EFFECT_CLEAN, EFFECT_GLOSSY, EFFECT_NEON, EFFECT_GLASS) } ?: EFFECT_GLOSSY
                result.add(FloatingLayerProfile(
                    id = id,
                    name = name,
                    isDefault = item.optBoolean("default", index == 0),
                    colors = colors.take(5),
                    effect = profileEffect,
                    widthDp = item.optInt("width", 320).coerceIn(1, 4096),
                    heightDp = item.optInt("height", 100).coerceIn(1, 4096),
                    barCount = item.optInt("bar_count", 18).coerceIn(1, MAX_BAR_COUNT),
                    barWidthDp = item.optInt("bar_width", 12).coerceIn(1, 2048),
                    gapDp = item.optInt("bar_gap", 3).coerceIn(0, 512),
                    scalePercent = item.optInt("scale", 100).coerceIn(1, 500),
                    opacityPercent = item.optInt("opacity", 100).coerceIn(0, 100),
                    shadowOpacityPercent = item.optInt("shadow_opacity", 100).coerceIn(0, 100),
                    glowPercent = item.optInt("glow", defaultGlowForEffect(profileEffect)).let { if (profileEffect == EFFECT_CLEAN) 0 else it.coerceIn(0, 100) },
                    responsePercent = item.optInt("response_speed", 82).coerceIn(0, 100),
                    cornerRadiusDp = item.optInt("corner_radius", 14).coerceIn(0, 512),
                    verticalMode = item.optBoolean("vertical_mode", false),
                    singleAudioMode = item.optString("single_audio_mode", "MUSIC").takeIf { it == "DJ" || it == "MUSIC" } ?: "MUSIC",
                    bassMinHz = item.optInt("bass_min_hz", 20).coerceIn(0, 20000),
                    bassMaxHz = item.optInt("bass_max_hz", 125).coerceIn(0, 20000),
                    horizontalAlignment = item.optString("horizontal_alignment", "LEFT").takeIf { it in setOf("LEFT", "CENTER", "RIGHT") } ?: "LEFT",
                    mirrorMode = item.optString("mirror_mode", "OFF").takeIf { it in setOf("OFF", "VERTICAL", "HORIZONTAL") } ?: "OFF",
                    positionXdp = item.optInt("position_x", 0).coerceIn(0, 100000),
                    positionYdp = item.optInt("position_y", 0).coerceIn(0, 100000),
                    positionMode = item.optString("position_mode", "STATIC").takeIf { it == "STATIC" || it == "DYNAMIC" } ?: "STATIC",
                    rotationDegrees = item.optInt("rotation_degrees", 0).coerceIn(0, 359),
                    visible = item.optBoolean("visible", true),
                    effectGlowValues = item.optJSONObject("effect_glow_values")?.let { values ->
                        listOf(EFFECT_CLEAN, EFFECT_GLOSSY, EFFECT_NEON, EFFECT_GLASS)
                            .mapNotNull { key -> if (values.has(key)) key to values.optInt(key, 0).coerceIn(0, 100) else null }
                            .toMap()
                    } ?: emptyMap(),
                    appearancePresetName = item.optString("appearance_preset", "").trim().takeIf { it.isNotEmpty() }
                ))
            }
            result
        } catch (_: Exception) { mutableListOf() }
    }

    private fun writeFloatingProfileLibrary(profiles: List<FloatingLayerProfile>) {
        // Auto-generated Profile N labels follow their current layer slot after
        // add/delete/reorder. User-defined names are preserved verbatim.
        val normalizedProfiles = profiles.take(12).mapIndexed { index, profile ->
            when {
                index == 0 && (profile.isDefault || profile.id == "root") ->
                    profile.copy(id = "root", name = "Default", isDefault = true)
                profile.name.matches(Regex("Profile \\d+")) ->
                    profile.copy(name = "Profile ${index + 1}", isDefault = false)
                else -> profile.copy(isDefault = false)
            }
        }
        val array = JSONArray()
        normalizedProfiles.forEach { profile ->
            val item = JSONObject()
            item.put("id", profile.id)
            item.put("name", profile.name)
            item.put("default", profile.isDefault)
            item.put("colors", JSONArray(profile.colors.take(5).map { String.format("#%06X", 0xFFFFFF and it) }))
            item.put("effect", profile.effect)
            item.put("width", profile.widthDp)
            item.put("height", profile.heightDp)
            item.put("bar_count", profile.barCount)
            item.put("bar_width", profile.barWidthDp)
            item.put("bar_gap", profile.gapDp)
            item.put("scale", profile.scalePercent)
            item.put("opacity", profile.opacityPercent)
            item.put("shadow_opacity", profile.shadowOpacityPercent)
            item.put("glow", profile.glowPercent)
            item.put("response_speed", profile.responsePercent)
            item.put("corner_radius", profile.cornerRadiusDp)
            item.put("vertical_mode", profile.verticalMode)
            item.put("single_audio_mode", profile.singleAudioMode)
            item.put("bass_min_hz", profile.bassMinHz)
            item.put("bass_max_hz", profile.bassMaxHz)
            item.put("horizontal_alignment", profile.horizontalAlignment)
            item.put("mirror_mode", profile.mirrorMode)
            item.put("position_x", profile.positionXdp)
            item.put("position_y", profile.positionYdp)
            item.put("position_mode", profile.positionMode)
            item.put("rotation_degrees", profile.rotationDegrees)
            item.put("visible", profile.visible)
            item.put("effect_glow_values", JSONObject().apply {
                profile.effectGlowValues.forEach { (effect, value) -> put(effect, value.coerceIn(0, 100)) }
            })
            if (profile.appearancePresetName.isNullOrBlank()) item.remove("appearance_preset")
            else item.put("appearance_preset", profile.appearancePresetName)
            array.put(item)
        }
        prefs.edit().putString("floating_profiles", array.toString()).apply()
        invalidateLivePreviewProfileCache()
        refreshFloatingSceneManagerStatus()
    }

    // -----------------------------------------------------------------
    // FLOATING SCENE MANAGER — full multi-layer project presets
    // A scene contains the complete Floating Profile layer list, each layer's
    // mode/colour/appearance/response/position, and the selected layer ID.
    // -----------------------------------------------------------------

    private fun readFloatingScenePresets(): MutableList<JSONObject> {
        val raw = prefs.getString("floating_scene_presets", null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<JSONObject>()
            for (index in 0 until minOf(array.length(), MAX_FLOATING_SCENES)) {
                val item = array.optJSONObject(index) ?: continue
                val layers = item.optJSONArray("layers") ?: continue
                if (layers.length() !in 1..12) continue
                if (item.optString("format") != "com.floatingdj.floating-scene") continue
                result.add(JSONObject(item.toString()))
            }
            result
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun writeFloatingScenePresets(scenes: List<JSONObject>) {
        val array = JSONArray()
        scenes.take(MAX_FLOATING_SCENES).forEach { array.put(JSONObject(it.toString())) }
        prefs.edit().putString("floating_scene_presets", array.toString()).apply()
        refreshFloatingSceneManagerStatus()
    }

    private fun createCurrentFloatingScene(name: String): JSONObject {
        syncSelectedFloatingProfileFromCurrent()
        val rawLayers = prefs.getString("floating_profiles", "[]") ?: "[]"
        return JSONObject().apply {
            put("format", "com.floatingdj.floating-scene")
            put("schema_version", FLOATING_SCENE_SCHEMA_VERSION)
            put("name", name.take(40))
            put("selected_profile_id", selectedFloatingProfileId)
            put("floating_overlay_requested", floatingOverlayEnabled)
            put("layers", JSONArray(rawLayers))
            put("updated_at", System.currentTimeMillis())
        }
    }

    private fun canonicalJsonValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> {
            val keys = mutableListOf<String>()
            val iterator = value.keys()
            while (iterator.hasNext()) keys.add(iterator.next())
            keys.sorted().joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                JSONObject.quote(key) + ":" + canonicalJsonValue(value.opt(key))
            }
        }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]", separator = ",") {
            canonicalJsonValue(value.opt(it))
        }
        is String -> JSONObject.quote(value)
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }

    private fun activeFloatingSceneMatchesCurrent(): Boolean {
        val activeId = prefs.getString("active_floating_scene_id", null) ?: return false
        val active = readFloatingScenePresets().firstOrNull { it.optString("id") == activeId } ?: return false
        val currentLayers = try { JSONArray(prefs.getString("floating_profiles", "[]") ?: "[]") }
            catch (_: Exception) { return false }
        val savedLayers = active.optJSONArray("layers") ?: return false
        val currentSelected = prefs.getString("selected_floating_profile_id", selectedFloatingProfileId)
            ?: selectedFloatingProfileId
        val currentOverlay = prefs.getBoolean("floating_overlay_enabled", floatingOverlayEnabled)
        return canonicalJsonValue(savedLayers) == canonicalJsonValue(currentLayers) &&
            active.optString("selected_profile_id", "root") == currentSelected &&
            active.optBoolean("floating_overlay_requested", currentOverlay) == currentOverlay
    }

    private fun refreshFloatingSceneManagerStatus() {
        val status = findViewById<TextView>(R.id.txtFloatingSceneStatus) ?: return
        val scenes = readFloatingScenePresets()
        val activeId = prefs.getString("active_floating_scene_id", null)
        val active = scenes.firstOrNull { it.optString("id") == activeId }
        val currentRaw = prefs.getString("floating_profiles", "[]") ?: "[]"
        val currentSelected = prefs.getString("selected_floating_profile_id", selectedFloatingProfileId)
            ?: selectedFloatingProfileId
        val matches = active != null && activeFloatingSceneMatchesCurrent()
        status.text = when {
            matches -> if (appLanguage == "id") {
                "●  SCENE TERSIMPAN  ·  ${active?.optString("name", "Scene")}  ·  ${active?.optJSONArray("layers")?.length() ?: 0} LAYER"
            } else {
                "●  SAVED SCENE  ·  ${active?.optString("name", "Scene")}  ·  ${active?.optJSONArray("layers")?.length() ?: 0} LAYERS"
            }
            active != null -> if (appLanguage == "id") "●  CURRENT STATE  ·  ADA PERUBAHAN BELUM DISIMPAN" else "●  CURRENT STATE  ·  UNSAVED CHANGES"
            else -> if (appLanguage == "id") "●  CURRENT STATE  ·  BELUM DISIMPAN" else "●  CURRENT STATE  ·  NOT SAVED"
        }
        status.contentDescription = status.text
    }

    private fun promptSaveFloatingSceneAs(onSaved: ((String) -> Unit)? = null) {
        val scenes = readFloatingScenePresets()
        if (scenes.size >= MAX_FLOATING_SCENES) {
            Toast.makeText(this, if (appLanguage == "id") "Maksimal $MAX_FLOATING_SCENES scene tersimpan" else "Maximum $MAX_FLOATING_SCENES saved scenes", Toast.LENGTH_SHORT).show()
            return
        }
        showProfileNameDialog(
            if (appLanguage == "id") "SIMPAN FLOATING SCENE" else "SAVE FLOATING SCENE AS",
            if (appLanguage == "id") "Scene ${scenes.size + 1}" else "Scene ${scenes.size + 1}"
        ) { name ->
            val existing = readFloatingScenePresets()
            if (existing.any { it.optString("name").equals(name, ignoreCase = true) }) {
                Toast.makeText(this, if (appLanguage == "id") "Nama scene sudah dipakai" else "A scene with that name already exists", Toast.LENGTH_SHORT).show()
                return@showProfileNameDialog
            }
            val scene = createCurrentFloatingScene(name)
            scene.put("id", "scene_${java.util.UUID.randomUUID().toString().take(10)}")
            existing.add(scene)
            writeFloatingScenePresets(existing)
            prefs.edit().putString("active_floating_scene_id", scene.optString("id")).apply()
            refreshFloatingSceneManagerStatus()
            onSaved?.invoke(scene.optString("id"))
            Toast.makeText(this, if (appLanguage == "id") "Semua layer berhasil disimpan sebagai scene" else "All floating layers saved as a scene", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyFloatingScene(scene: JSONObject, commitAsActive: Boolean): Boolean {
        val layerArray = scene.optJSONArray("layers") ?: return false
        if (layerArray.length() !in 1..12) return false

        val previousRaw = prefs.getString("floating_profiles", "[]") ?: "[]"
        prefs.edit().putString("floating_profiles", layerArray.toString()).commit()
        val parsed = readFloatingProfileLibrary()
        if (parsed.size != layerArray.length()) {
            prefs.edit().putString("floating_profiles", previousRaw).commit()
            return false
        }
        writeFloatingProfileLibrary(parsed)

        val requestedId = scene.optString("selected_profile_id", "root")
        val selected = parsed.firstOrNull { it.id == requestedId }
            ?: parsed.firstOrNull { it.id == "root" }
            ?: parsed.first()
        selectedFloatingProfileId = selected.id
        livePreviewProfileId = selected.id
        prefs.edit()
            .putString("selected_floating_profile_id", selected.id)
            .putString("live_preview_profile_id", selected.id)
            .putString("last_interacted_floating_profile_id", selected.id)
            .apply()

        // Preview and Load both apply the scene's overlay preference. Preview is
        // temporary: showFloatingSceneManager restores the original toggle/state
        // when the user closes the manager without committing. A saved scene must
        // preview its actual visibility state, not only its layer dimensions.
        val requestedOverlay = scene.optBoolean("floating_overlay_requested", floatingOverlayEnabled)
        floatingOverlayEnabled = requestedOverlay && Settings.canDrawOverlays(this)
        prefs.edit().putBoolean("floating_overlay_enabled", floatingOverlayEnabled).apply()
        if (::floatingOverlaySwitch.isInitialized) setFloatingOverlaySwitch(floatingOverlayEnabled)

        if (commitAsActive) {
            val activeId = scene.optString("id").takeIf { it.isNotBlank() }
            prefs.edit().putString("active_floating_scene_id", activeId).apply()
        }

        applyFloatingProfileToControls(selected, updateService = true)
        refreshFloatingSceneManagerStatus()
        return true
    }

    private fun showFloatingSceneManager() {
        val scenes = readFloatingScenePresets()
        val originalLayers = prefs.getString("floating_profiles", "[]") ?: "[]"
        val originalSelected = prefs.getString("selected_floating_profile_id", selectedFloatingProfileId) ?: selectedFloatingProfileId
        val originalLivePreview = prefs.getString("live_preview_profile_id", livePreviewProfileId) ?: livePreviewProfileId
        val originalLastInteracted = prefs.getString("last_interacted_floating_profile_id", originalSelected) ?: originalSelected
        val originalActiveScene = prefs.getString("active_floating_scene_id", null)
        val originalOverlayRequested = floatingOverlayEnabled
        val activeIsCurrent = activeFloatingSceneMatchesCurrent()
        // The saved scene is selected only while the live library still matches it.
        // Any edit makes Current State the sole selected row.
        var selectedSceneId = if (activeIsCurrent) originalActiveScene ?: "CURRENT" else "CURRENT"
        var previewing = false
        var committed = false

        val bodyScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            background = null
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(10.dp(), 10.dp(), 10.dp(), 14.dp())
        }
        val statusText = subtitle(
            if (appLanguage == "id") "Pilih Current State atau scene tersimpan. Ketuk scene untuk langsung melihat preview lengkap, termasuk posisi setiap layer."
            else "Choose Current State or a saved scene. Selecting a row instantly previews all layers, including their saved positions."
        )
        body.addView(statusText)
        val sceneRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(sceneRows, matchWrap().apply { topMargin = 10.dp() })
        bodyScroll.addView(body)

        val loadButton = glassButton(if (appLanguage == "id") "MUAT PILIHAN" else "LOAD SELECTED")
        val saveChangesButton = glassButton(if (appLanguage == "id") "SIMPAN PERUBAHAN" else "SAVE CHANGES")
        val renameButton = glassButton(if (appLanguage == "id") "GANTI NAMA" else "RENAME")
        val deleteButton = glassButton(if (appLanguage == "id") "HAPUS" else "DELETE")
        val cancelButton = glassButton(if (appLanguage == "id") "TUTUP" else "CLOSE")

        fun sceneDescription(scene: JSONObject): String {
            val count = scene.optJSONArray("layers")?.length() ?: 0
            val selectedName = scene.optString("selected_profile_id", "root")
            var selectedLayerName = "Default"
            scene.optJSONArray("layers")?.let { layers ->
                for (i in 0 until layers.length()) {
                    val layer = layers.optJSONObject(i) ?: continue
                    if (layer.optString("id") == selectedName) {
                        selectedLayerName = layer.optString("name", "Default")
                        break
                    }
                }
            }
            return "$count layers  ·  Active: $selectedLayerName"
        }

        fun restoreOriginalScene() {
            if (!previewing || committed) return
            prefs.edit()
                .putString("floating_profiles", originalLayers)
                .putString("selected_floating_profile_id", originalSelected)
                .putString("live_preview_profile_id", originalLivePreview)
                .putString("last_interacted_floating_profile_id", originalLastInteracted)
                .putBoolean("floating_overlay_enabled", originalOverlayRequested)
                .apply()
            floatingOverlayEnabled = originalOverlayRequested && Settings.canDrawOverlays(this)
            if (::floatingOverlaySwitch.isInitialized) setFloatingOverlaySwitch(floatingOverlayEnabled)
            if (originalActiveScene == null) prefs.edit().remove("active_floating_scene_id").apply()
            else prefs.edit().putString("active_floating_scene_id", originalActiveScene).apply()
            selectedFloatingProfileId = originalSelected
            livePreviewProfileId = originalLivePreview
            val restored = readFloatingProfileLibrary().firstOrNull { it.id == originalSelected }
                ?: readFloatingProfileLibrary().firstOrNull()
            if (restored != null) applyFloatingProfileToControls(restored, updateService = true)
            previewing = false
            refreshFloatingSceneManagerStatus()
        }

        fun previewSelection() {
            if (selectedSceneId == "CURRENT") {
                restoreOriginalScene()
                statusText.text = if (appLanguage == "id") "Current State aktif. Preview kembali ke konfigurasi yang sedang kamu pakai." else "Current State is active. Preview restored to your current configuration."
                return
            }
            val target = readFloatingScenePresets().firstOrNull { it.optString("id") == selectedSceneId }
            if (target == null || !applyFloatingScene(target, commitAsActive = false)) {
                statusText.text = if (appLanguage == "id") "Scene tidak valid atau tidak bisa dipreview." else "This scene is invalid or could not be previewed."
                return
            }
            previewing = true
            statusText.text = if (appLanguage == "id") {
                "Preview aktif: ${target.optString("name")}. Semua layer, posisi, mode, warna, dan Bar Appearance sedang ditampilkan sementara."
            } else {
                "Live preview: ${target.optString("name")}. Every layer, position, mode, color, and Bar Appearance is temporarily applied."
            }
        }

        fun renderRows() {
            sceneRows.removeAllViews()
            val currentRow = glassButton("")
            currentRow.gravity = Gravity.CENTER_VERTICAL
            currentRow.text = (if (selectedSceneId == "CURRENT") "●  " else "○  ") +
                (if (appLanguage == "id") "Current State · Kondisi sekarang" else "Current State · Live configuration")
            currentRow.setPadding(13.dp(), 11.dp(), 13.dp(), 11.dp())
            applyPillStyle(currentRow, selectedSceneId == "CURRENT")
            currentRow.setOnClickListener {
                selectedSceneId = "CURRENT"
                previewSelection()
                renderRows()
            }
            sceneRows.addView(currentRow, matchWrap().apply { bottomMargin = 7.dp() })

            readFloatingScenePresets().forEach { scene ->
                val id = scene.optString("id")
                val selected = selectedSceneId == id
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(13.dp(), 11.dp(), 13.dp(), 11.dp())
                    background = pillDrawable(selected, isLightTheme(), currentAccent(), 15)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        selectedSceneId = id
                        previewSelection()
                        renderRows()
                    }
                }
                val title = TextView(this).apply {
                    text = (if (selected) "●  " else "○  ") + scene.optString("name", "Floating Scene")
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(pillForegroundColor(isLightTheme(), selected))
                }
                val detail = subtitle(sceneDescription(scene)).apply {
                    setTextColor(if (isLightTheme()) Color.parseColor("#66758C") else Color.parseColor("#AAB7CC"))
                    setPadding(22.dp(), 4.dp(), 0, 0)
                }
                row.addView(title)
                row.addView(detail)
                sceneRows.addView(row, matchWrap().apply { bottomMargin = 7.dp() })
            }
        }

        val footerTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        footerTop.addView(loadButton, LinearLayout.LayoutParams(0, 43.dp(), 1f).apply { setMargins(2.dp(), 0, 4.dp(), 0) })
        footerTop.addView(saveChangesButton, LinearLayout.LayoutParams(0, 43.dp(), 1f).apply { setMargins(4.dp(), 0, 2.dp(), 0) })
        val footerMiddle = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6.dp(), 0, 0) }
        footerMiddle.addView(renameButton, LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { setMargins(2.dp(), 0, 3.dp(), 0) })
        footerMiddle.addView(deleteButton, LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { setMargins(3.dp(), 0, 2.dp(), 0) })
        val footerBottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6.dp(), 0, 0) }
        footerBottom.addView(cancelButton, LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { setMargins(2.dp(), 0, 2.dp(), 0) })
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(footerTop)
            addView(footerMiddle)
            addView(footerBottom)
        }
        val dialog = showGlassStudioDialog(
            if (appLanguage == "id") "FLOATING SCENE MANAGER" else "FLOATING SCENE MANAGER",
            bodyScroll, footer, 0.96f, 0.88f
        )

        renderRows()
        // A stale saved-scene ID must never win over an unsaved Current State.
        if (selectedSceneId != "CURRENT") previewSelection()

        loadButton.setOnClickListener {
            if (selectedSceneId == "CURRENT") {
                restoreOriginalScene()
                committed = true
                dialog.dismiss()
                return@setOnClickListener
            }
            val target = readFloatingScenePresets().firstOrNull { it.optString("id") == selectedSceneId } ?: return@setOnClickListener
            if (applyFloatingScene(target, commitAsActive = true)) {
                committed = true
                previewing = false
                dialog.dismiss()
                Toast.makeText(this, if (appLanguage == "id") "Scene dimuat lengkap" else "Complete floating scene loaded", Toast.LENGTH_SHORT).show()
            }
        }

        saveChangesButton.setOnClickListener {
            if (selectedSceneId == "CURRENT") {
                promptSaveFloatingSceneAs { newId ->
                    selectedSceneId = newId
                    committed = true
                    previewing = false
                    refreshFloatingSceneManagerStatus()
                    renderRows()
                }
                return@setOnClickListener
            }
            val updated = readFloatingScenePresets()
            val index = updated.indexOfFirst { it.optString("id") == selectedSceneId }
            if (index < 0) return@setOnClickListener
            val old = updated[index]
            val fresh = createCurrentFloatingScene(old.optString("name", "Floating Scene"))
            fresh.put("id", selectedSceneId)
            updated[index] = fresh
            writeFloatingScenePresets(updated)
            prefs.edit().putString("active_floating_scene_id", selectedSceneId).apply()
            committed = true
            previewing = false
            refreshFloatingSceneManagerStatus()
            dialog.dismiss()
            Toast.makeText(this, if (appLanguage == "id") "Perubahan scene disimpan" else "Scene changes saved", Toast.LENGTH_SHORT).show()
        }

        renameButton.setOnClickListener {
            if (selectedSceneId == "CURRENT") return@setOnClickListener
            val current = readFloatingScenePresets().firstOrNull { it.optString("id") == selectedSceneId } ?: return@setOnClickListener
            showProfileNameDialog(if (appLanguage == "id") "GANTI NAMA SCENE" else "RENAME SCENE", current.optString("name")) { newName ->
                val updated = readFloatingScenePresets()
                val entry = updated.firstOrNull { it.optString("id") == selectedSceneId } ?: return@showProfileNameDialog
                entry.put("name", newName.take(40))
                writeFloatingScenePresets(updated)
                renderRows()
            }
        }

        deleteButton.setOnClickListener {
            if (selectedSceneId == "CURRENT") return@setOnClickListener
            val target = readFloatingScenePresets().firstOrNull { it.optString("id") == selectedSceneId } ?: return@setOnClickListener
            showGlassConfirmDialog(
                if (appLanguage == "id") "HAPUS SCENE?" else "DELETE SCENE?",
                "${target.optString("name")} · ${target.optJSONArray("layers")?.length() ?: 0} layers",
                if (appLanguage == "id") "BATAL" else "CANCEL",
                if (appLanguage == "id") "HAPUS" else "DELETE"
            ) {
                if (previewing) restoreOriginalScene()
                writeFloatingScenePresets(readFloatingScenePresets().filterNot { it.optString("id") == selectedSceneId })
                if (prefs.getString("active_floating_scene_id", null) == selectedSceneId) {
                    prefs.edit().remove("active_floating_scene_id").apply()
                }
                selectedSceneId = "CURRENT"
                renderRows()
            }
        }
        cancelButton.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            restoreOriginalScene()
        }
    }

    private fun showFloatingSceneExportPicker() {
        val scenes = readFloatingScenePresets()
        var selectedId = "CURRENT"
        val bodyScroll = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp()) }
        body.addView(subtitle(if (appLanguage == "id") "Pilih Current State atau scene tersimpan. Satu file akan berisi semua layer dan posisi." else "Choose Current State or a saved scene. One file contains all layers and their positions."))
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(rows, matchWrap().apply { topMargin = 10.dp() })
        bodyScroll.addView(body)
        fun render() {
            rows.removeAllViews()
            val entries = mutableListOf<Pair<String, String>>()
            entries.add("CURRENT" to if (appLanguage == "id") "Current State · Semua layer saat ini" else "Current State · All current layers")
            scenes.forEach { entries.add(it.optString("id") to "${it.optString("name")} · ${it.optJSONArray("layers")?.length() ?: 0} layers") }
            entries.forEach { (id, label) ->
                val row = glassButton((if (selectedId == id) "●  " else "○  ") + label)
                applyPillStyle(row, selectedId == id)
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(13.dp(), 11.dp(), 13.dp(), 11.dp())
                row.setOnClickListener { selectedId = id; render() }
                rows.addView(row, matchWrap().apply { bottomMargin = 7.dp() })
            }
        }
        render()
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        val export = glassButton(if (appLanguage == "id") "EKSPOR SCENE" else "EXPORT SCENE")
        footer.addView(cancel, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(export, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(if (appLanguage == "id") "PILIH SCENE UNTUK EKSPOR" else "CHOOSE SCENE TO EXPORT", bodyScroll, footer, 0.94f, 0.76f)
        cancel.setOnClickListener { dialog.dismiss() }
        export.setOnClickListener {
            val payload = if (selectedId == "CURRENT") {
                createCurrentFloatingScene("Current State")
            } else {
                scenes.firstOrNull { it.optString("id") == selectedId }?.let { JSONObject(it.toString()) }
            }
            if (payload == null) return@setOnClickListener
            pendingFloatingSceneExportJson = payload.toString(2)
            pendingFloatingSceneExportName = payload.optString("name", "Floating Scene")
            val safeName = pendingFloatingSceneExportName.orEmpty().replace(Regex("[^A-Za-z0-9_-]"), "_").take(32)
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "FloatingDJ_${safeName}.fdjscene.json")
            }
            try {
                startActivityForResult(intent, REQUEST_EXPORT_FLOATING_SCENE)
                dialog.dismiss()
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, if (appLanguage == "id") "File picker tidak tersedia" else "No document picker is available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchFloatingSceneImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivityForResult(intent, REQUEST_IMPORT_FLOATING_SCENE) }
        catch (_: ActivityNotFoundException) {
            Toast.makeText(this, if (appLanguage == "id") "File picker tidak tersedia" else "No document picker is available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readDocumentTextLimited(uri: Uri, maxBytes: Int): String {
        val input = contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Unable to read selected file")
        input.use { stream ->
            val output = java.io.ByteArrayOutputStream(minOf(maxBytes, 8192))
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (total + count > maxBytes) throw IllegalArgumentException("Selected file is too large")
                output.write(buffer, 0, count)
                total += count
            }
            return String(output.toByteArray(), Charsets.UTF_8)
        }
    }

    private fun reviewImportedFloatingScene(uri: Uri) {
        try {
            val jsonText = readDocumentTextLimited(uri, 300_000)
            val imported = JSONObject(jsonText)
            require(imported.optString("format") == "com.floatingdj.floating-scene") { "Unsupported scene file" }
            require(imported.optInt("schema_version", 0) in 1..FLOATING_SCENE_SCHEMA_VERSION) { "Unsupported scene version" }
            val layers = imported.optJSONArray("layers") ?: throw IllegalArgumentException("Scene has no layers")
            require(layers.length() in 1..12) { "Scene must contain 1–12 layers" }
            val oldRaw = prefs.getString("floating_profiles", "[]") ?: "[]"
            val parsed = try {
                prefs.edit().putString("floating_profiles", layers.toString()).commit()
                readFloatingProfileLibrary()
            } finally {
                prefs.edit().putString("floating_profiles", oldRaw).commit()
            }
            require(parsed.size == layers.length()) { "Some Floating Profile layers are invalid" }
            require(readFloatingScenePresets().size < MAX_FLOATING_SCENES) {
                if (appLanguage == "id") "Scene Manager penuh. Hapus satu scene dulu sebelum impor." else "Scene Manager is full. Delete a saved scene before importing another."
            }
            val layerSummary = parsed.mapIndexed { index, profile -> "${index + 1}. ${profile.name} · ${if (profile.verticalMode) "Single" else "Horizontal"} · ${profile.positionXdp}, ${profile.positionYdp} dp" }
                .joinToString("\n")
            val summary = buildString {
                append("Scene: ").append(imported.optString("name", "Imported Scene"))
                append("\nLayers: ").append(parsed.size)
                append("\nSelected: ").append(imported.optString("selected_profile_id", "root"))
                append("\n\n").append(layerSummary)
                append("\n\nAndroid overlay permissions are device-specific. They are not transferred by this file.")
            }
            showGlassConfirmDialog(
                if (appLanguage == "id") "REVIEW IMPORT SCENE" else "REVIEW IMPORTED SCENE",
                summary,
                if (appLanguage == "id") "BATAL" else "CANCEL",
                if (appLanguage == "id") "IMPORT" else "IMPORT",
                0.94f,
                0.78f
            ) {
                val existing = readFloatingScenePresets()
                val newScene = JSONObject(imported.toString())
                newScene.put("id", "scene_${java.util.UUID.randomUUID().toString().take(10)}")
                var name = newScene.optString("name", "Imported Scene").take(40)
                var suffix = 2
                while (existing.any { it.optString("name").equals(name, true) }) {
                    name = "${newScene.optString("name", "Imported Scene").take(30)} $suffix"
                    suffix++
                }
                newScene.put("name", name)
                newScene.put("updated_at", System.currentTimeMillis())
                existing.add(newScene)
                writeFloatingScenePresets(existing)
                val canOverlay = Settings.canDrawOverlays(this)
                if (newScene.optBoolean("floating_overlay_requested", false) && !canOverlay) {
                    floatingOverlayEnabled = false
                    prefs.edit().putBoolean("floating_overlay_enabled", false).apply()
                    if (::floatingOverlaySwitch.isInitialized) setFloatingOverlaySwitch(false)
                }
                if (applyFloatingScene(newScene, commitAsActive = true)) {
                    Toast.makeText(this, if (appLanguage == "id") "Scene dan semua layer berhasil diimpor" else "Scene and all layers imported", Toast.LENGTH_SHORT).show()
                    if (newScene.optBoolean("floating_overlay_requested", false) && !canOverlay) {
                        Toast.makeText(this, if (appLanguage == "id") "Aktifkan izin Floating di Android jika ingin menampilkan overlay." else "Grant Android overlay permission to display the floating layers.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (error: Exception) {
            Toast.makeText(this, "${if (appLanguage == "id") "Impor scene gagal" else "Scene import failed"}: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureFloatingProfileLibrary() {
        var profiles = readFloatingProfileLibrary()
        if (profiles.isEmpty()) {
            profiles = mutableListOf(currentFloatingLayerSnapshot("root", "Default", true))
        } else {
            // Keep exactly one immutable root profile. A malformed/imported library
            // must never leave multiple rows marked as Default.
            val rootIndex = profiles.indexOfFirst { it.isDefault || it.id == "root" }
            if (rootIndex < 0) {
                profiles.add(0, currentFloatingLayerSnapshot("root", "Default", true))
            } else {
                val root = profiles.removeAt(rootIndex).copy(id = "root", name = "Default", isDefault = true)
                profiles.add(0, root)
            }
            for (index in 1 until profiles.size) {
                if (profiles[index].isDefault || profiles[index].id == "root") {
                    profiles[index] = profiles[index].copy(isDefault = false, id = "layer_${index}_${profiles[index].id}")
                }
                // Rename only generated names, never a user's custom layer name.
                if (profiles[index].name.matches(Regex("Profile \\d+"))) {
                    profiles[index] = profiles[index].copy(name = "Profile ${index + 1}")
                }
            }
        }
        selectedFloatingProfileId = selectedFloatingProfileId.takeIf { id -> profiles.any { it.id == id } } ?: "root"
        writeFloatingProfileLibrary(profiles)
        prefs.edit().putString("selected_floating_profile_id", selectedFloatingProfileId).apply()
    }

    private fun syncSelectedFloatingProfileFromCurrent() {
        if (suppressFloatingProfileSync) return
        val profiles = readFloatingProfileLibrary()
        if (profiles.isEmpty()) return
        val index = profiles.indexOfFirst { it.id == selectedFloatingProfileId }
        if (index < 0) return
        val old = profiles[index]
        profiles[index] = currentFloatingLayerSnapshot(old.id, old.name, old.isDefault)
        writeFloatingProfileLibrary(profiles)
    }

    private fun floatingProfileJson(includeLiveSelectedProfile: Boolean = false): String {
        val savedProfiles = readFloatingProfileLibrary()
        val profiles = if (includeLiveSelectedProfile) {
            savedProfiles.map { profile ->
                if (profile.id == selectedFloatingProfileId) {
                    currentFloatingLayerSnapshot(profile.id, profile.name, profile.isDefault)
                } else profile
            }
        } else savedProfiles
        val array = JSONArray()
        profiles.forEach { profile ->
            val item = JSONObject()
            item.put("id", profile.id)
            item.put("name", profile.name)
            item.put("default", profile.isDefault)
            item.put("colors", JSONArray(profile.colors.map { it }))
            item.put("effect", profile.effect)
            item.put("width", profile.widthDp)
            item.put("height", profile.heightDp)
            item.put("bar_count", profile.barCount)
            item.put("bar_width", profile.barWidthDp)
            item.put("bar_gap", profile.gapDp)
            item.put("scale", profile.scalePercent)
            item.put("opacity", profile.opacityPercent)
            item.put("shadow_opacity", profile.shadowOpacityPercent)
            item.put("glow", profile.glowPercent)
            item.put("response_speed", profile.responsePercent)
            item.put("corner_radius", profile.cornerRadiusDp)
            item.put("vertical_mode", profile.verticalMode)
            item.put("single_audio_mode", profile.singleAudioMode)
            item.put("bass_min_hz", profile.bassMinHz)
            item.put("bass_max_hz", profile.bassMaxHz)
            item.put("horizontal_alignment", profile.horizontalAlignment)
            item.put("mirror_mode", profile.mirrorMode)
            item.put("position_x", profile.positionXdp)
            item.put("position_y", profile.positionYdp)
            item.put("position_mode", profile.positionMode)
            item.put("rotation_degrees", profile.rotationDegrees)
            item.put("visible", profile.visible)
            item.put("effect_glow_values", JSONObject().apply {
                profile.effectGlowValues.forEach { (effectName, value) -> put(effectName, value.coerceIn(0, 100)) }
            })
            if (!profile.appearancePresetName.isNullOrBlank()) item.put("appearance_preset", profile.appearancePresetName)
            array.put(item)
        }
        return array.toString()
    }

    private fun applyFloatingProfileToControls(profile: FloatingLayerProfile, updateService: Boolean = true) {
        livePreviewProfileId = profile.id
        invalidateLivePreviewProfileCache()
        livePreviewBarLayoutSignature = ""
        livePreviewFrequencySignature = ""
        prefs.edit().putString("live_preview_profile_id", livePreviewProfileId).apply()
        suppressFloatingProfileSync = true
        try {
            spectrumColors.clear(); spectrumColors.addAll(profile.colors.take(5))
            while (spectrumColors.size < 5) spectrumColors.add(Color.WHITE)
            barEffect = profile.effect
            overlayWidthDp = profile.widthDp
            overlayHeightDp = profile.heightDp
            barCount = profile.barCount
            barWidthDp = profile.barWidthDp
            barGapDp = profile.gapDp
            scalePercent = profile.scalePercent
            opacityPercent = profile.opacityPercent
            shadowOpacityPercent = profile.shadowOpacityPercent
            glowPercent = profile.glowPercent
            responsePercent = profile.responsePercent
            cornerRadiusDp = profile.cornerRadiusDp
            isVerticalMode = profile.verticalMode
            singleAudioMode = profile.singleAudioMode
            bassMinHz = profile.bassMinHz
            bassMaxHz = profile.bassMaxHz
            horizontalAlignment = profile.horizontalAlignment
            mirrorMode = profile.mirrorMode
            positionXdp = profile.positionXdp
            positionYdp = profile.positionYdp
            positionInitialized = true
            positionMode = profile.positionMode
            rotationDegrees = profile.rotationDegrees
        } finally { suppressFloatingProfileSync = false }
        prefs.edit()
            .putString("selected_floating_profile_id", profile.id)
            .putString("live_preview_profile_id", profile.id)
            .putString("last_interacted_floating_profile_id", profile.id)
            .apply()
        lastAppliedFloatingProfileId = profile.id
        saveSettings()
        refreshColorSwatches()
        buildSpectrumBars()
        updateLivePreviewProfileButton()
        buildLiveFrequencyLabels()
        updateModeButtons(); updateSingleModeButtons(); updateSingleModeVisibility()
        updateEffectButtons(); updateHorizontalFlowButtons()
        buildFloatingControls()
        buildSingleModePanel()
        updateSingleModeButtons()
        updateSingleModeVisibility()
        buildFloatingProfileList()
        refreshFloatingProfileSectionPickers()
        applyTheme(animated = false)
        updateSpectrumBandLabels(animated = false)
        if (updateService) updateFloatingService()
    }

    /**
     * A floating overlay reports focus as soon as its dynamic window receives
     * ACTION_DOWN. Update the selected layer and in-app Live Spectrum without
     * sending a second layer rebuild back to the Service during an active drag.
     */
    private fun selectFloatingProfileFromOverlay(id: String) {
        val target = readFloatingProfileLibrary().firstOrNull { it.id == id } ?: return
        if (selectedFloatingProfileId != id) {
            syncSelectedFloatingProfileFromCurrent()
            selectedFloatingProfileId = id
            applyFloatingProfileToControls(target, updateService = false)
        } else {
            livePreviewProfileId = id
            invalidateLivePreviewProfileCache()
            lastAppliedFloatingProfileId = id
            prefs.edit()
                .putString("selected_floating_profile_id", id)
                .putString("live_preview_profile_id", id)
                .putString("last_interacted_floating_profile_id", id)
                .apply()
            updateLivePreviewProfileButton()
            refreshLivePreviewAfterProfileEdit()
            updateModeButtons()
            updateSingleModeButtons()
            updateSingleModeVisibility()
            buildLiveFrequencyLabels()
            updateSpectrumBandLabels(animated = true)
            if (latestSpectrumLevels.isNotEmpty()) updatePreviewLevels(latestSpectrumLevels)
            buildFloatingProfileList()
        }
    }

    private fun selectFloatingProfile(id: String) {
        val target = readFloatingProfileLibrary().firstOrNull { it.id == id } ?: return
        if (id == selectedFloatingProfileId) {
            livePreviewProfileId = id
            invalidateLivePreviewProfileCache()
            lastAppliedFloatingProfileId = id
            prefs.edit()
                .putString("selected_floating_profile_id", id)
                .putString("live_preview_profile_id", id)
                .putString("last_interacted_floating_profile_id", id)
                .apply()
            updateLivePreviewProfileButton()
            buildSpectrumBars()
            buildLiveFrequencyLabels()
            updateSpectrumBandLabels(animated = true)
            if (latestSpectrumLevels.isNotEmpty()) updatePreviewLevels(latestSpectrumLevels)
            return
        }
        syncSelectedFloatingProfileFromCurrent()
        selectedFloatingProfileId = target.id
        prefs.edit().putString("last_interacted_floating_profile_id", target.id).apply()
        applyFloatingProfileToControls(target)
    }

    private fun miniDragHandle(): TextView = TextView(this).apply {
        text = "⠿"
        gravity = Gravity.CENTER
        textSize = 22f
        contentDescription = if (appLanguage == "id") "Tahan untuk pindahkan layer" else "Hold to reorder layer"
        setPadding(8.dp(), 5.dp(), 8.dp(), 5.dp())
        applyPillStyle(this, false)
        isClickable = true
        isFocusable = true
    }

    private fun floatingProfileDisplayName(profile: FloatingLayerProfile, index: Int): String {
        return if (profile.isDefault || profile.id == "root") "Profile 1 · Default" else profile.name
    }

    private fun buildFloatingProfileList() {
        if (!::floatingProfilesList.isInitialized) return
        floatingProfilesList.removeAllViews()
        val profiles = readFloatingProfileLibrary()
        if (::floatingProfileCount.isInitialized) {
            floatingProfileCount.text = "${profiles.size}/12"
            floatingProfileCount.contentDescription = if (appLanguage == "id") "${profiles.size} dari maksimal 12 profil" else "${profiles.size} of 12 floating profiles"
        }
        if (profiles.none { it.id == livePreviewProfileId }) {
            livePreviewProfileId = profiles.firstOrNull { it.id == selectedFloatingProfileId }?.id ?: profiles.firstOrNull()?.id ?: "root"
            prefs.edit().putString("live_preview_profile_id", livePreviewProfileId).apply()
            updateLivePreviewProfileButton()
            buildSpectrumBars()
            buildLiveFrequencyLabels()
        }
        profiles.forEachIndexed { index, profile ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(11.dp(), 7.dp(), 9.dp(), 7.dp())
                background = floatingProfileRowDrawable(profile.id == selectedFloatingProfileId, isLightTheme(), currentAccent())
                setOnClickListener { selectFloatingProfile(profile.id) }
                tag = profile.id
            }
            // Drag is available only from its dedicated handle. The page ScrollView
            // is not intercepted when the user scrolls or taps the profile row.
            if (!profile.isDefault) {
                val dragHandle = miniDragHandle()
                row.addView(dragHandle)
                var downRawY = 0f
                var downRawX = 0f
                var dragging = false
                var holdTask: Runnable? = null
                dragHandle.setOnTouchListener { handle, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downRawY = event.rawY
                            downRawX = event.rawX
                            dragging = false
                            handle.parent?.requestDisallowInterceptTouchEvent(true)
                            holdTask = Runnable {
                                dragging = true
                                handle.animate().cancel()
                                handle.animate().scaleX(1.18f).scaleY(1.18f)
                                    .translationZ(8.dp().toFloat()).alpha(0.88f)
                                    .setDuration(150L).start()
                                row.animate().cancel()
                                row.animate().translationZ(8.dp().toFloat()).alpha(0.92f)
                                    .setDuration(150L).start()
                            }
                            holdTask?.let { updateHandler.postDelayed(it, 360L) }
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (dragging) {
                                row.translationY = event.rawY - downRawY
                            } else if (abs(event.rawY - downRawY) > 16.dp() || abs(event.rawX - downRawX) > 24.dp()) {
                                holdTask?.let { updateHandler.removeCallbacks(it) }
                                holdTask = null
                                // A normal swipe on the grip (without the hold delay)
                                // belongs to the page ScrollView, not to layer reorder.
                                handle.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            holdTask?.let { updateHandler.removeCallbacks(it) }
                            holdTask = null
                            if (dragging) {
                                val listLocation = IntArray(2)
                                floatingProfilesList.getLocationOnScreen(listLocation)
                                val yInList = event.rawY - listLocation[1]
                                var targetId: String? = null
                                for (childIndex in 0 until floatingProfilesList.childCount) {
                                    val child = floatingProfilesList.getChildAt(childIndex)
                                    if (child !== row && yInList < child.top + child.height / 2f) {
                                        targetId = child.tag as? String
                                        break
                                    }
                                }
                                val updated = readFloatingProfileLibrary()
                                val from = updated.indexOfFirst { it.id == profile.id }
                                if (from > 0) {
                                    val moved = updated.removeAt(from)
                                    val targetIndex = when (targetId) {
                                        "root" -> 1
                                        null -> updated.size
                                        else -> updated.indexOfFirst { it.id == targetId }.takeIf { it >= 1 } ?: updated.size
                                    }
                                    updated.add(targetIndex.coerceIn(1, updated.size), moved)
                                    writeFloatingProfileLibrary(updated)
                                    buildFloatingProfileList()
                                    refreshFloatingProfileSectionPickers()
                                    updateFloatingService()
                                }
                            } else {
                                selectFloatingProfile(profile.id)
                            }
                            handle.parent?.requestDisallowInterceptTouchEvent(false)
                            handle.animate().cancel()
                            handle.scaleX = 1f; handle.scaleY = 1f; handle.translationZ = 0f; handle.alpha = 1f
                            row.animate().cancel()
                            row.translationY = 0f; row.translationZ = 0f; row.alpha = 1f
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            holdTask?.let { updateHandler.removeCallbacks(it) }
                            holdTask = null
                            handle.parent?.requestDisallowInterceptTouchEvent(false)
                            handle.animate().cancel()
                            handle.scaleX = 1f; handle.scaleY = 1f; handle.translationZ = 0f; handle.alpha = 1f
                            row.animate().cancel()
                            row.translationY = 0f; row.translationZ = 0f; row.alpha = 1f
                            true
                        }
                        else -> true
                    }
                }
            }
            val name = TextView(this).apply {
                val selected = profile.id == selectedFloatingProfileId
                text = (if (selected) "●  " else "○  ") + "LAYER ${index + 1}  ·  ${floatingProfileDisplayName(profile, index)}"
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(pillForegroundColor(isLightTheme(), selected))
                setTag(R.id.tag_selector_selected, selected)
                setPadding(7.dp(), 7.dp(), 5.dp(), 7.dp())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(name)
            fun miniAction(label: String, enabled: Boolean = true, onClick: () -> Unit): TextView = TextView(this).apply {
                text = label; gravity = Gravity.CENTER; textSize = 17f
                alpha = if (enabled) 1f else 0.35f
                isEnabled = enabled
                setPadding(5.dp(), 5.dp(), 5.dp(), 5.dp())
                minWidth = 36.dp()
                minHeight = 36.dp()
                layoutParams = LinearLayout.LayoutParams(36.dp(), 36.dp()).apply {
                    setMargins(2.dp(), 0, 2.dp(), 0)
                }
                applyPillStyle(this, false, enabled = enabled)
                setOnClickListener { if (enabled) onClick() }
            }
            fun miniVisibilityAction(): ImageButton = ImageButton(this).apply {
                setImageResource(if (profile.visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off)
                imageTintList = ColorStateList.valueOf(if (isLightTheme()) Color.parseColor("#40516A") else Color.WHITE)
                background = pillDrawable(false, isLightTheme(), currentAccent(), 13)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
                contentDescription = if (profile.visible) {
                    if (appLanguage == "id") "Sembunyikan ${profile.name}" else "Hide ${profile.name}"
                } else {
                    if (appLanguage == "id") "Tampilkan ${profile.name}" else "Show ${profile.name}"
                }
                layoutParams = LinearLayout.LayoutParams(38.dp(), 38.dp()).apply {
                    setMargins(3.dp(), 0, 3.dp(), 0)
                }
                setOnClickListener {
                    val updated = readFloatingProfileLibrary()
                    val targetIndex = updated.indexOfFirst { it.id == profile.id }
                    if (targetIndex >= 0) {
                        val current = updated[targetIndex]
                        updated[targetIndex] = current.copy(visible = !current.visible)
                        writeFloatingProfileLibrary(updated)
                        buildFloatingProfileList()
                        refreshFloatingProfileSectionPickers()
                        updateFloatingService()
                        Toast.makeText(
                            this@MainActivity,
                            if (!current.visible) {
                                if (appLanguage == "id") "${current.name} ditampilkan" else "${current.name} is visible"
                            } else {
                                if (appLanguage == "id") "${current.name} disembunyikan" else "${current.name} is hidden"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            row.addView(miniAction("⌖") { showFloatingLayerPositionDialog(profile.id) })
            row.addView(miniVisibilityAction())
            row.addView(miniAction("✎", !profile.isDefault) {
                showProfileNameDialog(if (appLanguage == "id") "GANTI NAMA PROFILE" else "RENAME FLOATING PROFILE", profile.name) { newName ->
                    val list = readFloatingProfileLibrary()
                    if (list.any { it.id != profile.id && it.name.equals(newName, true) }) {
                        Toast.makeText(this, if (appLanguage == "id") "Nama sudah dipakai" else "Name already exists", Toast.LENGTH_SHORT).show()
                    } else {
                        val i = list.indexOfFirst { it.id == profile.id }
                        if (i >= 0) list[i] = list[i].copy(name = newName.take(28))
                        writeFloatingProfileLibrary(list); buildFloatingProfileList(); buildFloatingControls()
                    }
                }
            })
            row.addView(miniAction("×", !profile.isDefault) {
                showGlassConfirmDialog(
                    if (appLanguage == "id") "HAPUS FLOATING PROFILE?" else "DELETE FLOATING PROFILE?",
                    "${profile.name}\n${if (appLanguage == "id") "Layer dan pengaturan layer ini akan dihapus." else "This layer and its settings will be removed."}",
                    if (appLanguage == "id") "BATAL" else "CANCEL",
                    if (appLanguage == "id") "HAPUS" else "DELETE",
                    0.90f,
                    0.48f
                ) {
                    val list = readFloatingProfileLibrary().filterNot { it.id == profile.id }.toMutableList()
                    if (selectedFloatingProfileId == profile.id) selectedFloatingProfileId = "root"
                    writeFloatingProfileLibrary(list)
                    if (selectedFloatingProfileId == "root") applyFloatingProfileToControls(list.first()) else buildFloatingProfileList()
                    updateFloatingService()
                    refreshFloatingSceneManagerStatus()
                }
            })
            row.alpha = 0f
            row.translationY = 8.dp().toFloat()
            floatingProfilesList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 58.dp()).apply {
                marginStart = 3.dp()
                marginEnd = 3.dp()
                bottomMargin = 9.dp()
            })
            row.animate().alpha(1f).translationY(0f).setDuration(180L + index * 18L).start()
        }
    }

    private fun addFloatingProfile() {
        val profiles = readFloatingProfileLibrary()
        if (profiles.size >= 12) {
            Toast.makeText(this, if (appLanguage == "id") "Maksimal 12 layer floating" else "Maximum 12 floating layers", Toast.LENGTH_SHORT).show()
            return
        }
        val base = currentFloatingLayerSnapshot()
        val id = "layer_" + java.util.UUID.randomUUID().toString().take(8)
        var suffix = profiles.size + 1
        var suggestedName = "Profile $suffix"
        while (profiles.any { it.name.equals(suggestedName, ignoreCase = true) }) {
            suffix++
            suggestedName = "Profile $suffix"
        }
        val newProfile = base.copy(id = id, name = suggestedName, isDefault = false, visible = true)
        profiles.add(newProfile)
        writeFloatingProfileLibrary(profiles)
        selectFloatingProfile(id)
        Toast.makeText(this, if (appLanguage == "id") "Floating profile ditambahkan" else "Floating profile added", Toast.LENGTH_SHORT).show()
    }

    private fun showFloatingLayerPositionDialog(profileId: String) {
        val original = readFloatingProfileLibrary().firstOrNull { it.id == profileId } ?: return
        var draft = original
        var unit = positionUnit
        var stepDp = positionStepDp.coerceIn(1f, 1000f)
        var syncingInputs = false
        val panel = glassPanel().apply { background = null; setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp()) }
        panel.addView(subtitle(if (appLanguage == "id") "Atur posisi tiap layer. Koordinat bisa diketik atau digeser; perubahan tampil langsung dan bisa dibatalkan." else "Adjust this layer position. Type coordinates or use the arrows; changes preview live and can be cancelled."))
        val coordinates = sectionTitle("")
        coordinates.gravity = Gravity.CENTER
        coordinates.setPadding(0, 10.dp(), 0, 8.dp())
        panel.addView(coordinates)
        activePositionDialogProfileId = profileId
        activePositionCoordinateView = coordinates
        activePositionDialogUnit = unit

        val unitRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dpButton = glassButton("DP")
        val cmButton = glassButton("CM")
        unitRow.addView(dpButton, LinearLayout.LayoutParams(0, 38.dp(), 1f).apply { marginEnd = 4.dp() })
        unitRow.addView(cmButton, LinearLayout.LayoutParams(0, 38.dp(), 1f).apply { marginStart = 4.dp() })
        panel.addView(unitRow)

        fun displayCoordinate(valueDp: Int): String = if (unit == "DP") valueDp.toString()
            else String.format(java.util.Locale.US, "%.2f", dpToCm(valueDp.toFloat()))
        fun parseCoordinate(raw: String): Int? {
            val value = raw.trim().replace(',', '.').toFloatOrNull() ?: return null
            if (value < 0f) return null
            val dpValue = if (unit == "DP") value else cmToDp(value)
            return dpValue.toInt().coerceIn(0, 100000)
        }
        fun coordinateInput(label: String, initial: Int): EditText = EditText(this).apply {
            hint = label
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(displayCoordinate(initial))
            setTextColor(if (isLightTheme()) Color.parseColor("#26344B") else Color.WHITE)
            setHintTextColor(if (isLightTheme()) Color.parseColor("#8693A8") else Color.parseColor("#8593A8"))
            setPadding(10.dp(), 7.dp(), 10.dp(), 7.dp())
            background = glassEditDrawable()
        }
        val xInput = coordinateInput(if (appLanguage == "id") "POSISI X ($unit)" else "POSITION X ($unit)", draft.positionXdp)
        val yInput = coordinateInput(if (appLanguage == "id") "POSISI Y ($unit)" else "POSITION Y ($unit)", draft.positionYdp)
        val coordinateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        coordinateRow.addView(xInput, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 4.dp() })
        coordinateRow.addView(yInput, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 4.dp() })
        panel.addView(coordinateRow, matchWrap().apply { topMargin = 6.dp() })

        val stepLabel = subtitle(if (appLanguage == "id") "JARAK LANGKAH ($unit)" else "STEP DISTANCE ($unit)").apply {
            setPadding(1.dp(), 11.dp(), 1.dp(), 3.dp())
        }
        val stepInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine(true)
            setText(if (unit == "DP") stepDp.toInt().toString() else String.format(java.util.Locale.US, "%.2f", dpToCm(stepDp)))
            hint = "12"
            setTextColor(if (isLightTheme()) Color.parseColor("#26344B") else Color.WHITE)
            setHintTextColor(if (isLightTheme()) Color.parseColor("#8693A8") else Color.parseColor("#8593A8"))
            setPadding(10.dp(), 7.dp(), 10.dp(), 7.dp())
            background = glassEditDrawable()
        }
        panel.addView(stepLabel)
        panel.addView(stepInput, matchWrap())

        fun refreshCoordinateText() {
            coordinates.text = "X ${displayCoordinate(draft.positionXdp)} $unit     Y ${displayCoordinate(draft.positionYdp)} $unit     ·     ${draft.rotationDegrees}°"
        }
        fun refreshCoordinateInputs() {
            syncingInputs = true
            xInput.setText(displayCoordinate(draft.positionXdp))
            yInput.setText(displayCoordinate(draft.positionYdp))
            stepInput.setText(if (unit == "DP") stepDp.toInt().toString() else String.format(java.util.Locale.US, "%.2f", dpToCm(stepDp)))
            xInput.hint = "X ($unit)"; yInput.hint = "Y ($unit)"
            stepLabel.text = if (appLanguage == "id") "JARAK LANGKAH ($unit)" else "STEP DISTANCE ($unit)"
            syncingInputs = false
            refreshCoordinateText()
        }
        fun applyDraft(updateInputs: Boolean = false) {
            // Clamp X/Y using the same pixel geometry as the overlay Service. Typing
            // an arbitrarily large value or pressing an arrow now lands exactly on
            // the physical edge instead of leaving a stale off-screen coordinate.
            draft = clampFloatingLayerPosition(draft)
            val list = readFloatingProfileLibrary()
            val index = list.indexOfFirst { it.id == profileId }
            if (index >= 0) list[index] = draft
            writeFloatingProfileLibrary(list)
            if (profileId == selectedFloatingProfileId) {
                positionXdp = draft.positionXdp
                positionYdp = draft.positionYdp
                positionInitialized = true
                positionMode = draft.positionMode
                rotationDegrees = draft.rotationDegrees
                prefs.edit().putInt("position_x", positionXdp).putInt("position_y", positionYdp)
                    .putBoolean("position_initialized", true).putString("position_mode", positionMode)
                    .putInt("rotation_degrees", rotationDegrees).apply()
            }
            if (updateInputs) refreshCoordinateInputs() else refreshCoordinateText()
            updateFloatingService()
        }
        fun refreshUnitButtons() {
            applyPillStyle(dpButton, unit == "DP")
            applyPillStyle(cmButton, unit == "CM")
        }
        dpButton.setOnClickListener {
            unit = "DP"
            activePositionDialogUnit = unit
            positionUnit = unit
            prefs.edit().putString("position_unit", unit).apply()
            refreshUnitButtons(); refreshCoordinateInputs()
        }
        cmButton.setOnClickListener {
            unit = "CM"
            activePositionDialogUnit = unit
            positionUnit = unit
            prefs.edit().putString("position_unit", unit).apply()
            refreshUnitButtons(); refreshCoordinateInputs()
        }
        refreshUnitButtons(); refreshCoordinateText()

        fun coordinateWatcher(isX: Boolean): TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (syncingInputs) return
                val parsed = parseCoordinate(s?.toString().orEmpty()) ?: return
                draft = if (isX) draft.copy(positionXdp = parsed) else draft.copy(positionYdp = parsed)
                applyDraft()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        xInput.addTextChangedListener(coordinateWatcher(true))
        yInput.addTextChangedListener(coordinateWatcher(false))
        // Once the field is finished, show the authoritative clamped coordinate.
        xInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) refreshCoordinateInputs() }
        yInput.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) refreshCoordinateInputs() }
        stepInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (syncingInputs) return
                val value = s?.toString()?.trim()?.replace(',', '.')?.toFloatOrNull() ?: return
                if (value <= 0f) return
                stepDp = if (unit == "DP") value.coerceIn(1f, 1000f) else cmToDp(value).coerceIn(1f, 1000f)
                positionStepDp = stepDp
                prefs.edit().putFloat("position_step", stepDp).apply()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fun move(dx: Int, dy: Int) {
            val step = stepDp.toInt().coerceAtLeast(1)
            draft = clampFloatingLayerPosition(draft.copy(
                positionXdp = (draft.positionXdp + dx * step).coerceIn(0, 100000),
                positionYdp = (draft.positionYdp + dy * step).coerceIn(0, 100000)
            ))
            applyDraft(updateInputs = true)
        }
        val up = LinearLayout(this).apply { gravity = Gravity.CENTER }
        up.addView(positionButton("↑") { move(0, -1) }, LinearLayout.LayoutParams(58.dp(), 44.dp()))
        panel.addView(up, matchWrap().apply { topMargin = 12.dp() })
        val middle = LinearLayout(this).apply { gravity = Gravity.CENTER }
        middle.addView(positionButton("←") { move(-1, 0) }, LinearLayout.LayoutParams(58.dp(), 44.dp()))
        middle.addView(positionButton("CENTER") {
            draft = centerFloatingLayerPosition(draft)
            applyDraft(updateInputs = true)
        }, LinearLayout.LayoutParams(86.dp(), 44.dp()).apply { marginStart = 5.dp(); marginEnd = 5.dp() })
        middle.addView(positionButton("→") { move(1, 0) }, LinearLayout.LayoutParams(58.dp(), 44.dp()))
        panel.addView(middle)
        val down = LinearLayout(this).apply { gravity = Gravity.CENTER }
        down.addView(positionButton("↓") { move(0, 1) }, LinearLayout.LayoutParams(58.dp(), 44.dp()))
        panel.addView(down)

        val dragMode = glassButton("POSITION MODE · ${draft.positionMode}   ⌄")
        dragMode.setOnClickListener {
            draft = draft.copy(positionMode = if (draft.positionMode == "STATIC") "DYNAMIC" else "STATIC")
            dragMode.text = "POSITION MODE · ${draft.positionMode}   ⌄"
            applyDraft()
        }
        panel.addView(dragMode, matchWrap().apply { topMargin = 10.dp() })
        panel.addView(subtitle("ROTATION · 0°–359°").apply { setPadding(0, 14.dp(), 0, 4.dp()) })
        val rotationValue = EditText(this).apply {
            setSingleLine(true); inputType = InputType.TYPE_CLASS_NUMBER
            setText(draft.rotationDegrees.toString())
            setTextColor(if (isLightTheme()) Color.DKGRAY else Color.WHITE)
            background = glassEditDrawable(); setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
        }
        val seek = SeekBar(this).apply { max = 359; progress = draft.rotationDegrees; progressTintList = ColorStateList.valueOf(currentAccent()); thumbTintList = ColorStateList.valueOf(currentAccent()) }
        var syncingRotation = false
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                draft = draft.copy(rotationDegrees = progress)
                syncingRotation = true
                rotationValue.setText(progress.toString())
                syncingRotation = false
                applyDraft()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        rotationValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (syncingRotation) return
                val value = s?.toString()?.toIntOrNull() ?: return
                draft = draft.copy(rotationDegrees = value.coerceIn(0, 359))
                if (seek.progress != draft.rotationDegrees) seek.progress = draft.rotationDegrees
                applyDraft()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        panel.addView(rotationValue, matchWrap()); panel.addView(seek)
        activePositionDraftUpdater = { x, y ->
            draft = draft.copy(positionXdp = x, positionYdp = y)
            // Keep numeric X/Y fields synchronized while the overlay is dragged.
            // TextWatchers ignore these programmatic updates through syncingInputs.
            syncingInputs = true
            xInput.setText(displayCoordinate(x))
            yInput.setText(displayCoordinate(y))
            syncingInputs = false
            refreshCoordinateText()
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true; addView(panel) }
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        val done = glassButton(if (appLanguage == "id") "SELESAI" else "DONE")
        footer.addView(cancel, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(done, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(if (appLanguage == "id") "POSISI LAYER · ${original.name}" else "LAYER POSITION · ${original.name}", scroll, footer, 0.94f, 0.90f)
        var committed = false
        var rollbackDone = false
        fun restoreOriginalPreview() {
            if (rollbackDone || committed) return
            rollbackDone = true
            val list = readFloatingProfileLibrary()
            val index = list.indexOfFirst { it.id == profileId }
            if (index >= 0) list[index] = original
            writeFloatingProfileLibrary(list)
            if (profileId == selectedFloatingProfileId) {
                positionXdp = original.positionXdp; positionYdp = original.positionYdp
                positionInitialized = true; positionMode = original.positionMode; rotationDegrees = original.rotationDegrees
                prefs.edit().putInt("position_x", positionXdp).putInt("position_y", positionYdp)
                    .putBoolean("position_initialized", true).putString("position_mode", positionMode)
                    .putInt("rotation_degrees", rotationDegrees).apply()
            }
            updateFloatingService()
        }
        dialog.setOnDismissListener {
            restoreOriginalPreview()
            if (activePositionDialogProfileId == profileId) {
                activePositionDialogProfileId = null
                activePositionCoordinateView = null
                activePositionDraftUpdater = null
            }
        }
        cancel.setOnClickListener { dialog.dismiss() }
        done.setOnClickListener { committed = true; updateFloatingService(); dialog.dismiss() }
    }

    private fun showFloatingProfilePicker() {
        val profiles = readFloatingProfileLibrary()
        if (profiles.isEmpty()) return
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 10.dp(), 8.dp(), 8.dp())
            addView(subtitle(if (appLanguage == "id") "Pilih layer yang ingin kamu atur. Setiap layer menyimpan tampilan dan posisi sendiri." else "Choose a layer to edit. Each layer keeps its own appearance and position."))
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(list)
        }
        body.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = 10.dp() })
        val rows = profiles.mapIndexed { index, profile ->
            val selected = profile.id == selectedFloatingProfileId
            glassButton("${if (selected) "●" else "○"}   LAYER ${index + 1}  ·  ${profile.name}").apply {
                gravity = Gravity.CENTER_VERTICAL
                textSize = 11f
                setPadding(14.dp(), 8.dp(), 12.dp(), 8.dp())
                applyPillStyle(this, selected)
                list.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()).apply { bottomMargin = 7.dp() })
            }
        }
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = glassButton(if (appLanguage == "id") "TUTUP" else "CLOSE")
        footer.addView(cancel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()))
        val dialog = showGlassStudioDialog(
            if (appLanguage == "id") "PILIH FLOATING PROFILE" else "SELECT FLOATING PROFILE",
            body, footer, 0.92f, 0.76f
        )
        rows.forEachIndexed { index, row ->
            row.setOnClickListener {
                val id = profiles[index].id
                dialog.dismiss()
                selectFloatingProfile(id)
            }
        }
        cancel.setOnClickListener { dialog.dismiss() }
    }

    private fun addFloatingProfileSectionControls(container: LinearLayout, scope: String) {
        val profile = readFloatingProfileLibrary().firstOrNull { it.id == selectedFloatingProfileId }
        val title = when (scope) {
            "MODE" -> if (appLanguage == "id") "PROFILE MODE" else "MODE PROFILE"
            "COLORS" -> if (appLanguage == "id") "PROFILE WARNA" else "COLOR PROFILE LAYER"
            else -> if (appLanguage == "id") "PROFILE FLOATING" else "FLOATING PROFILE"
        }
        container.addView(sectionTitle(title).apply { textSize = 10f; setPadding(0, 10.dp(), 0, 4.dp()) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val picker = glassButton("${profile?.name ?: "Default"}   ⌄").apply {
            gravity = Gravity.CENTER_VERTICAL
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(11.dp(), 9.dp(), 9.dp(), 9.dp())
            setOnClickListener { showFloatingProfilePicker() }
        }
        row.addView(picker, LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { marginEnd = 6.dp() })
        val copy = glassButton(if (appLanguage == "id") "SALIN KE…" else "COPY TO…").apply {
            textSize = 9f
            setPadding(9.dp(), 8.dp(), 9.dp(), 8.dp())
            setOnClickListener { showCopyFloatingProfileSectionPicker(scope) }
        }
        row.addView(copy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 42.dp()))
        container.addView(row, matchWrap().apply { bottomMargin = 7.dp() })
        floatingProfileSectionPickerButtons.add(picker)
        floatingProfileSectionCopyButtons.add(copy)
    }

    private fun buildColorProfileFloatingControls() {
        colorProfileFloatingControls.removeAllViews()
        addFloatingProfileSectionControls(colorProfileFloatingControls, "COLORS")
    }

    private fun refreshFloatingProfileSectionPickers() {
        val profile = readFloatingProfileLibrary().firstOrNull { it.id == selectedFloatingProfileId }
        floatingProfileSectionPickerButtons.removeAll { it.parent == null }
        floatingProfileSectionPickerButtons.forEach { it.text = "${profile?.name ?: "Default"}   ⌄" }
        floatingProfilePickerButton?.text = "${profile?.name ?: "Default"}   ⌄"
    }

    private fun showCopyFloatingProfileSectionPicker(scope: String) {
        syncSelectedFloatingProfileFromCurrent()
        val sourceId = selectedFloatingProfileId
        val allProfiles = readFloatingProfileLibrary()
        val targets = allProfiles.filter { it.id != sourceId }
        if (targets.isEmpty()) {
            Toast.makeText(this, if (appLanguage == "id") "Tambahkan profile lain dulu" else "Add another profile first", Toast.LENGTH_SHORT).show()
            return
        }
        val sectionName = when (scope) {
            "MODE" -> "Visualizer Mode + HZ + Flow/Mirror"
            "COLORS" -> if (appLanguage == "id") "Warna Spectrum" else "Spectrum Colors"
            else -> "Bar Appearance"
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp(), 10.dp(), 8.dp(), 8.dp())
            addView(subtitle(if (appLanguage == "id") "Pilih satu atau beberapa layer tujuan. Tidak ada pilihan otomatis." else "Select one or more destination layers. Nothing is selected by default."))
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            addView(list)
        }
        body.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = 10.dp() })
        val allTargetIds = targets.map { it.id }.toSet()
        val choices = mutableListOf<Pair<String, Set<String>>>()
        choices.add((if (appLanguage == "id") "SEMUA FLOATING PROFILE" else "ALL FLOATING PROFILES") to allTargetIds)
        targets.forEach { profile ->
            val realIndex = allProfiles.indexOfFirst { it.id == profile.id } + 1
            choices.add("LAYER $realIndex · ${profile.name}" to setOf(profile.id))
        }
        val selectedIds = linkedSetOf<String>()
        val rows = choices.map { choice ->
            glassButton(choice.first).apply {
                gravity = Gravity.CENTER_VERTICAL
                textSize = 10f
                setPadding(13.dp(), 8.dp(), 10.dp(), 8.dp())
                applyPillStyle(this, false)
                list.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 46.dp()).apply { bottomMargin = 7.dp() })
            }
        }
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val close = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        val copy = glassButton(if (appLanguage == "id") "SALIN TERPILIH" else "COPY SELECTED").apply { isEnabled = false; alpha = 0.45f }
        footer.addView(close, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 6.dp() })
        footer.addView(copy, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 6.dp() })
        val dialog = showGlassStudioDialog(if (appLanguage == "id") "SALIN PENGATURAN" else "COPY PROFILE SETTINGS", body, footer, 0.94f, 0.76f)

        fun refreshSelectionUi() {
            val allSelected = selectedIds.containsAll(allTargetIds) && selectedIds.isNotEmpty()
            rows.forEachIndexed { index, row ->
                val isSelected = if (index == 0) allSelected else choices[index].second.all { it in selectedIds }
                row.text = (if (isSelected) "✓  " else "○  ") + choices[index].first
                applyPillStyle(row, isSelected)
            }
            copy.isEnabled = selectedIds.isNotEmpty()
            copy.alpha = if (selectedIds.isEmpty()) 0.45f else 1f
        }
        rows.forEachIndexed { index, row ->
            row.setOnClickListener {
                val ids = choices[index].second
                val isAllChoice = index == 0
                val shouldSelect = if (isAllChoice) !(selectedIds.containsAll(allTargetIds) && selectedIds.isNotEmpty())
                    else !selectedIds.containsAll(ids)
                if (isAllChoice) {
                    if (shouldSelect) selectedIds.addAll(allTargetIds) else selectedIds.clear()
                } else {
                    if (shouldSelect) selectedIds.addAll(ids) else selectedIds.removeAll(ids)
                }
                refreshSelectionUi()
            }
        }
        close.setOnClickListener { dialog.dismiss() }
        copy.setOnClickListener {
            val targetIds = selectedIds.toSet()
            if (targetIds.isEmpty()) return@setOnClickListener
            val targetLabel = if (targetIds.containsAll(allTargetIds)) {
                if (appLanguage == "id") "Semua Floating Profile" else "All Floating Profiles"
            } else targetIds.mapNotNull { id -> allProfiles.firstOrNull { it.id == id }?.name }.joinToString(", ")
            showGlassConfirmDialog(
                if (appLanguage == "id") "KONFIRMASI SALINAN" else "CONFIRM COPY",
                if (appLanguage == "id") "Salin $sectionName ke $targetLabel? Pengaturan tujuan akan ditimpa." else "Copy $sectionName to $targetLabel? Destination settings will be overwritten.",
                if (appLanguage == "id") "BATAL" else "CANCEL",
                if (appLanguage == "id") "YA, SALIN" else "YES, COPY"
            ) {
                val source = readFloatingProfileLibrary().firstOrNull { it.id == sourceId } ?: return@showGlassConfirmDialog
                val updated = readFloatingProfileLibrary().map { target ->
                    if (target.id !in targetIds) target else when (scope) {
                        "MODE" -> target.copy(
                            verticalMode = source.verticalMode,
                            singleAudioMode = source.singleAudioMode,
                            bassMinHz = source.bassMinHz,
                            bassMaxHz = source.bassMaxHz,
                            horizontalAlignment = source.horizontalAlignment,
                            mirrorMode = source.mirrorMode
                        )
                        "COLORS" -> target.copy(colors = source.colors)
                        else -> target.copy(
                            effect = source.effect, widthDp = source.widthDp, heightDp = source.heightDp,
                            barCount = source.barCount, barWidthDp = source.barWidthDp, gapDp = source.gapDp,
                            scalePercent = source.scalePercent, opacityPercent = source.opacityPercent,
                            shadowOpacityPercent = source.shadowOpacityPercent,
                            glowPercent = source.glowPercent, responsePercent = source.responsePercent,
                            cornerRadiusDp = source.cornerRadiusDp,
                            appearancePresetName = source.appearancePresetName
                        )
                    }
                }
                writeFloatingProfileLibrary(updated)
                buildFloatingProfileList()
                refreshFloatingProfileSectionPickers()
                if (livePreviewProfileId in targetIds) {
                    invalidateLivePreviewProfileCache()
                    updateLivePreviewProfileButton()
                    buildSpectrumBars()
                    buildLiveFrequencyLabels()
                    if (latestSpectrumLevels.isNotEmpty()) updatePreviewLevels(latestSpectrumLevels)
                }
                updateFloatingService()
                Toast.makeText(this, if (appLanguage == "id") "Pengaturan berhasil disalin" else "Settings copied", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        refreshSelectionUi()
    }

    private fun showCopyFloatingAppearancePicker() = showCopyFloatingProfileSectionPicker("APPEARANCE")

    private fun addFloatingProfileAppearanceControls() {
        addFloatingProfileSectionControls(controlsContainer, "APPEARANCE")
    }

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
        val shadowOpacityPercent: Int,
        val glowPercent: Int,
        val responsePercent: Int,
        val cornerRadiusDp: Int,
        val verticalMode: Boolean = false,
        val singleAudioMode: String = "MUSIC",
        val bassMinHz: Int = 20,
        val bassMaxHz: Int = 125,
        val horizontalAlignment: String = "LEFT",
        val mirrorMode: String = "OFF",
        val positionXdp: Int = 0,
        val positionYdp: Int = 0,
        val positionMode: String = "STATIC",
        val rotationDegrees: Int = 0,
        val floatingOverlayEnabled: Boolean = false
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
                    while (colors.size < 5) colors.add(colors.last())
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
                            barCount = item.optInt("bar_count", 15).coerceIn(1, MAX_BAR_COUNT),
                            barWidthDp = item.optInt("bar_width", 20).coerceIn(0, 2048),
                            gapDp = item.optInt("bar_gap", 3).coerceIn(0, 512),
                            scalePercent = item.optInt("scale", 100).coerceIn(0, 500),
                            opacityPercent = item.optInt("opacity", 100).coerceIn(0, 100),
                            shadowOpacityPercent = item.optInt("shadow_opacity", 100).coerceIn(0, 100),
                            glowPercent = item.optInt("glow", if (effect == EFFECT_NEON) 100 else 50).coerceIn(0, 100),
                            responsePercent = item.optInt("response_speed", 82).coerceIn(0, 100),
                            cornerRadiusDp = item.optInt("corner_radius", 14).coerceIn(0, 512),
                            verticalMode = item.optBoolean("vertical_mode", false),
                            singleAudioMode = item.optString("single_audio_mode", "MUSIC").takeIf { it == "DJ" || it == "MUSIC" } ?: "MUSIC",
                            bassMinHz = item.optInt("bass_min_hz", 20).coerceIn(0, 20000),
                            bassMaxHz = item.optInt("bass_max_hz", 125).coerceIn(0, 20000),
                            horizontalAlignment = item.optString("horizontal_alignment", "LEFT").takeIf { it in setOf("LEFT", "CENTER", "RIGHT") } ?: "LEFT",
                            mirrorMode = item.optString("mirror_mode", if (item.optBoolean("mirror_enabled", false)) "HORIZONTAL" else "OFF")
                                .takeIf { it in setOf("OFF", "VERTICAL", "HORIZONTAL") } ?: "OFF",
                            positionXdp = item.optInt("position_x", 0).coerceIn(0, 100000),
                            positionYdp = item.optInt("position_y", 0).coerceIn(0, 100000),
                            positionMode = item.optString("position_mode", "STATIC").takeIf { it == "STATIC" || it == "DYNAMIC" } ?: "STATIC",
                            rotationDegrees = item.optInt("rotation_degrees", 0).coerceIn(0, 359),
                            floatingOverlayEnabled = item.optBoolean("floating_overlay_enabled", false)
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
            item.put("shadow_opacity", profile.shadowOpacityPercent)
            item.put("glow", profile.glowPercent)
            item.put("response_speed", profile.responsePercent)
            item.put("corner_radius", profile.cornerRadiusDp)
            item.put("vertical_mode", profile.verticalMode)
            item.put("single_audio_mode", profile.singleAudioMode)
            item.put("bass_min_hz", profile.bassMinHz)
            item.put("bass_max_hz", profile.bassMaxHz)
            item.put("horizontal_alignment", profile.horizontalAlignment)
            item.put("mirror_mode", profile.mirrorMode)
            item.put("mirror_enabled", profile.mirrorMode != "OFF") // Legacy compatibility.
            item.put("position_x", profile.positionXdp)
            item.put("position_y", profile.positionYdp)
            item.put("position_mode", profile.positionMode)
            item.put("rotation_degrees", profile.rotationDegrees)
            item.put("floating_overlay_enabled", profile.floatingOverlayEnabled)
            array.put(item)
        }
        prefs.edit().putString("color_profiles", array.toString()).apply()
    }

    private fun currentProfileSnapshot(name: String): ColorProfileEntry = ColorProfileEntry(
        name = name.trim().ifBlank { "Current Configuration" }.take(28),
        colors = spectrumColors.take(5).toList(),
        effect = barEffect,
        widthDp = overlayWidthDp,
        heightDp = overlayHeightDp,
        barCount = barCount,
        barWidthDp = barWidthDp,
        gapDp = barGapDp,
        scalePercent = scalePercent,
        opacityPercent = opacityPercent,
        shadowOpacityPercent = shadowOpacityPercent,
        glowPercent = glowPercent,
        responsePercent = responsePercent,
        cornerRadiusDp = cornerRadiusDp,
        verticalMode = isVerticalMode,
        singleAudioMode = singleAudioMode,
        bassMinHz = bassMinHz,
        bassMaxHz = bassMaxHz,
        horizontalAlignment = horizontalAlignment,
        mirrorMode = mirrorMode,
        positionXdp = positionXdp,
        positionYdp = positionYdp,
        positionMode = positionMode,
        rotationDegrees = rotationDegrees,
        floatingOverlayEnabled = floatingOverlayEnabled
    )

    private fun profileValuesEqual(first: ColorProfileEntry, second: ColorProfileEntry): Boolean {
        return first.copy(name = "") == second.copy(name = "")
    }

    private fun applyProfileToLiveState(profile: ColorProfileEntry, persist: Boolean) {
        invalidateLivePreviewProfileCache()
        val previousSuppressProfileDirty = suppressProfileDirty
        suppressProfileDirty = true
        var overlayPermissionNeeded = false
        try {
            spectrumColors.clear()
            spectrumColors.addAll(profile.colors.take(5))
            while (spectrumColors.size < 5) spectrumColors.add(Color.WHITE)
            barEffect = profile.effect
            overlayWidthDp = profile.widthDp.coerceIn(0, 4096)
            overlayHeightDp = profile.heightDp.coerceIn(0, 4096)
            barCount = profile.barCount.coerceIn(1, MAX_BAR_COUNT)
            barWidthDp = profile.barWidthDp.coerceIn(0, 2048)
            barGapDp = profile.gapDp.coerceIn(0, 512)
            scalePercent = profile.scalePercent.coerceIn(0, 500)
            opacityPercent = profile.opacityPercent.coerceIn(0, 100)
            shadowOpacityPercent = profile.shadowOpacityPercent.coerceIn(0, 100)
            glowPercent = profile.glowPercent.coerceIn(0, 100)
            responsePercent = profile.responsePercent.coerceIn(0, 100)
            cornerRadiusDp = profile.cornerRadiusDp.coerceIn(0, 512)
            isVerticalMode = profile.verticalMode
            singleAudioMode = profile.singleAudioMode
            bassMinHz = profile.bassMinHz.coerceIn(0, 20000)
            bassMaxHz = profile.bassMaxHz.coerceIn(0, 20000)
            horizontalAlignment = profile.horizontalAlignment
            mirrorMode = profile.mirrorMode
            positionXdp = profile.positionXdp.coerceAtLeast(0)
            positionYdp = profile.positionYdp.coerceAtLeast(0)
            positionMode = profile.positionMode
            rotationDegrees = profile.rotationDegrees.coerceIn(0, 359)
            positionInitialized = true
            scanDisplayBounds()
            refreshColorSwatches()
            buildSpectrumBars()
            updateModeButtons()
            updateSingleModeButtons()
            updateSingleModeVisibility()
            updateEffectButtons()
            updateHorizontalFlowButtons()
            syncSliderValue("HORIZONTAL WIDTH", overlayWidthDp)
            syncSliderValue("BAR HEIGHT / LENGTH", overlayHeightDp)
            syncSliderValue("HORIZONTAL BAR COUNT", barCount)
            syncSliderValue("BAR THICKNESS", barWidthDp)
            syncSliderValue("BAR GAP", barGapDp)
            syncSliderValue("SCALE", scalePercent)
            syncSliderValue("BAR OPACITY", opacityPercent)
            syncSliderValue("SHADOW BAR OPACITY", shadowOpacityPercent)
            syncSliderValue("GLOW INTENSITY", glowPercent)
            syncSliderValue("RESPONSE SPEED", responsePercent)
            syncSliderValue("CORNER ROUNDNESS", cornerRadiusDp)
            syncSliderValue(if (appLanguage == "id") "FREKUENSI MULAI" else "START FREQUENCY", bassMinHz)
            syncSliderValue(if (appLanguage == "id") "FREKUENSI AKHIR" else "END FREQUENCY", bassMaxHz)
            if (persist) {
                loadedProfileName = profile.name
                profileDirty = false
                overlayPermissionNeeded = profile.floatingOverlayEnabled && !Settings.canDrawOverlays(this)
                floatingOverlayEnabled = profile.floatingOverlayEnabled && Settings.canDrawOverlays(this)
                prefs.edit()
                    .putString("profile_base_name", profile.name)
                    .putString("active_color_profile", profile.name)
                    .putString("last_managed_color_profile", profile.name)
                    .putBoolean("profile_dirty", false)
                    .putBoolean("floating_overlay_enabled", floatingOverlayEnabled)
                    .apply()
                setFloatingOverlaySwitch(floatingOverlayEnabled)
                saveSettings()
            }
            // A preview updates the live renderer temporarily, but must not
            // mutate the saved Current State / preset relationship.
            updateFloatingService(includeLiveSelectedProfile = !persist)
        } finally {
            suppressProfileDirty = previousSuppressProfileDirty
        }
        if (persist) {
            markProfileDirtyFromCurrentState()
            if (overlayPermissionNeeded) {
                showGlassConfirmDialog(
                    if (appLanguage == "id") "IZIN FLOATING DIPERLUKAN" else "OVERLAY PERMISSION REQUIRED",
                    if (appLanguage == "id") "Preset ini meminta Floating Bar aktif. Android perlu izin tampil di atas aplikasi lain di perangkat ini." else "This profile requests the Floating Bar. Android needs permission to display over other apps on this device.",
                    if (appLanguage == "id") "NANTI" else "NOT NOW",
                    if (appLanguage == "id") "BUKA PENGATURAN" else "OPEN SETTINGS",
                    0.92f, 0.50f
                ) { enableFloatingOverlay() }
            }
        }
    }

    private fun markProfileDirtyFromCurrentState() {
        if (suppressProfileDirty || profilePreviewActive) return
        val baseName = loadedProfileName ?: prefs.getString("profile_base_name", null)
        val profiles = readColorProfiles()
        val saved = baseName?.let { name -> profiles.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        val changed = when {
            saved != null -> !profileValuesEqual(saved, currentProfileSnapshot(saved.name))
            profiles.isNotEmpty() -> true
            else -> false
        }
        profileDirty = changed
        val editor = prefs.edit().putBoolean("profile_dirty", changed)
        if (changed) {
            editor.remove("active_color_profile")
        } else if (saved != null) {
            editor.putString("active_color_profile", saved.name)
        } else {
            editor.remove("active_color_profile")
            editor.remove("profile_base_name")
        }
        if (baseName != null) editor.putString("profile_base_name", baseName)
        editor.apply()
        profileSaveChangesButton?.let { button ->
            button.isEnabled = changed
            button.alpha = if (changed) 1f else 0.48f
        }
    }

    private fun saveCurrentStateToProfile(targetName: String) {
        val safeName = targetName.trim().take(28)
        if (safeName.isBlank()) return
        val profiles = readColorProfiles()
        val index = profiles.indexOfFirst { it.name.equals(safeName, ignoreCase = true) }
        val entry = currentProfileSnapshot(safeName)
        if (index >= 0) profiles[index] = entry else profiles.add(entry)
        writeColorProfiles(profiles)
        // Persist the current controls first. saveSettings() may still compare
        // against the previous base preset, so establish the new active preset
        // only after that comparison has completed.
        saveSettings()
        loadedProfileName = safeName
        profileDirty = false
        prefs.edit()
            .putString("profile_base_name", safeName)
            .putString("active_color_profile", safeName)
            .putString("last_managed_color_profile", safeName)
            .putBoolean("profile_dirty", false)
            .apply()
        profileSaveChangesButton?.let { it.isEnabled = false; it.alpha = 0.48f }
        Toast.makeText(this, if (appLanguage == "id") "Perubahan disimpan ke $safeName" else "Changes saved to $safeName", Toast.LENGTH_SHORT).show()
    }

    private fun showSaveChangesTargetPicker() {
        val profiles = readColorProfiles()
        if (profiles.isEmpty()) {
            Toast.makeText(this, if (appLanguage == "id") "Belum ada preset. Pakai Simpan Sebagai dulu." else "No saved presets yet. Use Save As first.", Toast.LENGTH_SHORT).show()
            return
        }
        var selectedIndex = profiles.indexOfFirst { it.name.equals(loadedProfileName, true) }.coerceAtLeast(0)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(subtitle(if (appLanguage == "id") "Pilih preset tujuan untuk menyimpan konfigurasi aktif." else "Choose which saved preset should receive the current configuration."))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(), 0, 0) }
        val rows = mutableListOf<TextView>()
        fun refreshRows() {
            rows.forEachIndexed { index, row ->
                row.text = (if (index == selectedIndex) "◉  " else "○  ") + profiles[index].name
                applyPillStyle(row, index == selectedIndex)
            }
        }
        profiles.forEachIndexed { index, profile ->
            val row = glassButton(profile.name)
            row.setOnClickListener { selectedIndex = index; refreshRows() }
            rows.add(row)
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 42.dp()).apply { bottomMargin = 5.dp() })
        }
        refreshRows()
        body.addView(list)
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        val save = glassButton(if (appLanguage == "id") "SIMPAN KE PRESET" else "SAVE TO PRESET")
        footer.addView(cancel, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(save, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(if (appLanguage == "id") "SIMPAN PERUBAHAN" else "SAVE CHANGES", body, footer, 0.92f, 0.76f)
        cancel.setOnClickListener { dialog.dismiss() }
        save.setOnClickListener { saveCurrentStateToProfile(profiles[selectedIndex].name); dialog.dismiss() }
    }

    private fun saveColorProfile(name: String, colors: List<Int>): Boolean {
        val safeName = name.trim().take(28)
        if (safeName.isBlank() || colors.size < 2) return false

        val profiles = readColorProfiles()
        val existing = profiles.indexOfFirst {
            it.name.equals(safeName, ignoreCase = true)
        }
        if (existing >= 0) {
            Toast.makeText(
                this,
                if (appLanguage == "id") "Nama profil sudah dipakai. Pilih nama lain atau gunakan Save Changes." else "That profile name already exists. Choose another name or use Save Changes.",
                Toast.LENGTH_SHORT
            ).show()
            return false
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
            shadowOpacityPercent = shadowOpacityPercent,
            glowPercent = glowPercent,
            responsePercent = responsePercent,
            cornerRadiusDp = cornerRadiusDp,
            verticalMode = isVerticalMode,
            singleAudioMode = singleAudioMode,
            bassMinHz = bassMinHz,
            bassMaxHz = bassMaxHz,
            horizontalAlignment = horizontalAlignment,
            mirrorMode = mirrorMode,
            positionXdp = positionXdp,
            positionYdp = positionYdp,
            positionMode = positionMode,
            rotationDegrees = rotationDegrees,
            floatingOverlayEnabled = floatingOverlayEnabled
        )
        profiles.add(entry)

        writeColorProfiles(profiles)
        prefs.edit()
            .putString("active_color_profile", safeName)
            .putString("profile_base_name", safeName)
            .putString("last_managed_color_profile", safeName)
            .putBoolean("profile_dirty", false)
            .apply()
        loadedProfileName = safeName
        profileDirty = false
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
        onRename: (String, String) -> Unit,
        onPreview: (ColorProfileEntry?) -> Unit = {}
    ) {
        val profiles = readColorProfiles()
        if (profiles.isEmpty()) {
            Toast.makeText(this, if (appLanguage == "id") "Belum ada profil. Simpan dulu lewat SIMPAN SEBAGAI." else "No saved profiles yet. Use SAVE AS PROFILE first.", Toast.LENGTH_SHORT).show()
            return
        }

        // The manager opens on the profile currently associated with the working state,
        // not on an unrelated last-touched row from an earlier visit.
        val preferredProfileName = loadedProfileName ?: prefs.getString("last_managed_color_profile", null)
        val initialIndex = profiles.indexOfFirst { it.name.equals(preferredProfileName, ignoreCase = true) }
            .let { if (it >= 0) it else profiles.lastIndex }
        var selectedIndex = initialIndex.coerceIn(0, profiles.lastIndex)
        var previewIndex: Int? = null
        var accepted = false
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val listPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(5.dp(), 5.dp(), 5.dp(), 8.dp())
        }
        val currentLabel = TextView(this).apply {
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setLineSpacing(2.dp().toFloat(), 1f)
            setPadding(14.dp(), 11.dp(), 14.dp(), 11.dp())
            applyPillStyle(this, true)
        }
        listPanel.addView(currentLabel, matchWrap().apply { bottomMargin = 13.dp() })
        listPanel.addView(sectionTitle(if (appLanguage == "id") "PRESET SAYA" else "MY PRESETS").apply {
            setPadding(8.dp(), 0, 8.dp(), 8.dp())
        })
        val rowViews = mutableListOf<TextView>()
        fun refreshSelection() {
            val previewed = previewIndex?.takeIf { it in profiles.indices }
            currentLabel.text = when {
                previewed != null -> {
                    val prefix = if (appLanguage == "id") "SEDANG PREVIEW" else "PREVIEWING PRESET"
                    "$prefix\n${profiles[previewed].name}"
                }
                profileDirty -> {
                    if (appLanguage == "id") "●  CURRENT STATE\nPerubahan belum disimpan" else "●  CURRENT STATE\nUnsaved changes"
                }
                !loadedProfileName.isNullOrBlank() -> {
                    val prefix = if (appLanguage == "id") "PRESET AKTIF" else "ACTIVE PRESET"
                    "$prefix\n$loadedProfileName"
                }
                else -> {
                    if (appLanguage == "id") "●  CURRENT STATE\nKonfigurasi aktif" else "●  CURRENT STATE\nActive configuration"
                }
            }
            rowViews.forEachIndexed { index, row ->
                val isPreview = previewed == index
                val isCurrentPreset = previewed == null && !profileDirty &&
                    profiles[index].name.equals(loadedProfileName, ignoreCase = true)
                // Exactly one trackpoint: preview row, active preset row, or
                // the Current State card when no saved preset is current.
                val hasPointer = isPreview || isCurrentPreset
                val suffix = when {
                    isPreview -> "  · PREVIEW"
                    isCurrentPreset -> "  · CURRENT"
                    else -> ""
                }
                row.text = (if (hasPointer) "●  " else "○  ") + profiles[index].name + suffix
                applyPillStyle(row, hasPointer)
            }
        }
        profiles.forEachIndexed { index, profile ->
            val row = glassButton(profile.name).apply {
                gravity = Gravity.CENTER_VERTICAL
                textSize = 13f
                setPadding(14.dp(), 11.dp(), 14.dp(), 11.dp())
                setOnClickListener {
                    selectedIndex = index
                    previewIndex = index
                    profilePreviewActive = true
                    onPreview(profile)
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
            if (appLanguage == "id") "PROFIL TERSIMPAN" else "SAVED PROFILES",
            scroll, footer, 0.96f, 0.72f
        )
        dialog.setOnDismissListener {
            if (!accepted) onPreview(null)
        }
        loadButton.setOnClickListener {
            val chosen = profiles[selectedIndex]
            accepted = true
            profilePreviewActive = false
            prefs.edit()
                .putString("last_managed_color_profile", chosen.name)
                .putString("profile_base_name", chosen.name)
                .putString("active_color_profile", chosen.name)
                .putBoolean("profile_dirty", false)
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
                    prefs.edit().putString("last_managed_color_profile", safeName).apply()
                    if (loadedProfileName.equals(chosen.name, ignoreCase = true)) {
                        loadedProfileName = safeName
                        prefs.edit().putString("profile_base_name", safeName).apply()
                        if (!profileDirty) prefs.edit().putString("active_color_profile", safeName).apply()
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
                if (loadedProfileName.equals(chosen.name, ignoreCase = true)) {
                    loadedProfileName = fallbackProfile
                    prefs.edit().putString("profile_base_name", fallbackProfile).apply()
                    if (fallbackProfile == null) prefs.edit().remove("active_color_profile").putBoolean("profile_dirty", false).apply()
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

        var selectedIndex = 0
        var usingPreset = false
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
                applyPillStyle(button, index == selectedIndex)
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
        val presetScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isHorizontalFadingEdgeEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = true
            clipChildren = true
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            background = null
        }
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // A small safety gutter keeps preset chips from touching the glass rails.
            setPadding(12.dp(), 0, 12.dp(), 0)
        }
        presetScroll.addView(presetRow)
        fun previewPalette(preset: Pair<String, IntArray>) {
            draftColors.clear()
            draftColors.addAll(preset.second.take(5))
            while (draftColors.size < 5) draftColors.add(Color.WHITE)
            selectedIndex = 0
            usingPreset = true
            updateEditorFromColor(draftColors[0])
            spectrumColors.clear()
            spectrumColors.addAll(draftColors.take(5))
            refreshColorSwatches()
            scheduleRealtimeUpdate()
        }

        val presets = listOf(
            Pair("WHITE", intArrayOf(Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE)),
            Pair("WHITE · CYAN · GOLD", intArrayOf(Color.WHITE, Color.parseColor("#22D3EE"), Color.WHITE, Color.parseColor("#FFD166"), Color.WHITE)),
            Pair("SPECTRUM RGB", intArrayOf(Color.parseColor("#FF1744"), Color.parseColor("#FF9100"), Color.parseColor("#FFEA00"), Color.parseColor("#22C55E"), Color.parseColor("#2979FF"))),
            Pair("NEON", intArrayOf(Color.parseColor("#00F5FF"), Color.parseColor("#A855F7"), Color.parseColor("#FF00AA"), Color.parseColor("#FFFFFF"), Color.parseColor("#22D3EE"))),
            Pair("SUNSET", intArrayOf(Color.parseColor("#FF1744"), Color.parseColor("#FF9100"), Color.parseColor("#FFEA00"), Color.parseColor("#9C27B0"), Color.parseColor("#FF6B35"))),
            Pair("OCEAN", intArrayOf(Color.parseColor("#001F54"), Color.parseColor("#0077B6"), Color.parseColor("#00B4D8"), Color.parseColor("#90E0EF"), Color.WHITE)),
            Pair("FOREST", intArrayOf(Color.parseColor("#14532D"), Color.parseColor("#22C55E"), Color.parseColor("#A3E635"), Color.parseColor("#FACC15"), Color.parseColor("#0F766E"))),
            Pair("ICE", intArrayOf(Color.WHITE, Color.parseColor("#BAE6FD"), Color.parseColor("#38BDF8"), Color.parseColor("#4F46E5"), Color.parseColor("#A5F3FC"))),
            Pair("AURORA", intArrayOf(Color.parseColor("#A7F3D0"), Color.parseColor("#34D399"), Color.parseColor("#38BDF8"), Color.parseColor("#818CF8"), Color.parseColor("#E879F9"))),
            Pair("PLASMA", intArrayOf(Color.parseColor("#FF2D95"), Color.parseColor("#C026D3"), Color.parseColor("#7C3AED"), Color.parseColor("#2563EB"), Color.parseColor("#22D3EE"))),
            Pair("AMBER", intArrayOf(Color.parseColor("#FFF7CC"), Color.parseColor("#FFE082"), Color.parseColor("#FFB300"), Color.parseColor("#FB8C00"), Color.parseColor("#FFF3B0"))),
            Pair("MINT", intArrayOf(Color.parseColor("#ECFDF5"), Color.parseColor("#A7F3D0"), Color.parseColor("#6EE7B7"), Color.parseColor("#2DD4BF"), Color.parseColor("#99F6E4"))),
            Pair("ARCTIC WHITE", intArrayOf(Color.WHITE, Color.parseColor("#E0F7FA"), Color.parseColor("#80DEEA"), Color.parseColor("#29B6F6"), Color.WHITE)),
            Pair("CYAN LASER", intArrayOf(Color.parseColor("#E0FFFF"), Color.parseColor("#80DEEA"), Color.parseColor("#00E5FF"), Color.parseColor("#00B8D4"), Color.parseColor("#E0FFFF"))),
            Pair("SYNTHWAVE", intArrayOf(Color.parseColor("#FF4ECD"), Color.parseColor("#D500F9"), Color.parseColor("#7C4DFF"), Color.parseColor("#304FFE"), Color.parseColor("#00E5FF"))),
            Pair("CYBERPUNK", intArrayOf(Color.parseColor("#F500FF"), Color.parseColor("#FF2E93"), Color.parseColor("#FFEA00"), Color.parseColor("#00F5D4"), Color.parseColor("#00BBF9"))),
            Pair("FIRESTORM", intArrayOf(Color.parseColor("#FFF3B0"), Color.parseColor("#FFD166"), Color.parseColor("#F77F00"), Color.parseColor("#D62828"), Color.parseColor("#6A040F"))),
            Pair("LAVA", intArrayOf(Color.parseColor("#FF6D00"), Color.parseColor("#FF3D00"), Color.parseColor("#DD2C00"), Color.parseColor("#B71C1C"), Color.parseColor("#4A001F"))),
            Pair("GOLDEN HOUR", intArrayOf(Color.parseColor("#FFF8E1"), Color.parseColor("#FFE082"), Color.parseColor("#FFC107"), Color.parseColor("#FF8F00"), Color.parseColor("#FFCA28"))),
            Pair("ROYAL PURPLE", intArrayOf(Color.parseColor("#F3E5F5"), Color.parseColor("#CE93D8"), Color.parseColor("#AB47BC"), Color.parseColor("#7B1FA2"), Color.parseColor("#311B92"))),
            Pair("BLUE LASER", intArrayOf(Color.parseColor("#E3F2FD"), Color.parseColor("#90CAF9"), Color.parseColor("#42A5F5"), Color.parseColor("#1E88E5"), Color.parseColor("#0D47A1"))),
            Pair("DEEP SEA", intArrayOf(Color.parseColor("#E0FBFC"), Color.parseColor("#98C1D9"), Color.parseColor("#3D5A80"), Color.parseColor("#293241"), Color.parseColor("#001219"))),
            Pair("AQUA POP", intArrayOf(Color.parseColor("#CCFBF1"), Color.parseColor("#5EEAD4"), Color.parseColor("#2DD4BF"), Color.parseColor("#06B6D4"), Color.parseColor("#0EA5E9"))),
            Pair("CANDY POP", intArrayOf(Color.parseColor("#FF80AB"), Color.parseColor("#EA80FC"), Color.parseColor("#B388FF"), Color.parseColor("#8C9EFF"), Color.parseColor("#80D8FF"))),
            Pair("PASTEL DREAM", intArrayOf(Color.parseColor("#FBCFE8"), Color.parseColor("#DDD6FE"), Color.parseColor("#BFDBFE"), Color.parseColor("#A7F3D0"), Color.parseColor("#FEF3C7"))),
            Pair("TOXIC GREEN", intArrayOf(Color.parseColor("#F4FF81"), Color.parseColor("#C6FF00"), Color.parseColor("#76FF03"), Color.parseColor("#00E676"), Color.parseColor("#00BFA5"))),
            Pair("MAGENTA PULSE", intArrayOf(Color.parseColor("#FCE4EC"), Color.parseColor("#F48FB1"), Color.parseColor("#EC407A"), Color.parseColor("#D500F9"), Color.parseColor("#7B1FA2"))),
            Pair("AMETHYST", intArrayOf(Color.parseColor("#EDE7F6"), Color.parseColor("#B39DDB"), Color.parseColor("#9575CD"), Color.parseColor("#673AB7"), Color.parseColor("#4527A0"))),
            Pair("RGB CLASSIC", intArrayOf(Color.parseColor("#FF0000"), Color.parseColor("#FFFF00"), Color.parseColor("#00FF00"), Color.parseColor("#00FFFF"), Color.parseColor("#0000FF"))),
            Pair("WHITE ICE GOLD", intArrayOf(Color.WHITE, Color.parseColor("#B3E5FC"), Color.parseColor("#4FC3F7"), Color.parseColor("#FFD54F"), Color.WHITE)),
            Pair("CHERRY BLOSSOM", intArrayOf(Color.parseColor("#FFF1F2"), Color.parseColor("#FDA4AF"), Color.parseColor("#FB7185"), Color.parseColor("#E11D48"), Color.parseColor("#BE123C"))),
            Pair("RAINBOW SPECTRUM", intArrayOf(Color.parseColor("#FF1744"), Color.parseColor("#FF9100"), Color.parseColor("#FFEA00"), Color.parseColor("#00E676"), Color.parseColor("#2979FF"))),
            Pair("FIRE", intArrayOf(Color.parseColor("#FFF176"), Color.parseColor("#FFB300"), Color.parseColor("#FB8C00"), Color.parseColor("#E53935"), Color.parseColor("#7F0000"))),
            Pair("ICE CRYSTAL", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#E1F5FE"), Color.parseColor("#81D4FA"), Color.parseColor("#29B6F6"), Color.parseColor("#3949AB"))),
            Pair("MONO SILVER", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#CFD8DC"), Color.parseColor("#90A4AE"), Color.parseColor("#546E7A"), Color.parseColor("#263238"))),
            Pair("VAPORWAVE", intArrayOf(Color.parseColor("#FF71CE"), Color.parseColor("#B967FF"), Color.parseColor("#01CDFE"), Color.parseColor("#05FFA1"), Color.parseColor("#FFFB96"))),
            Pair("MATRIX", intArrayOf(Color.parseColor("#E8F5E9"), Color.parseColor("#A5D6A7"), Color.parseColor("#66BB6A"), Color.parseColor("#00C853"), Color.parseColor("#1B5E20"))),
            Pair("GOLD FOIL", intArrayOf(Color.parseColor("#FFFDE7"), Color.parseColor("#FFE082"), Color.parseColor("#FFC107"), Color.parseColor("#FF8F00"), Color.parseColor("#6D4C41"))),
            Pair("LIQUID VIOLET", intArrayOf(Color.parseColor("#F3E5F5"), Color.parseColor("#CE93D8"), Color.parseColor("#AB47BC"), Color.parseColor("#7E57C2"), Color.parseColor("#311B92"))),
            Pair("SUNSET OCEAN", intArrayOf(Color.parseColor("#FF6F61"), Color.parseColor("#FFB26B"), Color.parseColor("#FFE29A"), Color.parseColor("#55CBCD"), Color.parseColor("#167D9A"))),
            Pair("SYNTHWAVE SUN", intArrayOf(Color.parseColor("#FF4D8D"), Color.parseColor("#FF7B54"), Color.parseColor("#FFCA6A"), Color.parseColor("#B45EFF"), Color.parseColor("#4D4CFF"))),
            Pair("DISCO NIGHT", intArrayOf(Color.parseColor("#FF00A8"), Color.parseColor("#8A2BE2"), Color.parseColor("#3949AB"), Color.parseColor("#00D4FF"), Color.parseColor("#F8F7FF"))),
            Pair("AURORA GLOW", intArrayOf(Color.parseColor("#B2FFDA"), Color.parseColor("#43E6B4"), Color.parseColor("#38C7E8"), Color.parseColor("#7C83FD"), Color.parseColor("#E879F9"))),
            Pair("GLASS TEAL", intArrayOf(Color.parseColor("#E0F7FA"), Color.parseColor("#80CBC4"), Color.parseColor("#4DB6AC"), Color.parseColor("#00897B"), Color.parseColor("#004D40"))),
            Pair("NEON MAGENTA", intArrayOf(Color.parseColor("#FFE0F2"), Color.parseColor("#FF7AD9"), Color.parseColor("#FF00A8"), Color.parseColor("#B000FF"), Color.parseColor("#5E00A8"))),
            Pair("CHERRY COLA", intArrayOf(Color.parseColor("#FFD6D6"), Color.parseColor("#FF6B6B"), Color.parseColor("#C1121F"), Color.parseColor("#780000"), Color.parseColor("#3D0000"))),
            Pair("LAGOON", intArrayOf(Color.parseColor("#D9FFF8"), Color.parseColor("#83E8D2"), Color.parseColor("#35C9C8"), Color.parseColor("#168AAD"), Color.parseColor("#184E77"))),
            Pair("CITRUS POP", intArrayOf(Color.parseColor("#FFF9C4"), Color.parseColor("#EEFF41"), Color.parseColor("#C6FF00"), Color.parseColor("#76FF03"), Color.parseColor("#FF9100"))),
            Pair("NEBULA", intArrayOf(Color.parseColor("#F8BBD0"), Color.parseColor("#CE93D8"), Color.parseColor("#9575CD"), Color.parseColor("#5C6BC0"), Color.parseColor("#26C6DA"))),
            Pair("GALACTIC", intArrayOf(Color.parseColor("#E1BEE7"), Color.parseColor("#BA68C8"), Color.parseColor("#7E57C2"), Color.parseColor("#3949AB"), Color.parseColor("#00BCD4"))),
            Pair("EVENT HORIZON", intArrayOf(Color.parseColor("#FFF3E0"), Color.parseColor("#FFAB91"), Color.parseColor("#FF7043"), Color.parseColor("#8E24AA"), Color.parseColor("#1A237E"))),
            Pair("SPOTLIGHT", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#FFF9C4"), Color.parseColor("#FFE082"), Color.parseColor("#FFB74D"), Color.parseColor("#FF7043"))),
            Pair("MOONLIGHT", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#E8EAF6"), Color.parseColor("#C5CAE9"), Color.parseColor("#7986CB"), Color.parseColor("#283593"))),
            Pair("INFERNO", intArrayOf(Color.parseColor("#FFF3E0"), Color.parseColor("#FF9800"), Color.parseColor("#F4511E"), Color.parseColor("#C62828"), Color.parseColor("#4E0000"))),
            Pair("GLACIER", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#E0F7FA"), Color.parseColor("#B2EBF2"), Color.parseColor("#4DD0E1"), Color.parseColor("#0277BD"))),
            Pair("EMERALD FOREST", intArrayOf(Color.parseColor("#E8F5E9"), Color.parseColor("#A5D6A7"), Color.parseColor("#4CAF50"), Color.parseColor("#00897B"), Color.parseColor("#004D40"))),
            Pair("TWILIGHT", intArrayOf(Color.parseColor("#FFC1CC"), Color.parseColor("#D8A7FF"), Color.parseColor("#9C8CFF"), Color.parseColor("#647DFF"), Color.parseColor("#2B3A8A"))),
            Pair("DEEP OCEAN", intArrayOf(Color.parseColor("#B3E5FC"), Color.parseColor("#4FC3F7"), Color.parseColor("#0288D1"), Color.parseColor("#01579B"), Color.parseColor("#001B44"))),
            Pair("ROSE NOIR", intArrayOf(Color.parseColor("#FFE4E9"), Color.parseColor("#F48FB1"), Color.parseColor("#C2185B"), Color.parseColor("#880E4F"), Color.parseColor("#31102B"))),
            Pair("FILM NOIR", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#B0BEC5"), Color.parseColor("#78909C"), Color.parseColor("#455A64"), Color.parseColor("#111820"))),
            Pair("PURE BLACK", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#BDBDBD"), Color.parseColor("#757575"), Color.parseColor("#424242"), Color.parseColor("#000000"))),
            Pair("DEEP SPACE", intArrayOf(Color.parseColor("#D1C4E9"), Color.parseColor("#9575CD"), Color.parseColor("#5E35B1"), Color.parseColor("#283593"), Color.parseColor("#070B24"))),
            Pair("CORAL REEF", intArrayOf(Color.parseColor("#FFE0B2"), Color.parseColor("#FFAB91"), Color.parseColor("#FF8A65"), Color.parseColor("#26C6DA"), Color.parseColor("#00838F"))),
            Pair("TROPICAL PUNCH", intArrayOf(Color.parseColor("#FF80AB"), Color.parseColor("#FF4081"), Color.parseColor("#FFAB40"), Color.parseColor("#EEFF41"), Color.parseColor("#00E5FF"))),
            Pair("ELECTRIC LIME", intArrayOf(Color.parseColor("#F9FBE7"), Color.parseColor("#EEFF41"), Color.parseColor("#C6FF00"), Color.parseColor("#76FF03"), Color.parseColor("#00C853"))),
            Pair("BLUE HOUR", intArrayOf(Color.parseColor("#E3F2FD"), Color.parseColor("#90CAF9"), Color.parseColor("#5C6BC0"), Color.parseColor("#3949AB"), Color.parseColor("#1A237E"))),
            Pair("ULTRAVIOLET", intArrayOf(Color.parseColor("#F3E5F5"), Color.parseColor("#EA80FC"), Color.parseColor("#D500F9"), Color.parseColor("#7C4DFF"), Color.parseColor("#311B92"))),
            Pair("PINK LEMONADE", intArrayOf(Color.parseColor("#FFF1F2"), Color.parseColor("#FDA4AF"), Color.parseColor("#FB7185"), Color.parseColor("#F9A8D4"), Color.parseColor("#FDE68A"))),
            Pair("PEACH SORBET", intArrayOf(Color.parseColor("#FFF1E6"), Color.parseColor("#FFD6A5"), Color.parseColor("#FFADAD"), Color.parseColor("#FFB4A2"), Color.parseColor("#E5989B"))),
            Pair("LAVENDER HAZE", intArrayOf(Color.parseColor("#F5F0FF"), Color.parseColor("#D8B4FE"), Color.parseColor("#C4B5FD"), Color.parseColor("#A5B4FC"), Color.parseColor("#818CF8"))),
            Pair("SAPPHIRE PULSE", intArrayOf(Color.parseColor("#E3F2FD"), Color.parseColor("#64B5F6"), Color.parseColor("#1E88E5"), Color.parseColor("#3949AB"), Color.parseColor("#0D1B5E"))),
            Pair("EMERALD PULSE", intArrayOf(Color.parseColor("#E8F5E9"), Color.parseColor("#81C784"), Color.parseColor("#26A69A"), Color.parseColor("#00897B"), Color.parseColor("#1B5E20"))),
            Pair("RUBY WAVE", intArrayOf(Color.parseColor("#FFEBEE"), Color.parseColor("#EF9A9A"), Color.parseColor("#E53935"), Color.parseColor("#C62828"), Color.parseColor("#6A0000"))),
            Pair("OPAL WHITE", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#E0F2F1"), Color.parseColor("#E1F5FE"), Color.parseColor("#F3E5F5"), Color.parseColor("#FFF8E1"))),
            Pair("PEARL ICE", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#ECEFF1"), Color.parseColor("#CFD8DC"), Color.parseColor("#B0BEC5"), Color.parseColor("#80DEEA"))),
            Pair("SILVER STORM", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#CFD8DC"), Color.parseColor("#90A4AE"), Color.parseColor("#607D8B"), Color.parseColor("#37474F"))),
            Pair("BRONZE FIRE", intArrayOf(Color.parseColor("#FFF3E0"), Color.parseColor("#FFCC80"), Color.parseColor("#FF9800"), Color.parseColor("#A1887F"), Color.parseColor("#4E342E"))),
            Pair("DESERT DUSK", intArrayOf(Color.parseColor("#FFE0B2"), Color.parseColor("#FFCC80"), Color.parseColor("#D7A86E"), Color.parseColor("#B7795B"), Color.parseColor("#704C5E"))),
            Pair("DESERT MIRAGE", intArrayOf(Color.parseColor("#FFF8E1"), Color.parseColor("#FFE0B2"), Color.parseColor("#80CBC4"), Color.parseColor("#4DB6AC"), Color.parseColor("#00695C"))),
            Pair("AUTUMN LEAVES", intArrayOf(Color.parseColor("#FFF3E0"), Color.parseColor("#FFB74D"), Color.parseColor("#F57C00"), Color.parseColor("#A1887F"), Color.parseColor("#6D4C41"))),
            Pair("SPRING BLOOM", intArrayOf(Color.parseColor("#FCE4EC"), Color.parseColor("#F8BBD0"), Color.parseColor("#C5E1A5"), Color.parseColor("#80CBC4"), Color.parseColor("#FFF59D"))),
            Pair("SUMMER NEON", intArrayOf(Color.parseColor("#FFEA00"), Color.parseColor("#FF9100"), Color.parseColor("#FF4081"), Color.parseColor("#00E5FF"), Color.parseColor("#76FF03"))),
            Pair("WINTER FROST", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#E1F5FE"), Color.parseColor("#B3E5FC"), Color.parseColor("#81D4FA"), Color.parseColor("#4FC3F7"))),
            Pair("OCEANIC TEAL", intArrayOf(Color.parseColor("#E0F2F1"), Color.parseColor("#80CBC4"), Color.parseColor("#26A69A"), Color.parseColor("#00897B"), Color.parseColor("#004D40"))),
            Pair("MIDNIGHT CYAN", intArrayOf(Color.parseColor("#E0FFFF"), Color.parseColor("#00E5FF"), Color.parseColor("#00B8D4"), Color.parseColor("#006064"), Color.parseColor("#001F2D"))),
            Pair("REDLINE", intArrayOf(Color.parseColor("#FFEBEE"), Color.parseColor("#FF5252"), Color.parseColor("#F44336"), Color.parseColor("#D50000"), Color.parseColor("#5A0000"))),
            Pair("TOXIC WASTE", intArrayOf(Color.parseColor("#F4FF81"), Color.parseColor("#C6FF00"), Color.parseColor("#AEEA00"), Color.parseColor("#76FF03"), Color.parseColor("#00C853"))),
            Pair("ACID VIOLET", intArrayOf(Color.parseColor("#FCE4EC"), Color.parseColor("#F500FF"), Color.parseColor("#D500F9"), Color.parseColor("#AA00FF"), Color.parseColor("#6200EA"))),
            Pair("ELECTRIC BLUE", intArrayOf(Color.parseColor("#E3F2FD"), Color.parseColor("#82B1FF"), Color.parseColor("#448AFF"), Color.parseColor("#2979FF"), Color.parseColor("#0D47A1"))),
            Pair("PIXEL RAINBOW", intArrayOf(Color.parseColor("#FF1744"), Color.parseColor("#FFEA00"), Color.parseColor("#00E676"), Color.parseColor("#00B0FF"), Color.parseColor("#D500F9"))),
            Pair("CRT PHOSPHOR", intArrayOf(Color.parseColor("#E8F5E9"), Color.parseColor("#A5D6A7"), Color.parseColor("#66BB6A"), Color.parseColor("#00E676"), Color.parseColor("#1B5E20"))),
            Pair("COPPER SUNSET", intArrayOf(Color.parseColor("#FFF3E0"), Color.parseColor("#FFCC80"), Color.parseColor("#E6A15C"), Color.parseColor("#B86B45"), Color.parseColor("#6D3B36"))),
            Pair("CHAMPAGNE", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#FFF8E1"), Color.parseColor("#FFE0B2"), Color.parseColor("#E6C88A"), Color.parseColor("#B59A68"))),
            Pair("MONOCHROME ICE", intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#ECEFF1"), Color.parseColor("#B0BEC5"), Color.parseColor("#78909C"), Color.parseColor("#263238"))),
            Pair("PRISM", intArrayOf(Color.parseColor("#FF4081"), Color.parseColor("#FFAB40"), Color.parseColor("#EEFF41"), Color.parseColor("#40C4FF"), Color.parseColor("#B388FF"))),
            Pair("AURORA BOREALIS", intArrayOf(Color.parseColor("#A7FFEB"), Color.parseColor("#64FFDA"), Color.parseColor("#1DE9B6"), Color.parseColor("#80D8FF"), Color.parseColor("#B388FF"))),
            Pair("DEEP AMETHYST", intArrayOf(Color.parseColor("#EDE7F6"), Color.parseColor("#B39DDB"), Color.parseColor("#7E57C2"), Color.parseColor("#512DA8"), Color.parseColor("#1A0938"))),
            Pair("COBALT GOLD", intArrayOf(Color.parseColor("#FFF8E1"), Color.parseColor("#FFD54F"), Color.parseColor("#42A5F5"), Color.parseColor("#1565C0"), Color.parseColor("#0D1B4C"))),
            Pair("NEON CORAL", intArrayOf(Color.parseColor("#FFF1E6"), Color.parseColor("#FF8A80"), Color.parseColor("#FF5252"), Color.parseColor("#FF4081"), Color.parseColor("#E040FB"))),
            Pair("PASTEL SPECTRUM", intArrayOf(Color.parseColor("#FFB3BA"), Color.parseColor("#FFDFBA"), Color.parseColor("#FFFFBA"), Color.parseColor("#BAFFC9"), Color.parseColor("#BAE1FF")))
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
                stateListAnimator = null
                elevation = 0f
                setOnClickListener { previewPalette(preset) }
            }
            presetRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 43.dp()).apply {
                setMargins(0, 0, 7.dp(), 0)
            })
        }
        val presetStripNavigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = true
        }
        val presetStripLeft = glassButton("‹").apply {
            textSize = 22f
            includeFontPadding = false
            contentDescription = if (appLanguage == "id") "Preset warna sebelumnya" else "Previous color presets"
        }
        val presetStripRight = glassButton("›").apply {
            textSize = 22f
            includeFontPadding = false
            contentDescription = if (appLanguage == "id") "Preset warna berikutnya" else "More color presets"
        }
        styleScrollArrow(presetStripLeft)
        styleScrollArrow(presetStripRight)
        presetStripNavigation.addView(presetStripLeft, LinearLayout.LayoutParams(34.dp(), 40.dp()).apply {
            marginEnd = 5.dp()
        })
        presetStripNavigation.addView(presetScroll, LinearLayout.LayoutParams(0, 46.dp(), 1f))
        presetStripNavigation.addView(presetStripRight, LinearLayout.LayoutParams(34.dp(), 40.dp()).apply {
            marginStart = 5.dp()
        })
        panel.addView(presetStripNavigation)
        // Color Studio is a separate dialog window, so MainActivity itself loses
        // window focus while this strip is visible. Install the same viewport-pinned
        // liquid-glass edge treatment locally for this one strip, and explicitly let
        // this drawable render while the Activity window is unfocused. No other modal
        // dialog receives scroll edge-glass or bubble indicators.
        installScrollEdgeGlassFade(
            presetScroll,
            horizontal = true,
            drawWhenWindowUnfocused = true
        )

        fun refreshPresetStripArrows() {
            val child = presetScroll.getChildAt(0)
            val maxScroll = ((child?.width ?: 0) - presetScroll.width).coerceAtLeast(0)
            val canGoLeft = presetScroll.scrollX > 2.dp()
            val canGoRight = maxScroll > 2.dp() && presetScroll.scrollX < maxScroll - 2.dp()
            presetStripLeft.isEnabled = canGoLeft
            presetStripRight.isEnabled = canGoRight
            presetStripLeft.alpha = if (canGoLeft) 0.94f else 0.42f
            presetStripRight.alpha = if (canGoRight) 0.94f else 0.42f
        }
        presetStripLeft.setOnClickListener {
            presetScroll.smoothScrollBy(-(presetScroll.width * 0.78f).toInt().coerceAtLeast(90.dp()), 0)
        }
        presetStripRight.setOnClickListener {
            presetScroll.smoothScrollBy((presetScroll.width * 0.78f).toInt().coerceAtLeast(90.dp()), 0)
        }
        presetScroll.setOnScrollChangeListener { _, _, _, _, _ -> refreshPresetStripArrows() }
        // Always open the quick preset strip at its first item. A clickable TextView
        // can otherwise request focus and make HorizontalScrollView reveal a later chip.
        presetScroll.post {
            presetScroll.scrollTo(0, 0)
            refreshPresetStripArrows()
        }

        val openAllPresetsButton = glassButton(
            if (appLanguage == "id") "▦  BUKA SEMUA PRESET WARNA (${presets.size})" else "▦  OPEN ALL PRESETS (${presets.size})"
        ).apply {
            textSize = 10f
            contentDescription = if (appLanguage == "id") "Buka galeri semua preset warna" else "Open the complete color preset gallery"
        }
        panel.addView(openAllPresetsButton, matchWrap().apply { topMargin = 8.dp() })

        openAllPresetsButton.setOnClickListener {
            val galleryBody = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(3.dp(), 4.dp(), 3.dp(), 4.dp())
            }
            galleryBody.addView(subtitle(
                if (appLanguage == "id") "Pilih warna buat preview langsung. Tekan TERAPKAN PALET di jendela utama untuk menyimpan." else "Tap a palette to preview it live. Use APPLY PALETTE in the main editor to keep it."
            ))
            val searchInput = EditText(this).apply {
                hint = if (appLanguage == "id") "Cari preset warna…" else "Search color presets…"
                setSingleLine(true)
                textSize = 12f
                inputType = InputType.TYPE_CLASS_TEXT
                val lightGallery = isLightTheme()
                setTextColor(if (lightGallery) Color.parseColor("#26344B") else Color.WHITE)
                setHintTextColor(if (lightGallery) Color.parseColor("#8290A6") else Color.parseColor("#98A6BD"))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    if (lightGallery) intArrayOf(Color.WHITE, Color.parseColor("#EEF2F8"))
                    else intArrayOf(Color.parseColor("#26374F"), Color.parseColor("#152237"))
                ).apply {
                    cornerRadius = 14.dp().toFloat()
                    setStroke(1.dp(), if (lightGallery) 0x668C9BB1 else 0x777D91B8)
                }
                setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            }
            galleryBody.addView(searchInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 42.dp()).apply { topMargin = 9.dp() })
            val galleryScroll = ScrollView(this).apply {
                isFillViewport = false
                isVerticalScrollBarEnabled = false
                clipToPadding = false
                setPadding(0, 4.dp(), 0, 10.dp())
            }
            val galleryGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            galleryScroll.addView(galleryGrid, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            galleryBody.addView(galleryScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = 5.dp() })

            var galleryDialog: AlertDialog? = null
            fun renderPaletteGallery(query: String) {
                galleryGrid.removeAllViews()
                val filtered = presets.filter { it.first.contains(query.trim(), ignoreCase = true) }
                if (filtered.isEmpty()) {
                    galleryGrid.addView(subtitle(if (appLanguage == "id") "Preset tidak ketemu." else "No matching palette."), matchWrap().apply { topMargin = 14.dp() })
                    return
                }
                filtered.chunked(2).forEach { pair ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    pair.forEach { preset ->
                        val averageLuma = preset.second.map { color ->
                            (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0
                        }.average()
                        val textColor = if (averageLuma > 0.64) Color.parseColor("#172235") else Color.WHITE
                        val tile = TextView(this).apply {
                            text = preset.first
                            gravity = Gravity.CENTER
                            textSize = 9.5f
                            maxLines = 2
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            includeFontPadding = false
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(textColor)
                            setPadding(7.dp(), 8.dp(), 7.dp(), 8.dp())
                            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, preset.second).apply {
                                cornerRadius = 14.dp().toFloat()
                                setStroke(1.dp(), if (isLightTheme()) 0x667889A5 else 0x667D91B8)
                            }
                            isClickable = true
                            isFocusable = true
                            contentDescription = preset.first
                            setOnClickListener { clickedView ->
                                // Keep the album open so users can compare palettes rapidly.
                                // previewPalette updates the main editor, selected layer,
                                // Live Spectrum and Floating overlay without committing until Apply.
                                previewPalette(preset)
                                // Use the clicked View supplied by the listener. Referencing
                                // the local `tile` from inside its own `apply` initializer
                                // is out of scope during Kotlin initialization.
                                clickedView.animate().cancel()
                                clickedView.animate().scaleX(0.98f).scaleY(0.98f).setDuration(75L)
                                    .withEndAction {
                                        clickedView.animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                                    }
                                    .start()
                            }
                        }
                        row.addView(tile, LinearLayout.LayoutParams(0, 54.dp(), 1f).apply {
                            setMargins(3.dp(), 3.dp(), 3.dp(), 3.dp())
                        })
                    }
                    if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 54.dp(), 1f))
                    galleryGrid.addView(row, matchWrap())
                }
            }
            searchInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderPaletteGallery(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
            renderPaletteGallery("")
            val galleryFooter = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val galleryClose = glassButton(if (appLanguage == "id") "TUTUP" else "CLOSE")
            galleryFooter.addView(galleryClose, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()))
            galleryDialog = showGlassStudioDialog(
                if (appLanguage == "id") "SEMUA PRESET WARNA" else "ALL COLOR PRESETS",
                galleryBody, galleryFooter, 0.96f, 0.84f
            )
            galleryClose.setOnClickListener { galleryDialog?.dismiss() }
            galleryBody.post { installScrollAffordances(galleryBody) }
        }
        updateEditorFromColor(draftColors[0])

        // Palette editing is per active Floating Profile and is applied live.
        scroll.addView(panel)

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancelPalette = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        val applyPalette = glassButton(if (appLanguage == "id") "TERAPKAN PALET" else "APPLY PALETTE")
        footer.addView(cancelPalette, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(applyPalette, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(
            if (appLanguage == "id") "KELOLA PROFIL WARNA" else "MANAGE COLOR PROFILE",
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

                // Apply only this palette to the currently selected Floating Profile.
                // Scene save/load is handled separately by Floating Scene Manager.

                saveSettings()
                refreshColorSwatches()
                updateEffectButtons()
                syncSliderValue("HORIZONTAL WIDTH", overlayWidthDp)
                syncSliderValue("BAR HEIGHT / LENGTH", overlayHeightDp)
                syncSliderValue("HORIZONTAL BAR COUNT", barCount)
                syncSliderValue("BAR THICKNESS", barWidthDp)
                syncSliderValue("BAR GAP", barGapDp)
                syncSliderValue("SCALE", scalePercent)
                syncSliderValue("BAR OPACITY", opacityPercent)
                syncSliderValue("SHADOW BAR OPACITY", shadowOpacityPercent)
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
            "FLOATING SCENE MANAGER" to "PENGELOLA SCENE FLOATING",
            "Save every floating layer, its appearance, mode, response and position as one scene." to "Simpan semua layer floating, tampilan, mode, respons, dan posisinya dalam satu scene.",
            "CURRENT STATE · UNSAVED" to "KONDISI SAAT INI · BELUM DISIMPAN",
            "＋ SAVE SCENE AS" to "＋ SIMPAN SCENE",
            "MANAGE SCENES" to "KELOLA SCENE",
            "EXPORT SCENE" to "EKSPOR SCENE",
            "IMPORT SCENE" to "IMPOR SCENE",
            "Edit five color stops for the selected floating layer" to "Atur lima titik warna untuk layer floating yang dipilih",
            "✦  OPEN COLOR STUDIO" to "✦  BUKA COLOR STUDIO",
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
            "● AUDIO LIVE · ON" to "● AUDIO LIVE · NYALA",
            "● AUDIO LIVE · OFF" to "● AUDIO LIVE · MATI",
            "● AUDIO STARTING" to "● AUDIO MEMULAI",
            "CLEAN" to "BERSIH",
            "FROSTED" to "KACA BURAM",
            "HORIZONTAL WIDTH" to "LEBAR HORIZONTAL",
            "BAR HEIGHT / LENGTH" to "TINGGI / PANJANG BAR",
            "HORIZONTAL BAR COUNT" to "JUMLAH BAR HORIZONTAL",
            "BAR THICKNESS" to "KETEBALAN BAR",
            "BAR GAP" to "JARAK ANTAR BAR",
            "SCALE" to "SKALA",
            "BAR OPACITY" to "OPASITAS BAR",
            "SHADOW BAR OPACITY" to "OPASITAS SHADOW BAR",
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
            "MANAGE COLOR PROFILE" to "KELOLA PROFIL WARNA",
            "ALL COLOR PRESETS" to "SEMUA PRESET WARNA",
            "OPEN ALL PRESETS" to "BUKA SEMUA PRESET WARNA",
            "Search color presets…" to "Cari preset warna…",
            "No matching palette." to "Preset tidak ketemu.",
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
            "FLOATING PROFILE" to "PROFIL FLOATING",
            "Independent layers · hold a row to reorder" to "Layer independen · tahan baris untuk mengurutkan",
            "＋  ADD FLOATING PROFILE" to "＋  TAMBAH PROFIL FLOATING",
            "Aa  CUSTOM TEXT COLOR" to "Aa  WARNA TEKS KUSTOM",
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
     * A real, independent sibling view below a horizontal navigation row.
     * The HorizontalScrollView itself is never resized, padded, foregrounded,
     * wrapped, detached, or re-parented. Only this sibling draws the liquid-glass
     * track/thumb and reads scrollX from the original host.
     */
    private inner class HorizontalBubbleIndicatorView(
        private val host: HorizontalScrollView
    ) : View(this@MainActivity) {
        private val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val haloPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val glossPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
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
        }

        private fun childMetrics(): Triple<Float, Float, Float> {
            val child = host.getChildAt(0) ?: return Triple(0f, 0f, 0f)
            val margins = child.layoutParams as? ViewGroup.MarginLayoutParams
            val content = (maxOf(child.width, child.measuredWidth) +
                (margins?.leftMargin ?: 0) + (margins?.rightMargin ?: 0)).toFloat()
            val viewport = (host.width - host.paddingLeft - host.paddingRight).toFloat().coerceAtLeast(1f)
            return Triple(content, viewport, (content - viewport).coerceAtLeast(0f))
        }

        fun refreshMetrics() {
            val range = childMetrics().third
            // Do not inspect this indicator's own dimensions: when GONE Android
            // measures it as 0x0, which would otherwise prevent it becoming visible
            // again after a hidden list grows or is shown.
            val nextVisibility = if (host.width > 0 && range > 1f) View.VISIBLE else View.GONE
            if (visibility != nextVisibility) visibility = nextVisibility
            invalidate()
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val (contentWidth, viewportWidth, range) = childMetrics()
            if (range <= 1f || width <= 0 || height <= 0) return

            // This sibling spans the parent column, but its drawing lane is aligned
            // to the exact on-screen x-range of the original scroll viewport.
            host.getLocationOnScreen(hostLocation)
            getLocationOnScreen(indicatorLocation)
            val hostLeft = (hostLocation[0] - indicatorLocation[0]).toFloat()
            val inset = 9f * density
            val rawLeft = hostLeft + host.paddingLeft + inset
            val rawRight = hostLeft + host.width - host.paddingRight - inset
            if (rawRight <= 0f || rawLeft >= width.toFloat()) return
            val trackLeft = rawLeft.coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat())
            val trackRight = rawRight.coerceIn(trackLeft + 1f, width.toFloat())
            val trackWidth = (trackRight - trackLeft).coerceAtLeast(1f)
            val centerY = height / 2f
            val light = isLightTheme()
            val accent = currentAccent()
            val highlight = Color.rgb(
                (Color.red(accent) + 255) / 2,
                (Color.green(accent) + 255) / 2,
                (Color.blue(accent) + 255) / 2
            )

            trackPaint.shader = null
            trackPaint.color = if (light) Color.argb(74, 59, 76, 101) else Color.argb(92, 220, 231, 248)
            canvas.drawRoundRect(
                trackLeft, centerY - 1.7f * density, trackRight, centerY + 1.7f * density,
                3f * density, 3f * density, trackPaint
            )

            val proportionalWidth = trackWidth * (viewportWidth / contentWidth.coerceAtLeast(1f))
            // A capped capsule retains useful travel distance on short lists.
            val thumbMaxWidth = (trackWidth * 0.56f).coerceAtLeast(1f)
            val thumbMinWidth = minOf(24f * density, thumbMaxWidth)
            val thumbWidth = proportionalWidth.coerceIn(thumbMinWidth, thumbMaxWidth)
            val fraction = (host.scrollX.toFloat() / range).coerceIn(0f, 1f)
            val thumbLeft = trackLeft + (trackWidth - thumbWidth) * fraction
            val thumbRight = thumbLeft + thumbWidth

            haloPaint.shader = null
            haloPaint.color = Color.argb(if (light) 62 else 76,
                Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(
                thumbLeft - 1f * density, centerY - 6f * density,
                thumbRight + 1f * density, centerY + 6f * density,
                7f * density, 7f * density, haloPaint
            )

            if (cachedAccent != accent || cachedLight != light || bodyShader == null) {
                cachedAccent = accent
                cachedLight = light
                bodyShader = android.graphics.LinearGradient(
                    0f, centerY - 3.1f * density, 0f, centerY + 3.1f * density,
                    highlight,
                    if (light) accent else Color.rgb(
                        (Color.red(accent) * 0.92f).toInt(),
                        (Color.green(accent) * 0.92f).toInt(),
                        (Color.blue(accent) * 0.92f).toInt()
                    ),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            bodyPaint.shader = bodyShader
            bodyPaint.color = Color.WHITE
            canvas.drawRoundRect(
                thumbLeft, centerY - 3.2f * density, thumbRight, centerY + 3.2f * density,
                6f * density, 6f * density, bodyPaint
            )
            bodyPaint.shader = null

            glossPaint.shader = null
            glossPaint.color = Color.argb(if (light) 178 else 140, 255, 255, 255)
            canvas.drawRoundRect(
                thumbLeft + 4f * density, centerY - 2.25f * density,
                (thumbRight - 4f * density).coerceAtLeast(thumbLeft + 4f * density),
                centerY - 1.15f * density, 1f * density, 1f * density, glossPaint
            )
        }
    }

    /**
     * Separate viewport-fixed vertical bubble for the main page. It is a sibling
     * of the main ScrollView inside mainShell, so it never becomes part of the
     * page's moving content and never participates in touch/scroll dispatch.
     */
    private inner class VerticalBubbleIndicatorView(
        private val host: ScrollView
    ) : View(this@MainActivity) {
        private val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val haloPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val glossPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        private var cachedAccent = Int.MIN_VALUE
        private var bodyShader: android.graphics.Shader? = null

        init {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setWillNotDraw(false)
            setBackgroundColor(Color.TRANSPARENT)
        }

        fun refreshMetrics() {
            val child = host.getChildAt(0)
            val contentHeight = maxOf(child?.height ?: 0, child?.measuredHeight ?: 0).toFloat()
            val viewportHeight = (host.height - host.paddingTop - host.paddingBottom).toFloat().coerceAtLeast(1f)
            val range = (contentHeight - viewportHeight).coerceAtLeast(0f)
            val nextVisibility = if (host.width > 0 && host.height > 0 && range > 1f) View.VISIBLE else View.GONE
            if (visibility != nextVisibility) visibility = nextVisibility
            invalidate()
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val child = host.getChildAt(0) ?: return
            val contentHeight = maxOf(child.height, child.measuredHeight).toFloat()
            val viewportHeight = (host.height - host.paddingTop - host.paddingBottom).toFloat().coerceAtLeast(1f)
            val range = (contentHeight - viewportHeight).coerceAtLeast(0f)
            if (range <= 1f || width <= 0 || height <= 0) return

            val topInset = maxOf(14f * density, contentRoot.paddingTop.toFloat() + 5f * density)
            val bottomInset = maxOf(14f * density, contentRoot.paddingBottom.toFloat() + 5f * density)
            val top = topInset.coerceAtMost(height.toFloat() - 24f * density)
            val bottom = (height.toFloat() - bottomInset).coerceAtLeast(top + 24f * density)
            val trackHeight = (bottom - top).coerceAtLeast(1f)
            val x = width - 6f * density
            val light = isLightTheme()
            val accent = currentAccent()

            trackPaint.shader = null
            trackPaint.color = if (light) Color.argb(74, 59, 76, 101) else Color.argb(92, 220, 231, 248)
            canvas.drawRoundRect(x - 1.4f * density, top, x + 1.4f * density, bottom,
                3f * density, 3f * density, trackPaint)

            val thumbHeight = (trackHeight * viewportHeight / contentHeight.coerceAtLeast(1f)).coerceIn(
                minOf(30f * density, trackHeight), trackHeight
            )
            val fraction = (host.scrollY.toFloat() / range).coerceIn(0f, 1f)
            val thumbTop = top + (trackHeight - thumbHeight) * fraction
            val thumbBottom = thumbTop + thumbHeight

            haloPaint.shader = null
            haloPaint.color = Color.argb(66, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(x - 4.1f * density, thumbTop - 1f * density,
                x + 4.1f * density, thumbBottom + 1f * density,
                4.2f * density, 4.2f * density, haloPaint)

            if (cachedAccent != accent || bodyShader == null) {
                cachedAccent = accent
                bodyShader = android.graphics.LinearGradient(
                    x - 2.2f * density, 0f, x + 2.2f * density, 0f,
                    Color.rgb((Color.red(accent) + 255) / 2,
                        (Color.green(accent) + 255) / 2, (Color.blue(accent) + 255) / 2),
                    accent, android.graphics.Shader.TileMode.CLAMP
                )
            }
            bodyPaint.shader = bodyShader
            canvas.drawRoundRect(x - 2.2f * density, thumbTop, x + 2.2f * density, thumbBottom,
                4f * density, 4f * density, bodyPaint)
            bodyPaint.shader = null

            glossPaint.shader = null
            glossPaint.color = Color.argb(if (light) 180 else 140, 255, 255, 255)
            canvas.drawRoundRect(x - 1.2f * density, thumbTop + 4f * density,
                x - 0.3f * density, (thumbBottom - 4f * density).coerceAtLeast(thumbTop + 4f * density),
                1f * density, 1f * density, glossPaint)
        }
    }

    /**
     * Viewport-fixed frosted edge treatment for scroll containers.
     * The drawable is hosted by the scroll view's parent ViewGroup overlay, not
     * by the scrolling view itself. Its bounds are recomputed in parent-local
     * coordinates, so the edge treatment is not carried by the scroll view's
     * internal scroll transform. It does not participate in layout or touch.
     * Uses API-1 LinearGradient; no API-31 RenderEffect dependency is required.
     */
    private inner class ScrollEdgeGlassFadeDrawable(
        private val host: View,
        private val horizontal: Boolean,
        private val drawWhenWindowUnfocused: Boolean = false
    ) : android.graphics.drawable.Drawable() {
        private val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        private val surfacePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val accentPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val shinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val railPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val railAccentPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val railHaloPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val railHighlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var startSurfaceShader: android.graphics.Shader? = null
        private var endSurfaceShader: android.graphics.Shader? = null
        private var startAccentShader: android.graphics.Shader? = null
        private var endAccentShader: android.graphics.Shader? = null
        private var shineShader: android.graphics.Shader? = null
        private var cachedBounds = android.graphics.Rect()
        private var cachedLight: Boolean? = null
        private var cachedAccent = Int.MIN_VALUE
        private var cachedEdgeSize = -1f
        private var drawableAlpha = 255
        private var filter: android.graphics.ColorFilter? = null

        init {
            surfacePaint.style = android.graphics.Paint.Style.FILL
            accentPaint.style = android.graphics.Paint.Style.FILL
            shinePaint.style = android.graphics.Paint.Style.FILL
            railPaint.style = android.graphics.Paint.Style.FILL
            railAccentPaint.style = android.graphics.Paint.Style.FILL
            railHaloPaint.style = android.graphics.Paint.Style.FILL
            railHighlightPaint.style = android.graphics.Paint.Style.FILL
        }

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            super.onBoundsChange(bounds)
            rebuildShaders(bounds)
        }

        fun refreshTheme() {
            cachedLight = null
            cachedAccent = Int.MIN_VALUE
            rebuildShaders(bounds)
            invalidateSelf()
        }

        private fun rebuildShaders(bounds: android.graphics.Rect) {
            if (bounds.isEmpty) {
                startSurfaceShader = null
                endSurfaceShader = null
                startAccentShader = null
                endAccentShader = null
                shineShader = null
                return
            }
            val light = isLightTheme()
            val accent = currentAccent()
            val dimension = if (horizontal) bounds.width().toFloat() else bounds.height().toFloat()
            // A narrow frosted band keeps labels readable while softening the
            // viewport boundary. The glass rails themselves remain full-length.
            val edgeSize = minOf(18f * density, dimension * 0.18f).coerceAtLeast(1f)
            if (cachedBounds == bounds && cachedLight == light && cachedAccent == accent && cachedEdgeSize == edgeSize) return

            cachedBounds.set(bounds)
            cachedLight = light
            cachedAccent = accent
            cachedEdgeSize = edgeSize

            // API 26-30 do not expose a public real-time backdrop blur for Views.
            // This translucent pearl/blue veil emulates frosted glass without
            // capturing or blurring a bitmap on every scroll frame.
            val frostEdge = if (light) Color.rgb(229, 239, 250) else Color.rgb(190, 211, 235)
            val frostMiddle = if (light) Color.rgb(218, 231, 246) else Color.rgb(164, 190, 220)
            val edgeColor = Color.argb(if (light) 98 else 84, Color.red(frostEdge), Color.green(frostEdge), Color.blue(frostEdge))
            val midColor = Color.argb(if (light) 58 else 48, Color.red(frostMiddle), Color.green(frostMiddle), Color.blue(frostMiddle))
            val innerColor = Color.argb(if (light) 23 else 19, 225, 238, 252)
            val clearBase = Color.argb(0, Color.red(frostEdge), Color.green(frostEdge), Color.blue(frostEdge))
            val accentEdge = Color.argb(if (light) 18 else 22, Color.red(accent), Color.green(accent), Color.blue(accent))
            val accentMid = Color.argb(8, Color.red(accent), Color.green(accent), Color.blue(accent))
            val clearAccent = Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent))
            val edgeStops = floatArrayOf(0f, 0.24f, 0.66f, 1f)
            val reverseStops = floatArrayOf(0f, 0.34f, 0.76f, 1f)
            val centerX = (bounds.left + bounds.right) / 2f
            val centerY = (bounds.top + bounds.bottom) / 2f

            if (horizontal) {
                startSurfaceShader = android.graphics.LinearGradient(
                    bounds.left.toFloat(), centerY, bounds.left + edgeSize, centerY,
                    intArrayOf(edgeColor, midColor, innerColor, clearBase), edgeStops, android.graphics.Shader.TileMode.CLAMP
                )
                endSurfaceShader = android.graphics.LinearGradient(
                    bounds.right - edgeSize, centerY, bounds.right.toFloat(), centerY,
                    intArrayOf(clearBase, innerColor, midColor, edgeColor), reverseStops, android.graphics.Shader.TileMode.CLAMP
                )
                startAccentShader = android.graphics.LinearGradient(
                    bounds.left.toFloat(), centerY, bounds.left + edgeSize, centerY,
                    intArrayOf(accentEdge, accentMid, accentMid, clearAccent), edgeStops, android.graphics.Shader.TileMode.CLAMP
                )
                endAccentShader = android.graphics.LinearGradient(
                    bounds.right - edgeSize, centerY, bounds.right.toFloat(), centerY,
                    intArrayOf(clearAccent, accentMid, accentMid, accentEdge), reverseStops, android.graphics.Shader.TileMode.CLAMP
                )
                shineShader = android.graphics.LinearGradient(
                    centerX, bounds.top.toFloat(), centerX, bounds.bottom.toFloat(),
                    intArrayOf(Color.TRANSPARENT, Color.argb(if (light) 104 else 82, 245, 250, 255), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP
                )
            } else {
                startSurfaceShader = android.graphics.LinearGradient(
                    centerX, bounds.top.toFloat(), centerX, bounds.top + edgeSize,
                    intArrayOf(edgeColor, midColor, innerColor, clearBase), edgeStops, android.graphics.Shader.TileMode.CLAMP
                )
                endSurfaceShader = android.graphics.LinearGradient(
                    centerX, bounds.bottom - edgeSize, centerX, bounds.bottom.toFloat(),
                    intArrayOf(clearBase, innerColor, midColor, edgeColor), reverseStops, android.graphics.Shader.TileMode.CLAMP
                )
                startAccentShader = android.graphics.LinearGradient(
                    centerX, bounds.top.toFloat(), centerX, bounds.top + edgeSize,
                    intArrayOf(accentEdge, accentMid, accentMid, clearAccent), edgeStops, android.graphics.Shader.TileMode.CLAMP
                )
                endAccentShader = android.graphics.LinearGradient(
                    centerX, bounds.bottom - edgeSize, centerX, bounds.bottom.toFloat(),
                    intArrayOf(clearAccent, accentMid, accentMid, accentEdge), reverseStops, android.graphics.Shader.TileMode.CLAMP
                )
                shineShader = android.graphics.LinearGradient(
                    bounds.left.toFloat(), centerY, bounds.right.toFloat(), centerY,
                    intArrayOf(Color.TRANSPARENT, Color.argb(if (light) 104 else 82, 245, 250, 255), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP
                )
            }
        }

        private fun drawEdge(canvas: android.graphics.Canvas, rect: android.graphics.RectF, surface: android.graphics.Shader?, accent: android.graphics.Shader?) {
            if (surface == null) return
            surfacePaint.shader = surface
            surfacePaint.alpha = drawableAlpha
            surfacePaint.colorFilter = filter
            canvas.drawRect(rect, surfacePaint)
            surfacePaint.shader = null
            if (accent != null) {
                accentPaint.shader = accent
                accentPaint.alpha = drawableAlpha
                accentPaint.colorFilter = filter
                canvas.drawRect(rect, accentPaint)
                accentPaint.shader = null
            }
        }

        /**
         * Draws a soft, rounded multi-layer rail rather than a hard 1dp line.
         * The low-alpha outer halo + translucent body + narrow specular glint
         * imitate thick satin/frosted glass without a per-frame bitmap blur.
         */
        private fun drawFrostedRail(
            canvas: android.graphics.Canvas,
            bounds: android.graphics.Rect,
            leading: Boolean,
            horizontal: Boolean,
            light: Boolean,
            accent: Int
        ) {
            val crossSize = if (horizontal) bounds.width().toFloat() else bounds.height().toFloat()
            val longSize = if (horizontal) bounds.height().toFloat() else bounds.width().toFloat()
            if (crossSize <= 0f || longSize <= 0f) return

            val haloSize = minOf(9.5f * density, crossSize * 0.68f).coerceAtLeast(1f)
            val shellSize = minOf(6.4f * density, crossSize * 0.48f).coerceAtLeast(1f)
            val bodySize = minOf(3.5f * density, crossSize * 0.30f).coerceAtLeast(0.8f)
            val endInset = minOf(2f * density, longSize * 0.08f)
            val haloAlpha = (if (light) 31 else 25) * drawableAlpha / 255
            val shellAlpha = (if (light) 48 else 39) * drawableAlpha / 255
            val bodyAlpha = (if (light) 112 else 91) * drawableAlpha / 255
            val highlightAlpha = (if (light) 174 else 146) * drawableAlpha / 255
            val accentAlpha = (if (light) 46 else 38) * drawableAlpha / 255

            railHaloPaint.color = Color.argb(haloAlpha, 207, 224, 244)
            railPaint.color = Color.argb(bodyAlpha, 226, 239, 253)
            railHighlightPaint.color = Color.argb(highlightAlpha, 255, 255, 255)
            railAccentPaint.color = Color.argb(accentAlpha,
                Color.red(accent), Color.green(accent), Color.blue(accent))
            railHaloPaint.colorFilter = filter
            railPaint.colorFilter = filter
            railHighlightPaint.colorFilter = filter
            railAccentPaint.colorFilter = filter

            if (horizontal) {
                val edge = if (leading) bounds.left.toFloat() else bounds.right.toFloat()
                val haloStart = if (leading) edge + 0.35f * density else edge - haloSize - 0.35f * density
                val shellStart = if (leading) edge + 1.15f * density else edge - shellSize - 1.15f * density
                val bodyStart = if (leading) edge + 2.25f * density else edge - bodySize - 2.25f * density
                val halo = android.graphics.RectF(haloStart, bounds.top + endInset,
                    haloStart + haloSize, bounds.bottom - endInset)
                val shell = android.graphics.RectF(shellStart, bounds.top + endInset,
                    shellStart + shellSize, bounds.bottom - endInset)
                val body = android.graphics.RectF(bodyStart, bounds.top + endInset,
                    bodyStart + bodySize, bounds.bottom - endInset)
                canvas.drawRoundRect(halo, haloSize / 2f, haloSize / 2f, railHaloPaint)
                // A second, slightly smaller translucent layer gives the rail a diffused edge.
                railHaloPaint.color = Color.argb(shellAlpha, 224, 237, 250)
                canvas.drawRoundRect(shell, shellSize / 2f, shellSize / 2f, railHaloPaint)
                railHaloPaint.color = Color.argb(haloAlpha, 207, 224, 244)
                canvas.drawRoundRect(body, bodySize / 2f, bodySize / 2f, railPaint)

                val highlightX = if (leading) body.left + 0.65f * density else body.right - 1.15f * density
                val highlight = android.graphics.RectF(highlightX, body.top + 2f * density,
                    highlightX + 0.55f * density, body.bottom - 2f * density)
                canvas.drawRoundRect(highlight, 0.28f * density, 0.28f * density, railHighlightPaint)

                val accentX = if (leading) body.right - 0.85f * density else body.left + 0.25f * density
                val accentLine = android.graphics.RectF(accentX, body.top + 4f * density,
                    accentX + 0.55f * density, body.bottom - 4f * density)
                canvas.drawRoundRect(accentLine, 0.28f * density, 0.28f * density, railAccentPaint)
            } else {
                val edge = if (leading) bounds.top.toFloat() else bounds.bottom.toFloat()
                val haloStart = if (leading) edge + 0.35f * density else edge - haloSize - 0.35f * density
                val shellStart = if (leading) edge + 1.15f * density else edge - shellSize - 1.15f * density
                val bodyStart = if (leading) edge + 2.25f * density else edge - bodySize - 2.25f * density
                val halo = android.graphics.RectF(bounds.left + endInset, haloStart,
                    bounds.right - endInset, haloStart + haloSize)
                val shell = android.graphics.RectF(bounds.left + endInset, shellStart,
                    bounds.right - endInset, shellStart + shellSize)
                val body = android.graphics.RectF(bounds.left + endInset, bodyStart,
                    bounds.right - endInset, bodyStart + bodySize)
                canvas.drawRoundRect(halo, haloSize / 2f, haloSize / 2f, railHaloPaint)
                railHaloPaint.color = Color.argb(shellAlpha, 224, 237, 250)
                canvas.drawRoundRect(shell, shellSize / 2f, shellSize / 2f, railHaloPaint)
                railHaloPaint.color = Color.argb(haloAlpha, 207, 224, 244)
                canvas.drawRoundRect(body, bodySize / 2f, bodySize / 2f, railPaint)

                val highlightY = if (leading) body.top + 0.65f * density else body.bottom - 1.15f * density
                val highlight = android.graphics.RectF(body.left + 2f * density, highlightY,
                    body.right - 2f * density, highlightY + 0.55f * density)
                canvas.drawRoundRect(highlight, 0.28f * density, 0.28f * density, railHighlightPaint)

                val accentY = if (leading) body.bottom - 0.85f * density else body.top + 0.25f * density
                val accentLine = android.graphics.RectF(body.left + 4f * density, accentY,
                    body.right - 4f * density, accentY + 0.55f * density)
                canvas.drawRoundRect(accentLine, 0.28f * density, 0.28f * density, railAccentPaint)
            }
            railHaloPaint.colorFilter = null
            railPaint.colorFilter = null
            railHighlightPaint.colorFilter = null
            railAccentPaint.colorFilter = null
        }

        override fun draw(canvas: android.graphics.Canvas) {
            // Activity-page fades stay hidden while a modal owns window focus.
            // Color Studio's own preset strip opts in explicitly via
            // drawWhenWindowUnfocused, because its drawable belongs to the dialog tree.
            if (!drawWhenWindowUnfocused && !this@MainActivity.hasWindowFocus()) return
            val b = bounds
            if (b.isEmpty) return
            rebuildShaders(b)
            val range: Int
            val position: Int
            if (horizontal) {
                val scroll = host as? HorizontalScrollView ?: return
                range = horizontalScrollRange(scroll)
                position = scroll.scrollX
            } else {
                val scroll = host as? ScrollView ?: return
                val child = scroll.getChildAt(0)
                val contentHeight = maxOf(child?.height ?: 0, child?.measuredHeight ?: 0)
                val viewportHeight = (scroll.height - scroll.paddingTop - scroll.paddingBottom).coerceAtLeast(0)
                range = (contentHeight - viewportHeight).coerceAtLeast(0)
                position = scroll.scrollY
            }
            val save = canvas.save()
            canvas.clipRect(b)
            if (horizontal && range > 1) {
                val edgeWidth = cachedEdgeSize
                if (position > 1) {
                    val rect = android.graphics.RectF(b.left.toFloat(), b.top.toFloat(), b.left + edgeWidth, b.bottom.toFloat())
                    drawEdge(canvas, rect, startSurfaceShader, startAccentShader)
                }
                if (position < range - 1) {
                    val rect = android.graphics.RectF(b.right - edgeWidth, b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                    drawEdge(canvas, rect, endSurfaceShader, endAccentShader)
                }
                // Rounded satin-glass rails remain fixed to the viewport, and are
                // shown only while the strip has real overflow to navigate.
                // The inner gutter keeps first/last chips clear of the glass.
                drawFrostedRail(canvas, b, leading = true, horizontal = true,
                    light = cachedLight == true, accent = cachedAccent)
                drawFrostedRail(canvas, b, leading = false, horizontal = true,
                    light = cachedLight == true, accent = cachedAccent)
            } else if (!horizontal && range > 1) {
                val edgeHeight = cachedEdgeSize
                if (position > 1) {
                    val rect = android.graphics.RectF(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.top + edgeHeight)
                    drawEdge(canvas, rect, startSurfaceShader, startAccentShader)
                }
                if (position < range - 1) {
                    val rect = android.graphics.RectF(b.left.toFloat(), b.bottom - edgeHeight, b.right.toFloat(), b.bottom.toFloat())
                    drawEdge(canvas, rect, endSurfaceShader, endAccentShader)
                }
                // Only render rails when there is genuinely scrollable content.
                // Short lists (for example two Floating Profile layers) stay clean.
                drawFrostedRail(canvas, b, leading = true, horizontal = false,
                    light = cachedLight == true, accent = cachedAccent)
                drawFrostedRail(canvas, b, leading = false, horizontal = false,
                    light = cachedLight == true, accent = cachedAccent)
            }
            shinePaint.shader = null
            railPaint.colorFilter = null
            railAccentPaint.colorFilter = null
            railHaloPaint.colorFilter = null
            railHighlightPaint.colorFilter = null
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) {
            drawableAlpha = alpha.coerceIn(0, 255)
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            filter = colorFilter
            invalidateSelf()
        }

        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

        override fun getPadding(padding: android.graphics.Rect): Boolean {
            padding.set(0, 0, 0, 0)
            return false
        }
    }

    private fun installScrollEdgeGlassFade(
        host: View,
        horizontal: Boolean,
        drawWhenWindowUnfocused: Boolean = false
    ) {
        if (host.getTag(R.id.tag_scroll_edge_glass) != null) return
        if (host !is HorizontalScrollView && host !is ScrollView) return

        // A drawable hosted by ScrollView.overlay can inherit the scrolling
        // container's drawing transform on some framework/view-hierarchy paths.
        // Anchor it one level above, in the direct parent ViewGroup overlay.
        // The parent's overlay is drawn after every sibling, while its coordinate
        // system remains independent from this ScrollView's own scrollX/scrollY.
        // The app supports API 26+, and ViewGroupOverlay has existed since API 18.
        val overlayHost = host.parent as? ViewGroup ?: return
        val fade = ScrollEdgeGlassFadeDrawable(host, horizontal, drawWhenWindowUnfocused)
        var overlayAttached = false

        fun syncOverlayBounds() {
            if (!host.isAttachedToWindow || !overlayHost.isAttachedToWindow) return
            val hostLocation = IntArray(2)
            val parentLocation = IntArray(2)
            host.getLocationOnScreen(hostLocation)
            overlayHost.getLocationOnScreen(parentLocation)
            val left = hostLocation[0] - parentLocation[0]
            val top = hostLocation[1] - parentLocation[1]
            val nextLeft = left
            val nextTop = top
            val nextRight = left + host.width.coerceAtLeast(0)
            val nextBottom = top + host.height.coerceAtLeast(0)
            val current = fade.bounds
            if (current.left != nextLeft || current.top != nextTop ||
                current.right != nextRight || current.bottom != nextBottom) {
                fade.setBounds(nextLeft, nextTop, nextRight, nextBottom)
            }
            // Schedule one redraw on the next display frame instead of forcing
            // repeated immediate invalidations while a finger is dragging.
            fade.invalidateSelf()
            overlayHost.postInvalidateOnAnimation()
        }

        fun attachFadeToParentOverlay() {
            if (!overlayAttached && host.isAttachedToWindow && overlayHost.isAttachedToWindow) {
                overlayHost.overlay.add(fade)
                overlayAttached = true
            }
            syncOverlayBounds()
        }

        fun detachFadeFromParentOverlay() {
            if (overlayAttached) {
                overlayHost.overlay.remove(fade)
                overlayAttached = false
                overlayHost.invalidate()
            }
        }

        host.setTag(R.id.tag_scroll_edge_glass, fade)
        scrollEdgeFadeRefs.add(java.lang.ref.WeakReference(fade))

        val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            syncOverlayBounds()
        }
        host.addOnLayoutChangeListener(layoutListener)
        overlayHost.addOnLayoutChangeListener(layoutListener)
        host.getChildAt(0)?.addOnLayoutChangeListener(layoutListener)

        // ViewTreeObserver is additive and available on Android 11. It reports
        // both this scroll container's movement and ancestor scrolling, allowing
        // the bounds to follow the viewport when an outer page/dialog scrolls.
        var registeredObserver: android.view.ViewTreeObserver? = null
        val scrollListener = android.view.ViewTreeObserver.OnScrollChangedListener {
            syncOverlayBounds()
        }
        fun unregisterScrollObserver() {
            val previous = registeredObserver
            if (previous != null && previous.isAlive) {
                try {
                    previous.removeOnScrollChangedListener(scrollListener)
                } catch (_: IllegalStateException) {
                    // Observer may be replaced during detach.
                }
            }
            registeredObserver = null
        }
        fun registerScrollObserver() {
            val current = host.viewTreeObserver
            if (!current.isAlive || registeredObserver === current) return
            unregisterScrollObserver()
            try {
                current.addOnScrollChangedListener(scrollListener)
                registeredObserver = current
            } catch (_: IllegalStateException) {
                registeredObserver = null
            }
        }
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                attachFadeToParentOverlay()
                registerScrollObserver()
            }
            override fun onViewDetachedFromWindow(view: View) {
                unregisterScrollObserver()
                detachFadeFromParentOverlay()
            }
        })
        if (host.isAttachedToWindow) {
            attachFadeToParentOverlay()
            registerScrollObserver()
        }
        host.post { attachFadeToParentOverlay() }
    }

    private fun refreshScrollEdgeGlassFades() {
        val iterator = scrollEdgeFadeRefs.iterator()
        while (iterator.hasNext()) {
            val fade = iterator.next().get()
            if (fade == null) iterator.remove() else fade.refreshTheme()
        }
    }

    /**
     * Finds a vertical LinearLayout that can host a second, independent indicator
     * immediately after the row containing the original HorizontalScrollView.
     * No child is removed or re-parented. Unsupported hierarchies are left untouched.
     */
    private fun findHorizontalIndicatorPlacement(host: HorizontalScrollView): Pair<LinearLayout, View>? {
        var anchor: View = host
        var parent = anchor.parent
        while (parent is ViewGroup) {
            if (parent is LinearLayout && parent.orientation == LinearLayout.VERTICAL) {
                return Pair(parent, anchor)
            }
            anchor = parent
            parent = anchor.parent
        }
        return null
    }

    /**
     * Observe scroll movement without replacing a ScrollView's single native
     * OnScrollChangeListener slot. ViewTreeObserver supports additive listeners
     * and is available well below the app's minSdk (26).
     *
     * The tree callback can fire for other scrolling views in the same window,
     * so we compare this host's own coordinates before invalidating its indicator.
     * The observer is re-registered across detach/attach because Android may
     * supply a new ViewTreeObserver for a newly attached hierarchy.
     */
    private fun observeScrollForIndicator(host: View, indicator: View) {
        var lastX = host.scrollX
        var lastY = host.scrollY
        var registeredObserver: android.view.ViewTreeObserver? = null

        val scrollListener = android.view.ViewTreeObserver.OnScrollChangedListener {
            val currentX = host.scrollX
            val currentY = host.scrollY
            if (currentX != lastX || currentY != lastY) {
                lastX = currentX
                lastY = currentY
                indicator.invalidate()
                (host.getTag(R.id.tag_scroll_edge_glass) as? ScrollEdgeGlassFadeDrawable)?.invalidateSelf()
            }
        }

        fun unregisterObserver() {
            val previous = registeredObserver
            if (previous != null && previous.isAlive) {
                try {
                    previous.removeOnScrollChangedListener(scrollListener)
                } catch (_: IllegalStateException) {
                    // The decor may have replaced its observer while detaching.
                }
            }
            registeredObserver = null
        }

        fun registerObserver() {
            val current = host.viewTreeObserver
            if (!current.isAlive || registeredObserver === current) return
            unregisterObserver()
            try {
                current.addOnScrollChangedListener(scrollListener)
                registeredObserver = current
                lastX = host.scrollX
                lastY = host.scrollY
            } catch (_: IllegalStateException) {
                registeredObserver = null
            }
        }

        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                registerObserver()
            }

            override fun onViewDetachedFromWindow(view: View) {
                unregisterObserver()
            }
        })

        if (host.isAttachedToWindow) registerObserver()
    }

    private fun installBubbleScrollIndicator(host: HorizontalScrollView) {
        if (host.getTag(R.id.tag_scroll_indicator) != null) return
        val placement = findHorizontalIndicatorPlacement(host) ?: run {
            android.util.Log.w("FloatingDJ", "Skipped separate scroll indicator: no vertical LinearLayout host (viewId=${host.id})")
            return
        }
        val container = placement.first
        val anchor = placement.second
        val anchorIndex = container.indexOfChild(anchor)
        if (anchorIndex < 0) return

        val indicator = HorizontalBubbleIndicatorView(host)
        if (::liveFrequencyScroll.isInitialized && host === liveFrequencyScroll &&
            ::liveFrequencyControlsRow.isInitialized && liveFrequencyControlsRow.visibility != View.VISIBLE) {
            indicator.visibility = View.GONE
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 12.dp()).apply {
            topMargin = 2.dp()
            bottomMargin = 5.dp()
        }
        // This is a new sibling below the nav row, not a child of the scrolling view
        // and not an overlay. It owns its own measured height and cannot clip the chips.
        container.addView(indicator, anchorIndex + 1, params)
        host.setTag(R.id.tag_scroll_indicator, indicator)

        host.isHorizontalScrollBarEnabled = false
        host.isHorizontalFadingEdgeEnabled = false
        host.overScrollMode = View.OVER_SCROLL_NEVER
        observeScrollForIndicator(host, indicator)
        val refreshListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            indicator.refreshMetrics()
        }
        host.addOnLayoutChangeListener(refreshListener)
        host.getChildAt(0)?.addOnLayoutChangeListener(refreshListener)
        indicator.addOnLayoutChangeListener(refreshListener)
        indicator.post { indicator.refreshMetrics() }
        host.post { indicator.refreshMetrics() }
    }

    private fun installMainVerticalBubbleIndicator() {
        if (mainShell.getTag(R.id.tag_main_scroll_indicator) != null) return
        val indicator = VerticalBubbleIndicatorView(mainRoot)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.FILL
        )
        mainShell.addView(indicator, params)
        mainShell.setTag(R.id.tag_main_scroll_indicator, indicator)
        mainRoot.isVerticalScrollBarEnabled = false
        mainRoot.isVerticalFadingEdgeEnabled = false
        mainRoot.overScrollMode = View.OVER_SCROLL_NEVER
        observeScrollForIndicator(mainRoot, indicator)
        val refreshListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            indicator.refreshMetrics()
        }
        mainRoot.addOnLayoutChangeListener(refreshListener)
        mainRoot.getChildAt(0)?.addOnLayoutChangeListener(refreshListener)
        contentRoot.addOnLayoutChangeListener(refreshListener)
        indicator.addOnLayoutChangeListener(refreshListener)
        indicator.post { indicator.refreshMetrics() }
    }

    private fun horizontalScrollRange(scroll: HorizontalScrollView): Int {
        val child = scroll.getChildAt(0) ?: return 0
        val margins = child.layoutParams as? ViewGroup.MarginLayoutParams
        val contentWidth = maxOf(child.width, child.measuredWidth) +
            (margins?.leftMargin ?: 0) + (margins?.rightMargin ?: 0)
        val viewportWidth = (scroll.width - scroll.paddingLeft - scroll.paddingRight).coerceAtLeast(0)
        return (contentWidth - viewportWidth).coerceAtLeast(0)
    }

    /**
     * Keep framework ScrollBarDrawable disabled (Android 11 stability), while custom
     * indicators live in separate, normally measured sibling views below the rows.
     */
    private fun installScrollAffordances(root: View, mainPageAffordances: Boolean = false) {
        when (root) {
            is HorizontalScrollView -> {
                root.isHorizontalScrollBarEnabled = false
                root.isHorizontalFadingEdgeEnabled = false
                root.overScrollMode = View.OVER_SCROLL_NEVER
                if (mainPageAffordances) {
                    installScrollEdgeGlassFade(root, horizontal = true)
                    installBubbleScrollIndicator(root)
                }
            }
            is ScrollView -> {
                root.isVerticalScrollBarEnabled = false
                root.isVerticalFadingEdgeEnabled = false
                root.overScrollMode = View.OVER_SCROLL_NEVER
                if (mainPageAffordances) {
                    installScrollEdgeGlassFade(root, horizontal = false)
                }
            }
        }
        if (root is ViewGroup) {
            val children = (0 until root.childCount).map { root.getChildAt(it) }
            children.forEach { installScrollAffordances(it, mainPageAffordances) }
        }
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
        heightFraction: Float = 0.92f,
    ): AlertDialog {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable(isLightTheme(), currentAccent())
            setPadding(15.dp(), 11.dp(), 15.dp(), 12.dp())
            clipToOutline = true
            clipChildren = true
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2.dp(), 1.dp(), 2.dp(), 8.dp())
        }
        val titleView = sectionTitle(title).apply {
            textSize = 14f
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            contentDescription = if (appLanguage == "id") "Judul jendela" else "Dialog title"
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, 40.dp(), 1f))
        val closeButton = glassButton("×").apply {
            textSize = 21f
            includeFontPadding = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            contentDescription = if (appLanguage == "id") "Tutup" else "Close"
        }
        header.addView(closeButton, LinearLayout.LayoutParams(36.dp(), 36.dp()).apply { leftMargin = 8.dp() })
        shell.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val headerDivider = View(this).apply {
            background = studioDividerDrawable()
        }
        shell.addView(headerDivider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2.dp()).apply {
            leftMargin = 4.dp(); rightMargin = 4.dp(); topMargin = 2.dp()
        })

        val bodyFrame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            setPadding(10.dp(), 14.dp(), 10.dp(), 14.dp())
        }
        bodyFrame.addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        shell.addView(bodyFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // Keep the modal footer visually clean: no bright gradient beam under
        // the scrolling body. The footer remains fixed and separated by spacing only.
        shell.addView(footer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 12.dp()
        })

        val dialog = AlertDialog.Builder(this).setView(shell).create()
        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            sizeStudioDialog(dialog, widthFraction, heightFraction)
            styleTextTree(shell, isLightTheme())
            styleDynamicTree(shell, isLightTheme(), currentAccent())
            applyDialogTextScale(dialog)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.decorView?.post { installScrollAffordances(shell) }
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
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true; background = null }
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
            styleTextTree(window.decorView as? ViewGroup ?: return@post, isLightTheme())
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
                val group = decor as? ViewGroup ?: return@post
                installScrollAffordances(group)
                styleTextTree(group, isLightTheme())
                styleDynamicTree(group, isLightTheme(), currentAccent())
            }
        }
    }

    private fun showGeneralSettings() {
        val light = isLightTheme()
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        val panel = glassPanel().apply {
            orientation = LinearLayout.VERTICAL
            background = null
            setPadding(16.dp(), 14.dp(), 16.dp(), 18.dp())
        }
        fun addSettingsGroupTitle(value: String) {
            val heading = sectionTitle(value).apply {
                textSize = 10.5f
                includeFontPadding = false
                letterSpacing = 0.065f
                setPadding(2.dp(), 14.dp(), 2.dp(), 7.dp())
            }
            panel.addView(heading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            panel.addView(View(this).apply { background = studioDividerDrawable() },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2.dp()).apply {
                    leftMargin = 2.dp(); rightMargin = 2.dp(); bottomMargin = 10.dp()
                })
        }
        val title = sectionTitle(if (appLanguage == "id") "PENGATURAN UMUM" else "GENERAL SETTINGS").apply {
            textSize = 15f
            includeFontPadding = false
        }
        panel.addView(title)
        panel.addView(subtitle(if (appLanguage == "id") "Atur bahasa, skala tampilan, dan info aplikasi." else "Configure language, interface scale, and app information."),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 6.dp() })

        addSettingsGroupTitle(if (appLanguage == "id") "BAHASA / LANGUAGE" else "LANGUAGE")
        val languageLabels = listOf("English", "Bahasa Indonesia")
        var selectedLanguageIndex = if (appLanguage == "id") 1 else 0
        val languageDropdown = glassButton(languageLabels[selectedLanguageIndex] + "    ⌄").apply {
            tag = "floatingdj:content-surface"
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
                    setPadding(13.dp(), 12.dp(), 13.dp(), 12.dp())
                    applyPillStyle(this, index == selectedLanguageIndex, light, currentAccent())
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
        panel.addView(languageDropdown, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dp()).apply {
            topMargin = 2.dp(); bottomMargin = 7.dp()
        })

        addSettingsGroupTitle(if (appLanguage == "id") "UKURAN UI / TEKS" else "UI / TEXT SCALE")
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
        panel.addView(scaleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 2.dp() })
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
        panel.addView(scaleSeek, matchWrap().apply { topMargin = 9.dp(); bottomMargin = 5.dp() })
        panel.addView(subtitle(if (appLanguage == "id") "Batas 85–125% biar tampilan tetap nyaman dibaca. Perubahan diterapkan setelah disimpan." else "Bounded to 85–125% for comfortable reading. Changes apply after saving."),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 2.dp(); bottomMargin = 4.dp() })

        addSettingsGroupTitle(if (appLanguage == "id") "INFO APLIKASI" else "ABOUT THIS APP")
        panel.addView(subtitle(if (appLanguage == "id") "Profil pembuat, email, dan GitHub." else "Developer information, email, and GitHub."),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            })
        val aboutButton = glassButton(if (appLanguage == "id") "BUKA INFO APLIKASI" else "OPEN APP INFORMATION")
        aboutButton.setOnClickListener { showAboutDialog() }
        panel.addView(aboutButton, matchWrap().apply { bottomMargin = 5.dp() })

        addSettingsGroupTitle(if (appLanguage == "id") "CADANGAN KONFIGURASI" else "CONFIGURATION BACKUP")
        panel.addView(subtitle(if (appLanguage == "id") "Ekspor menyimpan pengaturan dan seluruh profil. Impor akan menampilkan ringkasan sebelum diterapkan." else "Export stores settings and all saved profiles. Import shows a review summary before applying changes."),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 7.dp()
            })
        val backupRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val export = glassButton(if (appLanguage == "id") "EKSPOR" else "EXPORT")
        val import = glassButton(if (appLanguage == "id") "IMPOR" else "IMPORT")
        backupRow.addView(export, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        backupRow.addView(import, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        export.setOnClickListener { launchConfigExport() }
        import.setOnClickListener { launchConfigImport() }
        panel.addView(backupRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = 9.dp(); bottomMargin = 5.dp()
        })
        scroll.addView(panel)

        scroll.background = null
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cancelSettings = glassButton(if (appLanguage == "id") "TUTUP" else "CLOSE")
        val saveSettingsButton = glassButton(if (appLanguage == "id") "SIMPAN" else "SAVE")
        footer.addView(cancelSettings, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 6.dp() })
        footer.addView(saveSettingsButton, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 6.dp() })
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
            isVerticalScrollBarEnabled = false
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

    private fun showExportProfilePicker() {
        val savedProfiles = readColorProfiles()
        val options = mutableListOf<ColorProfileEntry>()
        options.add(currentProfileSnapshot("Current Configuration"))
        options.addAll(savedProfiles)
        var selectedIndex = 0
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(subtitle(if (appLanguage == "id") "Pilih konfigurasi aktif atau preset yang mau diekspor." else "Choose the current configuration or a saved preset to export."))
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8.dp(), 0, 0) }
        val rows = mutableListOf<TextView>()
        fun refreshRows() {
            rows.forEachIndexed { index, row ->
                val selected = index == selectedIndex
                row.text = (if (selected) "◉  " else "○  ") + options[index].name
                applyPillStyle(row, selected)
            }
        }
        options.forEachIndexed { index, option ->
            val row = glassButton(option.name)
            row.setOnClickListener { selectedIndex = index; refreshRows() }
            rows.add(row)
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 44.dp()).apply { bottomMargin = 5.dp() })
        }
        refreshRows()
        body.addView(list)
        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cancel = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        val export = glassButton(if (appLanguage == "id") "EKSPOR" else "EXPORT")
        footer.addView(cancel, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(export, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(if (appLanguage == "id") "PILIH PROFIL EKSPOR" else "CHOOSE PROFILE TO EXPORT", body, footer, 0.92f, 0.70f)
        cancel.setOnClickListener { dialog.dismiss() }
        export.setOnClickListener {
            pendingProfileExport = options[selectedIndex]
            dialog.dismiss()
            exportColorProfile(options[selectedIndex])
        }
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
            .put("opacity", profile.opacityPercent).put("shadow_opacity", profile.shadowOpacityPercent)
            .put("glow", profile.glowPercent)
            .put("response_speed", profile.responsePercent).put("corner_radius", profile.cornerRadiusDp)
            .put("vertical_mode", profile.verticalMode)
            .put("single_audio_mode", profile.singleAudioMode)
            .put("bass_min_hz", profile.bassMinHz)
            .put("bass_max_hz", profile.bassMaxHz)
            .put("horizontal_alignment", profile.horizontalAlignment)
            .put("mirror_mode", profile.mirrorMode)
            .put("mirror_enabled", profile.mirrorMode != "OFF") // Legacy compatibility.
            .put("position_x", profile.positionXdp)
            .put("position_y", profile.positionYdp)
            .put("position_mode", profile.positionMode)
            .put("rotation_degrees", profile.rotationDegrees)
            .put("floating_overlay_enabled", profile.floatingOverlayEnabled)
        return root
    }

    private fun profileFromJson(raw: String): ColorProfileEntry {
        require(raw.length <= 128_000) { "Profile file is too large" }
        val root = JSONObject(raw)
        require(root.optString("app") == "FloatingDJVisualizerProfile") { "This is not a Floating DJ profile" }
        require(root.optInt("schema", -1) in 1..PROFILE_SCHEMA_VERSION) { "Unsupported profile version" }
        val name = root.optString("name").trim().take(28)
        require(name.isNotBlank()) { "Profile name is missing" }
        val colorsJson = root.optJSONArray("colors") ?: throw IllegalArgumentException("Profile colors are missing")
        val colors = mutableListOf<Int>()
        for (i in 0 until colorsJson.length()) {
            val rawColor = colorsJson.optString(i)
            val color = try { Color.parseColor(rawColor) } catch (_: Exception) { null }
            if (color != null) colors.add(color)
        }
        require(colors.size in 2..5) { "A profile must contain two to five valid color stops" }
        while (colors.size < 5) colors.add(colors.last())
        val effect = root.optString("effect", EFFECT_GLOSSY)
            .takeIf { it in listOf(EFFECT_CLEAN, EFFECT_GLOSSY, EFFECT_NEON, EFFECT_GLASS) } ?: EFFECT_GLOSSY
        return ColorProfileEntry(
            name = name,
            colors = colors,
            effect = effect,
            widthDp = root.optInt("width", 320).coerceIn(0, 4096),
            heightDp = root.optInt("height", 360).coerceIn(0, 4096),
            barCount = root.optInt("bar_count", 15).coerceIn(1, MAX_BAR_COUNT),
            barWidthDp = root.optInt("bar_width", 20).coerceIn(0, 2048),
            gapDp = root.optInt("bar_gap", 3).coerceIn(0, 512),
            scalePercent = root.optInt("scale", 100).coerceIn(0, 500),
            opacityPercent = root.optInt("opacity", 100).coerceIn(0, 100),
            shadowOpacityPercent = root.optInt("shadow_opacity", 100).coerceIn(0, 100),
            glowPercent = root.optInt("glow", if (effect == EFFECT_NEON) 100 else 50).coerceIn(0, 100),
            responsePercent = root.optInt("response_speed", 82).coerceIn(0, 100),
            cornerRadiusDp = root.optInt("corner_radius", 14).coerceIn(0, 512),
            verticalMode = root.optBoolean("vertical_mode", false),
            singleAudioMode = root.optString("single_audio_mode", "MUSIC").takeIf { it == "DJ" || it == "MUSIC" } ?: "MUSIC",
            bassMinHz = root.optInt("bass_min_hz", 20).coerceIn(0, 20000),
            bassMaxHz = root.optInt("bass_max_hz", 125).coerceIn(0, 20000),
            horizontalAlignment = root.optString("horizontal_alignment", "LEFT").takeIf { it in setOf("LEFT", "CENTER", "RIGHT") } ?: "LEFT",
            mirrorMode = root.optString("mirror_mode", if (root.optBoolean("mirror_enabled", false)) "HORIZONTAL" else "OFF")
                .takeIf { it in setOf("OFF", "VERTICAL", "HORIZONTAL") } ?: "OFF",
            positionXdp = root.optInt("position_x", 0).coerceIn(0, 100000),
            positionYdp = root.optInt("position_y", 0).coerceIn(0, 100000),
            positionMode = root.optString("position_mode", "STATIC").takeIf { it == "STATIC" || it == "DYNAMIC" } ?: "STATIC",
            rotationDegrees = root.optInt("rotation_degrees", 0).coerceIn(0, 359),
            floatingOverlayEnabled = root.optBoolean("floating_overlay_enabled", false)
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
            val raw = readDocumentTextLimited(uri, 128_000)
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
                    append("\nSkala / opasitas bar: ").append(profile.scalePercent).append("% / ").append(profile.opacityPercent).append("%")
                    append("\nOpasitas shadow bar: ").append(profile.shadowOpacityPercent).append("%")
                    append("\nGlow / respons: ").append(profile.glowPercent).append("% / ").append(profile.responsePercent).append("%")
                    append("\nRadius sudut: ").append(profile.cornerRadiusDp).append(" dp")
                    append("\nMode: ").append(if (profile.verticalMode) "Single" else "Horizontal")
                    append("\nFrekuensi: ").append(profile.bassMinHz).append("–").append(profile.bassMaxHz).append(" Hz")
                    append("\nArah / Mirror: ").append(profile.horizontalAlignment).append(" / ").append(profile.mirrorMode)
                    append("\nFloating diminta aktif: ").append(if (profile.floatingOverlayEnabled) "Ya" else "Tidak")
                    append("\nPosisi X/Y: ").append(profile.positionXdp).append(" / ").append(profile.positionYdp).append(" dp · ").append(profile.rotationDegrees).append("°")
                    if (existing) append("\n\nNama profil ini sudah ada. Versi lama akan diganti.")
                } else {
                    append("Profile: ").append(profile.name)
                    append("\nColor stops: 5")
                    append("\nEffect: ").append(profile.effect)
                    append("\nSize: ").append(profile.widthDp).append(" × ").append(profile.heightDp).append(" dp")
                    append("\nBars: ").append(profile.barCount)
                    append("\nThickness / gap: ").append(profile.barWidthDp).append(" / ").append(profile.gapDp).append(" dp")
                    append("\nScale / bar opacity: ").append(profile.scalePercent).append("% / ").append(profile.opacityPercent).append("%")
                    append("\nShadow bar opacity: ").append(profile.shadowOpacityPercent).append("%")
                    append("\nGlow / response: ").append(profile.glowPercent).append("% / ").append(profile.responsePercent).append("%")
                    append("\nCorner radius: ").append(profile.cornerRadiusDp).append(" dp")
                    append("\nMode: ").append(if (profile.verticalMode) "Single" else "Horizontal")
                    append("\nFrequency: ").append(profile.bassMinHz).append("–").append(profile.bassMaxHz).append(" Hz")
                    append("\nDirection / Mirror: ").append(profile.horizontalAlignment).append(" / ").append(profile.mirrorMode)
                    append("\nFloating overlay requested: ").append(if (profile.floatingOverlayEnabled) "On" else "Off")
                    append("\nPosition X/Y: ").append(profile.positionXdp).append(" / ").append(profile.positionYdp).append(" dp · ").append(profile.rotationDegrees).append("°")
                    if (existing) append("\n\nA profile with this name already exists and will be replaced.")
                }
            }
            val reviewScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true; background = null }
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
                Toast.makeText(this, if (appLanguage == "id") "Profil berhasil diimpor" else "Profile imported", Toast.LENGTH_SHORT).show()
                reviewDialog.dismiss()
            }
        } catch (error: Exception) {
            Toast.makeText(this, if (appLanguage == "id") "Impor profil gagal: ${error.message ?: "file tidak valid"}" else "Profile import failed: ${error.message ?: "invalid file"}", Toast.LENGTH_SHORT).show()
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
            val raw = readDocumentTextLimited(uri, 1_500_000)
            val root = JSONObject(raw)
            require(root.optString("app") == "FloatingDJVisualizer") { "This file is not a Floating DJ backup" }
            require(root.optInt("schema", -1) in 1..CONFIG_SCHEMA_VERSION) { "Unsupported backup version" }
            val values = root.optJSONObject("settings") ?: throw IllegalArgumentException("Backup settings are missing")
            val imported = JSONObject()
            val keys = values.keys()
            val stringKeys = setOf(
                "single_audio_mode", "horizontal_alignment", "horizontal_mirror_mode", "position_mode",
                "position_unit", "theme_mode", "bar_effect", "app_language", "colors", "color_profiles",
                "active_color_profile", "profile_base_name", "last_managed_color_profile",
                "floating_profiles", "selected_floating_profile_id",
                "floating_scene_presets", "active_floating_scene_id"
            )
            val intKeys = setOf(
                "width", "height", "bar_count", "bar_width", "bar_gap", "scale", "opacity", "shadow_opacity", "glow",
                "response_speed", "corner_radius", "bass_min_hz", "bass_max_hz", "position_x", "position_y",
                "rotation_degrees", "theme_accent", "theme_surface_color", "theme_edge_color", "theme_text_color",
                "theme_text_color_glass", "theme_text_color_custom", "theme_text_color_system", "theme_text_color_system_light", "theme_text_color_system_dark", "theme_text_color_light",
                "ui_scale_percent"
            )
            val booleanKeys = setOf("vertical", "horizontal_mirror", "position_initialized", "profile_dirty")
            val floatKeys = setOf("position_step")
            while (keys.hasNext()) {
                val key = keys.next()
                // Runtime permissions, service state and unknown future keys are informational only.
                if (key in setOf("floating_overlay_enabled", "backup_capture", "service_active", "service_paused")) continue
                val value = values.opt(key)
                val valid = when (key) {
                    in stringKeys -> value is String
                    in intKeys -> value is Int || (value is Long && value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                    in booleanKeys -> value is Boolean
                    in floatKeys -> value is Number && (value as Number).toDouble().isFinite()
                    else -> false
                }
                if (!valid || value == JSONObject.NULL) continue
                // SharedPreferences getInt() must never encounter a Long value for an int key.
                if (key in intKeys) imported.put(key, (value as Number).toInt())
                else if (key in floatKeys) imported.put(key, (value as Number).toDouble())
                else imported.put(key, value)
            }
            // Old backups may not have this marker. Coordinates in a backup are intentional.
            if (values.has("position_x") && values.has("position_y") && !imported.has("position_initialized")) {
                imported.put("position_initialized", true)
            }
            if (imported.has("floating_profiles")) {
                val floatingRaw = imported.optString("floating_profiles", "[]")
                require(floatingRaw.length <= 250_000) { "Floating profile data is too large" }
                JSONArray(floatingRaw)
            }
            if (imported.has("color_profiles")) {
                val colorRaw = imported.optString("color_profiles", "[]")
                require(colorRaw.length <= 250_000) { "Color profile data is too large" }
                JSONArray(colorRaw)
            }
            if (imported.has("floating_scene_presets")) {
                val sceneRaw = imported.optString("floating_scene_presets", "[]")
                require(sceneRaw.length <= 900_000) { "Floating scene backup is too large" }
                val sceneArray = JSONArray(sceneRaw)
                require(sceneArray.length() <= MAX_FLOATING_SCENES) { "Too many floating scenes in backup" }
                val sceneIds = mutableSetOf<String>()
                for (sceneIndex in 0 until sceneArray.length()) {
                    val scene = sceneArray.optJSONObject(sceneIndex)
                        ?: throw IllegalArgumentException("Invalid floating scene entry")
                    require(scene.optString("format") == "com.floatingdj.floating-scene") { "Unsupported floating scene format" }
                    require(scene.optInt("schema_version", 0) in 1..FLOATING_SCENE_SCHEMA_VERSION) { "Unsupported floating scene version" }
                    val sceneId = scene.optString("id").trim()
                    require(sceneId.isNotEmpty() && sceneIds.add(sceneId)) { "Floating scene IDs must be unique" }
                    require(scene.optString("name").isNotBlank()) { "Floating scene name is missing" }
                    val layers = scene.optJSONArray("layers") ?: throw IllegalArgumentException("Floating scene has no layers")
                    require(layers.length() in 1..12) { "Each floating scene must contain 1–12 layers" }
                    val layerIds = mutableSetOf<String>()
                    for (layerIndex in 0 until layers.length()) {
                        val layer = layers.optJSONObject(layerIndex)
                            ?: throw IllegalArgumentException("Invalid layer in floating scene")
                        val layerId = layer.optString("id").trim()
                        require(layerId.isNotEmpty() && layerIds.add(layerId)) { "Layer IDs must be unique within a scene" }
                        val layerColors = layer.optJSONArray("colors")
                            ?: throw IllegalArgumentException("A floating layer has no color palette")
                        require(layerColors.length() in 2..5) { "A floating layer must contain 2–5 colors" }
                        for (colorIndex in 0 until layerColors.length()) {
                            val colorText = layerColors.optString(colorIndex)
                            require(colorText.isNotBlank()) { "Empty color in floating scene" }
                            Color.parseColor(colorText)
                        }
                    }
                    val selectedId = scene.optString("selected_profile_id", "root")
                    require(layerIds.contains(selectedId) || selectedId == "root") { "Selected layer is missing from floating scene" }
                }
                val activeSceneId = imported.optString("active_floating_scene_id", "")
                if (activeSceneId.isNotBlank() && !sceneIds.contains(activeSceneId)) {
                    imported.remove("active_floating_scene_id")
                }
                imported.put("floating_scene_presets", sceneArray.toString())
            }
            val floatingRequested = values.optBoolean("floating_overlay_enabled", false)
            val backupRequested = values.optBoolean("backup_capture", false)
            val floatingCount = try { JSONArray(imported.optString("floating_profiles", "[]")).length() } catch (_: Exception) { 0 }
            val scenePresetCount = try { JSONArray(imported.optString("floating_scene_presets", "[]")).length() } catch (_: Exception) { 0 }
            val summary = buildString {
                if (appLanguage == "id") {
                    append("Pengaturan dan profil warna: ").append(if (imported.has("color_profiles")) "ikut" else "tidak ada")
                    append("\nFloating Profile Layers: ").append(floatingCount)
                    append("\nFloating Scene Manager presets: ").append(scenePresetCount)
                    append("\nFloating Bar diminta aktif: ").append(if (floatingRequested) "Ya" else "Tidak")
                    append("\nBackup audio diminta aktif: ").append(if (backupRequested) "Ya" else "Tidak")
                    append("\nTema: ").append(imported.optString("theme_mode", "default"))
                    append("\nEfek bar: ").append(imported.optString("bar_effect", "default"))
                    append("\nUkuran: ").append(imported.optInt("width", 320)).append(" × ").append(imported.optInt("height", 360)).append(" dp")
                    append("\nJumlah / ketebalan / jarak bar: ").append(imported.optInt("bar_count", 15)).append(" / ").append(imported.optInt("bar_width", 20)).append(" / ").append(imported.optInt("bar_gap", 3)).append(" dp")
                    append("\nSkala / opasitas bar / glow: ").append(imported.optInt("scale", 100)).append("% / ").append(imported.optInt("opacity", 100)).append("% / ").append(imported.optInt("glow", if (imported.optString("bar_effect", EFFECT_GLOSSY) == EFFECT_NEON) 100 else 50)).append("%")
                    append("\nOpasitas shadow bar: ").append(imported.optInt("shadow_opacity", 100)).append("%")
                    append("\nRespons: ").append(imported.optInt("response_speed", 82)).append("%")
                    append("\nPosisi X/Y: ").append(imported.optInt("position_x", 0)).append(" / ").append(imported.optInt("position_y", 0)).append(" dp")
                    append("\nRotasi: ").append(imported.optInt("rotation_degrees", 0)).append("°")
                    append("\n\nIzin Android tidak ikut dipindahkan. Izin overlay dan MediaProjection tetap harus disetujui di perangkat ini. Status aktif dari perangkat asal hanya informasi.")
                } else {
                    append("Settings and saved color profiles: ").append(if (imported.has("color_profiles")) "included" else "not included")
                    append("\nFloating Profile Layers: ").append(floatingCount)
                    append("\nFloating Scene Manager presets: ").append(scenePresetCount)
                    append("\nFloating Bar requested: ").append(if (floatingRequested) "On" else "Off")
                    append("\nBackup capture requested: ").append(if (backupRequested) "On" else "Off")
                    append("\nTheme: ").append(imported.optString("theme_mode", "default"))
                    append("\nBar effect: ").append(imported.optString("bar_effect", "default"))
                    append("\nSize: ").append(imported.optInt("width", 320)).append(" × ").append(imported.optInt("height", 360)).append(" dp")
                    append("\nBar count / thickness / gap: ").append(imported.optInt("bar_count", 15)).append(" / ").append(imported.optInt("bar_width", 20)).append(" / ").append(imported.optInt("bar_gap", 3)).append(" dp")
                    append("\nScale / bar opacity / glow: ").append(imported.optInt("scale", 100)).append("% / ").append(imported.optInt("opacity", 100)).append("% / ").append(imported.optInt("glow", if (imported.optString("bar_effect", EFFECT_GLOSSY) == EFFECT_NEON) 100 else 50)).append("%")
                    append("\nShadow bar opacity: ").append(imported.optInt("shadow_opacity", 100)).append("%")
                    append("\nResponse: ").append(imported.optInt("response_speed", 82)).append("%")
                    append("\nPosition X/Y: ").append(imported.optInt("position_x", 0)).append(" / ").append(imported.optInt("position_y", 0)).append(" dp")
                    append("\nRotation: ").append(imported.optInt("rotation_degrees", 0)).append("°")
                    append("\n\nAndroid permissions are not transferred by a backup. Overlay and MediaProjection must be approved on this device. Source service state is informational only.")
                }
            }
            val reviewScroll = ScrollView(this).apply {
                isFillViewport = true
                isVerticalScrollBarEnabled = false
                background = null
            }
            val reviewBody = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(9.dp(), 11.dp(), 9.dp(), 12.dp())
                addView(subtitle(summary).apply { textSize = 11f })
            }
            reviewScroll.addView(reviewBody)
            val reviewFooter = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val reviewCancel = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
            val reviewImport = glassButton(if (appLanguage == "id") "IMPOR" else "IMPORT")
            reviewFooter.addView(reviewCancel, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
            reviewFooter.addView(reviewImport, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
            val review = showGlassStudioDialog(
                if (appLanguage == "id") "TINJAU BACKUP" else "REVIEW BACKUP",
                reviewScroll, reviewFooter, 0.94f, 0.88f
            )
            reviewCancel.setOnClickListener { review.dismiss() }
            reviewImport.setOnClickListener {
                try {
                    commitConfigurationBackup(imported, floatingRequested)
                    Toast.makeText(this, if (appLanguage == "id") "Backup berhasil diterapkan" else "Backup imported", Toast.LENGTH_SHORT).show()
                    review.dismiss()
                } catch (error: Exception) {
                    Toast.makeText(this, if (appLanguage == "id") "Impor backup gagal: ${error.message ?: "file tidak valid"}" else "Import failed: ${error.message ?: "invalid file"}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (error: Exception) {
            Toast.makeText(this, if (appLanguage == "id") "Impor backup gagal: ${error.message ?: "file tidak valid"}" else "Backup import failed: ${error.message ?: "invalid file"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun commitConfigurationBackup(imported: JSONObject, floatingRequested: Boolean) {
        try { stopService(Intent(this, FloatingVisualizerService::class.java)) } catch (_: Exception) {}
        floatingServiceStarted = false
        servicePaused = false
        backupCaptureEnabled = false
        floatingOverlayEnabled = false
        val editor = prefs.edit()
        val keys = imported.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = imported.opt(key)) {
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
        if (prefs.getBoolean("position_initialized", false)) {
            positionInitialized = true
            positionXdp = prefs.getInt("position_x", positionXdp).coerceAtLeast(0)
            positionYdp = prefs.getInt("position_y", positionYdp).coerceAtLeast(0)
        }
        initializeOrClampPosition()
        ensureFloatingProfileLibrary()
        applyTheme(animated = true)
        refreshColorSwatches()
        updateModeButtons(); updateEffectButtons(); updateSingleModeButtons()
        updateSingleModeVisibility(); updateHorizontalFlowButtons()
        buildSpectrumBars(); buildSingleModePanel(); buildFloatingControls(); buildFloatingProfileList()
        refreshFloatingSceneManagerStatus()
        backupSwitch.setOnCheckedChangeListener(null)
        backupSwitch.isChecked = false
        backupSwitch.setOnCheckedChangeListener { _, checked -> if (checked) enableBackupCapture() else disableBackupCapture() }
        floatingOverlaySwitch.setOnCheckedChangeListener(null)
        floatingOverlaySwitch.isChecked = false
        floatingOverlaySwitch.setOnCheckedChangeListener { _, checked -> if (checked) enableFloatingOverlay() else disableFloatingOverlay() }
        val wantsOverlay = floatingRequested
        if (wantsOverlay && !Settings.canDrawOverlays(this)) {
            showGlassConfirmDialog(
                if (appLanguage == "id") "IZIN FLOATING DIPERLUKAN" else "OVERLAY PERMISSION REQUIRED",
                if (appLanguage == "id") "Backup meminta Floating Bar aktif. Android perlu izin di perangkat ini." else "The backup requests Floating Bar. Android needs permission on this device.",
                if (appLanguage == "id") "NANTI" else "NOT NOW",
                if (appLanguage == "id") "BUKA PENGATURAN" else "OPEN SETTINGS",
                0.92f, 0.50f
            ) { enableFloatingOverlay() }
        } else if (wantsOverlay) {
            floatingOverlayEnabled = true
            prefs.edit().putBoolean("floating_overlay_enabled", true).apply()
            setFloatingOverlaySwitch(true)
        }
        updateFloatingService()
    }

    private fun simpleSeekListener(onValue: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) onValue(progress) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun showThemeColorPicker(title: String, initialColor: Int, onApply: (Int) -> Unit) {
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = true; background = null }
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
            isVerticalScrollBarEnabled = false
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

        panel.addView(sectionTitle(if (appLanguage == "id") "WARNA TEKS PER TEMA" else "TEXT COLOR PER THEME").apply {
            setPadding(0, 17.dp(), 0, 7.dp())
        })
        panel.addView(subtitle(if (appLanguage == "id") "Setiap tema punya warna teks sendiri. Mengubah satu tema tidak akan mengubah tema lainnya." else "Each theme stores its own text color. Editing one theme never changes another.").apply {
            setPadding(0, 0, 0, 7.dp())
        })
        var textTargetMode = themeMode
        var selectedTextColor = readThemeTextColor(textTargetMode)
        val textThemeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val themeTargets = listOf(
            THEME_GLASS to "GLOSSY",
            THEME_CUSTOM to "CUSTOM",
            THEME_SYSTEM to "SYSTEM",
            THEME_DARK to "DARK",
            THEME_LIGHT to "LIGHT"
        )
        val textTargetButtons = mutableListOf<TextView>()
        val textTargetPreview = View(this)
        val textTargetLabel = subtitle("")
        fun refreshTextTargetUi() {
            for (index in 0 until textThemeRow.childCount) {
                val button = textThemeRow.getChildAt(index) as? TextView ?: continue
                val active = themeTargets[index].first == textTargetMode
                applyPillStyle(button, active)
            }
            selectedTextColor = readThemeTextColor(textTargetMode)
            textTargetLabel.text = "${themeTargets.first { it.first == textTargetMode }.second} · #${String.format(java.util.Locale.US, "%06X", 0xFFFFFF and selectedTextColor)}"
            textTargetPreview.background = GradientDrawable().apply {
                setColor(selectedTextColor)
                cornerRadius = 12.dp().toFloat()
                setStroke(1.dp(), 0x66FFFFFF)
            }
        }
        themeTargets.forEach { target ->
            val button = glassButton(target.second).apply {
                textSize = 8f
                setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
                setOnClickListener {
                    textTargetMode = target.first
                    refreshTextTargetUi()
                }
            }
            textTargetButtons.add(button)
            textThemeRow.addView(button, LinearLayout.LayoutParams(0, 40.dp(), 1f).apply {
                setMargins(2.dp(), 0, 2.dp(), 0)
            })
        }
        panel.addView(textThemeRow)
        val textColorActionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val pickTextColor = glassButton(if (appLanguage == "id") "PILIH WARNA TEKS" else "PICK TEXT COLOR")
        textColorActionRow.addView(pickTextColor, LinearLayout.LayoutParams(0, 42.dp(), 1f).apply { marginEnd = 7.dp() })
        textColorActionRow.addView(textTargetPreview, LinearLayout.LayoutParams(52.dp(), 30.dp()))
        panel.addView(textColorActionRow, matchWrap().apply { topMargin = 8.dp() })
        panel.addView(textTargetLabel.apply { setPadding(2.dp(), 5.dp(), 2.dp(), 0) })
        pickTextColor.setOnClickListener {
            showThemeColorPicker(if (appLanguage == "id") "WARNA TEKS · ${themeTargets.first { it.first == textTargetMode }.second}" else "TEXT COLOR · ${themeTargets.first { it.first == textTargetMode }.second}", selectedTextColor) { color ->
                selectedTextColor = color
                prefs.edit().putInt(themeTextPreferenceKey(textTargetMode), color).apply()
                if (themeMode == textTargetMode) {
                    themeTextColor = color
                    prefs.edit().putInt("theme_text_color", color).apply()
                    applyTheme(animated = true)
                    styleTextTree(panel, isLightTheme())
                    textTargetLabel.setTextColor(themedTextColor(textTargetLabel, isLightTheme()))
                }
                refreshTextTargetUi()
            }
        }
        refreshTextTargetUi()

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
            // Keep the current reference (Glossy / Custom / System / Dark / Light).
            // Text colors for all five themes are stored independently as soon
            // as each target is edited in this Color Studio.
            saveSettings()
            applyTheme(animated = true)
            updateModeButtons()
            updateEffectButtons()
            updateSingleModeButtons()
            refreshFloatingNotificationTheme()
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
            val distance = sqrt(dx.toDouble() * dx.toDouble() + dy.toDouble() * dy)
                .toFloat()
                .coerceAtMost(radius)
            hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0 + 360.0) % 360.0).toFloat()
            saturation = (distance / radius.coerceAtLeast(1f)).coerceIn(0f, 1f)
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
        clearSelectedAppearancePreset()
        scanDisplayBounds()
        overlayWidthDp = minOf(320, (screenWidthDp * 0.94f).toInt().coerceAtLeast(120))
        overlayHeightDp = minOf(360, (screenHeightDp * 0.90f).toInt().coerceAtLeast(80))
        barCount = 15
        barWidthDp = 20
        barGapDp = 3
        scalePercent = 100
        opacityPercent = 100
        shadowOpacityPercent = 100
        glowPercent = defaultGlowForEffect(barEffect)
        responsePercent = if (barEffect == EFFECT_NEON) 90 else 82
        cornerRadiusDp = 14
        clampPosition()
        updateEffectButtons()
        syncSliderValue("HORIZONTAL WIDTH", overlayWidthDp)
        syncSliderValue("BAR HEIGHT / LENGTH", overlayHeightDp)
        syncSliderValue("HORIZONTAL BAR COUNT", barCount)
        syncSliderValue("BAR THICKNESS", barWidthDp)
        syncSliderValue("BAR GAP", barGapDp)
        syncSliderValue("SCALE", scalePercent)
        syncSliderValue("BAR OPACITY", opacityPercent)
        syncSliderValue("SHADOW BAR OPACITY", shadowOpacityPercent)
        syncSliderValue("GLOW INTENSITY", glowPercent)
        syncSliderValue("RESPONSE SPEED", responsePercent)
        syncSliderValue("CORNER ROUNDNESS", cornerRadiusDp)
        updateBarAppearanceModeAvailability()
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
            applyPillStyle(dpButton, positionUnit == "DP")
            applyPillStyle(cmButton, positionUnit == "CM")
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
        panel.addView(up, matchWrap().apply { topMargin = 16.dp() })

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
        val originalRotationDegrees = rotationDegrees
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

        var rotationTextUpdating = false
        rotationInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (rotationTextUpdating) return
                val value = s?.toString()?.toIntOrNull() ?: return
                rotationDegrees = value.coerceIn(0, 359)
                rotationSeek.progress = rotationDegrees
                rotationLabel.text = "$rotationDegrees°"
                scheduleRealtimeUpdate(90L)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

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
            isVerticalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            background = null
            addView(panel)
        }
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val doneButton = glassButton(if (appLanguage == "id") "SELESAI" else "DONE")
        val cancelButton = glassButton(if (appLanguage == "id") "BATAL" else "CANCEL")
        footer.addView(cancelButton, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginEnd = 5.dp() })
        footer.addView(doneButton, LinearLayout.LayoutParams(0, 44.dp(), 1f).apply { marginStart = 5.dp() })
        val dialog = showGlassStudioDialog(
            if (appLanguage == "id") "STUDIO POSISI FLOATING" else "FLOATING POSITION STUDIO",
            scroll, footer, 0.96f, 0.92f
        )
        doneButton.setOnClickListener { applyRotationValue(); dialog.dismiss() }
        cancelButton.setOnClickListener {
            updateHandler.removeCallbacks(realtimeUpdate)
            realtimeUpdateScheduled = false
            rotationDegrees = originalRotationDegrees
            saveSettings()
            updateFloatingService()
            dialog.dismiss()
        }
    }

    private fun positionButton(label: String, action: () -> Unit): TextView {
        return glassButton(label).apply { textSize = 16f; setOnClickListener { action() } }
    }

    private fun formatLayerCoordinateStatus(xDp: Int, yDp: Int, unit: String): String {
        return if (unit == "DP") "X $xDp dp     Y $yDp dp"
        else "X ${String.format(java.util.Locale.US, "%.2f", dpToCm(xDp.toFloat()))} cm     Y ${String.format(java.util.Locale.US, "%.2f", dpToCm(yDp.toFloat()))} cm"
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
        isClickable = true
        isFocusable = true
        setPadding(10.dp(), 10.dp(), 10.dp(), 10.dp())
        applyPillStyle(this, false)
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = translateUiText(value)
        textSize = 13f
        setTextColor(themedTextColor(this, isLightTheme()))
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun subtitle(value: String): TextView = TextView(this).apply {
        text = translateUiText(value)
        textSize = 10f
        setTextColor(themedTextColor(this, isLightTheme()))
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
                button.setTextColor(textOnAccent(currentAccent()))
                button.contentDescription = "Resume audio visualizer"
            }
            floatingServiceStarted -> {
                button.text = translateUiText("■  STOP VISUALIZER")
                button.background = dangerDrawable()
                button.setTextColor(Color.WHITE)
                button.contentDescription = "Stop audio visualizer"
            }
            else -> {
                button.text = translateUiText("▶  START VISUALIZER")
                button.background = accentDrawable(currentAccent())
                button.setTextColor(textOnAccent(currentAccent()))
                button.contentDescription = "Start audio visualizer"
            }
        }
    }

    private fun startFloatingFromButton() {
        // Audio engine and live in-app spectrum start independently from overlay permission.
        requestPrimaryPermissions()
    }

    private fun requestPrimaryPermissions() {
        val pending = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pending.add(Manifest.permission.RECORD_AUDIO)
        }
        // Android 13+ needs this runtime grant for the FGS notification to appear
        // in the notification drawer. Denial must not block audio visualization.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pending.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (pending.isNotEmpty()) {
            requestPermissions(pending.toTypedArray(), REQUEST_AUDIO)
        } else {
            startPrimaryVisualizer()
        }
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
            setEngineStatusBadge("STARTING")
            audioSourceStatus.text = translateUiText("VISUALIZER · STARTING")
            updateStartStopButton()
        } catch (e: Exception) {
            floatingServiceStarted = false
            prefs.edit().putBoolean("service_active", false).apply()
            Toast.makeText(this, "Tidak bisa memulai audio: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Backup playback capture membutuhkan Android 10+", Toast.LENGTH_SHORT).show()
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
            val audioIndex = permissions.indexOf(Manifest.permission.RECORD_AUDIO)
            val audioGranted = audioIndex >= 0 && grantResults.getOrNull(audioIndex) == PackageManager.PERMISSION_GRANTED
            val notificationIndex = permissions.indexOf(Manifest.permission.POST_NOTIFICATIONS)
            val notificationDenied = Build.VERSION.SDK_INT >= 33 && notificationIndex >= 0 &&
                grantResults.getOrNull(notificationIndex) != PackageManager.PERMISSION_GRANTED
            if (audioGranted) {
                if (notificationDenied) {
                    Toast.makeText(
                        this,
                        if (appLanguage == "id") "Audio tetap jalan. Notifikasi bisa diaktifkan dari Pengaturan Android." else "Audio will work. You can enable its notification in Android Settings.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                startPrimaryVisualizer()
            } else {
                Toast.makeText(this, if (appLanguage == "id") "Izin audio diperlukan untuk visualizer" else "Audio permission is required for the visualizer", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, if (appLanguage == "id") "Backup berhasil diekspor" else "Backup exported", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(this, if (appLanguage == "id") "Ekspor backup gagal: ${error.message}" else "Backup export failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_IMPORT_CONFIG -> if (resultCode == RESULT_OK && data?.data != null) applyConfigBackup(data.data!!)
            REQUEST_EXPORT_PROFILE -> if (resultCode == RESULT_OK && data?.data != null) {
                try {
                    val profile = pendingProfileExport ?: throw IllegalStateException("No profile selected")
                    contentResolver.openOutputStream(data.data!!, "wt")?.bufferedWriter()?.use {
                        it.write(profileToJson(profile).toString(2))
                    } ?: throw IllegalStateException("Unable to create selected file")
                    Toast.makeText(this, if (appLanguage == "id") "Profil berhasil diekspor" else "Profile exported", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(this, if (appLanguage == "id") "Ekspor profil gagal: ${error.message}" else "Profile export failed: ${error.message}", Toast.LENGTH_SHORT).show()
                } finally { pendingProfileExport = null }
            } else if (requestCode == REQUEST_EXPORT_PROFILE) {
                pendingProfileExport = null
            }
            REQUEST_IMPORT_PROFILE -> if (resultCode == RESULT_OK && data?.data != null) reviewImportedColorProfile(data.data!!)
            REQUEST_EXPORT_FLOATING_SCENE -> if (resultCode == RESULT_OK && data?.data != null) {
                try {
                    val json = pendingFloatingSceneExportJson ?: throw IllegalStateException("No scene selected")
                    contentResolver.openOutputStream(data.data!!, "wt")?.bufferedWriter()?.use { it.write(json) }
                        ?: throw IllegalStateException("Unable to create selected file")
                    Toast.makeText(this, if (appLanguage == "id") "Scene lengkap berhasil diekspor" else "Complete scene exported", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(this, "${if (appLanguage == "id") "Ekspor scene gagal" else "Scene export failed"}: ${error.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    pendingFloatingSceneExportJson = null
                    pendingFloatingSceneExportName = null
                }
            } else if (requestCode == REQUEST_EXPORT_FLOATING_SCENE) {
                pendingFloatingSceneExportJson = null
                pendingFloatingSceneExportName = null
            }
            REQUEST_IMPORT_FLOATING_SCENE -> if (resultCode == RESULT_OK && data?.data != null) reviewImportedFloatingScene(data.data!!)
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
                        Toast.makeText(this, "Backup gagal: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun addFloatingSettings(intent: Intent, includeLiveSelectedProfile: Boolean = false) {
        intent.putExtra(FloatingVisualizerService.EXTRA_VERTICAL, isVerticalMode)
        intent.putExtra(FloatingVisualizerService.EXTRA_COLORS, spectrumColors.toIntArray())
        intent.putExtra(FloatingVisualizerService.EXTRA_WIDTH, overlayWidthDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_HEIGHT, overlayHeightDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_BAR_COUNT, barCount)
        intent.putExtra(FloatingVisualizerService.EXTRA_BAR_WIDTH, barWidthDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_GAP, barGapDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_SCALE, scalePercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_OPACITY, opacityPercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_SHADOW_OPACITY, shadowOpacityPercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_GLOW, glowPercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_RESPONSE, responsePercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_CORNER_RADIUS, cornerRadiusDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_EFFECT, barEffect)
        intent.putExtra(FloatingVisualizerService.EXTRA_SINGLE_MODE, singleAudioMode)
        intent.putExtra(FloatingVisualizerService.EXTRA_BASS_MIN_HZ, bassMinHz)
        intent.putExtra(FloatingVisualizerService.EXTRA_BASS_MAX_HZ, bassMaxHz)
        intent.putExtra(FloatingVisualizerService.EXTRA_HORIZONTAL_ALIGNMENT, horizontalAlignment)
        intent.putExtra(FloatingVisualizerService.EXTRA_MIRROR, mirrorMode != "OFF")
        intent.putExtra(FloatingVisualizerService.EXTRA_MIRROR_MODE, mirrorMode)
        intent.putExtra(FloatingVisualizerService.EXTRA_POSITION_X, positionXdp)
        intent.putExtra(FloatingVisualizerService.EXTRA_POSITION_Y, positionYdp)
        intent.putExtra(FloatingVisualizerService.EXTRA_POSITION_MODE, positionMode)
        intent.putExtra(FloatingVisualizerService.EXTRA_ROTATION, rotationDegrees)
        intent.putExtra(FloatingVisualizerService.EXTRA_FLOATING_ENABLED, floatingOverlayEnabled)
        intent.putExtra(FloatingVisualizerService.EXTRA_FLOATING_PROFILES_JSON, floatingProfileJson(includeLiveSelectedProfile))
    }

    private fun refreshFloatingNotificationTheme() {
        if (!floatingServiceStarted && !servicePaused) return
        val intent = Intent(this, FloatingVisualizerService::class.java).apply {
            action = FloatingVisualizerService.ACTION_REFRESH_NOTIFICATION
        }
        try { startService(intent) } catch (_: Exception) { }
    }

    private fun updateFloatingService(includeLiveSelectedProfile: Boolean = false) {
        invalidateLivePreviewProfileCache()
        markProfileDirtyFromCurrentState()
        // Regular edits are committed to the selected Floating Profile before its
        // JSON is sent. Preview-only imports keep the temporary override in the
        // Intent without mutating the saved profile library.
        if (!includeLiveSelectedProfile) syncSelectedFloatingProfileFromCurrent()
        scheduleBarAppearancePresetRefresh()
        // Refresh the monitor from the same state that will be serialized to Service.
        if (::barsContainer.isInitialized && livePreviewProfileId == selectedFloatingProfileId) {
            refreshLivePreviewAfterProfileEdit()
            updateModeButtons()
            updateSingleModeButtons()
            updateSingleModeVisibility()
            buildLiveFrequencyLabels()
            if (latestSpectrumLevels.isNotEmpty()) updatePreviewLevels(latestSpectrumLevels)
        }
        // The service can remain in the foreground while paused from its notification.
        // Keep settings synchronized in that state without creating a new audio session.
        if (!floatingServiceStarted && !servicePaused) return
        val intent = Intent(this, FloatingVisualizerService::class.java).apply {
            action = FloatingVisualizerService.ACTION_UPDATE
            addFloatingSettings(this, includeLiveSelectedProfile)
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
        setEngineStatusBadge("OFF")
        audioSourceStatus.text = translateUiText("VISUALIZER · READY")
        updateStartStopButton()
    }

    private fun setEngineStatusBadge(state: String) {
        val isActive = state == "ON"
        val isStarting = state == "STARTING"
        val bgColors = when {
            isActive -> intArrayOf(Color.parseColor("#2BA879"), Color.parseColor("#167653"))
            isStarting -> intArrayOf(Color.parseColor("#D99A36"), Color.parseColor("#A86A1D"))
            else -> intArrayOf(Color.parseColor("#E05D70"), Color.parseColor("#A9324A"))
        }
        engineStatus.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, bgColors).apply {
            cornerRadius = 18.dp().toFloat()
            setStroke(1.dp(), 0x55FFFFFF)
        }
        engineStatus.setTextColor(Color.WHITE)
        engineStatus.text = when (state) {
            "ON" -> translateUiText("● AUDIO LIVE · ON")
            "STARTING" -> translateUiText("● AUDIO STARTING")
            "PAUSED" -> translateUiText("● AUDIO LIVE · OFF")
            "ERROR" -> translateUiText("● AUDIO LIVE · OFF")
            else -> translateUiText("● AUDIO LIVE · OFF")
        }
    }

    private fun updateEngineStatus(status: String) {
        when {
            status.startsWith("PRIMARY_ACTIVE") -> {
                floatingServiceStarted = true
                backupCaptureEnabled = false
                setEngineStatusBadge("ON")
                audioSourceStatus.text = translateUiText("VISUALIZER · ACTIVE")
                setBackupSwitch(false)
                servicePaused = false
                prefs.edit().putBoolean("service_active", true).putBoolean("backup_capture", false).putBoolean("service_paused", false).apply()
            }
            status.startsWith("BACKUP_ACTIVE") -> {
                floatingServiceStarted = true
                backupCaptureEnabled = true
                setEngineStatusBadge("ON")
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
                setEngineStatusBadge("ERROR")
                audioSourceStatus.text = translateUiText("VISUALIZER · UNAVAILABLE")
                Toast.makeText(this, status.substringAfter(':', "Visualizer tidak tersedia"), Toast.LENGTH_SHORT).show()
            }
            status.startsWith("BACKUP_ERROR") -> {
                backupCaptureEnabled = false
                prefs.edit().putBoolean("backup_capture", false).apply()
                setBackupSwitch(false)
                setEngineStatusBadge("ERROR")
                audioSourceStatus.text = translateUiText("PRIMARY VISUALIZER")
                Toast.makeText(this, status.substringAfter(':', "Backup tidak tersedia"), Toast.LENGTH_SHORT).show()
            }
            status.startsWith("OVERLAY_ACTIVE") -> {
                floatingOverlayEnabled = true
                setFloatingOverlaySwitch(true)
                prefs.edit().putBoolean("floating_overlay_enabled", true).apply()
                markProfileDirtyFromCurrentState()
            }
            status.startsWith("OVERLAY_HIDDEN") -> {
                floatingOverlayEnabled = false
                setFloatingOverlaySwitch(false)
                prefs.edit().putBoolean("floating_overlay_enabled", false).apply()
                markProfileDirtyFromCurrentState()
            }
            status.startsWith("OVERLAY_PERMISSION_REQUIRED") -> {
                floatingOverlayEnabled = false
                setFloatingOverlaySwitch(false)
                prefs.edit().putBoolean("floating_overlay_enabled", false).apply()
                Toast.makeText(this, "Izin tampil di atas aplikasi perlu diaktifkan dari aplikasi", Toast.LENGTH_SHORT).show()
            }
            status.startsWith("OVERLAY_ERROR") -> {
                Toast.makeText(
                    this,
                    status.substringAfter(':', "Floating window gagal dibuat; audio tetap berjalan"),
                    Toast.LENGTH_SHORT
                ).show()
            }
            status.startsWith("AUDIO_PAUSED") -> {
                floatingServiceStarted = false
                servicePaused = true
                backupCaptureEnabled = false
                prefs.edit().putBoolean("service_active", false).putBoolean("backup_capture", false).putBoolean("service_paused", true).apply()
                setEngineStatusBadge("PAUSED")
                audioSourceStatus.text = translateUiText("ENGINE · PAUSED")
                setBackupSwitch(false)
            }
            status == "READY" -> {
                floatingServiceStarted = false
                servicePaused = false
                backupCaptureEnabled = false
                setEngineStatusBadge("OFF")
                audioSourceStatus.text = translateUiText("VISUALIZER · READY")
                setBackupSwitch(false)
            }
            status.startsWith("STOPPED") -> {
                floatingServiceStarted = false
                servicePaused = false
                backupCaptureEnabled = false
                prefs.edit().putBoolean("service_active", false).putBoolean("service_paused", false).apply()
                setEngineStatusBadge("OFF")
                audioSourceStatus.text = translateUiText("VISUALIZER · READY")
                setBackupSwitch(false)
            }
        }
        updateStartStopButton()
    }
}
