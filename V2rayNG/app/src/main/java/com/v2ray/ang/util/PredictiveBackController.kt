package com.v2ray.ang.util

import android.app.Activity
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.LifecycleOwner
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Drives the predictive back gesture visuals for an Activity's content root, matching
 * whichever [PredictiveBackAnimation] style is selected in Settings.
 *
 * This transforms the whole content root under android.R.id.content (toolbar + body move
 * together as one unit), so it works the same way regardless of which layout/toolbar setup
 * a given activity uses. Follow-through on release (cancel or commit) is spring-driven rather
 * than a fixed-duration tween, so it inherits the finger's velocity instead of snapping into a
 * canned animation - closer to how the gesture feels in InstallerX-Revived's Compose-based
 * predictive back transitions, even though the two implementations don't share any code.
 */
class PredictiveBackController(private val activity: Activity) {

    private var root: View? = null
    private var swipeFromLeft = true
    private var initialTouchY = 0f
    private var cornerRadiusPx = 0f
    private var committing = false

    private var lastProgress = 0f
    private var lastProgressTimeNanos = 0L
    private var progressVelocity = 0f // progress units (0..1) per second

    private val activeSprings = mutableListOf<SpringAnimation>()

    private val density get() = activity.resources.displayMetrics.density

    private val outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
        }
    }

    private val cornerRadiusProperty = object : FloatPropertyCompat<PredictiveBackController>("cornerRadius") {
        override fun getValue(controller: PredictiveBackController) = controller.cornerRadiusPx
        override fun setValue(controller: PredictiveBackController, value: Float) {
            controller.cornerRadiusPx = value
            controller.root?.invalidateOutline()
        }
    }

    fun attach(lifecycleOwner: LifecycleOwner) {
        val callback = object : OnBackPressedCallback(true) {

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                cancelSprings()
                committing = false
                val style = currentStyle()
                if (style == PredictiveBackAnimation.NONE) return

                val target = resolveContentRoot() ?: return
                root = target
                swipeFromLeft = backEvent.swipeEdge == BackEventCompat.EDGE_LEFT
                initialTouchY = backEvent.touchY
                lastProgress = 0f
                lastProgressTimeNanos = System.nanoTime()
                progressVelocity = 0f

                target.pivotX = if (swipeFromLeft) 0f else target.width.toFloat()
                target.pivotY = target.height / 2f

                if (style.usesRoundedCorners) {
                    target.clipToOutline = true
                    target.outlineProvider = outlineProvider
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                val style = currentStyle()
                if (style == PredictiveBackAnimation.NONE) return
                val target = root ?: resolveContentRoot()?.also { root = it } ?: return
                trackVelocity(backEvent.progress)
                applyProgress(target, style, backEvent.progress, backEvent.touchY)
            }

            override fun handleOnBackCancelled() {
                val style = currentStyle()
                if (style == PredictiveBackAnimation.NONE) return
                root?.let { settleBack(it, style) }
            }

            override fun handleOnBackPressed() {
                val style = currentStyle()
                val target = root ?: resolveContentRoot()
                if (style == PredictiveBackAnimation.NONE || target == null || committing) {
                    finishNow()
                    return
                }
                commitClose(target, style)
            }
        }
        activity.onBackPressedDispatcher.addCallback(lifecycleOwner, callback)
    }

    private fun currentStyle(): PredictiveBackAnimation =
        PredictiveBackAnimation.fromValue(
            MmkvManager.decodeSettingsString(AppConfig.PREF_PREDICTIVE_BACK_ANIMATION)
        )

    private fun resolveContentRoot(): View? {
        val contentFrame = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return if (contentFrame.childCount > 0) contentFrame.getChildAt(0) else null
    }

    private fun trackVelocity(rawProgress: Float) {
        val progress = rawProgress.coerceIn(0f, 1f)
        val now = System.nanoTime()
        val dt = (now - lastProgressTimeNanos) / 1_000_000_000f
        if (dt > 0.001f) {
            progressVelocity = (progress - lastProgress) / dt
        }
        lastProgress = progress
        lastProgressTimeNanos = now
    }

    private fun applyProgress(target: View, style: PredictiveBackAnimation, rawProgress: Float, touchY: Float) {
        val progress = rawProgress.coerceIn(0f, 1f)
        val edgeSign = if (swipeFromLeft) 1f else -1f
        val eased = EASING.getInterpolation(progress)

        val scaleMin = style.scaleMin
        val scale = 1f - (1f - scaleMin) * eased
        target.scaleX = scale
        target.scaleY = scale

        val driftPx = style.maxDriftDp * density
        val slidePx = if (style == PredictiveBackAnimation.CLASSIC) {
            edgeSign * progress * target.width
        } else {
            edgeSign * eased * driftPx
        }
        target.translationX = slidePx

        if (style.verticalFollowDp > 0f) {
            val heightHalf = max(1f, target.height / 2f)
            val rawDelta = touchY - initialTouchY
            val ratio = min(1f, abs(rawDelta) / heightHalf)
            val maxShiftPx = style.verticalFollowDp * density
            target.translationY = maxShiftPx * ratio * (if (rawDelta < 0f) -1f else 1f) * eased
        }

        if (style.usesRoundedCorners) {
            cornerRadiusPx = style.cornerRadiusDp * density * eased
            target.invalidateOutline()
        }

        target.alpha = 1f - style.alphaDrop * eased
    }

    /** Springs the content root back to its resting state, inheriting the gesture's velocity. */
    private fun settleBack(target: View, style: PredictiveBackAnimation) {
        cancelSprings()
        val tuning = style.cancelSpring
        val driftPx = style.maxDriftDp * density
        val cornerTargetPx = style.cornerRadiusDp * density

        val scaleXVelocity = -(1f - style.scaleMin) * progressVelocity
        val translationXVelocity = if (style == PredictiveBackAnimation.CLASSIC) {
            (if (swipeFromLeft) 1f else -1f) * target.width * progressVelocity
        } else {
            (if (swipeFromLeft) 1f else -1f) * driftPx * progressVelocity
        }
        val alphaVelocity = -style.alphaDrop * progressVelocity
        val cornerVelocity = cornerTargetPx * progressVelocity

        activeSprings += spring(target, DynamicAnimation.SCALE_X, 1f, tuning, scaleXVelocity)
        activeSprings += spring(target, DynamicAnimation.SCALE_Y, 1f, tuning, scaleXVelocity)
        activeSprings += spring(target, DynamicAnimation.TRANSLATION_X, 0f, tuning, translationXVelocity)
        activeSprings += spring(target, DynamicAnimation.TRANSLATION_Y, 0f, tuning, 0f)
        activeSprings += spring(target, DynamicAnimation.ALPHA, 1f, tuning, alphaVelocity)

        if (style.usesRoundedCorners) {
            val cornerSpring = SpringAnimation(this, cornerRadiusProperty, 0f).apply {
                spring = SpringForce(0f).apply {
                    dampingRatio = tuning.dampingRatio
                    stiffness = tuning.stiffness
                }
                setStartVelocity(cornerVelocity)
                addEndListener { _, canceled, _, _ ->
                    if (!canceled) target.clipToOutline = false
                }
            }
            activeSprings += cornerSpring
        }

        activeSprings.forEach { it.start() }
    }

    /** Springs the content root the rest of the way closed, then finishes the activity. */
    private fun commitClose(target: View, style: PredictiveBackAnimation) {
        committing = true
        cancelSprings()
        val tuning = style.commitSpring
        val edgeSign = if (swipeFromLeft) 1f else -1f

        if (style == PredictiveBackAnimation.CLASSIC) {
            val targetX = edgeSign * target.width.toFloat()
            val translationXSpring = spring(
                target,
                DynamicAnimation.TRANSLATION_X,
                targetX,
                tuning,
                progressVelocity * target.width * edgeSign
            ).apply {
                addEndListener { _, canceled, _, _ -> if (!canceled) finishNow() }
            }
            activeSprings += translationXSpring
            translationXSpring.start()
            return
        }

        val toScale = (style.scaleMin - tuning.extraScaleDrop).coerceAtLeast(0.5f)
        val toTranslationX = edgeSign * style.maxDriftDp * density * 1.6f
        val scaleXVelocity = -(1f - style.scaleMin) * progressVelocity

        val scaleXSpring = spring(target, DynamicAnimation.SCALE_X, toScale, tuning, scaleXVelocity)
        val scaleYSpring = spring(target, DynamicAnimation.SCALE_Y, toScale, tuning, scaleXVelocity)
        val translationXSpring = spring(
            target,
            DynamicAnimation.TRANSLATION_X,
            toTranslationX,
            tuning,
            edgeSign * style.maxDriftDp * density * progressVelocity
        ).apply {
            addEndListener { _, canceled, _, _ -> if (!canceled) finishNow() }
        }
        val alphaSpring = spring(target, DynamicAnimation.ALPHA, 0f, tuning, -style.alphaDrop * progressVelocity)

        activeSprings += listOf(scaleXSpring, scaleYSpring, translationXSpring, alphaSpring)

        if (style.usesRoundedCorners) {
            val cornerTargetPx = style.cornerRadiusDp * density
            val cornerSpring = SpringAnimation(this, cornerRadiusProperty, cornerTargetPx).apply {
                spring = SpringForce(cornerTargetPx).apply {
                    dampingRatio = tuning.dampingRatio
                    stiffness = tuning.stiffness
                }
                setStartVelocity(cornerTargetPx * progressVelocity)
            }
            activeSprings += cornerSpring
        }

        activeSprings.forEach { it.start() }
    }

    private fun spring(
        view: View,
        property: DynamicAnimation.ViewProperty,
        target: Float,
        tuning: SpringTuning,
        startVelocity: Float,
    ): SpringAnimation = SpringAnimation(view, property, target).apply {
        spring = SpringForce(target).apply {
            dampingRatio = tuning.dampingRatio
            stiffness = tuning.stiffness
        }
        setStartVelocity(startVelocity)
    }

    private fun cancelSprings() {
        activeSprings.forEach { it.cancel() }
        activeSprings.clear()
    }

    private fun finishNow() {
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }

    companion object {
        private val EASING = android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    }
}

