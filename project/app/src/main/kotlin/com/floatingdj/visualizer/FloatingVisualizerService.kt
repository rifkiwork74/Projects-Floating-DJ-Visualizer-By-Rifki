package com.floatingdj.visualizer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
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
        const val ACTION_UPDATE = "com.floatingdj.visualizer.UPDATE"
        const val ACTION_LEVELS = "com.floatingdj.visualizer.LEVELS"
        const val ACTION_POSITION = "com.floatingdj.visualizer.POSITION"
        const val EXTRA_LEVELS = "extra_levels"

        const val EXTRA_VERTICAL = "extra_vertical"
        const val EXTRA_COLORS = "extra_colors"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_BAR_WIDTH = "extra_bar_width"
        const val EXTRA_GAP = "extra_gap"
        const val EXTRA_SCALE = "extra_scale"
        const val EXTRA_OPACITY = "extra_opacity"
        const val EXTRA_USE_MIC = "extra_use_mic"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        const val EXTRA_SINGLE_MODE = "extra_single_mode"
        const val EXTRA_BASS_MIN_HZ = "extra_bass_min_hz"
        const val EXTRA_BASS_MAX_HZ = "extra_bass_max_hz"
        const val EXTRA_POSITION_X = "extra_position_x"
        const val EXTRA_POSITION_Y = "extra_position_y"
        const val EXTRA_POSITION_MODE = "extra_position_mode"
        const val EXTRA_ROTATION = "extra_rotation"

        private const val CHANNEL_ID = "floating_dj_audio"
        private const val NOTIFICATION_ID = 9001
        private const val SAMPLE_RATE = 44100
        private const val FFT_SIZE = 4096
        private const val HOP_SIZE = 1024
        private const val BAND_COUNT = 15
        private const val SEGMENT_COUNT = 24
    }

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Window is the touch/position bounding box; content rotates inside it.
    private var windowView: FrameLayout? = null
    private var contentView: LinearLayout? = null
    private val floatingBars = mutableListOf<View>()
    private var overlayParams: WindowManager.LayoutParams? = null
    private var renderedVertical: Boolean? = null

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var projection: MediaProjection? = null

    @Volatile private var captureRunning = false
    @Volatile private var stopping = false
    @Volatile private var bassMinHz = 60
    @Volatile private var bassMaxHz = 160

    private var isVerticalMode = false
    private var widthDp = 320
    private var heightDp = 360
    private var barWidthDp = 20
    private var gapDp = 3
    private var scalePercent = 100
    private var opacityPercent = 100
    private var singleAudioMode = "MUSIC"
    private var positionMode = "STATIC"
    private var positionXdp = 0
    private var positionYdp = 0
    private var rotationDegrees = 0

    private var spectrumColors = intArrayOf(
        Color.CYAN,
        Color.MAGENTA,
        Color.GREEN,
        Color.YELLOW,
        Color.RED
    )

    private val smoothedLevels = FloatArray(BAND_COUNT)
    private var smoothedBass = 0f
    private var smoothedMusic = 0f

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            captureRunning = false
            releaseAudioRecord()
            removeOverlay()
            if (!stopping) stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        readSettings(intent)

        if (intent.action == ACTION_UPDATE) {
            if (audioRecord == null || !captureRunning) {
                android.util.Log.w("FloatingDJ", "Update ignored: capture session is not active")
                stopSelf(startId)
                return START_NOT_STICKY
            }
            updateFloatingWindow()
            return START_STICKY
        }

        if (audioRecord != null && captureRunning) {
            updateFloatingWindow()
            return START_STICKY
        }

        stopping = false
        try {
            if (Build.VERSION.SDK_INT >= 29 && !intent.getBooleanExtra(EXTRA_USE_MIC, false)) {
                val resultData = getProjectionIntent(intent)
                ?: throw IllegalStateException("Data persetujuan audio tidak tersedia")
                startProjectionForeground()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                startPlaybackCapture(resultCode, resultData)
            } else {
                startMicrophoneForeground()
                startMicrophoneCapture()
            }
        } catch (e: Exception) {
            android.util.Log.e("FloatingDJ", "Capture start failed", e)
            failService("Audio capture gagal: ${e.message}")
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun getProjectionIntent(intent: Intent): Intent? {
        return intent.getParcelableExtra(EXTRA_PROJECTION_DATA) as? Intent
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        mainHandler.post { updateFloatingWindow() }
    }

    // -------------------------------------------------------------
    // FOREGROUND NOTIFICATION
    // -------------------------------------------------------------

    private fun startProjectionForeground() {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun startMicrophoneForeground() {
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating DJ Audio Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Real-time audio visualizer" }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
        .setContentTitle("Floating DJ Visualizer")
        .setContentText("Real-time audio visualizer aktif")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .build()
    }

    // -------------------------------------------------------------
    // SETTINGS
    // -------------------------------------------------------------

    private fun readSettings(intent: Intent) {
        isVerticalMode = intent.getBooleanExtra(EXTRA_VERTICAL, isVerticalMode)
        widthDp = intent.getIntExtra(EXTRA_WIDTH, widthDp).coerceIn(120, 1600)
        heightDp = intent.getIntExtra(EXTRA_HEIGHT, heightDp).coerceIn(80, 1600)
        barWidthDp = intent.getIntExtra(EXTRA_BAR_WIDTH, barWidthDp).coerceIn(4, 120)
        gapDp = intent.getIntExtra(EXTRA_GAP, gapDp).coerceIn(0, 40)
        scalePercent = intent.getIntExtra(EXTRA_SCALE, scalePercent).coerceIn(50, 200)
        opacityPercent = intent.getIntExtra(EXTRA_OPACITY, opacityPercent).coerceIn(10, 100)
        intent.getIntArrayExtra(EXTRA_COLORS)?.takeIf { it.size >= 2 }?.let { spectrumColors = it.copyOf() }
        singleAudioMode = intent.getStringExtra(EXTRA_SINGLE_MODE) ?: singleAudioMode
        bassMinHz = intent.getIntExtra(EXTRA_BASS_MIN_HZ, bassMinHz).coerceIn(30, 150)
        bassMaxHz = intent.getIntExtra(EXTRA_BASS_MAX_HZ, bassMaxHz).coerceIn(bassMinHz + 20, 400)
        positionXdp = intent.getIntExtra(EXTRA_POSITION_X, positionXdp).coerceAtLeast(0)
        positionYdp = intent.getIntExtra(EXTRA_POSITION_Y, positionYdp).coerceAtLeast(0)
        positionMode = intent.getStringExtra(EXTRA_POSITION_MODE) ?: positionMode
        rotationDegrees = intent.getIntExtra(EXTRA_ROTATION, rotationDegrees).coerceIn(0, 359)
    }

    // -------------------------------------------------------------
    // AUDIO CAPTURE
    // -------------------------------------------------------------

    private fun startPlaybackCapture(resultCode: Int, resultData: Intent) {
        if (Build.VERSION.SDK_INT < 29) throw IllegalStateException("Playback capture membutuhkan Android 10+")
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = manager.getMediaProjection(resultCode, resultData)
        ?: throw IllegalStateException("MediaProjection tidak tersedia")
        projection = mp
        mp.registerCallback(projectionCallback, mainHandler)

        val config = AudioPlaybackCaptureConfiguration.Builder(mp)
        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        .addMatchingUsage(AudioAttributes.USAGE_GAME)
        .build()
        createAndStartRecorder(config)
    }

    private fun createAndStartRecorder(config: AudioPlaybackCaptureConfiguration) {
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
        if (minBuffer <= 0) throw IllegalStateException("Format audio tidak didukung perangkat")
        val recorder = AudioRecord.Builder()
        .setAudioFormat(format)
        .setBufferSizeInBytes(max(minBuffer, FFT_SIZE * 4))
        .setAudioPlaybackCaptureConfig(config)
        .build()
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("AudioRecord gagal diinisialisasi")
        }
        audioRecord = recorder
        recorder.startRecording()
        showFloatingWindow()
        startCaptureThread()
        setServiceActive(true)
    }

    @Suppress("DEPRECATION")
    private fun startMicrophoneCapture() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) throw IllegalStateException("Format mikrofon tidak didukung")
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuffer, FFT_SIZE * 4)
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Mikrofon gagal diinisialisasi")
        }
        audioRecord = recorder
        recorder.startRecording()
        showFloatingWindow()
        startCaptureThread()
        setServiceActive(true)
    }

    private fun setServiceActive(active: Boolean) {
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        .edit().putBoolean("service_active", active).apply()
    }

    // -------------------------------------------------------------
    // OVERLAPPED AUDIO FRAME
    // -------------------------------------------------------------

    private fun startCaptureThread() {
        captureRunning = true
        val recorder = audioRecord ?: return
        captureThread = Thread {
            val frame = ShortArray(FFT_SIZE)
            val chunk = ShortArray(HOP_SIZE)
            var filled = 0
            while (captureRunning) {
                val read = try {
                    recorder.read(chunk, 0, HOP_SIZE, AudioRecord.READ_BLOCKING)
                } catch (e: Exception) {
                    android.util.Log.e("FloatingDJ", "Audio read error", e)
                    -1
                }
                if (read <= 0) break

                // Shift by the number of samples actually returned.
                System.arraycopy(frame, read, frame, 0, FFT_SIZE - read)
                System.arraycopy(chunk, 0, frame, FFT_SIZE - read, read)
                filled = min(FFT_SIZE, filled + read)
                if (filled < FFT_SIZE) continue

                val levels = calculateSpectrum(frame)
                mainHandler.post {
                    if (captureRunning) updateFloatingBars(levels)
                }
                val broadcast = Intent(ACTION_LEVELS)
                .setPackage(packageName)
                .putExtra(EXTRA_LEVELS, levels)
                sendBroadcast(broadcast)
            }
            if (captureRunning) {
                mainHandler.post { failService("Audio capture terputus") }
            }
        }.apply {
            name = "FloatingDJ-AudioEngine"
            start()
        }
    }

    // -------------------------------------------------------------
    // FFT
    // -------------------------------------------------------------

    private fun calculateSpectrum(samples: ShortArray): FloatArray {
        val real = DoubleArray(FFT_SIZE)
        val imag = DoubleArray(FFT_SIZE)
        for (i in 0 until FFT_SIZE) {
            val sample = samples[i].toDouble() / 32768.0
            val hann = 0.5 * (1.0 - cos(2.0 * PI * i / (FFT_SIZE - 1)))
            real[i] = sample * hann
        }

        var j = 0
        for (i in 1 until FFT_SIZE) {
            var bit = FFT_SIZE shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
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

        val result = FloatArray(BAND_COUNT + 2)
        for (band in 0 until BAND_COUNT) {
            val low = 45.0 * (12000.0 / 45.0).pow(band.toDouble() / BAND_COUNT)
            val high = 45.0 * (12000.0 / 45.0).pow((band + 1).toDouble() / BAND_COUNT)
            val firstBin = max(1, (low * FFT_SIZE / SAMPLE_RATE).toInt())
            val lastBin = min(FFT_SIZE / 2, max(firstBin + 1, (high * FFT_SIZE / SAMPLE_RATE).toInt()))
            var peak = 0.0
            for (bin in firstBin until lastBin) {
                val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                if (mag > peak) peak = mag
            }
            val raw = (peak / FFT_SIZE * 8.0).coerceIn(0.0, 1.0)
            val target = sqrt(raw).toFloat()
            val previous = smoothedLevels[band]
            smoothedLevels[band] = if (target > previous) {
                previous * 0.12f + target * 0.88f
            } else {
                previous * 0.48f + target * 0.52f
            }
            result[band] = smoothedLevels[band]
        }

        val firstBass = max(1, ceil(bassMinHz * FFT_SIZE.toDouble() / SAMPLE_RATE).toInt())
        val lastBass = min(FFT_SIZE / 2, (bassMaxHz * FFT_SIZE.toDouble() / SAMPLE_RATE).toInt())
        var bassPeak = 0.0
        if (lastBass >= firstBass) {
            for (bin in firstBass..lastBass) {
                val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
                if (mag > bassPeak) bassPeak = mag
            }
        }
        val bassTarget = (bassPeak / FFT_SIZE * 12.0).coerceIn(0.0, 1.0).pow(0.62).toFloat()
        smoothedBass = if (bassTarget > smoothedBass) {
            smoothedBass * 0.08f + bassTarget * 0.92f
        } else {
            smoothedBass * 0.42f + bassTarget * 0.58f
        }
        result[BAND_COUNT] = smoothedBass

        var sum = 0f
        for (i in 0 until BAND_COUNT) sum += result[i] * result[i]
        val rms = sqrt(sum / BAND_COUNT)
        val musicTarget = (rms * 1.8f).coerceIn(0f, 1f).toDouble().pow(0.72).toFloat()
        smoothedMusic = if (musicTarget > smoothedMusic) {
            smoothedMusic * 0.12f + musicTarget * 0.88f
        } else {
            smoothedMusic * 0.48f + musicTarget * 0.52f
        }
        result[BAND_COUNT + 1] = smoothedMusic
        return result
    }

    // -------------------------------------------------------------
    // OVERLAY GEOMETRY + ROTATION
    // -------------------------------------------------------------

    private fun localContentSizePx(): Pair<Int, Int> {
        val density = resources.displayMetrics.density
        val localWdp = if (isVerticalMode) barWidthDp else widthDp
        var w = (localWdp * scalePercent / 100f * density).toInt().coerceAtLeast(dp(3))
        var h = (heightDp * scalePercent / 100f * density).toInt().coerceAtLeast(dp(3))
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        val boundW = w * c + h * s
        val boundH = w * s + h * c
        val fit = min(
            1.0,
            min(metrics.widthPixels / boundW, metrics.heightPixels / boundH)
        ).toFloat()
        if (fit < 1f) {
            w = (w * fit).toInt().coerceAtLeast(dp(3))
            h = (h * fit).toInt().coerceAtLeast(dp(3))
        }
        return Pair(w, h)
    }

    private fun rotatedBoundsPx(contentWidth: Int, contentHeight: Int): Pair<Int, Int> {
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val c = abs(cos(radians))
        val s = abs(sin(radians))
        val w = ceil(contentWidth * c + contentHeight * s).toInt().coerceAtLeast(1)
        val h = ceil(contentWidth * s + contentHeight * c).toInt().coerceAtLeast(1)
        return Pair(w, h)
    }

    private fun showFloatingWindow() {
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
        val wmParams = WindowManager.LayoutParams(
            boundW,
            boundH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.TOP or Gravity.START

        val density = resources.displayMetrics.density
        wmParams.x = (positionXdp * density).toInt()
        wmParams.y = (positionYdp * density).toInt()

        windowView = host
        contentView = content
        overlayParams = wmParams

        try {
            windowManager.addView(host, wmParams)
            applyFloatingDimensions()
        } catch (e: Exception) {
            android.util.Log.e("FloatingDJ", "Overlay creation failed", e)
            removeOverlay()
            failService("Floating gagal: ${e.message}")
        }
    }

    private fun addFloatingBars(content: LinearLayout) {
        content.removeAllViews()
        floatingBars.clear()
        content.orientation = if (isVerticalMode) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        if (isVerticalMode) {
            val meter = SegmentedMeterView(this)
            meter.segmentGapDp = gapDp
            content.addView(meter)
            floatingBars.add(meter)
        } else {
            repeat(BAND_COUNT) {
                val bar = View(this).apply {
                    background = createBarDrawable()
                    pivotY = 0f
                }
                content.addView(bar)
                floatingBars.add(bar)
            }
        }
        renderedVertical = isVerticalMode
    }

    private fun updateFloatingWindow() {
        if (audioRecord == null || !captureRunning) return
        val content = contentView
        val host = windowView
        if (content == null || host == null) {
            showFloatingWindow()
            return
        }
        val expected = if (isVerticalMode) 1 else BAND_COUNT
        if (renderedVertical != isVerticalMode || floatingBars.size != expected) {
            addFloatingBars(content)
        }
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
        val density = resources.displayMetrics.density
        params.x = (positionXdp * density).toInt()
        params.y = (positionYdp * density).toInt()
        content.rotation = rotationDegrees.toFloat()
        content.pivotX = contentW / 2f
        content.pivotY = contentH / 2f
        val contentParams = FrameLayout.LayoutParams(
            contentW,
            contentH,
            Gravity.CENTER
        )
        content.layoutParams = contentParams

        if (isVerticalMode) {
            (floatingBars.firstOrNull() as? SegmentedMeterView)?.let {
                it.segmentGapDp = gapDp
                it.layoutParams = LinearLayout.LayoutParams(contentW, contentH)
                it.invalidate()
            }
        } else {
            val actualGap = if (gapDp == 0) 0 else dp(gapDp)
            val totalGap = actualGap * (BAND_COUNT - 1)
            val available = (contentW - totalGap).coerceAtLeast(BAND_COUNT * dp(1))
            val requestedBar = dp(barWidthDp).coerceAtLeast(dp(1))
            val actualBar = min(requestedBar, available / BAND_COUNT)
            for ((index, bar) in floatingBars.withIndex()) {
                val lp = LinearLayout.LayoutParams(actualBar, contentH)
                val left = if (index == 0) 0 else actualGap / 2
                val right = if (index == BAND_COUNT - 1) 0 else actualGap - actualGap / 2
                lp.setMargins(left, 0, right, 0)
                bar.layoutParams = lp
                bar.background = createBarDrawable()
                bar.pivotY = contentH.toFloat()
            }
        }

        val (screenW, screenH) = displaySizePx()
        params.x = params.x.coerceIn(0, (screenW - boundW).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (screenH - boundH).coerceAtLeast(0))
        positionXdp = (params.x / resources.displayMetrics.density).toInt()
        positionYdp = (params.y / resources.displayMetrics.density).toInt()
        configureTouchMode(host, params)
        try {
            windowManager.updateViewLayout(host, params)
        } catch (e: Exception) {
            android.util.Log.e("FloatingDJ", "Window update failed", e)
        }
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
    
    private fun configureTouchMode(host: FrameLayout, params: WindowManager.LayoutParams) {
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (positionMode == "DYNAMIC") {
            host.setOnTouchListener { _, event -> handleDrag(event, params, host) }
        } else {
            host.setOnTouchListener(null)
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }

    private fun handleDrag(event: MotionEvent, params: WindowManager.LayoutParams, host: FrameLayout): Boolean {
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

    private var dragStartX = 0
    private var dragStartY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f

    private fun savePosition() {
        getSharedPreferences("floating_dj_settings", MODE_PRIVATE)
        .edit()
        .putInt("position_x", positionXdp)
        .putInt("position_y", positionYdp)
        .putBoolean("position_initialized", true)
        .apply()
        val update = Intent(ACTION_POSITION)
        .setPackage(packageName)
        .putExtra(EXTRA_POSITION_X, positionXdp)
        .putExtra(EXTRA_POSITION_Y, positionYdp)
        sendBroadcast(update)
    }

    // -------------------------------------------------------------
    // COLOURS + BAR DRAWING
    // -------------------------------------------------------------

    private fun createBarDrawable(): GradientDrawable {
        val alpha = opacityPercent * 255 / 100
        val colors = IntArray(spectrumColors.size) { i ->
            val c = spectrumColors[i]
            Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
        }
        return GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, colors).apply {
            cornerRadius = dp(100).toFloat()
        }
    }

    private fun updateFloatingBars(levels: FloatArray) {
        if (floatingBars.isEmpty()) return
        if (isVerticalMode) {
            val level = if (singleAudioMode == "DJ") levels.getOrElse(BAND_COUNT) { 0f }
            else levels.getOrElse(BAND_COUNT + 1) { 0f }
            (floatingBars.firstOrNull() as? SegmentedMeterView)?.setAudioLevel(level)
        } else {
            for (i in floatingBars.indices) {
                val level = levels.getOrElse(i) { 0f }.coerceIn(0f, 1f)
                floatingBars[i].scaleY = 0.02f + level * 0.98f
            }
        }
    }

    private inner class SegmentedMeterView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var segmentGapDp = 3
        private var level = 0f
        private var peak = 0f
        private var peakHoldUntil = 0L
        private var lastUpdate = SystemClock.uptimeMillis()

        fun setAudioLevel(value: Float) {
            val now = SystemClock.uptimeMillis()
            val target = if (value < 0.018f) 0f else value.coerceIn(0f, 1f)
            level = if (target > level) level + (target - level) * 0.9f
            else level + (target - level) * 0.68f
            if (level >= peak) {
                peak = level
                peakHoldUntil = now + 130L
            } else if (now > peakHoldUntil) {
                val dt = ((now - lastUpdate).coerceIn(0L, 100L)) / 1000f
                peak = (peak - 1.9f * dt).coerceAtLeast(level)
            }
            lastUpdate = now
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0 || spectrumColors.isEmpty()) return
            val colorAlpha = opacityPercent * 255 / 100
            if (segmentGapDp == 0) {
                // Gap zero: one uninterrupted gradient line, filled from bottom by audio level.
                val alphaColors = IntArray(spectrumColors.size) { index ->
                    val color = spectrumColors[index]
                    Color.argb(
                        colorAlpha,
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    )
                }
                val shader = android.graphics.LinearGradient(
                    0f, height.toFloat(), 0f, 0f,
                    alphaColors, null,
                    android.graphics.Shader.TileMode.CLAMP
                )
                paint.shader = shader
                val fillTop = height * (1f - level.coerceIn(0f, 1f))
                canvas.drawRect(0f, fillTop, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
                if (peak > 0.03f) {
                    paint.color = Color.WHITE
                    val peakY = height * (1f - peak.coerceIn(0f, 1f))
                    canvas.drawRect(0f, peakY, width.toFloat(), peakY + dp(2), paint)
                }
                return
            }

            val requestedGap = dp(segmentGapDp).toFloat()
            val maxGap = ((height - dp(2) * SEGMENT_COUNT).coerceAtLeast(0)).toFloat() / (SEGMENT_COUNT - 1)
            val gap = min(requestedGap, maxGap)
            val segH = (height - gap * (SEGMENT_COUNT - 1)) / SEGMENT_COUNT
            if (segH <= 0f) return
            for (index in 0 until SEGMENT_COUNT) {
                val threshold = (index + 1).toFloat() / SEGMENT_COUNT
                val active = level >= threshold
                val colorIndex = ((SEGMENT_COUNT - 1 - index) * spectrumColors.size / SEGMENT_COUNT)
                .coerceIn(0, spectrumColors.lastIndex)
                val color = spectrumColors[colorIndex]
                paint.color = if (active) {
                    Color.argb(colorAlpha, Color.red(color), Color.green(color), Color.blue(color))
                } else {
                    Color.argb(colorAlpha * 12 / 100, Color.red(color), Color.green(color), Color.blue(color))
                }
                val bottomY = height - (index * (segH + gap))
                canvas.drawRoundRect(
                    0f, bottomY - segH, width.toFloat(), bottomY,
                    segH / 2f, segH / 2f, paint
                )
            }
            if (peak > 0.04f) {
                val peakIndex = ((peak * SEGMENT_COUNT).toInt() - 1).coerceIn(0, SEGMENT_COUNT - 1)
                val bottomY = height - peakIndex * (segH + gap)
                paint.color = Color.WHITE
                canvas.drawRoundRect(
                    0f, bottomY - segH, width.toFloat(), bottomY - segH + dp(2),
                    dp(1).toFloat(), dp(1).toFloat(), paint
                )
            }
        }
    }

    // -------------------------------------------------------------
    // CLEANUP / ERROR
    // -------------------------------------------------------------

    private fun removeOverlay() {
        windowView?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
        windowView = null
        contentView = null
        floatingBars.clear()
        overlayParams = null
        renderedVertical = null
    }

    private fun releaseAudioRecord() {
        captureRunning = false
        val recorder = audioRecord
        audioRecord = null
        val thread = captureThread
        if (recorder != null) {
            try { recorder.stop() } catch (_: Exception) {}
        }
        if (thread != null && thread !== Thread.currentThread()) {
            try { thread.join(700L) } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        captureThread = null
        if (recorder != null) {
            try { recorder.release() } catch (_: Exception) {}
        }
    }

    private fun stopCapture() {
        stopping = true
        releaseAudioRecord()
        val currentProjection = projection
        projection = null
        if (currentProjection != null) {
            try { currentProjection.unregisterCallback(projectionCallback) } catch (_: Exception) {}
            try { currentProjection.stop() } catch (_: Exception) {}
        }
    }

    private fun failService(message: String) {
        mainHandler.post {
            android.widget.Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        stopCapture()
        removeOverlay()
        setServiceActive(false)
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
