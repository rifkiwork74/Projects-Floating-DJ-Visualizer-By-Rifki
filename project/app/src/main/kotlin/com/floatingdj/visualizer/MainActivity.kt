package com.floatingdj.visualizer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import android.view.WindowInsets


class MainActivity : Activity() {

    private lateinit var barsContainer: LinearLayout
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
    private var receiverRegistered = false
    private var pendingOverlayStart = false

    private var overlayWidthDp = 320
    private var overlayHeightDp = 360
    private var barWidthDp = 20
    private var barGapDp = 3
    private var scalePercent = 100
    private var opacityPercent = 100

    private var singleAudioMode = "MUSIC"
    private var bassMinHz = 60
    private var bassMaxHz = 160

    private var screenWidthDp = 360
    private var screenHeightDp = 740
    private var screenWidthPx = 1080
    private var screenHeightPx = 2200

    // Position is stored as the top-left of the rotated overlay bounding box.
    private var positionXdp = 0
    private var positionYdp = 0
    private var positionInitialized = false
    private var positionMode = "STATIC"
    private var positionUnit = "DP"
    private var positionStep = 12f
    private var rotationDegrees = 0

    private var singleModeCard: LinearLayout? = null
    private var bassControls: LinearLayout? = null

    private val updateHandler = Handler(Looper.getMainLooper())
    private val realtimeUpdate = Runnable {
        saveSettings()
        updateFloatingService()
    }

    companion object {
        private const val REQUEST_OVERLAY = 1001
        private const val REQUEST_AUDIO = 2001
        private const val REQUEST_PROJECTION = 2002
    }

