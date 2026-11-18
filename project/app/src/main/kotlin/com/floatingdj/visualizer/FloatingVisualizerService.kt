package com.floatingdj.visualizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.LinearGradient
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.audiofx.Visualizer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RemoteViews
import com.floatingdj.visualizer.eq.service.EqEngineService
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class FloatingVisualizerService : Service() {

    companion object {
        const val ACTION_START_PRIMARY = "com.floatingdj.visualizer.START_PRIMARY"
        const val ACTION_ENABLE_BACKUP = "com.floatingdj.visualizer.ENABLE_BACKUP"
        const val ACTION_DISABLE_BACKUP = "com.floatingdj.visualizer.DISABLE_BACKUP"
        const val ACTION_UPDATE = "com.floatingdj.visualizer.UPDATE"
        const val ACTION_REFRESH_NOTIFICATION = "com.floatingdj.visualizer.REFRESH_NOTIFICATION"
        const val ACTION_STOP = "com.floatingdj.visualizer.STOP"
        const val ACTION_LEVELS = "com.floatingdj.visualizer.LEVELS"
        const val ACTION_POSITION = "com.floatingdj.visualizer.POSITION"
        const val ACTION_LAYER_FOCUSED = "com.floatingdj.visualizer.LAYER_FOCUSED"
        const val ACTION_ENGINE_STATUS = "com.floatingdj.visualizer.ENGINE_STATUS"
        const val ACTION_NOTIFICATION_TOGGLE_OVERLAY = "com.floatingdj.visualizer.NOTIFICATION_TOGGLE_OVERLAY"
        const val ACTION_NOTIFICATION_TOGGLE_AUDIO = "com.floatingdj.visualizer.NOTIFICATION_TOGGLE_AUDIO"
        const val ACTION_NOTIFICATION_START_AUDIO = "com.floatingdj.visualizer.NOTIFICATION_START_AUDIO"
        const val ACTION_NOTIFICATION_STOP_AUDIO = "com.floatingdj.visualizer.NOTIFICATION_STOP_AUDIO"
        const val ACTION_NOTIFICATION_TOGGLE_EQ = "com.floatingdj.visualizer.NOTIFICATION_TOGGLE_EQ"
        const val ACTION_SET_OVERLAY_SUPPRESSED = "com.floatingdj.visualizer.SET_OVERLAY_SUPPRESSED"

        const val EXTRA_LEVELS = "extra_levels"
        const val EXTRA_ENGINE_STATUS = "extra_engine_status"
        const val EXTRA_VERTICAL = "extra_vertical"
        const val EXTRA_COLORS = "extra_colors"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_BAR_COUNT = "extra_bar_count"
        const val EXTRA_BAR_WIDTH = "extra_bar_width"
        const val EXTRA_GAP = "extra_gap"
        const val EXTRA_SCALE = "extra_scale"
        const val EXTRA_OPACITY = "extra_opacity"
        const val EXTRA_SHADOW_OPACITY = "extra_shadow_opacity"
        const val EXTRA_GLOW = "extra_glow"
        const val EXTRA_RESPONSE = "extra_response_speed"
        const val EXTRA_FFT_SIZE = "extra_fft_size"
        const val EXTRA_HOP_SIZE = "extra_hop_size"
        const val EXTRA_CORNER_RADIUS = "extra_corner_radius"
        const val EXTRA_EFFECT = "extra_effect"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        const val EXTRA_SINGLE_MODE = "extra_single_mode"
        const val EXTRA_BASS_MIN_HZ = "extra_bass_min_hz"
        const val EXTRA_BASS_MAX_HZ = "extra_bass_max_hz"
        const val EXTRA_HORIZONTAL_ALIGNMENT = "extra_horizontal_alignment"
        const val EXTRA_MIRROR = "extra_horizontal_mirror" // Legacy boolean.
        const val EXTRA_MIRROR_MODE = "extra_mirror_mode"
        const val EXTRA_POSITION_X = "extra_position_x"
        const val EXTRA_POSITION_Y = "extra_position_y"
        const val EXTRA_POSITION_MODE = "extra_position_mode"
        const val EXTRA_ROTATION = "extra_rotation"
        const val EXTRA_FLOATING_ENABLED = "extra_floating_enabled"
        const val EXTRA_FLOATING_PROFILES_JSON = "extra_floating_profiles_json"
        const val EXTRA_POSITION_PROFILE_ID = "extra_position_profile_id"
        const val EXTRA_POSITION_FINAL = "extra_position_final"
        const val EXTRA_LAYER_ID = "extra_layer_id"
        const val EXTRA_OVERLAY_SUPPRESSED = "extra_overlay_suppressed"

        private const val CHANNEL_ID = "floating_dj_audio"
        private const val NOTIFICATION_ID = 9001
        private const val SAMPLE_RATE = 44100
        private const val DEFAULT_FFT_SIZE = 2048
        private const val DEFAULT_HOP_SIZE = 512
        private const val MAX_BAR_COUNT = 64
        private const val MIN_SPECTRUM_HZ = 20.0
        private const val MAX_SPECTRUM_HZ = 20000.0
        private const val SEGMENT_COUNT = 24
    }

    private enum class EngineSource { NONE, VISUALIZER, PROJECTION }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // One VSYNC-driven animation loop advances all Floating renderers together.
    // Audio/FFT delivery remains independent from the display refresh rate.
    private var spectrumFrameScheduled = false
    private var spectrumLastFrameNanos = 0L
    private val spectrumFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            spectrumFrameScheduled = false
            if (stopping || engineSource == EngineSource.NONE) {
                spectrumLastFrameNanos = 0L
                return
            }
            val dtSeconds = if (spectrumLastFrameNanos == 0L) (1f / 60f) else
                ((frameTimeNanos - spectrumLastFrameNanos).coerceIn(1_000_000L, 50_000_000L) / 1_000_000_000f)
            spectrumLastFrameNanos = frameTimeNanos
            val nowMs = SystemClock.uptimeMillis()
            val responseFactors = spectrumFrameSmoothingFactors(responsePercent, dtSeconds)
            var continueAnimating = false
            floatingBars.forEach { view ->
                when (view) {
                    is EffectBarView -> if (view.advanceAnimation(dtSeconds, responseFactors.first, responseFactors.second)) continueAnimating = true
                    is SegmentedMeterView -> if (view.advanceAnimation(dtSeconds, nowMs, responseFactors.first, responseFactors.second)) continueAnimating = true
                }
            }
            layeredRuntime.forEach { runtime ->
                if (runtime.config.visible) {
                    val layerFactors = spectrumFrameSmoothingFactors(runtime.config.responsePercent, dtSeconds)
                    if (runtime.spectrum.advanceAnimation(dtSeconds, nowMs, layerFactors.first, layerFactors.second)) {
                        continueAnimating = true
                    }
                }
            }
            if (continueAnimating) {
                requestSpectrumAnimationFrame()
            } else {
                spectrumLastFrameNanos = 0L
            }
        }
    }

    private fun requestSpectrumAnimationFrame() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { requestSpectrumAnimationFrame() }
            return
        }
        if (spectrumFrameScheduled || stopping || engineSource == EngineSource.NONE) return
        if (floatingBars.none { it.isShown } && layeredRuntime.none { it.config.visible && it.spectrum.isShown }) return
        spectrumFrameScheduled = true
        Choreographer.getInstance().postFrameCallback(spectrumFrameCallback)
    }

    private fun cancelSpectrumAnimationFrame() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { cancelSpectrumAnimationFrame() }
            return
        }
        if (spectrumFrameScheduled) {
            Choreographer.getInstance().removeFrameCallback(spectrumFrameCallback)
            spectrumFrameScheduled = false
        }
        spectrumLastFrameNanos = 0L
    }

    private fun spectrumFrameSmoothingFactors(responsePercent: Int, dtSeconds: Float): Pair<Float, Float> {
        val response = responsePercent.coerceIn(0, 100) / 100f
        // Convert the old per-capture response (nominally 20 Hz) into a time-based
        // exponential curve once per display frame, not once per individual bar.
        val dt = dtSeconds.coerceIn(0.001f, 0.05f)
        fun factor(sampleFactor: Float): Float {
            val ratePerSecond = -kotlin.math.ln((1f - sampleFactor).coerceAtLeast(0.001f).toDouble()) / 0.05
            return (1.0 - exp(-ratePerSecond * dt)).toFloat().coerceIn(0f, 1f)
        }
        return Pair(factor(0.56f + response * 0.40f), factor(0.20f + response * 0.62f))
    }

    private var windowView: FrameLayout? = null
    private var contentView: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    // Preserve the visual center of the overlay when rotated bounds change.
    private var overlayCenterXpx: Int? = null
    private var overlayCenterYpx: Int? = null
    private val floatingBars = mutableListOf<View>()
    private var renderedVertical: Boolean? = null

    private var visualizer: Visualizer? = null
    @Volatile private var visualizerResolutionLogged = false
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var projection: MediaProjection? = null
    private var engineSource = EngineSource.NONE

    @Volatile private var backupRunning = false
    @Volatile private var stopping = false
    @Volatile private var bassMinHz = 20
    @Volatile private var bassMaxHz = 125

    private var isVerticalMode = false
    private var widthDp = 320
    private var heightDp = 360
    private var barCount = 15
    private var barWidthDp = 20
    private var gapDp = 3
    private var scalePercent = 100
    private var opacityPercent = 100
    private var shadowOpacityPercent = 100
    private var glowPercent = 50
    private var responsePercent = 82
    @Volatile private var fftSizeSetting = DEFAULT_FFT_SIZE
    @Volatile private var hopSizeSetting = DEFAULT_HOP_SIZE
    private var cornerRadiusDp = 14
    private var singleAudioMode = "MUSIC"
    private var horizontalAlignment = "LEFT"
    private var mirrorMode = "OFF" // OFF, VERTICAL, HORIZONTAL
    private var positionMode = "STATIC"
    private var positionXdp = 0
    private var positionYdp = 0
    private var rotationDegrees = 0
    private var effectStyle = "GLOSSY"
    private var floatingOverlayEnabled = false

    private var spectrumColors = intArrayOf(
        Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE
    )

    // Reused per frame: keep the glow pass independent from each bar's gradient Paint.
    private val spectrumGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spectrumGlowClipPath = Path()
    // Reused by the optional PCM/MediaProjection FFT loop to avoid per-frame
    // allocations that can create avoidable garbage-collection jitter.
    private var pcmFftReal = DoubleArray(DEFAULT_FFT_SIZE)
    private var pcmFftImag = DoubleArray(DEFAULT_FFT_SIZE)
    private var pcmHannWindow = createHannWindow(DEFAULT_FFT_SIZE)

    private fun createHannWindow(size: Int): DoubleArray = DoubleArray(size) { index ->
        0.5 * (1.0 - cos(2.0 * PI * index / (size - 1)))
    }

    private fun normalizeFftSize(value: Int): Int = when {
        value < 1536 -> 1024
        value < 3072 -> 2048
        else -> 4096
    }

    private fun normalizeHopSize(value: Int, fftSize: Int): Int {
        val allowed = listOf(256, 512, 1024, 2048).filter { it <= fftSize.coerceAtLeast(256) }
        return allowed.minByOrNull { kotlin.math.abs(it - value) } ?: 256
    }

    private fun ensurePcmFftWorkspace() {
        val size = fftSizeSetting.coerceIn(1024, 4096)
        if (pcmFftReal.size != size || pcmFftImag.size != size || pcmHannWindow.size != size) {
            pcmFftReal = DoubleArray(size)
            pcmFftImag = DoubleArray(size)
            pcmHannWindow = createHannWindow(size)
        }
    }
    private var switchingSource = false

    // Keep only the newest FFT frame while the UI thread is busy. This avoids
    // queuing stale spectrum frames and showing delayed bars after a long layout/draw.
    private val pendingSpectrumFrame = AtomicReference<FloatArray?>(null)
    private val spectrumDeliveryScheduled = AtomicBoolean(false)
    private var foregroundStarted = false
    private var audioPaused = false
    private var audioTransitionInProgress = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            if (stopping || switchingSource) return
            switchingSource = true
            projection = null
            releaseAudioRecordOnly()
            engineSource = EngineSource.NONE
            startPrimaryForeground()
            val success = startPrimaryEngineInternal()
            switchingSource = false
            if (success) broadcastStatus("PRIMARY_ACTIVE: Projection ended; Visualizer restarted")
            else broadcastStatus("BACKUP_ERROR: Projection ended and primary Visualizer is unavailable")
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val commandIntent = intent ?: run {
            val saved = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
            val wasRunning = saved.getBoolean("service_active", false)
            val wasPaused = saved.getBoolean("service_paused", false)
            if (!wasRunning || wasPaused) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            // Android may recreate a sticky foreground service with a null Intent.
            // Start the engine again only when the persisted state says it was previously running.
            Intent(this, FloatingVisualizerService::class.java).apply { action = ACTION_START_PRIMARY }
        }

        val oldOverlaySetting = floatingOverlayEnabled
        val oldFftSizeSetting = fftSizeSetting
        val oldHopSizeSetting = hopSizeSetting
        readSettings(commandIntent)

        when (commandIntent.action) {
            ACTION_SET_OVERLAY_SUPPRESSED -> {
                // Legacy compatibility: dialog visibility must never hide the Floating layer.
                // Only the user's Floating switch controls floatingOverlayEnabled.
                applyOverlayVisibility()
                return START_STICKY
            }
            ACTION_STOP -> {
                stopping = true
                stopEngineResources()
                removeOverlay()
                setServiceActive(false)
                stopForegroundCompat()
                stopSelf(startId)
                return START_NOT_STICKY
            }
            ACTION_NOTIFICATION_TOGGLE_OVERLAY -> {
                val requestedOverlayState = commandIntent.getBooleanExtra(
                    RemoteViews.EXTRA_CHECKED,
                    !floatingOverlayEnabled
                )
                if (requestedOverlayState && !Settings.canDrawOverlays(this)) {
                    floatingOverlayEnabled = false
                    getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
                        .putBoolean("floating_overlay_enabled", false)
                        .apply()
                    updateFloatingWindow()
                    broadcastStatus("OVERLAY_PERMISSION_REQUIRED: Allow display over other apps from the app")
                } else {
                    floatingOverlayEnabled = requestedOverlayState
                    getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
                        .putBoolean("floating_overlay_enabled", floatingOverlayEnabled)
                        .apply()
                    updateFloatingWindow()
                    broadcastStatus(if (floatingOverlayEnabled) "OVERLAY_ACTIVE: Floating overlay shown" else "OVERLAY_HIDDEN: Floating overlay hidden")
                }
                refreshNotification("Audio ${if (engineSource == EngineSource.NONE) "stopped" else "active"}")
                return START_STICKY
            }
            ACTION_NOTIFICATION_TOGGLE_AUDIO -> {
                handleNotificationAudioToggle()
                return START_STICKY
            }
            ACTION_NOTIFICATION_START_AUDIO -> {
                startAudioFromNotification()
                return START_STICKY
            }
            ACTION_NOTIFICATION_STOP_AUDIO -> {
                stopAudioFromNotification()
                return START_STICKY
            }
            ACTION_NOTIFICATION_TOGGLE_EQ -> {
                handleNotificationEqToggle()
                return START_STICKY
            }
            ACTION_REFRESH_NOTIFICATION -> {
                if (foregroundStarted) {
                    val message = when (engineSource) {
                        EngineSource.PROJECTION -> "Backup audio active"
                        EngineSource.VISUALIZER -> "System audio active"
                        EngineSource.NONE -> if (audioPaused) "Audio stopped" else "Audio service ready"
                    }
                    refreshNotification(message)
                }
                return START_STICKY
            }
            ACTION_UPDATE -> {
                val fftSizeChanged = oldFftSizeSetting != fftSizeSetting
                val hopSizeChanged = oldHopSizeSetting != hopSizeSetting
                if (fftSizeChanged || hopSizeChanged) {
                    when (engineSource) {
                        EngineSource.VISUALIZER -> {
                            // Standard Android Visualizer has no Hop Size parameter;
                            // only a changed capture size requires recreating this effect.
                            if (fftSizeChanged) {
                                val restarted = startPrimaryEngineInternal()
                                if (!restarted) broadcastStatus("PRIMARY_ERROR: Visualizer could not apply the selected FFT size")
                                else broadcastStatus("PRIMARY_ACTIVE: Visualizer reconfigured · FFT ${fftSizeSetting}")
                            }
                            updateFloatingWindow()
                            return START_STICKY
                        }
                        EngineSource.PROJECTION -> {
                            val restarted = restartProjectionCaptureForAnalysisSettings()
                            if (restarted) {
                                broadcastStatus("BACKUP_ACTIVE: MediaProjection reconfigured · FFT ${fftSizeSetting} · Hop ${hopSizeSetting}")
                            } else {
                                // Preserve a usable audio source if the PCM recorder
                                // rejects a newly selected analysis configuration.
                                switchingSource = true
                                stopProjectionResources()
                                switchingSource = false
                                startPrimaryForeground()
                                val fallback = startPrimaryEngineInternal()
                                if (fallback) broadcastStatus("BACKUP_ERROR: New FFT/Hop settings were rejected; primary Visualizer restarted")
                                else broadcastStatus("BACKUP_ERROR: Reconfiguration failed and primary Visualizer is unavailable")
                            }
                            updateFloatingWindow()
                            return START_STICKY
                        }
                        EngineSource.NONE -> Unit
                    }
                }
                when (engineSource) {
                    EngineSource.VISUALIZER -> broadcastStatus("PRIMARY_ACTIVE: Visualizer active")
                    EngineSource.PROJECTION -> broadcastStatus("BACKUP_ACTIVE: MediaProjection active")
                    EngineSource.NONE -> {
                        if (!foregroundStarted) {
                            broadcastStatus("STOPPED: No active audio session")
                            setServiceActive(false)
                            stopSelf(startId)
                            return START_NOT_STICKY
                        } else if (audioPaused) {
                            updateFloatingWindow()
                            broadcastStatus("AUDIO_PAUSED: Audio session is paused")
                            refreshNotification("Audio stopped · tap Start Visualizer to start again")
                            return START_STICKY
                        } else {
                            // Primary Visualizer may be unavailable on this device.
                            // Keep the foreground host alive so the optional backup
                            // can be enabled without restarting the whole service.
                            broadcastStatus("PRIMARY_ERROR: Primary audio engine is unavailable; optional backup can still be enabled")
                            return START_STICKY
                        }
                    }
                }
                updateFloatingWindow()
                // Slider/layout updates should not rebuild the system notification on every tick.
                // Refresh only when the user changed the overlay switch or the service corrected it.
                if (oldOverlaySetting != floatingOverlayEnabled) {
                    refreshNotification(if (engineSource == EngineSource.PROJECTION) "Backup audio active" else "System audio active")
                }
                return START_STICKY
            }
            ACTION_DISABLE_BACKUP -> {
                switchingSource = true
                stopProjectionResources()
                switchingSource = false
                startPrimaryForeground()
                val ok = startPrimaryEngineInternal()
                if (ok) broadcastStatus("PRIMARY_ACTIVE: Visualizer active")
                else broadcastStatus("PRIMARY_ERROR: Output-mix Visualizer could not start")
                return START_STICKY
            }
            ACTION_ENABLE_BACKUP -> {
                try {
                    startBackupForeground()
                    val resultCode = commandIntent.getIntExtra(EXTRA_RESULT_CODE, ActivityResult.RESULT_CANCELED)
                    val resultData = getProjectionIntent(commandIntent)
                    ?: throw IllegalStateException("Data persetujuan MediaProjection tidak ditemukan")
                    val ok = startBackupEngine(resultCode, resultData)
                    if (!ok) throw IllegalStateException("Playback capture tidak bisa dimulai")
                } catch (e: Exception) {
                    android.util.Log.e("FloatingDJ", "Backup start failed", e)
                    stopProjectionResources()
                    startPrimaryForeground()
                    val primaryOk = startPrimaryEngineInternal()
                    broadcastStatus("BACKUP_ERROR: ${e.message ?: "Backup gagal"}")
                    // Keep the foreground host alive so the user can retry backup
                    // even when both the primary output-mix engine and this attempt fail.
                    setServiceActive(true)
                }
                return START_STICKY
            }
            ACTION_START_PRIMARY, null -> {
                try {
                    startPrimaryForeground()
                    if (engineSource == EngineSource.VISUALIZER && visualizer != null) {
                        updateFloatingWindow()
                        broadcastStatus("PRIMARY_ACTIVE: Visualizer active")
                        refreshNotification("System audio visualizer active")
                    } else {
                        val ok = startPrimaryEngineInternal()
                        if (!ok) broadcastStatus("PRIMARY_ERROR: Output-mix Visualizer is not available on this device")
                    }
                    refreshNotification("System audio visualizer")
                } catch (e: Exception) {
                    android.util.Log.e("FloatingDJ", "Primary visualizer failed", e)
                    releaseVisualizer()
                    broadcastStatus("PRIMARY_ERROR: ${e.message ?: "Visualizer unavailable"}")
                    setServiceActive(false)
                }
                return START_STICKY
            }
            else -> {
                startPrimaryForeground()
                val ok = startPrimaryEngineInternal()
                if (!ok) broadcastStatus("PRIMARY_ERROR: Unknown start request")
                return START_STICKY
            }
        }
    }

    private object ActivityResult { const val RESULT_CANCELED = 0 }

    @Suppress("DEPRECATION")
    private fun getProjectionIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_PROJECTION_DATA) as? Intent
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.post { updateFloatingWindow() }
    }

    // -----------------------------------------------------------------
    // FOREGROUND SERVICE
    // -----------------------------------------------------------------

    private fun startPrimaryForeground() {
        val notification = createNotification("System audio visualizer active")
        when {
            Build.VERSION.SDK_INT >= 34 -> {
                // The engine observes the system output mix; it does not record
                // microphone input. Android 14+ therefore uses specialUse.
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            }
            Build.VERSION.SDK_INT >= 29 -> {
                // Android 10-13 have no dedicated system-audio-visualizer type.
                // NONE avoids incorrectly labelling output-mix analysis as mic input.
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
                )
            }
            else -> startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
        audioPaused = false
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
            .putBoolean("service_paused", false)
            .apply()
        setServiceActive(true)
    }

    private fun startBackupForeground() {
        val notification = createNotification("Optional MediaProjection audio backup active")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
        audioPaused = false
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
            .putBoolean("service_paused", false)
            .apply()
        setServiceActive(true)
    }

    /** Notification copy follows the app's selected language; audio processing is unaffected. */
    private fun localizedServiceText(source: String): String {
        val language = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
            .getString("app_language", "en")
        if (language != "id") return source
        val translations = listOf(
            "Floating DJ Audio Engine" to "Mesin Audio Floating DJ",
            "Floating DJ Visualizer" to "Visualizer Floating DJ",
            "Real-time visualizer audio engine status" to "Status mesin visualizer audio real-time",
            "Real-time Audio Spectrum" to "Spektrum Audio Real-Time",
            "FLOATING ON" to "FLOATING AKTIF",
            "FLOATING OFF" to "FLOATING NONAKTIF",
            "Floating" to "Floating",
            "Start Visualizer" to "Mulai Visualizer",
            "Stop Visualizer" to "Hentikan Visualizer",
            "EQ Engine" to "Mesin EQ",
            "EQ ON" to "EQ AKTIF",
            "EQ OFF" to "EQ NONAKTIF",
            "M.Proj" to "M.Proj",
            "Floating ON" to "Floating AKTIF",
            "Floating OFF" to "Floating NONAKTIF",
            "M.Proj ON" to "M.Proj AKTIF",
            "M.Proj OFF" to "M.Proj NONAKTIF",
            "START" to "MULAI",
            "M.PROJ ON" to "M.PROJ AKTIF",
            "M.PROJ OFF" to "M.PROJ NONAKTIF",
            "System audio visualizer active" to "Visualizer audio sistem aktif",
            "Optional MediaProjection audio backup active" to "Cadangan audio MediaProjection aktif",
            "MediaProjection backup audio active" to "Audio cadangan MediaProjection aktif",
            "Primary audio unavailable · backup ready" to "Audio utama tidak tersedia · cadangan siap",
            "Primary audio unavailable · tap Start Visualizer to retry" to "Audio utama tidak tersedia · ketuk Mulai Visualizer untuk mencoba lagi",
            "Audio stopped · tap Start Visualizer to start again" to "Audio dihentikan · ketuk Mulai Visualizer untuk memulai lagi",
            "System audio visualizer" to "Visualizer audio sistem",
            "Audio stopped" to "Audio dihentikan",
            "Audio active" to "Audio aktif"
        )
        var translated = source
        translations.forEach { (english, indonesian) -> translated = translated.replace(english, indonesian) }
        return translated
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                localizedServiceText("Floating DJ Audio Engine"),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = localizedServiceText("Real-time visualizer audio engine status") }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun stopAudioFromNotification() {
        if (audioTransitionInProgress) return
        audioTransitionInProgress = true
        try {
            // MainActivity can request Stop after Android has reclaimed the
            // service process. Re-establish the foreground host first so this
            // action can still leave a valid stopped notification with a Start
            // action instead of creating a foreground service that never calls
            // startForeground().
            if (!foregroundStarted) startPrimaryForeground()

            // Idempotent stop: the notification and MainActivity use this same
            // stopped state, so repeated taps cannot toggle a half-updated engine.
            if (engineSource != EngineSource.NONE) {
                stopping = true
                pendingSpectrumFrame.set(null)
                cancelSpectrumAnimationFrame()
                switchingSource = true
                try {
                    releaseVisualizer()
                    stopProjectionResources()
                    engineSource = EngineSource.NONE
                } finally {
                    switchingSource = false
                }
                removeOverlay()
            }

            audioPaused = true
            setServiceActive(false)
            getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
                .putBoolean("service_paused", true)
                .putBoolean("backup_capture", false)
                .apply()
            broadcastStatus("AUDIO_PAUSED: Audio analysis stopped")
            refreshNotification("Audio stopped · tap Start Visualizer to start again")
        } finally {
            audioTransitionInProgress = false
        }
    }

    private fun startAudioFromNotification() {
        if (audioTransitionInProgress) return
        audioTransitionInProgress = true
        try {
            // Idempotent start: don't recreate a healthy Visualizer if Android
            // delivers a second tap while the notification is refreshing.
            if (engineSource != EngineSource.NONE) {
                updateFloatingWindow()
                val status = if (engineSource == EngineSource.PROJECTION) {
                    "BACKUP_ACTIVE: MediaProjection audio capture is active"
                } else {
                    "PRIMARY_ACTIVE: Visualizer output-mix is active"
                }
                broadcastStatus(status)
                refreshNotification(if (engineSource == EngineSource.PROJECTION) "MediaProjection backup audio active" else "System audio visualizer active")
                return
            }

            startPrimaryForeground()
            val started = startPrimaryEngineInternal()
            if (!started) {
                audioPaused = false
                getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
                    .putBoolean("service_paused", false)
                    .apply()
                refreshNotification("Primary audio unavailable · tap Start Visualizer to retry")
            }
        } catch (error: Throwable) {
            android.util.Log.e("FloatingDJ", "Unable to start audio from notification", error)
            audioPaused = false
            broadcastStatus("PRIMARY_ERROR: ${error.message ?: "Audio engine could not start"}")
            refreshNotification("Primary audio unavailable · tap Start Visualizer to retry")
        } finally {
            audioTransitionInProgress = false
        }
    }

    private fun handleNotificationEqToggle() {
        val prefs = getSharedPreferences("eq_presets_v1", MODE_PRIVATE)
        val enabled = prefs.getBoolean("engine_enabled", false)
        if (enabled) {
            prefs.edit().putBoolean("engine_enabled", false).apply()
            // EqEngineService owns the effect handles; stop it rather than releasing
            // them from this unrelated foreground service.
            stopService(Intent(this, EqEngineService::class.java))
        } else {
            prefs.edit().putBoolean("engine_enabled", true).apply()
            val eqIntent = Intent(this, EqEngineService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(eqIntent) else startService(eqIntent)
            } catch (t: Throwable) {
                prefs.edit().putBoolean("engine_enabled", false).apply()
                broadcastStatus("EQ_NOTIFICATION_ERROR: ${t.message ?: "EQ service could not start"}")
            }
        }
        refreshNotification(if (engineSource == EngineSource.NONE) "Audio stopped" else "Audio active")
    }

    private fun notificationEqEnabled(): Boolean = getSharedPreferences("eq_presets_v1", MODE_PRIVATE)
        .getBoolean("engine_enabled", false)

    private fun handleNotificationAudioToggle() {
        if (engineSource == EngineSource.NONE) startAudioFromNotification()
        else stopAudioFromNotification()
    }

    private fun notificationPendingFlags(): Int {
        return PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }

    private fun openAppPendingIntent(): PendingIntent {
        val open = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(this, 9100, open, notificationPendingFlags())
    }

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val command = Intent(this, FloatingVisualizerService::class.java).setAction(action)
        return PendingIntent.getService(this, requestCode, command, notificationPendingFlags())
    }

    private fun backupTogglePendingIntent(): PendingIntent {
        val open = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_NOTIFICATION_TOGGLE_BACKUP, true)
        }
        return PendingIntent.getActivity(this, 9103, open, notificationPendingFlags())
    }

    private fun notificationThemeMode(): String {
        val preferences = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        return preferences.getString("theme_mode", "GLASS") ?: "GLASS"
    }

    private fun notificationThemeTextColor(): Int {
        val preferences = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        val mode = notificationThemeMode()
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val (key, fallback) = when (mode) {
            "LIGHT" -> "theme_text_color_light" to Color.parseColor("#24324A")
            "DARK" -> "theme_text_color_dark" to Color.parseColor("#F2F5FC")
            "SYSTEM" -> if (night) {
                "theme_text_color_system_dark" to Color.parseColor("#F2F5FC")
            } else {
                "theme_text_color_system_light" to Color.parseColor("#24324A")
            }
            "CUSTOM" -> "theme_text_color_custom" to Color.parseColor("#F5F7FC")
            else -> "theme_text_color_glass" to Color.parseColor("#F5F7FC")
        }
        val savedColor = when {
            preferences.contains(key) -> preferences.getInt(key, fallback)
            mode == "CUSTOM" && preferences.contains("theme_text_color") -> preferences.getInt("theme_text_color", fallback)
            mode == "SYSTEM" && preferences.contains("theme_text_color_system") -> preferences.getInt("theme_text_color_system", fallback)
            else -> fallback
        }
        val light = notificationStudioIsLight()
        if (mode == "CUSTOM") {
            val surface = preferences.getInt("theme_surface_color", Color.parseColor("#1C2B42"))
            val top = notificationAdjustColor(surface, 0.08f)
            val bottom = notificationAdjustColor(surface, -0.08f)
            if (minOf(notificationContrastRatio(savedColor, top), notificationContrastRatio(savedColor, bottom)) < 4.5) {
                return notificationBestContrastText(top, bottom)
            }
            return savedColor
        }
        val referenceSurface = if (light) Color.WHITE else Color.parseColor("#101A2B")
        return if (notificationContrastRatio(savedColor, referenceSurface) >= 4.5) savedColor
        else notificationBestContrastText(referenceSurface, referenceSurface)
    }

    private fun notificationColorLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92 else Math.pow((normalized + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    private fun notificationContrastRatio(first: Int, second: Int): Double {
        val a = notificationColorLuminance(first)
        val b = notificationColorLuminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun notificationBestContrastText(firstSurface: Int, secondSurface: Int): Int {
        val whiteMinimum = minOf(notificationContrastRatio(Color.WHITE, firstSurface), notificationContrastRatio(Color.WHITE, secondSurface))
        val dark = Color.parseColor("#17243A")
        val darkMinimum = minOf(notificationContrastRatio(dark, firstSurface), notificationContrastRatio(dark, secondSurface))
        return if (whiteMinimum >= darkMinimum) Color.WHITE else dark
    }

    private fun notificationStudioIsLight(): Boolean {
        val preferences = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        return when (notificationThemeMode()) {
            "LIGHT" -> true
            "SYSTEM" -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
            "CUSTOM" -> {
                val surface = preferences.getInt("theme_surface_color", Color.parseColor("#1C2B42"))
                (Color.red(surface) * 0.2126 + Color.green(surface) * 0.7152 + Color.blue(surface) * 0.0722) > 145.0
            }
            "DARK", "GLASS" -> false
            else -> false
        }
    }

    private fun notificationStudioAccent(): Int {
        val preferences = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        return when (notificationThemeMode()) {
            "CUSTOM" -> preferences.getInt("theme_accent", Color.parseColor("#9B7BFF"))
            "DARK" -> Color.parseColor("#F1F4FA")
            "LIGHT" -> Color.parseColor("#26344B")
            "SYSTEM" -> if (notificationStudioIsLight()) Color.parseColor("#26344B") else Color.parseColor("#F1F4FA")
            else -> Color.parseColor("#9B7BFF") // GLASS owns its built-in accent.
        }
    }

    private fun notificationSwitchTrackColor(checked: Boolean): Int {
        val preferences = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        val mode = notificationThemeMode()
        val light = notificationStudioIsLight()
        val accent = notificationStudioAccent()
        return when (mode) {
            "LIGHT" -> if (checked) Color.parseColor("#FFFFFF") else Color.parseColor("#E8EDF5")
            "DARK" -> if (checked) Color.parseColor("#344258") else Color.parseColor("#202B3D")
            "SYSTEM" -> if (light) {
                if (checked) Color.parseColor("#FFFFFF") else Color.parseColor("#E8EDF5")
            } else {
                if (checked) Color.parseColor("#344258") else Color.parseColor("#202B3D")
            }
            "CUSTOM" -> if (checked) accent else preferences.getInt("theme_surface_color", Color.parseColor("#1C2B42"))
            else -> if (checked) accent else Color.parseColor("#26364B") // GLASS
        }
    }

    private fun notificationContrastColor(background: Int): Int {
        fun luminance(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        val l = 0.2126 * luminance(Color.red(background)) +
            0.7152 * luminance(Color.green(background)) +
            0.0722 * luminance(Color.blue(background))
        val whiteContrast = 1.05 / (l + 0.05)
        val darkContrast = (l + 0.05) / 0.05
        return if (whiteContrast >= darkContrast) Color.WHITE else Color.parseColor("#26344B")
    }

    private fun notificationAdjustColor(color: Int, amount: Float): Int {
        val target = if (amount >= 0f) 255 else 0
        val ratio = kotlin.math.abs(amount).coerceIn(0f, 1f)
        fun channel(value: Int): Int = (value + (target - value) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(channel(Color.red(color)), channel(Color.green(color)), channel(Color.blue(color)))
    }

    private fun buildNotificationIconBitmap(resourceId: Int, tintColor: Int): Bitmap {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val size = (28f * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val drawable = getDrawable(resourceId)?.mutate() ?: return bitmap
        drawable.setTint(tintColor)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** Draw the notification toggle ourselves so Android/OEM Switch tint defaults cannot override Theme Studio. */
    private fun buildNotificationToggleBitmap(checked: Boolean): Bitmap {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val width = (54f * density).toInt().coerceAtLeast(1)
        val height = (30f * density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val trackColor = notificationSwitchTrackColor(checked)
        val thumbColor = notificationContrastColor(trackColor)
        val inset = 1f * density
        val radius = height / 2f
        val topColor = notificationAdjustColor(trackColor, if (notificationThemeMode() == "LIGHT") -0.025f else 0.10f)
        val bottomColor = notificationAdjustColor(trackColor, if (notificationThemeMode() == "LIGHT") -0.045f else -0.10f)
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, inset, 0f, height - inset, topColor, bottomColor, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, trackPaint)
        trackPaint.shader = null
        trackPaint.style = Paint.Style.STROKE
        trackPaint.strokeWidth = 1f * density
        trackPaint.color = if (notificationStudioIsLight()) Color.argb(55, 38, 52, 75) else Color.argb(90, 220, 232, 250)
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, trackPaint)

        val thumbRadius = (height - 6f * density).coerceAtLeast(2f) / 2f
        val thumbX = if (checked) width - radius else radius
        val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = thumbColor
            setShadowLayer(1.2f * density, 0f, 0.8f * density, 0x44000000)
        }
        canvas.drawCircle(thumbX, height / 2f, thumbRadius, thumbPaint)
        thumbPaint.clearShadowLayer()
        thumbPaint.style = Paint.Style.STROKE
        thumbPaint.strokeWidth = 0.7f * density
        thumbPaint.color = if (notificationStudioIsLight()) Color.argb(55, 20, 32, 52) else Color.argb(70, 255, 255, 255)
        canvas.drawCircle(thumbX, height / 2f, thumbRadius, thumbPaint)
        return bitmap
    }

    private fun createNotification(message: String): Notification {
        val audioActive = engineSource != EngineSource.NONE
        val backupActive = engineSource == EngineSource.PROJECTION
        val overlayLabel = localizedServiceText("Floating")
        val audioLabel = localizedServiceText(if (audioActive) "Stop Visualizer" else "Start Visualizer")
        val backupLabel = localizedServiceText("M.Proj")
        val eqActive = notificationEqEnabled()
        val overlayState = if (floatingOverlayEnabled) "ON" else "OFF"
        val backupState = if (backupActive) "ON" else "OFF"
        val eqState = if (eqActive) "ON" else "OFF"
        val studioTextColor = notificationThemeTextColor()
        val studioSecondaryTextColor = if (notificationStudioIsLight()) Color.parseColor("#66748A") else Color.parseColor("#AAB7CA")
        val compactSummary = localizedServiceText(
            "Floating $overlayState · ${if (audioActive) "Stop Visualizer" else "Start Visualizer"} · EQ $eqState · M.Proj $backupState"
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val compactView = RemoteViews(packageName, R.layout.notification_controls_compact).apply {
            setTextViewText(R.id.notificationCompactSummary, compactSummary)
            setTextViewText(R.id.notificationCompactStatus, localizedServiceText(message))
            setTextColor(R.id.notificationCompactSummary, studioTextColor)
            setTextColor(R.id.notificationCompactStatus, studioSecondaryTextColor)
        }

        val expandedView = RemoteViews(packageName, R.layout.notification_controls_expanded).apply {
            val primaryTextColor = studioTextColor
            setTextColor(R.id.notificationFloatingLabel, primaryTextColor)
            setTextColor(R.id.notificationPauseLabel, primaryTextColor)
            setTextColor(R.id.notificationEqLabel, primaryTextColor)
            setTextColor(R.id.notificationProjectionLabel, primaryTextColor)
            setImageViewBitmap(R.id.notificationFloatingIcon, buildNotificationIconBitmap(android.R.drawable.ic_menu_view, primaryTextColor))
            setTextViewText(R.id.notificationFloatingLabel, overlayLabel)
            setImageViewBitmap(R.id.notificationFloatingSwitch, buildNotificationToggleBitmap(floatingOverlayEnabled))
            setContentDescription(R.id.notificationFloatingSwitch, if (floatingOverlayEnabled) "Floating on" else "Floating off")
            val floatingToggleIntent = serviceActionPendingIntent(ACTION_NOTIFICATION_TOGGLE_OVERLAY, 9101)
            setOnClickPendingIntent(R.id.notificationFloatingSwitch, floatingToggleIntent)

            setImageViewBitmap(
                R.id.notificationPauseIcon,
                buildNotificationIconBitmap(
                    if (audioActive) R.drawable.ic_notification_stop else android.R.drawable.ic_media_play,
                    primaryTextColor
                )
            )
            setTextViewText(R.id.notificationPauseLabel, audioLabel)
            val audioAction = if (audioActive) ACTION_NOTIFICATION_STOP_AUDIO else ACTION_NOTIFICATION_START_AUDIO
            val audioRequestCode = if (audioActive) 9102 else 9105
            setOnClickPendingIntent(
                R.id.notificationPauseRow,
                serviceActionPendingIntent(audioAction, audioRequestCode)
            )

            setImageViewBitmap(R.id.notificationEqIcon, buildNotificationIconBitmap(android.R.drawable.ic_media_ff, primaryTextColor))
            setTextViewText(R.id.notificationEqLabel, localizedServiceText("EQ Engine"))
            setImageViewBitmap(R.id.notificationEqSwitch, buildNotificationToggleBitmap(eqActive))
            setContentDescription(R.id.notificationEqSwitch, if (eqActive) "Equalizer on" else "Equalizer off")
            setOnClickPendingIntent(
                R.id.notificationEqSwitch,
                serviceActionPendingIntent(ACTION_NOTIFICATION_TOGGLE_EQ, 9104)
            )

            setImageViewBitmap(R.id.notificationProjectionIcon, buildNotificationIconBitmap(android.R.drawable.ic_menu_manage, primaryTextColor))
            setTextViewText(R.id.notificationProjectionLabel, backupLabel)
            setImageViewBitmap(R.id.notificationProjectionSwitch, buildNotificationToggleBitmap(backupActive))
            setContentDescription(R.id.notificationProjectionSwitch, if (backupActive) "MediaProjection on" else "MediaProjection off")
            val projectionToggleIntent = backupTogglePendingIntent()
            setOnClickPendingIntent(R.id.notificationProjectionSwitch, projectionToggleIntent)

        }

        return builder
            .setContentTitle(localizedServiceText("Floating DJ Visualizer"))
            .setContentText(compactSummary)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Custom controls are ordinary notification actions, not a MediaSession.
            // The media-style icon is used only for the independent Start/Stop Visualizer row.
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(compactView)
            .setCustomBigContentView(expandedView)
            .build()
    }

    private fun refreshNotification(message: String) {
        if (!foregroundStarted) return
        createNotificationChannel()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        foregroundStarted = false
        audioPaused = false
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
            .putBoolean("service_paused", false)
            .apply()
    }

    // -----------------------------------------------------------------
    // SETTINGS
    // -----------------------------------------------------------------

    private fun serviceFormatFloatingProfiles(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return try {
            val array = org.json.JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val savedColors = item.optJSONArray("colors") ?: org.json.JSONArray()
                val runtimeColors = org.json.JSONArray()
                for (colorIndex in 0 until savedColors.length()) {
                    val value = savedColors.opt(colorIndex)
                    val parsed = when (value) {
                        is Number -> value.toInt()
                        is String -> try { Color.parseColor(value) } catch (_: Exception) { Color.WHITE }
                        else -> Color.WHITE
                    }
                    runtimeColors.put(parsed)
                }
                item.put("colors", runtimeColors)
            }
            array.toString()
        } catch (error: Exception) {
            android.util.Log.e("FloatingDJ", "Unable to restore saved floating profile JSON", error)
            ""
        }
    }

    private fun readSettings(intent: Intent) {
        // Hydrate every setting from durable preferences first. Notification
        // PendingIntents contain only an action, so without this step a new
        // Service instance would render its constructor defaults (white/horizontal).
        val saved = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        isVerticalMode = saved.getBoolean("vertical", isVerticalMode)
        widthDp = saved.getInt("width", widthDp).coerceIn(0, 4096)
        heightDp = saved.getInt("height", heightDp).coerceIn(0, 4096)
        barCount = saved.getInt("bar_count", barCount).coerceIn(1, MAX_BAR_COUNT)
        barWidthDp = saved.getInt("bar_width", barWidthDp).coerceIn(0, 2048)
        gapDp = saved.getInt("bar_gap", gapDp).coerceIn(0, 512)
        scalePercent = saved.getInt("scale", scalePercent).coerceIn(0, 500)
        opacityPercent = saved.getInt("opacity", opacityPercent).coerceIn(0, 100)
        shadowOpacityPercent = saved.getInt("shadow_opacity", shadowOpacityPercent).coerceIn(0, 100)
        glowPercent = saved.getInt("glow", glowPercent).coerceIn(0, 100)
        responsePercent = saved.getInt("response_speed", responsePercent).coerceIn(0, 100)
        fftSizeSetting = normalizeFftSize(saved.getInt("fft_size", fftSizeSetting))
        hopSizeSetting = normalizeHopSize(saved.getInt("hop_size", hopSizeSetting), fftSizeSetting)
        cornerRadiusDp = saved.getInt("corner_radius", cornerRadiusDp).coerceIn(0, 512)
        effectStyle = saved.getString("bar_effect", effectStyle) ?: effectStyle
        floatingOverlayEnabled = saved.getBoolean("floating_overlay_enabled", floatingOverlayEnabled)
        floatingProfilesJson = serviceFormatFloatingProfiles(saved.getString("floating_profiles", null))
        singleAudioMode = saved.getString("single_audio_mode", singleAudioMode) ?: singleAudioMode
        bassMinHz = saved.getInt("bass_min_hz", bassMinHz).coerceIn(0, 20000)
        bassMaxHz = saved.getInt("bass_max_hz", bassMaxHz).coerceIn(0, 20000)
        horizontalAlignment = saved.getString("horizontal_alignment", horizontalAlignment)
            ?.takeIf { it in setOf("LEFT", "CENTER", "RIGHT") } ?: horizontalAlignment
        mirrorMode = saved.getString("horizontal_mirror_mode", null)
            ?.takeIf { it in setOf("OFF", "VERTICAL", "HORIZONTAL") }
            ?: if (saved.getBoolean("horizontal_mirror", false)) "HORIZONTAL" else "OFF"
        positionXdp = saved.getInt("position_x", positionXdp).coerceAtLeast(0)
        positionYdp = saved.getInt("position_y", positionYdp).coerceAtLeast(0)
        positionMode = saved.getString("position_mode", positionMode) ?: positionMode
        rotationDegrees = saved.getInt("rotation_degrees", rotationDegrees).coerceIn(0, 359)
        val savedColors = saved.getString("colors", null)
        if (!savedColors.isNullOrBlank()) {
            val parsed = savedColors.split(',').mapNotNull { token ->
                try { Color.parseColor(token.trim()) } catch (_: Exception) { null }
            }
            if (parsed.size >= 2) spectrumColors = parsed.toIntArray()
        }

        // Explicit configuration values from a normal MainActivity start/update
        // always take precedence over the persisted recovery snapshot.
        isVerticalMode = intent.getBooleanExtra(EXTRA_VERTICAL, isVerticalMode)
        widthDp = intent.getIntExtra(EXTRA_WIDTH, widthDp).coerceIn(0, 4096)
        heightDp = intent.getIntExtra(EXTRA_HEIGHT, heightDp).coerceIn(0, 4096)
        barCount = intent.getIntExtra(EXTRA_BAR_COUNT, barCount).coerceIn(1, MAX_BAR_COUNT)
        barWidthDp = intent.getIntExtra(EXTRA_BAR_WIDTH, barWidthDp).coerceIn(0, 2048)
        gapDp = intent.getIntExtra(EXTRA_GAP, gapDp).coerceIn(0, 512)
        scalePercent = intent.getIntExtra(EXTRA_SCALE, scalePercent).coerceIn(0, 500)
        opacityPercent = intent.getIntExtra(EXTRA_OPACITY, opacityPercent).coerceIn(0, 100)
        shadowOpacityPercent = intent.getIntExtra(EXTRA_SHADOW_OPACITY, shadowOpacityPercent).coerceIn(0, 100)
        glowPercent = intent.getIntExtra(EXTRA_GLOW, glowPercent).coerceIn(0, 100)
        responsePercent = intent.getIntExtra(EXTRA_RESPONSE, responsePercent).coerceIn(0, 100)
        fftSizeSetting = normalizeFftSize(intent.getIntExtra(EXTRA_FFT_SIZE, fftSizeSetting))
        hopSizeSetting = normalizeHopSize(intent.getIntExtra(EXTRA_HOP_SIZE, hopSizeSetting), fftSizeSetting)
        cornerRadiusDp = intent.getIntExtra(EXTRA_CORNER_RADIUS, cornerRadiusDp).coerceIn(0, 512)
        intent.getIntArrayExtra(EXTRA_COLORS)?.takeIf { it.size >= 2 }?.let { spectrumColors = it.copyOf() }
        effectStyle = intent.getStringExtra(EXTRA_EFFECT) ?: effectStyle
        floatingOverlayEnabled = intent.getBooleanExtra(EXTRA_FLOATING_ENABLED, floatingOverlayEnabled)
        intent.getStringExtra(EXTRA_FLOATING_PROFILES_JSON)?.let { floatingProfilesJson = it }
        singleAudioMode = intent.getStringExtra(EXTRA_SINGLE_MODE) ?: singleAudioMode
        bassMinHz = intent.getIntExtra(EXTRA_BASS_MIN_HZ, bassMinHz).coerceIn(0, 20000)
        bassMaxHz = intent.getIntExtra(EXTRA_BASS_MAX_HZ, bassMaxHz).coerceIn(0, 20000)
        horizontalAlignment = intent.getStringExtra(EXTRA_HORIZONTAL_ALIGNMENT)
            ?.takeIf { it in setOf("LEFT", "CENTER", "RIGHT") } ?: horizontalAlignment
        val legacyMirror = intent.getBooleanExtra(EXTRA_MIRROR, mirrorMode != "OFF")
        mirrorMode = intent.getStringExtra(EXTRA_MIRROR_MODE)
            ?.takeIf { it in setOf("OFF", "VERTICAL", "HORIZONTAL") }
            ?: if (legacyMirror) "HORIZONTAL" else "OFF"
        val requestedPositionX = intent.getIntExtra(EXTRA_POSITION_X, positionXdp).coerceAtLeast(0)
        val requestedPositionY = intent.getIntExtra(EXTRA_POSITION_Y, positionYdp).coerceAtLeast(0)
        if (requestedPositionX != positionXdp || requestedPositionY != positionYdp) {
            positionXdp = requestedPositionX
            positionYdp = requestedPositionY
            overlayCenterXpx = null
            overlayCenterYpx = null
        }
        positionMode = intent.getStringExtra(EXTRA_POSITION_MODE) ?: positionMode
        rotationDegrees = intent.getIntExtra(EXTRA_ROTATION, rotationDegrees).coerceIn(0, 359)
    }

    private fun applyOverlayVisibility() {
        // Dialogs and preset galleries never suppress the floating window.
        // Visibility follows the master Floating switch, engine state, and per-layer visibility only.
        val shouldDisplay = floatingOverlayEnabled && engineSource != EngineSource.NONE
        windowView?.visibility = if (shouldDisplay) View.VISIBLE else View.INVISIBLE
        layeredRuntime.forEach { runtime ->
            runtime.host.visibility = if (shouldDisplay && runtime.config.visible) View.VISIBLE else View.INVISIBLE
        }
    }

    // -----------------------------------------------------------------
    // PRIMARY ENGINE: ANDROID VISUALIZER, SESSION 0
    // -----------------------------------------------------------------

    private fun startPrimaryEngineInternal(): Boolean {
        stopping = false
        pendingSpectrumFrame.set(null)
        releaseVisualizer()
        stopProjectionResources()

        var candidate: Visualizer? = null

        return try {
            val engine = Visualizer(0)
            candidate = engine
            visualizerResolutionLogged = false

            val range = Visualizer.getCaptureSizeRange()
            if (range.size < 2 || range[0] <= 0 || range[1] < range[0]) {
                throw IllegalStateException("Visualizer capture-size range tidak valid")
            }

            // Visualizer capture size must be a power of two inside its reported range.
            val preferredCaptureSize = fftSizeSetting
            val cappedMaximum = min(preferredCaptureSize, range[1])
            var captureSize = Integer.highestOneBit(cappedMaximum)
            while (captureSize < range[0] && captureSize > 0) {
                captureSize = captureSize shl 1
            }
            if (captureSize <= 0 || captureSize !in range[0]..range[1]) {
                throw IllegalStateException("Tidak ada capture size power-of-two yang valid pada range ${range[0]}..${range[1]}")
            }
            val captureSizeStatus = engine.setCaptureSize(captureSize)
            if (captureSizeStatus != Visualizer.SUCCESS) {
                throw IllegalStateException("Gagal mengatur capture size ($captureSizeStatus)")
            }

            val maxRate = Visualizer.getMaxCaptureRate()
            if (maxRate <= 0) throw IllegalStateException("Visualizer melaporkan capture rate maksimum tidak valid: $maxRate")
            // Android's Visualizer callback rate is expressed in milliHertz. The
            // platform/AOSP implementation commonly caps this at 20,000 mHz (20 Hz).
            // Drawing at 60/90/120 Hz can interpolate between samples, but cannot
            // create new audio measurements between those callback frames.
            val rate = min(maxRate, 20000)
            val sampleRateHz = (engine.samplingRate / 1000.0).takeIf { it > 1000.0 } ?: 44100.0
            val binHz = sampleRateHz / captureSize.toDouble()
            val windowMs = captureSize * 1000.0 / sampleRateHz
            android.util.Log.i(
                "FloatingDJ.Audio",
                "Visualizer requestedFft=$preferredCaptureSize, appliedCaptureSize=$captureSize, binResolution=${String.format(java.util.Locale.US, "%.2f", binHz)}Hz, " +
                    "window=${String.format(java.util.Locale.US, "%.1f", windowMs)}ms, " +
                    "requestedRate=${rate / 1000.0}Hz, mappedSpectrum=${MIN_SPECTRUM_HZ.toInt()}–${MAX_SPECTRUM_HZ.toInt()}Hz"
            )
            var lastFftCallbackNanos = 0L
            var averageCallbackIntervalMs = 0.0
            var lastCallbackDiagnosticNanos = 0L
            val listenerStatus = engine.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        // Tidak digunakan; visualisasi memakai FFT.
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.isEmpty()) return
                        val callbackNanos = System.nanoTime()
                        if (lastFftCallbackNanos != 0L) {
                            val intervalMs = (callbackNanos - lastFftCallbackNanos) / 1_000_000.0
                            averageCallbackIntervalMs = if (averageCallbackIntervalMs == 0.0) intervalMs
                                else averageCallbackIntervalMs * 0.80 + intervalMs * 0.20
                        }
                        lastFftCallbackNanos = callbackNanos
                        val calculationStartNanos = System.nanoTime()
                        val levels = calculateVisualizerSpectrum(fft, samplingRate)
                        val calculationMs = (System.nanoTime() - calculationStartNanos) / 1_000_000.0
                        publishLevels(levels)
                        if (lastCallbackDiagnosticNanos == 0L || callbackNanos - lastCallbackDiagnosticNanos >= 5_000_000_000L) {
                            lastCallbackDiagnosticNanos = callbackNanos
                            val deliveredHz = if (averageCallbackIntervalMs > 0.0) 1000.0 / averageCallbackIntervalMs else 0.0
                            android.util.Log.i(
                                "FloatingDJ.Audio",
                                "FFT callback=${String.format(java.util.Locale.US, "%.1f", deliveredHz)}Hz, " +
                                    "avgInterval=${String.format(java.util.Locale.US, "%.1f", averageCallbackIntervalMs)}ms, " +
                                    "analysis=${String.format(java.util.Locale.US, "%.2f", calculationMs)}ms"
                            )
                        }
                    }
                },
                rate,
                false,
                true
            )

            if (listenerStatus != Visualizer.SUCCESS) {
                throw IllegalStateException("Gagal memasang FFT listener ($listenerStatus)")
            }

            val enableStatus = engine.setEnabled(true)
            if (enableStatus != Visualizer.SUCCESS) {
                throw IllegalStateException("Visualizer tidak bisa diaktifkan ($enableStatus)")
            }

            visualizer = engine
            candidate = null // Ownership berpindah ke property visualizer.
            engineSource = EngineSource.VISUALIZER

            updateFloatingWindow()
            setServiceActive(true)
            // Restore the independently owned EQ service only after the host
            // service has persisted its active state, so its notification refresh
            // sees the correct Start/Stop and Floating settings.
            restoreEqEngineFromPreferencesIfEnabled()
            broadcastStatus("PRIMARY_ACTIVE: Visualizer output-mix is active")
            refreshNotification("System audio visualizer active")
            true
        } catch (e: Exception) {
            android.util.Log.e("FloatingDJ", "Unable to start Visualizer(0)", e)

            candidate?.let { failedEngine ->
                try { failedEngine.setEnabled(false) } catch (_: Exception) {}
                try { failedEngine.setDataCaptureListener(null, 0, false, false) } catch (_: Exception) {}
                try { failedEngine.release() } catch (_: Exception) {}
            }

            releaseVisualizer()
            engineSource = EngineSource.NONE
            // The service remains foreground and can still accept a user-approved
            // MediaProjection backup request from the app switch.
            setServiceActive(true)
            broadcastStatus("PRIMARY_ERROR: ${e.message ?: "Visualizer output-mix could not initialize"}. Check RECORD_AUDIO and MODIFY_AUDIO_SETTINGS; some device audio HALs do not expose session 0.")
            refreshNotification("Primary audio unavailable · backup ready")
            false
        }
    }

    /**
     * Notification Start can recreate the host service after Android reclaimed
     * the app process. Restore the separately-owned EQ effect service only when
     * the user's persisted EQ switch was ON; never force-enable EQ implicitly.
     */
    private fun restoreEqEngineFromPreferencesIfEnabled() {
        val eqPrefs = getSharedPreferences("eq_presets_v1", MODE_PRIVATE)
        if (!eqPrefs.getBoolean("engine_enabled", false)) return
        try {
            val eqIntent = Intent(this, com.floatingdj.visualizer.eq.service.EqEngineService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(eqIntent) else startService(eqIntent)
        } catch (error: Throwable) {
            android.util.Log.e("FloatingDJ", "Unable to restore the previously enabled EQ engine", error)
            broadcastStatus("EQ_RESTORE_ERROR: ${error.message ?: "Saved EQ engine could not be restored"}")
        }
    }

    private fun releaseVisualizer() {
        val old = visualizer
        visualizer = null
        if (old != null) {
            try { old.setEnabled(false) } catch (_: Exception) {}
            try { old.setDataCaptureListener(null, 0, false, false) } catch (_: Exception) {}
            try { old.release() } catch (_: Exception) {}
        }
    }

    private fun calculateVisualizerSpectrum(fft: ByteArray, samplingRateMilliHz: Int): FloatArray {
        // A fixed 64-band logarithmic bank gives every rendered bar a stable,
        // independent frequency map regardless of the selected bar count.
        val result = FloatArray(MAX_BAR_COUNT + 2)
        // Android packs the FFT as: DC real at [0], Nyquist real at [1],
        // then Re/Im pairs for bins 1..N/2-1. DC and Nyquist are NOT a pair.
        val nyquistBin = (fft.size / 2).coerceAtLeast(1)
        val sampleRate = (samplingRateMilliHz / 1000.0).takeIf { it > 1000.0 } ?: 44100.0
        if (!visualizerResolutionLogged) {
            visualizerResolutionLogged = true
            android.util.Log.i(
                "FloatingDJ.Audio",
                "FFT resolution=${String.format(java.util.Locale.US, "%.2f", sampleRate / fft.size)}Hz/bin, " +
                    "captureSize=${fft.size}, window=${String.format(java.util.Locale.US, "%.1f", fft.size * 1000.0 / sampleRate)}ms, " +
                    "spectrumFloor=${MIN_SPECTRUM_HZ.toInt()}Hz"
            )
        }
        val nyquist = sampleRate / 2.0

        fun magnitudeAtBin(bin: Int): Double {
            val safeBin = bin.coerceIn(0, nyquistBin)
            if (safeBin == 0) return abs(fft[0].toInt().toDouble())
            if (safeBin == nyquistBin) return abs(fft[1].toInt().toDouble())
            val index = safeBin * 2
            if (index + 1 >= fft.size) return 0.0
            val real = fft[index].toInt().toDouble()
            val imag = fft[index + 1].toInt().toDouble()
            return sqrt(real * real + imag * imag)
        }

        fun interpolatedMagnitude(hz: Double): Double {
            val position = (hz / nyquist * nyquistBin).coerceIn(0.0, nyquistBin.toDouble())
            val lower = floor(position).toInt().coerceIn(0, nyquistBin)
            val upper = (lower + 1).coerceAtMost(nyquistBin)
            val fraction = position - lower
            return magnitudeAtBin(lower) * (1.0 - fraction) + magnitudeAtBin(upper) * fraction
        }

        val spectrumMinHz = MIN_SPECTRUM_HZ
        val spectrumMaxHz = min(MAX_SPECTRUM_HZ, nyquist * 0.995).coerceAtLeast(spectrumMinHz + 1.0)
        val ratio = spectrumMaxHz / spectrumMinHz
        var power = 0.0
        for (band in 0 until MAX_BAR_COUNT) {
            val lowHz = spectrumMinHz * ratio.pow(band.toDouble() / MAX_BAR_COUNT)
            val highHz = spectrumMinHz * ratio.pow((band + 1).toDouble() / MAX_BAR_COUNT)
            val magnitude = measureLogBandMagnitude(lowHz, highHz, ::interpolatedMagnitude)
            val raw = (magnitude / 100.0).coerceIn(0.0, 1.0)
            val target = sqrt(raw).toFloat().coerceIn(0f, 1f)
            result[band] = target
            power += target.toDouble() * target.toDouble()
        }

        val lowBassHz = min(bassMinHz, bassMaxHz).toDouble()
        val highBassHz = max(bassMinHz, bassMaxHz).toDouble()
        val binHz = sampleRate / fft.size.toDouble()
        // 0–0 remains the documented full-range mode. Otherwise skip DC (not
        // an audible bass tone) and select the nearest available positive bins.
        val fullRange = lowBassHz == 0.0 && highBassHz == 0.0
        val firstBass = if (fullRange) 1 else
            ceil(lowBassHz / binHz).toInt().coerceIn(1, (nyquistBin - 1).coerceAtLeast(1))
        val lastBass = if (fullRange) (nyquistBin - 1).coerceAtLeast(firstBass) else
            floor(highBassHz / binHz).toInt().coerceIn(firstBass, (nyquistBin - 1).coerceAtLeast(firstBass))
        var bassPeak = 0.0
        for (bin in firstBass..lastBass) bassPeak = max(bassPeak, magnitudeAtBin(bin))
        result[MAX_BAR_COUNT] = (bassPeak / 100.0).coerceIn(0.0, 1.0).pow(0.58).toFloat()

        result[MAX_BAR_COUNT + 1] = sqrt(power / MAX_BAR_COUNT.toDouble()).toFloat().coerceIn(0f, 1f)
        return result
    }

    /** Samples several fractional FFT positions inside one logarithmic band.
     * Fractional interpolation avoids assigning the exact same coarse FFT bin
     * to multiple neighbouring low-frequency bars on devices with small FFTs.
     */
    private fun measureLogBandMagnitude(
        lowHz: Double,
        highHz: Double,
        sampleAtHz: (Double) -> Double
    ): Double {
        val safeLow = lowHz.coerceAtLeast(1.0)
        val safeHigh = highHz.coerceAtLeast(safeLow * 1.0001)
        val ratio = safeHigh / safeLow
        var power = 0.0
        var peak = 0.0
        val sampleCount = 5
        for (i in 0 until sampleCount) {
            val position = (i + 0.5) / sampleCount.toDouble()
            val hz = safeLow * ratio.pow(position)
            val magnitude = sampleAtHz(hz).coerceAtLeast(0.0)
            power += magnitude * magnitude
            peak = max(peak, magnitude)
        }
        val rms = sqrt(power / sampleCount.toDouble())
        return max(rms, peak * 0.72)
    }

    private fun smooth(previous: Float, target: Float, attack: Float, release: Float): Float {
        val factor = if (target >= previous) attack else release
        return previous + (target - previous) * factor
    }

    // -----------------------------------------------------------------
    // BACKUP ENGINE: MEDIAPROJECTION AUDIO PLAYBACK CAPTURE (ANDROID 10+)
    // -----------------------------------------------------------------

    private fun buildProjectionAudioRecorder(mp: MediaProjection): AudioRecord {
        val config = AudioPlaybackCaptureConfiguration.Builder(mp)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) throw IllegalStateException("AudioRecord tidak mendukung format yang diminta")
        val recorder = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(minBuffer, fftSizeSetting * 4))
            .setAudioPlaybackCaptureConfig(config)
            .build()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Audio playback capture gagal diinisialisasi")
        }
        return recorder
    }

    private fun logProjectionAnalysisSettings() {
        val fft = fftSizeSetting
        val hop = hopSizeSetting
        android.util.Log.i(
            "FloatingDJ.Audio",
            "MediaProjection FFT size=$fft, hop=$hop, " +
                "window=${String.format(java.util.Locale.US, "%.1f", fft * 1000.0 / SAMPLE_RATE)}ms, " +
                "hopInterval=${String.format(java.util.Locale.US, "%.1f", hop * 1000.0 / SAMPLE_RATE)}ms, " +
                "resolution=${String.format(java.util.Locale.US, "%.2f", SAMPLE_RATE.toDouble() / fft)}Hz/bin, " +
                "overlap=${String.format(java.util.Locale.US, "%.1f", (1.0 - hop.toDouble() / fft) * 100.0)}%"
        )
    }

    private fun startBackupEngine(resultCode: Int, resultData: Intent): Boolean {
        if (Build.VERSION.SDK_INT < 29) throw IllegalStateException("MediaProjection audio capture membutuhkan Android 10+")
        stopping = false
        pendingSpectrumFrame.set(null)
        switchingSource = true
        releaseVisualizer()
        stopProjectionResources()
        engineSource = EngineSource.NONE
        switchingSource = false

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = manager.getMediaProjection(resultCode, resultData)
            ?: throw IllegalStateException("MediaProjection tidak tersedia")
        projection = mp
        mp.registerCallback(projectionCallback, mainHandler)
        ensurePcmFftWorkspace()
        val recorder = buildProjectionAudioRecorder(mp)
        audioRecord = recorder
        recorder.startRecording()
        logProjectionAnalysisSettings()
        engineSource = EngineSource.PROJECTION
        updateFloatingWindow()
        startBackupThread()
        setServiceActive(true)
        broadcastStatus("BACKUP_ACTIVE: MediaProjection audio capture is active")
        refreshNotification("MediaProjection backup audio active")
        return true
    }

    private fun restartProjectionCaptureForAnalysisSettings(): Boolean {
        val mp = projection ?: return false
        return try {
            // Stop and join the old PCM thread before reallocating FFT work buffers.
            releaseAudioRecordOnly()
            ensurePcmFftWorkspace()
            val recorder = buildProjectionAudioRecorder(mp)
            audioRecord = recorder
            recorder.startRecording()
            logProjectionAnalysisSettings()
            engineSource = EngineSource.PROJECTION
            startBackupThread()
            refreshNotification("MediaProjection analysis settings updated")
            true
        } catch (error: Exception) {
            android.util.Log.e("FloatingDJ.Audio", "Unable to reconfigure MediaProjection FFT", error)
            releaseAudioRecordOnly()
            false
        }
    }

    private fun startBackupThread() {
        ensurePcmFftWorkspace()
        val fftSize = fftSizeSetting
        val hopSize = normalizeHopSize(hopSizeSetting, fftSize)
        hopSizeSetting = hopSize
        backupRunning = true
        val recorder = audioRecord ?: return
        captureThread = Thread {
            val frame = ShortArray(fftSize)
            val chunk = ShortArray(hopSize)
            var filled = 0
            while (backupRunning) {
                val read = try {
                    recorder.read(chunk, 0, hopSize, AudioRecord.READ_BLOCKING)
                } catch (e: Exception) {
                    android.util.Log.e("FloatingDJ", "Backup read failed", e)
                    -1
                }
                if (read <= 0) break
                if (read > fftSize) continue
                System.arraycopy(frame, read, frame, 0, fftSize - read)
                System.arraycopy(chunk, 0, frame, fftSize - read, read)
                filled = min(fftSize, filled + read)
                if (filled < fftSize) continue
                val levels = calculatePcmSpectrum(frame, fftSize)
                publishLevels(levels)
            }
            if (backupRunning && !stopping) {
                mainHandler.post {
                    if (engineSource == EngineSource.PROJECTION) {
                        broadcastStatus("BACKUP_ERROR: Audio playback capture stream ended")
                        switchingSource = true
                        stopProjectionResources()
                        switchingSource = false
                        startPrimaryForeground()
                        val started = startPrimaryEngineInternal()
                        if (!started) setServiceActive(false)
                    }
                }
            }
        }.apply {
            name = "FloatingDJ-ProjectionAudio"
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    private fun calculatePcmSpectrum(samples: ShortArray, fftSize: Int): FloatArray {
        val real = pcmFftReal
        val imag = pcmFftImag
        val window = pcmHannWindow
        real.fill(0.0)
        imag.fill(0.0)
        for (i in 0 until fftSize) {
            val sample = samples[i].toDouble() / 32768.0
            real[i] = sample * window[i]
        }
        var j = 0
        for (i in 1 until fftSize) {
            var bit = fftSize shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var length = 2
        while (length <= fftSize) {
            val angle = -2.0 * PI / length
            val rootR = cos(angle)
            val rootI = sin(angle)
            var start = 0
            while (start < fftSize) {
                var wr = 1.0
                var wi = 0.0
                for (k in 0 until length / 2) {
                    val even = start + k
                    val odd = even + length / 2
                    val vr = real[odd] * wr - imag[odd] * wi
                    val vi = real[odd] * wi + imag[odd] * wr
                    real[odd] = real[even] - vr
                    imag[odd] = imag[even] - vi
                    real[even] += vr
                    imag[even] += vi
                    val nextWr = wr * rootR - wi * rootI
                    wi = wr * rootI + wi * rootR
                    wr = nextWr
                }
                start += length
            }
            length *= 2
        }

        val result = FloatArray(MAX_BAR_COUNT + 2)
        val nyquist = SAMPLE_RATE / 2.0
        val nyquistBin = fftSize / 2
        fun magnitudeAtBin(bin: Int): Double {
            val safe = bin.coerceIn(0, nyquistBin)
            return sqrt(real[safe] * real[safe] + imag[safe] * imag[safe])
        }
        fun interpolatedMagnitude(hz: Double): Double {
            val position = (hz / nyquist * nyquistBin).coerceIn(0.0, nyquistBin.toDouble())
            val lower = floor(position).toInt().coerceIn(0, nyquistBin)
            val upper = (lower + 1).coerceAtMost(nyquistBin)
            val fraction = position - lower
            return magnitudeAtBin(lower) * (1.0 - fraction) + magnitudeAtBin(upper) * fraction
        }

        val spectrumMinHz = MIN_SPECTRUM_HZ
        val spectrumMaxHz = min(MAX_SPECTRUM_HZ, nyquist * 0.995).coerceAtLeast(spectrumMinHz + 1.0)
        val ratio = spectrumMaxHz / spectrumMinHz
        var power = 0.0
        for (band in 0 until MAX_BAR_COUNT) {
            val low = spectrumMinHz * ratio.pow(band.toDouble() / MAX_BAR_COUNT)
            val high = spectrumMinHz * ratio.pow((band + 1).toDouble() / MAX_BAR_COUNT)
            val magnitude = measureLogBandMagnitude(low, high, ::interpolatedMagnitude)
            val target = sqrt((magnitude / fftSize * 9.0).coerceIn(0.0, 1.0)).toFloat().coerceIn(0f, 1f)
            result[band] = target
            power += target.toDouble() * target.toDouble()
        }

        val lowBassHz = min(bassMinHz, bassMaxHz).toDouble()
        val highBassHz = max(bassMinHz, bassMaxHz).toDouble()
        val binHz = SAMPLE_RATE.toDouble() / fftSize.toDouble()
        val fullRange = lowBassHz == 0.0 && highBassHz == 0.0
        val firstBass = if (fullRange) 1 else
            ceil(lowBassHz / binHz).toInt().coerceIn(1, (nyquistBin - 1).coerceAtLeast(1))
        val lastBass = if (fullRange) (nyquistBin - 1).coerceAtLeast(firstBass) else
            floor(highBassHz / binHz).toInt().coerceIn(firstBass, (nyquistBin - 1).coerceAtLeast(firstBass))
        var bassPeak = 0.0
        for (bin in firstBass..lastBass) bassPeak = max(bassPeak, magnitudeAtBin(bin))
        result[MAX_BAR_COUNT] = (bassPeak / fftSize * 12.0).coerceIn(0.0, 1.0).pow(0.60).toFloat()
        result[MAX_BAR_COUNT + 1] = sqrt(power / MAX_BAR_COUNT.toDouble()).toFloat().coerceIn(0f, 1f)
        return result
    }

    private fun releaseAudioRecordOnly() {
        backupRunning = false
        val recorder = audioRecord
        audioRecord = null
        val thread = captureThread
        if (recorder != null) {
            try { recorder.stop() } catch (_: Exception) {}
        }
        if (thread != null && thread !== Thread.currentThread()) {
            try { thread.join(500L) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        captureThread = null
        if (recorder != null) {
            try { recorder.release() } catch (_: Exception) {}
        }
    }

    private fun stopProjectionResources() {
        releaseAudioRecordOnly()
        val oldProjection = projection
        projection = null
        if (oldProjection != null) {
            try { oldProjection.unregisterCallback(projectionCallback) } catch (_: Exception) {}
            try { oldProjection.stop() } catch (_: Exception) {}
        }
        if (engineSource == EngineSource.PROJECTION) engineSource = EngineSource.NONE
    }

    private fun stopEngineResources() {
        stopping = true
        pendingSpectrumFrame.set(null)
        cancelSpectrumAnimationFrame()
        releaseVisualizer()
        stopProjectionResources()
        engineSource = EngineSource.NONE
    }

    // -----------------------------------------------------------------
    // LEVEL DELIVERY
    // -----------------------------------------------------------------

    private fun publishLevels(levels: FloatArray) {
        pendingSpectrumFrame.set(levels.copyOf())
        scheduleLatestSpectrumDelivery()
    }

    private fun scheduleLatestSpectrumDelivery() {
        if (!spectrumDeliveryScheduled.compareAndSet(false, true)) return
        mainHandler.post {
            try {
                val latest = pendingSpectrumFrame.getAndSet(null)
                if (latest != null && engineSource != EngineSource.NONE && !stopping) {
                    updateFloatingBars(latest)
                    layeredRuntime.forEach { runtime ->
                        if (runtime.config.visible) runtime.spectrum.setSpectrum(latest)
                    }
                    requestSpectrumAnimationFrame()
                    // Broadcast on the main queue after coalescing, so MainActivity
                    // receives the newest spectrum instead of accumulating stale frames.
                    val update = Intent(ACTION_LEVELS)
                        .setPackage(packageName)
                        .putExtra(EXTRA_LEVELS, latest)
                    sendBroadcast(update)
                }
            } finally {
                spectrumDeliveryScheduled.set(false)
                // A callback may have replaced the pending frame while this runnable
                // was executing. Schedule one more pass, always consuming the newest one.
                if (pendingSpectrumFrame.get() != null) scheduleLatestSpectrumDelivery()
            }
        }
    }

    private fun broadcastStatus(status: String) {
        val update = Intent(ACTION_ENGINE_STATUS)
        .setPackage(packageName)
        .putExtra(EXTRA_ENGINE_STATUS, status)
        sendBroadcast(update)
    }

    private fun setServiceActive(active: Boolean) {
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        .edit().putBoolean("service_active", active).apply()
    }

    // -----------------------------------------------------------------
    // TRANSPARENT OVERLAY / CUTOUT SUPPORT
    // -----------------------------------------------------------------

    private fun showFloatingWindow() {
        if (!floatingOverlayEnabled || !Settings.canDrawOverlays(this)) {
            removeOverlay()
            if (floatingOverlayEnabled && !Settings.canDrawOverlays(this)) {
                floatingOverlayEnabled = false
                getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
                    .putBoolean("floating_overlay_enabled", false)
                    .apply()
                broadcastStatus("OVERLAY_PERMISSION_REQUIRED: Overlay permission was revoked")
            }
            return
        }
        removeOverlay()
        val host = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }
        val content = LinearLayout(this).apply {
            orientation = if (isVerticalMode) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = contentGravity()
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            clipChildren = false
            clipToPadding = false
        }
        addFloatingBars(content)
        host.addView(content)

        val (contentW, contentH) = localContentSizePx()
        val (boundW, boundH) = rotatedBoundsPx(contentW, contentH)
        val params = WindowManager.LayoutParams(
            boundW,
            boundH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.LEFT
        configureOverlayInsets(params)
        val density = resources.displayMetrics.density
        params.x = (positionXdp * density).toInt()
        params.y = (positionYdp * density).toInt()
        overlayCenterXpx = params.x + boundW / 2
        overlayCenterYpx = params.y + boundH / 2

        windowView = host
        contentView = content
        overlayParams = params
        try {
            host.visibility = View.VISIBLE
            windowManager.addView(host, params)
            applyFloatingDimensions()
            applyOverlayVisibility()
        } catch (e: Exception) {
            android.util.Log.e("FloatingDJ", "Overlay creation failed", e)
            removeOverlay()
            // Overlay is optional: a WindowManager failure must not tear down
            // the working audio engine or its foreground service.
            broadcastStatus("OVERLAY_ERROR: Floating window could not be attached: ${e.message ?: "WindowManager error"}")
        }
    }

    private fun contentGravity(): Int {
        // The whole spectrum always stays centered inside the overlay bounds.
        // Flow direction changes only which frequency feeds each bar.
        return Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
    }

    private fun localContentSizePx(): Pair<Int, Int> {
        val density = resources.displayMetrics.density
        val localWidth = if (isVerticalMode) barWidthDp else widthDp
        var w = (localWidth * scalePercent / 100f * density).toInt().coerceAtLeast(dp(3))
        var h = (heightDp * scalePercent / 100f * density).toInt().coerceAtLeast(dp(3))
        val (screenW, screenH) = displaySizePx()
        val r = Math.toRadians(rotationDegrees.toDouble())
        val c = abs(cos(r)); val s = abs(sin(r))
        val rotatedW = w * c + h * s
        val rotatedH = w * s + h * c
        val fit = min(1.0, min(screenW / rotatedW, screenH / rotatedH)).toFloat()
        if (fit < 1f) { w = (w * fit).toInt().coerceAtLeast(dp(3)); h = (h * fit).toInt().coerceAtLeast(dp(3)) }
        return Pair(w, h)
    }

    private fun rotatedBoundsPx(width: Int, height: Int): Pair<Int, Int> {
        val r = Math.toRadians(rotationDegrees.toDouble())
        val c = abs(cos(r)); val s = abs(sin(r))
        return Pair(
            ceil(width * c + height * s).toInt().coerceAtLeast(1),
            ceil(width * s + height * c).toInt().coerceAtLeast(1)
        )
    }

    private fun displaySizePx(): Pair<Int, Int> {
        val metrics = android.util.DisplayMetrics()
        // Keep the overlay's coordinate space identical to MainActivity's position
        // editor. These TYPE_APPLICATION_OVERLAY windows are anchored to the full display.
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Pair(metrics.widthPixels.coerceAtLeast(1), metrics.heightPixels.coerceAtLeast(1))
    }

    /** Convert screen pixels to a stored dp coordinate without losing the final edge pixel. */
    private fun pxToPositionDp(valuePx: Int, maxPx: Int): Int {
        val bounded = valuePx.coerceIn(0, maxPx.coerceAtLeast(0))
        if (bounded == 0) return 0
        // At the physical edge, ceil is intentional: the subsequent pixel clamp in
        // updateLayeredRuntime/applyFloatingDimensions resolves it to the exact edge.
        if (bounded >= maxPx.coerceAtLeast(0)) {
            return ceil(maxPx.coerceAtLeast(0) / resources.displayMetrics.density.coerceAtLeast(0.1f)).toInt()
        }
        return (bounded / resources.displayMetrics.density.coerceAtLeast(0.1f)).toInt().coerceAtLeast(0)
    }

    private fun addFloatingBars(content: LinearLayout) {
        content.removeAllViews()
        floatingBars.clear()
        content.orientation = if (isVerticalMode) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        content.gravity = contentGravity()
        if (isVerticalMode) {
            val meter = SegmentedMeterView(this)
            meter.layoutParams = LinearLayout.LayoutParams(ViewGroupSize.MATCH_PARENT, ViewGroupSize.MATCH_PARENT)
            floatingBars.add(meter)
            content.addView(meter)
        } else {
            repeat(barCount.coerceAtLeast(1)) { index ->
                val bar = EffectBarView(this, index)
                bar.contentDescription = barFrequencyDescription(index, barCount)
                floatingBars.add(bar)
                content.addView(bar)
            }
        }
        renderedVertical = isVerticalMode
    }

    private object ViewGroupSize { const val MATCH_PARENT = -1 }

    // -----------------------------------------------------------------
    // MULTI-LAYER FLOATING SCENE
    // A single transparent overlay window hosts independent transparent
    // layer views. This keeps z-order deterministic and avoids one window
    // per profile while still preserving independent positions and styles.
    // -----------------------------------------------------------------

    private data class FloatingSceneLayer(
        val id: String,
        val name: String,
        val isDefault: Boolean,
        val colors: IntArray,
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
        var positionXdp: Int,
        var positionYdp: Int,
        val positionMode: String,
        val rotationDegrees: Int,
        val visible: Boolean = true
    )

    private data class FloatingSceneRuntime(
        var config: FloatingSceneLayer,
        val host: FrameLayout,
        val spectrum: FloatingSceneSpectrumView,
        val params: WindowManager.LayoutParams,
        var screenWidthPx: Int = 0,
        var screenHeightPx: Int = 0
    )

    private var floatingProfilesJson: String = ""
    private var layeredSourceSignature: String? = null
    private val layeredRuntime = mutableListOf<FloatingSceneRuntime>()

    private fun parseFloatingSceneLayers(raw: String): List<FloatingSceneLayer> {
        return try {
            val array = org.json.JSONArray(raw)
            val output = mutableListOf<FloatingSceneLayer>()
            for (i in 0 until min(array.length(), 12)) {
                val item = array.optJSONObject(i) ?: continue
                val colorsJson = item.optJSONArray("colors") ?: org.json.JSONArray()
                val colors = IntArray(colorsJson.length()) { colorIndex ->
                    colorsJson.optInt(colorIndex, Color.WHITE)
                }.takeIf { it.size >= 2 } ?: intArrayOf(Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE)
                val profileEffect = item.optString("effect", "GLOSSY")
                    .takeIf { it in setOf("CLEAN", "GLOSSY", "NEON", "GLASS") } ?: "GLOSSY"
                output.add(FloatingSceneLayer(
                    id = item.optString("id", "layer_$i"),
                    name = item.optString("name", "Layer ${i + 1}"),
                    isDefault = item.optBoolean("default", i == 0),
                    colors = colors,
                    effect = profileEffect,
                    widthDp = item.optInt("width", 320).coerceIn(1, 4096),
                    heightDp = item.optInt("height", 100).coerceIn(1, 4096),
                    barCount = item.optInt("bar_count", 18).coerceIn(1, MAX_BAR_COUNT),
                    barWidthDp = item.optInt("bar_width", 12).coerceIn(1, 2048),
                    gapDp = item.optInt("bar_gap", 3).coerceIn(0, 512),
                    scalePercent = item.optInt("scale", 100).coerceIn(1, 500),
                    opacityPercent = item.optInt("opacity", 100).coerceIn(0, 100),
                    shadowOpacityPercent = item.optInt("shadow_opacity", 100).coerceIn(0, 100),
                    glowPercent = if (profileEffect == "CLEAN") 0 else item.optInt(
                        "glow",
                        when (profileEffect) {
                            "NEON" -> 100
                            "GLASS" -> 38
                            else -> 50
                        }
                    ).coerceIn(0, 100),
                    responsePercent = item.optInt("response_speed", 82).coerceIn(0, 100),
                    cornerRadiusDp = item.optInt("corner_radius", 14).coerceIn(0, 512),
                    verticalMode = item.optBoolean("vertical_mode", false),
                    singleAudioMode = item.optString("single_audio_mode", "MUSIC").takeIf { it == "DJ" || it == "MUSIC" } ?: "MUSIC",
                    bassMinHz = item.optInt("bass_min_hz", 20).coerceIn(0, 20000),
                    bassMaxHz = item.optInt("bass_max_hz", 125).coerceIn(0, 20000),
                    horizontalAlignment = item.optString("horizontal_alignment", "LEFT").takeIf { it in setOf("LEFT", "CENTER", "RIGHT") } ?: "LEFT",
                    mirrorMode = item.optString("mirror_mode", "OFF").takeIf { it in setOf("OFF", "VERTICAL", "HORIZONTAL") } ?: "OFF",
                    positionXdp = item.optInt("position_x", 0).coerceAtLeast(0),
                    positionYdp = item.optInt("position_y", 0).coerceAtLeast(0),
                    positionMode = item.optString("position_mode", "STATIC").takeIf { it == "DYNAMIC" } ?: "STATIC",
                    rotationDegrees = item.optInt("rotation_degrees", 0).coerceIn(0, 359),
                    visible = item.optBoolean("visible", true)
                ))
            }
            output.sortedBy { if (it.isDefault || it.id == "root") 0 else 1 }
        } catch (error: Exception) {
            android.util.Log.e("FloatingDJ", "Unable to parse floating profile layers", error)
            emptyList()
        }
    }

    private fun updateLayeredFloatingWindow() {
        if (!floatingOverlayEnabled || !Settings.canDrawOverlays(this) || engineSource == EngineSource.NONE) {
            removeLayeredWindow()
            return
        }
        val layers = parseFloatingSceneLayers(floatingProfilesJson)
        if (layers.isEmpty()) {
            removeLayeredWindow()
            return
        }
        removeLegacyOverlayOnly()
        val (screenW, screenH) = displaySizePx()
        val expectedIds = layers.map { it.id }
        val currentIds = layeredRuntime.map { it.config.id }
        if (currentIds != expectedIds) {
            removeLayeredWindow()
            rebuildLayeredViews(layers, screenW, screenH)
        } else {
            // Update existing windows and views in place. Slider/rotation edits happen
            // continuously; removing and re-adding every overlay on each tick caused flicker.
            layers.forEachIndexed { index, config ->
                updateLayeredRuntime(layeredRuntime[index], config, screenW, screenH, updateWindow = true)
            }
        }
        layeredSourceSignature = expectedIds.joinToString("|") + "@$screenW:$screenH"
    }

    private fun configureOverlayInsets(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= 30) {
            // Overlay windows normally fit inside system-bar insets. Floating DJ
            // intentionally supports placement into the bottom gesture/navigation area.
            params.setFitInsetsTypes(0)
            params.setFitInsetsSides(0)
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun rebuildLayeredViews(
        layers: List<FloatingSceneLayer>,
        screenW: Int,
        screenH: Int
    ) {
        layeredRuntime.clear()
        // Reverse insertion so Default is added last and stays visually above all other layers.
        layers.asReversed().forEachIndexed { reverseIndex, config ->
            val host = FrameLayout(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                clipChildren = false
                clipToPadding = false
                elevation = (layers.size - reverseIndex).toFloat()
            }
            val spectrum = FloatingSceneSpectrumView(this, config)
            // Attach the renderer to its per-profile overlay host. Without this child
            // attachment, audio levels were updated but no pixels could be drawn.
            host.addView(spectrum, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
            val params = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.LEFT
            configureOverlayInsets(params)
            val runtime = FloatingSceneRuntime(config, host, spectrum, params)
            try {
                updateLayeredRuntime(runtime, config, screenW, screenH, updateWindow = false)
                windowManager.addView(host, params)
                layeredRuntime.add(runtime)
            } catch (error: Exception) {
                android.util.Log.e("FloatingDJ", "Unable to attach layer ${config.name}", error)
                try { windowManager.removeView(host) } catch (_: Exception) {}
            }
        }
        layeredRuntime.reverse()
    }

    private fun updateLayeredRuntime(
        runtime: FloatingSceneRuntime,
        config: FloatingSceneLayer,
        screenW: Int,
        screenH: Int,
        updateWindow: Boolean
    ) {
        val previousConfig = runtime.config
        val params = runtime.params
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)

        // Coordinates in the profile JSON are authoritative. A dragged layer is
        // persisted on ACTION_UP, so switching DYNAMIC -> STATIC does not need to
        // reuse the old WindowManager x/y here. This is also essential when a saved
        // scene is previewed: its own coordinates must replace the previous scene.
        val resolvedConfig = config

        val requestedWdp = (if (resolvedConfig.verticalMode) resolvedConfig.barWidthDp else resolvedConfig.widthDp)
            .coerceAtLeast(1) * resolvedConfig.scalePercent.coerceIn(1, 500) / 100f
        val requestedHdp = resolvedConfig.heightDp.coerceAtLeast(1) * resolvedConfig.scalePercent.coerceIn(1, 500) / 100f
        val rawW = (requestedWdp * density).toInt().coerceAtLeast(1)
        val rawH = (requestedHdp * density).toInt().coerceAtLeast(1)
        val radians = Math.toRadians(resolvedConfig.rotationDegrees.toDouble())
        val cosR = abs(cos(radians))
        val sinR = abs(sin(radians))
        val rawBoundW = ceil(rawW * cosR + rawH * sinR).toInt().coerceAtLeast(1)
        val rawBoundH = ceil(rawW * sinR + rawH * cosR).toInt().coerceAtLeast(1)
        val fit = min(1f, min(screenW.toFloat() / rawBoundW, screenH.toFloat() / rawBoundH)).coerceIn(0.0001f, 1f)
        val logicalW = (rawW * fit).toInt().coerceAtLeast(1)
        val logicalH = (rawH * fit).toInt().coerceAtLeast(1)
        val boundW = ceil(logicalW * cosR + logicalH * sinR).toInt().coerceIn(1, screenW.coerceAtLeast(1))
        val boundH = ceil(logicalW * sinR + logicalH * cosR).toInt().coerceIn(1, screenH.coerceAtLeast(1))

        val isVisible = resolvedConfig.visible
        val windowWidth = if (isVisible) boundW else 1
        val windowHeight = if (isVisible) boundH else 1
        val nextFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (resolvedConfig.positionMode == "DYNAMIC" && isVisible) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        val nextX = (resolvedConfig.positionXdp * density).toInt().coerceIn(0, (screenW - windowWidth).coerceAtLeast(0))
        val nextY = (resolvedConfig.positionYdp * density).toInt().coerceIn(0, (screenH - windowHeight).coerceAtLeast(0))
        val childParams = runtime.spectrum.layoutParams as? FrameLayout.LayoutParams
        val childSizeChanged = childParams == null || childParams.width != logicalW || childParams.height != logicalH
        val rotationChanged = runtime.spectrum.rotation != resolvedConfig.rotationDegrees.toFloat()
        val visibilityChanged = previousConfig.visible != resolvedConfig.visible
        val windowGeometryChanged = params.width != windowWidth || params.height != windowHeight ||
            params.x != nextX || params.y != nextY || params.flags != nextFlags || visibilityChanged
        val positionModeChanged = previousConfig.positionMode != resolvedConfig.positionMode || visibilityChanged
        val screenChanged = runtime.screenWidthPx != screenW || runtime.screenHeightPx != screenH

        runtime.config = resolvedConfig
        runtime.host.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
        runtime.screenWidthPx = screenW
        runtime.screenHeightPx = screenH
        runtime.spectrum.updateConfig(resolvedConfig)
        if (childSizeChanged) {
            runtime.spectrum.layoutParams = FrameLayout.LayoutParams(logicalW, logicalH, Gravity.CENTER)
        }
        if (rotationChanged || childSizeChanged) {
            runtime.spectrum.pivotX = logicalW / 2f
            runtime.spectrum.pivotY = logicalH / 2f
            if (rotationChanged) runtime.spectrum.rotation = resolvedConfig.rotationDegrees.toFloat()
        }

        params.width = windowWidth
        params.height = windowHeight
        params.flags = nextFlags
        params.x = nextX
        params.y = nextY
        configureOverlayInsets(params)
        if (positionModeChanged || screenChanged || !updateWindow) {
            if (resolvedConfig.positionMode == "DYNAMIC" && isVisible) {
                runtime.host.isClickable = true
                runtime.host.isFocusable = false
                attachLayerDrag(runtime.host, resolvedConfig, params, screenW, screenH)
            } else {
                runtime.host.setOnTouchListener(null)
                runtime.host.isClickable = false
                runtime.host.isFocusable = false
            }
        }
        if (childSizeChanged) runtime.host.requestLayout()
        runtime.host.invalidate()
        if (updateWindow && windowGeometryChanged) {
            try { windowManager.updateViewLayout(runtime.host, params) }
            catch (error: Exception) { android.util.Log.w("FloatingDJ", "Unable to update layer ${resolvedConfig.name}", error) }
        }
    }

    private fun attachLayerDrag(
        host: FrameLayout,
        initial: FloatingSceneLayer,
        params: WindowManager.LayoutParams,
        screenW: Int,
        screenH: Int
    ) {
        if (initial.positionMode != "DYNAMIC") {
            host.setOnTouchListener(null)
            host.isClickable = false
            return
        }
        var downX = 0f
        var downY = 0f
        var startLeft = 0
        var startTop = 0
        var lastBroadcastAt = 0L
        host.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startLeft = params.x
                    startTop = params.y
                    persistLayerFocus(initial.id)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startLeft + event.rawX - downX).toInt()
                        .coerceIn(0, (screenW - params.width).coerceAtLeast(0))
                    params.y = (startTop + event.rawY - downY).toInt()
                        .coerceIn(0, (screenH - params.height).coerceAtLeast(0))
                    try { windowManager.updateViewLayout(host, params) } catch (_: Exception) {}

                    // Keep coordinate readouts live without writing preferences for every
                    // touch frame. The release event persists exactly once.
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastBroadcastAt >= 80L) {
                        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
                        val xDp = pxToPositionDp(params.x, (screenW - params.width).coerceAtLeast(0))
                        val yDp = pxToPositionDp(params.y, (screenH - params.height).coerceAtLeast(0))
                        broadcastLayerPosition(initial.id, xDp, yDp, isFinal = false)
                        lastBroadcastAt = now
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
                    val xDp = pxToPositionDp(params.x, (screenW - params.width).coerceAtLeast(0))
                    val yDp = pxToPositionDp(params.y, (screenH - params.height).coerceAtLeast(0))
                    persistLayerPosition(initial.id, xDp, yDp)
                    true
                }
                else -> true
            }
        }
    }

    private fun persistLayerFocus(profileId: String) {
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
            .putString("selected_floating_profile_id", profileId)
            .putString("live_preview_profile_id", profileId)
            .putString("last_interacted_floating_profile_id", profileId)
            .apply()
        sendBroadcast(Intent(ACTION_LAYER_FOCUSED).setPackage(packageName)
            .putExtra(EXTRA_LAYER_ID, profileId))
    }

    private fun broadcastLayerPosition(profileId: String, xDp: Int, yDp: Int, isFinal: Boolean) {
        sendBroadcast(Intent(ACTION_POSITION).setPackage(packageName)
            .putExtra(EXTRA_POSITION_PROFILE_ID, profileId)
            .putExtra(EXTRA_POSITION_X, xDp)
            .putExtra(EXTRA_POSITION_Y, yDp)
            .putExtra(EXTRA_POSITION_FINAL, isFinal))
    }

    private fun persistLayerPosition(profileId: String, xDp: Int, yDp: Int) {
        try {
            val prefs = getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
            val raw = prefs.getString("floating_profiles", "[]") ?: "[]"
            val array = org.json.JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                if (item.optString("id") == profileId) {
                    item.put("position_x", xDp)
                    item.put("position_y", yDp)
                    break
                }
            }
            val editor = prefs.edit()
                .putString("floating_profiles", array.toString())
                .putBoolean("position_initialized", true)
            if (prefs.getString("selected_floating_profile_id", "root") == profileId) {
                editor.putInt("position_x", xDp).putInt("position_y", yDp)
            }
            editor.apply()
            val runtime = layeredRuntime.firstOrNull { it.config.id == profileId }
            if (runtime != null) runtime.config = runtime.config.copy(positionXdp = xDp, positionYdp = yDp)
            broadcastLayerPosition(profileId, xDp, yDp, isFinal = true)
        } catch (error: Exception) {
            android.util.Log.e("FloatingDJ", "Unable to persist layer position", error)
        }
    }

    private fun removeLegacyOverlayOnly() {
        windowView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        windowView = null; contentView = null; overlayParams = null
        overlayCenterXpx = null; overlayCenterYpx = null
        floatingBars.clear(); renderedVertical = null
    }

    private fun removeLayeredWindow() {
        layeredRuntime.forEach { runtime ->
            try { windowManager.removeView(runtime.host) } catch (_: Exception) {}
        }
        layeredRuntime.clear()
        layeredSourceSignature = null
    }

    private inner class FloatingSceneSpectrumView(
        context: android.content.Context,
        initialConfig: FloatingSceneLayer
    ) : View(context) {
        private var config: FloatingSceneLayer = initialConfig
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val levels = FloatArray(MAX_BAR_COUNT)
        private val targetLevels = FloatArray(MAX_BAR_COUNT)
        private val peaks = FloatArray(MAX_BAR_COUNT)
        private val peakHoldUntil = LongArray(MAX_BAR_COUNT)
        private var meterLevel = 0f
        private var targetMeterLevel = 0f
        private var meterPeak = 0f
        private var meterPeakHoldUntil = 0L

        // Hardware acceleration remains enabled; glow uses GPU-safe rounded outline passes.

        fun updateConfig(next: FloatingSceneLayer) {
            if (
                config.barCount != next.barCount ||
                config.verticalMode != next.verticalMode ||
                config.singleAudioMode != next.singleAudioMode ||
                config.horizontalAlignment != next.horizontalAlignment ||
                config.mirrorMode != next.mirrorMode
            ) {
                levels.fill(0f)
                targetLevels.fill(0f)
                peaks.fill(0f)
                peakHoldUntil.fill(0L)
                meterLevel = 0f
                targetMeterLevel = 0f
                meterPeak = 0f
                meterPeakHoldUntil = 0L
            }
            config = next
            invalidate()
        }

        fun setSpectrum(source: FloatArray) {
            if (config.verticalMode) {
                targetMeterLevel = if (config.singleAudioMode == "DJ") sampleSingleFrequencyRange(source)
                    else source.getOrElse(MAX_BAR_COUNT + 1) { 0f }.coerceIn(0f, 1f)
            } else {
                val count = config.barCount.coerceIn(1, MAX_BAR_COUNT)
                for (i in 0 until count) {
                    targetLevels[i] = sampleSceneBand(source, i, count).coerceIn(0f, 1f)
                }
            }
        }

        fun advanceAnimation(dtSeconds: Float, nowMs: Long, attackFactor: Float, releaseFactor: Float): Boolean {
            if (!isShown || width <= 0 || height <= 0) return false
            var changed = false
            var pending = false
            if (config.verticalMode) {
                val oldLevel = meterLevel
                val oldPeak = meterPeak
                val factor = if (targetMeterLevel >= meterLevel) attackFactor else releaseFactor
                meterLevel += (targetMeterLevel - meterLevel) * factor
                if (meterLevel > meterPeak) {
                    meterPeak = meterLevel
                    meterPeakHoldUntil = nowMs + 125L
                } else if (nowMs > meterPeakHoldUntil) {
                    meterPeak = (meterPeak - dtSeconds * 1.9f).coerceAtLeast(meterLevel)
                }
                changed = abs(meterLevel - oldLevel) > 0.00025f || abs(meterPeak - oldPeak) > 0.00025f
                pending = abs(targetMeterLevel - meterLevel) > 0.0015f || abs(meterPeak - meterLevel) > 0.02f
            } else {
                val count = config.barCount.coerceIn(1, MAX_BAR_COUNT)
                for (i in 0 until count) {
                    val oldLevel = levels[i]
                    val oldPeak = peaks[i]
                    val target = targetLevels[i]
                    val factor = if (target >= oldLevel) attackFactor else releaseFactor
                    levels[i] += (target - oldLevel) * factor
                    if (levels[i] > peaks[i]) {
                        peaks[i] = levels[i]
                        peakHoldUntil[i] = nowMs + 125L
                    } else if (nowMs > peakHoldUntil[i]) {
                        peaks[i] = (peaks[i] - dtSeconds * 1.9f).coerceAtLeast(levels[i])
                    }
                    if (abs(levels[i] - oldLevel) > 0.00025f || abs(peaks[i] - oldPeak) > 0.00025f) changed = true
                    if (abs(target - levels[i]) > 0.0015f || abs(peaks[i] - levels[i]) > 0.02f) pending = true
                }
            }
            if (changed) invalidate()
            return pending
        }

        private fun sampleSceneBand(source: FloatArray, index: Int, count: Int): Float {
            val symmetric = config.horizontalAlignment == "CENTER" || config.mirrorMode == "HORIZONTAL"
            val uniqueCount = if (symmetric) ((count + 1) / 2).coerceAtLeast(1) else count
            val uniqueIndex = if (symmetric) min(index, count - 1 - index) else index
            var center = (uniqueIndex + 0.5f) / uniqueCount.toFloat()
            if (config.horizontalAlignment == "RIGHT") center = 1f - center
            val halfWindow = MAX_BAR_COUNT.toFloat() / uniqueCount.toFloat() / 2f
            val start = (center * MAX_BAR_COUNT - halfWindow).coerceIn(0f, MAX_BAR_COUNT.toFloat())
            val end = (center * MAX_BAR_COUNT + halfWindow).coerceIn(0f, MAX_BAR_COUNT.toFloat())
            val first = floor(start.toDouble()).toInt().coerceIn(0, MAX_BAR_COUNT - 1)
            val last = floor((end - 0.0001f).coerceAtLeast(start).toDouble()).toInt().coerceIn(first, MAX_BAR_COUNT - 1)
            var sum = 0f; var weight = 0f
            for (band in first..last) {
                val overlap = (min(end, band + 1f) - max(start, band.toFloat())).coerceAtLeast(0f)
                if (overlap > 0f) { sum += source.getOrElse(band) { 0f } * overlap; weight += overlap }
            }
            return (sum / weight.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
        }

        private fun sampleSingleFrequencyRange(source: FloatArray): Float {
            val low = min(config.bassMinHz, config.bassMaxHz)
            val high = max(config.bassMinHz, config.bassMaxHz)
            val fullRange = low == 0 && high == 0
            val ratio = MAX_SPECTRUM_HZ / MIN_SPECTRUM_HZ
            val startHz = if (fullRange) MIN_SPECTRUM_HZ else low.coerceAtLeast(MIN_SPECTRUM_HZ.toInt()).toDouble()
            val endHz = if (fullRange) MAX_SPECTRUM_HZ else max(high, low).toDouble().coerceAtLeast(startHz)
            val start = (ln(startHz / MIN_SPECTRUM_HZ) / ln(ratio) * MAX_BAR_COUNT).toFloat().coerceIn(0f, MAX_BAR_COUNT - 0.001f)
            val end = (ln(endHz / MIN_SPECTRUM_HZ) / ln(ratio) * MAX_BAR_COUNT).toFloat().coerceIn(start + 0.001f, MAX_BAR_COUNT.toFloat())
            val first = floor(start.toDouble()).toInt().coerceIn(0, MAX_BAR_COUNT - 1)
            val last = floor((end - 0.0001f).coerceAtLeast(start).toDouble()).toInt().coerceIn(first, MAX_BAR_COUNT - 1)
            var total = 0f
            var weight = 0f
            for (band in first..last) {
                val overlap = (min(end, band + 1f) - max(start, band.toFloat())).coerceAtLeast(0f)
                if (overlap > 0f) { total += source.getOrElse(band) { 0f } * overlap; weight += overlap }
            }
            return (total / weight.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0 || config.colors.isEmpty()) return
            val alpha = config.opacityPercent.coerceIn(0, 100) * 255 / 100
            val radius = dp(config.cornerRadiusDp).toFloat()
            if (config.verticalMode) {
                drawSegmentedSingleMeter(canvas, alpha, radius)
                return
            }
            val count = config.barCount.coerceIn(1, MAX_BAR_COUNT)
            val requestedGap = visualGapPx(config.gapDp).toInt()
                .coerceAtLeast(if (config.gapDp == 0) 0 else 1).toFloat()
            val minimumBarWidth = dp(1).coerceAtLeast(1).toFloat()
            val maxGap = if (count <= 1) 0f else
                ((width.toFloat() - count.toFloat() * minimumBarWidth) / (count - 1).toFloat()).coerceAtLeast(0f)
            val gap = min(requestedGap, maxGap)
            val availableForBars = (width.toFloat() - gap * (count - 1).toFloat()).coerceAtLeast(count.toFloat() * minimumBarWidth)
            val requestedBarWidth = dp(config.barWidthDp).coerceAtLeast(dp(1)).toFloat()
            val barW = min(requestedBarWidth, availableForBars / count.toFloat()).coerceAtLeast(minimumBarWidth)
            val renderedGroupWidth = barW * count.toFloat() + gap * (count - 1).toFloat()
            val groupStartX = ((width.toFloat() - renderedGroupWidth) / 2f).coerceAtLeast(0f)
            for (i in 0 until count) {
                val level = levels[i].coerceIn(0f, 1f)
                val left = groupStartX + i * (barW + gap)
                val top = height - height * level
                val colorA = config.colors[i % config.colors.size]
                val colorB = config.colors[(i + 1) % config.colors.size]
                val barHeight = (height * level).coerceAtLeast(1f)
                val gapCurve = (config.gapDp / 64f).coerceIn(0f, 1f)
                val progressiveRadius = radius * (1f - gapCurve * 0.78f)
                val rects = if (config.mirrorMode == "VERTICAL") {
                    val half = height / 2f * level
                    listOf(RectF(left, height / 2f - half, left + barW, height / 2f), RectF(left, height / 2f, left + barW, height / 2f + half))
                } else listOf(RectF(left, top, left + barW, height.toFloat()))
                rects.forEachIndexed { rectIndex, rect ->
                    val rectRadius = min(progressiveRadius, min(rect.width(), rect.height()) / 2f)
                    drawSpectrumGlow(canvas, paint, rect, colorA, config.effect, config.glowPercent, config.opacityPercent, rectRadius)
                    paint.shader = LinearGradient(0f, rect.bottom, 0f, rect.top, colorB, colorA, Shader.TileMode.CLAMP)
                    paint.alpha = alpha
                    if (config.gapDp == 0) {
                        val outerRadius = min(radius, min(rect.width(), rect.height()) / 2f)
                        if (config.mirrorMode == "VERTICAL" && rectIndex == 1) {
                            drawBottomRoundedColumn(canvas, paint, rect, outerRadius)
                        } else {
                            drawTopRoundedColumn(canvas, paint, rect, outerRadius)
                        }
                    } else {
                        canvas.drawRoundRect(rect, rectRadius, rectRadius, paint)
                    }
                    paint.shader = null; paint.alpha = 255
                    if (config.effect == "GLOSSY" || config.effect == "GLASS") {
                        paint.color = Color.WHITE; paint.alpha = (alpha * if (config.effect == "GLASS") 0.36f else 0.24f).toInt()
                        val highlightW = max(dp(1).toFloat(), rect.width() * 0.18f)
                        canvas.drawRoundRect(rect.left + rect.width() * 0.16f, rect.top + dp(1), rect.left + rect.width() * 0.16f + highlightW, rect.bottom - dp(1), rectRadius, rectRadius, paint)
                        paint.alpha = 255
                    }
                }
                // Peak caps are drawn at the real outer endpoint(s). For vertical
                // top/bottom mirroring, the cap is mirrored with the bar body. The
                // level/peak index already follows LOW->HIGH, CENTER or HIGH->LOW flow.
                if (peaks[i] > 0.05f && config.shadowOpacityPercent > 0 && abs(peaks[i] - level) > 0.025f) {
                    paint.shader = null
                    paint.color = Color.WHITE
                    paint.alpha = (185 * config.shadowOpacityPercent.coerceIn(0, 100) / 100)
                    val capHeight = dp(1).coerceAtLeast(1).toFloat()
                    if (config.mirrorMode == "VERTICAL") {
                        val centerY = height / 2f
                        val extent = centerY * peaks[i].coerceIn(0f, 1f)
                        val topY = (centerY - extent - capHeight / 2f).coerceIn(0f, height.toFloat() - capHeight)
                        val bottomY = (centerY + extent - capHeight / 2f).coerceIn(0f, height.toFloat() - capHeight)
                        canvas.drawRect(left, topY, left + barW, topY + capHeight, paint)
                        canvas.drawRect(left, bottomY, left + barW, bottomY + capHeight, paint)
                    } else {
                        val y = (height * (1f - peaks[i]) - capHeight / 2f).coerceIn(0f, height.toFloat() - capHeight)
                        canvas.drawRect(left, y, left + barW, y + capHeight, paint)
                    }
                    paint.alpha = 255
                }
                if (config.opacityPercent > 0 && level > 0.04f) {
                    paint.shader = null
                    paint.color = Color.WHITE
                    paint.alpha = (185 * config.opacityPercent.coerceIn(0, 100) / 100)
                    val endpointH = dp(1).coerceAtLeast(1).toFloat()
                    if (config.mirrorMode == "VERTICAL") {
                        val centerY = height / 2f
                        val extent = centerY * level.coerceIn(0f, 1f)
                        val topY = (centerY - extent - endpointH / 2f).coerceIn(0f, height.toFloat() - endpointH)
                        val bottomY = (centerY + extent - endpointH / 2f).coerceIn(0f, height.toFloat() - endpointH)
                        canvas.drawRect(left, topY, left + barW, topY + endpointH, paint)
                        canvas.drawRect(left, bottomY, left + barW, bottomY + endpointH, paint)
                    } else {
                        val y = (height * (1f - level) - endpointH / 2f).coerceIn(0f, height.toFloat() - endpointH)
                        canvas.drawRect(left, y, left + barW, y + endpointH, paint)
                    }
                    paint.alpha = 255
                }
            }
        }

        private fun drawSegmentedSingleMeter(canvas: Canvas, alpha: Int, radius: Float) {
            val count = 24
            val glowStrength = glowPercent.coerceIn(0, 100) / 100f * glowStyleFactor(config.effect)
            val needsGlowRoom = glowStrength > 0f && config.opacityPercent > 0
            val glowInsetX = if (needsGlowRoom) minOf(dp(14).toFloat() * glowStrength, width * 0.14f) else 0f
            val glowInsetY = 0f // Keep the Single meter body flush with the full longitudinal surface, including at 90°/270° rotation.
            val contentWidth = (width.toFloat() - glowInsetX * 2f).coerceAtLeast(1f)
            val contentHeight = (height.toFloat() - glowInsetY * 2f).coerceAtLeast(1f)
            val left = glowInsetX
            val right = width.toFloat() - glowInsetX
            val bottomEdge = height.toFloat() - glowInsetY
            val safeRadius = min(radius, min(contentWidth, contentHeight) / 2f)

            if (config.gapDp == 0) {
                val shaderColors = IntArray(config.colors.size) { index ->
                    val color = config.colors[index]
                    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
                }
                val fillTop = glowInsetY + contentHeight * (1f - meterLevel.coerceIn(0f, 1f))
                val rect = RectF(left, fillTop, right, bottomEdge)
                drawSpectrumGlow(canvas, paint, rect, config.colors.first(), config.effect, config.glowPercent, config.opacityPercent, safeRadius)
                paint.shader = LinearGradient(0f, bottomEdge, 0f, glowInsetY, shaderColors, null, Shader.TileMode.CLAMP)
                paint.style = Paint.Style.FILL
                paint.alpha = alpha
                canvas.drawRoundRect(rect, safeRadius, safeRadius, paint)
                paint.shader = null
                paint.alpha = 255
                if (config.effect == "GLOSSY" || config.effect == "GLASS") {
                    paint.color = Color.WHITE
                    paint.alpha = (alpha * if (config.effect == "GLASS") 0.42f else 0.32f).toInt()
                    canvas.drawRoundRect(
                        rect.left + rect.width() * 0.14f, fillTop + dp(2),
                        rect.left + rect.width() * 0.30f, bottomEdge - dp(2), safeRadius, safeRadius, paint
                    )
                    paint.alpha = 255
                }
                drawSinglePeakMarker(canvas, glowInsetX, glowInsetY)
                return
            }

            val requestedGap = visualGapPx(config.gapDp)
            val minSegmentHeight = dp(2).toFloat()
            val availableForGap = (contentHeight - minSegmentHeight * count.toFloat()).coerceAtLeast(0f)
            val maxGap = availableForGap / (count - 1).toFloat()
            val gap = min(requestedGap, maxGap)
            val segmentHeight = (contentHeight - gap * (count - 1).toFloat()) / count.toFloat()
            if (segmentHeight <= 0f) return

            val gapRatio = if (maxGap > 0f) (gap / maxGap).coerceIn(0f, 1f) else 0f
            val radiusScale = 1f - gapRatio * 0.78f
            val segmentRadius = min(min(radius, contentWidth / 2f) * radiusScale, segmentHeight / 2f)
            val activeCount = (meterLevel.coerceIn(0f, 1f) * count).toInt().coerceIn(0, count)

            for (index in 0 until count) {
                val top = glowInsetY + contentHeight - (index + 1).toFloat() * segmentHeight - index.toFloat() * gap
                val bottom = top + segmentHeight
                val colorIndex = (index * config.colors.size / count).coerceIn(0, config.colors.lastIndex)
                val color = config.colors[colorIndex]
                val active = index < activeCount
                val rect = RectF(left, top, right, bottom)
                if (active) drawSpectrumGlow(canvas, paint, rect, color, config.effect, config.glowPercent, config.opacityPercent, segmentRadius)
                paint.shader = null
                paint.style = Paint.Style.FILL
                paint.color = color
                paint.alpha = if (active) alpha else (config.shadowOpacityPercent.coerceIn(0, 100) * 255 / 100 * 0.10f).toInt()
                canvas.drawRoundRect(rect, segmentRadius, segmentRadius, paint)
                paint.alpha = 255
                if (active && (config.effect == "GLOSSY" || config.effect == "GLASS") && segmentHeight > dp(2)) {
                    paint.color = Color.WHITE
                    paint.alpha = (alpha * if (config.effect == "GLASS") 0.42f else 0.32f).toInt()
                    canvas.drawRoundRect(
                        rect.left + rect.width() * 0.14f, top + dp(1),
                        rect.left + rect.width() * 0.30f, bottom - dp(1), segmentRadius, segmentRadius, paint
                    )
                    paint.alpha = 255
                }
            }
            drawSinglePeakMarker(canvas, glowInsetX, glowInsetY)
        }

        private fun drawSinglePeakMarker(canvas: Canvas, insetX: Float = 0f, insetY: Float = 0f) {
            if (width <= 0 || height <= 0) return
            val markerHeight = dp(2).toFloat().coerceAtMost(height.toFloat())
            val innerHeight = (height.toFloat() - insetY * 2f).coerceAtLeast(1f)
            val markerLeft = insetX.coerceAtLeast(0f)
            val markerRight = (width.toFloat() - insetX).coerceAtLeast(markerLeft + 1f)

            fun drawMarker(value: Float, opacity: Int) {
                if (opacity <= 0 || value <= 0.04f) return
                val y = (insetY + innerHeight * (1f - value.coerceIn(0f, 1f)) - markerHeight / 2f)
                    .coerceIn(insetY, (height.toFloat() - insetY - markerHeight).coerceAtLeast(insetY))
                val marker = RectF(markerLeft, y, markerRight, y + markerHeight)
                drawSpectrumGlow(canvas, paint, marker, Color.WHITE, config.effect, config.glowPercent, opacity, dp(1).toFloat())
                paint.shader = null
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.alpha = 235 * opacity.coerceIn(0, 100) / 100
                canvas.drawRoundRect(marker, markerHeight / 2f, markerHeight / 2f, paint)
                paint.alpha = 255
            }

            // Held maximum is a separate shadow. The live endpoint is always tied
            // to Bar Opacity, including when it is also the current maximum.
            if (abs(meterPeak - meterLevel) > 0.025f) {
                drawMarker(meterPeak, config.shadowOpacityPercent)
            }
            drawMarker(meterLevel, config.opacityPercent)
        }

    }

    private fun rebuildLayeredOverlay() {
        layeredSourceSignature = null
        updateLayeredFloatingWindow()
    }


    private fun updateFloatingWindow() {
        if (floatingProfilesJson.isNotBlank()) {
            if (!floatingOverlayEnabled || !Settings.canDrawOverlays(this) || engineSource == EngineSource.NONE) {
                removeLayeredWindow()
                removeLegacyOverlayOnly()
                if (floatingOverlayEnabled && !Settings.canDrawOverlays(this)) {
                    floatingOverlayEnabled = false
                    getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit().putBoolean("floating_overlay_enabled", false).apply()
                    broadcastStatus("OVERLAY_PERMISSION_REQUIRED: Overlay permission was revoked")
                }
                return
            }
            updateLayeredFloatingWindow()
            return
        }
        if (!floatingOverlayEnabled || !Settings.canDrawOverlays(this)) {
            removeOverlay()
            if (floatingOverlayEnabled && !Settings.canDrawOverlays(this)) {
                floatingOverlayEnabled = false
                getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
                    .putBoolean("floating_overlay_enabled", false)
                    .apply()
                broadcastStatus("OVERLAY_PERMISSION_REQUIRED: Overlay permission was revoked")
            }
            return
        }
        if (engineSource == EngineSource.NONE) {
            removeOverlay()
            return
        }
        val content = contentView
        val host = windowView
        if (content == null || host == null) { showFloatingWindow(); return }
        val expected = if (isVerticalMode) 1 else barCount.coerceAtLeast(1)
        if (renderedVertical != isVerticalMode || floatingBars.size != expected) addFloatingBars(content)
        applyFloatingDimensions()
        applyOverlayVisibility()
    }

    private fun applyFloatingDimensions() {
        val host = windowView ?: return
        val content = contentView ?: return
        val params = overlayParams ?: return
        val oldWidth = params.width.coerceAtLeast(1)
        val oldHeight = params.height.coerceAtLeast(1)
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val centerX = overlayCenterXpx ?: ((positionXdp * density).toInt() + oldWidth / 2)
        val centerY = overlayCenterYpx ?: ((positionYdp * density).toInt() + oldHeight / 2)
        val (contentW, contentH) = localContentSizePx()
        val (boundW, boundH) = rotatedBoundsPx(contentW, contentH)
        params.width = boundW
        params.height = boundH
        // Keep the center fixed; only the outer bounds change when orientation or angle changes.
        params.x = centerX - boundW / 2
        params.y = centerY - boundH / 2
        content.gravity = contentGravity()
        content.rotation = rotationDegrees.toFloat()
        content.pivotX = contentW / 2f
        content.pivotY = contentH / 2f
        content.layoutParams = FrameLayout.LayoutParams(contentW, contentH, Gravity.CENTER)

        if (isVerticalMode) {
            val meter = floatingBars.firstOrNull() as? SegmentedMeterView
            meter?.layoutParams = LinearLayout.LayoutParams(contentW, contentH)
            meter?.invalidate()
        } else {
            val count = floatingBars.size.coerceAtLeast(1)
            val requestedGap = visualGapPx(gapDp).toInt().coerceAtLeast(if (gapDp == 0) 0 else 1)
            val maxGap = if (count > 1) {
                ((contentW - count * dp(1)).coerceAtLeast(0)) / (count - 1)
            } else 0
            val gapPx = min(requestedGap, maxGap)
            val totalGap = gapPx * (count - 1)
            val requestedGlowPadding = if (gapDp > 0 && glowPercent > 0) dp(8) else 0
            val glowPadding = min(requestedGlowPadding, (contentW / (count * 6)).coerceAtLeast(0))
            val available = (contentW - totalGap - 2 * glowPadding * count).coerceAtLeast(count * dp(1))
            // Bar Thickness always controls the drawable body width, including Gap 0.
            // With Gap 0 the bodies touch; any unused width is centered by the parent.
            // The count-based cap prevents overlap when the requested thickness is too large.
            val requested = dp(barWidthDp).coerceAtLeast(dp(1))
            val actualWidth = min(requested, available / count).coerceAtLeast(dp(1))
            val childWidth = actualWidth + 2 * glowPadding
            for ((index, bar) in floatingBars.withIndex()) {
                (bar as? EffectBarView)?.setGlowPaddingPx(glowPadding)
                bar.contentDescription = barFrequencyDescription(index, count)
                val left = if (index == 0) 0 else gapPx / 2
                val right = if (index == count - 1) 0 else gapPx - gapPx / 2
                bar.layoutParams = LinearLayout.LayoutParams(childWidth, contentH).apply {
                    setMargins(left, 0, right, 0)
                }
                bar.invalidate()
            }
        }

        val (screenW, screenH) = displaySizePx()
        params.x = params.x.coerceIn(0, (screenW - boundW).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenH - boundH).coerceAtLeast(0))
        overlayCenterXpx = params.x + boundW / 2
        overlayCenterYpx = params.y + boundH / 2
        // Do not round-trip px to dp here. Persist only explicit user drag/position changes.
        configureTouchMode(host, params)
        try { windowManager.updateViewLayout(host, params) }
        catch (e: Exception) { android.util.Log.e("FloatingDJ", "Overlay layout update failed", e) }
    }

    private fun configureTouchMode(host: FrameLayout, params: WindowManager.LayoutParams) {
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (positionMode == "DYNAMIC") {
            host.setOnTouchListener { _, event -> handleDrag(event, params, host) }
        } else {
            host.setOnTouchListener(null)
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }

    private var dragStartX = 0
    private var dragStartY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f

    private fun handleDrag(event: MotionEvent, params: WindowManager.LayoutParams, host: View): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = params.x
                dragStartY = params.y
                dragTouchX = event.rawX
                dragTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val (screenW, screenH) = displaySizePx()
                params.x = (dragStartX + event.rawX - dragTouchX).toInt()
                params.y = (dragStartY + event.rawY - dragTouchY).toInt()
                params.x = params.x.coerceIn(0, (screenW - params.width).coerceAtLeast(0))
                params.y = params.y.coerceIn(0, (screenH - params.height).coerceAtLeast(0))
                positionXdp = pxToPositionDp(params.x, (screenW - params.width).coerceAtLeast(0))
                positionYdp = pxToPositionDp(params.y, (screenH - params.height).coerceAtLeast(0))
                overlayCenterXpx = params.x + params.width / 2
                overlayCenterYpx = params.y + params.height / 2
                try { windowManager.updateViewLayout(host, params) } catch (_: Exception) {}
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                savePosition()
                return true
            }
        }
        return true
    }

    private fun savePosition() {
        overlayParams?.let { params ->
            val (screenW, screenH) = displaySizePx()
            overlayCenterXpx = params.x + params.width / 2
            overlayCenterYpx = params.y + params.height / 2
            positionXdp = pxToPositionDp(params.x, (screenW - params.width).coerceAtLeast(0))
            positionYdp = pxToPositionDp(params.y, (screenH - params.height).coerceAtLeast(0))
        }
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
        .putInt("position_x", positionXdp)
        .putInt("position_y", positionYdp)
        .putBoolean("position_initialized", true)
        .apply()
        sendBroadcast(Intent(ACTION_POSITION).setPackage(packageName)
            .putExtra(EXTRA_POSITION_X, positionXdp)
            .putExtra(EXTRA_POSITION_Y, positionYdp))
    }

    // -----------------------------------------------------------------
    // BAR DRAWING / EFFECTS
    // -----------------------------------------------------------------

    private fun drawNeonAura(
        canvas: Canvas, paint: Paint, body: RectF, color: Int,
        intensity: Float, spreadLimit: Float, radius: Float
    ) {
        val glowPercentValue = (intensity.coerceIn(0f, 1f) * 100f).toInt()
        drawSpectrumGlow(canvas, paint, body, color, effectStyle, glowPercentValue, opacityPercent, radius, spreadLimit)
    }

    private inner class EffectBarView(
        context: android.content.Context,
        private val index: Int
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var level = 0.03f
        private var targetLevel = 0.03f
        private var reservedGlowPaddingPx = 0

        fun setGlowPaddingPx(value: Int) {
            reservedGlowPaddingPx = value.coerceAtLeast(0)
            invalidate()
        }

        init { pivotY = 0f }

        fun setAudioLevel(value: Float) {
            targetLevel = value.coerceIn(0.02f, 1f)
        }

        fun advanceAnimation(dtSeconds: Float, attackFactor: Float, releaseFactor: Float): Boolean {
            if (!isShown || width <= 0 || height <= 0) return false
            val oldLevel = level
            val factor = if (targetLevel >= level) attackFactor else releaseFactor
            level += (targetLevel - level) * factor
            if (abs(level - oldLevel) > 0.00025f) invalidate()
            return abs(targetLevel - level) > 0.0015f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (height <= 0 || width <= 0 || spectrumColors.isEmpty()) return

            val glow = reservedGlowPaddingPx.toFloat()
                .coerceAtMost(width * 0.42f)
                .coerceAtMost(height * 0.18f)
            val availableHeight = (height.toFloat() - glow * 2f).coerceAtLeast(2f)
            val colorTop = spectrumColors[(index + 1) % spectrumColors.size]
            val colorBottom = spectrumColors[index % spectrumColors.size]
            val verticalMirror = mirrorMode == "VERTICAL"
            val rects = if (verticalMirror) {
                val axisY = height / 2f
                val halfAvailable = (availableHeight / 2f).coerceAtLeast(1f)
                val barHeight = halfAvailable * level.coerceIn(0f, 1f)
                listOf(
                    RectF(glow, axisY - barHeight, width.toFloat() - glow, axisY),
                    RectF(glow, axisY, width.toFloat() - glow, axisY + barHeight)
                )
            } else {
                val barHeight = availableHeight * level.coerceIn(0f, 1f)
                val top = height.toFloat() - glow - barHeight
                listOf(RectF(glow, top, width.toFloat() - glow, height.toFloat() - glow))
            }

            val neonStrength = glowPercent.coerceIn(0, 100) / 100f
            rects.forEachIndexed { rectIndex, rect ->
                if (rect.width() <= 0f || rect.height() <= 0f) return@forEachIndexed
                val gapCurve = (gapDp / 64f).coerceIn(0f, 1f)
                val radius = min(dp(cornerRadiusDp).toFloat() * (1f - gapCurve * 0.78f), min(rect.width(), rect.height()) / 2f)
                val auraSpread = if (gapDp == 0) dp(8).toFloat() * neonStrength else glow
                if (neonStrength > 0f && auraSpread > 0f) {
                    drawNeonAura(canvas, paint, rect, colorTop, neonStrength, auraSpread, radius)
                }

                paint.shader = LinearGradient(
                    0f, rect.bottom, 0f, rect.top,
                    colorBottom, colorTop, Shader.TileMode.CLAMP
                )
                paint.alpha = opacityPercent.coerceIn(0, 100) * 255 / 100
                if (gapDp == 0) {
                    val outerRadius = min(dp(cornerRadiusDp).toFloat(), min(rect.width(), rect.height()) / 2f)
                    if (mirrorMode == "VERTICAL" && rectIndex == 1) drawBottomRoundedColumn(canvas, paint, rect, outerRadius)
                    else drawTopRoundedColumn(canvas, paint, rect, outerRadius)
                } else {
                    canvas.drawRoundRect(rect, radius, radius, paint)
                }
                paint.shader = null
                paint.clearShadowLayer()
                paint.alpha = 255

                if (effectStyle == "GLOSSY" || effectStyle == "GLASS") {
                    paint.color = if (effectStyle == "GLASS") 0x88FFFFFF.toInt() else 0x66FFFFFF
                    paint.alpha = opacityPercent.coerceIn(0, 100) * 255 / 100
                    val highlightWidth = max(dp(1).toFloat(), rect.width() * 0.18f)
                    val insetY = min(dp(2).toFloat(), rect.height() * 0.18f)
                    canvas.drawRoundRect(
                        rect.left + rect.width() * 0.16f,
                        rect.top + insetY,
                        rect.left + rect.width() * 0.16f + highlightWidth,
                        rect.bottom - insetY,
                        radius, radius, paint
                    )
                    paint.alpha = 255
                }
            }
        }
    } // PENTING: EffectBarView ditutup di sini.

    private inner class SegmentedMeterView(
        context: android.content.Context
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var level = 0f
        private var targetLevel = 0f
        private var peak = 0f
        private var peakHoldUntil = 0L

        fun setAudioLevel(value: Float) {
            targetLevel = if (value < 0.018f) 0f else value.coerceIn(0f, 1f)
        }

        fun advanceAnimation(dtSeconds: Float, nowMs: Long, attackFactor: Float, releaseFactor: Float): Boolean {
            if (!isShown || width <= 0 || height <= 0) return false
            val oldLevel = level
            val oldPeak = peak
            val factor = if (targetLevel >= level) attackFactor else releaseFactor
            level += (targetLevel - level) * factor
            if (level > peak) {
                peak = level
                peakHoldUntil = nowMs + 125L
            } else if (nowMs > peakHoldUntil) {
                peak = (peak - 1.9f * dtSeconds).coerceAtLeast(level)
            }
            val changed = abs(level - oldLevel) > 0.00025f || abs(peak - oldPeak) > 0.00025f
            if (changed) invalidate()
            return abs(targetLevel - level) > 0.0015f || abs(peak - level) > 0.02f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (width <= 0 || height <= 0 || spectrumColors.isEmpty()) return

            val alpha = opacityPercent.coerceIn(0, 100) * 255 / 100
            val radius = min(dp(cornerRadiusDp).toFloat(), width / 2f)

            // GAP 0: satu bar kontinu, tanpa celah antarsegmen.
            if (gapDp == 0) {
                val shaderColors = IntArray(spectrumColors.size) { index ->
                    val color = spectrumColors[index]
                    Color.argb(
                        alpha,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    )
                }

                paint.shader = LinearGradient(
                    0f,
                    height.toFloat(),
                    0f,
                    0f,
                    shaderColors,
                    null,
                    Shader.TileMode.CLAMP
                )

                val neonStrength = glowPercent.coerceIn(0, 100) / 100f * glowStyleFactor(effectStyle)
                val glowInsetX = if (neonStrength > 0f && opacityPercent > 0) minOf(dp(14).toFloat() * neonStrength, width * 0.14f) else 0f
                val glowInsetY = 0f // Do not reserve transparent room along Single meter length; glow clips naturally at the display edge.
                val innerHeight = (height.toFloat() - glowInsetY * 2f).coerceAtLeast(1f)
                val fillTop = glowInsetY + innerHeight * (1f - level.coerceIn(0f, 1f))
                val meterBody = RectF(glowInsetX, fillTop, width.toFloat() - glowInsetX, height.toFloat() - glowInsetY)
                if (neonStrength > 0f) {
                    drawNeonAura(canvas, paint, meterBody, spectrumColors.first(), neonStrength, dp(10).toFloat() * neonStrength, radius)
                    paint.shader = LinearGradient(
                        0f, height.toFloat() - glowInsetY, 0f, glowInsetY,
                        shaderColors, null, Shader.TileMode.CLAMP
                    )
                }
                paint.alpha = alpha

                canvas.drawRoundRect(meterBody, radius, radius, paint)

                paint.clearShadowLayer()
                paint.shader = null

                if (effectStyle == "GLOSSY" || effectStyle == "GLASS") {
                    paint.color = if (effectStyle == "GLASS") {
                        0x77FFFFFF
                    } else {
                        0x70FFFFFF
                    }
                    paint.alpha = alpha

                    canvas.drawRoundRect(
                        meterBody.left + meterBody.width() * 0.16f,
                        fillTop + dp(2).toFloat(),
                        meterBody.left + meterBody.width() * 0.30f,
                        meterBody.bottom - dp(2).toFloat(),
                        radius,
                        radius,
                        paint
                    )
                    paint.alpha = 255
                }

                drawPeakIndicator(canvas, glowInsetX, glowInsetY)
                return
            }

            // GAP > 0: hitung seluruh geometri dengan Float agar tidak terjadi
            // benturan tipe Int/Float dan semua 24 segmen tetap muat.
            val requestedGap = visualGapPx(gapDp)
            val minimumSegmentHeight = dp(2).toFloat()

            val segmentedGlowStrength = glowPercent.coerceIn(0, 100) / 100f * glowStyleFactor(effectStyle)
            val segmentedInsetX = if (segmentedGlowStrength > 0f && opacityPercent > 0) minOf(dp(14).toFloat() * segmentedGlowStrength, width * 0.14f) else 0f
            val segmentedInsetY = 0f // Keep all 24 Single segments aligned to the full longitudinal surface.
            val innerHeight = (height.toFloat() - segmentedInsetY * 2f).coerceAtLeast(minimumSegmentHeight * SEGMENT_COUNT.toFloat())
            val availableForGap = (innerHeight - minimumSegmentHeight * SEGMENT_COUNT.toFloat()).coerceAtLeast(0f)

            val maxGap = availableForGap / (SEGMENT_COUNT - 1).toFloat()
            val gap = min(requestedGap, maxGap)

            val segmentHeight = (innerHeight - gap * (SEGMENT_COUNT - 1).toFloat()) / SEGMENT_COUNT.toFloat()

            if (segmentHeight <= 0f) return
            val gapRatio = if (maxGap > 0f) (gap / maxGap).coerceIn(0f, 1f) else 0f
            val radiusScale = 1f - gapRatio * 0.78f

            for (index in 0 until SEGMENT_COUNT) {
                val threshold = (index + 1).toFloat() / SEGMENT_COUNT.toFloat()
                val active = level >= threshold

                val colorIndex = (
                    index * spectrumColors.size / SEGMENT_COUNT
                ).coerceIn(0, spectrumColors.lastIndex)
                val color = spectrumColors[colorIndex]

                val top = segmentedInsetY + innerHeight -
                (index + 1).toFloat() * segmentHeight -
                index.toFloat() * gap
                val bottom = top + segmentHeight
                val segmentRadius = min(radius * radiusScale, segmentHeight / 2f)
                val neonStrength = if (active) glowPercent.coerceIn(0, 100) / 100f else 0f
                val segmentBody = RectF(segmentedInsetX, top, width.toFloat() - segmentedInsetX, bottom)

                if (neonStrength > 0f) {
                    drawNeonAura(canvas, paint, segmentBody, color, neonStrength, dp(10).toFloat() * neonStrength, segmentRadius)
                }

                paint.color = if (active) {
                    Color.argb(
                        alpha,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    )
                } else {
                    Color.argb(
                        shadowOpacityPercent.coerceIn(0, 100) * 255 / 100 * 10 / 100,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    )
                }

                canvas.drawRoundRect(
                    segmentBody,
                    segmentRadius,
                    segmentRadius,
                    paint
                )
                paint.clearShadowLayer()

                if (active &&
                    (effectStyle == "GLOSSY" || effectStyle == "GLASS") &&
                    segmentHeight > dp(2).toFloat()
                ) {
                    paint.color = if (effectStyle == "GLASS") {
                        0x66FFFFFF
                    } else {
                        0x60FFFFFF
                    }
                    paint.alpha = alpha

                    canvas.drawRoundRect(
                        segmentBody.left + segmentBody.width() * 0.13f,
                        top + dp(1).toFloat(),
                        segmentBody.left + segmentBody.width() * 0.28f,
                        bottom - dp(1).toFloat(),
                        segmentRadius,
                        segmentRadius,
                        paint
                    )
                    paint.alpha = 255
                }
            }

            drawPeakIndicator(canvas, segmentedInsetX, segmentedInsetY)
        }

        private fun drawPeakIndicator(canvas: Canvas, insetX: Float = 0f, insetY: Float = 0f) {
            if (width <= 0 || height <= 0) return
            val indicatorHeight = dp(2).toFloat().coerceAtMost(height.toFloat())
            val indicatorRadius = indicatorHeight / 2f

            fun drawIndicator(value: Float, opacity: Int) {
                if (opacity <= 0 || value <= 0.04f) return
                val innerHeight = (height.toFloat() - insetY * 2f).coerceAtLeast(1f)
                val top = (insetY + innerHeight * (1f - value.coerceIn(0f, 1f)) - indicatorHeight / 2f)
                    .coerceIn(insetY, (height.toFloat() - insetY - indicatorHeight).coerceAtLeast(insetY))
                paint.shader = null
                paint.clearShadowLayer()
                paint.color = Color.WHITE
                paint.alpha = (if (effectStyle == "CLEAN") 170 else 230) * opacity.coerceIn(0, 100) / 100
                canvas.drawRoundRect(insetX, top, width.toFloat() - insetX, top + indicatorHeight, indicatorRadius, indicatorRadius, paint)
                paint.alpha = 255
            }

            if (abs(peak - level) > 0.025f) drawIndicator(peak, shadowOpacityPercent)
            drawIndicator(level, opacityPercent)
        }
    } // PENTING: SegmentedMeterView ditutup di sini.

    // -----------------------------------------------------------------
    // AUDIO LEVEL -> BAR VIEW
    // Fungsi ini milik FloatingVisualizerService, BUKAN milik View.
    // -----------------------------------------------------------------

    private fun horizontalBandWindow(index: Int, countValue: Int): Pair<Float, Float> {
        val count = countValue.coerceAtLeast(1)
        val isMirrored = mirrorMode == "HORIZONTAL"
        val isSymmetric = horizontalAlignment == "CENTER" || isMirrored
        val uniqueCount = if (isSymmetric) ((count + 1) / 2).coerceAtLeast(1) else count
        val uniqueIndex = if (isSymmetric) min(index, count - 1 - index) else index
        var normalizedCenter = (uniqueIndex + 0.5f) / uniqueCount.toFloat()
        if (horizontalAlignment == "RIGHT") normalizedCenter = 1f - normalizedCenter

        val windowWidth = MAX_BAR_COUNT.toFloat() / uniqueCount.toFloat()
        val center = normalizedCenter * MAX_BAR_COUNT.toFloat()
        val start = (center - windowWidth / 2f).coerceIn(0f, MAX_BAR_COUNT.toFloat())
        val end = (center + windowWidth / 2f).coerceIn(0f, MAX_BAR_COUNT.toFloat())
        return Pair(start, end)
    }

    private fun sampleHorizontalLevel(levels: FloatArray, index: Int, count: Int): Float {
        if (levels.isEmpty()) return 0f
        val (start, end) = horizontalBandWindow(index, count)
        var weighted = 0f
        var totalWeight = 0f
        val firstBand = floor(start.toDouble()).toInt().coerceIn(0, MAX_BAR_COUNT - 1)
        val lastBand = floor((end - 0.0001f).coerceAtLeast(start).toDouble()).toInt().coerceIn(firstBand, MAX_BAR_COUNT - 1)
        for (band in firstBand..lastBand) {
            val overlap = (min(end, band + 1f) - max(start, band.toFloat())).coerceAtLeast(0f)
            if (overlap > 0f) {
                weighted += levels.getOrElse(band) { 0f }.coerceIn(0f, 1f) * overlap
                totalWeight += overlap
            }
        }
        return (weighted / totalWeight.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
    }

    private fun frequencyLabel(value: Double): String = if (value >= 1000.0) {
        String.format(java.util.Locale.US, "%.2f kHz", value / 1000.0)
    } else {
        String.format(java.util.Locale.US, "%.0f Hz", value)
    }

    private fun barFrequencyDescription(index: Int, count: Int): String {
        val (start, end) = horizontalBandWindow(index, count)
        val ratio = MAX_SPECTRUM_HZ / MIN_SPECTRUM_HZ
        val lowHz = MIN_SPECTRUM_HZ * ratio.pow(start.toDouble() / MAX_BAR_COUNT)
        val highHz = MIN_SPECTRUM_HZ * ratio.pow(end.toDouble() / MAX_BAR_COUNT)
        return "Band ${index + 1}: ${frequencyLabel(lowHz)}–${frequencyLabel(highHz)}"
    }

    private fun updateFloatingBars(levels: FloatArray) {
        if (floatingBars.isEmpty()) return

        if (isVerticalMode) {
            val level = if (singleAudioMode == "DJ") {
                levels.getOrElse(MAX_BAR_COUNT) { 0f }
            } else {
                levels.getOrElse(MAX_BAR_COUNT + 1) { 0f }
            }
            (floatingBars.firstOrNull() as? SegmentedMeterView)?.setAudioLevel(level)
        } else {
            val count = floatingBars.size.coerceAtLeast(1)
            floatingBars.forEachIndexed { index, bar ->
                val level = sampleHorizontalLevel(levels, index, count)
                (bar as? EffectBarView)?.setAudioLevel(level)
            }
        }
    }

    // -----------------------------------------------------------------
    // OVERLAY CLEANUP
    // -----------------------------------------------------------------

    private fun removeOverlay() {
        removeLayeredWindow()
        windowView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // View mungkin sudah dilepas oleh WindowManager.
            }
        }

        windowView = null
        contentView = null
        overlayParams = null
        overlayCenterXpx = null
        overlayCenterYpx = null
        floatingBars.clear()
        renderedVertical = null
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun drawTopRoundedColumn(canvas: Canvas, paint: Paint, rect: RectF, requestedRadius: Float) {
        val radius = requestedRadius.coerceAtLeast(0f).coerceAtMost(min(rect.width() / 2f, rect.height() / 2f))
        if (radius <= 0.5f) {
            val oldAa = paint.isAntiAlias
            paint.isAntiAlias = false
            canvas.drawRect(rect, paint)
            paint.isAntiAlias = oldAa
            return
        }
        canvas.drawRoundRect(
            RectF(rect.left, rect.top, rect.right, min(rect.bottom, rect.top + radius * 2f)),
            radius, radius, paint
        )
        canvas.drawRect(rect.left, rect.top + radius, rect.right, rect.bottom, paint)
    }

    private fun drawBottomRoundedColumn(canvas: Canvas, paint: Paint, rect: RectF, requestedRadius: Float) {
        val radius = requestedRadius.coerceAtLeast(0f).coerceAtMost(min(rect.width() / 2f, rect.height() / 2f))
        if (radius <= 0.5f) {
            val oldAa = paint.isAntiAlias
            paint.isAntiAlias = false
            canvas.drawRect(rect, paint)
            paint.isAntiAlias = oldAa
            return
        }
        canvas.drawRoundRect(
            RectF(rect.left, max(rect.top, rect.bottom - radius * 2f), rect.right, rect.bottom),
            radius, radius, paint
        )
        canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom - radius, paint)
    }

    /** Perceptual visual gap: step 1 is subtle on high-density screens. */
    private fun visualGapPx(value: Int): Float {
        if (value <= 0) return 0f
        return (dp(value).toFloat() * 0.55f).coerceAtLeast(0.55f)
    }

    private fun glowStyleFactor(effect: String): Float = when (effect) {
        "NEON" -> 1f
        "GLOSSY" -> 0.82f
        "GLASS" -> 0.70f
        "CLEAN" -> 0.42f
        else -> 0f
    }

    /**
     * Soft spectrum halo. Android's hardware Canvas supports non-text shadow layers
     * from API 28, which includes Android 11/API 30. Older API 26-27 devices use a
     * bounded set of anti-aliased translucent fills; no BlurMaskFilter is used.
     */
    private fun drawSpectrumGlow(
        canvas: Canvas, paint: Paint, body: RectF, color: Int, effect: String,
        glowPercent: Int, opacityPercent: Int, cornerRadius: Float,
        requestedSpread: Float = 0f
    ) {
        val strength = glowPercent.coerceIn(0, 100) / 100f * glowStyleFactor(effect)
        val opacity = opacityPercent.coerceIn(0, 100) / 100f
        if (strength <= 0f || opacity <= 0f || body.width() <= 0f || body.height() <= 0f) return

        val maxSpread = (if (requestedSpread > 0f) requestedSpread else dp(13).toFloat() * strength)
            .coerceIn(dp(3).toFloat(), dp(18).toFloat())
        val shadowAlpha = (220f * strength * opacity).toInt().coerceIn(0, 195)
        if (shadowAlpha <= 0) return

        // The clip removes the bar's own interior from the glow pass. The main renderer
        // paints the real gradient/opacity once, preventing a brighter or denser bar.
        spectrumGlowClipPath.reset()
        spectrumGlowClipPath.addRoundRect(
            body, cornerRadius.coerceAtLeast(0f), cornerRadius.coerceAtLeast(0f), Path.Direction.CW
        )
        val checkpoint = canvas.save()
        canvas.clipOutPath(spectrumGlowClipPath)

        val glow = spectrumGlowPaint
        glow.reset()
        glow.isAntiAlias = true
        glow.style = Paint.Style.FILL
        glow.color = Color.WHITE
        glow.alpha = 255

        if (Build.VERSION.SDK_INT >= 28 && canvas.isHardwareAccelerated) {
            // Two blur radii build a smooth outer aura and a tighter luminous bloom.
            // API 28+ hardware canvases support non-text shadow layers; Android 11 is API 30.
            glow.setShadowLayer(
                maxSpread, 0f, 0f,
                Color.argb(shadowAlpha * 62 / 100, Color.red(color), Color.green(color), Color.blue(color))
            )
            canvas.drawRoundRect(body, cornerRadius, cornerRadius, glow)
            glow.clearShadowLayer()
            glow.setShadowLayer(
                maxSpread * 0.56f, 0f, 0f,
                Color.argb(shadowAlpha, Color.red(color), Color.green(color), Color.blue(color))
            )
            canvas.drawRoundRect(body, cornerRadius, cornerRadius, glow)
            glow.clearShadowLayer()
        } else {
            // GPU-compatible fallback for API 26-27 or a software Canvas. Draw nested,
            // antialiased rings from faint/wide to brighter/tight, clipped outside the bar.
            for (pass in 8 downTo 1) {
                val spread = maxSpread * pass / 8f
                val rect = RectF(
                    body.left - spread, body.top - spread * 0.72f,
                    body.right + spread, body.bottom + spread * 0.72f
                )
                val factor = (1f - pass / 9f) * 0.18f + 0.025f
                val alpha = (shadowAlpha * factor).toInt().coerceIn(1, 60)
                glow.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
                val radius = (cornerRadius + spread).coerceAtLeast(0f)
                canvas.drawRoundRect(rect, radius, radius, glow)
            }
        }

        glow.clearShadowLayer()
        canvas.restoreToCount(checkpoint)
    }

    // -----------------------------------------------------------------
    // SERVICE LIFECYCLE — HARUS BERADA DI DALAM FloatingVisualizerService
    // -----------------------------------------------------------------

    override fun onDestroy() {
        stopping = true
        cancelSpectrumAnimationFrame()
        stopEngineResources()
        removeOverlay()
        setServiceActive(false)

        getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        .edit()
        .putBoolean("backup_capture", false)
        .apply()

        stopForegroundCompat()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
