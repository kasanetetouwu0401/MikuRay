package com.v2ray.ang.util

import android.content.Context
import android.content.res.XmlResourceParser
import com.v2ray.ang.R
import com.v2ray.ang.dto.PreferenceSearchEntry
import com.v2ray.ang.ui.preference.activity.AdvancedSettingsActivity
import com.v2ray.ang.ui.preference.activity.CoreSettingsActivity
import com.v2ray.ang.ui.preference.activity.FragmentSettingsActivity
import com.v2ray.ang.ui.preference.activity.MuxSettingsActivity
import com.v2ray.ang.ui.preference.activity.SettingsActivity
import com.v2ray.ang.ui.preference.activity.UiSettingsActivity
import com.v2ray.ang.ui.preference.activity.VpnSettingsActivity
import org.xmlpull.v1.XmlPullParser

/**
 * Builds a flat, searchable index of every preference declared across the app's
 * preference XML resources by parsing them at runtime via [XmlResourceParser].
 *
 * Because the index is built by walking the actual XML resources (rather than a
 * hand-maintained list), any preference added to one of the pref_*.xml files is
 * automatically picked up by search without further changes.
 *
 * The index is cached in-memory for the lifetime of the process and rebuilt lazily
 * on first access.
 */
object PreferenceSearchIndex {

    /** Maps each preference XML resource to the screen (activity) that hosts it. */
    private val screenSources: List<Pair<Int, Class<out android.app.Activity>>> = listOf(
        R.xml.pref_settings to SettingsActivity::class.java,
        R.xml.pref_ui_settings to UiSettingsActivity::class.java,
        R.xml.pref_vpn_settings to VpnSettingsActivity::class.java,
        R.xml.pref_core_settings to CoreSettingsActivity::class.java,
        R.xml.pref_mux_settings to MuxSettingsActivity::class.java,
        R.xml.pref_fragment_settings to FragmentSettingsActivity::class.java,
        R.xml.pref_advanced_settings to AdvancedSettingsActivity::class.java
    )

    /** Keys that exist purely to navigate to another settings screen; not real preferences. */
    private val navigationOnlyKeys = setOf(
        "pref_navigate_ui_settings",
        "pref_navigate_vpn_settings",
        "pref_navigate_core_settings",
        "pref_navigate_mux_settings",
        "pref_navigate_fragment_settings",
        "pref_navigate_advanced_settings",
        "pref_navigate_per_app_proxy_settings",
        "pref_navigate_check_update"
    )

    @Volatile
    private var cachedEntries: List<PreferenceSearchEntry>? = null

    /** Returns the cached index, building it on first call. */
    fun getEntries(context: Context): List<PreferenceSearchEntry> {
        cachedEntries?.let { return it }
        synchronized(this) {
            cachedEntries?.let { return it }
            val built = buildIndex(context.applicationContext)
            cachedEntries = built
            return built
        }
    }

    /** Forces a rebuild on next access; useful after locale changes. */
    fun invalidate() {
        cachedEntries = null
    }

    private fun buildIndex(context: Context): List<PreferenceSearchEntry> {
        val entries = mutableListOf<PreferenceSearchEntry>()

        for ((xmlRes, activityClass) in screenSources) {
            val screenTitle = resolveScreenTitle(activityClass)
            try {
                entries += parseScreen(context, xmlRes, screenTitle, activityClass)
            } catch (e: Exception) {
                android.util.Log.w("PreferenceSearchIndex", "Failed to parse screen $xmlRes", e)
            }
        }

        return entries
    }

    private fun resolveScreenTitle(activityClass: Class<out android.app.Activity>): Int = when (activityClass) {
        SettingsActivity::class.java -> R.string.title_settings
        UiSettingsActivity::class.java -> R.string.title_ui_settings
        VpnSettingsActivity::class.java -> R.string.title_vpn_settings
        CoreSettingsActivity::class.java -> R.string.title_core_settings
        MuxSettingsActivity::class.java -> R.string.title_mux_settings
        FragmentSettingsActivity::class.java -> R.string.title_fragment_settings
        AdvancedSettingsActivity::class.java -> R.string.title_advanced
        else -> R.string.title_settings
    }

