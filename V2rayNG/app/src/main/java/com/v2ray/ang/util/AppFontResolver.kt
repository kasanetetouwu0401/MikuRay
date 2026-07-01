package com.v2ray.ang.util

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.v2ray.ang.R

/**
 * Maps a bundled font's preference value (e.g. "google", "roboto") to its @font resource and
 * loaded [Typeface]. Shared between [com.v2ray.ang.ui.BaseActivity] (which applies the font
 * app-wide via a theme overlay) and the font picker UI (which needs to render each option's
 * label in that option's own font).
 */
object AppFontResolver {

    private fun fontResId(value: String?): Int = when (value) {
        "google"        -> R.font.googlesansregular
        "roboto"        -> R.font.robotoregular
        "poppins"       -> R.font.poppinsregular
        "chococooky"    -> R.font.chococookyregular
        "simpleday"     -> R.font.simpleday
        "fucek"         -> R.font.fucek
        "sfprodisplay"  -> R.font.sfprodisplay
        "dancingscript" -> R.font.dancingscript
        "cream"         -> R.font.cream
        "oneui"         -> R.font.oneui
        "inconsolata"   -> R.font.incosolata
        "emilyscandy"   -> R.font.emilyscandy
        "summerdream"   -> R.font.summerdream
        "rine"          -> R.font.rine
        "evolve"        -> R.font.evolvesans
        else            -> 0
    }

    /** Loads the [Typeface] for a bundled font value, or [Typeface.DEFAULT] for "default"/unknown/null. */
    fun getTypeface(context: Context, value: String?): Typeface {
        val resId = fontResId(value)
        if (resId == 0) return Typeface.DEFAULT
        return try {
            ResourcesCompat.getFont(context, resId) ?: Typeface.DEFAULT
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }
}
