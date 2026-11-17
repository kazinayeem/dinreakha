package com.example.tracker.data.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherModelsTest {

    @Test
    fun weatherCondition_fromCode() {
        val clearDay = WeatherCondition.fromCode(0, isDay = true)
        assertEquals("Clear Sky", clearDay.nameEn)
        assertEquals("☀️", clearDay.emoji)

        val clearNight = WeatherCondition.fromCode(0, isDay = false)
        assertEquals("Clear Night", clearNight.nameEn)
        assertEquals("🌙", clearNight.emoji)

        val rain = WeatherCondition.fromCode(61, isDay = true)
        assertEquals("Rain", rain.nameEn)
        assertEquals("🌧️", rain.emoji)

        val storm = WeatherCondition.fromCode(95, isDay = true)
        assertEquals("Thunderstorm", storm.nameEn)
        assertEquals("⛈️", storm.emoji)
    }

    @Test
    fun airQualityLevel_fromAqi() {
        assertEquals(AirQualityLevel.GOOD, AirQualityLevel.fromAqi(30))
        assertEquals(AirQualityLevel.MODERATE, AirQualityLevel.fromAqi(80))
        assertEquals(AirQualityLevel.SENSITIVE, AirQualityLevel.fromAqi(130))
        assertEquals(AirQualityLevel.UNHEALTHY, AirQualityLevel.fromAqi(170))
        assertEquals(AirQualityLevel.VERY_UNHEALTHY, AirQualityLevel.fromAqi(260))
        assertEquals(AirQualityLevel.HAZARDOUS, AirQualityLevel.fromAqi(350))
    }

    @Test
    fun weatherData_jsonRoundtrip() {
        val original = WeatherData(
            temperature = 25.4,
            feelsLike = 27.1,
            humidity = 66,
            windSpeed = 9.5,
            precipitation = 0.0,
            weatherCode = 3,
            isDay = true,
            tempMax = 29.9,
            tempMin = 21.6,
            cityName = "Dhaka",
            usAqi = 260,
            europeanAqi = 91,
            pm25 = 118.0,
            pm10 = 131.3,
            updatedAtMillis = 1731844800000L,
            isOfflineCache = false,
        )

        val json = original.toJson()
        val parsed = parseWeatherDataJson(json)

        assertNotNull(parsed)
        assertEquals(25.4, parsed!!.temperature, 0.01)
        assertEquals(27.1, parsed.feelsLike, 0.01)
        assertEquals(66, parsed.humidity)
        assertEquals(9.5, parsed.windSpeed, 0.01)
        assertEquals(3, parsed.weatherCode)
        assertEquals(29.9, parsed.tempMax!!, 0.01)
        assertEquals(21.6, parsed.tempMin!!, 0.01)
        assertEquals("Dhaka", parsed.cityName)
        assertEquals(260, parsed.usAqi)
        assertEquals(118.0, parsed.pm25!!, 0.01)
        assertEquals(AirQualityLevel.VERY_UNHEALTHY, parsed.aqiLevel)
        assertTrue(parsed.isOfflineCache)
    }
}