/** Spring tuning for a settle phase: higher stiffness snaps faster, lower dampingRatio bounces more. */
private data class SpringTuning(
    val stiffness: Float,
    val dampingRatio: Float,
    val extraScaleDrop: Float = 0f,
)

private val PredictiveBackAnimation.usesRoundedCorners: Boolean
    get() = this == PredictiveBackAnimation.AOSP ||
        this == PredictiveBackAnimation.SCALE

private val PredictiveBackAnimation.scaleMin: Float
    get() = when (this) {
        PredictiveBackAnimation.AOSP -> 0.90f
        PredictiveBackAnimation.SCALE -> 0.80f
        PredictiveBackAnimation.CLASSIC, PredictiveBackAnimation.NONE -> 1f
    }

private val PredictiveBackAnimation.maxDriftDp: Float
    get() = when (this) {
        PredictiveBackAnimation.AOSP -> 8f
        PredictiveBackAnimation.SCALE -> 48f
        PredictiveBackAnimation.CLASSIC -> 0f // unused, classic uses 1:1 slide instead
        PredictiveBackAnimation.NONE -> 0f
    }

private val PredictiveBackAnimation.verticalFollowDp: Float
    get() = when (this) {
        PredictiveBackAnimation.AOSP -> 4f
        PredictiveBackAnimation.SCALE -> 24f
        else -> 0f
    }

