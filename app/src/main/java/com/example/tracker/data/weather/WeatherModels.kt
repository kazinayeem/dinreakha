package com.example.tracker.data.weather

import androidx.compose.ui.graphics.Color

data class WeatherCondition(
    val code: Int,
    val nameEn: String,
    val nameBn: String,
    val emoji: String,
) {
    companion object {
        fun fromCode(code: Int, isDay: Boolean): WeatherCondition {
            return when (code) {
                0 -> if (isDay) WeatherCondition(0, "Clear Sky", "পরিষ্কার আকাশ", "☀️")
                     else WeatherCondition(0, "Clear Night", "পরিষ্কার রাত", "🌙")
                1 -> WeatherCondition(1, "Mainly Clear", "প্রায় মেঘমুক্ত", "🌤️")
                2 -> WeatherCondition(2, "Partly Cloudy", "আংশিক মেঘলা", "⛅")
                3 -> WeatherCondition(3, "Overcast", "মেঘলা আকাশ", "☁️")
                45, 48 -> WeatherCondition(code, "Foggy", "কুয়াশাচ্ছন্ন", "🌫️")
                51, 53, 55 -> WeatherCondition(code, "Drizzle", "গুঁড়ি গুঁড়ি বৃষ্টি", "🌦️")
                56, 57 -> WeatherCondition(code, "Freezing Drizzle", "হিমেল গুঁড়ি বৃষ্টি", "🌧️")
                61, 63, 65 -> WeatherCondition(code, "Rain", "বৃষ্টিপাত", "🌧️")
                66, 67 -> WeatherCondition(code, "Freezing Rain", "হিমেল বৃষ্টিপাত", "🌧️")
                71, 73, 75, 77 -> WeatherCondition(code, "Snow", "তুষারপাত", "❄️")
                80, 81, 82 -> WeatherCondition(code, "Rain Showers", "বর্ষণ", "🌧️")
                85, 86 -> WeatherCondition(code, "Snow Showers", "তুষার বর্ষণ", "❄️")
                95 -> WeatherCondition(95, "Thunderstorm", "বজ্রঝড়", "⛈️")
                96, 99 -> WeatherCondition(code, "Thunderstorm w/ Hail", "শিলাবৃষ্টি সহ ঝড়", "⛈️")
                else -> WeatherCondition(code, "Fair", "স্বাভাবিক", if (isDay) "🌤️" else "🌙")
            }
        }
    }
}

enum class AirQualityLevel(
    val range: IntRange,
    val labelEn: String,
    val labelBn: String,
    val adviceEn: String,
    val adviceBn: String,
    val color: Color,
) {
    GOOD(
        0..50,
        "Good",
        "ভালো",
        "Air is clean & fresh",
        "বাতাস পরিষ্কার ও স্বাস্থ্যকর",
        Color(0xFF2E7D32),
    ),
    MODERATE(
        51..100,
        "Moderate",
        "পরিমিত",
        "Acceptable air quality",
        "বাতাসের মান পরিমিত",
        Color(0xFFD4A017),
    ),
    SENSITIVE(
        101..150,
        "Unhealthy for Sensitive",
        "সংবেদনশীলদের জন্য ক্ষতিকর",
        "Sensitive groups should limit exertion",
        "সংবেদনশীল ব্যক্তিরা বাইরে সতর্কতা বজায় রাখুন",
        Color(0xFFE65100),
    ),
    UNHEALTHY(
        151..200,
        "Unhealthy",
        "অস্বাস্থ্যকর",
        "Wearing a mask is recommended outdoors",
        "বাইরে মাস্ক পরা এবং সতর্কতা প্রয়োজন",
        Color(0xFFC62828),
    ),
    VERY_UNHEALTHY(
        201..300,
        "Very Unhealthy",
        "খুব অস্বাস্থ্যকর",
        "Health alert: avoid prolonged outdoor activity",
        "স্বাস্থ্য সতর্কবার্তা: বাইরে থাকা এড়িয়ে চলুন",
        Color(0xFF7B1FA2),
    ),
    HAZARDOUS(
        301..Int.MAX_VALUE,
        "Hazardous",
        "ঝুঁকিপূর্ণ",
        "Emergency: remain indoors with windows closed",
        "জরুরি অবস্থা: ঘরের ভেতর অবস্থান করুন",
        Color(0xFF4A148C),
    );

    companion object {
        fun fromAqi(aqi: Int): AirQualityLevel {
            return entries.firstOrNull { aqi in it.range } ?: if (aqi < 0) GOOD else HAZARDOUS
        }
    }
}

data class WeatherData(
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val precipitation: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val tempMax: Double?,
    val tempMin: Double?,
    val cityName: String,
    val usAqi: Int?,
    val europeanAqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
    val updatedAtMillis: Long,
    val isOfflineCache: Boolean = false,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromCode(weatherCode, isDay)
    val aqiLevel: AirQualityLevel? get() = usAqi?.let { AirQualityLevel.fromAqi(it) }
}

data class WeatherState(
    val data: WeatherData? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class CityPreset(
    val nameEn: String,
    val nameBn: String,
    val lat: Double,
    val lon: Double,
)

val BANGLADESH_CITIES = listOf(
    CityPreset("Dhaka", "ঢাকা", 23.8103, 90.4125),
    CityPreset("Chittagong", "চট্টগ্রাম", 22.3569, 91.7832),
    CityPreset("Sylhet", "সিলেট", 24.8949, 91.8687),
    CityPreset("Rajshahi", "রাজশাহী", 24.3745, 88.6042),
    CityPreset("Khulna", "খুলনা", 22.8456, 89.5403),
    CityPreset("Barisal", "বরিশাল", 22.7010, 90.3535),
    CityPreset("Rangpur", "রংপুর", 25.7439, 89.2752),
    CityPreset("Mymensingh", "ময়মনসিংহ", 24.7471, 90.4203),
    CityPreset("Comilla", "কুমিল্লা", 23.4607, 91.1809),
    CityPreset("Gazipur", "গাজীপুর", 23.9999, 90.4203),
)

