package com.v2ray.ang.ui.preference

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.util.getColorAttr

/**
 * Scrolls to the preference identified by [key] (if present in this fragment's
 * screen) and briefly flashes its card background to draw attention to it.
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

        // Give the smooth scroll a moment to settle before grabbing the view holder,
        // otherwise the target row may not be bound/attached yet.
        recyclerView.postDelayed({
            val holder = recyclerView.findViewHolderForAdapterPosition(position)
            val cardView = findCardView(holder?.itemView) ?: return@postDelayed
            flashHighlight(cardView)
        }, 260L)
    }
}

private fun findCardView(view: android.view.View?): MaterialCardView? {
    return when (view) {
        null -> null
        is MaterialCardView -> view
        is android.view.ViewGroup -> {
            for (i in 0 until view.childCount) {
                val found = findCardView(view.getChildAt(i))
                if (found != null) return found
            }
            null
        }
        else -> null
    }
}

private fun flashHighlight(cardView: MaterialCardView) {
    val context = cardView.context
    val normalColor = context.getColorAttr("colorCard")
    val highlightColor = context.getColorAttr("colorTertiaryContainer")

    val animator = ValueAnimator.ofObject(ArgbEvaluator(), normalColor, highlightColor, normalColor).apply {
        duration = 900L
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            cardView.setCardBackgroundColor(animation.animatedValue as Int)
        }
    }
    animator.start()
}
