package com.v2ray.ang.util

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Draws a subtle "glass glare" highlight on top of a View:
 *  - a soft diagonal specular sweep across the upper portion
 *  - a thin bright stroke that fades along the top edge
 *
 * Meant to be used as a View's foreground (setForeground), so it never
 * affects the view's background/ripple, layout, or touch handling.
 */
class GlareDrawable(
    private val cornerRadiusPx: Float = 0f,
    private val highlightAlpha: Int = 70,
    private val strokeAlpha: Int = 90
) : Drawable() {

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp()
    }

    private val clipRect = RectF()

    private fun Float.dp(): Float = this

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        clipRect.set(0f, 0f, w, h)

        // Diagonal sweep highlight, brightest near the top-left, fading out
        // a bit past the vertical midpoint. Mimics light glancing off glass.
        highlightPaint.shader = LinearGradient(
            0f, 0f,
            w * 0.65f, h * 0.9f,
            intArrayOf(
                argbWithAlpha(highlightAlpha),
                argbWithAlpha((highlightAlpha * 0.35f).toInt()),
                argbWithAlpha(0)
            ),
            floatArrayOf(0f, 0.35f, 0.85f),
            Shader.TileMode.CLAMP
        )

        // Thin bright edge along the top, fading toward the sides.
        strokePaint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(
                argbWithAlpha(0),
                argbWithAlpha(strokeAlpha),
                argbWithAlpha((strokeAlpha * 0.4f).toInt()),
                argbWithAlpha(0)
            ),
            floatArrayOf(0f, 0.15f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun argbWithAlpha(alpha: Int) =
        android.graphics.Color.argb(alpha.coerceIn(0, 255), 255, 255, 255)

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        val saveCount = canvas.save()
        if (cornerRadiusPx > 0f) {
            val path = android.graphics.Path().apply {
                addRoundRect(
                    bounds.left.toFloat(), bounds.top.toFloat(),
                    bounds.right.toFloat(), bounds.bottom.toFloat(),
                    cornerRadiusPx, cornerRadiusPx,
                    android.graphics.Path.Direction.CW
                )
            }
            canvas.clipPath(path)
        }

        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.drawRect(clipRect, highlightPaint)

        // Top edge highlight stroke (inset slightly so it isn't clipped).
        val inset = strokePaint.strokeWidth / 2f
        canvas.drawLine(inset, inset, clipRect.width() - inset, inset, strokePaint)

        canvas.restoreToCount(saveCount)
    }

    override fun setAlpha(alpha: Int) {
        highlightPaint.alpha = alpha
        strokePaint.alpha = alpha
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        highlightPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }
}
