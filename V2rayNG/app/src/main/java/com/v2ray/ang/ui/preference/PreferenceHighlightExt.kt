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
 * Intended to be called from a settings Activity (ideally from onResume, after
 * the fragment transaction has been committed) in response to the user tapping
 * a result in [com.v2ray.ang.ui.preference.activity.PreferenceSearchActivity].
 *
 * Resolving the preference's adapter position can race with the fragment's own
 * setup (addPreferencesFromResource, CategoryStyleHelper restyling, etc), so this
 * retries for a short window rather than giving up after a single failed attempt.
 */
fun PreferenceFragmentCompat.scrollToAndHighlight(key: String?) {
    if (key.isNullOrBlank()) return
    attemptScrollToAndHighlight(key, attemptsLeft = 20)
}

private fun PreferenceFragmentCompat.attemptScrollToAndHighlight(key: String, attemptsLeft: Int) {
    val recyclerView = listView
    if (recyclerView == null) {
        if (attemptsLeft <= 0) return
        view?.postDelayed({ attemptScrollToAndHighlight(key, attemptsLeft - 1) }, 50L)
        return
    }

    val position = (recyclerView.adapter as? PreferenceGroupAdapter)
        ?.getPreferenceAdapterPosition(key)
        ?: RecyclerView.NO_POSITION

    if (position == RecyclerView.NO_POSITION) {
        if (attemptsLeft <= 0) return
        recyclerView.postDelayed({ attemptScrollToAndHighlight(key, attemptsLeft - 1) }, 50L)
        return
    }

    scrollToPreference(key)

    // Give the smooth scroll a moment to settle before grabbing the view holder,
    // otherwise the target row may not be bound/attached yet.
    waitForViewHolderAndHighlight(recyclerView, position, attemptsLeft = 12)
}

private fun waitForViewHolderAndHighlight(recyclerView: RecyclerView, position: Int, attemptsLeft: Int) {
    val holder = recyclerView.findViewHolderForAdapterPosition(position)
    val cardView = findCardView(holder?.itemView)

    if (cardView == null) {
        if (attemptsLeft <= 0) return
        recyclerView.postDelayed({ waitForViewHolderAndHighlight(recyclerView, position, attemptsLeft - 1) }, 50L)
        return
    }

    flashHighlight(cardView)
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
