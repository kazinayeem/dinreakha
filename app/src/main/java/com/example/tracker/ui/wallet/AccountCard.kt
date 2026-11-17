package com.example.tracker.ui.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.tracker.data.db.Account
import com.example.tracker.data.db.AccountType
import com.example.tracker.data.finance.Money

/** Refined luxury color palette per account type. */
val TYPE_COLORS: Map<AccountType, Long> = mapOf(
    AccountType.BANK to 0xFF16325C,
    AccountType.CASH to 0xFF1C5A38,
    AccountType.BKASH to 0xFFA80E52,
    AccountType.NAGAD to 0xFFCE4400,
    AccountType.ROCKET to 0xFF581F96,
    AccountType.OTHER to 0xFF2B3548,
)

/** Standard physical credit/debit card proportions (85.6 × 54 mm). */
private const val CARD_RATIO = 1.586f

/**
 * Premium physical card design:
 * Luxury gradient with specular metallic sheen, EMV contact chip,
 * contactless NFC waves, frosted glass type pill, and clean sans-serif typography.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountCard(
    account: Account,
    balance: Long,
    width: Dp = 280.dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val base = Color(account.color)
    val highlight = lerp(base, Color.White, 0.22f)
    val dark = lerp(base, Color.Black, 0.45f)
    val ink = Color.White

    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(CARD_RATIO)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp), ambientColor = Color.Black.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(highlight, base, dark),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.20f),
                    )
                ),
                shape = RoundedCornerShape(20.dp),
            )
            .drawBehind {
                // Subtle diagonal specular gloss reflection
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                        start = Offset(0f, size.height * 0.1f),
                        end = Offset(size.width * 0.75f, size.height),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = size.width * 0.3f,
                )
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Edit account",
            )
            .semantics {
                contentDescription = "${account.name}, ${account.type.label}, balance ${Money.format(balance)}"
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header Row: Type frosted badge + NFC waves & Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Type badge pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = account.type.icon(),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = ink,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = account.type.label.uppercase(),
                            color = ink,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.08.em,
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ContactlessWaveIcon()
                    if (account.type == AccountType.BANK || account.type == AccountType.OTHER) {
                        CardChip()
                    }
                }
            }

            // Middle: Balance
            Column(modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = "Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = ink.copy(alpha = 0.70f),
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = Money.format(balance),
                    color = if (balance < 0) Color(0xFFFFB4AB) else ink,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.02).em,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Footer Row: Account name + Card number / last 4
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = account.name,
                    modifier = Modifier.weight(1f, fill = false),
                    color = ink.copy(alpha = 0.90f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = account.last4?.let { "•••• $it" } ?: "••••",
                    color = ink.copy(alpha = 0.80f),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.em,
                    ),
                )
            }
        }
    }
}

/** Contactless NFC wave icon drawn with precision arcs. */
@Composable
private fun ContactlessWaveIcon() {
    Canvas(modifier = Modifier.size(width = 16.dp, height = 16.dp)) {
        val stroke = 1.6.dp.toPx()
        val color = Color.White.copy(alpha = 0.80f)
        val cy = size.height * 0.5f

        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-size.width * 0.2f, cy - size.height * 0.22f),
            size = Size(size.width * 0.44f, size.height * 0.44f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-size.width * 0.1f, cy - size.height * 0.38f),
            size = Size(size.width * 0.76f, size.height * 0.76f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(0f, cy - size.height * 0.52f),
            size = Size(size.width * 1.05f, size.height * 1.05f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/** Realistic gold EMV card chip with metallic gradient & circuit traces. */
@Composable
private fun CardChip() {
    Box(
        modifier = Modifier
            .size(width = 28.dp, height = 20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFFDF88),
                        Color(0xFFD4AF37),
                        Color(0xFFAA8020),
                        Color(0xFFE5C158),
                    )
                )
            )
            .border(0.5.dp, Color(0xFF7A5810).copy(alpha = 0.40f), RoundedCornerShape(5.dp)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 0.8.dp.toPx()
            val traceColor = Color(0xFF5A400A).copy(alpha = 0.40f)
            drawLine(traceColor, Offset(size.width * 0.35f, 0f), Offset(size.width * 0.35f, size.height), stroke)
            drawLine(traceColor, Offset(size.width * 0.65f, 0f), Offset(size.width * 0.65f, size.height), stroke)
            drawLine(traceColor, Offset(0f, size.height * 0.5f), Offset(size.width, size.height * 0.5f), stroke)
        }
    }
}

/** "Add account" companion card matching the card aspect ratio with pro styling. */
@Composable
fun AddAccountCard(width: Dp = 140.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(CARD_RATIO)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add Account",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Bank • MFS • Cash",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