    private val levelsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FloatingVisualizerService.ACTION_POSITION) {
                positionXdp = intent.getIntExtra(FloatingVisualizerService.EXTRA_POSITION_X, positionXdp)
                positionYdp = intent.getIntExtra(FloatingVisualizerService.EXTRA_POSITION_Y, positionYdp)
                return
            }
            val levels = intent?.getFloatArrayExtra(
                FloatingVisualizerService.EXTRA_LEVELS
            ) ?: return
            updatePreviewLevels(levels)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableEdgeToEdgeLayout()


        barsContainer = findViewById(R.id.equalizerBars)
        loadSettings()
        scanDisplayBounds()
        initializeOrClampPosition()

        setupButtons()
        buildSpectrumBars()
        updateModeButtons()
        buildSingleModePanel()
        buildFloatingControls()
        checkOverlayPermission()

        // Do not restart audio after Activity recreation. Only synchronize
        // settings if the service's last known state is active.
        if (floatingServiceStarted) {
            barsContainer.postDelayed({ updateFloatingService() }, 250L)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(FloatingVisualizerService.ACTION_LEVELS)
            addAction(FloatingVisualizerService.ACTION_POSITION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(levelsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(levelsReceiver, filter)
        }
        receiverRegistered = true
        // Re-read position changed by Dynamic drag while this Activity was stopped.
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

    // -------------------------------------------------------------
    // PERSISTENCE
    // -------------------------------------------------------------

    private fun loadSettings() {
        overlayWidthDp = prefs.getInt("width", 320)
        overlayHeightDp = prefs.getInt("height", 360)
        barWidthDp = prefs.getInt("bar_width", 20)
        barGapDp = prefs.getInt("bar_gap", 3)
        scalePercent = prefs.getInt("scale", 100)
        opacityPercent = prefs.getInt("opacity", 100)
        isVerticalMode = prefs.getBoolean("vertical", false)
        singleAudioMode = prefs.getString("single_audio_mode", "MUSIC") ?: "MUSIC"
        bassMinHz = prefs.getInt("bass_min_hz", 60)
        bassMaxHz = prefs.getInt("bass_max_hz", 160)
        floatingServiceStarted = prefs.getBoolean("service_active", false)
        positionXdp = prefs.getInt("position_x", 0)
        positionYdp = prefs.getInt("position_y", 0)
        positionInitialized = prefs.getBoolean("position_initialized", false)
        positionMode = prefs.getString("position_mode", "STATIC") ?: "STATIC"
        positionUnit = prefs.getString("position_unit", "DP") ?: "DP"
        positionStep = prefs.getFloat("position_step", 12f)
        rotationDegrees = prefs.getInt("rotation_degrees", 0).coerceIn(0, 359)

        val savedColors = prefs.getString("colors", null)
        if (!savedColors.isNullOrBlank()) {
            val parsed = savedColors.split(",").mapNotNull { value ->
                try { Color.parseColor(value.trim()) } catch (_: Exception) { null }
            }
            if (parsed.size >= 2) {
                spectrumColors.clear()
                spectrumColors.addAll(parsed.take(12))
            }
        }
    }

    private fun saveSettings() {
        prefs.edit()
        .putInt("width", overlayWidthDp)
        .putInt("height", overlayHeightDp)
        .putInt("bar_width", barWidthDp)
        .putInt("bar_gap", barGapDp)
        .putInt("scale", scalePercent)
        .putInt("opacity", opacityPercent)
        .putBoolean("vertical", isVerticalMode)
        .putString("single_audio_mode", singleAudioMode)
        .putInt("bass_min_hz", bassMinHz)
        .putInt("bass_max_hz", bassMaxHz)
        .putInt("position_x", positionXdp)
        .putInt("position_y", positionYdp)
        .putBoolean("position_initialized", positionInitialized)
        .putString("position_mode", positionMode)
        .putString("position_unit", positionUnit)
        .putFloat("position_step", positionStep)
        .putInt("rotation_degrees", rotationDegrees)
        .putString("colors", spectrumColors.joinToString(",") {
                String.format("#%06X", 0xFFFFFF and it)
        })
        .apply()
    }

    // -------------------------------------------------------------
    // SCREEN / SIZE GEOMETRY
    // -------------------------------------------------------------

    private fun scanDisplayBounds() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)

        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.maximumWindowMetrics.bounds
            screenWidthPx = bounds.width()
            screenHeightPx = bounds.height()
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            screenWidthPx = metrics.widthPixels
            screenHeightPx = metrics.heightPixels
        }

        screenWidthDp = (screenWidthPx / density).toInt().coerceAtLeast(160)
        screenHeightDp = (screenHeightPx / density).toInt().coerceAtLeast(240)
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
        val fit = minOf(
            1f,
            screenWidthDp.toFloat() / bw.toFloat(),
            screenHeightDp.toFloat() / bh.toFloat()
        )
        bw = (bw * fit).toInt().coerceAtLeast(1)
        bh = (bh * fit).toInt().coerceAtLeast(1)
        return Pair(bw, bh)
    }

    private fun initializeOrClampPosition() {
        val maxDimension = maxOf(screenWidthDp, screenHeightDp)
        overlayWidthDp = overlayWidthDp.coerceIn(120, (maxDimension * 0.90f).toInt())
        overlayHeightDp = overlayHeightDp.coerceIn(80, (maxDimension * 0.90f).toInt())
        barWidthDp = barWidthDp.coerceIn(4, 100)
        barGapDp = barGapDp.coerceIn(0, 24)
        scalePercent = scalePercent.coerceIn(50, 180)
        opacityPercent = opacityPercent.coerceIn(10, 100)
        bassMinHz = bassMinHz.coerceIn(30, 120)
        bassMaxHz = bassMaxHz.coerceIn(bassMinHz + 20, 300)

        val (boundsW, boundsH) = rotatedBoundsDp()
        if (!positionInitialized) {
            positionXdp = ((screenWidthDp - boundsW) / 2).coerceAtLeast(0)
            positionYdp = ((screenHeightDp - boundsH) / 2).coerceAtLeast(0)
            positionInitialized = true
        } else {
            clampPosition()
        }
        saveSettings()
    }

    private fun clampPosition() {
        val (boundsW, boundsH) = rotatedBoundsDp()
        positionXdp = positionXdp.coerceIn(0, (screenWidthDp - boundsW).coerceAtLeast(0))
        positionYdp = positionYdp.coerceIn(0, (screenHeightDp - boundsH).coerceAtLeast(0))
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    // -------------------------------------------------------------
    // BUTTONS / MODE
    // -------------------------------------------------------------

    private fun setupButtons() {
        findViewById<View>(R.id.btnModeVertical).setOnClickListener { selectMode(true) }
        findViewById<View>(R.id.btnModeHorizontal).setOnClickListener { selectMode(false) }
        findViewById<View>(R.id.btnColorStudio).setOnClickListener { showColorStudio() }
        findViewById<View>(R.id.btnStart).setOnClickListener { startFloatingFromButton() }
        findViewById<View>(R.id.btnStop).setOnClickListener { stopFloating() }
        findViewById<View>(R.id.btnPositionSettings).setOnClickListener { showPositionSettings() }
    }

    private fun selectMode(vertical: Boolean) {
        isVerticalMode = vertical
        scanDisplayBounds()
        clampPosition()
        updateModeButtons()
        updateSingleModeVisibility()
        buildSpectrumBars()
        saveSettings()
        updateFloatingService()
    }

    private fun updateModeButtons() {
        findViewById<View>(R.id.btnModeVertical).setBackgroundResource(
            if (isVerticalMode) R.drawable.bg_mode_active else R.drawable.bg_mode_inactive
        )
        findViewById<View>(R.id.btnModeHorizontal).setBackgroundResource(
            if (!isVerticalMode) R.drawable.bg_mode_active else R.drawable.bg_mode_inactive
        )
    }

    // -------------------------------------------------------------
    // PREVIEW
    // -------------------------------------------------------------

    private fun buildSpectrumBars() {
        barsContainer.removeAllViews()
        barsContainer.orientation = LinearLayout.HORIZONTAL
        barsContainer.gravity = Gravity.CENTER or Gravity.BOTTOM
        val count = if (isVerticalMode) 1 else 15
        repeat(count) { index ->
            val bar = View(this)
            bar.background = createGradient()
            bar.pivotY = 145.dp().toFloat()
            bar.scaleY = 0.08f
            val lp = LinearLayout.LayoutParams(
                if (isVerticalMode) 22.dp() else 8.dp(),
                145.dp()
            )
            if (!isVerticalMode) lp.setMargins(2.dp(), 0, 2.dp(), 0)
            barsContainer.addView(bar, lp)
        }
    }

    private fun updatePreviewLevels(levels: FloatArray) {
        for (i in 0 until barsContainer.childCount) {
            val level = when {
                isVerticalMode && singleAudioMode == "DJ" -> levels.getOrElse(15) { 0f }
                isVerticalMode -> levels.getOrElse(16) { 0f }
                else -> levels.getOrElse(i) { 0f }
            }
            barsContainer.getChildAt(i).scaleY = 0.04f + level.coerceIn(0f, 1f) * 0.96f
        }
    }

    private fun createGradient(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.BOTTOM_TOP,
        spectrumColors.toIntArray()
    ).apply { cornerRadius = 100.dp().toFloat() }

    // -------------------------------------------------------------
    // SINGLE BAR MODE PANEL
    // -------------------------------------------------------------

    private fun buildSingleModePanel() {
        val modeRow = findViewById<View>(R.id.btnModeVertical).parent as View
        val root = modeRow.parent as? LinearLayout ?: return

        val card = glassPanel().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
        }
        card.addView(sectionTitle("SINGLE BAR ENGINE"))
        card.addView(subtitle("Choose your audio response"))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12.dp(), 0, 8.dp())
        }
        val dj = glassButton("DJ BASS")
        val music = glassButton("MUSIC FULL")
        val p = LinearLayout.LayoutParams(0, 48.dp(), 1f)
        p.setMargins(4.dp(), 0, 4.dp(), 0)
        row.addView(dj, p)
        row.addView(music, p)
        card.addView(row)

        val bass = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        bassControls = bass
        addControlSlider(bass, "BASS LOW", bassMinHz, 30, 120, " Hz") {
            bassMinHz = minOf(it, bassMaxHz - 20)
        }
        addControlSlider(bass, "BASS HIGH", bassMaxHz, 80, 300, " Hz") {
            bassMaxHz = maxOf(it, bassMinHz + 20)
        }
        card.addView(bass)

        dj.setOnClickListener {
            singleAudioMode = "DJ"
            updateSingleModeButtons()
            saveSettings()
            updateFloatingService()
        }
        music.setOnClickListener {
            singleAudioMode = "MUSIC"
            updateSingleModeButtons()
            saveSettings()
            updateFloatingService()
        }

        singleModeCard = card
        root.addView(card, root.indexOfChild(modeRow) + 1)
        updateSingleModeButtons()
        updateSingleModeVisibility()
    }

    private fun updateSingleModeButtons() {
        val card = singleModeCard ?: return
        val row = card.getChildAt(2) as? LinearLayout ?: return
        row.getChildAt(0).setBackgroundResource(
            if (singleAudioMode == "DJ") R.drawable.bg_mode_active else R.drawable.bg_mode_inactive
        )
        row.getChildAt(1).setBackgroundResource(
            if (singleAudioMode == "MUSIC") R.drawable.bg_mode_active else R.drawable.bg_mode_inactive
        )
        bassControls?.visibility = if (singleAudioMode == "DJ") View.VISIBLE else View.GONE
    }

    private fun updateSingleModeVisibility() {
        val card = singleModeCard ?: return
        card.animate().cancel()
        if (isVerticalMode) {
            card.visibility = View.VISIBLE
            card.animate().alpha(1f).setDuration(180L).start()
        } else {
            card.animate().alpha(0f).setDuration(180L).withEndAction {
                card.visibility = View.GONE
            }.start()
        }
    }

    // -------------------------------------------------------------
    // FLOATING CONTROL PANEL
    // -------------------------------------------------------------

    private fun buildFloatingControls() {
        val actionRow = findViewById<View>(R.id.btnStart).parent as? View ?: return
        val root = actionRow.parent as? LinearLayout ?: return

        val panel = glassPanel().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
        }
        panel.addView(sectionTitle("FLOATING CONTROL"))
        val maxDimension = maxOf(screenWidthDp, screenHeightDp)
        addControlSlider(panel, "WIDTH (HORIZONTAL)", overlayWidthDp, 120, (screenWidthDp * 0.90f).toInt().coerceAtLeast(120), " dp") {
            overlayWidthDp = it
            clampPosition()
        }
        addControlSlider(panel, "HEIGHT / LENGTH", overlayHeightDp, 80, (screenHeightDp * 0.85f).toInt().coerceAtLeast(80), " dp") {
            overlayHeightDp = it
            clampPosition()
        }
        addControlSlider(panel, "BAR THICKNESS", barWidthDp, 4, (screenWidthDp * 0.30f).toInt().coerceAtLeast(24), " dp") {
            barWidthDp = it
            clampPosition()
        }
        addControlSlider(panel, "BAR GAP", barGapDp, 0, 24, " dp") { barGapDp = it }
        addControlSlider(panel, "SCALE", scalePercent, 50, 180, "%") {
            scalePercent = it
            clampPosition()
        }
        addControlSlider(panel, "OPACITY", opacityPercent, 10, 100, "%") { opacityPercent = it }

        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = 22.dp()
        root.addView(panel, root.indexOfChild(actionRow), lp)
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
        val label = subtitle("$name   $current$unit")
        label.setPadding(0, 12.dp(), 0, 2.dp())
        val slider = SeekBar(this)
        slider.max = safeMax - minimum
        slider.progress = (current - minimum).coerceIn(0, slider.max)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = minimum + progress
                    label.text = "$name   $value$unit"
                    onValue(value)
                    updateHandler.removeCallbacks(realtimeUpdate)
                    updateHandler.postDelayed(realtimeUpdate, 45L)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    saveSettings()
                    updateFloatingService()
                }
        })
        panel.addView(label)
        panel.addView(slider)
    }

    // -------------------------------------------------------------
    // POSITION STUDIO
    // -------------------------------------------------------------

    private fun showPositionSettings() {
        scanDisplayBounds()
        positionXdp = prefs.getInt("position_x", positionXdp)
        positionYdp = prefs.getInt("position_y", positionYdp)
        clampPosition()

        val root = glassPanel().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
        }
        root.addView(sectionTitle("POSITION STUDIO"))
        root.addView(subtitle("Move, rotate and save your floating visualizer."))

        val modeBtn = glassButton("POSITION: $positionMode")
        modeBtn.setOnClickListener {
            positionMode = if (positionMode == "STATIC") "DYNAMIC" else "STATIC"
            modeBtn.text = "POSITION: $positionMode"
            saveSettings()
            updateFloatingService()
        }
        root.addView(modeBtn, matchWrap().apply { topMargin = 10.dp() })

        val unitRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dpBtn = glassButton("DP")
        val cmBtn = glassButton("CM")
        val unitLp1 = LinearLayout.LayoutParams(0, 44.dp(), 1f)
        unitLp1.setMargins(4.dp(), 8.dp(), 4.dp(), 0)
        val unitLp2 = LinearLayout.LayoutParams(0, 44.dp(), 1f)
        unitLp2.setMargins(4.dp(), 8.dp(), 4.dp(), 0)
        unitRow.addView(dpBtn, unitLp1)
        unitRow.addView(cmBtn, unitLp2)
        root.addView(unitRow)
        fun refreshUnitButtons() {
            dpBtn.setBackgroundResource(if (positionUnit == "DP") R.drawable.bg_mode_active else R.drawable.bg_mode_inactive)
            cmBtn.setBackgroundResource(if (positionUnit == "CM") R.drawable.bg_mode_active else R.drawable.bg_mode_inactive)
        }
        refreshUnitButtons()

        val posLabel = TextView(this).apply {
            text = positionText()
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 12.dp(), 0, 6.dp())
        }
        root.addView(posLabel)

        root.addView(subtitle("STEP DISTANCE (${positionUnit})"))
        val stepInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(if (positionUnit == "DP") positionStep.toInt().toString() else dpToCm(positionStep).toString())
            hint = "Contoh: 12"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            background = glassDrawable()
        }
        root.addView(stepInput, matchWrap())

        dpBtn.setOnClickListener {
            positionUnit = "DP"
            stepInput.setText(positionStep.toInt().toString())
            refreshUnitButtons()
            saveSettings()
        }
        cmBtn.setOnClickListener {
            positionUnit = "CM"
            stepInput.setText(String.format("%.1f", dpToCm(positionStep)))
            refreshUnitButtons()
            saveSettings()
        }

        fun readStep() {
            val value = stepInput.text.toString().toFloatOrNull()
            if (value != null && value > 0f) {
                positionStep = if (positionUnit == "DP") {
                    value.coerceIn(1f, 1000f)
                } else {
                    cmToDp(value).coerceIn(1f, 1000f)
                }
                prefs.edit().putFloat("position_step", positionStep).apply()
            }
        }

        val dpad = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        fun dpadButton(symbol: String, dx: Int, dy: Int): TextView {
            return positionButton(symbol) {
                readStep()
                val stepPxDp = positionStep.toInt().coerceAtLeast(1)
                moveFloatingPosition(dx * stepPxDp, dy * stepPxDp)
                posLabel.text = positionText()
            }
        }
        val up = LinearLayout(this).apply { gravity = Gravity.CENTER }
        up.addView(dpadButton("↑", 0, -1), LinearLayout.LayoutParams(62.dp(), 48.dp()))
        dpad.addView(up)
        val mid = LinearLayout(this).apply { gravity = Gravity.CENTER }
        mid.addView(dpadButton("←", -1, 0), LinearLayout.LayoutParams(62.dp(), 48.dp()))
        mid.addView(positionButton("CENTER") {
                val (bw, bh) = rotatedBoundsDp()
                positionXdp = ((screenWidthDp - bw) / 2).coerceAtLeast(0)
                positionYdp = ((screenHeightDp - bh) / 2).coerceAtLeast(0)
                saveSettings(); updateFloatingService(); posLabel.text = positionText()
            }, LinearLayout.LayoutParams(84.dp(), 48.dp()).apply { setMargins(6.dp(), 0, 6.dp(), 0) })
        mid.addView(dpadButton("→", 1, 0), LinearLayout.LayoutParams(62.dp(), 48.dp()))
        dpad.addView(mid)
        val down = LinearLayout(this).apply { gravity = Gravity.CENTER }
        down.addView(dpadButton("↓", 0, 1), LinearLayout.LayoutParams(62.dp(), 48.dp()))
        dpad.addView(down)
        root.addView(dpad)

        root.addView(subtitle("ROTATION  ·  0°–359°").apply { setPadding(0, 14.dp(), 0, 2.dp()) })
        val rotationLabel = subtitle("$rotationDegrees°")
        val rotationSeek = SeekBar(this).apply { max = 359; progress = rotationDegrees }
        rotationSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    rotationDegrees = progress
                    rotationLabel.text = "$rotationDegrees°"
                    clampPosition()
                    updateHandler.removeCallbacks(realtimeUpdate)
                    updateHandler.postDelayed(realtimeUpdate, 50L)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    saveSettings(); updateFloatingService(); posLabel.text = positionText()
                }
        })
        root.addView(rotationLabel)
        root.addView(rotationSeek)

        val rotationInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(rotationDegrees.toString())
            hint = "Angle 0–359"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            background = glassDrawable()
        }
        root.addView(rotationInput, matchWrap())
        val applyRotation = glassButton("APPLY ROTATION")
        applyRotation.setOnClickListener {
            val value = rotationInput.text.toString().toIntOrNull()
            if (value != null) {
                rotationDegrees = value.coerceIn(0, 359)
                rotationSeek.progress = rotationDegrees
                rotationLabel.text = "$rotationDegrees°"
                clampPosition()
                saveSettings()
                updateFloatingService()
                posLabel.text = positionText()
            } else {
                Toast.makeText(this, "Masukkan sudut 0–359", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(applyRotation, matchWrap().apply { topMargin = 6.dp() })

        val scroll = android.widget.ScrollView(this).apply {
            isFillViewport = false
            addView(root)
        }
        val dialog = AlertDialog.Builder(this)
        .setView(scroll)
        .setPositiveButton("DONE", null)
        .create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((screenWidthPx * 0.92f).toInt(), (screenHeightPx * 0.82f).toInt())
    }

    private fun positionButton(text: String, action: (TextView) -> Unit): TextView {
        val button = glassButton(text)
        button.textSize = 18f
        button.setOnClickListener { action(button) }
        return button
    }

    private fun positionText(): String {
        return if (positionUnit == "DP") {
            "X $positionXdp dp     Y $positionYdp dp"
        } else {
            "X ${String.format("%.1f", dpToCm(positionXdp.toFloat()))} cm     Y ${String.format("%.1f", dpToCm(positionYdp.toFloat()))} cm"
        }
    }

    private fun dpToCm(dp: Float): Float {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val xdpi = resources.displayMetrics.xdpi.takeIf { it > 0f } ?: 160f
        return dp * density / xdpi * 2.54f
    }

    private fun cmToDp(cm: Float): Float {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val xdpi = resources.displayMetrics.xdpi.takeIf { it > 0f } ?: 160f
        return cm * xdpi / 2.54f / density
    }

    private fun moveFloatingPosition(dx: Int, dy: Int) {
        scanDisplayBounds()
        positionXdp += dx
        positionYdp += dy
        clampPosition()
        saveSettings()
        updateFloatingService()
    }

    private fun matchWrap(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    // -------------------------------------------------------------
    // COLOR STUDIO - LIQUID GLASS STYLE
    // -------------------------------------------------------------

    private fun showColorStudio() {
        val scroll = android.widget.ScrollView(this)
        val panel = glassPanel().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
        }
        panel.addView(sectionTitle("COLOR STUDIO PRO"))
        panel.addView(subtitle("Build a custom spectrum palette"))

        val preview = View(this)
        val hsv = FloatArray(3)
        Color.colorToHSV(spectrumColors.last(), hsv)
        var selectedColor = spectrumColors.last()
        var selectedPalette: IntArray? = null
        preview.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            spectrumColors.toIntArray()
        ).apply { cornerRadius = 18.dp().toFloat() }
        panel.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 64.dp()).apply { topMargin = 12.dp() })

        val hexInput = EditText(this).apply {
            hint = "HEX color, contoh #8B5CF6"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            background = glassDrawable()
        }
        panel.addView(hexInput, matchWrap().apply { topMargin = 10.dp() })
        var updatingHexField = false
        hexInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (!updatingHexField) selectedPalette = null
                }
                override fun afterTextChanged(s: Editable?) {}
        })

        fun setHexField(value: String) {
            updatingHexField = true
            hexInput.setText(value)
            hexInput.setSelection(hexInput.text.length)
            updatingHexField = false
        }

        fun refreshPreview() {
            selectedColor = Color.HSVToColor(hsv)
            preview.background = GradientDrawable().apply {
                setColor(selectedColor)
                cornerRadius = 18.dp().toFloat()
            }
            setHexField(String.format("#%06X", 0xFFFFFF and selectedColor))
        }

        fun addColorSlider(title: String, maxValue: Int, current: Int, callback: (Int) -> Unit) {
            val label = subtitle("$title  $current")
            label.setPadding(0, 10.dp(), 0, 0)
            val slider = SeekBar(this).apply { max = maxValue; progress = current.coerceIn(0, maxValue) }
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        callback(progress)
                        label.text = "$title  $progress"
                        selectedPalette = null
                        refreshPreview()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            panel.addView(label)
            panel.addView(slider)
        }

        addColorSlider("HUE", 360, hsv[0].toInt()) { hsv[0] = it.toFloat() }
        addColorSlider("SATURATION", 100, (hsv[1] * 100).toInt()) { hsv[1] = it / 100f }
        addColorSlider("BRIGHTNESS", 100, (hsv[2] * 100).toInt()) { hsv[2] = it / 100f }

        panel.addView(sectionTitle("PRESET GALLERY").apply { setPadding(0, 14.dp(), 0, 8.dp()) })
        val presetScroll = HorizontalScrollView(this)
        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presetScroll.addView(presetRow)
        data class Palette(val name: String, val colors: IntArray)
        val presets = listOf(
            Palette("NEON", intArrayOf(Color.parseColor("#00F5FF"), Color.parseColor("#A855F7"), Color.parseColor("#FF00AA"), Color.WHITE)),
            Palette("SUNSET", intArrayOf(Color.parseColor("#FF1744"), Color.parseColor("#FF9100"), Color.parseColor("#FFEA00"), Color.parseColor("#9C27B0"))),
            Palette("OCEAN", intArrayOf(Color.parseColor("#001F54"), Color.parseColor("#0077B6"), Color.parseColor("#00B4D8"), Color.parseColor("#90E0EF"))),
            Palette("FOREST", intArrayOf(Color.parseColor("#14532D"), Color.parseColor("#22C55E"), Color.parseColor("#A3E635"), Color.parseColor("#FACC15"))),
            Palette("ICE", intArrayOf(Color.WHITE, Color.parseColor("#BAE6FD"), Color.parseColor("#38BDF8"), Color.parseColor("#4F46E5")))
        )
        presets.forEach { preset ->
            val chip = TextView(this).apply {
                text = preset.name
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(Color.WHITE)
                setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, preset.colors).apply {
                    cornerRadius = 16.dp().toFloat()
                    setStroke(1.dp(), 0x66FFFFFF)
                }
                setOnClickListener {
                    selectedPalette = preset.colors
                    Color.colorToHSV(preset.colors.first(), hsv)
                    preview.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, preset.colors)
                    setHexField(String.format("#%06X", 0xFFFFFF and preset.colors.first()))
                }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 48.dp())
            lp.setMargins(0, 0, 8.dp(), 0)
            presetRow.addView(chip, lp)
        }
        panel.addView(presetScroll)
        refreshPreview()
        scroll.addView(panel)

        val dialog = AlertDialog.Builder(this)
        .setView(scroll)
        .setNegativeButton("CANCEL", null)
        .setPositiveButton("APPLY PALETTE", null)
        .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val hex = hexInput.text.toString().trim()
                val manualColor = try {
                    if (hex.isNotEmpty()) Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") else selectedColor
                } catch (_: Exception) {
                    Toast.makeText(this, "Kode HEX tidak valid", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                spectrumColors.clear()
                val palette = selectedPalette
                if (palette != null) spectrumColors.addAll(palette.toList()) else {
                    spectrumColors.add(Color.parseColor("#22D3EE"))
                    spectrumColors.add(manualColor)
                    spectrumColors.add(Color.parseColor("#A855F7"))
                }
                if (spectrumColors.size > 12) {
                    while (spectrumColors.size > 12) spectrumColors.removeAt(0)
                }
                saveSettings()
                buildSpectrumBars()
                updateFloatingService()
                dialog.dismiss()
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout((screenWidthPx * 0.92f).toInt(), (screenHeightPx * 0.82f).toInt())
    }

    // -------------------------------------------------------------
    // GLASS UI HELPERS
    // -------------------------------------------------------------

    private fun glassDrawable(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#E629344A"), Color.parseColor("#E6141B2A"))
    ).apply {
        cornerRadius = 18.dp().toFloat()
        setStroke(1.dp(), Color.parseColor("#667D91B8"))
    }

    private fun glassPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = glassDrawable()
    }

    private fun glassButton(textValue: String): TextView = TextView(this).apply {
        text = textValue
        gravity = Gravity.CENTER
        textSize = 12f
        setTextColor(Color.WHITE)
        setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#332C4260"))
            cornerRadius = 16.dp().toFloat()
            setStroke(1.dp(), Color.parseColor("#667D91B8"))
        }
        isClickable = true
        isFocusable = true
    }

    private fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun subtitle(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 11f
        setTextColor(Color.parseColor("#AEBBD0"))
    }

    // -------------------------------------------------------------
    // PERMISSIONS / AUDIO SESSION
    // -------------------------------------------------------------

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, REQUEST_OVERLAY)
        }
    }

    private fun startFloatingFromButton() {
        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayStart = true
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQUEST_OVERLAY
            )
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            continueAudioPermissionFlow()
        }
    }

    private fun continueAudioPermissionFlow() {
        if (Build.VERSION.SDK_INT >= 29) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_PROJECTION)
        } else {
            startCaptureService(null, Activity.RESULT_OK, true)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                continueAudioPermissionFlow()
            } else {
                Toast.makeText(this, "Izin audio belum diberikan", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) {
            if (pendingOverlayStart) {
                pendingOverlayStart = false
                if (Settings.canDrawOverlays(this)) startFloatingFromButton()
            }
            return
        }
        if (requestCode == REQUEST_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startCaptureService(data, resultCode, false)
            } else {
                Toast.makeText(this, "Persetujuan audio dibatalkan", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCaptureService(projectionData: Intent?, resultCode: Int, useMic: Boolean) {
        scanDisplayBounds()
        clampPosition()
        val serviceIntent = Intent(this, FloatingVisualizerService::class.java).apply {
            putExtra(FloatingVisualizerService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FloatingVisualizerService.EXTRA_PROJECTION_DATA, projectionData)
            putExtra(FloatingVisualizerService.EXTRA_USE_MIC, useMic)
            addFloatingSettings(this)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            floatingServiceStarted = true
            prefs.edit().putBoolean("service_active", true).apply()
            saveSettings()
            Toast.makeText(this, "FLOATING DJ STARTED", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            floatingServiceStarted = false
            prefs.edit().putBoolean("service_active", false).apply()
            Toast.makeText(this, "Service gagal: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun addFloatingSettings(intent: Intent) {
        intent.putExtra(FloatingVisualizerService.EXTRA_VERTICAL, isVerticalMode)
        intent.putExtra(FloatingVisualizerService.EXTRA_COLORS, spectrumColors.toIntArray())
        intent.putExtra(FloatingVisualizerService.EXTRA_WIDTH, overlayWidthDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_HEIGHT, overlayHeightDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_BAR_WIDTH, barWidthDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_GAP, barGapDp)
        intent.putExtra(FloatingVisualizerService.EXTRA_SCALE, scalePercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_OPACITY, opacityPercent)
        intent.putExtra(FloatingVisualizerService.EXTRA_SINGLE_MODE, singleAudioMode)
        intent.putExtra(FloatingVisualizerService.EXTRA_BASS_MIN_HZ, bassMinHz)
        intent.putExtra(FloatingVisualizerService.EXTRA_BASS_MAX_HZ, bassMaxHz)
        intent.putExtra(FloatingVisualizerService.EXTRA_POSITION_X, positionXdp)
        intent.putExtra(FloatingVisualizerService.EXTRA_POSITION_Y, positionYdp)
        intent.putExtra(FloatingVisualizerService.EXTRA_POSITION_MODE, positionMode)
        intent.putExtra(FloatingVisualizerService.EXTRA_ROTATION, rotationDegrees)
    }

    private fun updateFloatingService() {
        if (!floatingServiceStarted) return
        positionXdp = prefs.getInt("position_x", positionXdp)
        positionYdp = prefs.getInt("position_y", positionYdp)
        val intent = Intent(this, FloatingVisualizerService::class.java).apply {
            action = FloatingVisualizerService.ACTION_UPDATE
            addFloatingSettings(this)
        }
        try {
            startService(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memperbarui floating: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopFloating() {
        floatingServiceStarted = false
        updateHandler.removeCallbacks(realtimeUpdate)
        stopService(Intent(this, FloatingVisualizerService::class.java))
        for (i in 0 until barsContainer.childCount) barsContainer.getChildAt(i).scaleY = 0.08f
        prefs.edit().putBoolean("service_active", false).apply()
        Toast.makeText(this, "FLOATING DJ STOPPED", Toast.LENGTH_SHORT).show()
    }
    
    
    private fun enableEdgeToEdgeLayout() {
        val root = findViewById<View>(R.id.mainRoot)
        val content = findViewById<View>(R.id.contentRoot)
        val baseLeft = content.paddingLeft
        val baseTop = content.paddingTop
        val baseRight = content.paddingRight
        val baseBottom = content.paddingBottom

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        root.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val safeInsets = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                topInset = safeInsets.top
                bottomInset = safeInsets.bottom
            } else {
                @Suppress("DEPRECATION")
                run {
                    topInset = insets.systemWindowInsetTop
                    bottomInset = insets.systemWindowInsetBottom
                }
            }
            content.setPadding(
                baseLeft,
                baseTop + topInset,
                baseRight,
                baseBottom + bottomInset
            )
            insets
        }
        root.requestApplyInsets()
    }
}
