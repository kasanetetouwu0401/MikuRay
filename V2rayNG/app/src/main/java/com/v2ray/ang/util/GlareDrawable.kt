package com.v2ray.ang.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Draws a subtle "glass glare" highlight on top of a View: a soft, very
 * faint elliptical glow near the top-center of the shape that tapers out
 * in every direction — rather than a hard-edged full-width band, which
 * read as a flat/oversized "cap" on small circular buttons — paired with
 * an equally faint dark gradient near the bottom edge.
 *
 * The two are combined deliberately so the effect auto-adapts to light
 * and dark surfaces without needing to inspect the view's actual
 * background color: on a dark surface the white top glow is what reads,
 * the dark bottom gradient just blends in invisibly; on a light surface
 * it's the reverse — the dark gradient near the bottom is what gives a
 * visible hint of depth, while the white top glow blends into the
 * already-light surface.
 *
 * Default intensity is deliberately low (barely-there) so it reads as a
 * gentle hint of depth rather than an obvious shine.
 *
 * Meant to be used as a View's foreground (setForeground), so it never
 * affects the view's background/ripple, layout, or touch handling.
 *
 * [cornerRadiusProvider] and [contentRectProvider] are both re-evaluated
 * lazily (cached until the resolved rect actually changes) rather than
 * baked in once at construction time, since the drawable is normally
 * attached before the view's first layout pass.
 *
 * [contentRectProvider] additionally lets the caller account for a view's
 * internal insets (e.g. MaterialButton's insetTop/insetBottom, used to
 * leave shadow room). Without it the glare is drawn across the view's
 * *full* bounds, which can be visibly bigger than the actual rounded
 * surface — making the highlight look offset/detached instead of hugging
 * the button's real edges.
 */
class GlareDrawable(
    private val highlightAlpha: Int = 28,
    private val shadowAlpha: Int = 22,
    private val cornerRadiusProvider: () -> Float = { 0f },
    private val contentRectProvider: (() -> RectF?)? = null
) : Drawable() {

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val lastRect = RectF()
    private var shaderBuilt = false

    private fun ensureShader(rect: RectF) {
        if (shaderBuilt && lastRect == rect) return
        val w = rect.width()
        val h = rect.height()
        if (w <= 0f || h <= 0f) return
        lastRect.set(rect)
        shaderBuilt = true

        // Glow center sits just inside the top edge, horizontally centered.
        val cx = rect.centerX()
        val cy = rect.top + h * 0.05f
        // Ellipse half-extents: contained on small/round shapes, wide but
        // still tapered (not edge-to-edge) on long pills.
        val semiX = w * 0.6f
        val semiY = h * 0.65f

        val radial = RadialGradient(
            cx, cy, 1f,
            intArrayOf(
                argbWithAlpha(highlightAlpha, white = true),
                argbWithAlpha((highlightAlpha * 0.4f).toInt(), white = true),
                argbWithAlpha(0, white = true)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        // Stretch the (unit-radius) radial gradient into an ellipse that
        // matches the shape's aspect ratio.
        radial.setLocalMatrix(Matrix().apply { setScale(semiX, semiY, cx, cy) })
        highlightPaint.shader = radial

        // Faint vertical dark gradient hugging the bottom edge — invisible
        // on dark surfaces, gives a subtle depth cue on light surfaces.
        shadowPaint.shader = LinearGradient(
            0f, rect.top + h * 0.55f,
            0f, rect.bottom,
            argbWithAlpha(0, white = false),
            argbWithAlpha(shadowAlpha, white = false),
            Shader.TileMode.CLAMP
        )
    }

    private fun argbWithAlpha(alpha: Int, white: Boolean): Int {
        val channel = if (white) 255 else 0
        return Color.argb(alpha.coerceIn(0, 255), channel, channel, channel)
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        val contentRect = contentRectProvider?.invoke()?.takeIf { !it.isEmpty }
            ?: RectF(bounds)

        ensureShader(contentRect)

        val saveCount = canvas.save()
        val cornerRadiusPx = cornerRadiusProvider()
        if (cornerRadiusPx > 0f) {
            val path = Path().apply {
                addRoundRect(contentRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            }
            canvas.clipPath(path)
        } else {
            canvas.clipRect(contentRect)
        }

        canvas.drawRect(contentRect, highlightPaint)
        canvas.drawRect(contentRect, shadowPaint)
        canvas.restoreToCount(saveCount)
    }

    override fun setAlpha(alpha: Int) {
        highlightPaint.alpha = alpha
        shadowPaint.alpha = alpha
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        highlightPaint.colorFilter = colorFilter
        shadowPaint.colorFilter = colorFilter
    }
}