    private fun parseScreen(
        context: Context,
        xmlRes: Int,
        screenTitleRes: Int,
        activityClass: Class<out android.app.Activity>
    ): List<PreferenceSearchEntry> {
        val result = mutableListOf<PreferenceSearchEntry>()
        val screenTitle = context.getString(screenTitleRes)
        val parser: XmlResourceParser = context.resources.getXml(xmlRes)

        var currentCategoryTitle = screenTitle
        var currentCategoryIcon = 0

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    val attrs = readAttributesByLocalName(parser)

                    when {
                        isCategoryTag(tagName) -> {
                            currentCategoryTitle = resolveTitleAttr(context, attrs, fallback = screenTitle)
                            currentCategoryIcon = 0
                        }

                        isPreferenceTag(tagName) -> {
                            val key = attrs["key"]
                            if (!key.isNullOrBlank() && key !in navigationOnlyKeys) {
                                val title = resolveTitleAttr(context, attrs, fallback = key)
                                val summary = resolveSummaryAttr(context, attrs)
                                val iconRes = attrs.resourceId("icon").let { if (it != 0) it else currentCategoryIcon }

                                result += PreferenceSearchEntry(
                                    key = key,
                                    title = title,
                                    summary = summary,
                                    iconRes = iconRes,
                                    categoryTitle = currentCategoryTitle,
                                    screenTitle = screenTitle,
                                    targetActivity = activityClass
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } finally {
            parser.close()
        }

        return result
    }

    /**
     * Snapshot of an element's attributes, keyed by local attribute name
     * (ignoring namespace), since the app uses both android:key/title and
     * app:key/title across its custom preference subclasses.
     */
    private class AttrSnapshot {
        val rawValues = mutableMapOf<String, String>()
        val resourceIds = mutableMapOf<String, Int>()

        operator fun get(name: String): String? = rawValues[name]
        fun resourceId(name: String): Int = resourceIds[name] ?: 0
    }

    private fun readAttributesByLocalName(parser: XmlResourceParser): AttrSnapshot {
        val snapshot = AttrSnapshot()
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            snapshot.rawValues[name] = parser.getAttributeValue(i)
            // getAttributeResourceValue resolves @string/@drawable references; returns 0 for literals.
            snapshot.resourceIds[name] = parser.getAttributeResourceValue(i, 0)
        }
        return snapshot
    }

    private fun resolveTitleAttr(context: Context, attrs: AttrSnapshot, fallback: String): String {
        val res = attrs.resourceId("title")
        if (res != 0) return safeGetString(context, res, fallback)
        return attrs["title"] ?: fallback
    }

    private fun resolveSummaryAttr(context: Context, attrs: AttrSnapshot): String {
        val res = attrs.resourceId("summary")
        if (res != 0) return safeGetString(context, res, "")
        val raw = attrs["summary"].orEmpty()
        return if (raw == "%s") "" else raw
    }

    private fun isCategoryTag(tagName: String): Boolean {
        return tagName.endsWith("PreferenceCategory")
    }

    private fun isPreferenceTag(tagName: String): Boolean {
        // Anything that isn't the root screen or a category is treated as a leaf preference
        // (covers Preference, SwitchPreferenceCompat, ListPreference, EditTextPreference,
        // and the app's custom Preference subclasses like dialogs/banners).
        return tagName != "PreferenceScreen" && !isCategoryTag(tagName)
    }

    private fun safeGetString(context: Context, resId: Int, fallback: String): String {
        return try {
            context.getString(resId)
        } catch (e: Exception) {
            fallback
        }
    }
}
