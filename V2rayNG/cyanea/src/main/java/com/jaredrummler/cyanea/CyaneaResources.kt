/*
 * Copyright (C) 2018 Jared Rummler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jaredrummler.cyanea

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
import com.jaredrummler.cyanea.tinting.CyaneaTinter.CyaneaTintException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Resources to get custom colors from [Cyanea]
 */
@Suppress("DEPRECATION", "OverridingDeprecatedMember")
class CyaneaResources(original: Resources, private val cyanea: Cyanea = Cyanea.instance) :
  Resources(original.assets, original.displayMetrics, original.configuration) {

  init {
    cyanea.tinter.setup(original, this)
  }

  /* Track resources so we don't attempt to modify the Drawable or ColorStateList more than once */
  private val tintTracker = TintTracker()

  @Throws(Resources.NotFoundException::class)
  override fun getDrawable(id: Int): Drawable {
    return this.getDrawable(id, null)
  }

  @SuppressLint("PrivateResource")
  @Throws(Resources.NotFoundException::class)
  override fun getDrawable(id: Int, theme: Theme?): Drawable {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      super.getDrawable(id, theme).let { drawable ->
        if (!tintTracker.contains(id, theme)) {
          try {
            cyanea.tinter.tint(drawable)
          } catch (e: CyaneaTintException) {
            Cyanea.log(TAG, "Error tinting drawable", e)
          }
          tintTracker.add(id, theme)
        }
        return drawable
      }
    }
    return when (id) {
      R.color.cyanea_background_dark, R.drawable.cyanea_bg_dark
      -> ColorDrawable(cyanea.backgroundDark)
      R.color.cyanea_background_dark_darker, R.drawable.cyanea_bg_dark_darker
      -> ColorDrawable(cyanea.backgroundDarkDarker)
      R.color.cyanea_background_dark_lighter, R.drawable.cyanea_bg_dark_lighter
      -> ColorDrawable(cyanea.backgroundDarkLighter)
      R.color.cyanea_background_light, R.drawable.cyanea_bg_light
      -> ColorDrawable(cyanea.backgroundLight)
      R.color.cyanea_background_light_darker, R.drawable.cyanea_bg_light_darker
      -> ColorDrawable(cyanea.backgroundLightDarker)
      R.color.cyanea_background_light_lighter, R.drawable.cyanea_bg_light_lighter
      -> ColorDrawable(cyanea.backgroundLightLighter)
      else -> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
          super.getDrawable(id)
        } else {
          super.getDrawable(id, theme)
        }
      }
    }
  }

  @Throws(Resources.NotFoundException::class)
  override fun getColor(id: Int): Int {
    return this.getColor(id, null)
  }

  @SuppressLint("PrivateResource")
  @Throws(Resources.NotFoundException::class)
  override fun getColor(id: Int, theme: Theme?): Int = when (id) {
    // ------ PRIMARY COLORS ------
    R.color.cyanea_primary_reference, R.color.cyanea_primary -> cyanea.primary
    R.color.cyanea_primary_dark_reference, R.color.cyanea_primary_dark -> cyanea.primaryDark
    R.color.cyanea_primary_light_reference, R.color.cyanea_primary_light -> cyanea.primaryLight
    // ------ ACCENT COLORS ------
    R.color.cyanea_accent_reference, R.color.cyanea_accent -> cyanea.accent
    R.color.cyanea_accent_light_reference, R.color.cyanea_accent_light -> cyanea.accentLight
    R.color.cyanea_accent_dark_reference, R.color.cyanea_accent_dark -> cyanea.accentDark
    // ------ BACKGROUND COLORS ------
    R.color.cyanea_bg_dark, R.color.cyanea_background_dark -> cyanea.backgroundDark
    R.color.cyanea_background_dark_lighter -> cyanea.backgroundDarkLighter
    R.color.cyanea_background_dark_darker -> cyanea.backgroundDarkDarker
    R.color.cyanea_bg_light, R.color.cyanea_background_light -> cyanea.backgroundLight
    R.color.cyanea_background_light_darker -> cyanea.backgroundLightDarker
    R.color.cyanea_background_light_lighter -> cyanea.backgroundLightLighter
    // ------ MATERIAL3 COLOR ROLES ------
    R.color.cyanea_m3_primary -> cyanea.m3Primary
    R.color.cyanea_m3_on_primary -> cyanea.m3OnPrimary
    R.color.cyanea_m3_primary_container -> cyanea.m3PrimaryContainer
    R.color.cyanea_m3_on_primary_container -> cyanea.m3OnPrimaryContainer
    R.color.cyanea_m3_secondary -> cyanea.m3Secondary
    R.color.cyanea_m3_on_secondary -> cyanea.m3OnSecondary
    R.color.cyanea_m3_secondary_container -> cyanea.m3SecondaryContainer
    R.color.cyanea_m3_on_secondary_container -> cyanea.m3OnSecondaryContainer
    R.color.cyanea_m3_tertiary -> cyanea.m3Tertiary
    R.color.cyanea_m3_on_tertiary -> cyanea.m3OnTertiary
    R.color.cyanea_m3_tertiary_container -> cyanea.m3TertiaryContainer
    R.color.cyanea_m3_on_tertiary_container -> cyanea.m3OnTertiaryContainer
    R.color.cyanea_m3_error -> cyanea.m3Error
    R.color.cyanea_m3_on_error -> cyanea.m3OnError
    R.color.cyanea_m3_error_container -> cyanea.m3ErrorContainer
    R.color.cyanea_m3_on_error_container -> cyanea.m3OnErrorContainer
    R.color.cyanea_m3_background -> cyanea.m3Background
    R.color.cyanea_m3_on_background -> cyanea.m3OnBackground
    R.color.cyanea_m3_surface -> cyanea.m3Surface
    R.color.cyanea_m3_on_surface -> cyanea.m3OnSurface
    R.color.cyanea_m3_surface_variant -> cyanea.m3SurfaceVariant
    R.color.cyanea_m3_on_surface_variant -> cyanea.m3OnSurfaceVariant
    R.color.cyanea_m3_outline -> cyanea.m3Outline
    R.color.cyanea_m3_outline_variant -> cyanea.m3OutlineVariant
    R.color.cyanea_m3_surface_container_lowest -> cyanea.m3SurfaceContainerLowest
    R.color.cyanea_m3_surface_container_low -> cyanea.m3SurfaceContainerLow
    R.color.cyanea_m3_surface_container -> cyanea.m3SurfaceContainer
    R.color.cyanea_m3_surface_container_high -> cyanea.m3SurfaceContainerHigh
    R.color.cyanea_m3_surface_container_highest -> cyanea.m3SurfaceContainerHighest
    R.color.cyanea_m3_inverse_surface -> cyanea.m3InverseSurface
    R.color.cyanea_m3_inverse_on_surface -> cyanea.m3InverseOnSurface
    R.color.cyanea_m3_inverse_primary -> cyanea.m3InversePrimary
    R.color.cyanea_m3_primary_fixed -> cyanea.m3PrimaryFixed
    R.color.cyanea_m3_on_primary_fixed -> cyanea.m3OnPrimaryFixed
    R.color.cyanea_m3_primary_fixed_dim -> cyanea.m3PrimaryFixedDim
    R.color.cyanea_m3_on_primary_fixed_variant -> cyanea.m3OnPrimaryFixedVariant
    R.color.cyanea_m3_secondary_fixed -> cyanea.m3SecondaryFixed
    R.color.cyanea_m3_on_secondary_fixed -> cyanea.m3OnSecondaryFixed
    R.color.cyanea_m3_secondary_fixed_dim -> cyanea.m3SecondaryFixedDim
    R.color.cyanea_m3_on_secondary_fixed_variant -> cyanea.m3OnSecondaryFixedVariant
    R.color.cyanea_m3_tertiary_fixed -> cyanea.m3TertiaryFixed
    R.color.cyanea_m3_on_tertiary_fixed -> cyanea.m3OnTertiaryFixed
    R.color.cyanea_m3_tertiary_fixed_dim -> cyanea.m3TertiaryFixedDim
    R.color.cyanea_m3_on_tertiary_fixed_variant -> cyanea.m3OnTertiaryFixedVariant
    R.color.cyanea_m3_surface_dim -> cyanea.m3SurfaceDim
    R.color.cyanea_m3_surface_bright -> cyanea.m3SurfaceBright
    else -> {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        super.getColor(id)
      } else {
        super.getColor(id, theme)
      }
    }
  }

  @RequiresApi(Build.VERSION_CODES.M)
  @Throws(Resources.NotFoundException::class)
  override fun getColorStateList(id: Int): ColorStateList {
    return super.getColorStateList(id)
  }

  @RequiresApi(Build.VERSION_CODES.M)
  @Throws(Resources.NotFoundException::class)
  override fun getColorStateList(id: Int, theme: Resources.Theme?): ColorStateList {
    val colorStateList = super.getColorStateList(id, theme)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      if (!tintTracker.contains(id, theme)) {
        cyanea.tinter.tint(colorStateList)
        tintTracker.add(id, theme)
      }
    }
    return colorStateList
  }

  private inner class TintTracker {

    private val cache: MutableSet<Int> by lazy {
      Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    }

    internal fun contains(id: Int, theme: Resources.Theme?): Boolean = cache.contains(key(id, theme))

    internal fun add(id: Int, theme: Resources.Theme?): Boolean = cache.add(key(id, theme))

    private fun key(id: Int, theme: Resources.Theme?): Int = id + (theme?.hashCode() ?: 0)
  }

  companion object {
    private const val TAG = "CyaneaResources"
  }
}
