package com.v2ray.ang.util

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.v2ray.ang.R

/**
 * Picks a vivid background color for an icon from the app's existing theme palette
 * (the same `*_primary` colors used by [ThemeManager]'s 16 color themes), based on a
 * stable identity (e.g. an icon drawable's constant state hash). Same identity always
 * yields the same color, so colors stay consistent across app restarts (a la Telegram's
 * settings icon colors). Light/dark variants are resolved automatically since each color
 * resource has a `values-night` counterpart.
 */
object RandomIconColor {

    private val PALETTE = intArrayOf(
        R.color.red_primary,
        R.color.pink_primary,
        R.color.purple_primary,
        R.color.deepPurple_primary,
        R.color.indigo_primary,
        R.color.blue_primary,
        R.color.cyan_primary,
        R.color.teal_primary,
        R.color.green_primary,
        R.color.lightGreen_primary,
        R.color.lime_primary,
        R.color.yellow_primary,
        R.color.amber_primary,
        R.color.orange_primary,
        R.color.brown_primary,
        R.color.blueGrey_primary
    )

    @ColorInt
    fun forIdentity(context: Context, identity: Int): Int {
        val index = Math.floorMod(identity, PALETTE.size)
        return ContextCompat.getColor(context, PALETTE[index])
    }
}
