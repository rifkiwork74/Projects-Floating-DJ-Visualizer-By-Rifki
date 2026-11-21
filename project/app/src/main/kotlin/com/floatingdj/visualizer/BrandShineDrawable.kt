package com.floatingdj.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.sin

/**
 * Lightweight, theme-aware light sweep drawn above the main brand title.
 * All geometry is computed from the title bounds; no bitmap, blur layer,
 * random allocation, or background thread is used during animation.
 */
internal class BrandShineDrawable(context: Context) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private var progress = 0f
    private var accentColor = Color.rgb(155, 123, 255)
    private var edgeColor = Color.rgb(220, 230, 255)

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bloomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Deterministic particle positions keep every frame allocation-free and stable.
    private val particleSeeds = arrayOf(
        floatArrayOf(-0.72f, -0.38f, 0.10f),
        floatArrayOf(-0.48f, 0.24f, 0.34f),
        floatArrayOf(-0.25f, -0.16f, 0.56f),
        floatArrayOf(-0.08f, 0.43f, 0.78f),
        floatArrayOf(0.12f, -0.47f, 0.92f),
        floatArrayOf(0.28f, 0.12f, 1.14f),
        floatArrayOf(-0.88f, 0.06f, 1.36f),
        floatArrayOf(0.45f, -0.28f, 1.58f),
        floatArrayOf(-0.36f, 0.54f, 1.80f),
        floatArrayOf(0.02f, -0.61f, 2.02f),
        floatArrayOf(0.62f, 0.38f, 2.24f),
        floatArrayOf(-0.60f, -0.58f, 2.46f)
    )

    fun setPalette(accent: Int, edge: Int) {
        accentColor = accent
        edgeColor = edge
        invalidateSelf()
    }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (progress <= 0.001f) return
        val bounds = bounds
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        if (width <= 1f || height <= 1f) return

        val travelX = (-0.46f + progress * 1.92f) * width
        val halfRise = height * 0.34f
        val startX = travelX - halfRise * 0.58f
        val startY = height * 0.88f
        val endX = travelX + halfRise * 0.58f
        val endY = height * 0.12f
        val phase = progress * (Math.PI * 2.0).toFloat()
        val pulse = 0.76f + 0.24f * sin(phase * 2.0f)

        canvas.save()
        canvas.clipRect(bounds)

        // Layered strokes produce a restrained glow without an expensive blur.
        glowPaint.color = accentColor
        glowPaint.alpha = (42f * pulse).toInt().coerceIn(0, 255)
        glowPaint.strokeWidth = 10f * density
        canvas.drawLine(startX, startY, endX, endY, glowPaint)

        bloomPaint.color = edgeColor
        bloomPaint.alpha = (115f * pulse).toInt().coerceIn(0, 255)
        bloomPaint.strokeWidth = 4.5f * density
        canvas.drawLine(startX, startY, endX, endY, bloomPaint)

        corePaint.color = Color.WHITE
        corePaint.alpha = (238f * pulse).toInt().coerceIn(0, 255)
        corePaint.strokeWidth = 1.35f * density
        canvas.drawLine(startX, startY, endX, endY, corePaint)

        // A small, deterministic sparkle cloud follows the moving light slash.
        particleSeeds.forEachIndexed { index, seed ->
            val twinkle = (0.5f + 0.5f * sin(phase * 1.35f + seed[2])).coerceIn(0f, 1f)
            val alpha = (18f + 112f * twinkle).toInt().coerceIn(0, 130)
            val x = travelX + seed[0] * height * 0.62f
            val y = height * 0.5f + seed[1] * height * 0.58f
            val radius = (0.45f + twinkle * 0.72f) * density
            particlePaint.color = if (index % 3 == 0) edgeColor else Color.WHITE
            particlePaint.alpha = alpha
            canvas.drawCircle(x, y, radius, particlePaint)

            if (index % 4 == 0 && twinkle > 0.42f) {
                val ray = (2.0f + twinkle * 2.0f) * density
                particlePaint.alpha = (alpha * 0.72f).toInt()
                canvas.drawRect(x - ray, y - 0.45f * density, x + ray, y + 0.45f * density, particlePaint)
                canvas.drawRect(x - 0.45f * density, y - ray, x + 0.45f * density, y + ray, particlePaint)
            }
        }

        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        glowPaint.alpha = alpha
        bloomPaint.alpha = alpha
        corePaint.alpha = alpha
        particlePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        glowPaint.colorFilter = colorFilter
        bloomPaint.colorFilter = colorFilter
        corePaint.colorFilter = colorFilter
        particlePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Android graphics API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
