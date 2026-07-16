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

internal object Constants {
  internal const val NONE_TIMESTAMP = 0L
  internal const val LIGHT_ACTIONBAR_LUMINANCE_FACTOR = 0.75
}

internal object PrefKeys {
  internal const val PREF_FILE_NAME = "com.jaredrummler.cyanea"
  internal const val PREF_BASE_THEME = "base_theme"
  internal const val PREF_PRIMARY = "primary"
  internal const val PREF_PRIMARY_DARK = "primary_dark"
  internal const val PREF_PRIMARY_LIGHT = "primary_light"
  internal const val PREF_ACCENT = "accent"
  internal const val PREF_ACCENT_DARK = "accent_dark"
  internal const val PREF_ACCENT_LIGHT = "accent_light"
  internal const val PREF_BACKGROUND_LIGHT = "background_light"
  internal const val PREF_BACKGROUND_LIGHT_DARKER = "background_light_darker"
  internal const val PREF_BACKGROUND_LIGHT_LIGHTER = "background_light_lighter"
  internal const val PREF_BACKGROUND_DARK = "background_dark"
  internal const val PREF_BACKGROUND_DARK_DARKER = "background_dark_darker"
  internal const val PREF_BACKGROUND_DARK_LIGHTER = "background_dark_lighter"
  internal const val PREF_MENU_ICON_COLOR = "menu_icon_color"
  internal const val PREF_SUB_MENU_ICON_COLOR = "sub_menu_icon_color"
  internal const val PREF_NAVIGATION_BAR = "navigation_bar_color"
  internal const val PREF_SHOULD_TINT_STATUS_BAR = "should_tint_status_bar"
  internal const val PREF_SHOULD_TINT_NAV_BAR = "should_tint_nav_bar"
  internal const val PREF_TIMESTAMP = "timestamp"

  // ------ Material3 color roles ------
  internal const val PREF_M3_PRIMARY = "m3_primary"
  internal const val PREF_M3_ON_PRIMARY = "m3_on_primary"
  internal const val PREF_M3_PRIMARY_CONTAINER = "m3_primary_container"
  internal const val PREF_M3_ON_PRIMARY_CONTAINER = "m3_on_primary_container"
  internal const val PREF_M3_SECONDARY = "m3_secondary"
  internal const val PREF_M3_ON_SECONDARY = "m3_on_secondary"
  internal const val PREF_M3_SECONDARY_CONTAINER = "m3_secondary_container"
  internal const val PREF_M3_ON_SECONDARY_CONTAINER = "m3_on_secondary_container"
  internal const val PREF_M3_TERTIARY = "m3_tertiary"
  internal const val PREF_M3_ON_TERTIARY = "m3_on_tertiary"
  internal const val PREF_M3_TERTIARY_CONTAINER = "m3_tertiary_container"
  internal const val PREF_M3_ON_TERTIARY_CONTAINER = "m3_on_tertiary_container"
  internal const val PREF_M3_ERROR = "m3_error"
  internal const val PREF_M3_ON_ERROR = "m3_on_error"
  internal const val PREF_M3_ERROR_CONTAINER = "m3_error_container"
  internal const val PREF_M3_ON_ERROR_CONTAINER = "m3_on_error_container"
  internal const val PREF_M3_BACKGROUND = "m3_background"
  internal const val PREF_M3_ON_BACKGROUND = "m3_on_background"
  internal const val PREF_M3_SURFACE = "m3_surface"
  internal const val PREF_M3_ON_SURFACE = "m3_on_surface"
  internal const val PREF_M3_SURFACE_VARIANT = "m3_surface_variant"
  internal const val PREF_M3_ON_SURFACE_VARIANT = "m3_on_surface_variant"
  internal const val PREF_M3_OUTLINE = "m3_outline"
  internal const val PREF_M3_OUTLINE_VARIANT = "m3_outline_variant"
  internal const val PREF_M3_SURFACE_CONTAINER_LOWEST = "m3_surface_container_lowest"
  internal const val PREF_M3_SURFACE_CONTAINER_LOW = "m3_surface_container_low"
  internal const val PREF_M3_SURFACE_CONTAINER = "m3_surface_container"
  internal const val PREF_M3_SURFACE_CONTAINER_HIGH = "m3_surface_container_high"
  internal const val PREF_M3_SURFACE_CONTAINER_HIGHEST = "m3_surface_container_highest"
  internal const val PREF_M3_INVERSE_SURFACE = "m3_inverse_surface"
  internal const val PREF_M3_INVERSE_ON_SURFACE = "m3_inverse_on_surface"
  internal const val PREF_M3_INVERSE_PRIMARY = "m3_inverse_primary"
  internal const val PREF_M3_SEED = "m3_seed"
  internal const val PREF_M3_PRIMARY_FIXED = "m3_primary_fixed"
  internal const val PREF_M3_ON_PRIMARY_FIXED = "m3_on_primary_fixed"
  internal const val PREF_M3_PRIMARY_FIXED_DIM = "m3_primary_fixed_dim"
  internal const val PREF_M3_ON_PRIMARY_FIXED_VARIANT = "m3_on_primary_fixed_variant"
  internal const val PREF_M3_SECONDARY_FIXED = "m3_secondary_fixed"
  internal const val PREF_M3_ON_SECONDARY_FIXED = "m3_on_secondary_fixed"
  internal const val PREF_M3_SECONDARY_FIXED_DIM = "m3_secondary_fixed_dim"
  internal const val PREF_M3_ON_SECONDARY_FIXED_VARIANT = "m3_on_secondary_fixed_variant"
  internal const val PREF_M3_TERTIARY_FIXED = "m3_tertiary_fixed"
  internal const val PREF_M3_ON_TERTIARY_FIXED = "m3_on_tertiary_fixed"
  internal const val PREF_M3_TERTIARY_FIXED_DIM = "m3_tertiary_fixed_dim"
  internal const val PREF_M3_ON_TERTIARY_FIXED_VARIANT = "m3_on_tertiary_fixed_variant"
  internal const val PREF_M3_SURFACE_DIM = "m3_surface_dim"
  internal const val PREF_M3_SURFACE_BRIGHT = "m3_surface_bright"
}

internal object Defaults {
  internal const val DEFAULT_DARKER_FACTOR = 0.85f
  internal const val DEFAULT_LIGHTER_FACTOR = 0.15f
}
