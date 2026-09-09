package com.miku.ray.core

import android.app.Activity
import android.content.BroadcastReceiver
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
import com.miku.ray.util.LogUtil

object ServiceCommands {

    fun stopCoreService(ctx: Context) {
        try {
            val intent = Intent(AppConfig.ACTION_STOP_SERVICE).setPackage(ctx.packageName)
            ctx.sendBroadcast(intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ServiceCommands: failed to stop core service", e)
        }
    }

    fun restartCoreService(ctx: Context, onResult: (handled: Boolean) -> Unit) {
        val resultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onResult(resultCode == Activity.RESULT_OK)
            }
        }
        try {
            ctx.sendOrderedBroadcast(
                Intent(AppConfig.ACTION_RESTART_SERVICE).setPackage(ctx.packageName),
                null,
                resultReceiver,
                null,
                Activity.RESULT_CANCELED,
                null,
                null,
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ServiceCommands: failed to restart core service", e)
            onResult(false)
        }
    }

    fun startCoreTest(ctx: Context, message: TestServiceMessage) {
        try {
            val intent = Intent(ctx, CoreTestService::class.java).putExtra("content", message)
            ContextCompat.startForegroundService(ctx, intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ServiceCommands: failed to start core test", e)
        }
    }

    fun cancelCoreTest(ctx: Context, message: TestServiceMessage) {
        try {
            val intent = Intent(ctx, CoreTestService::class.java).putExtra("content", message)
            ContextCompat.startForegroundService(ctx, intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ServiceCommands: failed to cancel core test", e)
        }
    }

    fun startCountryCodeTest(ctx: Context, message: CountryCodeTestMessage) {
        try {
            val intent = Intent(ctx, CountryCodeTestService::class.java).putExtra("content", message)
            ContextCompat.startForegroundService(ctx, intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ServiceCommands: failed to start country code test", e)
        }
    }

    fun cancelCountryCodeTest(ctx: Context, message: CountryCodeTestMessage) {
        try {
            val intent = Intent(ctx, CountryCodeTestService::class.java).putExtra("content", message)
            ctx.stopService(intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ServiceCommands: failed to cancel country code test", e)
        }
    }

    fun startSubscriptionUpdate(ctx: Context, message: SubscriptionUpdateMessage) {
        try {
            val intent = Intent()
            intent.component = ComponentName(ctx, SubscriptionUpdateService::class.java)
            intent.putExtra("content", message)
            ContextCompat.startForegroundService(ctx, intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ServiceCommands: failed to start subscription update", e)
        }
    }

    fun cancelSubscriptionUpdate(ctx: Context, message: SubscriptionUpdateMessage) {
        try {
            val intent = Intent()
            intent.component = ComponentName(ctx, SubscriptionUpdateService::class.java)
            intent.putExtra("content", message)
            ctx.stopService(intent)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "ServiceCommands: failed to cancel subscription update", e)
        }
    }
}

/**
 * Same-process signal for a start failure that happens *before* the core service even exists
 * (e.g. invalid config), so there is nothing to bind an AIDL callback to yet. MainRepository
 * subscribes to this right next to its AIDL connections so MainViewModel sees one unified event
 * stream either way.
 */
object PreStartFailureNotifier {
    @Volatile
    var listener: ((message: String) -> Unit)? = null

    fun notify(message: String) {
        listener?.invoke(message)
    }
}
