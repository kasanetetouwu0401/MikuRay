package com.v2ray.ang.ui.preference

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.util.getColorAttr

/**
 * Scrolls to the preference identified by [key] (if present in this fragment's
 * screen) and briefly flashes its row background to draw attention to it.
 *
 * Intended to be called from a settings Activity's onCreate/onResume after the
 * preference screen has finished its initial layout, typically in response to
 * the user tapping a result in [com.v2ray.ang.ui.preference.activity.PreferenceSearchActivity].
 */
fun PreferenceFragmentCompat.scrollToAndHighlight(key: String?) {
    if (key.isNullOrBlank()) return

    val recyclerView = listView ?: return

    // Wait for the screen's own layout pass before we try to resolve the
    // preference's adapter position, otherwise the adapter may not be ready yet.
    recyclerView.post {
        val position = (recyclerView.adapter as? PreferenceGroupAdapter)
            ?.getPreferenceAdapterPosition(key)
            ?: RecyclerView.NO_POSITION

        if (position == RecyclerView.NO_POSITION) return@post

        scrollToPreference(key)

        // The smooth scroll triggered above is async and its duration depends on
        // distance, so instead of guessing a fixed delay we poll for the row to
        // become attached/bound, then highlight it. This also handles the case
        // where the row was already visible (no scroll needed at all).
        awaitViewHolderAndHighlight(recyclerView, position, attemptsLeft = 15)
    }
}

private fun awaitViewHolderAndHighlight(
    recyclerView: RecyclerView,
    position: Int,
    attemptsLeft: Int
) {
    val holder = recyclerView.findViewHolderForAdapterPosition(position)
    if (holder != null) {
        highlightRow(holder.itemView)
        return
    }
    if (attemptsLeft <= 0) return
    recyclerView.postDelayed({
        awaitViewHolderAndHighlight(recyclerView, position, attemptsLeft - 1)
    }, 80L)
}

/**
 * Flashes the given row's background to draw the eye to it. Most leaf
 * preferences use the plain androidx Preference layout (no [MaterialCardView]),
 * so this prefers an existing card if present but otherwise highlights the
 * row's own background directly so the effect always shows.
 */
private fun highlightRow(itemView: View) {
    val cardView = findCardView(itemView)
    if (cardView != null) {
        flashCardHighlight(cardView)
    } else {
        flashViewHighlight(itemView)
    }
}

private fun findCardView(view: View?): MaterialCardView? {
    return when (view) {
        null -> null
        is MaterialCardView -> view
        is ViewGroup -> {
            for (i in 0 until view.childCount) {
                val found = findCardView(view.getChildAt(i))
                if (found != null) return found
            }
            null
        }
        else -> null
    }
}

private fun flashCardHighlight(cardView: MaterialCardView) {
    val context = cardView.context
    val normalColor = context.getColorAttr("colorCard")
    val highlightColor = context.getColorAttr("colorTertiaryContainer")

    ValueAnimator.ofObject(ArgbEvaluator(), normalColor, highlightColor, normalColor).apply {
        duration = 900L
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            cardView.setCardBackgroundColor(animation.animatedValue as Int)
        }
    }.start()
}

/**
 * Fallback used when the row has no [MaterialCardView] wrapper: temporarily
 * overlays the row's background with a color animation, then restores
 * whatever background drawable it originally had.
 */
private fun flashViewHighlight(itemView: View) {
    val context = itemView.context
    val originalBackground: Drawable? = itemView.background
    val baseColor = context.getColorAttr("colorBg")
    val highlightColor = context.getColorAttr("colorTertiaryContainer")

    val overlay = ColorDrawable(baseColor)
    itemView.background = overlay

    ValueAnimator.ofObject(ArgbEvaluator(), baseColor, highlightColor, baseColor).apply {
        duration = 900L
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            overlay.color = animation.animatedValue as Int
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                itemView.background = originalBackground
            }
        })
    }.start()
}
