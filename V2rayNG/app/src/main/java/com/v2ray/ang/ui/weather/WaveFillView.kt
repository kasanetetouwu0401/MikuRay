package com.v2ray.ang.ui.weather

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * Gently animated liquid-fill view used behind the Humidity card's
 * percentage text: the water level sits at [fillFraction] of the view's
 * height with a slow sine-wave surface, similar to the "wave" fill Google's
 * weather app uses for the humidity block. Meant to be placed as the first
 * child inside the card's MaterialCardView so it fills the card behind the
 * text content drawn on top of it.
 */
class WaveFillView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var fillFraction: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var waveColor: Int = Color.GREEN
        set(value) {
            field = value
            paint.color = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private var phase = 0f
    private var animator: ValueAnimator? = null

    init {
        paint.color = waveColor
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
                duration = 5000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val waterY = h * (1f - fillFraction)
        val amplitude = (h * 0.018f).coerceAtLeast(2f)
        val steps = 24
        val stepW = w / steps

        path.reset()
        path.moveTo(0f, waterY)
        for (i in 0..steps) {
            val x = i * stepW
            val y = waterY + amplitude * sin((x / w) * Math.PI * 2 * 1.5 + phase).toFloat()
            path.lineTo(x, y)
        }
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()
        canvas.drawPath(path, paint)
    }
}
