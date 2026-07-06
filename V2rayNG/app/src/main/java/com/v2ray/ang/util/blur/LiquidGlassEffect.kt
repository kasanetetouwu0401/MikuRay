package com.v2ray.ang.util.blur

import android.content.Context
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import com.v2ray.ang.R

/**
 * Ported from NagramXF-dev (org.telegram.ui.Components.blur3.LiquidGlassEffect).
 *
 * This is the "liquid glass" refraction shader effect applied on top of an
 * already-rendered (and usually already-blurred) [RenderNode]. Unlike the
 * original, this version reads the AGSL source directly from resources
 * instead of going through Telegram's `AndroidUtilities.readRes`, since that
 * helper doesn't exist in MikuRay.
 */
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
class LiquidGlassEffect(context: Context, private val node: RenderNode) {

    private val shader: RuntimeShader

    init {
        val code = context.resources.openRawResource(R.raw.liquid_glass_shader)
            .bufferedReader()
            .use { it.readText() }
        shader = RuntimeShader(code)
        node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "img"))
    }

    private var resolutionX = 0f
    private var resolutionY = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var sizeX = 0f
    private var sizeY = 0f
    private var radiusLeftTop = 0f
    private var radiusRightTop = 0f
    private var radiusRightBottom = 0f
    private var radiusLeftBottom = 0f
    private var thickness = 0f
    private var intensity = 0f
    private var index = 0f
    private var foregroundColor = 0

    fun update(
        left: Float, top: Float, right: Float, bottom: Float,
        radiusLeftTopIn: Float, radiusRightTopIn: Float, radiusRightBottomIn: Float, radiusLeftBottomIn: Float,
        thicknessIn: Float,
        intensityIn: Float,
        indexIn: Float,
        foregroundColorIn: Int
    ) {
        var radiusLeftTop = radiusLeftTopIn
        var radiusRightTop = radiusRightTopIn
        var radiusRightBottom = radiusRightBottomIn
        var radiusLeftBottom = radiusLeftBottomIn

        val resolutionX = node.width.toFloat()
        val resolutionY = node.height.toFloat()
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val width = right - left
        val height = bottom - top
        val sizeX = width / 2f
        val sizeY = height / 2f

        if (radiusLeftTop + radiusLeftBottom > height) {
            val a = radiusLeftTop / (radiusLeftTop + radiusLeftBottom)
            radiusLeftTop = height * a
            radiusLeftBottom = height * (1.0f - a)
        }
        if (radiusRightTop + radiusRightBottom > height) {
            val a = radiusRightTop / (radiusRightTop + radiusRightBottom)
            radiusRightTop = height * a
            radiusRightBottom = height * (1.0f - a)
        }

        val changed =
            kotlin.math.abs(this.resolutionX - resolutionX) > 0.1f ||
                kotlin.math.abs(this.resolutionY - resolutionY) > 0.1f ||
                kotlin.math.abs(this.centerX - centerX) > 0.1f ||
                kotlin.math.abs(this.centerY - centerY) > 0.1f ||
                kotlin.math.abs(this.sizeX - sizeX) > 0.1f ||
                kotlin.math.abs(this.sizeY - sizeY) > 0.1f ||
                kotlin.math.abs(this.radiusLeftTop - radiusLeftTop) > 0.1f ||
                kotlin.math.abs(this.radiusRightTop - radiusRightTop) > 0.1f ||
                kotlin.math.abs(this.radiusRightBottom - radiusRightBottom) > 0.1f ||
                kotlin.math.abs(this.radiusLeftBottom - radiusLeftBottom) > 0.1f ||
                kotlin.math.abs(this.thickness - thicknessIn) > 0.1f ||
                kotlin.math.abs(this.intensity - intensityIn) > 0.1f ||
                kotlin.math.abs(this.index - indexIn) > 0.1f ||
                this.foregroundColor != foregroundColorIn

        if (!changed) return

        this.foregroundColor = foregroundColorIn
        val a = Color.alpha(foregroundColorIn) / 255f
        val r = Color.red(foregroundColorIn) / 255f * a
        val g = Color.green(foregroundColorIn) / 255f * a
        val b = Color.blue(foregroundColorIn) / 255f * a

        this.resolutionX = resolutionX; this.resolutionY = resolutionY
        this.centerX = centerX; this.centerY = centerY
        this.sizeX = sizeX; this.sizeY = sizeY
        this.radiusLeftTop = radiusLeftTop; this.radiusRightTop = radiusRightTop
        this.radiusRightBottom = radiusRightBottom; this.radiusLeftBottom = radiusLeftBottom
        this.thickness = thicknessIn
        this.intensity = intensityIn
        this.index = indexIn

        shader.setFloatUniform("resolution", resolutionX, resolutionY)
        shader.setFloatUniform("center", centerX, centerY)
        shader.setFloatUniform("size", sizeX, sizeY)
        shader.setFloatUniform("radius", radiusRightBottom, radiusRightTop, radiusLeftBottom, radiusLeftTop)
        shader.setFloatUniform("thickness", thicknessIn)
        shader.setFloatUniform("refract_intensity", intensityIn)
        shader.setFloatUniform("refract_index", indexIn)
        shader.setFloatUniform("foreground_color_premultiplied", r, g, b, a)
        node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "img"))
    }
}
