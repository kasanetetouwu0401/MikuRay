package com.v2ray.ang.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.qmdeve.blurview.widget.BlurView

/**
 * Drop-in replacement for using [BlurView] directly. Wraps it and layers InstallerX-Revived's
 * glass "recipe" on top, without touching how the backdrop is actually captured and blurred
 * (that part still comes from [BlurView] itself, so blur keeps working all the way down to
 * minSdk 24):
 *
 *  - a multi-entry, blend-mode-aware tint (see [GlassTintEntry]) instead of one flat alpha
 *    overlay colour — ported from InstallerX's `BlurColors`/`BlendColorEntry` idea, using
 *    android.graphics.BlendMode where available (API 29+);
 *  - on API 31+ (S), an optional vibrancy (saturation) boost, applied as a RenderEffect;
 *  - on API 33+ (Tiramisu), the rounded-rect refraction "lens" shader from
 *    [GLASS_REFRACTION_SHADER] / [GLASS_REFRACTION_DISPERSION_SHADER], adapted from
 *    InstallerX's `ui/library/liquid/Lens.kt` (itself from Kyant0/AndroidLiquidGlass,
 *    Apache-2.0). This is the actual "glass edge" look and only makes visual sense on a
 *    bounded, rounded shape (e.g. the bottom status pill) — there's no edge to refract
 *    against on a full-screen backdrop, so callers doing full-window blur should simply
 *    leave lens refraction off.
 *
 * Three effective tiers fall out of this naturally, matching how far each API lets us go:
 *  - API 24–30 (legacy): [BlurView]'s own blur + tint only (PorterDuff fallback for exotic
 *    blend modes, since android.graphics.BlendMode needs API 29).
 *  - API 31–32 (S/S_V2): blur + full blend-mode tint + vibrancy (RenderEffect exists, but
 *    RuntimeShader — needed for the lens — doesn't yet).
 *  - API 33+ (Tiramisu+): all of the above + the lens refraction shader.
 */
class GlassBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** The real blur engine. Exposed so existing `setBlurRadius`/`setBlurRounds` call sites
     *  barely need to change beyond swapping the type they construct. */
    val blurView: BlurView = BlurView(context, attrs)

    private var tintEntries: List<GlassTintEntry> = emptyList()
    private var vibrancySaturation: Float = 1f
    private var lensEnabled: Boolean = false
    private var refractionHeightPx: Float = 0f
    private var refractionAmountPx: Float = 0f
    private var chromaticAberration: Float = 0f
    private var depthEffect: Boolean = false
    private var cornerRadiusPx: Float = 0f

    private val tintClipPath = Path()
    private val tintClipRect = RectF()

    init {
        addView(blurView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Tinting is now entirely our responsibility (see setGlassTint below); make sure
        // the wrapped BlurView doesn't also draw its own flat overlay on top of ours.
        blurView.setOverlayColor(android.graphics.Color.TRANSPARENT)
        setWillNotDraw(false)
    }

    fun setBlurRadius(radius: Float) {
        blurView.setBlurRadius(radius)
    }

    fun setBlurRounds(rounds: Int) {
        blurView.setBlurRounds(rounds)
    }

    /** Corner radius in pixels, used both to clip the tint layer and as the lens shader's
     *  rounded-rect radius. Callers that already set a corner radius via `app:cornerRadius`
     *  in XML (read directly by the wrapped [BlurView]) should still call this too, so the
     *  tint/lens match the visible shape. */
    fun setGlassCornerRadiusPx(px: Float) {
        if (cornerRadiusPx == px) return
        cornerRadiusPx = px
        rebuildClipPath()
        rebuildRenderEffect()
        invalidate()
    }

    fun setGlassTint(entries: List<GlassTintEntry>) {
        tintEntries = entries
        invalidate()
    }

    /** 1f = unchanged. Values above 1 look more vivid, below 1 more muted. Needs API 31+;
     *  a no-op elsewhere. */
    fun setVibrancy(saturation: Float) {
        if (vibrancySaturation == saturation) return
        vibrancySaturation = saturation
        rebuildRenderEffect()
    }

    /**
     * The InstallerX "glass edge". Needs API 33+ (RuntimeShader); silently does nothing on
     * older devices, so it's always safe to call.
     *
     * @param refractionHeightPx how far in from the edge the bending band extends.
     * @param refractionAmountPx how strongly light bends within that band.
     * @param chromaticAberration 0 disables the rainbow-fringe dispersion pass (cheaper);
     *   ~0.2–0.5 gives a subtle-to-pronounced fringe, matching InstallerX's own usage.
     */
    fun setLensRefraction(
        enabled: Boolean,
        refractionHeightPx: Float = 24f,
        refractionAmountPx: Float = 24f,
        chromaticAberration: Float = 0f,
        depthEffect: Boolean = false,
    ) {
        this.lensEnabled = enabled
        this.refractionHeightPx = refractionHeightPx
        this.refractionAmountPx = refractionAmountPx
        this.chromaticAberration = chromaticAberration
        this.depthEffect = depthEffect
        rebuildRenderEffect()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildClipPath()
        rebuildRenderEffect()
    }

    private fun rebuildClipPath() {
        tintClipPath.reset()
        tintClipRect.set(0f, 0f, width.toFloat(), height.toFloat())
        val r = cornerRadiusPx.coerceAtLeast(0f)
        if (r > 0f) {
            tintClipPath.addRoundRect(tintClipRect, r, r, Path.Direction.CW)
        } else {
            tintClipPath.addRect(tintClipRect, Path.Direction.CW)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (tintEntries.isEmpty() || width <= 0 || height <= 0) return

        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        try {
            canvas.clipPath(tintClipPath)
            for (entry in tintEntries) {
                canvas.drawRect(tintClipRect, GlassPaints.tintPaint(entry))
            }
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    private fun rebuildRenderEffect() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return // RenderEffect needs API 31+
        if (width <= 0 || height <= 0) return

        val lens = if (lensEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            buildLensEffect()
        } else {
            null
        }

        val effect = when {
            lens != null && vibrancySaturation != 1f ->
                RenderEffect.createColorFilterEffect(saturationFilter(), lens)
            lens != null -> lens
            vibrancySaturation != 1f -> RenderEffect.createColorFilterEffect(saturationFilter())
            else -> null
        }
        setRenderEffect(effect)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun buildLensEffect(): RenderEffect? {
        if (refractionHeightPx <= 0f || refractionAmountPx <= 0f) return null

        val dispersionEnabled = chromaticAberration > 0f
        val shader = RuntimeShader(
            if (dispersionEnabled) GLASS_REFRACTION_DISPERSION_SHADER else GLASS_REFRACTION_SHADER
        )
        shader.setFloatUniform("size", width.toFloat(), height.toFloat())
        shader.setFloatUniform("offset", 0f, 0f)
        shader.setFloatUniform("cornerRadii", cornerRadiusPx, cornerRadiusPx, cornerRadiusPx, cornerRadiusPx)
        shader.setFloatUniform("refractionHeight", refractionHeightPx)
        shader.setFloatUniform("refractionAmount", -refractionAmountPx)
        shader.setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (dispersionEnabled) {
            shader.setFloatUniform("chromaticAberration", chromaticAberration)
        }

        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }

    private fun saturationFilter(): ColorMatrixColorFilter {
        val matrix = ColorMatrix()
        matrix.setSaturation(vibrancySaturation)
        return ColorMatrixColorFilter(matrix)
    }
}
