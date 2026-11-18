package com.example.tracker.ui.today

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tracker.data.schedule.PlanCalendar.EventType
import com.example.tracker.data.schedule.PlanCalendar.PlanEvent
import com.example.tracker.ui.components.HM
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.sin

private val Sun = Color(0xFFFFC27A)

/**
 * The Dayline — the app's signature element (দিনরেখা, "line of the day").
 * A horizon from wake time to bedtime. The sun travels an arc above it and sits at "now";
 * the part of the line already lived is drawn in the accent colour. Ticks mark classes,
 * prayers, buses and focus blocks; ticked-off items become solid dots.
 * One orchestrated motion: the sun glides to its place when the screen opens.
 */
@Composable
fun Dayline(
    wake: LocalDateTime,
    bed: LocalDateTime,
    now: LocalDateTime,
    events: List<PlanEvent>,
    doneKeys: Set<String>,
    modifier: Modifier = Modifier,
) {
    val total = Duration.between(wake, bed).toMinutes().coerceAtLeast(1).toFloat()
    fun frac(t: LocalDateTime) = Duration.between(wake, t).toMinutes() / total
    val target = frac(now)

    val ctx = LocalContext.current
    val animationsOff = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val sun = remember { Animatable(if (animationsOff) target.coerceIn(-0.08f, 1.08f) else 0f) }
    LaunchedEffect(target) {
        val to = target.coerceIn(-0.08f, 1.08f)
        if (animationsOff) sun.snapTo(to) else sun.animateTo(to, tween(com.example.tracker.ui.theme.Motion.HERO, easing = FastOutSlowInEasing))
    }

    val accent = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bg = MaterialTheme.colorScheme.background
    val pct = (target.coerceIn(0f, 1f) * 100).toInt()
    val described = when {
        target < 0f -> "Before your wake time, ${HM.format(wake)}."
        target > 1f -> "Past bedtime, ${HM.format(bed)}."
        else -> "$pct percent of your waking day has passed. Wake ${HM.format(wake)}, bed ${HM.format(bed)}."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = described },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp),
        ) {
            val pad = 14.dp.toPx()
            val w = size.width - pad * 2
            val horizon = size.height * 0.76f
            val arcH = size.height * 0.60f
            fun x(f: Float) = pad + w * f.coerceIn(0f, 1f)
            fun arcY(f: Float) = horizon - sin(PI * f.coerceIn(0f, 1f)).toFloat() * arcH

            // The sun's arc path (dashed)
            val path = Path().apply {
                moveTo(x(0f), horizon)
                for (i in 1..48) {
                    val f = i / 48f
                    lineTo(x(f), arcY(f))
                }
            }
            drawPath(
                path = path,
                color = rest.copy(alpha = 0.7f),
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 6.dp.toPx())),
                    cap = StrokeCap.Round,
                ),
            )

            // The horizon line: lived part in accent, remaining in rest
            val s = sun.value
            drawLine(rest, Offset(x(0f), horizon), Offset(x(1f), horizon), 4.dp.toPx(), StrokeCap.Round)
            if (s > 0f) {
                drawLine(accent, Offset(x(0f), horizon), Offset(x(s), horizon), 4.dp.toPx(), StrokeCap.Round)
            }

            // Event ticks along the horizon
            events.forEach { e ->
                val f = frac(e.start)
                if (f < 0f || f > 1f) return@forEach
                val col = if (e.type == EventType.CLASS) Color(0xFFFFC27A) else if (e.type == EventType.PRAYER) accent else muted
                val done = e.habitKey != null && e.habitKey in doneKeys
                if (done) {
                    drawCircle(accent, 5.dp.toPx(), Offset(x(f), horizon))
                } else {
                    drawLine(col, Offset(x(f), horizon + 6.dp.toPx()), Offset(x(f), horizon + 14.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }
            }

            // The Sun (or Moon outside the waking day)
            if (s in 0f..1f) {
                val c = Offset(x(s), arcY(s))
                // Multi-layered corona glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Sun.copy(alpha = 0.50f), Sun.copy(alpha = 0.15f), Color.Transparent),
                        center = c,
                        radius = 32.dp.toPx(),
                    ),
                    radius = 32.dp.toPx(),
                    center = c,
                )
                drawCircle(Sun, 9.5.dp.toPx(), c)
                drawCircle(Color.White.copy(alpha = 0.8f), 3.5.dp.toPx(), c)
            } else {
                val c = Offset(if (s < 0f) x(0f) else x(1f), horizon - 22.dp.toPx())
                drawCircle(Color(0xFFDDE3FF), 8.dp.toPx(), c)
                drawCircle(bg, 7.dp.toPx(), c + Offset(4.dp.toPx(), -3.dp.toPx())) // crescent
            }
        }

        // Wake and bed times under the ends of the horizon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Wake ${HM.format(wake)}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = muted,
            )
            Text(
                text = "Bed ${HM.format(bed)}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = muted,
            )
        }
    }
}
