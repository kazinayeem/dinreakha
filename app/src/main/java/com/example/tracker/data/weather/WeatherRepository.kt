package com.example.tracker.data.weather

import android.content.Context
import com.example.tracker.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WeatherRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _weatherFlow = MutableStateFlow(WeatherState(isLoading = true))
    val weatherFlow: StateFlow<WeatherState> = _weatherFlow.asStateFlow()

    init {
        scope.launch {
            // Immediately restore cached data so Home Screen displays without delay
            val cachedJson = settings.getCachedWeatherJson()
            if (!cachedJson.isNullOrBlank()) {
                val cached = parseWeatherDataJson(cachedJson)
                if (cached != null) {
                    _weatherFlow.value = WeatherState(data = cached.copy(isOfflineCache = true), isLoading = false)
                }
            }
            // Fetch live data
            refresh(force = false)
        }
    }

    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        val currentSettings = settings.settings.first()
        if (!currentSettings.weatherEnabled) {
            _weatherFlow.value = WeatherState(data = null, isLoading = false)
            return@withContext
        }

        val now = System.currentTimeMillis()
        val currentData = _weatherFlow.value.data
        // Cache valid for 20 minutes unless forced
        if (!force && currentData != null && (now - currentData.updatedAtMillis) < 20 * 60 * 1000L) {
            return@withContext
        }

        _weatherFlow.value = _weatherFlow.value.copy(isLoading = true, error = null)

        val detected = if (LocationHelper.hasLocationPermission(context)) {
            runCatching { LocationHelper.detectCurrentLocation(context) }.getOrNull()
        } else null

        val lat = detected?.latitude ?: currentSettings.weatherLat
        val lon = detected?.longitude ?: currentSettings.weatherLon
        val city = detected?.cityName ?: currentSettings.weatherCity

        if (detected != null && (detected.latitude != currentSettings.weatherLat || detected.longitude != currentSettings.weatherLon)) {
            runCatching { settings.setWeatherLocation(city, lat, lon) }
        }

        try {
            val weather = fetchFromApi(lat, lon, city)
            _weatherFlow.value = WeatherState(data = weather, isLoading = false, error = null)
            settings.setCachedWeatherJson(weather.toJson())
        } catch (e: Exception) {
            val fallback = _weatherFlow.value.data?.copy(isOfflineCache = true)
            _weatherFlow.value = WeatherState(
                data = fallback,
                isLoading = false,
                error = if (fallback != null) "Offline mode (showing cached)" else "Unable to fetch weather",
            )
        }
    }

    private suspend fun fetchFromApi(lat: Double, lon: Double, cityName: String): WeatherData = coroutineScope {
        val latStr = String.format(Locale.US, "%.4f", lat)
        val lonStr = String.format(Locale.US, "%.4f", lon)

        val weatherUrl = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latStr&longitude=$lonStr" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto"

        val aqiUrl = "https://air-quality-api.open-meteo.com/v1/air-quality" +
            "?latitude=$latStr&longitude=$lonStr" +
            "&current=us_aqi,european_aqi,pm10,pm2_5&timezone=auto"

        val weatherDeferred = async(Dispatchers.IO) { httpGet(weatherUrl) }
        val aqiDeferred = async(Dispatchers.IO) { runCatching { httpGet(aqiUrl) }.getOrNull() }

        val weatherJsonStr = weatherDeferred.await()
        val aqiJsonStr = aqiDeferred.await()

        val wRoot = JSONObject(weatherJsonStr)
        val current = wRoot.getJSONObject("current")
        val daily = wRoot.optJSONObject("daily")

        var tempMax: Double? = null
        var tempMin: Double? = null
        daily?.let {
            val maxArr = it.optJSONArray("temperature_2m_max")
            val minArr = it.optJSONArray("temperature_2m_min")
            if (maxArr != null && maxArr.length() > 0) tempMax = maxArr.getDouble(0)
            if (minArr != null && minArr.length() > 0) tempMin = minArr.getDouble(0)
        }

        var usAqi: Int? = null
        var europeanAqi: Int? = null
        var pm25: Double? = null
        var pm10: Double? = null

        if (!aqiJsonStr.isNullOrBlank()) {
            runCatching {
                val aqiRoot = JSONObject(aqiJsonStr)
                val aqiCurrent = aqiRoot.optJSONObject("current")
                if (aqiCurrent != null) {
                    if (aqiCurrent.has("us_aqi")) usAqi = aqiCurrent.getInt("us_aqi")
                    if (aqiCurrent.has("european_aqi")) europeanAqi = aqiCurrent.getInt("european_aqi")
                    if (aqiCurrent.has("pm2_5")) pm25 = aqiCurrent.getDouble("pm2_5")
                    if (aqiCurrent.has("pm10")) pm10 = aqiCurrent.getDouble("pm10")
                }
            }
        }

        WeatherData(
            temperature = current.getDouble("temperature_2m"),
            feelsLike = current.optDouble("apparent_temperature", current.getDouble("temperature_2m")),
            humidity = current.optInt("relative_humidity_2m", 50),
            windSpeed = current.optDouble("wind_speed_10m", 0.0),
            precipitation = current.optDouble("precipitation", 0.0),
            weatherCode = current.optInt("weather_code", 0),
            isDay = current.optInt("is_day", 1) == 1,
            tempMax = tempMax,
            tempMin = tempMin,
            cityName = cityName,
            usAqi = usAqi,
            europeanAqi = europeanAqi,
            pm25 = pm25,
            pm10 = pm10,
            updatedAtMillis = System.currentTimeMillis(),
            isOfflineCache = false,
        )
    }

    private fun httpGet(urlString: String): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("User-Agent", "Dinrekha/1.0 (Android)")
        }
        val code = conn.responseCode
        if (code in 200..299) {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
            throw Exception("HTTP $code: $err")
        }
    }
}

