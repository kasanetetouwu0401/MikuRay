package com.miku.ray.ui.main

import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.entities.SubscriptionCache
import kotlinx.coroutines.flow.SharedFlow

interface MainDataSource : AutoCloseable {
    val mainServiceEvent: SharedFlow<MainServiceEvent>

    fun getSelectedSubscriptionId(): String
    fun updateConfigViaSubAll(): SubscriptionUpdateResult
    fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult
    fun shareNonCustomConfigsToClipboard(guids: List<String>): Int

    fun connect()
    fun resyncState()
    fun startRealPingTest(msg: TestServiceMessage)
    fun cancelRealPingTestService(testId: String)
    fun startCountryCodeTest(msg: CountryCodeTestMessage)
    fun cancelCountryCodeTestService()
    fun testCurrentServerRealPing()
    fun fetchCurrentIp()
}
