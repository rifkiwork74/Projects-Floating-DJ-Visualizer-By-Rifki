package com.floatingdj.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.util.AttributeSet
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * One-line title renderer with a single-glyph kinetic impact.
 *
 * The outer TextView uses Android's hardware-accelerated scaleX/translationX
 * properties (the V3.0.24 motion path). This class only paints the measured glyphs
 * and applies a short local bubble lift to the character nearest the travelling
 * beam. No per-frame bitmap, Path, Layout, or particle allocation is performed.
 */
internal class KineticTitleTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : TextView(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
    private var sweepProgress = -1f
    private var sweepHeadStartX = -5.2f * density
    private var sweepHeadEndX = 0f
    private var titleMotionScaleX = 1f
    private var titleMotionTranslationX = 0f
    private var gradientStart = Color.WHITE
    private var gradientMiddle = Color.WHITE
    private var gradientEnd = Color.WHITE
    private var gradientMotion = 0f

    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.05f * density
    }
    private val glassGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.0f * density
    }
    private val glassHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 0.82f * density
    }

    fun setBrandGradient(start: Int, middle: Int, end: Int) {
        gradientStart = start
        gradientMiddle = middle
        gradientEnd = end
        invalidate()
    }

    fun setGradientMotion(progress: Float) {
        val next = progress.coerceIn(0f, 1f)
        if (abs(next - gradientMotion) > 0.001f) {
            gradientMotion = next
            invalidate()
        }
    }

    /** Beam-head coordinates are expressed in the untransformed local title space. */
    fun setSlashSweepGeometry(startX: Float, endX: Float) {
        sweepHeadStartX = startX
        sweepHeadEndX = endX
        invalidate()
    }

    /** Mirrors the hardware TextView transform so the beam and glyph hit stay registered. */
    fun setTitleMotion(scaleX: Float, translationX: Float) {
        val next = scaleX.coerceIn(0.88f, 1.12f)
        if (abs(next - titleMotionScaleX) > 0.0005f || abs(translationX - titleMotionTranslationX) > 0.02f) {
            titleMotionScaleX = next
            titleMotionTranslationX = translationX
            invalidate()
        }
    }

    /** -1 disables impact; 0..1 tracks the beam head across the visible title. */
    fun setSlashSweepProgress(progress: Float) {
        val next = if (progress < 0f) -1f else progress.coerceIn(0f, 1f)
        if (abs(next - sweepProgress) > 0.0007f || (next < 0f) != (sweepProgress < 0f)) {
            sweepProgress = next
            invalidate()
        }
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mixColor(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from) + (Color.red(to) - Color.red(from)) * t).toInt().coerceIn(0, 255),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * t).toInt().coerceIn(0, 255),
            (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t).toInt().coerceIn(0, 255)
        )
    }

    /** Periodic palette returns to the same color at phase 0 and 1. */
    private fun gradientColor(position: Float): Int {
        var t = position - gradientMotion
        t = ((t % 1f) + 1f) % 1f
        return when {
            t < 0.25f -> mixColor(gradientStart, gradientMiddle, t / 0.25f)
            t < 0.50f -> mixColor(gradientMiddle, gradientEnd, (t - 0.25f) / 0.25f)
            t < 0.75f -> mixColor(gradientEnd, gradientMiddle, (t - 0.50f) / 0.25f)
            else -> mixColor(gradientMiddle, gradientStart, (t - 0.75f) / 0.25f)
        }
    }

    private fun drawGlyphSparkle(canvas: Canvas, x: Float, y: Float, color: Int, strength: Float, phase: Float) {
        if (strength <= 0.12f) return
        val twinkle = 0.68f + 0.32f * abs(sin(phase * 6.28318f))
        val ray = (1.45f + 2.75f * strength) * density
        sparklePaint.color = color
        sparklePaint.alpha = (176f * strength * twinkle).toInt().coerceIn(0, 190)
        canvas.drawLine(x - ray, y, x + ray, y, sparklePaint)
        canvas.drawLine(x, y - ray, x, y + ray, sparklePaint)
        val diagonal = ray * 0.48f
        canvas.drawLine(x - diagonal, y - diagonal, x + diagonal, y + diagonal, sparklePaint)
        canvas.drawLine(x - diagonal, y + diagonal, x + diagonal, y - diagonal, sparklePaint)
        canvas.drawCircle(x, y, 0.65f * density, sparklePaint)
    }

    override fun onDraw(canvas: Canvas) {
        val currentLayout: Layout? = layout
        val value = text
        if (currentLayout == null || value.isNullOrEmpty() || currentLayout.lineCount != 1 || !isAttachedToWindow) {
            super.onDraw(canvas)
            return
        }

        val lineWidth = currentLayout.getLineWidth(0).coerceAtLeast(1f)
        val originX = compoundPaddingLeft + currentLayout.getLineLeft(0)
        val lineStart = currentLayout.getLineStart(0)
        val lineEnd = currentLayout.getLineEnd(0).coerceAtMost(value.length)
        val baselineY = baseline.toFloat()
        val beamActive = sweepProgress >= 0f

        // The beam is measured in the parent header's coordinates. Invert only the
        // hardware TextView transform to find the corresponding local glyph position.
        val visualHeadX = if (beamActive) {
            originX + sweepHeadStartX + (sweepHeadEndX - sweepHeadStartX) * sweepProgress
        } else Float.NEGATIVE_INFINITY
        val localHeadX = if (beamActive) {
            originX + (visualHeadX - titleMotionTranslationX - originX) / titleMotionScaleX
        } else Float.NEGATIVE_INFINITY

        val textPaint = paint
        val oldShader = textPaint.shader
        val oldColor = textPaint.color
        val oldAlpha = textPaint.alpha
        val oldStyle = textPaint.style
        textPaint.shader = null
        textPaint.style = Paint.Style.FILL

        try {
            var index = lineStart
            var visibleGlyphOrdinal = 0
            while (index < lineEnd) {
                val codePoint = Character.codePointAt(value, index)
                val glyphEnd = (index + Character.charCount(codePoint)).coerceAtMost(lineEnd)
                val leftRaw = compoundPaddingLeft + currentLayout.getPrimaryHorizontal(index)
                val rightRaw = compoundPaddingLeft + currentLayout.getPrimaryHorizontal(glyphEnd)
                val glyphLeft = min(leftRaw, rightRaw)
                val glyphRight = max(leftRaw, rightRaw)
                val glyphWidth = (glyphRight - glyphLeft).coerceAtLeast(0f)
                val glyphCenter = (glyphLeft + glyphRight) * 0.5f
                val isWhitespace = Character.isWhitespace(codePoint)
                val isBullet = codePoint == 0x2022 || codePoint == 0x00B7

                if (!isWhitespace && glyphWidth > 0.1f) {
                    val normalized = ((glyphCenter - originX) / lineWidth).coerceIn(0f, 1f)
                    val baseColor = gradientColor(normalized)
                    var bubble = 0f

                    if (beamActive) {
                        // Narrow impact radius keeps the bloom on one glyph at a time;
                        // the adjacent letter only begins lifting as the beam approaches it.
                        val hitRadius = max(6.0f * density, glyphWidth * 0.72f + 2.0f * density)
                        val distance = abs(localHeadX - glyphCenter)
                        val proximity = (1f - distance / hitRadius).coerceIn(0f, 1f)
                        bubble = smoothStep(proximity)
                    }

                    // A compact, rounded bubble impact on each glyph, including the
                    // leading bullet. The bullet receives a slightly stronger lift so
                    // it visibly participates as the beam leaves the left anchor.
                    val scaleX = 1f + (if (isBullet) 0.215f else 0.195f) * bubble
                    val scaleY = 1f + (if (isBullet) 0.175f else 0.155f) * bubble
                    val lift = (if (isBullet) 3.4f else 3.1f) * density * bubble
                    val saveGlyph = canvas.save()
                    canvas.translate(glyphCenter, baselineY - lift)
                    canvas.scale(scaleX, scaleY)

                    textPaint.color = if (bubble > 0.015f) {
                        mixColor(baseColor, Color.WHITE, 0.34f * bubble)
                    } else baseColor
                    textPaint.alpha = oldAlpha
                    canvas.drawText(value, index, glyphEnd, glyphLeft - glyphCenter, 0f, textPaint)

                    // Thin layered reflections create a clear-glass highlight on the
                    // impacted letter. Paint instances are reused; no frame allocations.
                    if (bubble > 0.16f) {
                        val highlightHalf = glyphWidth * (0.12f + 0.25f * bubble)
                        val highlightY = -textPaint.textSize * 0.69f
                        glassGlowPaint.color = mixColor(baseColor, Color.WHITE, 0.64f)
                        glassGlowPaint.alpha = (54f * bubble).toInt().coerceIn(0, 64)
                        canvas.drawLine(
                            -highlightHalf, highlightY,
                            highlightHalf * 0.82f, highlightY - 0.25f * density,
                            glassGlowPaint
                        )
                        glassHighlightPaint.color = Color.WHITE
                        glassHighlightPaint.alpha = (152f * bubble).toInt().coerceIn(0, 172)
                        canvas.drawLine(
                            -highlightHalf * 0.72f, highlightY - 0.35f * density,
                            highlightHalf * 0.52f, highlightY - 0.55f * density,
                            glassHighlightPaint
                        )
                    }

                    // A brief four-ray glint follows the leading top corner of each glyph.
                    if (bubble > 0.34f) {
                        val sparkleColor = mixColor(baseColor, Color.WHITE, 0.62f)
                        drawGlyphSparkle(
                            canvas,
                            (glyphRight - glyphCenter) * 0.76f,
                            -textPaint.textSize * 0.59f,
                            sparkleColor,
                            bubble,
                            gradientMotion + visibleGlyphOrdinal * 0.23f
                        )
                    }
                    canvas.restoreToCount(saveGlyph)
                    visibleGlyphOrdinal++
                }
                index = glyphEnd
            }
        } finally {
            textPaint.shader = oldShader
            textPaint.color = oldColor
            textPaint.alpha = oldAlpha
            textPaint.style = oldStyle
        }
    }
}
