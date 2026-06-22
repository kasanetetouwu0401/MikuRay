package com.v2ray.ang.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object WeatherHelper {

    data class WeatherResult(
        val emoji: String,
        val tempCelsius: Int
    ) {
        fun getTemperatureString(celsius: Boolean = isDefaultCelsius()): String =
            if (celsius) "${tempCelsius}°C"
            else "${Math.round(tempCelsius * 9.0 / 5.0 + 32)}°F"
    }

    // ── WMO code → emoji ─────────────────────────────────────────────────────
    private fun emojiForCode(code: Int, isDay: Boolean): String = when (code) {
        0, 1    -> if (isDay) "\u2600" else moonPhaseEmoji()   // ☀ / moon
        2       -> "\u26c5"                                      // ⛅
        3       -> "\u2601"                                      // ☁
        45, 48  -> "\ud83d\ude36\u200d\ud83c\udf2b"            // 😶‍🌫
        51, 53, 55,
        61, 63,
        80, 81  -> "\ud83c\udf26"                               // 🌦
        56, 57,
        65, 66, 67,
        82      -> "\ud83c\udf27"                               // 🌧
        71, 73, 75, 77,
        85, 86  -> "\ud83c\udf28"                               // 🌨
        95      -> "\u26a1"                                      // ⚡
        96, 99  -> "\u26c8"                                     // ⛈
        else    -> if (isDay) "\u2600" else moonPhaseEmoji()
    }

    // OWM icon id → emoji
    private fun emojiForOwmIcon(icon: String?): String = when {
        icon == null            -> "\u2600"
        icon.startsWith("01")   -> if (icon.endsWith("d")) "\u2600" else moonPhaseEmoji() // clear
        icon.startsWith("02")   -> "\u26c5"   // few clouds
        icon.startsWith("03") ||
        icon.startsWith("04")   -> "\u2601"   // scattered/broken clouds
        icon.startsWith("09")   -> "\ud83c\udf27" // shower rain
        icon.startsWith("10")   -> "\ud83c\udf26" // rain
        icon.startsWith("11")   -> "\u26c8"   // thunderstorm
        icon.startsWith("13")   -> "\ud83c\udf28" // snow
        icon.startsWith("50")   -> "\ud83d\ude36\u200d\ud83c\udf2b" // mist
        else                    -> "\u2600"
    }

    // wttr.in condition code → emoji (subset of their weather codes)
    private fun emojiForWttrCode(code: Int): String = when (code) {
        113                     -> "\u2600"          // Sunny/Clear
        116                     -> "\u26c5"          // Partly Cloudy
        119, 122                -> "\u2601"          // Cloudy/Overcast
        143, 248, 260           -> "\ud83d\ude36\u200d\ud83c\udf2b" // Mist/Fog
        176, 263, 266, 293,
        296, 299, 302, 305,
        308, 353, 356, 359      -> "\ud83c\udf26"   // Rain variants
        179, 182, 185, 227,
        230, 281, 284, 323,
        326, 329, 332, 335,
        338, 350, 362, 365,
        368, 371, 374, 377      -> "\ud83c\udf28"   // Snow variants
        200, 386, 389, 392,
        395                     -> "\u26c8"          // Thunderstorm
        else                    -> "\u2600"
    }

    private fun moonPhaseEmoji(): String {
        val newMoonRef = 2451550.1
        val synodicMonth = 29.53058867
        val julianNow = System.currentTimeMillis() / 86400000.0 + 2440588.0 - 0.5
        val phase = ((julianNow - newMoonRef) % synodicMonth + synodicMonth) % synodicMonth
        return when {
            phase < 1.85  -> "\ud83c\udf1a"
            phase < 5.54  -> "\ud83c\udf1b"
            phase < 9.23  -> "\ud83c\udf13"
            phase < 12.92 -> "\ud83c\udf14"
            phase < 16.61 -> "\ud83c\udf1d"
            phase < 20.30 -> "\ud83c\udf16"
            phase < 23.99 -> "\ud83c\udf17"
            phase < 27.68 -> "\ud83c\udf1c"
            else          -> "\ud83c\udf1a"
        }
    }

    // ── Emoji → drawable ─────────────────────────────────────────────────────
    fun iconResForEmoji(emoji: String?): Int {
        if (emoji.isNullOrEmpty()) return R.drawable.ic_weather_sunny
        return when (emoji) {
            "\u2600"                                            -> R.drawable.ic_weather_sunny
            "\u2601"                                            -> R.drawable.ic_cloud
            "\u26c5", "\ud83c\udf24"                          -> R.drawable.ic_cloud
            "\ud83c\udf26", "\ud83c\udf27"                    -> R.drawable.ic_weather_rain
            "\u26a1", "\u26c8"                                 -> R.drawable.ic_weather_storm
            "\u2744", "\ud83c\udf28"                          -> R.drawable.ic_weather_snow
            "\ud83d\ude36\u200d\ud83c\udf2b"                  -> R.drawable.ic_weather_fog
            "\ud83c\udf13", "\ud83c\udf14",
            "\ud83c\udf16", "\ud83c\udf17",
            "\ud83c\udf1a", "\ud83c\udf1b",
            "\ud83c\udf1c", "\ud83c\udf1d"                    -> R.drawable.ic_weather_night
            else                                               -> R.drawable.ic_weather_sunny
        }
    }

    // ── C/F helpers ──────────────────────────────────────────────────────────
    fun isDefaultCelsius(): Boolean {
        val tz = TimeZone.getDefault().id
        return !(tz.startsWith("US/") ||
            tz == "America/Nassau" ||
            tz == "America/Belize" ||
            tz == "America/Cayman" ||
            tz == "Pacific/Palau")
    }

    fun isCelsius(): Boolean {
        val stored = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_USE_CELSIUS, "")
        return if (stored.isNullOrEmpty()) isDefaultCelsius() else stored == "true"
    }

    // ── Permission helpers ────────────────────────────────────────────────────
    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasFineLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocationPermission(context)
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ── MMKV cache ───────────────────────────────────────────────────────────
    fun getCachedWeather(): WeatherResult? {
        val ts = MmkvManager.decodeSettingsLong(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        if (ts == 0L) return null
        if (System.currentTimeMillis() - ts > AppConfig.WEATHER_CACHE_TTL_MS) return null
        return readCacheEntry()
    }

    fun getCachedWeatherStale(): WeatherResult? {
        val ts = MmkvManager.decodeSettingsLong(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        if (ts == 0L) return null
        return readCacheEntry()
    }

    private fun readCacheEntry(): WeatherResult? {
        val temp = MmkvManager.decodeSettingsInt(AppConfig.PREF_WEATHER_CACHE_TEMP, Int.MIN_VALUE)
        if (temp == Int.MIN_VALUE) return null
        val emoji = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_CACHE_EMOJI, "") ?: ""
        return WeatherResult(emoji = emoji, tempCelsius = temp)
    }

    private fun saveCache(result: WeatherResult) {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TEMP, result.tempCelsius)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_EMOJI, result.emoji)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, System.currentTimeMillis())
    }

    fun clearCache() {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
    }

    // ── HTTP client ───────────────────────────────────────────────────────────
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // ── Location ──────────────────────────────────────────────────────────────
    private suspend fun getCurrentLocation(
        context: Context,
        force: Boolean = false
    ): android.location.Location? {
        if (!hasLocationPermission(context)) return null
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (force || hasFineLocationPermission(context))
            Priority.PRIORITY_HIGH_ACCURACY
        else
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        if (force) runCatching { fusedClient.flushLocations() }
        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cts.cancel() }
            try {
                fusedClient.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (e: SecurityException) {
                cont.resume(null)
            }
        }
    }

    // ── Fetch — dispatch ke API yang dipilih ──────────────────────────────────
    suspend fun fetchCurrentWeather(context: Context, force: Boolean = false): WeatherResult? =
        withContext(Dispatchers.IO) {
            val location = getCurrentLocation(context, force) ?: return@withContext null
            val api = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_API, "")
                .takeIf { !it.isNullOrEmpty() } ?: AppConfig.WEATHER_API_DEFAULT
            try {
                when (api) {
                    AppConfig.WEATHER_API_WTTR  -> fetchWttr(location)
                    AppConfig.WEATHER_API_OWM   -> fetchOwm(location)
                    else                         -> fetchOpenMeteo(location)
                }?.also { saveCache(it) }
            } catch (e: Exception) {
                LogUtil.e("WeatherHelper", "fetchCurrentWeather[$api] failed: ${e.message}")
                null
            }
        }

    // open-meteo ──────────────────────────────────────────────────────────────
    private fun fetchOpenMeteo(location: android.location.Location): WeatherResult? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${location.latitude}" +
            "&longitude=${location.longitude}" +
            "&current=temperature_2m,weather_code,is_day"
        val body = getBody(url) ?: return null
        val json = JsonUtil.parseString(body) ?: return null
        val current = json.getAsJsonObject("current") ?: return null
        val temp = current.get("temperature_2m")?.asDouble ?: return null
        val code = current.get("weather_code")?.asInt ?: 0
        val isDay = (current.get("is_day")?.asInt ?: 1) == 1
        return WeatherResult(
            emoji = emojiForCode(code, isDay),
            tempCelsius = Math.round(temp).toInt()
        )
    }

    // wttr.in ─────────────────────────────────────────────────────────────────
    private fun fetchWttr(location: android.location.Location): WeatherResult? {
        val url = "https://wttr.in/${location.latitude},${location.longitude}" +
            "?format=j1"
        val body = getBody(url) ?: return null
        val json = JsonUtil.parseString(body) ?: return null
        val current = json.getAsJsonArray("current_condition")
            ?.get(0)?.asJsonObject ?: return null
        val tempC = current.get("temp_C")?.asInt ?: return null
        val code = current.get("weatherCode")?.asInt ?: 113
        return WeatherResult(
            emoji = emojiForWttrCode(code),
            tempCelsius = tempC
        )
    }

    // OpenWeatherMap ──────────────────────────────────────────────────────────
    private fun fetchOwm(location: android.location.Location): WeatherResult? {
        val apiKey = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_OWM_KEY, "")
            ?.takeIf { it.isNotBlank() }
            ?: com.v2ray.ang.BuildConfig.OWM_API_KEY.takeIf { it.isNotBlank() }
        if (apiKey == null) {
            LogUtil.w("WeatherHelper", "OWM API key not set")
            return null
        }
        val url = "https://api.openweathermap.org/data/2.5/weather" +
            "?lat=${location.latitude}" +
            "&lon=${location.longitude}" +
            "&units=metric" +
            "&appid=$apiKey"
        val body = getBody(url) ?: return null
        val json = JsonUtil.parseString(body) ?: return null
        val temp = json.getAsJsonObject("main")?.get("temp")?.asDouble ?: return null
        val icon = json.getAsJsonArray("weather")
            ?.get(0)?.asJsonObject?.get("icon")?.asString
        return WeatherResult(
            emoji = emojiForOwmIcon(icon),
            tempCelsius = Math.round(temp).toInt()
        )
    }

    private fun getBody(url: String): String? {
        val req = Request.Builder().url(url).build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }

    // ── WorkManager ───────────────────────────────────────────────────────────
    fun scheduleBackgroundUpdates(context: Context, forceReschedule: Boolean = false) {
        if (!hasLocationPermission(context)) return
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(
            AppConfig.WEATHER_UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .addTag(AppConfig.WEATHER_UPDATE_TASK_NAME)
            .build()
        val policy = if (forceReschedule) ExistingPeriodicWorkPolicy.REPLACE
        else ExistingPeriodicWorkPolicy.KEEP
        RemoteWorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(AppConfig.WEATHER_UPDATE_TASK_NAME, policy, request)
    }

    fun cancelBackgroundUpdates(context: Context) {
        RemoteWorkManager.getInstance(context).cancelUniqueWork(AppConfig.WEATHER_UPDATE_TASK_NAME)
    }

    class UpdateWorker(context: Context, params: WorkerParameters) :
        CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SHOW_WEATHER_CHIP, false))
                return Result.success()
            if (!hasBackgroundLocationPermission(applicationContext))
                return Result.success()
            val result = fetchCurrentWeather(applicationContext)
            return if (result != null) Result.success() else Result.retry()
        }
    }
}
