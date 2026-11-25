package com.floatingdj.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One shared, measured timeline for the header beam and glyph impact renderer. */
internal object BrandTitleTimeline {
    // A single frame-synchronised clock drives the beam, title transform,
    // gradient and per-glyph impact. The endpoint turn overlaps the final
    // travel interval; there is no stationary dwell between phases.
    const val DURATION_MS = 8000L
    // Beam reveal/fade use the same 200 ms quintic easing window so the loop
    // enters and exits with matching size/opacity. BLOOM_END remains unchanged
    // because it is also the established start boundary for the clockwise spin.
    const val BEAM_REVEAL_END = 0.025f      // 200 ms visual reveal
    const val BLOOM_END = 0.075f            // 600 ms title bloom / unchanged rotation boundary
    const val SPIN_END = 0.30f              // 2,400 ms total; entry rotation remains unchanged
    const val TURN_START = 0.75f            // 6,000 ms: counter-turn begins while the beam is still moving
    const val TURN_END = 0.975f             // 7,800 ms: finish the endpoint turn; matching 200 ms fade follows
}

/**
 * Header-wide slash renderer.
 *
 * The line blooms at a fixed point just LEFT of the title bullet, rotates clockwise
 * into a 40-degree slash, travels once across the measured title, turns counter-clockwise
 * at the right endpoint until horizontal, then fades. It is hosted by the pinned-header
 * overlay (not TextView.overlay), so the full trail is not clipped to glyph bounds.
 * All Paint objects and particle seeds are allocated once; draw() performs no bitmap,
 * Path, RectF, or per-frame object allocation.
 */
