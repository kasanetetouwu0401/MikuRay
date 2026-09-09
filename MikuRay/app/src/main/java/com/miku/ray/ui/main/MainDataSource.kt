package com.miku.ray.ui.main

import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.dto.SubscriptionUpdateResult
import com.miku.ray.dto.entities.SubscriptionCache
import kotlinx.coroutines.flow.SharedFlow

interface MainDataSource : AutoCloseable {
    val mainServiceEvent: SharedFlow<MainServiceEvent>

    fun getSelectedSubscriptionId(): String
    fun updateConfigViaSubAll(): SubscriptionUpdateResult
    fun updateConfigViaSub(subscriptionCache: SubscriptionCache): SubscriptionUpdateResult
    fun shareNonCustomConfigsToClipboard(guids: List<String>): Int
    fun sendServiceCommand(command: Int, content: String = "")
    fun sendTestService(message: TestServiceMessage)
    fun sendCountryCodeTestService(message: CountryCodeTestMessage)
    fun testCurrentServerRealPing()
}
