package com.example.tracker.ui.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DonutSmall
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.tracker.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tracker.data.finance.FinanceMath
import com.example.tracker.data.finance.Money
import com.example.tracker.ui.components.Dot
import com.example.tracker.ui.components.LabeledProgress
import com.example.tracker.ui.components.SectionCard
import com.example.tracker.ui.theme.Expense
import com.example.tracker.ui.theme.Income
import java.time.format.DateTimeFormatter

/** Categorical chart palette — defined with the other theme colours. */
val CHART_COLORS get() = com.example.tracker.ui.theme.ChartPalette

private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun ReportScreen(vm: WalletViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val d = data ?: return
    val cur = FinanceMath.monthTotals(month, d.txns)
    val prev = FinanceMath.monthTotals(month.minusMonths(1), d.txns)
    val byCat = cur.byCategory.entries.sortedByDescending { it.value }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Month Selector Card
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(20.dp),
                    ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.month.value = month.minusMonths(1) }) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronLeft,
                            contentDescription = com.example.tracker.ui.i18n.tr("Previous month"),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = MONTH.format(month),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { vm.month.value = month.plusMonths(1) }) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = com.example.tracker.ui.i18n.tr("Next month"),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Executive Financial Overview Card
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(20.dp),
                    ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Income
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SouthWest, null, Modifier.size(14.dp), tint = Income)
                            Spacer(Modifier.width(4.dp))
                            Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = Money.format(cur.income),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Income,
                            maxLines = 1,
                        )
                    }

                    // Expense
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NorthEast, null, Modifier.size(14.dp), tint = Expense)
                            Spacer(Modifier.width(4.dp))
                            Text("Expense", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = Money.format(cur.expense),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Expense,
                            maxLines = 1,
                        )
                    }

                    // Net
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("Net", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = Money.format(cur.net, showSign = true),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (cur.net >= 0) Income else Expense,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Month-over-Month Comparison Card
        item {
            SectionCard(
                title = "Month vs month",
                icon = if ((FinanceMath.changePercent(cur.expense, prev.expense) ?: 0) > 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
            ) {
                val ch = FinanceMath.changePercent(cur.expense, prev.expense)
                Text(
                    text = when {
                        ch == null -> "No previous month data available to compare."
                        ch > 0 -> "Spending is up $ch% vs last month (${Money.format(prev.expense)} → ${Money.format(cur.expense)})."
                        ch < 0 -> "Spending dropped ${-ch}% vs last month (${Money.format(prev.expense)} → ${Money.format(cur.expense)})."
                        else -> "Spending is identical to last month."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Income flow: ${Money.format(prev.income)} → ${Money.format(cur.income)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Spending by Category Donut Card
        item {
            SectionCard(
                title = "By category",
                icon = Icons.Rounded.DonutSmall,
            ) {
                if (byCat.isEmpty()) {
                    Text(
                        text = "No recorded expenses for this month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Donut(byCat.map { it.value }, Modifier.size(130.dp))
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            byCat.take(6).forEachIndexed { i, (catId, v) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 3.dp),
                                ) {
                                    Dot(CHART_COLORS[i % CHART_COLORS.size], size = 8.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = d.categoryById[catId]?.let { "${it.icon} ${it.name}" } ?: "Other",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = "${v * 100 / cur.expense.coerceAtLeast(1)}%",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Daily Spending Distribution Card
        item {
            SectionCard(
                title = "Spending per day",
                icon = Icons.Rounded.BarChart,
            ) {
                val days = (1..month.lengthOfMonth()).map { cur.byDay[month.atDay(it)] ?: 0L }
                DailyBars(days, Modifier.fillMaxWidth().height(130.dp))
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Day 1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Day ${month.lengthOfMonth()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val max = days.maxOrNull() ?: 0
                if (max > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Peak day ${Money.format(max)}, daily average ${Money.format(cur.expense / month.lengthOfMonth())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Top Spending Categories Progress
        if (byCat.isNotEmpty()) {
            item {
                SectionCard(title = "Top expenses") {
                    byCat.take(5).forEachIndexed { i, (catId, v) ->
                        val cat = d.categoryById[catId]
                        LabeledProgress(
                            label = "${cat?.icon ?: ""} ${cat?.name ?: "Other"}",
                            value = Money.format(v),
                            progress = v.toFloat() / (byCat.first().value.coerceAtLeast(1)),
                            color = CHART_COLORS[i % CHART_COLORS.size],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Donut(values: List<Long>, modifier: Modifier) {
    val total = values.sum().toFloat().coerceAtLeast(1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        val stroke = size.minDimension * 0.18f
        val d = size.minDimension - stroke
        val tl = Offset(stroke / 2, stroke / 2)
        drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
        var start = -90f
        values.forEachIndexed { i, v ->
            val sweep = 360f * v / total
            drawArc(CHART_COLORS[i % CHART_COLORS.size], start, (sweep - 1.5f).coerceAtLeast(0.5f), false, tl, Size(d, d), style = Stroke(stroke))
            start += sweep
        }
    }
}

@Composable
private fun DailyBars(values: List<Long>, modifier: Modifier) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
    val color = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.outlineVariant
    Box(modifier) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            val n = values.size
            val slot = size.width / n
            val w = slot * 0.62f
            drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), 2f)
            values.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (size.height - 4f) * (v / max)
                drawRoundRect(color, Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(w / 3))
            }
        }
    }
}