private val PredictiveBackAnimation.cornerRadiusDp: Float
    get() = when (this) {
        PredictiveBackAnimation.AOSP -> 32f
        PredictiveBackAnimation.SCALE -> 28f
        else -> 0f
    }

private val PredictiveBackAnimation.alphaDrop: Float
    get() = 0f

/** No-bounce, snap-to-place spring used when the gesture is cancelled (finger released, no commit). */
private val PredictiveBackAnimation.cancelSpring: SpringTuning
    get() = when (this) {
        PredictiveBackAnimation.AOSP -> SpringTuning(stiffness = 800f, dampingRatio = 0.9f)
        PredictiveBackAnimation.SCALE -> SpringTuning(stiffness = 900f, dampingRatio = 0.85f)
        PredictiveBackAnimation.CLASSIC -> SpringTuning(stiffness = 1000f, dampingRatio = 1f)
        PredictiveBackAnimation.NONE -> SpringTuning(stiffness = 1000f, dampingRatio = 1f)
    }

/** Spring used when the gesture commits (finger released past the threshold, activity closes). */
private val PredictiveBackAnimation.commitSpring: SpringTuning
    get() = when (this) {
        PredictiveBackAnimation.AOSP -> SpringTuning(stiffness = 1200f, dampingRatio = 0.7f, extraScaleDrop = 0.05f)
        PredictiveBackAnimation.SCALE -> SpringTuning(stiffness = 1400f, dampingRatio = 0.55f, extraScaleDrop = 0.10f)
        PredictiveBackAnimation.CLASSIC -> SpringTuning(stiffness = 1000f, dampingRatio = 1f)
        PredictiveBackAnimation.NONE -> SpringTuning(stiffness = 1000f, dampingRatio = 1f)
    }
