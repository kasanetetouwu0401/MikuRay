package com.v2ray.ang.ui.preference

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout // Tambahkan import AppBarLayout
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.R // Tambahkan import R untuk mengakses R.id.app_bar
import com.v2ray.ang.util.getColorAttr

/**
 * Scrolls to the preference identified by [key] (if present in this fragment's
 * screen) and briefly flashes its card background to draw attention to it.
 *
 * Intended to be called from a settings fragment's onStart(), after preference
 * screen inflation is guaranteed complete.
 */
fun PreferenceFragmentCompat.scrollToAndHighlight(key: String?) {
    if (key.isNullOrBlank()) return

    // 1. Instruksikan AppBarLayout untuk collapse (mengecil) secara terprogram
    // Parameter (false, true) = expanded diset ke false, dengan animasi true
    activity?.findViewById<AppBarLayout>(R.id.app_bar)?.setExpanded(false, true)

    val recyclerView = listView ?: return

    // Use ViewTreeObserver to wait until the RecyclerView has actually laid out
    // its children before trying to resolve adapter positions.
    recyclerView.post {
        doScrollAndHighlight(recyclerView, key)
    }
}

private fun PreferenceFragmentCompat.doScrollAndHighlight(recyclerView: RecyclerView, key: String) {
    val adapter = recyclerView.adapter as? PreferenceGroupAdapter ?: run {
        // Adapter not ready yet — retry after next layout pass
        recyclerView.postDelayed({ doScrollAndHighlight(recyclerView, key) }, 100L)
        return
    }

    val position = adapter.getPreferenceAdapterPosition(key)
    if (position == RecyclerView.NO_POSITION) return

    scrollToPreference(key)

    // Wait for smooth scroll to settle, then flash the card
    recyclerView.postDelayed({
        val holder = recyclerView.findViewHolderForAdapterPosition(position)
        val cardView = findCardView(holder?.itemView) ?: return@postDelayed
        flashHighlight(cardView)
    }, 350L)
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
