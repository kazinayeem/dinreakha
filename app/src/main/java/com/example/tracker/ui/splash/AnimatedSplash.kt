package com.example.tracker.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tracker.data.quotes.MotivationQuotes
import com.example.tracker.data.repo.QuoteCategory
import com.example.tracker.data.repo.QuoteLanguage
import com.example.tracker.ui.theme.BrandIndigo
import com.example.tracker.ui.theme.BrandMint
import com.example.tracker.ui.theme.BrandNight
import com.example.tracker.ui.theme.BrandTeal
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

private val Sun = Color(0xFFFFC27A)

/**
 * Animated splash intro:
 *  1. Horizon line draws left → right
 *  2. Sun rises out of horizon with warm glow
 *  3. Sunrays pop out with spring
 *  4. Horizon shoots up into Dinrekha checkmark
 *  5. "Dinrekha" + "দিনরেখা" fade in
 *  6. Curated inspirational quote (Ayat / Hadith / Productivity) fades in
 *  7. Relaxed reading pace ("speed 1") with instant tap-to-skip.
 */
@Composable
fun AnimatedSplash(
    category: QuoteCategory = QuoteCategory.ALL,
    language: QuoteLanguage = QuoteLanguage.BOTH,
    onFinished: () -> Unit,
) {
    val quote = remember(category, language) {
        MotivationQuotes.getRandomQuote(category, language)
    }

    var finished by remember { mutableStateOf(false) }
    val finishOnce = remember(onFinished) {
        {
            if (!finished) {
                finished = true
                onFinished()
            }
        }
    }

    val line = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val rays = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val quoteAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Step 1: Horizon line
        line.animateTo(0.3f, tween(340, easing = LinearOutSlowInEasing))
        // Step 2 & 3: Sun rise + rays
        val sun = async { rise.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        delay(340)
        val r = async { rays.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
        line.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        sun.await()
        // Step 4: Title + Bengali tagline
        title.animateTo(1f, tween(260))
        tagline.animateTo(1f, tween(200))
        r.await()
        // Step 5: Inspirational quote fade-in
        quoteAlpha.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        // Comfortable reading time before continuing automatically
        delay(2000)
        finishOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BrandNight, BrandIndigo, BrandTeal)))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = finishOnce,),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Canvas(Modifier.size(140.dp)) {
                val s = size.minDimension / 108f
                val horizonY = 66f * s

                // Sun clipped above horizon
                val sunCenter = Offset(46f * s, 56f * s + (1f - rise.value) * 22f * s)
                clipRect(0f, 0f, size.width, horizonY - 3f * s) {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Sun.copy(alpha = 0.35f * rise.value), Color.Transparent),
                            center = sunCenter,
                            radius = 30f * s,
                        ),
                        radius = 30f * s,
                        center = sunCenter,
                    )
                    clipRect(0f, 0f, size.width, sunCenter.y) {
                        drawCircle(Sun, radius = 13f * s, center = sunCenter)
                    }
                }

                // Sun rays
                if (rays.value > 0f) {
                    val rayStroke = Stroke(3f * s, cap = StrokeCap.Round)
                    listOf(Offset(0f, -1f), Offset(-0.707f, -0.707f), Offset(0.707f, -0.707f)).forEach { dir ->
                        val start = Offset(46f * s, 56f * s) + dir * (17f * s)
                        val end = start + dir * (4.5f * s * rays.value)
                        drawLine(Sun, start, end, rayStroke.width, cap = StrokeCap.Round)
                    }
                }

                // Day-line → check
                val full = Path().apply {
                    moveTo(26f * s, 66f * s)
                    lineTo(46f * s, 66f * s)
                    lineTo(53f * s, 73f * s)
                    lineTo(80f * s, 46f * s)
                }
                val pm = PathMeasure().apply { setPath(full, false) }
                val partial = Path()
                pm.getSegment(0f, pm.length * line.value, partial, true)
                drawPath(partial, BrandMint, style = Stroke(6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (line.value > 0.98f) translate(0f, 0f) {
                    drawCircle(BrandMint.copy(alpha = 0.25f), radius = 7f * s, center = Offset(80f * s, 46f * s))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Dinrekha",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                modifier = Modifier.alpha(title.value),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "দিনরেখা",
                color = BrandMint.copy(alpha = 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.alpha(tagline.value),
            )

            Spacer(Modifier.height(28.dp))

            // Dynamic Inspirational Quote Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(quoteAlpha.value),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "“${quote.text}”",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "— ${quote.source}",
                        color = BrandMint,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Tap anywhere to skip",
                color = Color.White.copy(alpha = 0.42f * quoteAlpha.value),
                fontSize = 12.sp,
                letterSpacing = 0.3.sp,
            )
        }
    }
}
