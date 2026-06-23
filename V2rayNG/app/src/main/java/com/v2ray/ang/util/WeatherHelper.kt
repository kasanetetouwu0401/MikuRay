package com.v2ray.ang.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    fun getCacheAgeMs(): Long {
        val ts = MmkvManager.decodeSettingsLong(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        if (ts == 0L) return -1L
        return System.currentTimeMillis() - ts
    }

    private fun isCacheValidForLocation(location: android.location.Location): Boolean {
        val cachedLat = MmkvManager.decodeSettingsFloat(AppConfig.PREF_WEATHER_CACHE_LAT, 0f)
        val cachedLon = MmkvManager.decodeSettingsFloat(AppConfig.PREF_WEATHER_CACHE_LON, 0f)
        if (cachedLat == 0f && cachedLon == 0f) return false  // belum pernah simpan koordinat
        val results = FloatArray(1)
        android.location.Location.distanceBetween(cachedLat.toDouble(), cachedLon.toDouble(),
            location.latitude, location.longitude, results)
        val moved = results[0]
        if (moved > AppConfig.WEATHER_LOCATION_STALE_METERS) {
            LogUtil.d("WeatherHelper", "Location moved ${moved.toInt()}m > threshold, cache invalid")
            return false
        }
        return true
    }

    private fun readCacheEntry(): WeatherResult? {
        val temp = MmkvManager.decodeSettingsInt(AppConfig.PREF_WEATHER_CACHE_TEMP, Int.MIN_VALUE)
        if (temp == Int.MIN_VALUE) return null
        val emoji = MmkvManager.decodeSettingsString(AppConfig.PREF_WEATHER_CACHE_EMOJI, "") ?: ""
        return WeatherResult(emoji = emoji, tempCelsius = temp)
    }

    private fun saveCache(result: WeatherResult, location: android.location.Location? = null) {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TEMP, result.tempCelsius)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_EMOJI, result.emoji)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, System.currentTimeMillis())
        if (location != null) {
            MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LAT, location.latitude.toFloat())
            MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LON, location.longitude.toFloat())
        }
    }

    fun clearCache() {
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TIMESTAMP, 0L)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_TEMP, Int.MIN_VALUE)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_EMOJI, "")
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LAT, 0f)
        MmkvManager.encodeSettings(AppConfig.PREF_WEATHER_CACHE_LON, 0f)
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)   // turunkan dari 15s supaya cepat fail
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private fun findNonVpnNetwork(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val networks = cm.allNetworks
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) continue
            return network
        }
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return network
        }
        return null
    }

    private suspend fun getCurrentLocation(
        context: Context,
        force: Boolean = false
    ): android.location.Location? {
        if (!hasLocationPermission(context)) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) return null

        val useFine = force || hasFineLocationPermission(context)
        val provider = if (useFine && isGpsEnabled) {
            LocationManager.GPS_PROVIDER
        } else if (isNetworkEnabled) {
            LocationManager.NETWORK_PROVIDER
        } else {
            LocationManager.GPS_PROVIDER
        }

        if (!force) {
            try {
                val lastKnown = locationManager.getLastKnownLocation(provider)
                if (lastKnown != null) {
                    LogUtil.d("WeatherHelper", "Using last known location (age=${System.currentTimeMillis() - lastKnown.time}ms)")
                    return lastKnown
                }
                val fallbackProvider = if (provider == LocationManager.GPS_PROVIDER)
                    LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
                if (locationManager.isProviderEnabled(fallbackProvider)) {
                    val fallback = locationManager.getLastKnownLocation(fallbackProvider)
                    if (fallback != null) return fallback
                }
            } catch (e: SecurityException) {
                LogUtil.w("WeatherHelper", "getLastKnownLocation SecurityException: ${e.message}")
            }
        }

        val vpnRunning = try {
            com.v2ray.ang.core.CoreServiceManager.isRunning()
        } catch (e: Exception) {
            false
        }
        LogUtil.d("WeatherHelper", "Requesting fresh fix via $provider (vpnRunning=$vpnRunning, force=$force)")

        val effectiveTimeoutMs = if (vpnRunning && !force) {
            AppConfig.WEATHER_LOCATION_TIMEOUT_MS / 2
        } else {
            AppConfig.WEATHER_LOCATION_TIMEOUT_MS
        }

        val cancellationSignal = CancellationSignal()
        val location = withTimeoutOrNull(effectiveTimeoutMs) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { cancellationSignal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        locationManager,
                        provider,
                        cancellationSignal,
                        ContextCompat.getMainExecutor(context)
                    ) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

        if (location != null) return location

        LogUtil.w("WeatherHelper", "Location request timed out after ${effectiveTimeoutMs}ms (vpnRunning=$vpnRunning), trying fallback provider")

        val altProvider = if (provider == LocationManager.GPS_PROVIDER)
            LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
        if (locationManager.isProviderEnabled(altProvider)) {
            try {
                val altLocation = locationManager.getLastKnownLocation(altProvider)
                if (altLocation != null) {
                    LogUtil.d("WeatherHelper", "Fallback provider $altProvider returned last known location")
                }
                return altLocation
            } catch (e: SecurityException) {
                LogUtil.w("WeatherHelper", "Fallback getLastKnownLocation SecurityException: ${e.message}")
            }
        }
        return null
    }

    suspend fun fetchCurrentWeather(context: Context, force: Boolean = false): WeatherResult? =
        withContext(Dispatchers.IO) {
            val location = getCurrentLocation(context, force) ?: return@withContext null
            if (!force) {
                val cached = getCachedWeather()
                if (cached != null && isCacheValidForLocation(location)) {
                    LogUtil.d("WeatherHelper", "Cache still valid for current location, skipping fetch")
                    return@withContext cached
                }
            }
            try {
                fetchOpenMeteo(context, location)?.also { saveCache(it, location) }
            } catch (e: Exception) {
                LogUtil.e("WeatherHelper", "fetchCurrentWeather failed: ${e::class.simpleName} - ${e.message}")
                null
            }
        }

    private fun fetchOpenMeteo(context: Context, location: android.location.Location): WeatherResult? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${location.latitude}" +
            "&longitude=${location.longitude}" +
            "&current=temperature_2m,weather_code,is_day"
        val body = getBody(context, url) ?: return null
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

    private fun getBody(context: Context, url: String): String? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MikuRay/1.0 (Android)")
            .header("Accept", "application/json")
            .build()

        val network = findNonVpnNetwork(context)
        val httpClient = if (network != null) {
            try {
                client.newBuilder()
                    .socketFactory(network.socketFactory)
                    .dns(object : okhttp3.Dns {
                        override fun lookup(hostname: String): List<java.net.InetAddress> {
                            return try {
                                network.getAllByName(hostname).toList()
                            } catch (e: Exception) {
                                LogUtil.w("WeatherHelper", "DNS lookup via network failed, fallback to system: ${e.message}")
                                okhttp3.Dns.SYSTEM.lookup(hostname)
                            }
                        }
                    })
                    .build()
            } catch (e: Exception) {
                LogUtil.w("WeatherHelper", "Failed to bind OkHttp to non-VPN network: ${e.message}")
                client
            }
        } else {
            LogUtil.w("WeatherHelper", "No non-VPN network found, using default client")
            client
        }

        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                LogUtil.w("WeatherHelper", "Open-Meteo HTTP ${resp.code}")
                null
            } else resp.body?.string()
        }
    }

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
