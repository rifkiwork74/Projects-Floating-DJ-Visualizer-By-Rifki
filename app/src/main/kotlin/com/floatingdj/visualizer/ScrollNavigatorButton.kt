package com.floatingdj.visualizer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.min

/**
 * Circular liquid-glass section navigator.
 * Tap: next named section. Hold: an expanding tactile button and a filling ring;
 * on completion it performs the long scroll to the bottom.
 */
internal class ScrollNavigatorButton(context: Context) : View(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val arcRect = RectF()
    private val holdArcRect = RectF()
    private val arrowPath = Path()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.05f * density
    }
    private val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.85f * density
    }
    private val pageTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.45f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val pageArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.15f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val holdTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val holdArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.45f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val holdGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6.2f * density
        strokeCap = Paint.Cap.ROUND
    }
    // A separate thin stroke keeps the expanding glass-window halo visible
    // outside the button, including when the button reaches its 118% hold scale.
    private val expandingWindowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.25f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var topColor = Color.rgb(22, 49, 76)
    private var bottomColor = Color.rgb(9, 24, 45)
    private var outlineColor = Color.argb(170, 103, 197, 230)
    private var arrowColor = Color.WHITE
    private var progressColor = Color.rgb(42, 220, 224)
    private var trackColor = Color.argb(68, 190, 213, 230)
    private var backgroundShader: Shader? = null
    private var innerShader: Shader? = null

    private var pageProgress = 0f
    private var holdProgress = 0f
    // A right chevron rotated 90° points down; 270° points up.
    private var arrowRotation = 90f
    private var arrowTargetRotation = 90f
    private var pressScale = 1f
    private var feedbackScale = 1f

    private var arrowAnimator: ValueAnimator? = null
    private var holdAnimator: ValueAnimator? = null
    private var pressScaleAnimator: ValueAnimator? = null
    private var feedbackAnimator: ValueAnimator? = null
    private var holdCompleted = false
    private var holdCancelledByMove = false
    private var downX = 0f
    private var downY = 0f
    private var tapAction: (() -> Unit)? = null
    private var holdCompleteAction: (() -> Unit)? = null

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Go to next section; hold to scroll to bottom"
        minimumWidth = (68f * density).toInt()
        minimumHeight = (68f * density).toInt()
        elevation = 9f * density
    }

    fun setNavigationActions(onTap: () -> Unit, onHoldComplete: () -> Unit) {
        tapAction = onTap
        holdCompleteAction = onHoldComplete
    }

    fun setPalette(
        top: Int,
        bottom: Int,
        outline: Int,
        icon: Int,
        progress: Int,
        track: Int = Color.argb(68, 190, 213, 230)
    ) {
        topColor = top
        bottomColor = bottom
        outlineColor = outline
        arrowColor = icon
        progressColor = progress
        trackColor = track
        updateShaders(width, height)
        invalidate()
    }

    fun setPageProgress(value: Float) {
        val next = value.coerceIn(0f, 1f)
        if (abs(next - pageProgress) > 0.003f) {
            pageProgress = next
            invalidate()
        }
    }

    fun setAtBottom(
        atBottom: Boolean,
        nextDescription: String = "Go to next section",
        topDescription: String = "Back to top"
    ) {
        contentDescription = if (atBottom) "$topDescription; hold to scroll to bottom" else "$nextDescription; hold to scroll to bottom"
        val target = if (atBottom) 270f else 90f
        if (abs(target - arrowTargetRotation) < 0.5f) return
        arrowTargetRotation = target
        arrowAnimator?.cancel()
        arrowAnimator = ValueAnimator.ofFloat(arrowRotation, target).apply {
            duration = 205L
            interpolator = DecelerateInterpolator(1.55f)
            addUpdateListener {
                arrowRotation = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** Subtle draw-space feedback; avoids changing the measured/touch target size. */
    fun playNavigationFeedback() {
        feedbackAnimator?.cancel()
        feedbackAnimator = ValueAnimator.ofFloat(feedbackScale, 0.945f, 1f).apply {
            duration = 218L
            interpolator = DecelerateInterpolator(1.35f)
            addUpdateListener {
                feedbackScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun updateShaders(w: Int, h: Int) {
        if (w <= 0 || h <= 0) {
            backgroundShader = null
            innerShader = null
            return
        }
        backgroundShader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(), topColor, bottomColor, Shader.TileMode.CLAMP
        )
        innerShader = LinearGradient(
            0f, h * 0.20f, w.toFloat(), h * 0.82f,
            lighten(topColor, 0.13f), bottomColor, Shader.TileMode.CLAMP
        )
    }

    private fun lighten(color: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(color) + (255 - Color.red(color)) * t).toInt(),
            (Color.green(color) + (255 - Color.green(color)) * t).toInt(),
            (Color.blue(color) + (255 - Color.blue(color)) * t).toInt()
        )
    }

    private fun animatePressScaleToRest() {
        pressScaleAnimator?.cancel()
        pressScaleAnimator = ValueAnimator.ofFloat(pressScale, 1f).apply {
            duration = 175L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                pressScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun startHoldProgress() {
        holdAnimator?.cancel()
        pressScaleAnimator?.cancel()
        holdCompleted = false
        holdCancelledByMove = false
        holdProgress = 0f
        pressScale = 0.94f
        holdAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500L
            interpolator = LinearInterpolator()
            addUpdateListener {
                holdProgress = it.animatedValue as Float
                // The whole glass control expands while an outer window ring
                // grows behind it. Both motions are driven by this same hold clock.
                pressScale = 0.94f + 0.24f * holdProgress
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isPressed && !holdCancelledByMove && holdProgress >= 0.999f && !holdCompleted) {
                        holdCompleted = true
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        holdCompleteAction?.invoke()
                    }
                }
            })
            start()
        }
    }

    private fun cancelHoldProgress() {
        holdAnimator?.cancel()
        holdAnimator = null
        holdProgress = 0f
        animatePressScaleToRest()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isEnabled) return false
                downX = event.x
                downY = event.y
                isPressed = true
                holdCompleted = false
                holdCancelledByMove = false
                parent?.requestDisallowInterceptTouchEvent(true)
                startHoldProgress()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPressed && !holdCompleted) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        holdCancelledByMove = true
                        isPressed = false
                        cancelHoldProgress()
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val shouldTap = isPressed && !holdCompleted && !holdCancelledByMove
                holdAnimator?.cancel()
                holdAnimator = null
                isPressed = false
                holdProgress = 0f
                animatePressScaleToRest()
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                if (shouldTap) performClick()
                holdCompleted = false
                holdCancelledByMove = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                holdAnimator?.cancel()
                holdAnimator = null
                isPressed = false
                holdProgress = 0f
                holdCompleted = false
                holdCancelledByMove = true
                animatePressScaleToRest()
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        tapAction?.invoke()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShaders(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width, height).toFloat()
        if (side <= 0f) return

        val cx = width * 0.5f
        val cy = height * 0.5f
        // The expanding halo is drawn before the button's own scale transform,
        // preserving a safe outer margin even while the glass surface reaches 118%.
        val outerRadius = side * 0.5f - 8.0f * density
        if (isPressed && holdProgress > 0.01f) {
            // Keep the ring outside the same 94% -> 118% scale applied to the
            // glass button. Clamp it to the measured 68dp surface so the halo
            // never gets cut off at the view edge on dense displays.
            val buttonScale = 0.94f + 0.24f * holdProgress
            val ringOffset = (1.2f + 0.85f * holdProgress) * density
            val maxRingRadius = side * 0.5f - expandingWindowPaint.strokeWidth * 0.5f - 0.5f * density
            val windowRadius = (outerRadius * buttonScale + ringOffset).coerceAtMost(maxRingRadius)
            expandingWindowPaint.color = progressColor
            expandingWindowPaint.alpha = (18f + 72f * holdProgress).toInt().coerceIn(0, 92)
            canvas.drawCircle(cx, cy, windowRadius, expandingWindowPaint)
            expandingWindowPaint.alpha = 255
        }

        val canvasSave = canvas.save()
        canvas.scale(pressScale * feedbackScale, pressScale * feedbackScale, cx, cy)

        val pageRadius = outerRadius - 0.9f * density
        val innerRadius = outerRadius - 9.2f * density
        val holdRadius = innerRadius + 4.0f * density

        backgroundPaint.shader = backgroundShader
        backgroundPaint.alpha = if (isPressed) 246 else 228
        canvas.drawCircle(cx, cy, outerRadius, backgroundPaint)
        backgroundPaint.shader = null
        backgroundPaint.alpha = 255

        borderPaint.color = outlineColor
        canvas.drawCircle(cx, cy, outerRadius, borderPaint)

        arcRect.set(cx - pageRadius, cy - pageRadius, cx + pageRadius, cy + pageRadius)
        pageTrackPaint.color = trackColor
        canvas.drawArc(arcRect, -90f, 360f, false, pageTrackPaint)
        pageArcPaint.color = progressColor
        pageArcPaint.alpha = 205
        canvas.drawArc(arcRect, -90f, 360f * pageProgress, false, pageArcPaint)
        pageArcPaint.alpha = 255

        innerPaint.shader = innerShader
        innerPaint.alpha = if (isPressed) 255 else 242
        canvas.drawCircle(cx, cy, innerRadius, innerPaint)
        innerPaint.shader = null
        innerPaint.alpha = 255
        innerBorderPaint.color = Color.argb(
            105,
            Color.red(outlineColor),
            Color.green(outlineColor),
            Color.blue(outlineColor)
        )
        canvas.drawCircle(cx, cy, innerRadius, innerBorderPaint)

        holdArcRect.set(cx - holdRadius, cy - holdRadius, cx + holdRadius, cy + holdRadius)
        holdTrackPaint.color = Color.argb(
            if (isPressed) 132 else 42,
            Color.red(trackColor),
            Color.green(trackColor),
            Color.blue(trackColor)
        )
        canvas.drawArc(holdArcRect, -90f, 360f, false, holdTrackPaint)
        if (isPressed && holdProgress > 0.005f) {
            holdGlowPaint.color = progressColor
            holdGlowPaint.alpha = (52f + 54f * holdProgress).toInt().coerceIn(0, 112)
            canvas.drawArc(holdArcRect, -90f, 360f * holdProgress, false, holdGlowPaint)
        }
        holdArcPaint.color = progressColor
        holdArcPaint.alpha = if (isPressed) 255 else 0
        canvas.drawArc(holdArcRect, -90f, 360f * holdProgress, false, holdArcPaint)
        holdArcPaint.alpha = 255
        holdGlowPaint.alpha = 255

        // Manually drawn bold chevron (>) with no arrow stem.
        arrowPath.reset()
        arrowPath.moveTo(cx - 4.6f * density, cy - 7.2f * density)
        arrowPath.lineTo(cx + 3.8f * density, cy)
        arrowPath.lineTo(cx - 4.6f * density, cy + 7.2f * density)
        arrowPaint.color = arrowColor
        arrowPaint.alpha = if (isPressed) 210 else 255
        val arrowSave = canvas.save()
        canvas.rotate(arrowRotation, cx, cy)
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restoreToCount(arrowSave)
        arrowPaint.alpha = 255

        canvas.restoreToCount(canvasSave)
    }

    override fun onDetachedFromWindow() {
        arrowAnimator?.cancel()
        arrowAnimator = null
        holdAnimator?.cancel()
        holdAnimator = null
        pressScaleAnimator?.cancel()
        pressScaleAnimator = null
        feedbackAnimator?.cancel()
        feedbackAnimator = null
        holdProgress = 0f
        pressScale = 1f
        feedbackScale = 1f
        super.onDetachedFromWindow()
    }
}
