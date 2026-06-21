package com.v2ray.ang.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Updated GlareDrawable:
 * Modified to match a classic "glossy pill" UI highlight with a sharp 
 * top inner rim light and a smooth top-down linear gradient.
 */
class GlareDrawable(
    private val highlightAlpha: Int = 35, // Ditingkatkan agar efek kaca lebih terlihat
    private val rimAlpha: Int = 90,       // Intensitas garis tajam di tepi atas
    private val shadowAlpha: Int = 30,    // Intensitas bayangan bawah
    private val cornerRadiusProvider: () -> Float = { 0f },
    private val contentRectProvider: (() -> RectF?)? = null
) : Drawable() {

    // Main soft top highlight fill
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Bottom depth shadow fill
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // NEW: Sharp top inner stroke for that "glass edge" look
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f // Sebagian akan terpotong (clipped) oleh canvas, menghasilkan ~1.5px inner border yang rapi
    }

    private val lastRect = RectF()
    private val clipPath = Path() // Optimasi: Reusable path agar tidak boros memori di dalam onDraw
    private var shaderBuilt = false

    private fun ensureShader(rect: RectF) {
        if (shaderBuilt && lastRect == rect) return
        val w = rect.width()
        val h = rect.height()
        if (w <= 0f || h <= 0f) return
        lastRect.set(rect)
        shaderBuilt = true

        // 1. Pantulan utama (Soft Fill) - Menggunakan LinearGradient agar rata ujung ke ujung
        highlightPaint.shader = LinearGradient(
            0f, rect.top,
            0f, rect.top + h * 0.5f,
            intArrayOf(
                argbWithAlpha(highlightAlpha, white = true),
                argbWithAlpha((highlightAlpha * 0.3f).toInt(), white = true),
                argbWithAlpha(0, white = true)
            ),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP
        )

        // 2. Garis pantulan tajam di tepi atas (Rim Light)
        rimPaint.shader = LinearGradient(
            0f, rect.top,
            0f, rect.top + h * 0.35f,
            argbWithAlpha(rimAlpha, white = true),
            argbWithAlpha(0, white = true),
            Shader.TileMode.CLAMP
        )

        // 3. Bayangan gelap di tepi bawah untuk memberi kesan timbul (3D bevel)
        shadowPaint.shader = LinearGradient(
            0f, rect.bottom - h * 0.4f,
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
        
        // Optimasi alokasi Path
        if (cornerRadiusPx > 0f) {
            clipPath.reset()
            clipPath.addRoundRect(contentRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            canvas.clipPath(clipPath)
        } else {
            canvas.clipRect(contentRect)
        }

        // Draw fills (Glow & Shadow)
        canvas.drawRect(contentRect, highlightPaint)
        canvas.drawRect(contentRect, shadowPaint)

        // Draw Rim Light Stroke (Digambar di atas fill)
        // Canvas trick: Karena sudah di-clip sebelumnya, garis tepi ini akan terpotong persis
        // di batas luar, menjadikannya "inner stroke" yang menempel sempurna dengan lengkungan.
        if (cornerRadiusPx > 0f) {
            canvas.drawRoundRect(contentRect, cornerRadiusPx, cornerRadiusPx, rimPaint)
        } else {
            canvas.drawRect(contentRect, rimPaint)
        }

        canvas.restoreToCount(saveCount)
    }

    override fun setAlpha(alpha: Int) {
        highlightPaint.alpha = alpha
        shadowPaint.alpha = alpha
        rimPaint.alpha = alpha
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        highlightPaint.colorFilter = colorFilter
        shadowPaint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
    }
}