fun WeatherData.toJson(): String {
    val o = JSONObject()
    o.put("temperature", temperature)
    o.put("feelsLike", feelsLike)
    o.put("humidity", humidity)
    o.put("windSpeed", windSpeed)
    o.put("precipitation", precipitation)
    o.put("weatherCode", weatherCode)
    o.put("isDay", isDay)
    tempMax?.let { o.put("tempMax", it) }
    tempMin?.let { o.put("tempMin", it) }
    o.put("cityName", cityName)
    usAqi?.let { o.put("usAqi", it) }
    europeanAqi?.let { o.put("europeanAqi", it) }
    pm25?.let { o.put("pm25", it) }
    pm10?.let { o.put("pm10", it) }
    o.put("updatedAtMillis", updatedAtMillis)
    return o.toString()
}

fun parseWeatherDataJson(json: String): WeatherData? {
    return runCatching {
        val o = JSONObject(json)
        WeatherData(
            temperature = o.getDouble("temperature"),
            feelsLike = o.optDouble("feelsLike", o.getDouble("temperature")),
            humidity = o.optInt("humidity", 50),
            windSpeed = o.optDouble("windSpeed", 0.0),
            precipitation = o.optDouble("precipitation", 0.0),
            weatherCode = o.optInt("weatherCode", 0),
            isDay = o.optBoolean("isDay", true),
            tempMax = if (o.has("tempMax")) o.getDouble("tempMax") else null,
            tempMin = if (o.has("tempMin")) o.getDouble("tempMin") else null,
            cityName = o.optString("cityName", "Dhaka"),
            usAqi = if (o.has("usAqi")) o.getInt("usAqi") else null,
            europeanAqi = if (o.has("europeanAqi")) o.getInt("europeanAqi") else null,
            pm25 = if (o.has("pm25")) o.getDouble("pm25") else null,
            pm10 = if (o.has("pm10")) o.getDouble("pm10") else null,
            updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis()),
            isOfflineCache = true,
        )
    }.getOrNull()
}

