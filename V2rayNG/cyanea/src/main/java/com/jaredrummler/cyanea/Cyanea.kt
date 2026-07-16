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

@file:Suppress("DEPRECATION")

package com.jaredrummler.cyanea

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.Keep
import androidx.annotation.MainThread
import com.jaredrummler.cyanea.Constants.LIGHT_ACTIONBAR_LUMINANCE_FACTOR
import com.jaredrummler.cyanea.Constants.NONE_TIMESTAMP
import com.jaredrummler.cyanea.Cyanea.BaseTheme.DARK
import com.jaredrummler.cyanea.Cyanea.BaseTheme.LIGHT
import com.jaredrummler.cyanea.Defaults.DEFAULT_DARKER_FACTOR
import com.jaredrummler.cyanea.Defaults.DEFAULT_LIGHTER_FACTOR
import com.jaredrummler.cyanea.PrefKeys.PREF_ACCENT
import com.jaredrummler.cyanea.PrefKeys.PREF_ACCENT_DARK
import com.jaredrummler.cyanea.PrefKeys.PREF_ACCENT_LIGHT
import com.jaredrummler.cyanea.PrefKeys.PREF_BACKGROUND_DARK
import com.jaredrummler.cyanea.PrefKeys.PREF_BACKGROUND_DARK_DARKER
import com.jaredrummler.cyanea.PrefKeys.PREF_BACKGROUND_DARK_LIGHTER
import com.jaredrummler.cyanea.PrefKeys.PREF_BACKGROUND_LIGHT
import com.jaredrummler.cyanea.PrefKeys.PREF_BACKGROUND_LIGHT_DARKER
import com.jaredrummler.cyanea.PrefKeys.PREF_BACKGROUND_LIGHT_LIGHTER
import com.jaredrummler.cyanea.PrefKeys.PREF_BASE_THEME
import com.jaredrummler.cyanea.PrefKeys.PREF_FILE_NAME
import com.jaredrummler.cyanea.PrefKeys.PREF_MENU_ICON_COLOR
import com.jaredrummler.cyanea.PrefKeys.PREF_NAVIGATION_BAR
import com.jaredrummler.cyanea.PrefKeys.PREF_PRIMARY
import com.jaredrummler.cyanea.PrefKeys.PREF_PRIMARY_DARK
import com.jaredrummler.cyanea.PrefKeys.PREF_PRIMARY_LIGHT
import com.jaredrummler.cyanea.PrefKeys.PREF_SHOULD_TINT_NAV_BAR
import com.jaredrummler.cyanea.PrefKeys.PREF_SHOULD_TINT_STATUS_BAR
import com.jaredrummler.cyanea.PrefKeys.PREF_SUB_MENU_ICON_COLOR
import com.jaredrummler.cyanea.PrefKeys.PREF_TIMESTAMP
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.Scheme
import com.jaredrummler.cyanea.inflator.CyaneaInflationDelegate
import com.jaredrummler.cyanea.inflator.CyaneaLayoutInflater
import com.jaredrummler.cyanea.tinting.CyaneaTinter
import com.jaredrummler.cyanea.tinting.MenuTint
import com.jaredrummler.cyanea.utils.ColorUtils
import kotlin.properties.Delegates

/**
 * Contains colors for an application theme.
 *
 * Before using Cyanea you must initialize it in your application class or have the application class be [CyaneaApp].
 *
 * To retrieve a color from a Cyanea based activity, simply call:
 *
 * ```kotlin
 * val primaryColor = cyanea.primary // application's primary color
 * val accentColor = cyanea.accent // application's accent color
 * ```
 *
 * To dynamically edit a theme you can use [Cyanea.Editor].
 *
 * Example:
 *
 * ```kotlin
 * Cyanea.instance.edit {
 *   primary(Color.RED)
 *   accent(Color.YELLOW)
 *   background(Color.BLACK)
 * }
 * ```
 *
 * After editing a theme you must recreate the activity for changes to apply.
 */
class Cyanea private constructor(private val prefs: SharedPreferences) {

  /** The primary color displayed most frequently across your app */
  var primary by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** A lighter version of the [primary] color */
  var primaryLight by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** A darker version of the [primary] color */
  var primaryDark by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** The accent color that accents select parts of the UI */
  var accent by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** A lighter version of the [accent] color */
  var accentLight by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** A darker version of the [accent] color */
  var accentDark by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** The background color used as the underlying color of the app's content */
  val backgroundColor: Int
    get() = when (baseTheme) {
      LIGHT -> backgroundLight
      DARK -> backgroundDark
    }

  /* A lighter version of the [background] color */
  val backgroundColorLight: Int
    get() = when (baseTheme) {
      LIGHT -> backgroundLightLighter
      DARK -> backgroundDarkLighter
    }

  /* A darker version of the [background] color */
  val backgroundColorDark: Int
    get() = when (baseTheme) {
      LIGHT -> backgroundLightDarker
      DARK -> backgroundDarkDarker
    }