internal class BrandShineDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
    private var cycleProgress = -1f
    private var accentColor = Color.rgb(24, 215, 208)
    private var edgeColor = Color.rgb(71, 120, 255)

    private var bulletEdgeX = 24f * density
    private var textLeftX = 26f * density
    private var textWidth = 120f * density
    private var centerY = 25f * density
    private var dashLength = 34f * density
    private var endGapPx = 15f * density

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val hotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // trail fraction, vertical offset, twinkle phase, radius factor
    private val particles = arrayOf(
        floatArrayOf(0.14f, -0.34f, 0.18f, 0.92f),
        floatArrayOf(0.25f, 0.22f, 0.73f, 0.82f),
        floatArrayOf(0.36f, -0.12f, 1.27f, 0.76f),
        floatArrayOf(0.47f, 0.39f, 1.82f, 1.00f),
        floatArrayOf(0.58f, -0.43f, 2.34f, 0.78f),
        floatArrayOf(0.69f, 0.12f, 2.91f, 1.05f),
        floatArrayOf(0.80f, -0.21f, 3.47f, 0.82f),
        floatArrayOf(0.91f, 0.34f, 4.04f, 0.94f),
        floatArrayOf(1.02f, -0.05f, 4.61f, 0.84f),
        floatArrayOf(1.13f, 0.42f, 5.15f, 1.02f),
        floatArrayOf(1.24f, -0.29f, 5.73f, 0.82f),
        floatArrayOf(1.35f, 0.15f, 6.25f, 0.96f)
    )

    fun setGeometry(
        anchorX: Float,
        titleLeft: Float,
        measuredTextWidth: Float,
        baselineY: Float,
        lineLengthPx: Float,
        trailingGapPx: Float
    ) {
        bulletEdgeX = anchorX
        textLeftX = titleLeft
        textWidth = measuredTextWidth.coerceAtLeast(1f)
        centerY = baselineY
        dashLength = lineLengthPx.coerceAtLeast(0f)
        endGapPx = trailingGapPx.coerceIn(8f * density, 28f * density)
        invalidateSelf()
    }

    fun setPalette(accent: Int, edge: Int) {
        accentColor = accent
        edgeColor = edge
        invalidateSelf()
    }

    fun setProgress(value: Float) {
        cycleProgress = if (value < 0f) -1f else value.coerceIn(0f, 1f)
        invalidateSelf()
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        // Quintic smootherstep has zero velocity and acceleration at both ends.
        return t * t * t * (t * (t * 6f - 15f) + 10f)
    }

    private fun lerp(start: Float, end: Float, amount: Float): Float =
        start + (end - start) * amount.coerceIn(0f, 1f)

    private fun drawBeam(
        canvas: Canvas,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        alpha: Float
    ) {
        if (alpha <= 0.001f) return
        auraPaint.color = accentColor
        auraPaint.alpha = (78f * alpha).toInt().coerceIn(0, 92)
        auraPaint.strokeWidth = 10.0f * density
        corePaint.color = edgeColor
        corePaint.alpha = (242f * alpha).toInt().coerceIn(0, 248)
        corePaint.strokeWidth = 3.35f * density
        hotPaint.color = Color.WHITE
        hotPaint.alpha = (220f * alpha).toInt().coerceIn(0, 228)
        hotPaint.strokeWidth = 0.95f * density
        canvas.drawLine(x1, y1, x2, y2, auraPaint)
        canvas.drawLine(x1, y1, x2, y2, corePaint)
        canvas.drawLine(x1, y1, x2, y2, hotPaint)
    }

    override fun draw(canvas: Canvas) {
        val p = cycleProgress
        if (p < 0f) return
        val b = bounds
        val width = b.width().toFloat()
        val height = b.height().toFloat()
        if (width <= 1f || height <= 1f || dashLength < density) return

        val save = canvas.save()
        canvas.translate(b.left.toFloat(), b.top.toFloat())
        canvas.clipRect(0f, 0f, width, height)

        val halfLength = dashLength * 0.5f
        val spinCenterX = bulletEdgeX - halfLength
        // Leave a clear optical breathing gap after the measured title. Since the
        // line is horizontal at the endpoint, its center is derived from halfLength.
        val rightTipTargetX = textLeftX + textWidth + endGapPx
        val rightCenterX = rightTipTargetX - halfLength

        // Shared phase boundaries. The beam keeps moving through TURN_START and
        // reaches its final x-position at the exact moment it becomes horizontal.
        val bloomEnd = BrandTitleTimeline.BLOOM_END
        val spinEnd = BrandTitleTimeline.SPIN_END
        val turnStart = BrandTitleTimeline.TURN_START
        val turnEnd = BrandTitleTimeline.TURN_END

        if (p < bloomEnd) {
            // Reveal reaches full size in the same 200 ms used by the final fade.
            // The remaining portion before BLOOM_END is a clean settled hold;
            // the established rotation start/end timestamps are not changed.
            val bloom = smoothStep((p / BrandTitleTimeline.BEAM_REVEAL_END).coerceIn(0f, 1f))
            val visibleAlpha = bloom
            // Grow symmetrically around a fixed center so the line remains optically centered.
            val currentHalf = halfLength * (0.10f + 0.90f * bloom)
            drawBeam(
                canvas,
                spinCenterX - currentHalf, centerY,
                spinCenterX + currentHalf, centerY,
                visibleAlpha
            )
            headPaint.color = Color.WHITE
            headPaint.alpha = (170f * visibleAlpha).toInt().coerceIn(0, 180)
            canvas.drawCircle(spinCenterX + currentHalf, centerY, (0.8f + 0.7f * bloom) * density, headPaint)
            canvas.restoreToCount(save)
            return
        }

        val centerX: Float
        val angleDegrees: Double
        val alpha: Float
        val lengthScale: Float
        val forwardSweep: Float
        when {
            p < spinEnd -> {
                val spin = smoothStep((p - bloomEnd) / (spinEnd - bloomEnd))
                centerX = spinCenterX
                // Canvas coordinates are y-down: positive angle is clockwise.
                // +320 degrees ends at the visual 40-degree slash.
                angleDegrees = 320.0 * spin.toDouble()
                alpha = 1f
                lengthScale = 1f
                forwardSweep = -1f
            }
            p < turnEnd -> {
                // One continuous travel curve drives both x-position and the
                // final turn. Rotation starts near the right edge while the beam
                // still advances, so it never pauses before turning.
                val travel = smoothStep((p - spinEnd) / (turnEnd - spinEnd))
                val turn = smoothStep((p - turnStart) / (turnEnd - turnStart))
                centerX = lerp(spinCenterX, rightCenterX, travel)
                angleDegrees = if (p < turnStart) 320.0 else lerp(320f, 0f, turn).toDouble()
                alpha = 1f
                lengthScale = 1f
                forwardSweep = travel
            }
            else -> {
                val fade = smoothStep((p - turnEnd) / (1f - turnEnd))
                centerX = rightCenterX
                angleDegrees = 0.0
                alpha = 1f - fade
                // Match the opening 10% -> 100% growth in reverse: the beam
                // contracts to 10% while fading, reaching the same invisible
                // seam state that the next cycle starts from.
                lengthScale = 0.10f + 0.90f * (1f - fade)
                // Keep the endpoint glint/particle tail alive through the fade so
                // it dissolves with the beam instead of disappearing on one frame.
                forwardSweep = 1f
            }
        }

        val currentHalf = halfLength * lengthScale
        val angle = angleDegrees * PI / 180.0
        val dx = (cos(angle) * currentHalf).toFloat()
        val dy = (sin(angle) * currentHalf).toFloat()
        drawBeam(
            canvas,
            centerX - dx, centerY - dy,
            centerX + dx, centerY + dy,
            alpha
        )

        if (forwardSweep >= 0f) {
            val glint = (0.42f + 0.58f * (0.5f + 0.5f * sin(forwardSweep * PI * 9.0))).toFloat()
            val headX = centerX + dx * 0.88f
            val headY = centerY + dy * 0.88f
            headPaint.color = Color.WHITE
            headPaint.alpha = (210f * glint * alpha).toInt().coerceIn(0, 218)
            canvas.drawCircle(headX, headY, (1.45f + glint) * density, headPaint)

            var index = 0
            while (index < particles.size) {
                val seed = particles[index]
                val twinkle = (0.30f + 0.70f * (
                    0.5f + 0.5f * sin(forwardSweep * PI * 7.0 + seed[2].toDouble())
                )).toFloat()
                val x = centerX - seed[0] * dashLength * 1.10f
                val y = centerY + seed[1] * 12.5f * density
                val radius = (0.45f + 1.28f * twinkle) * seed[3] * density
                particlePaint.color = when (index % 4) {
                    0 -> Color.WHITE
                    1 -> edgeColor
                    2 -> accentColor
                    else -> Color.WHITE
                }
                particlePaint.alpha = (108f * twinkle * alpha).toInt().coerceIn(0, 116)
                canvas.drawCircle(x, y, radius, particlePaint)
                if (index % 3 == 0 && twinkle > 0.66f) {
                    val ray = (1.2f + 1.9f * twinkle) * density
                    canvas.drawLine(x - ray, y, x + ray, y, particlePaint)
                    canvas.drawLine(x, y - ray, x, y + ray, particlePaint)
                }
                index++
            }
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) {
        auraPaint.alpha = alpha
        corePaint.alpha = alpha
        hotPaint.alpha = alpha
        particlePaint.alpha = alpha
        headPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        auraPaint.colorFilter = colorFilter
        corePaint.colorFilter = colorFilter
        hotPaint.colorFilter = colorFilter
        particlePaint.colorFilter = colorFilter
        headPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android graphics API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
