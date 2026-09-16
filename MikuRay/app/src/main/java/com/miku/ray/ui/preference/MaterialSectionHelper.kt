package com.miku.ray.ui.preference

import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import com.miku.ray.AppConfig
import com.miku.ray.R
import com.miku.ray.handler.MmkvManager

/**
 * Applies the "Material Sections" + "Spacing" settings (see pref_material_containers block
 * in pref_ui_settings.xml) to every preference screen that already uses MikuRay's uwu_top /
 * uwu_mid / uwu_bot / uwu_sin card layouts.
 *
 * - Material Sections ON (default): preferences inside the same category are visually merged
 *   into one rounded card (first = top corners, last = bottom corners, everything in between
 *   flat), exactly like today.
 * - Material Sections OFF: every preference becomes its own fully-rounded standalone card
 *   (uwu_sin), same look as a single-item section.
 * - Spacing controls how much room sits between cards: Normal (current values), Big, Huge.
 *
 * Only preferences whose declared app:layout already points at one of the uwu_* card layouts
 * are touched - anything else (banners, custom dialogs, etc.) is left completely alone. This
 * mirrors CategoryStyleHelper, and should be called right alongside it.
 */
object MaterialSectionHelper {

    private const val SPACING_NORMAL = "normal"
    private const val SPACING_BIG = "big"
    private const val SPACING_HUGE = "huge"

    fun sectionsEnabled(): Boolean =
        MmkvManager.decodeSettingsBool(AppConfig.PREF_MATERIAL_SECTIONS, true)

    fun currentSpacing(): String =
        MmkvManager.decodeSettingsString(AppConfig.PREF_CARD_SPACING, SPACING_NORMAL) ?: SPACING_NORMAL

    fun applyToFragment(fragment: PreferenceFragmentCompat) {
        fragment.preferenceScreen?.let { applyToGroup(it) }
    }

    fun applyToGroup(root: PreferenceGroup) {
        val sectionsOn = sectionsEnabled()
        val spacing = currentSpacing()
        walk(root, sectionsOn, spacing)
    }

    /**
     * Static-layout counterpart of [applyToFragment], for the bottom sheet menus (More Menu,
     * Sort Sub, Asset Menu, Routing Menu, Share Config, Share Sub, Sub Group Options) whose
     * uwu_top/mid/bot/single cards are inflated once as fixed XML rows rather than bound
     * through the Preference framework.
     *
     * Call this once, at the end of onViewCreated(), AFTER any code that toggles a card's
     * visibility - it only re-shapes/re-spaces cards that are currently View.VISIBLE, so a
     * hidden item never leaves a broken corner behind, and the group is treated as a single
     * standalone card automatically when only one item ends up visible.
     *
     * [cardIds] must list the section's card view ids in their on-screen top-to-bottom order.
     */
    fun applyToCards(root: View, cardIds: List<Int>) {
        val sectionsOn = sectionsEnabled()
        val spacing = currentSpacing()
        val cards = cardIds.mapNotNull { root.findViewById<MaterialCardView>(it) }
            .filter { it.visibility == View.VISIBLE }
        if (cards.isEmpty()) return

        val outerGap = dpToPx(root, outerGapDp(spacing))
        val innerGap = dpToPx(root, innerGapDp(spacing))
        val lastIndex = cards.lastIndex

        cards.forEachIndexed { index, card ->
            val shapeStyle = when {
                !sectionsOn || cards.size == 1 -> R.style.ShapeAppearance_App_CardView_Single
                index == 0 -> R.style.ShapeAppearance_App_CardView_Top
                index == lastIndex -> R.style.ShapeAppearance_App_CardView_Bottom
                else -> R.style.ShapeAppearance_App_CardView_Middle
            }
            card.shapeAppearanceModel = ShapeAppearanceModel.builder(card.context, shapeStyle, 0).build()

            (card.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = if (!sectionsOn || cards.size == 1 || index == 0) outerGap else 0
                lp.bottomMargin = if (!sectionsOn || cards.size == 1 || index == lastIndex) outerGap else innerGap
                card.layoutParams = lp
            }
        }
    }

    private fun outerGapDp(spacing: String) = when (spacing) {
        SPACING_BIG -> 14
        SPACING_HUGE -> 18
        else -> 10
    }

    private fun innerGapDp(spacing: String) = when (spacing) {
        SPACING_BIG -> 8
        SPACING_HUGE -> 12
        else -> 4
    }

    private fun dpToPx(view: View, dp: Int): Int =
        (dp * view.resources.displayMetrics.density).toInt()

    /**
     * Special-cased for uwu_banner_theme.xml (the "check for update" banner at the top of UI
     * Settings): unlike the uwu_top/mid/bot/sin system, its two inner pieces are plain
     * [ClipRoundedCardView]s with a single uniform corner radius each (no per-corner
     * shapeAppearanceOverlay), clipped together by one shared outer rounded container. So
     * instead of Top/Middle/Bottom shapes, this only has two states:
     *
     * - Material Sections ON (default): the two pieces stay visually merged - small inner
     *   radius, gap between them follows the "inner" spacing value (4/8/12dp).
     * - Material Sections OFF: both pieces become their own fully-rounded standalone card
     *   (matching the outer 28dp radius), gap between them widens to the "outer" spacing
     *   value (10/14/18dp), same as any other standalone card elsewhere in the app.
     */
    fun applyToBannerTheme(root: View) {
        val infoCard = root.findViewById<MaterialCardView>(R.id.theme_banner_info_card) ?: return
        val updateCard = root.findViewById<MaterialCardView>(R.id.onClick) ?: return

        val sectionsOn = sectionsEnabled()
        val spacing = currentSpacing()

        val innerRadiusPx = dpToPx(root, 5).toFloat()
        val outerRadiusPx = dpToPx(root, 28).toFloat()
        val radiusPx = if (sectionsOn) innerRadiusPx else outerRadiusPx
        infoCard.radius = radiusPx
        updateCard.radius = radiusPx

        val gapDp = if (sectionsOn) innerGapDp(spacing) else outerGapDp(spacing)
        (updateCard.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.topMargin = dpToPx(root, gapDp)
            updateCard.layoutParams = lp
        }
    }

