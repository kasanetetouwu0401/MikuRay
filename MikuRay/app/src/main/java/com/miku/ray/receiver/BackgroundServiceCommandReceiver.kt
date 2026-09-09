package com.miku.ray.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miku.ray.aidl.AidlProtocol
import com.miku.ray.aidl.AidlServiceClient
import com.miku.ray.service.CoreTestService
import com.miku.ray.service.CountryCodeTestService
import com.miku.ray.service.SubscriptionUpdateService

class BackgroundServiceCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_TEST_CANCEL -> AidlServiceClient.commandIfRunning(
                context, CoreTestService::class.java, AidlProtocol.TEST_CANCEL,
            )
            ACTION_COUNTRY_CANCEL -> AidlServiceClient.commandIfRunning(
                context, CountryCodeTestService::class.java, AidlProtocol.COUNTRY_CANCEL,
            )
            ACTION_SUBSCRIPTION_CANCEL -> AidlServiceClient.commandIfRunning(
                context, SubscriptionUpdateService::class.java, AidlProtocol.SUBSCRIPTION_CANCEL,
            )
        }
    }

    companion object {
        const val ACTION_TEST_CANCEL = "com.miku.ray.action.TEST_CANCEL"
        const val ACTION_COUNTRY_CANCEL = "com.miku.ray.action.COUNTRY_CANCEL"
        const val ACTION_SUBSCRIPTION_CANCEL = "com.miku.ray.action.SUBSCRIPTION_CANCEL"
    }
}
