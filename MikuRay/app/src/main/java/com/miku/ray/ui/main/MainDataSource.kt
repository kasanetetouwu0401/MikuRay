package com.miku.ray.ui.main

import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.GroupMapItem
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.entities.ProfileItem
import com.miku.ray.dto.entities.ServerAffiliationInfo
import com.miku.ray.dto.entities.SubscriptionCache
import com.miku.ray.dto.entities.SubscriptionItem
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

interface MainDataSource : Closeable {
    val mainServiceEvent: Flow<MainServiceEvent>

    fun getSelectedSubscriptionId(): String
    fun setSelectedSubscriptionId(id: String)

    fun getSelectServer(): String?
    fun setSelectServer(guid: String)

    fun getString(resId: Int): String
    fun getString(resId: Int, vararg formatArgs: Any): String

    fun isGroupAllDisplayEnabled(): Boolean
    fun getGroups(): List<GroupMapItem>
    fun getSubscriptions(): List<SubscriptionCache>
    fun getSubscriptionItem(id: String): SubscriptionItem?
    fun getSubsList(): List<String>

    fun getServerGuidList(groupId: String): List<String>
    fun decodeServerConfig(guid: String): ProfileItem?
    fun decodeAffiliationInfo(guid: String): ServerAffiliationInfo?
    fun decodePinnedServers(): List<String>
    fun togglePinnedServer(guid: String): Boolean

    fun getSortOrder(groupId: String): Int
    fun restoreOriginServerListIfNeeded(groupId: String)

    fun encodeServerList(guids: List<String>, groupId: String)
    fun removeServer(guid: String)
    fun removeAllServer(): Int
    fun removeInvalidServerByGuid(guid: String): Int
    fun swapServer(fromGuid: String, toGuid: String, groupId: String, order: List<String>)

    fun clearAllTestDelayResults(guids: List<String>)
    fun clearAllCountryCodes(guids: List<String>)
    fun sortByTestResultsForSub(subId: String)

    /** Snapshots the pre-sort order and switches the group's sort mode to "by delay". */
    fun prepareGroupForAutoSortByDelay(subId: String)

    fun resetProfileTraffic(guid: String)
    fun resetGroupTraffic(groupId: String)
    fun resetAllTraffic()

    fun markConnectionStopped()
    fun isAutoRemoveInvalidAfterTest(): Boolean
    fun isAutoSortAfterTest(): Boolean

    fun updateConfigViaSubAll(): SubscriptionUpdateResult
    fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult
    fun shareNonCustomConfigsToClipboard(guids: List<String>): Int

    fun sendMsg2Service(msgId: Int, content: String)
    fun sendMsg2TestService(msg: TestServiceMessage)
    fun sendMsg2CountryCodeTestService(msg: CountryCodeTestMessage)

    fun registerClient()
    fun initAssets()
}
