package com.v2ray.ang.ui.preference

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.LinearSmoothScroller // Tambahan import
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.R
import com.v2ray.ang.util.getColorAttr

fun PreferenceFragmentCompat.scrollToAndHighlight(key: String?) {
    if (key.isNullOrBlank()) return

    // 1. Collapse toolbar terlebih dahulu
    activity?.findViewById<AppBarLayout>(R.id.app_bar)?.setExpanded(false, true)

    val recyclerView = listView ?: return
    recyclerView.post {
        doScrollAndHighlight(recyclerView, key)
    }
}

private fun PreferenceFragmentCompat.doScrollAndHighlight(recyclerView: RecyclerView, key: String) {
    val adapter = recyclerView.adapter as? PreferenceGroupAdapter ?: run {
        recyclerView.postDelayed({ doScrollAndHighlight(recyclerView, key) }, 100L)
        return
    }

    val position = adapter.getPreferenceAdapterPosition(key)
    if (position == RecyclerView.NO_POSITION) return

    // 2. Ganti scrollToPreference(key) dengan custom SmoothScroller
    val layoutManager = recyclerView.layoutManager
    if (layoutManager != null) {
        val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun calculateDtToFit(
                viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int
            ): Int {
                // Kalkulasi titik tengah RecyclerView (box) dan titik tengah Item (view)
                val boxCenter = boxStart + (boxEnd - boxStart) / 2
                val viewCenter = viewStart + (viewEnd - viewStart) / 2
                
                // Kembalikan selisih jaraknya agar item berada persis di tengah
                return boxCenter - viewCenter
            }
        }
        smoothScroller.targetPosition = position
        layoutManager.startSmoothScroll(smoothScroller)
    }

    // 3. Tambahkan sedikit delay dari 350L menjadi 450L 
    // karena smooth scroll butuh waktu tambahan agar item benar-benar ter-render di tengah
    recyclerView.postDelayed({
        val holder = recyclerView.findViewHolderForAdapterPosition(position)
        val cardView = findCardView(holder?.itemView) ?: return@postDelayed
        flashHighlight(cardView)
    }, 450L) 
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
