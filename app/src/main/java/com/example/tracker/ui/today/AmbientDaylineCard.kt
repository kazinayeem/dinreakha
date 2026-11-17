package com.example.tracker.ui.today

import android.Manifest
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tracker.data.schedule.PlanCalendar.EventType
import com.example.tracker.data.schedule.PlanCalendar.PlanEvent
import com.example.tracker.data.weather.AirQualityLevel
import com.example.tracker.data.weather.LocationHelper
import com.example.tracker.data.weather.WeatherState
import com.example.tracker.ui.components.HM
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val SunGold = Color(0xFFFFB74D)
private val SunsetOrange = Color(0xFFFF7043)
private val NightIndigo = Color(0xFF6366F1)

/**
 * Home hero: weather on top, the Dayline (wake → bed, sun or moon at "now") underneath.
 * Follows the app's own light/dark theme (not the system's), icons instead of sentences,
 * and one motion only — the sun gliding into place when the screen opens.
 */
@Composable
fun AmbientDaylineCard(
    wake: LocalDateTime,
    bed: LocalDateTime,
    now: LocalDateTime,
    events: List<PlanEvent>,
    doneKeys: Set<String>,
    weatherState: WeatherState,
    onRefreshWeather: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current
    val data = weatherState.data
    val cs = MaterialTheme.colorScheme
    val light = cs.surface.luminance() > 0.5f

    val totalMin = Duration.between(wake, bed).toMinutes().coerceAtLeast(1).toFloat()
    fun frac(t: LocalDateTime) = Duration.between(wake, t).toMinutes() / totalMin
    val target = frac(now)

    val animationsOff = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val body = remember { Animatable(if (animationsOff) target.coerceIn(-0.08f, 1.08f) else 0f) }
    LaunchedEffect(target) {
        val to = target.coerceIn(-0.08f, 1.08f)
        if (animationsOff) body.snapTo(to) else body.animateTo(to, tween(900, easing = FastOutSlowInEasing))
    }

    // Sun after wake, moon from Maghrib (or when the weather service says it's night).
    val maghrib = events.firstOrNull { it.type == EventType.PRAYER && it.title.equals("Maghrib", true) }?.start
        ?: wake.toLocalDate().atTime(17, 15)
    val isNight = now >= maghrib || data?.isDay == false || now < wake || now >= bed
    val isSunset = !isNight && Duration.between(now, maghrib).toMinutes() in 0..45

    val temp = data?.temperature
    val tint = when {
        isNight -> NightIndigo
        temp != null && temp >= 30.0 -> SunsetOrange
        temp != null && temp < 20.0 -> Color(0xFF0EA5E9)
        else -> cs.primary
    }
    val accent = if (isNight) NightIndigo.copy(alpha = if (light) 1f else 0.9f) else cs.primary
    val moon = if (light) Color(0xFF7C83C9) else Color(0xFFE8ECFF)
    val rest = cs.outlineVariant
    val muted = cs.onSurfaceVariant

    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.any { it }) onRefreshWeather()
    }
    val refresh: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        if (LocationHelper.hasLocationPermission(ctx)) onRefreshWeather()
        else locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = cs.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = if (light) 0.10f else 0.14f), Color.Transparent)))
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp),
        ) {
            // ---- Weather: icon + temperature on the left, air quality + refresh on the right ----
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    data != null -> {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(weatherIcon(data.weatherCode, !isNight), data.condition.nameEn, tint = tint, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${data.temperature.roundToInt()}°", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                            Text(data.cityName.ifBlank { data.condition.nameEn }, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                        }
                        data.usAqi?.let { aqi ->
                            val level = data.aqiLevel ?: AirQualityLevel.GOOD
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(level.color.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .semantics(mergeDescendants = true) { contentDescription = "Air quality $aqi, ${level.labelEn}" },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Air, null, tint = level.color, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("$aqi", color = level.color, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    weatherState.isLoading -> {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = accent)
                        Spacer(Modifier.weight(1f))
                    }
                    else -> {
                        Icon(Icons.Rounded.MyLocation, null, tint = muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Weather", style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.weight(1f))
                    }
                }
                val spin = if (weatherState.isLoading) {
                    rememberInfiniteTransition(label = "spin").animateFloat(
                        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "rot",
                    ).value
                } else 0f
                IconButton(onClick = refresh) {
                    Icon(Icons.Rounded.Refresh, "Refresh weather", tint = muted, modifier = Modifier.size(20.dp).rotate(spin))
                }
            }

            // ---- The Dayline ----
            Canvas(
                Modifier.fillMaxWidth().padding(end = 8.dp).height(92.dp)
                    .semantics { contentDescription = "Day ${(target.coerceIn(0f, 1f) * 100).toInt()} percent done" },
            ) {
                val pad = 12.dp.toPx()
                val w = size.width - pad * 2
                val horizon = size.height * 0.78f
                val arcH = size.height * 0.62f
                fun x(f: Float) = pad + w * f.coerceIn(0f, 1f)
                fun arcY(f: Float) = horizon - sin(PI * f.coerceIn(0f, 1f)).toFloat() * arcH

                val path = Path().apply {
                    moveTo(x(0f), horizon)
                    for (i in 1..40) { val f = i / 40f; lineTo(x(f), arcY(f)) }
                }
                drawPath(path, rest.copy(alpha = 0.6f),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 6.dp.toPx())), cap = StrokeCap.Round))

                val s = body.value
                drawLine(rest, Offset(x(0f), horizon), Offset(x(1f), horizon), 4.dp.toPx(), StrokeCap.Round)
                if (s > 0f) drawLine(accent, Offset(x(0f), horizon), Offset(x(s), horizon), 4.dp.toPx(), StrokeCap.Round)

                events.forEach { e ->
                    val f = frac(e.start)
                    if (f < 0f || f > 1f) return@forEach
                    val done = e.habitKey != null && e.habitKey in doneKeys
                    if (done) drawCircle(accent, 4.5.dp.toPx(), Offset(x(f), horizon))
                    else drawLine(
                        if (e.type == EventType.CLASS) SunGold else muted.copy(alpha = 0.7f),
                        Offset(x(f), horizon + 6.dp.toPx()), Offset(x(f), horizon + 12.dp.toPx()), 2.dp.toPx(), StrokeCap.Round,
                    )
                }

                val pos = if (s in 0f..1f) Offset(x(s), arcY(s)) else Offset(if (s < 0f) x(0f) else x(1f), horizon - 18.dp.toPx())
                if (isNight) {
                    // A few still stars above the arc
                    listOf(0.12f to 0.30f, 0.27f to 0.08f, 0.46f to 0.18f, 0.63f to 0.04f, 0.81f to 0.22f, 0.93f to 0.10f)
                        .forEachIndexed { i, (fx, fy) ->
                            drawCircle(moon.copy(alpha = if (i % 2 == 0) 0.75f else 0.45f),
                                (if (i % 3 == 0) 1.6f else 1.1f).dp.toPx(), Offset(x(fx), size.height * fy + 4.dp.toPx()))
                        }
                    val r = 10.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(moon.copy(alpha = 0.30f), Color.Transparent), pos, r * 3f), r * 3f, pos)
                    // True crescent: disc minus an offset disc, so whatever is behind shows through.
                    val crescent = Path().apply {
                        op(
                            Path().apply { addOval(androidx.compose.ui.geometry.Rect(pos, r)) },
                            Path().apply { addOval(androidx.compose.ui.geometry.Rect(pos + Offset(r * 0.55f, -r * 0.35f), r * 0.88f)) },
                            androidx.compose.ui.graphics.PathOperation.Difference,
                        )
                    }
                    drawPath(crescent, Brush.radialGradient(listOf(Color.White, moon), pos - Offset(r * 0.4f, 0f), r * 1.4f))
                } else {
                    val sun = if (isSunset) SunsetOrange else SunGold
                    val r = 9.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(sun.copy(alpha = 0.40f), Color.Transparent), pos, r * 3.2f), r * 3.2f, pos)
                    // Eight short rays
                    for (k in 0 until 8) {
                        val a = k * PI / 4
                        val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
                        drawLine(sun.copy(alpha = 0.8f), pos + dir * (r * 1.45f), pos + dir * (r * 1.95f), 2.dp.toPx(), StrokeCap.Round)
                    }
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3D6), sun), pos - Offset(r * 0.3f, r * 0.3f), r * 1.3f), r, pos)
                }
            }

            // ---- Wake · progress · bed, as icons ----
            Row(
                Modifier.fillMaxWidth().padding(end = 8.dp, top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconValue(Icons.Rounded.WbTwilight, HM.format(wake), muted, "Wake")
                IconValue(
                    if (isNight) Icons.Rounded.NightsStay else Icons.Rounded.WbSunny,
                    "${(target.coerceIn(0f, 1f) * 100).toInt()}%", accent, "Day done",
                )
                IconValue(Icons.Rounded.Bedtime, HM.format(bed), muted, "Bed")
            }

            // ---- Weather details: three icon chips ----
            if (data != null) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(Icons.Rounded.WaterDrop, "${data.humidity}%", "Humidity", Modifier.weight(1f))
                    Chip(Icons.Rounded.Air, "${data.windSpeed.roundToInt()} km/h", "Wind", Modifier.weight(1f))
                    if (data.tempMax != null && data.tempMin != null) {
                        Chip(Icons.Rounded.Thermostat, "${data.tempMax.roundToInt()}° / ${data.tempMin.roundToInt()}°", "High and low", Modifier.weight(1f))
                    }
                }
                val level = data.aqiLevel
                if (level != null && level.ordinal >= AirQualityLevel.SENSITIVE.ordinal) {
                    Spacer(Modifier.height(8.dp))
                    Text(level.adviceEn, style = MaterialTheme.typography.labelMedium, color = level.color)
                }
            }
        }
    }
}

@Composable
private fun IconValue(icon: ImageVector, value: String, tint: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value" },
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = tint)
    }
}

@Composable
private fun Chip(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

private fun weatherIcon(code: Int, day: Boolean): ImageVector = when (code) {
    0 -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    1, 2 -> if (day) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    3, 45, 48 -> Icons.Rounded.Cloud
    in 51..67, in 80..82 -> Icons.Rounded.Umbrella
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.Thunderstorm
    else -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
}
