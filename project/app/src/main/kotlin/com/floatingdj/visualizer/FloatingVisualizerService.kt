package com.floatingdj.visualizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.LinearGradient
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class FloatingVisualizerService : Service() {

    companion object {
        const val ACTION_START_PRIMARY = "com.floatingdj.visualizer.START_PRIMARY"
        const val ACTION_ENABLE_BACKUP = "com.floatingdj.visualizer.ENABLE_BACKUP"
        const val ACTION_DISABLE_BACKUP = "com.floatingdj.visualizer.DISABLE_BACKUP"
        const val ACTION_UPDATE = "com.floatingdj.visualizer.UPDATE"
        const val ACTION_STOP = "com.floatingdj.visualizer.STOP"
        const val ACTION_LEVELS = "com.floatingdj.visualizer.LEVELS"
        const val ACTION_POSITION = "com.floatingdj.visualizer.POSITION"
        const val ACTION_ENGINE_STATUS = "com.floatingdj.visualizer.ENGINE_STATUS"
        const val ACTION_NOTIFICATION_TOGGLE_OVERLAY = "com.floatingdj.visualizer.NOTIFICATION_TOGGLE_OVERLAY"
        const val ACTION_NOTIFICATION_TOGGLE_AUDIO = "com.floatingdj.visualizer.NOTIFICATION_TOGGLE_AUDIO"

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
        const val EXTRA_GLOW = "extra_glow"
        const val EXTRA_RESPONSE = "extra_response_speed"
        const val EXTRA_CORNER_RADIUS = "extra_corner_radius"
        const val EXTRA_EFFECT = "extra_effect"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        const val EXTRA_SINGLE_MODE = "extra_single_mode"
        const val EXTRA_BASS_MIN_HZ = "extra_bass_min_hz"
        const val EXTRA_BASS_MAX_HZ = "extra_bass_max_hz"
        const val EXTRA_POSITION_X = "extra_position_x"
        const val EXTRA_POSITION_Y = "extra_position_y"
        const val EXTRA_POSITION_MODE = "extra_position_mode"
        const val EXTRA_ROTATION = "extra_rotation"
        const val EXTRA_FLOATING_ENABLED = "extra_floating_enabled"

        private const val CHANNEL_ID = "floating_dj_audio"
        private const val NOTIFICATION_ID = 9001
        private const val SAMPLE_RATE = 44100
        private const val FFT_SIZE = 4096
        private const val HOP_SIZE = 1024
        private const val MAX_BAR_COUNT = 64
        private const val SEGMENT_COUNT = 24
    }

    private enum class EngineSource { NONE, VISUALIZER, PROJECTION }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowView: FrameLayout? = null
    private var contentView: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private val floatingBars = mutableListOf<View>()
    private var renderedVertical: Boolean? = null

    private var visualizer: Visualizer? = null
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var projection: MediaProjection? = null
    private var engineSource = EngineSource.NONE

    @Volatile private var backupRunning = false
    @Volatile private var stopping = false
    @Volatile private var bassMinHz = 60
    @Volatile private var bassMaxHz = 160

    private var isVerticalMode = false
    private var widthDp = 320
    private var heightDp = 360
    private var barCount = 15
    private var barWidthDp = 20
    private var gapDp = 3
    private var scalePercent = 100
    private var opacityPercent = 100
    private var glowPercent = 65
    private var responsePercent = 82
    private var cornerRadiusDp = 14
    private var singleAudioMode = "MUSIC"
    private var positionMode = "STATIC"
    private var positionXdp = 0
    private var positionYdp = 0
    private var rotationDegrees = 0
    private var effectStyle = "GLOSSY"
    private var floatingOverlayEnabled = false

    private var spectrumColors = intArrayOf(
        Color.parseColor("#22D3EE"),
        Color.parseColor("#A855F7"),
        Color.parseColor("#22C55E"),
        Color.parseColor("#FACC15"),
        Color.parseColor("#EF4444")
    )

    private val smoothedLevels = FloatArray(MAX_BAR_COUNT)
    // Reused by the optional PCM/MediaProjection FFT loop to avoid per-frame
    // 64 KB allocations that can create avoidable garbage-collection jitter.
    private val pcmFftReal = DoubleArray(FFT_SIZE)
    private val pcmFftImag = DoubleArray(FFT_SIZE)
    private var smoothedBass = 0f
    private var smoothedMusic = 0f
    private var switchingSource = false
    private var foregroundStarted = false
    private var audioPaused = false
    private var mediaSession: MediaSession? = null

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
            if (success) broadcastStatus("PRIMARY_ACTIVE: Projection ended; Visualizer resumed")
            else broadcastStatus("BACKUP_ERROR: Projection ended and primary Visualizer is unavailable")
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        createMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val oldOverlaySetting = floatingOverlayEnabled
        readSettings(intent)

        when (intent.action) {
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
                if (!Settings.canDrawOverlays(this)) {
                    floatingOverlayEnabled = false
                    getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
                        .putBoolean("floating_overlay_enabled", false)
                        .apply()
                    updateFloatingWindow()
                    broadcastStatus("OVERLAY_PERMISSION_REQUIRED: Allow display over other apps from the app")
                } else {
                    floatingOverlayEnabled = !floatingOverlayEnabled
                    getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
                        .putBoolean("floating_overlay_enabled", floatingOverlayEnabled)
                        .apply()
                    updateFloatingWindow()
                    broadcastStatus(if (floatingOverlayEnabled) "OVERLAY_ACTIVE: Floating overlay shown" else "OVERLAY_HIDDEN: Floating overlay hidden")
                }
                refreshNotification("Audio ${if (engineSource == EngineSource.NONE) "paused" else "active"}")
                return START_STICKY
            }
            ACTION_NOTIFICATION_TOGGLE_AUDIO -> {
                handleNotificationAudioToggle()
                return START_STICKY
            }
            ACTION_UPDATE -> {
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
                            refreshNotification("Audio paused · tap Resume to continue")
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
                    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, ActivityResult.RESULT_CANCELED)
                    val resultData = getProjectionIntent(intent)
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
        mediaSession?.setActive(true)
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
        mediaSession?.setActive(true)
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating DJ Audio Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Real-time visualizer audio engine status" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun createMediaSession() {
        if (mediaSession != null) return
        mediaSession = MediaSession(this, "FloatingDJVisualizer").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Floating DJ Visualizer")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "Real-time Audio Spectrum")
                    .build()
            )
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    mainHandler.post { handleNotificationAudioToggle() }
                }

                override fun onPause() {
                    mainHandler.post {
                        if (engineSource != EngineSource.NONE) pauseAudioFromNotification()
                    }
                }
            }, mainHandler)
        }
    }

    private fun updateMediaSessionState() {
        val active = engineSource != EngineSource.NONE
        val state = when {
            active -> PlaybackState.STATE_PLAYING
            audioPaused -> PlaybackState.STATE_PAUSED
            else -> PlaybackState.STATE_STOPPED
        }
        val actions = if (active) {
            PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP
        } else {
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE
        }
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (active) 1f else 0f)
                .build()
        )
    }

    private fun pauseAudioFromNotification() {
        if (engineSource == EngineSource.NONE) return
        switchingSource = true
        releaseVisualizer()
        stopProjectionResources()
        engineSource = EngineSource.NONE
        switchingSource = false
        removeOverlay()
        audioPaused = true
        setServiceActive(false)
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
            .putBoolean("service_paused", true)
            .putBoolean("backup_capture", false)
            .apply()
        broadcastStatus("AUDIO_PAUSED: Audio analysis paused from notification")
        refreshNotification("Audio paused · tap Resume to continue")
    }

    private fun handleNotificationAudioToggle() {
        if (engineSource == EngineSource.NONE) {
            try {
                startPrimaryForeground()
                val resumed = startPrimaryEngineInternal()
                if (!resumed) broadcastStatus("PRIMARY_ERROR: Audio engine could not resume from notification")
            } catch (e: Exception) {
                broadcastStatus("PRIMARY_ERROR: ${e.message ?: "Audio engine could not resume"}")
            }
        } else {
            pauseAudioFromNotification()
        }
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

    private fun createNotification(message: String): Notification {
        val audioActive = engineSource != EngineSource.NONE
        val overlayLabel = if (floatingOverlayEnabled) "FLOATING ON" else "FLOATING OFF"
        val audioLabel = when {
            audioActive -> "PAUSE"
            audioPaused -> "RESUME"
            else -> "START"
        }
        val backupActive = engineSource == EngineSource.PROJECTION
        val backupLabel = if (backupActive) "M.PROJ ON" else "M.PROJ OFF"

        updateMediaSessionState()

        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val mediaStyle = Notification.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        mediaSession?.sessionToken?.let { mediaStyle.setMediaSession(it) }

        return builder
            .setContentTitle("Floating DJ Visualizer")
            .setContentText(
                "$message · Floating ${if (floatingOverlayEnabled) "ON" else "OFF"} · M.Proj ${if (backupActive) "ON" else "OFF"}"
            )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent())
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(mediaStyle)
            // Native MediaStyle keeps the three controls consistent with Android System UI.
            .addAction(
                if (floatingOverlayEnabled) android.R.drawable.ic_menu_view else android.R.drawable.ic_menu_crop,
                overlayLabel,
                serviceActionPendingIntent(ACTION_NOTIFICATION_TOGGLE_OVERLAY, 9101)
            )
            .addAction(
                if (audioActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                audioLabel,
                serviceActionPendingIntent(ACTION_NOTIFICATION_TOGGLE_AUDIO, 9102)
            )
            .addAction(
                if (backupActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_menu_manage,
                backupLabel,
                backupTogglePendingIntent()
            )
            .build()
    }

    private fun refreshNotification(message: String) {
        if (!foregroundStarted) return
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
        mediaSession?.setActive(false)
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE).edit()
            .putBoolean("service_paused", false)
            .apply()
    }

    // -----------------------------------------------------------------
    // SETTINGS
    // -----------------------------------------------------------------

    private fun readSettings(intent: Intent) {
        isVerticalMode = intent.getBooleanExtra(EXTRA_VERTICAL, isVerticalMode)
        widthDp = intent.getIntExtra(EXTRA_WIDTH, widthDp).coerceIn(100, 1800)
        heightDp = intent.getIntExtra(EXTRA_HEIGHT, heightDp).coerceIn(60, 1800)
        barCount = intent.getIntExtra(EXTRA_BAR_COUNT, barCount).coerceIn(5, MAX_BAR_COUNT)
        barWidthDp = intent.getIntExtra(EXTRA_BAR_WIDTH, barWidthDp).coerceIn(2, 140)
        gapDp = intent.getIntExtra(EXTRA_GAP, gapDp).coerceIn(0, 50)
        scalePercent = intent.getIntExtra(EXTRA_SCALE, scalePercent).coerceIn(40, 220)
        opacityPercent = intent.getIntExtra(EXTRA_OPACITY, opacityPercent).coerceIn(5, 100)
        glowPercent = intent.getIntExtra(EXTRA_GLOW, glowPercent).coerceIn(0, 100)
        responsePercent = intent.getIntExtra(EXTRA_RESPONSE, responsePercent).coerceIn(0, 100)
        cornerRadiusDp = intent.getIntExtra(EXTRA_CORNER_RADIUS, cornerRadiusDp).coerceIn(0, 50)
        intent.getIntArrayExtra(EXTRA_COLORS)?.takeIf { it.size >= 2 }?.let { spectrumColors = it.copyOf() }
        effectStyle = intent.getStringExtra(EXTRA_EFFECT) ?: effectStyle
        floatingOverlayEnabled = intent.getBooleanExtra(EXTRA_FLOATING_ENABLED, floatingOverlayEnabled)
        singleAudioMode = intent.getStringExtra(EXTRA_SINGLE_MODE) ?: singleAudioMode
        bassMinHz = intent.getIntExtra(EXTRA_BASS_MIN_HZ, bassMinHz).coerceIn(30, 300)
        bassMaxHz = intent.getIntExtra(EXTRA_BASS_MAX_HZ, bassMaxHz).coerceIn(bassMinHz + 20, 600)
        positionXdp = intent.getIntExtra(EXTRA_POSITION_X, positionXdp).coerceAtLeast(0)
        positionYdp = intent.getIntExtra(EXTRA_POSITION_Y, positionYdp).coerceAtLeast(0)
        positionMode = intent.getStringExtra(EXTRA_POSITION_MODE) ?: positionMode
        rotationDegrees = intent.getIntExtra(EXTRA_ROTATION, rotationDegrees).coerceIn(0, 359)
    }

    // -----------------------------------------------------------------
    // PRIMARY ENGINE: ANDROID VISUALIZER, SESSION 0
    // -----------------------------------------------------------------

    private fun startPrimaryEngineInternal(): Boolean {
        stopping = false
        releaseVisualizer()
        stopProjectionResources()

        var candidate: Visualizer? = null

        return try {
            val engine = Visualizer(0)
            candidate = engine

            val range = Visualizer.getCaptureSizeRange()
            if (range.size < 2 || range[0] <= 0 || range[1] < range[0]) {
                throw IllegalStateException("Visualizer capture-size range tidak valid")
            }

            // Visualizer capture size must be a power of two inside its reported range.
            val cappedMaximum = min(1024, range[1])
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

            val rate = min(Visualizer.getMaxCaptureRate(), 20000)
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
                        val levels = calculateVisualizerSpectrum(fft, samplingRate)
                        publishLevels(levels)
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
        val result = FloatArray(MAX_BAR_COUNT + 2)
        val binCount = (fft.size / 2).coerceAtLeast(2)
        val sampleRate = (samplingRateMilliHz / 1000.0).takeIf { it > 1000.0 } ?: 44100.0
        val nyquist = sampleRate / 2.0

        fun magnitude(bin: Int): Double {
            val index = bin * 2
            if (index + 1 >= fft.size) return 0.0
            val real = fft[index].toInt().toDouble()
            val imag = fft[index + 1].toInt().toDouble()
            return sqrt(real * real + imag * imag)
        }

        for (band in 0 until barCount) {
            val lowHz = 45.0 * (12000.0 / 45.0).pow(band.toDouble() / barCount)
            val highHz = 45.0 * (12000.0 / 45.0).pow((band + 1).toDouble() / barCount)
            val first = ((lowHz / nyquist) * binCount).toInt().coerceIn(1, binCount - 1)
            val last = (((highHz / nyquist) * binCount).toInt()).coerceIn(first + 1, binCount)
            var peak = 0.0
            for (bin in first until last) peak = max(peak, magnitude(bin))
            val raw = (peak / 100.0).coerceIn(0.0, 1.0)
            val target = sqrt(raw).toFloat()
            smoothedLevels[band] = smooth(smoothedLevels[band], target, responseAttack(), responseRelease())
            result[band] = smoothedLevels[band]
        }

        val firstBass = ((bassMinHz / nyquist) * binCount).toInt().coerceIn(1, binCount - 1)
        val lastBass = ((bassMaxHz / nyquist) * binCount).toInt().coerceIn(firstBass + 1, binCount)
        var bassPeak = 0.0
        for (bin in firstBass until lastBass) bassPeak = max(bassPeak, magnitude(bin))
        val bassTarget = (bassPeak / 100.0).coerceIn(0.0, 1.0).pow(0.58).toFloat()
        smoothedBass = smooth(smoothedBass, bassTarget, (responseAttack() + 0.04f).coerceAtMost(0.99f), responseRelease())
        result[MAX_BAR_COUNT] = smoothedBass

        var power = 0f
        for (band in 0 until barCount) power += smoothedLevels[band] * smoothedLevels[band]
        val musicTarget = sqrt(power / barCount.toFloat()).coerceIn(0f, 1f)
        smoothedMusic = smooth(smoothedMusic, musicTarget, responseAttack(), responseRelease())
        result[MAX_BAR_COUNT + 1] = smoothedMusic
        return result
    }

    private fun responseFactor(): Float = responsePercent.coerceIn(0, 100) / 100f

    // Higher values mean faster attack and quicker recovery, shared by both capture engines.
    private fun responseAttack(): Float = 0.56f + responseFactor() * 0.40f

    private fun responseRelease(): Float = 0.20f + responseFactor() * 0.62f

    private fun smooth(previous: Float, target: Float, attack: Float, release: Float): Float {
        val factor = if (target >= previous) attack else release
        return previous + (target - previous) * factor
    }

    // -----------------------------------------------------------------
    // BACKUP ENGINE: MEDIAPROJECTION AUDIO PLAYBACK CAPTURE (ANDROID 10+)
    // -----------------------------------------------------------------

    private fun startBackupEngine(resultCode: Int, resultData: Intent): Boolean {
        if (Build.VERSION.SDK_INT < 29) throw IllegalStateException("MediaProjection audio capture membutuhkan Android 10+")
        stopping = false
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
        .setBufferSizeInBytes(max(minBuffer, FFT_SIZE * 4))
        .setAudioPlaybackCaptureConfig(config)
        .build()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Audio playback capture gagal diinisialisasi")
        }
        audioRecord = recorder
        recorder.startRecording()
        engineSource = EngineSource.PROJECTION
        updateFloatingWindow()
        startBackupThread()
        setServiceActive(true)
        broadcastStatus("BACKUP_ACTIVE: MediaProjection audio capture is active")
        refreshNotification("MediaProjection backup audio active")
        return true
    }

    private fun startBackupThread() {
        backupRunning = true
        val recorder = audioRecord ?: return
        captureThread = Thread {
            val frame = ShortArray(FFT_SIZE)
            val chunk = ShortArray(HOP_SIZE)
            var filled = 0
            while (backupRunning) {
                val read = try {
                    recorder.read(chunk, 0, HOP_SIZE, AudioRecord.READ_BLOCKING)
                } catch (e: Exception) {
                    android.util.Log.e("FloatingDJ", "Backup read failed", e)
                    -1
                }
                if (read <= 0) break
                if (read > FFT_SIZE) continue
                System.arraycopy(frame, read, frame, 0, FFT_SIZE - read)
                System.arraycopy(chunk, 0, frame, FFT_SIZE - read, read)
                filled = min(FFT_SIZE, filled + read)
                if (filled < FFT_SIZE) continue
                val levels = calculatePcmSpectrum(frame)
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
                        val resumed = startPrimaryEngineInternal()
                        if (!resumed) setServiceActive(false)
                    }
                }
            }
        }.apply {
            name = "FloatingDJ-ProjectionAudio"
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    private fun calculatePcmSpectrum(samples: ShortArray): FloatArray {
        val real = pcmFftReal
        val imag = pcmFftImag
        real.fill(0.0)
        imag.fill(0.0)
        for (i in 0 until FFT_SIZE) {
            val sample = samples[i].toDouble() / 32768.0
            val hann = 0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))
            real[i] = sample * hann
        }
        var j = 0
        for (i in 1 until FFT_SIZE) {
            var bit = FFT_SIZE shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
        }
        var length = 2
        while (length <= FFT_SIZE) {
            val angle = -2.0 * PI / length
            val rootR = cos(angle)
            val rootI = sin(angle)
            var start = 0
            while (start < FFT_SIZE) {
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
        for (band in 0 until barCount) {
            val low = 45.0 * (12000.0 / 45.0).pow(band.toDouble() / barCount)
            val high = 45.0 * (12000.0 / 45.0).pow((band + 1).toDouble() / barCount)
            val first = max(1, (low * FFT_SIZE / SAMPLE_RATE).toInt())
            val last = min(FFT_SIZE / 2, max(first + 1, (high * FFT_SIZE / SAMPLE_RATE).toInt()))
            var peak = 0.0
            for (bin in first until last) peak = max(peak, sqrt(real[bin] * real[bin] + imag[bin] * imag[bin]))
            val target = sqrt((peak / FFT_SIZE * 9.0).coerceIn(0.0, 1.0)).toFloat()
            smoothedLevels[band] = smooth(smoothedLevels[band], target, responseAttack(), responseRelease())
            result[band] = smoothedLevels[band]
        }
        val firstBass = max(1, (bassMinHz * FFT_SIZE / SAMPLE_RATE).toInt())
        val lastBass = min(FFT_SIZE / 2, (bassMaxHz * FFT_SIZE / SAMPLE_RATE).toInt()).coerceAtLeast(firstBass)
        var bassPeak = 0.0
        for (bin in firstBass..lastBass) bassPeak = max(bassPeak, sqrt(real[bin] * real[bin] + imag[bin] * imag[bin]))
        val bassTarget = (bassPeak / FFT_SIZE * 12.0).coerceIn(0.0, 1.0).pow(0.60).toFloat()
        smoothedBass = smooth(smoothedBass, bassTarget, (responseAttack() + 0.04f).coerceAtMost(0.99f), responseRelease())
        result[MAX_BAR_COUNT] = smoothedBass
        var sum = 0f
        for (band in 0 until barCount) sum += smoothedLevels[band] * smoothedLevels[band]
        val musicTarget = sqrt(sum / barCount.toFloat()).coerceIn(0f, 1f)
        smoothedMusic = smooth(smoothedMusic, musicTarget, responseAttack(), responseRelease())
        result[MAX_BAR_COUNT + 1] = smoothedMusic
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
        releaseVisualizer()
        stopProjectionResources()
        engineSource = EngineSource.NONE
    }

    // -----------------------------------------------------------------
    // LEVEL DELIVERY
    // -----------------------------------------------------------------

    private fun publishLevels(levels: FloatArray) {
        val safe = levels.copyOf()
        mainHandler.post {
            if (engineSource != EngineSource.NONE) updateFloatingBars(safe)
        }
        val update = Intent(ACTION_LEVELS)
        .setPackage(packageName)
        .putExtra(EXTRA_LEVELS, safe)
        sendBroadcast(update)
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
            gravity = Gravity.CENTER or Gravity.BOTTOM
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
        params.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 30) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val density = resources.displayMetrics.density
        params.x = (positionXdp * density).toInt()
        params.y = (positionYdp * density).toInt()

        windowView = host
        contentView = content
        overlayParams = params
        try {
            windowManager.addView(host, params)
            applyFloatingDimensions()
        } catch (e: Exception) {
            android.util.Log.e("FloatingDJ", "Overlay creation failed", e)
            removeOverlay()
            // Overlay is optional: a WindowManager failure must not tear down
            // the working audio engine or its foreground service.
            broadcastStatus("OVERLAY_ERROR: Floating window could not be attached: ${e.message ?: "WindowManager error"}")
        }
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
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            return Pair(bounds.width(), bounds.height())
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    private fun addFloatingBars(content: LinearLayout) {
        content.removeAllViews()
        floatingBars.clear()
        content.orientation = if (isVerticalMode) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        if (isVerticalMode) {
            val meter = SegmentedMeterView(this)
            meter.layoutParams = LinearLayout.LayoutParams(ViewGroupSize.MATCH_PARENT, ViewGroupSize.MATCH_PARENT)
            floatingBars.add(meter)
            content.addView(meter)
        } else {
            repeat(barCount) { index ->
                val bar = EffectBarView(this, index)
                floatingBars.add(bar)
                content.addView(bar)
            }
        }
        renderedVertical = isVerticalMode
    }

    private object ViewGroupSize { const val MATCH_PARENT = -1 }

    private fun updateFloatingWindow() {
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
        val expected = if (isVerticalMode) 1 else barCount
        if (renderedVertical != isVerticalMode || floatingBars.size != expected) addFloatingBars(content)
        applyFloatingDimensions()
    }

    private fun applyFloatingDimensions() {
        val host = windowView ?: return
        val content = contentView ?: return
        val params = overlayParams ?: return
        val (contentW, contentH) = localContentSizePx()
        val (boundW, boundH) = rotatedBoundsPx(contentW, contentH)
        params.width = boundW
        params.height = boundH
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        // ACTION_UPDATE receives authoritative persisted coordinates from MainActivity.
        params.x = (positionXdp * density).toInt()
        params.y = (positionYdp * density).toInt()
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
            val requestedGap = if (gapDp == 0) 0 else dp(gapDp)
            val maxGap = if (count > 1) {
                ((contentW - count * dp(1)).coerceAtLeast(0)) / (count - 1)
            } else 0
            val gapPx = min(requestedGap, maxGap)
            val totalGap = gapPx * (count - 1)
            val available = (contentW - totalGap).coerceAtLeast(count * dp(1))
            val requested = dp(barWidthDp).coerceAtLeast(dp(1))
            val actualWidth = min(requested, available / count)
            for ((index, bar) in floatingBars.withIndex()) {
                val left = if (index == 0) 0 else gapPx / 2
                val right = if (index == count - 1) 0 else gapPx - gapPx / 2
                bar.layoutParams = LinearLayout.LayoutParams(actualWidth, contentH).apply {
                    setMargins(left, 0, right, 0)
                }
                bar.invalidate()
            }
        }

        val (screenW, screenH) = displaySizePx()
        val oldPositionX = positionXdp
        val oldPositionY = positionYdp
        params.x = params.x.coerceIn(0, (screenW - boundW).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenH - boundH).coerceAtLeast(0))
        positionXdp = (params.x / resources.displayMetrics.density).toInt()
        positionYdp = (params.y / resources.displayMetrics.density).toInt()
        if (positionXdp != oldPositionX || positionYdp != oldPositionY) savePosition()
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
                positionXdp = (params.x / resources.displayMetrics.density).toInt()
                positionYdp = (params.y / resources.displayMetrics.density).toInt()
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
        canvas: Canvas,
        paint: Paint,
        body: RectF,
        color: Int,
        intensity: Float,
        spreadLimit: Float,
        radius: Float
    ) {
        if (intensity <= 0f || spreadLimit <= 0f || body.width() <= 0f || body.height() <= 0f) return
        paint.shader = null
        paint.clearShadowLayer()
        for (layer in 4 downTo 1) {
            val spread = spreadLimit * layer / 4f
            val aura = RectF(
                body.left - spread,
                body.top - spread * 0.26f,
                body.right + spread,
                body.bottom + spread * 0.18f
            )
            val layerStrength = 0.14f + (4 - layer) * 0.12f
            val alpha = (
                opacityPercent.coerceIn(0, 100) * 2.55f * layerStrength * intensity
            ).toInt().coerceIn(0, 125)
            paint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            val auraRadius = radius + spread
            canvas.drawRoundRect(aura, auraRadius, auraRadius, paint)
        }
    }

    private inner class EffectBarView(
        context: android.content.Context,
        private val index: Int
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var level = 0.03f

        init {
            pivotY = 0f
        }

        fun setAudioLevel(value: Float) {
            val target = value.coerceIn(0.02f, 1f)
            val factor = if (target > level) responseAttack() else responseRelease()
            level += (target - level) * factor
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (height <= 0 || width <= 0 || spectrumColors.isEmpty()) return

            val barHeight = height.toFloat() * level
            if (barHeight <= 0f) return

            val top = height.toFloat() - barHeight
            val rect = RectF(0f, top, width.toFloat(), height.toFloat())
            val colorTop = spectrumColors[(index + 1) % spectrumColors.size]
            val colorBottom = spectrumColors[index % spectrumColors.size]
            val radius = min(dp(cornerRadiusDp).toFloat(), width / 2f)
            val neonStrength = if (effectStyle == "NEON") glowPercent.coerceIn(0, 100) / 100f else 0f
            val glowInset = min(dp(12).toFloat() * neonStrength, width * 0.34f)
            val auraBody = RectF(rect).apply { if (glowInset > 0f) inset(glowInset, 0f) }

            if (neonStrength > 0f) {
                drawNeonAura(canvas, paint, auraBody, colorTop, neonStrength, glowInset, radius)
            }

            paint.shader = LinearGradient(
                0f,
                height.toFloat(),
                0f,
                top,
                colorBottom,
                colorTop,
                Shader.TileMode.CLAMP
            )
            paint.alpha = opacityPercent * 255 / 100
            // Main bar remains fully filled at the selected opacity; NEON only adds light.
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.clearShadowLayer()
            paint.shader = null
            paint.alpha = 255

            if (effectStyle == "GLOSSY" || effectStyle == "GLASS") {
                paint.color = if (effectStyle == "GLASS") {
                    0x66FFFFFF
                } else {
                    0x4DFFFFFF
                }
                paint.alpha = opacityPercent * 255 / 100

                val highlightWidth = max(dp(1).toFloat(), rect.width() * 0.18f)
                canvas.drawRoundRect(
                    rect.left + rect.width() * 0.18f,
                    top + dp(2).toFloat(),
                    rect.left + rect.width() * 0.18f + highlightWidth,
                    height.toFloat() - dp(2).toFloat(),
                    radius,
                    radius,
                    paint
                )

                paint.color = Color.WHITE
                paint.alpha = 65 * opacityPercent / 100
                canvas.drawRoundRect(
                    rect.left,
                    top,
                    rect.right,
                    top + dp(1).toFloat(),
                    radius,
                    radius,
                    paint
                )
                paint.alpha = 255
            }
        }
    } // PENTING: EffectBarView ditutup di sini.

    private inner class SegmentedMeterView(
        context: android.content.Context
    ) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var level = 0f
        private var peak = 0f
        private var peakHoldUntil = 0L
        private var lastFrameTime = SystemClock.uptimeMillis()

        fun setAudioLevel(value: Float) {
            val now = SystemClock.uptimeMillis()
            val target = if (value < 0.018f) 0f else value.coerceIn(0f, 1f)

            val factor = if (target > level) responseAttack() else responseRelease()
            level += (target - level) * factor

            if (level >= peak) {
                peak = level
                peakHoldUntil = now + 115L
            } else if (now > peakHoldUntil) {
                val deltaSeconds = (
                    (now - lastFrameTime).coerceIn(0L, 100L)
                    .toFloat() / 1000f
                )
                peak = (peak - 2.4f * deltaSeconds).coerceAtLeast(level)
            }

            lastFrameTime = now
            invalidate()
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

                val fillTop = height.toFloat() * (1f - level.coerceIn(0f, 1f))
                val neonStrength = if (effectStyle == "NEON") glowPercent.coerceIn(0, 100) / 100f else 0f
                val meterBody = RectF(0f, fillTop, width.toFloat(), height.toFloat())
                val glowInset = min(dp(14).toFloat() * neonStrength, width * 0.38f)
                val auraBody = RectF(meterBody).apply {
                    if (glowInset > 0f) inset(glowInset, 0f)
                }
                if (neonStrength > 0f) {
                    drawNeonAura(canvas, paint, auraBody, spectrumColors.first(), neonStrength, glowInset, radius)
                    // The aura helper uses low-alpha paint for its light layers.
                    // Restore both shader and body alpha so the single bar stays solid.
                    paint.shader = LinearGradient(
                        0f, height.toFloat(), 0f, 0f,
                        shaderColors, null, Shader.TileMode.CLAMP
                    )
                }
                paint.alpha = alpha

                canvas.drawRoundRect(
                    meterBody,
                    radius,
                    radius,
                    paint
                )

                paint.clearShadowLayer()
                paint.shader = null

                if (effectStyle == "GLOSSY" || effectStyle == "GLASS") {
                    paint.color = if (effectStyle == "GLASS") {
                        0x77FFFFFF
                    } else {
                        0x55FFFFFF
                    }
                    paint.alpha = alpha

                    canvas.drawRoundRect(
                        meterBody.left + meterBody.width() * 0.16f,
                        fillTop + dp(2).toFloat(),
                        meterBody.left + meterBody.width() * 0.30f,
                        height.toFloat() - dp(2).toFloat(),
                        radius,
                        radius,
                        paint
                    )
                    paint.alpha = 255
                }

                drawPeakIndicator(canvas)
                return
            }

            // GAP > 0: hitung seluruh geometri dengan Float agar tidak terjadi
            // benturan tipe Int/Float dan semua 24 segmen tetap muat.
            val requestedGap = dp(gapDp).toFloat()
            val minimumSegmentHeight = dp(2).toFloat()

            val availableForGap = (
                height.toFloat() - minimumSegmentHeight * SEGMENT_COUNT.toFloat()
            ).coerceAtLeast(0f)

            val maxGap = availableForGap / (SEGMENT_COUNT - 1).toFloat()
            val gap = min(requestedGap, maxGap)

            val segmentHeight = (
                height.toFloat() - gap * (SEGMENT_COUNT - 1).toFloat()
            ) / SEGMENT_COUNT.toFloat()

            if (segmentHeight <= 0f) return

            for (index in 0 until SEGMENT_COUNT) {
                val threshold = (index + 1).toFloat() / SEGMENT_COUNT.toFloat()
                val active = level >= threshold

                val colorIndex = (
                    index * spectrumColors.size / SEGMENT_COUNT
                ).coerceIn(0, spectrumColors.lastIndex)
                val color = spectrumColors[colorIndex]

                val top = height.toFloat() -
                (index + 1).toFloat() * segmentHeight -
                index.toFloat() * gap
                val bottom = top + segmentHeight
                val segmentRadius = min(radius, segmentHeight / 2f)
                val neonStrength = if (effectStyle == "NEON" && active) glowPercent.coerceIn(0, 100) / 100f else 0f
                val segmentBody = RectF(0f, top, width.toFloat(), bottom)
                val glowInset = min(dp(12).toFloat() * neonStrength, width * 0.36f)
                val auraBody = RectF(segmentBody).apply {
                    if (glowInset > 0f) inset(glowInset, 0f)
                }

                if (neonStrength > 0f) {
                    drawNeonAura(canvas, paint, auraBody, color, neonStrength, glowInset, segmentRadius)
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
                        alpha * 10 / 100,
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
                        0x44FFFFFF
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

            drawPeakIndicator(canvas)
        }

        private fun drawPeakIndicator(canvas: Canvas) {
            if (peak <= 0.04f || width <= 0 || height <= 0) return

            paint.shader = null
            paint.clearShadowLayer()
            paint.color = Color.WHITE
            paint.alpha = if (effectStyle == "CLEAN") 170 else 230

            val peakY = height.toFloat() * (1f - peak.coerceIn(0f, 1f))
            val indicatorHeight = dp(2).toFloat().coerceAtMost(height.toFloat())
            val indicatorRadius = dp(1).toFloat()

            canvas.drawRoundRect(
                0f,
                peakY,
                width.toFloat(),
                (peakY + indicatorHeight).coerceAtMost(height.toFloat()),
                indicatorRadius,
                indicatorRadius,
                paint
            )
            paint.alpha = 255
        }
    } // PENTING: SegmentedMeterView ditutup di sini.

    // -----------------------------------------------------------------
    // AUDIO LEVEL -> BAR VIEW
    // Fungsi ini milik FloatingVisualizerService, BUKAN milik View.
    // -----------------------------------------------------------------

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
            floatingBars.forEachIndexed { index, bar ->
                val level = levels.getOrElse(index) { 0f }.coerceIn(0f, 1f)
                (bar as? EffectBarView)?.setAudioLevel(level)
            }
        }
    }

    // -----------------------------------------------------------------
    // OVERLAY CLEANUP
    // -----------------------------------------------------------------

    private fun removeOverlay() {
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
        floatingBars.clear()
        renderedVertical = null
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    // -----------------------------------------------------------------
    // SERVICE LIFECYCLE — HARUS BERADA DI DALAM FloatingVisualizerService
    // -----------------------------------------------------------------

    override fun onDestroy() {
        stopping = true
        stopEngineResources()
        removeOverlay()
        setServiceActive(false)

        getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        .edit()
        .putBoolean("backup_capture", false)
        .apply()

        stopForegroundCompat()
        mediaSession?.setActive(false)
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
