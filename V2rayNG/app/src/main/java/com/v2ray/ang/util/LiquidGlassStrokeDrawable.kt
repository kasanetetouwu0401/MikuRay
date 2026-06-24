package com.v2ray.ang.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Direct Kotlin port of Telegram's `org.telegram.ui.Components.blur3.StrokeDrawable`
 * (+ the relevant static `drawStroke` helper from `BlurredBackgroundDrawable`).
 *
 * This draws a rounded-rect fill plus TWO separate strokes:
 *  - a brighter stroke clipped to just the top half of the shape
 *  - a dimmer stroke clipped to just the bottom half of the shape
 * That's what gives the "light catching the rim of a glass panel" look —
 * a single uniform GradientDrawable stroke can't do this, since it has no
 * concept of "top edge vs bottom edge" brightness.
 *
 * No blur, no RenderEffect — purely vector/canvas drawing, cheap to redraw.
 */
class LiquidGlassStrokeDrawable(density: Float) : Drawable() {

    companion object {
        // Values lifted from Telegram's BlurredBackgroundColorProviderThemed (dark theme branch)
        const val DARK_STROKE_TOP = 0x28FFFFFF.toInt()    // ~16% white
        const val DARK_STROKE_BOTTOM = 0x14FFFFFF.toInt() // ~8% white

        // Light theme branch uses full white but relies on a drop shadow to read as "glass";
        // since we don't draw a shadow here, use a softer translucent white instead.
        const val LIGHT_STROKE_TOP = 0x59FFFFFF.toInt()    // ~35% white
        const val LIGHT_STROKE_BOTTOM = 0x29FFFFFF.toInt() // ~16% white
    }

    private val rect = RectF()
    private var alphaFactor = 1f

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintStrokeTop = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val paintStrokeBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val strokeWidthTop = 1f * density
    private val strokeWidthBottom = (2f / 3f) * density

    var cornerRadius: Float = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    var strokeColorTop: Int = 0
        set(value) {
            field = value
            paintStrokeTop.color = multAlpha(value, alphaFactor)
            paintStrokeTop.strokeWidth = strokeWidthTop
            invalidateSelf()
        }

    var strokeColorBottom: Int = 0
        set(value) {
            field = value
            paintStrokeBottom.color = multAlpha(value, alphaFactor)
            paintStrokeBottom.strokeWidth = strokeWidthBottom
            invalidateSelf()
        }

    fun setFillColor(color: Int) {
        paintFill.color = color
        invalidateSelf()
    }

    /** Convenience: pick light/dark stroke values to match the current theme. */
    fun applyThemeDefaults(isDark: Boolean) {
        if (isDark) {
            strokeColorTop = DARK_STROKE_TOP
            strokeColorBottom = DARK_STROKE_BOTTOM
        } else {
            strokeColorTop = LIGHT_STROKE_TOP
            strokeColorBottom = LIGHT_STROKE_BOTTOM
        }
    }

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        if (rect.isEmpty || cornerRadius <= 0f) return

        if (Color.alpha(paintFill.color) > 0) {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paintFill)
        }
        if (Color.alpha(strokeColorTop) > 0) {
            drawStroke(canvas, rect, cornerRadius, strokeWidthTop, isTop = true, paint = paintStrokeTop)
        }
        if (Color.alpha(strokeColorBottom) > 0) {
            drawStroke(canvas, rect, cornerRadius, strokeWidthBottom, isTop = false, paint = paintStrokeBottom)
        }
    }

    /** Port of BlurredBackgroundDrawable.drawStroke(Canvas, RectF, float, float, boolean, Paint). */
    private fun drawStroke(
        canvas: Canvas,
        r: RectF,
        radii: Float,
        strokeWidth: Float,
        isTop: Boolean,
        paint: Paint
    ) {
        val strokeHalf = strokeWidth / 2f
        canvas.save()
        try {
            if (isTop) {
                val clipBottom = (r.top + radii * 2f).coerceIn(r.top, r.bottom)
                if (canvas.clipRect(r.left - strokeHalf, r.top, r.right + strokeHalf, clipBottom)) {
                    canvas.drawRoundRect(
                        r.left - strokeHalf,
                        r.top + strokeHalf,
                        r.right + strokeHalf,
                        r.bottom + strokeHalf,
                        radii, radii,
                        paint
                    )
                }
            } else {
                val clipTop = (r.bottom - radii * 2f).coerceIn(r.top, r.bottom)
                if (canvas.clipRect(r.left - strokeHalf, clipTop, r.right + strokeHalf, r.bottom)) {
                    canvas.drawRoundRect(
                        r.left - strokeHalf,
                        r.top - strokeHalf,
                        r.right + strokeHalf,
                        r.bottom - strokeHalf,
                        radii, radii,
                        paint
                    )
                }
            }
        } finally {
            canvas.restore()
        }
    }

    override fun setAlpha(alpha: Int) {
        alphaFactor = alpha / 255f
        paintFill.alpha = (Color.alpha(paintFill.color) * alphaFactor).toInt()
        paintStrokeTop.color = multAlpha(strokeColorTop, alphaFactor)
        paintStrokeBottom.color = multAlpha(strokeColorBottom, alphaFactor)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) { /* not supported */ }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private fun multAlpha(color: Int, factor: Float): Int {
        val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
