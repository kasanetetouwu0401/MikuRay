package com.miku.ray.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.miku.ray.AppConfig
import com.miku.ray.dto.CountryCodeTestMessage
import com.miku.ray.dto.SubscriptionUpdateMessage
import com.miku.ray.dto.TestServiceMessage
import com.miku.ray.service.CoreTestService
import com.miku.ray.service.CountryCodeTestService
import com.miku.ray.service.SubscriptionUpdateService

/**
 * Starts the worker services with explicit component intents (the old msg2
 * broadcast protocol is fully retired). Results flow back over AIDL callbacks.
 */
object ServiceCommands {

    fun startTestService(ctx: Context, message: TestServiceMessage) {
        try {
            val intent = Intent()
            intent.component = ComponentName(ctx, CoreTestService::class.java)
            intent.action = AppConfig.ACTION_TEST_START
            intent.putExtra("content", message)
            when (message.key) {
                AppConfig.MSG_MEASURE_CONFIG_START,
                AppConfig.MSG_MEASURE_CONFIG_CANCEL
                -> startAsForeground(ctx, intent)

                else -> ctx.startService(intent)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send command to test service", e)
        }
    }

    fun cancelTestService(ctx: Context, testId: String) {
        startTestService(
            ctx,
            TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_CANCEL, testId = testId)
        )
    }

    fun startCountryCodeService(ctx: Context, message: CountryCodeTestMessage) {
        try {
            val intent = Intent(ctx, CountryCodeTestService::class.java)
            .setAction(AppConfig.ACTION_COUNTRY_START)
            .putExtra("content", message)
            when (message.key) {
                AppConfig.MSG_COUNTRY_CODE_START -> startAsForeground(ctx, intent)
                AppConfig.MSG_COUNTRY_CODE_CANCEL -> ctx.stopService(intent)
                else -> ctx.startService(intent)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send command to country code service", e)
        }
    }

    fun cancelCountryCodeService(ctx: Context) {
        startCountryCodeService(ctx, CountryCodeTestMessage(key = AppConfig.MSG_COUNTRY_CODE_CANCEL))
    }

    fun startSubscriptionService(ctx: Context, message: SubscriptionUpdateMessage) {
        try {
            val intent = Intent()
            intent.component = ComponentName(ctx, SubscriptionUpdateService::class.java)
            intent.action = AppConfig.ACTION_SUB_START
            intent.putExtra("content", message)
            when (message.key) {
                AppConfig.MSG_SUB_UPDATE_START -> startAsForeground(ctx, intent)
                AppConfig.MSG_SUB_UPDATE_CANCEL -> ctx.stopService(intent)
                else -> ctx.startService(intent)
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to send command to subscription update service", e)
        }
    }

    private fun startAsForeground(ctx: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(ctx, intent)
        } else {
            ctx.startService(intent)
        }
    }
}
