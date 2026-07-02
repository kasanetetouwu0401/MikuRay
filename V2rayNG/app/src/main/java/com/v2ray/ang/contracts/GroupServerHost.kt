package com.v2ray.ang.contracts

/**
 * Contract implemented by any host Activity that embeds [com.v2ray.ang.ui.GroupServerFragment]
 * (via [com.v2ray.ang.ui.GroupPagerAdapter]).
 *
 * [com.v2ray.ang.ui.MainActivity] implements this normally, but a lightweight overlay
 * Activity (e.g. [com.v2ray.ang.ui.QuickProfileSwitchActivity], used to switch the active
 * profile from the connection notification without opening the full app) can implement it
 * too, so the same fragment/tab UI can be reused outside of MainActivity.
 */
interface GroupServerHost {

    /** Restart the running core with whatever server is currently selected. */
    fun restartV2Ray()

    /** Refresh the tab titles/badges after the server list for a group changed. */
    fun refreshGroupTabTitles(refreshAll: Boolean = false)

    /** Show the share options for a given server config. */
    fun showShareBottomSheet(guid: String, configType: Int)
}
