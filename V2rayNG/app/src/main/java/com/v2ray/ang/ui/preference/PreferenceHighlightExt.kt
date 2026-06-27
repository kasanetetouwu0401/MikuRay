package com.v2ray.ang.ui.preference

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.util.DisplayMetrics // Tambahan import untuk kalkulasi speed
import android.view.animation.LinearInterpolator
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import com.v2ray.ang.R
import com.v2ray.ang.util.getColorAttr

fun PreferenceFragmentCompat.scrollToAndHighlight(key: String?) {
    if (key.isNullOrBlank()) return

    val appBar = activity?.findViewById<AppBarLayout>(R.id.app_bar)
    // 1. Collapse toolbar terlebih dahulu
    appBar?.setExpanded(false, true)

    val recyclerView = listView ?: return
    
    // Trik 1: Beri jeda waktu agar animasi collapse AppBar jalan duluan
    // Ini mencegah tabrakan render yang bikin transisi terasa kasar
    val delayCollapse = if (appBar != null) 250L else 0L
    
    recyclerView.postDelayed({
        doScrollAndHighlight(recyclerView, key)
    }, delayCollapse)
}

private fun PreferenceFragmentCompat.doScrollAndHighlight(recyclerView: RecyclerView, key: String) {
    val adapter = recyclerView.adapter as? PreferenceGroupAdapter ?: run {
        recyclerView.postDelayed({ doScrollAndHighlight(recyclerView, key) }, 100L)
        return
    }

    val position = adapter.getPreferenceAdapterPosition(key)
    if (position == RecyclerView.NO_POSITION) return

    val layoutManager = recyclerView.layoutManager
    if (layoutManager != null) {
        val smoothScroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun calculateDtToFit(
                viewStart: Int, viewEnd: Int, boxStart: Int, boxEnd: Int, snapPreference: Int
            ): Int {
                val boxCenter = boxStart + (boxEnd - boxStart) / 2
                val viewCenter = viewStart + (viewEnd - viewStart) / 2
                return boxCenter - viewCenter
            }

            // Trik 2: Perlambat sedikit scroll-nya agar terasa lebih mulus
            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                // Semakin besar angkanya, semakin lambat animasinya. 
                // Angka 75f biasanya pas untuk estetika Material. (Default bawaan sekitar 25f).
                return 75f / displayMetrics.densityDpi 
            }
        }
        smoothScroller.targetPosition = position
        layoutManager.startSmoothScroll(smoothScroller)
    }

    // Trik 3: Tambahkan delay lebih panjang karena scroll-nya sekarang lebih lambat
    // Naik dari 450L menjadi 650L agar aman
    recyclerView.postDelayed({
        val holder = recyclerView.findViewHolderForAdapterPosition(position)
        val cardView = findCardView(holder?.itemView) ?: return@postDelayed
        flashHighlight(cardView)
    }, 650L) 
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
