package com.v2ray.ang.ui.debug

import android.app.Activity
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.haozhang.lib.SlantedTextView
import com.v2ray.ang.util.getColorAttr
import com.v2ray.ang.R

object DebugBadgeController {

    private const val BADGE_TAG = "debug_badge_slanted_tv"
    private const val BADGE_TEXT = "DEBUG"
    private const val BADGE_SIZE_DP = 64

    fun attach(activity: Activity, root: ViewGroup) {
        if (root !is RelativeLayout) return
        if (root.findViewWithTag<SlantedTextView>(BADGE_TAG) != null) return

        val density = activity.resources.displayMetrics.density
        val sizePx = (BADGE_SIZE_DP * density).toInt()

        val colorPrimary = activity.getColorAttr("colorPrimary")
        val colorOnPrimary = activity.getColorAttr("colorOnPrimary")

        val badge = SlantedTextView(activity).apply {
            tag = BADGE_TAG
            setText(BADGE_TEXT)
            setTextColor(colorOnPrimary)
            setSlantedBackgroundColor(colorPrimary)
            setSlantedLength((40 * density).toInt()) 
            setMode(SlantedTextView.MODE_RIGHT)
            setTextSize(16f)
        }

        val params = RelativeLayout.LayoutParams(sizePx, sizePx).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            addRule(RelativeLayout.ALIGN_PARENT_END)
        }

        root.addView(badge, params)
        badge.bringToFront()
    }
}
