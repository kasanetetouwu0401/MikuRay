package com.v2ray.ang.util

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider

/**
 * Clips this view (and all its children) so only the TOP-left/TOP-right
 * corners appear rounded, like a bottom-sheet docked under a header.
 *
 * Trick: Outline.setRoundRect only supports a uniform radius, so we extend
 * the outline rect's bottom edge by [radiusDp] past the view's actual
 * height. The bottom rounded curve then falls outside the visible bounds,
 * leaving only the top corners visibly rounded while the bottom stays
 * sharp (flush with the screen edge).
 */
fun View.roundTopCorners(radiusDp: Float) {
    val radiusPx = radiusDp * resources.displayMetrics.density
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            if (view.width == 0 || view.height == 0) return
            outline.setRoundRect(
                0,
                0,
                view.width,
                (view.height + radiusPx).toInt(),
                radiusPx
            )
        }
    }
    clipToOutline = true
}
