package com.miku.ray.ui.main

import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.entities.SubscriptionCache
import kotlinx.coroutines.flow.SharedFlow

/** A single event coming from any of the AIDL-bound services, key/content mirrors AppConfig.MSG_*. */
data class RawServiceEvent(val key: Int, val content: String?)

interface MainDataSource : AutoCloseable {
    val mainServiceEvent: SharedFlow<MainServiceEvent>

    /** Every event from the core service + the test/country-code job services, replacing the old
     *  BROADCAST_ACTION_ACTIVITY receiver in MainViewModel. */
    val rawServiceEvents: SharedFlow<RawServiceEvent>

    fun getSelectedSubscriptionId(): String
    fun updateConfigViaSubAll(): SubscriptionUpdateResult
    fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult
    fun shareNonCustomConfigsToClipboard(guids: List<String>): Int

    /** Re-reads the current core state and re-emits it, replacing the old
     *  sendMsg2Service(MSG_REGISTER_CLIENT, ...) resync trick. */
    fun resyncState()

    fun requestMeasureDelay()
    fun requestMeasureIp()

    suspend fun startCoreTest(msg: TestServiceMessage)
    suspend fun cancelCoreTest(msg: TestServiceMessage)
    suspend fun startCountryCodeTest(msg: CountryCodeTestMessage)
    suspend fun cancelCountryCodeTest(msg: CountryCodeTestMessage)
}
