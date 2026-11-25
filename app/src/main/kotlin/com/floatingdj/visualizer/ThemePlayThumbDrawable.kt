package com.floatingdj.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.R

/**
 * Intrinsic-sized thumb drawable with a high-contrast Play/Pause glyph.
 * Since the glyph is drawn inside the thumb drawable itself, it follows the
 * platform Switch thumb animation instead of being positioned independently.
 */
class ThemePlayThumbDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val sizePx = (22f * density + 0.5f).toInt()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPath = Path()
    private val glyphRect = RectF()

    private var normalFill = Color.WHITE
    private var disabledFill = Color.rgb(220, 226, 235)
    private var edgeColor = 0x668FA3C1
    private var glyphColor = Color.rgb(38, 48, 66)
    private var drawableAlpha = 255

    fun setThemeColors(
        fillColor: Int,
        disabledFillColor: Int,
        strokeColor: Int,
        iconColor: Int
    ) {
        normalFill = fillColor
        disabledFill = disabledFillColor
        edgeColor = strokeColor
        glyphColor = iconColor
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val diameter = minOf(b.width(), b.height()).toFloat()
        if (diameter <= 0f) return

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val radius = diameter * 0.47f
        val enabled = state.contains(R.attr.state_enabled)
        val checked = state.contains(R.attr.state_checked)

        fillPaint.color = if (enabled) normalFill else disabledFill
        fillPaint.alpha = drawableAlpha
        fillPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, fillPaint)

        edgePaint.color = edgeColor
        edgePaint.alpha = drawableAlpha
        canvas.drawCircle(cx, cy, radius, edgePaint)

        glyphPaint.color = glyphColor
        glyphPaint.alpha = if (enabled) drawableAlpha else (drawableAlpha * 0.62f).toInt()

        val iconSize = diameter * 0.43f
        if (checked) {
            val barW = iconSize * 0.24f
            val gap = iconSize * 0.17f
            val barH = iconSize * 0.82f
            val left = cx - gap / 2f - barW
            glyphRect.set(left, cy - barH / 2f, left + barW, cy + barH / 2f)
            canvas.drawRoundRect(glyphRect, barW / 2f, barW / 2f, glyphPaint)
            glyphRect.set(cx + gap / 2f, cy - barH / 2f, cx + gap / 2f + barW, cy + barH / 2f)
            canvas.drawRoundRect(glyphRect, barW / 2f, barW / 2f, glyphPaint)
        } else {
            glyphPath.reset()
            glyphPath.moveTo(cx - iconSize * 0.28f, cy - iconSize * 0.48f)
            glyphPath.lineTo(cx + iconSize * 0.48f, cy)
            glyphPath.lineTo(cx - iconSize * 0.28f, cy + iconSize * 0.48f)
            glyphPath.close()
            canvas.drawPath(glyphPath, glyphPaint)
        }
    }

    override fun getIntrinsicWidth(): Int = sizePx
    override fun getIntrinsicHeight(): Int = sizePx
    override fun isStateful(): Boolean = true
    override fun onStateChange(state: IntArray): Boolean {
        invalidateSelf()
        return true
    }
    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }
    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        edgePaint.colorFilter = colorFilter
        glyphPaint.colorFilter = colorFilter
        invalidateSelf()
    }
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