  /** The color of icons in a [Menu] */
  var menuIconColor by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** The color of icons in a [menu's][Menu] sub-menu */
  var subMenuIconColor by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** The color of the navigation bar, usually is black or the [primary] color */
  var navigationBar by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** True to set the [primaryDark] color on the system status bar */
  var shouldTintStatusBar by Delegates.notNull<Boolean>()
    private set

  /** True to set the [navigationBar] color on the system navigation bar */
  var shouldTintNavBar by Delegates.notNull<Boolean>()
    private set

  /** The base theme. Either [LIGHT] or [DARK] */
  var baseTheme by Delegates.notNull<BaseTheme>()
    internal set

  /** True if the [baseTheme] is [DARK] */
  val isDark get() = baseTheme == DARK

  /** True if the [baseTheme] is [LIGHT] */
  val isLight get() = baseTheme == LIGHT

  /** True if the [primary] color is a dark color */
  val isActionBarDark get() = ColorUtils.isDarkColor(primary, 0.75)

  /** True if the [primary] color is a light color */
  val isActionBarLight get() = !isActionBarDark

  /** True if the theme has been modified at least once */
  val isThemeModified get() = timestamp != NONE_TIMESTAMP

  /** Helper to tint a [Drawable], [ColorStateList] or a [View] */
  val tinter by lazy { CyaneaTinter() }
  val themes by lazy { CyaneaThemes(this) }

  internal var backgroundDark by Delegates.notNull<Int>()
    @ColorInt get
  internal var backgroundDarkLighter by Delegates.notNull<Int>()
    @ColorInt get
  internal var backgroundDarkDarker by Delegates.notNull<Int>()
    @ColorInt get
  internal var backgroundLight by Delegates.notNull<Int>()
    @ColorInt get
  internal var backgroundLightLighter by Delegates.notNull<Int>()
    @ColorInt get
  internal var backgroundLightDarker by Delegates.notNull<Int>()
    @ColorInt get

  internal var timestamp: Long = 0L
    private set

  // ------ Material3 color roles ------
  // Generated from a seed color using the same TonalSpot algorithm Material3/DynamicColors
  // uses. See [Editor.materialYou].

  /** M3 `colorPrimary` */
  var m3Primary by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnPrimary` */
  var m3OnPrimary by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorPrimaryContainer` */
  var m3PrimaryContainer by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnPrimaryContainer` */
  var m3OnPrimaryContainer by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSecondary` */
  var m3Secondary by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnSecondary` */
  var m3OnSecondary by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSecondaryContainer` */
  var m3SecondaryContainer by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnSecondaryContainer` */
  var m3OnSecondaryContainer by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorTertiary` */
  var m3Tertiary by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnTertiary` */
  var m3OnTertiary by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorTertiaryContainer` */
  var m3TertiaryContainer by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnTertiaryContainer` */
  var m3OnTertiaryContainer by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorError` */
  var m3Error by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnError` */
  var m3OnError by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorErrorContainer` */
  var m3ErrorContainer by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnErrorContainer` */
  var m3OnErrorContainer by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `android:colorBackground` */
  var m3Background by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnBackground` */
  var m3OnBackground by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSurface` */
  var m3Surface by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnSurface` */
  var m3OnSurface by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSurfaceVariant` */
  var m3SurfaceVariant by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnSurfaceVariant` */
  var m3OnSurfaceVariant by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOutline` */
  var m3Outline by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOutlineVariant` */
  var m3OutlineVariant by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSurfaceContainerLowest` */
  var m3SurfaceContainerLowest by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSurfaceContainerLow` */
  var m3SurfaceContainerLow by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSurfaceContainer` */
  var m3SurfaceContainer by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSurfaceContainerHigh` */
  var m3SurfaceContainerHigh by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSurfaceContainerHighest` */
  var m3SurfaceContainerHighest by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSurfaceInverse` (inverseSurface) */
  var m3InverseSurface by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnSurfaceInverse` (inverseOnSurface) */
  var m3InverseOnSurface by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorPrimaryInverse` (inversePrimary) */
  var m3InversePrimary by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorPrimaryFixed` */
  var m3PrimaryFixed by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnPrimaryFixed` */
  var m3OnPrimaryFixed by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorPrimaryFixedDim` */
  var m3PrimaryFixedDim by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnPrimaryFixedVariant` */
  var m3OnPrimaryFixedVariant by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSecondaryFixed` */
  var m3SecondaryFixed by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnSecondaryFixed` */
  var m3OnSecondaryFixed by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSecondaryFixedDim` */
  var m3SecondaryFixedDim by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnSecondaryFixedVariant` */
  var m3OnSecondaryFixedVariant by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorTertiaryFixed` */
  var m3TertiaryFixed by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnTertiaryFixed` */
  var m3OnTertiaryFixed by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorTertiaryFixedDim` */
  var m3TertiaryFixedDim by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorOnTertiaryFixedVariant` */
  var m3OnTertiaryFixedVariant by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSurfaceDim` */
  var m3SurfaceDim by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** M3 `colorSurfaceBright` */
  var m3SurfaceBright by Delegates.notNull<Int>()
    @ColorInt get
    private set

  /** The seed color the current Material3 color roles were generated from, or null if [Editor.materialYou] was never called */
  var m3Seed: Int? = null
    private set

  init {
    loadDefaults()
  }

  /**
   * Tint all items and sub-menu items in a [menu][Menu]
   *
   * @param menu the Menu to tint
   * @param activity the current Activity
   * @param forceIcons False to hide sub-menu icons from showing. True by default.
   */
  @JvmOverloads
  fun tint(menu: Menu, activity: Activity, forceIcons: Boolean = true) =
    MenuTint(
      menu,
      menuIconColor = menuIconColor,
      subIconColor = subMenuIconColor,
      forceIcons = forceIcons
    ).apply(activity)

  /**
   * Create a new [Editor] to edit this instance
   */
  fun edit() = Editor(this)

  /**
   * Reset all theme values. The activity must be recreated after resetting.
   */
  fun reset() = prefs.edit().clear().apply().also { loadDefaults() }.run { Recreator() }

  /**
   * Creates a new editor and applys any edits in the action parameter
   */
  inline fun edit(action: Cyanea.Editor.() -> Unit) = edit().also { editor -> action(editor) }.apply()

  private fun loadDefaults() {
    primary = prefs.getInt(
      PREF_PRIMARY,
      res.getColor(R.color.cyanea_primary_reference)
    )
    primaryDark = prefs.getInt(
      PREF_PRIMARY_DARK,
      res.getColor(R.color.cyanea_primary_dark_reference)
    )
    primaryLight = prefs.getInt(
      PREF_PRIMARY_LIGHT,
      res.getColor(R.color.cyanea_primary_light_reference)
    )

    accent = prefs.getInt(
      PREF_ACCENT,
      res.getColor(R.color.cyanea_accent_reference)
    )
    accentDark = prefs.getInt(
      PREF_ACCENT_DARK,
      res.getColor(R.color.cyanea_accent_dark_reference)
    )
    accentLight = prefs.getInt(
      PREF_ACCENT_LIGHT,
      res.getColor(R.color.cyanea_accent_light_reference)
    )

    backgroundLight = prefs.getInt(
      PREF_BACKGROUND_LIGHT,
      res.getColor(R.color.cyanea_bg_light)
    )
    backgroundLightDarker = prefs.getInt(
      PREF_BACKGROUND_LIGHT_DARKER,
      res.getColor(R.color.cyanea_bg_light_darker)
    )
    backgroundLightLighter = prefs.getInt(
      PREF_BACKGROUND_LIGHT_LIGHTER,
      res.getColor(R.color.cyanea_bg_light_lighter)
    )

    backgroundDark = prefs.getInt(
      PREF_BACKGROUND_DARK,
      res.getColor(R.color.cyanea_bg_dark)
    )
    backgroundDarkDarker = prefs.getInt(
      PREF_BACKGROUND_DARK_DARKER,
      res.getColor(R.color.cyanea_bg_dark_darker)
    )
    backgroundDarkLighter = prefs.getInt(
      PREF_BACKGROUND_DARK_LIGHTER,
      res.getColor(R.color.cyanea_bg_dark_lighter)
    )

    baseTheme = getBaseTheme(prefs, res)

    menuIconColor = prefs.getInt(
      PREF_MENU_ICON_COLOR,
      res.getColor(if (isActionBarLight) R.color.cyanea_menu_icon_dark else R.color.cyanea_menu_icon_light)
    )
    subMenuIconColor = prefs.getInt(
      PREF_SUB_MENU_ICON_COLOR,
      res.getColor(if (baseTheme == LIGHT) R.color.cyanea_sub_menu_icon_dark else R.color.cyanea_sub_menu_icon_light)
    )

    navigationBar = prefs.getInt(
      PREF_NAVIGATION_BAR,
      res.getColor(R.color.cyanea_navigation_bar_reference)
    )

    shouldTintStatusBar = prefs.getBoolean(
      PREF_SHOULD_TINT_STATUS_BAR,
      res.getBoolean(R.bool.should_tint_status_bar)
    )
    shouldTintNavBar = prefs.getBoolean(
      PREF_SHOULD_TINT_NAV_BAR,
      res.getBoolean(R.bool.should_tint_nav_bar)
    )

    timestamp = prefs.getLong(PREF_TIMESTAMP, NONE_TIMESTAMP)

    m3Primary = prefs.getInt(PrefKeys.PREF_M3_PRIMARY, res.getColor(R.color.cyanea_m3_primary))
    m3OnPrimary = prefs.getInt(PrefKeys.PREF_M3_ON_PRIMARY, res.getColor(R.color.cyanea_m3_on_primary))
    m3PrimaryContainer = prefs.getInt(PrefKeys.PREF_M3_PRIMARY_CONTAINER, res.getColor(R.color.cyanea_m3_primary_container))
    m3OnPrimaryContainer = prefs.getInt(PrefKeys.PREF_M3_ON_PRIMARY_CONTAINER, res.getColor(R.color.cyanea_m3_on_primary_container))
    m3Secondary = prefs.getInt(PrefKeys.PREF_M3_SECONDARY, res.getColor(R.color.cyanea_m3_secondary))
    m3OnSecondary = prefs.getInt(PrefKeys.PREF_M3_ON_SECONDARY, res.getColor(R.color.cyanea_m3_on_secondary))
    m3SecondaryContainer = prefs.getInt(PrefKeys.PREF_M3_SECONDARY_CONTAINER, res.getColor(R.color.cyanea_m3_secondary_container))
    m3OnSecondaryContainer = prefs.getInt(PrefKeys.PREF_M3_ON_SECONDARY_CONTAINER, res.getColor(R.color.cyanea_m3_on_secondary_container))
    m3Tertiary = prefs.getInt(PrefKeys.PREF_M3_TERTIARY, res.getColor(R.color.cyanea_m3_tertiary))
    m3OnTertiary = prefs.getInt(PrefKeys.PREF_M3_ON_TERTIARY, res.getColor(R.color.cyanea_m3_on_tertiary))
    m3TertiaryContainer = prefs.getInt(PrefKeys.PREF_M3_TERTIARY_CONTAINER, res.getColor(R.color.cyanea_m3_tertiary_container))
    m3OnTertiaryContainer = prefs.getInt(PrefKeys.PREF_M3_ON_TERTIARY_CONTAINER, res.getColor(R.color.cyanea_m3_on_tertiary_container))
    m3Error = prefs.getInt(PrefKeys.PREF_M3_ERROR, res.getColor(R.color.cyanea_m3_error))
    m3OnError = prefs.getInt(PrefKeys.PREF_M3_ON_ERROR, res.getColor(R.color.cyanea_m3_on_error))
    m3ErrorContainer = prefs.getInt(PrefKeys.PREF_M3_ERROR_CONTAINER, res.getColor(R.color.cyanea_m3_error_container))
    m3OnErrorContainer = prefs.getInt(PrefKeys.PREF_M3_ON_ERROR_CONTAINER, res.getColor(R.color.cyanea_m3_on_error_container))
    m3Background = prefs.getInt(PrefKeys.PREF_M3_BACKGROUND, res.getColor(R.color.cyanea_m3_background))
    m3OnBackground = prefs.getInt(PrefKeys.PREF_M3_ON_BACKGROUND, res.getColor(R.color.cyanea_m3_on_background))
    m3Surface = prefs.getInt(PrefKeys.PREF_M3_SURFACE, res.getColor(R.color.cyanea_m3_surface))
    m3OnSurface = prefs.getInt(PrefKeys.PREF_M3_ON_SURFACE, res.getColor(R.color.cyanea_m3_on_surface))
    m3SurfaceVariant = prefs.getInt(PrefKeys.PREF_M3_SURFACE_VARIANT, res.getColor(R.color.cyanea_m3_surface_variant))
    m3OnSurfaceVariant = prefs.getInt(PrefKeys.PREF_M3_ON_SURFACE_VARIANT, res.getColor(R.color.cyanea_m3_on_surface_variant))
    m3Outline = prefs.getInt(PrefKeys.PREF_M3_OUTLINE, res.getColor(R.color.cyanea_m3_outline))
    m3OutlineVariant = prefs.getInt(PrefKeys.PREF_M3_OUTLINE_VARIANT, res.getColor(R.color.cyanea_m3_outline_variant))
    m3SurfaceContainerLowest = prefs.getInt(PrefKeys.PREF_M3_SURFACE_CONTAINER_LOWEST, res.getColor(R.color.cyanea_m3_surface_container_lowest))
    m3SurfaceContainerLow = prefs.getInt(PrefKeys.PREF_M3_SURFACE_CONTAINER_LOW, res.getColor(R.color.cyanea_m3_surface_container_low))
    m3SurfaceContainer = prefs.getInt(PrefKeys.PREF_M3_SURFACE_CONTAINER, res.getColor(R.color.cyanea_m3_surface_container))
    m3SurfaceContainerHigh = prefs.getInt(PrefKeys.PREF_M3_SURFACE_CONTAINER_HIGH, res.getColor(R.color.cyanea_m3_surface_container_high))
    m3SurfaceContainerHighest = prefs.getInt(PrefKeys.PREF_M3_SURFACE_CONTAINER_HIGHEST, res.getColor(R.color.cyanea_m3_surface_container_highest))
    m3InverseSurface = prefs.getInt(PrefKeys.PREF_M3_INVERSE_SURFACE, res.getColor(R.color.cyanea_m3_inverse_surface))
    m3InverseOnSurface = prefs.getInt(PrefKeys.PREF_M3_INVERSE_ON_SURFACE, res.getColor(R.color.cyanea_m3_inverse_on_surface))
    m3InversePrimary = prefs.getInt(PrefKeys.PREF_M3_INVERSE_PRIMARY, res.getColor(R.color.cyanea_m3_inverse_primary))
    m3PrimaryFixed = prefs.getInt(PrefKeys.PREF_M3_PRIMARY_FIXED, res.getColor(R.color.cyanea_m3_primary_fixed))
    m3OnPrimaryFixed = prefs.getInt(PrefKeys.PREF_M3_ON_PRIMARY_FIXED, res.getColor(R.color.cyanea_m3_on_primary_fixed))
    m3PrimaryFixedDim = prefs.getInt(PrefKeys.PREF_M3_PRIMARY_FIXED_DIM, res.getColor(R.color.cyanea_m3_primary_fixed_dim))
    m3OnPrimaryFixedVariant = prefs.getInt(PrefKeys.PREF_M3_ON_PRIMARY_FIXED_VARIANT, res.getColor(R.color.cyanea_m3_on_primary_fixed_variant))
    m3SecondaryFixed = prefs.getInt(PrefKeys.PREF_M3_SECONDARY_FIXED, res.getColor(R.color.cyanea_m3_secondary_fixed))
    m3OnSecondaryFixed = prefs.getInt(PrefKeys.PREF_M3_ON_SECONDARY_FIXED, res.getColor(R.color.cyanea_m3_on_secondary_fixed))
    m3SecondaryFixedDim = prefs.getInt(PrefKeys.PREF_M3_SECONDARY_FIXED_DIM, res.getColor(R.color.cyanea_m3_secondary_fixed_dim))
    m3OnSecondaryFixedVariant = prefs.getInt(PrefKeys.PREF_M3_ON_SECONDARY_FIXED_VARIANT, res.getColor(R.color.cyanea_m3_on_secondary_fixed_variant))
    m3TertiaryFixed = prefs.getInt(PrefKeys.PREF_M3_TERTIARY_FIXED, res.getColor(R.color.cyanea_m3_tertiary_fixed))
    m3OnTertiaryFixed = prefs.getInt(PrefKeys.PREF_M3_ON_TERTIARY_FIXED, res.getColor(R.color.cyanea_m3_on_tertiary_fixed))
    m3TertiaryFixedDim = prefs.getInt(PrefKeys.PREF_M3_TERTIARY_FIXED_DIM, res.getColor(R.color.cyanea_m3_tertiary_fixed_dim))
    m3OnTertiaryFixedVariant = prefs.getInt(PrefKeys.PREF_M3_ON_TERTIARY_FIXED_VARIANT, res.getColor(R.color.cyanea_m3_on_tertiary_fixed_variant))
    m3SurfaceDim = prefs.getInt(PrefKeys.PREF_M3_SURFACE_DIM, res.getColor(R.color.cyanea_m3_surface_dim))
    m3SurfaceBright = prefs.getInt(PrefKeys.PREF_M3_SURFACE_BRIGHT, res.getColor(R.color.cyanea_m3_surface_bright))

    m3Seed = if (prefs.contains(PrefKeys.PREF_M3_SEED)) prefs.getInt(PrefKeys.PREF_M3_SEED, primary) else null

    setDefaultDarkerAndLighterColors()
  }

  private fun setDefaultDarkerAndLighterColors() {
    // We use a transparent primary|accent dark|light colors so the library user
    // is not required to specify a color value for for accent|primary light|dark
    // If the theme is using the transparent (fake) primary dark color, we need
    // to update our color values and create light|dark variants for them.
    if (primaryDark == getOriginalColor(R.color.cyanea_default_primary_dark)) {
      primaryDark = ColorUtils.darker(primary, DEFAULT_DARKER_FACTOR)
    }
    if (primaryLight == getOriginalColor(R.color.cyanea_default_primary_light)) {
      primaryLight = ColorUtils.lighter(primary, DEFAULT_LIGHTER_FACTOR)
    }
    if (accentDark == getOriginalColor(R.color.cyanea_default_accent_dark)) {
      accentDark = ColorUtils.darker(accent, DEFAULT_DARKER_FACTOR)
    }
    if (accentLight == getOriginalColor(R.color.cyanea_default_accent_light)) {
      accentLight = ColorUtils.lighter(accent, DEFAULT_LIGHTER_FACTOR)
    }
  }

  companion object {

    @SuppressLint("StaticFieldLeak") // application context is safe
    internal lateinit var app: Application
    lateinit var res: Resources

    /**
     * Initialize Cyanea. This should be done in the [application][Application] class.
     */
    @JvmStatic
    fun init(app: Application, res: Resources) {
      this.app = app
      this.res = res
    }

    /**
     * Check if Cyanea has been initialized.
     *
     * @see [init]
     */
    @JvmStatic
    fun isInitialized(): Boolean {
      return try {
        app
        res
        true
      } catch (e: UninitializedPropertyAccessException) {
        false
      }
    }

    private object Holder {

      val INSTANCE: Cyanea
        get() {
          try {
            val preferences = app.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
            return Cyanea(preferences)
          } catch (e: UninitializedPropertyAccessException) {
            throw IllegalStateException("Cyanea.init must be called before referencing the singleton instance")
          }
        }
    }

    private val instances by lazy { mutableMapOf<String, Cyanea>() }

    /**
     * The singleton [Cyanea] instance that you can use throughout the application.
     */
    @JvmStatic
    val instance: Cyanea by lazy { Holder.INSTANCE }

    /**
     * Get a instance of [Cyanea] by name. This will create a new instance if none exist.
     *
     * This allows you to have more than one color scheme in an app. You must override Activity#getCyanea().
     */
    @JvmStatic
    fun getInstance(name: String): Cyanea {
      instances[name]?.let { cyanea ->
        return cyanea
      } ?: run {
        val preferences = app.getSharedPreferences(name, Context.MODE_PRIVATE)
        val cyanea = Cyanea(preferences)
        instances[name] = cyanea
        return cyanea
      }
    }

    /**
     * Intercept and create views at inflation time
     *
     * @delegate The delegate used to intercept and create views
     */
    @JvmStatic
    @MainThread
    fun setInflationDelegate(delegate: CyaneaInflationDelegate) {
      CyaneaLayoutInflater.inflationDelegate = delegate
    }

    /**
     * Turns on logging for the [Cyanea] library
     */
    @JvmStatic
    var loggingEnabled = false

    @JvmStatic
    fun log(tag: String, msg: String, ex: Throwable? = null) {
      if (loggingEnabled) {
        Log.d(tag, msg, ex)
      }
    }

    /**
     * Get the original color of a color resource.
     *
     * @param resid The color resource to retrieve
     */
    @JvmStatic
    @ColorInt
    fun getOriginalColor(@ColorRes resid: Int): Int = res.getColor(resid)

    private fun getBaseTheme(prefs: SharedPreferences, res: Resources): BaseTheme {
      val themeName = prefs.getString(PREF_BASE_THEME, null)
      return when (themeName) {
        LIGHT.name -> LIGHT
        DARK.name -> DARK
        else -> {
          TypedValue().also {
            app.theme?.resolveAttribute(android.R.attr.windowBackground, it, true)
          }.let {
            return if (it.type >= TypedValue.TYPE_FIRST_COLOR_INT && it.type <= TypedValue.TYPE_LAST_COLOR_INT) {
              if (ColorUtils.isDarkColor(it.data, LIGHT_ACTIONBAR_LUMINANCE_FACTOR)) DARK else LIGHT
            } else if (res.getBoolean(R.bool.is_default_theme_light)) LIGHT else DARK
          }
        }
      }
    }
  }

  /**
   * An editor for Cyanea to change colors and other values
   */
  @Suppress("MemberVisibilityCanBePrivate")
  class Editor internal constructor(private val cyanea: Cyanea) {

    private val editor = cyanea.prefs.edit()

    /**
     * Set the [primary] color using a color resource.
     *
     * The [primaryDark], [primaryLight], [navigationBar], and [menuIconColor] will also be updated to match the theme.
     */
    fun primaryResource(@ColorRes resid: Int) = primary(res.getColor(resid))

    /** Set the [primary] dark color using a color resource. */
    fun primaryDarkResource(@ColorRes resid: Int) = primaryDark(res.getColor(resid))

    /** Set the [primary] light color using a color resource. */
    fun primaryLightResource(@ColorRes resid: Int) = primaryLight(res.getColor(resid))

    /**
     * Set the [accent] dark color using a color resource.
     *
     * The [accentDark] and [accentLight] colors will also be updated.
     */
    fun accentResource(@ColorRes resid: Int): Editor = accent(res.getColor(resid))

    /** Set the [accent] dark color using a color resource. */
    fun accentDarkResource(@ColorRes resid: Int) = accentDark(res.getColor(resid))

    /** Set the [accent] light color using a color resource. */
    fun accentLightResource(@ColorRes resid: Int) = accentLight(res.getColor(resid))

    /**
     * Set the background color using a color resource.
     *
     * The [baseTheme], [backgroundLight], [backgroundDark] and [subMenuIconColor] will also be updated.
     */
    fun backgroundResource(@ColorRes resid: Int) = background(res.getColor(resid))

    /** Set the background color for a [LIGHT] theme using a color resource. */
    fun backgroundLightResource(@ColorRes resid: Int) = backgroundLight(res.getColor(resid))

    /** Set the background dark color for a [LIGHT] theme using a color resource. */
    fun backgroundLightDarkerResource(@ColorRes resid: Int) = backgroundLightDarker(res.getColor(resid))

    /** Set the background light color for a [LIGHT] theme using a color resource. */
    fun backgroundLightLighterResource(@ColorRes resid: Int) = backgroundLightLighter(res.getColor(resid))

    /** Set the background color for a [DARK] theme using a color resource. */
    fun backgroundDarkResource(@ColorRes resid: Int) = backgroundDark(res.getColor(resid))

    /** Set the background dark color for a [DARK] theme using a color resource. */
    fun backgroundDarkDarkerResource(@ColorRes resid: Int) = backgroundDarkDarker(res.getColor(resid))

    /** Set the background light color for a [DARK] theme using a color resource. */
    fun backgroundDarkLighterResource(@ColorRes resid: Int) = backgroundDarkLighter(res.getColor(resid))

    /** Set the [menuIconColor] using a color resource */
    fun menuIconColorResource(@ColorRes resid: Int) = menuIconColor(res.getColor(resid))

    /** Set the [subMenuIconColor] using a color resource */
    fun subMenuIconColorResource(@ColorRes resid: Int) = subMenuIconColor(res.getColor(resid))

    /** Set the [navigationBar] color using a color resource */
    fun navigationBarResource(@ColorRes resid: Int) = navigationBar(res.getColor(resid))

    /**
     * Set the [primary] color using a color resource.
     *
     * The [primaryDark], [primaryLight], [navigationBar], and [menuIconColor] will also be updated to match the theme.
     */
    fun primary(@ColorInt color: Int): Editor {
      cyanea.primary = color
      editor.putInt(PREF_PRIMARY, color)
      val isDarkColor = ColorUtils.isDarkColor(color, LIGHT_ACTIONBAR_LUMINANCE_FACTOR)
      val menuIconColorRes = if (isDarkColor) R.color.cyanea_menu_icon_light else R.color.cyanea_menu_icon_dark
      primaryDark(ColorUtils.darker(color, DEFAULT_DARKER_FACTOR))
      primaryLight(ColorUtils.lighter(color, DEFAULT_LIGHTER_FACTOR))
      menuIconColor(res.getColor(menuIconColorRes))
      navigationBar(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || isDarkColor) color else Color.BLACK)
      return this
    }

    /** Set the [primary] dark color using a color resource. */
    fun primaryDark(@ColorInt color: Int): Editor {
      cyanea.primaryDark = color
      editor.putInt(PREF_PRIMARY_DARK, color)
      return this
    }

    /** Set the [primary] light color using a color resource. */
    fun primaryLight(@ColorInt color: Int): Editor {
      cyanea.primaryLight = color
      editor.putInt(PREF_PRIMARY_LIGHT, color)
      return this
    }

    /**
     * Set the [accent] dark color using a color resource.
     *
     * The [accentDark] and [accentLight] colors will also be updated.
     */
    fun accent(@ColorInt color: Int): Editor {
      cyanea.accent = color
      editor.putInt(PREF_ACCENT, color)
      accentDark(ColorUtils.darker(color, DEFAULT_DARKER_FACTOR))
      accentLight(ColorUtils.lighter(color, DEFAULT_LIGHTER_FACTOR))
      return this
    }

    /** Set the [accent] dark color using a color resource. */
    fun accentDark(@ColorInt color: Int): Editor {
      cyanea.accentDark = color
      editor.putInt(PREF_ACCENT_DARK, color)
      return this
    }

    /** Set the [accent] light color using a color resource. */
    fun accentLight(@ColorInt color: Int): Editor {
      cyanea.accentLight = color
      editor.putInt(PREF_ACCENT_LIGHT, color)
      return this
    }

    /**
     * Set the background color using a color resource.
     *
     * The [baseTheme], [backgroundLight], [backgroundDark] and [subMenuIconColor] will also be updated.
     */
    fun background(@ColorInt color: Int): Editor {
      val lighter = ColorUtils.lighter(color, DEFAULT_LIGHTER_FACTOR)
      val darker = ColorUtils.darker(color, DEFAULT_DARKER_FACTOR)
      val isDarkColor = ColorUtils.isDarkColor(color, LIGHT_ACTIONBAR_LUMINANCE_FACTOR)
      if (isDarkColor) {
        baseTheme(DARK)
        backgroundDark(color)
        backgroundDarkDarker(darker)
        backgroundDarkLighter(lighter)
        subMenuIconColor(res.getColor(R.color.cyanea_sub_menu_icon_light))
      } else {
        baseTheme(LIGHT)
        backgroundLight(color)
        backgroundLightDarker(darker)
        backgroundLightLighter(lighter)
        subMenuIconColor(res.getColor(R.color.cyanea_sub_menu_icon_dark))
      }
      return this
    }

    /**
     * Generate and apply a full set of Material3 color roles (colorPrimary, colorOnPrimary,
     * colorPrimaryContainer, colorSecondary, colorSurface, colorOutline, etc.) from a single
     * seed color, using the same TonalSpot algorithm Android's own Material You dynamic color
     * system uses ([com.google.android.material.color.utilities.Scheme]).
     *
     * This also updates the legacy [primary]/[accent]/[background] colors so that
     * `Theme.Cyanea.*` (non-M3) call sites stay visually consistent with the generated scheme.
     *
     * @param seed The seed/source color, e.g. a banner's dominant color or a user's color pick.
     * @param dark True to generate the dark Material3 scheme, false for the light scheme.
     */
    fun materialYou(@ColorInt seed: Int, dark: Boolean): Editor {
      val scheme = if (dark) Scheme.dark(seed) else Scheme.light(seed)

      set(PrefKeys.PREF_M3_PRIMARY, scheme.primary) { cyanea.m3Primary = it }
      set(PrefKeys.PREF_M3_ON_PRIMARY, scheme.onPrimary) { cyanea.m3OnPrimary = it }
      set(PrefKeys.PREF_M3_PRIMARY_CONTAINER, scheme.primaryContainer) { cyanea.m3PrimaryContainer = it }
      set(PrefKeys.PREF_M3_ON_PRIMARY_CONTAINER, scheme.onPrimaryContainer) { cyanea.m3OnPrimaryContainer = it }
      set(PrefKeys.PREF_M3_SECONDARY, scheme.secondary) { cyanea.m3Secondary = it }
      set(PrefKeys.PREF_M3_ON_SECONDARY, scheme.onSecondary) { cyanea.m3OnSecondary = it }
      set(PrefKeys.PREF_M3_SECONDARY_CONTAINER, scheme.secondaryContainer) { cyanea.m3SecondaryContainer = it }
      set(PrefKeys.PREF_M3_ON_SECONDARY_CONTAINER, scheme.onSecondaryContainer) { cyanea.m3OnSecondaryContainer = it }
      set(PrefKeys.PREF_M3_TERTIARY, scheme.tertiary) { cyanea.m3Tertiary = it }
      set(PrefKeys.PREF_M3_ON_TERTIARY, scheme.onTertiary) { cyanea.m3OnTertiary = it }
      set(PrefKeys.PREF_M3_TERTIARY_CONTAINER, scheme.tertiaryContainer) { cyanea.m3TertiaryContainer = it }
      set(PrefKeys.PREF_M3_ON_TERTIARY_CONTAINER, scheme.onTertiaryContainer) { cyanea.m3OnTertiaryContainer = it }
      set(PrefKeys.PREF_M3_ERROR, scheme.error) { cyanea.m3Error = it }
      set(PrefKeys.PREF_M3_ON_ERROR, scheme.onError) { cyanea.m3OnError = it }
      set(PrefKeys.PREF_M3_ERROR_CONTAINER, scheme.errorContainer) { cyanea.m3ErrorContainer = it }
      set(PrefKeys.PREF_M3_ON_ERROR_CONTAINER, scheme.onErrorContainer) { cyanea.m3OnErrorContainer = it }
      set(PrefKeys.PREF_M3_BACKGROUND, scheme.background) { cyanea.m3Background = it }
      set(PrefKeys.PREF_M3_ON_BACKGROUND, scheme.onBackground) { cyanea.m3OnBackground = it }
      set(PrefKeys.PREF_M3_SURFACE, scheme.surface) { cyanea.m3Surface = it }
      set(PrefKeys.PREF_M3_ON_SURFACE, scheme.onSurface) { cyanea.m3OnSurface = it }
      set(PrefKeys.PREF_M3_SURFACE_VARIANT, scheme.surfaceVariant) { cyanea.m3SurfaceVariant = it }
      set(PrefKeys.PREF_M3_ON_SURFACE_VARIANT, scheme.onSurfaceVariant) { cyanea.m3OnSurfaceVariant = it }
      set(PrefKeys.PREF_M3_OUTLINE, scheme.outline) { cyanea.m3Outline = it }
      set(PrefKeys.PREF_M3_OUTLINE_VARIANT, scheme.outlineVariant) { cyanea.m3OutlineVariant = it }
      set(PrefKeys.PREF_M3_INVERSE_SURFACE, scheme.inverseSurface) { cyanea.m3InverseSurface = it }
      set(PrefKeys.PREF_M3_INVERSE_ON_SURFACE, scheme.inverseOnSurface) { cyanea.m3InverseOnSurface = it }
      set(PrefKeys.PREF_M3_INVERSE_PRIMARY, scheme.inversePrimary) { cyanea.m3InversePrimary = it }

      // The original open-source `Scheme` class doesn't expose the newer surface-container
      // tonal levels, so derive them here: colors within the same HCT tonal palette share hue
      // and chroma, only the tone changes, so we can reuse the neutral palette's hue/chroma
      // from `surface` and simply re-tone it to the standard M3 surface-container tone stops.
      val neutral = Hct.fromInt(scheme.surface)
      fun neutralTone(tone: Double) = Hct.from(neutral.hue, neutral.chroma, tone).toInt()
      if (dark) {
        set(PrefKeys.PREF_M3_SURFACE_CONTAINER_LOWEST, neutralTone(4.0)) { cyanea.m3SurfaceContainerLowest = it }
        set(PrefKeys.PREF_M3_SURFACE_CONTAINER_LOW, neutralTone(10.0)) { cyanea.m3SurfaceContainerLow = it }
        set(PrefKeys.PREF_M3_SURFACE_CONTAINER, neutralTone(12.0)) { cyanea.m3SurfaceContainer = it }
        set(PrefKeys.PREF_M3_SURFACE_CONTAINER_HIGH, neutralTone(17.0)) { cyanea.m3SurfaceContainerHigh = it }
        set(PrefKeys.PREF_M3_SURFACE_CONTAINER_HIGHEST, neutralTone(22.0)) { cyanea.m3SurfaceContainerHighest = it }
      } else {
        set(PrefKeys.PREF_M3_SURFACE_CONTAINER_LOWEST, neutralTone(100.0)) { cyanea.m3SurfaceContainerLowest = it }
        set(PrefKeys.PREF_M3_SURFACE_CONTAINER_LOW, neutralTone(96.0)) { cyanea.m3SurfaceContainerLow = it }
        set(PrefKeys.PREF_M3_SURFACE_CONTAINER, neutralTone(94.0)) { cyanea.m3SurfaceContainer = it }
        set(PrefKeys.PREF_M3_SURFACE_CONTAINER_HIGH, neutralTone(92.0)) { cyanea.m3SurfaceContainerHigh = it }
        set(PrefKeys.PREF_M3_SURFACE_CONTAINER_HIGHEST, neutralTone(90.0)) { cyanea.m3SurfaceContainerHighest = it }
      }

      // "Fixed" roles keep the same tone regardless of light/dark mode, and surfaceDim/Bright
      // are just the neutral palette re-toned - reuse the same hue/chroma preservation trick.
      val primaryHct = Hct.fromInt(scheme.primary)
      val secondaryHct = Hct.fromInt(scheme.secondary)
      val tertiaryHct = Hct.fromInt(scheme.tertiary)
      fun tone(hct: Hct, t: Double) = Hct.from(hct.hue, hct.chroma, t).toInt()

      set(PrefKeys.PREF_M3_PRIMARY_FIXED, tone(primaryHct, 90.0)) { cyanea.m3PrimaryFixed = it }
      set(PrefKeys.PREF_M3_ON_PRIMARY_FIXED, tone(primaryHct, 10.0)) { cyanea.m3OnPrimaryFixed = it }
      set(PrefKeys.PREF_M3_PRIMARY_FIXED_DIM, tone(primaryHct, 80.0)) { cyanea.m3PrimaryFixedDim = it }
      set(PrefKeys.PREF_M3_ON_PRIMARY_FIXED_VARIANT, tone(primaryHct, 30.0)) { cyanea.m3OnPrimaryFixedVariant = it }
      set(PrefKeys.PREF_M3_SECONDARY_FIXED, tone(secondaryHct, 90.0)) { cyanea.m3SecondaryFixed = it }
      set(PrefKeys.PREF_M3_ON_SECONDARY_FIXED, tone(secondaryHct, 10.0)) { cyanea.m3OnSecondaryFixed = it }
      set(PrefKeys.PREF_M3_SECONDARY_FIXED_DIM, tone(secondaryHct, 80.0)) { cyanea.m3SecondaryFixedDim = it }
      set(PrefKeys.PREF_M3_ON_SECONDARY_FIXED_VARIANT, tone(secondaryHct, 30.0)) { cyanea.m3OnSecondaryFixedVariant = it }
      set(PrefKeys.PREF_M3_TERTIARY_FIXED, tone(tertiaryHct, 90.0)) { cyanea.m3TertiaryFixed = it }
      set(PrefKeys.PREF_M3_ON_TERTIARY_FIXED, tone(tertiaryHct, 10.0)) { cyanea.m3OnTertiaryFixed = it }
      set(PrefKeys.PREF_M3_TERTIARY_FIXED_DIM, tone(tertiaryHct, 80.0)) { cyanea.m3TertiaryFixedDim = it }
      set(PrefKeys.PREF_M3_ON_TERTIARY_FIXED_VARIANT, tone(tertiaryHct, 30.0)) { cyanea.m3OnTertiaryFixedVariant = it }
      if (dark) {
        set(PrefKeys.PREF_M3_SURFACE_DIM, neutralTone(6.0)) { cyanea.m3SurfaceDim = it }
        set(PrefKeys.PREF_M3_SURFACE_BRIGHT, neutralTone(24.0)) { cyanea.m3SurfaceBright = it }
      } else {
        set(PrefKeys.PREF_M3_SURFACE_DIM, neutralTone(87.0)) { cyanea.m3SurfaceDim = it }
        set(PrefKeys.PREF_M3_SURFACE_BRIGHT, neutralTone(98.0)) { cyanea.m3SurfaceBright = it }
      }

      cyanea.m3Seed = seed
      editor.putInt(PrefKeys.PREF_M3_SEED, seed)

      // Keep the legacy 2-color Theme.Cyanea.* attrs in sync with the generated M3 scheme.
      primary(scheme.primary)
      accent(scheme.secondary)
      background(scheme.background)

      return this
    }

    /** Small helper: persist [color] under [key] and apply it to the live [Cyanea] instance via [assign]. */
    private inline fun set(key: String, @ColorInt color: Int, assign: (Int) -> Unit): Editor {
      assign(color)
      editor.putInt(key, color)
      return this
    }

    /** Set the background color for a [LIGHT] theme using a literal (hardcoded) color integer. */
    fun backgroundLight(@ColorInt color: Int): Editor {
      cyanea.backgroundLight = color
      editor.putInt(PREF_BACKGROUND_LIGHT, color)
      return this
    }

    /** Set the background dark color for a [LIGHT] theme using a literal (hardcoded) color integer. */
    fun backgroundLightDarker(@ColorInt color: Int): Editor {
      cyanea.backgroundDarkDarker = color
      editor.putInt(PREF_BACKGROUND_LIGHT_DARKER, color)
      return this
    }

    /** Set the background light color for a [LIGHT] theme using a literal (hardcoded) color integer. */
    fun backgroundLightLighter(@ColorInt color: Int): Editor {
      cyanea.backgroundLightLighter = color
      editor.putInt(PREF_BACKGROUND_LIGHT_LIGHTER, color)
      return this
    }

    /** Set the background color for a [DARK] theme using a literal (hardcoded) color integer. */
    fun backgroundDark(@ColorInt color: Int): Editor {
      cyanea.backgroundDark = color
      editor.putInt(PREF_BACKGROUND_DARK, color)
      return this
    }

    /** Set the background dark color for a [DARK] theme using a literal (hardcoded) color integer. */
    fun backgroundDarkDarker(@ColorInt color: Int): Editor {
      cyanea.backgroundDarkDarker = color
      editor.putInt(PREF_BACKGROUND_DARK_DARKER, color)
      return this
    }

    /** Set the background light color for a [DARK] theme using a literal (hardcoded) color integer. */
    fun backgroundDarkLighter(@ColorInt color: Int): Editor {
      cyanea.backgroundDarkLighter = color
      editor.putInt(PREF_BACKGROUND_DARK_LIGHTER, color)
      return this
    }

    /** Set the [menuIconColor] using a literal (hardcoded) color integer */
    fun menuIconColor(@ColorInt color: Int): Editor {
      cyanea.menuIconColor = color
      editor.putInt(PREF_MENU_ICON_COLOR, color)
      return this
    }

    /** Set the [subMenuIconColor] using a literal (hardcoded) color integer */
    fun subMenuIconColor(@ColorInt color: Int): Editor {
      cyanea.subMenuIconColor = color
      editor.putInt(PREF_SUB_MENU_ICON_COLOR, color)
      return this
    }

    /** Set the [navigationBar] color using a literal (hardcoded) color integer */
    fun navigationBar(@ColorInt color: Int): Editor {
      cyanea.navigationBar = color
      editor.putInt(PREF_NAVIGATION_BAR, color)
      return this
    }

    /** Set whether or not to tint the system status bar */
    fun shouldTintStatusBar(choice: Boolean): Editor {
      cyanea.shouldTintStatusBar = choice
      editor.putBoolean(PREF_SHOULD_TINT_STATUS_BAR, choice)
      return this
    }

    /** Set whether or not to tint the system navigation bar */
    fun shouldTintNavBar(choice: Boolean): Editor {
      cyanea.shouldTintNavBar = choice
      editor.putBoolean(PREF_SHOULD_TINT_NAV_BAR, choice)
      return this
    }

    /** Set the base theme. Either [LIGHT] or [DARK]. This should correlate with the [backgroundColor] */
    fun baseTheme(theme: BaseTheme): Editor {
      cyanea.baseTheme = theme
      editor.putString(PREF_BASE_THEME, theme.name)
      return this
    }

    /**
     * Apply preferences to the editor. For theme changes to be applied you must recreate the activity.
     */
    fun apply(): Recreator {
      cyanea.timestamp = System.currentTimeMillis()
      editor.putLong(PREF_TIMESTAMP, cyanea.timestamp)
      editor.apply()
      return Recreator()
    }
  }

  /**
   * Helper to recreate a modified themed activity
   */
  class Recreator {

    /**
     * Recreate the current activity
     *
     * @param activity The current activity
     * @param delay The delay in milliseconds until the activity is recreated
     * @param smooth True to use a fade-in/fade-out animation when re-creating.
     * Use with caution, this will create a new instance of the activity.
     */
    @JvmOverloads
    fun recreate(activity: Activity, delay: Long = DEFAULT_DELAY, smooth: Boolean = false) {
      Handler().postDelayed({
        activity.run {
          if (smooth) {
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
          } else {
            recreate()
          }
        }
      }, delay)
    }

    companion object {
      private const val DEFAULT_DELAY = 200L
    }
  }

  /**
   * Callback when a theme has been modified and the [Activity] has been recreated.
   */
  interface ThemeModifiedListener {

    /**
     * Called in [onResume][Activity.onResume] of an [Activity] when the theme has been modified.
     */
    fun onThemeModified()
  }

  @Keep
  enum class BaseTheme {
    LIGHT,
    DARK
  }
}