    private fun walk(group: PreferenceGroup, sectionsOn: Boolean, spacing: String) {
        val cards = (0 until group.preferenceCount)
            .map { group.getPreference(it) }
            .filter { it !is PreferenceGroup && it.isVisible && it.isSectionCard() }

        val lastIndex = cards.lastIndex
        cards.forEachIndexed { index, pref ->
            val isSwitch = pref.layoutResource in SWITCH_LAYOUT_IDS
            val newLayout = when {
                !sectionsOn || cards.size == 1 -> sinLayout(isSwitch, spacing)
                index == 0 -> topLayout(isSwitch, spacing)
                index == lastIndex -> botLayout(isSwitch, spacing)
                else -> midLayout(isSwitch, spacing)
            }
            if (pref.layoutResource != newLayout) pref.layoutResource = newLayout
        }

        for (i in 0 until group.preferenceCount) {
            val child = group.getPreference(i)
            if (child is PreferenceGroup) walk(child, sectionsOn, spacing)
        }
    }

    private fun Preference.isSectionCard(): Boolean = layoutResource in GROUPED_LAYOUT_IDS

    private fun topLayout(isSwitch: Boolean, spacing: String) = pick(
        isSwitch, spacing,
        R.layout.uwu_top, R.layout.uwu_top_big, R.layout.uwu_top_huge,
        R.layout.uwu_top_switch, R.layout.uwu_top_switch_big, R.layout.uwu_top_switch_huge
    )

    private fun midLayout(isSwitch: Boolean, spacing: String) = pick(
        isSwitch, spacing,
        R.layout.uwu_mid, R.layout.uwu_mid_big, R.layout.uwu_mid_huge,
        R.layout.uwu_mid_switch, R.layout.uwu_mid_switch_big, R.layout.uwu_mid_switch_huge
    )

    private fun botLayout(isSwitch: Boolean, spacing: String) = pick(
        isSwitch, spacing,
        R.layout.uwu_bot, R.layout.uwu_bot_big, R.layout.uwu_bot_huge,
        R.layout.uwu_bot_switch, R.layout.uwu_bot_switch_big, R.layout.uwu_bot_switch_huge
    )

    private fun sinLayout(isSwitch: Boolean, spacing: String) = pick(
        isSwitch, spacing,
        R.layout.uwu_sin, R.layout.uwu_sin_big, R.layout.uwu_sin_huge,
        R.layout.uwu_sin_switch, R.layout.uwu_sin_switch_big, R.layout.uwu_sin_switch_huge
    )

    private fun pick(
        isSwitch: Boolean,
        spacing: String,
        normal: Int, big: Int, huge: Int,
        normalSwitch: Int, bigSwitch: Int, hugeSwitch: Int
    ): Int {
        val (n, b, h) = if (isSwitch) Triple(normalSwitch, bigSwitch, hugeSwitch) else Triple(normal, big, huge)
        return when (spacing) {
            SPACING_BIG -> b
            SPACING_HUGE -> h
            else -> n
        }
    }

    private val GROUPED_LAYOUT_IDS: Set<Int> by lazy {
        setOf(
            R.layout.uwu_top, R.layout.uwu_top_big, R.layout.uwu_top_huge,
            R.layout.uwu_top_switch, R.layout.uwu_top_switch_big, R.layout.uwu_top_switch_huge,
            R.layout.uwu_mid, R.layout.uwu_mid_big, R.layout.uwu_mid_huge,
            R.layout.uwu_mid_switch, R.layout.uwu_mid_switch_big, R.layout.uwu_mid_switch_huge,
            R.layout.uwu_bot, R.layout.uwu_bot_big, R.layout.uwu_bot_huge,
            R.layout.uwu_bot_switch, R.layout.uwu_bot_switch_big, R.layout.uwu_bot_switch_huge,
            R.layout.uwu_sin, R.layout.uwu_sin_big, R.layout.uwu_sin_huge,
            R.layout.uwu_sin_switch, R.layout.uwu_sin_switch_big, R.layout.uwu_sin_switch_huge
        )
    }

    private val SWITCH_LAYOUT_IDS: Set<Int> by lazy {
        setOf(
            R.layout.uwu_top_switch, R.layout.uwu_top_switch_big, R.layout.uwu_top_switch_huge,
            R.layout.uwu_mid_switch, R.layout.uwu_mid_switch_big, R.layout.uwu_mid_switch_huge,
            R.layout.uwu_bot_switch, R.layout.uwu_bot_switch_big, R.layout.uwu_bot_switch_huge,
            R.layout.uwu_sin_switch, R.layout.uwu_sin_switch_big, R.layout.uwu_sin_switch_huge
        )
    }
}
